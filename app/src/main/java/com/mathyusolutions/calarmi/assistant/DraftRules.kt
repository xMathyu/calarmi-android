package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.domain.RecurrenceEngine
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Weekday
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Las reglas que aplica `CreateReminderTool` de iOS a lo que propone el modelo,
 * compartidas aquí por el parser y por Gemini Nano: ninguna fecha sale de un
 * motor sin pasar por ellas.
 */
object DraftRules {

    /**
     * Pasa al futuro una fecha que quedó atrás por menos de 24 h, avanzando de
     * a un período de la regla: «a las 9» dicho a las 23:00 solo puede ser
     * mañana. Una fecha de otro día pasado se deja (y se avisa que no sonará).
     */
    fun rollIntoFuture(date: LocalDateTime, rule: RecurrenceRule, now: LocalDateTime): LocalDateTime {
        if (date.isAfter(now) || Duration.between(date, now).toHours() >= 24) return date
        var candidate = date
        var guard = 0
        while (!candidate.isAfter(now) && guard < 1000) {
            candidate = when (rule) {
                RecurrenceRule.Once -> candidate.plusDays(1)
                is RecurrenceRule.Daily -> candidate.plusDays(maxOf(1, rule.interval).toLong())
                // Con días elegidos se avanza de a uno: `snap` la deja luego en un día pedido.
                is RecurrenceRule.Weekly ->
                    if (rule.weekdays.isEmpty()) candidate.plusWeeks(maxOf(1, rule.interval).toLong()) else candidate.plusDays(1)
                is RecurrenceRule.Monthly -> candidate.plusMonths(maxOf(1, rule.interval).toLong())
                is RecurrenceRule.Yearly -> candidate.plusYears(maxOf(1, rule.interval).toLong())
            }
            guard++
        }
        return candidate
    }

    /** Lleva la fecha al primer día pedido («todos los lunes» empieza el lunes), con la misma hora. */
    fun snap(date: LocalDateTime, weekdays: Set<Weekday>): LocalDateTime {
        if (weekdays.isEmpty()) return date
        for (offset in 0..7) {
            val candidate = date.plusDays(offset.toLong())
            if (weekdays.any { it.dayOfWeek == candidate.dayOfWeek }) return candidate
        }
        return date
    }

    fun resolveDate(date: LocalDateTime, rule: RecurrenceRule, now: LocalDateTime): LocalDateTime {
        val rolled = rollIntoFuture(date, rule, now)
        val weekdays = (rule as? RecurrenceRule.Weekly)?.weekdays.orEmpty()
        val snapped = snap(rolled, weekdays)
        // `snap` no retrocede, pero si el día pedido era hoy y la hora ya pasó, al siguiente.
        return if (!snapped.isAfter(now) && weekdays.isNotEmpty()) snap(rolled.plusDays(1), weekdays) else snapped
    }

    /** Cumpleaños y aniversarios sin hora («el 15 de marzo») suenan a las 9, no a medianoche. */
    fun defaultingDayEventTime(date: LocalDateTime, choice: CategoryChoice): LocalDateTime =
        if (choice.isDayEvent && date.hour == 0 && date.minute == 0) date.withHour(9).withMinute(0) else date

    /** Minutos libres → los avisos fijos más cercanos, sin repetir. Vacío = a la hora. */
    fun leadTimes(minutes: List<Int>?): List<AlarmLeadTime> {
        val mapped = (minutes ?: listOf(0)).map { AlarmLeadTime.closestTo(maxOf(0, it)) }.distinct()
        return mapped.sortedBy { it.seconds }.ifEmpty { listOf(AlarmLeadTime.AT_START) }
    }

    /** «once/daily/weekly…» + días. Nombrar días siempre es semanal, diga lo que diga el slug. */
    fun recurrence(slug: String?, weekdays: Set<Weekday>, interval: Int = 1): RecurrenceRule {
        val n = maxOf(1, interval)
        val s = slug?.trim()?.lowercase()
        // Un «once» explícito es «este sábado», aunque el modelo nombre el día.
        if (s == "once") return RecurrenceRule.Once
        if (weekdays.isNotEmpty()) return RecurrenceRule.Weekly(if (s == "weekly") n else 1, weekdays)
        return when (s) {
            "daily" -> RecurrenceRule.Daily(n)
            "weekly" -> RecurrenceRule.Weekly(n)
            "monthly" -> RecurrenceRule.Monthly(n)
            "yearly" -> RecurrenceRule.Yearly(n)
            else -> RecurrenceRule.Once
        }
    }

    /** ¿Le queda alguna vez por sonar? Una alarma única en el pasado se guarda pero nunca suena. */
    fun willEverRing(date: LocalDateTime, rule: RecurrenceRule, now: LocalDateTime, zone: ZoneId): Boolean =
        RecurrenceEngine.nextOccurrences(
            rule, date.atZone(zone).toInstant().toEpochMilli(), 1, now.atZone(zone).toInstant().toEpochMilli(), zone,
        ).isNotEmpty()

    /**
     * Fechas del modelo: a veces ISO con zona y a veces «ingenuas». Las
     * ingenuas son hora LOCAL: sin esto, «a las 2 pm» en UTC−5 quedaba a las 9.
     */
    fun parseIsoDate(iso: String?, zone: ZoneId): LocalDateTime? {
        val s = iso?.trim()?.removeSuffix(".000")?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return OffsetDateTime.parse(s).atZoneSameInstant(zone).toLocalDateTime() }
        val normalized = s.replace(' ', 'T')
        runCatching { return LocalDateTime.parse(normalized) }
        runCatching { return LocalDateTime.parse("${normalized}:00") }
        runCatching { return LocalDate.parse(s).atStartOfDay() }
        return null
    }

    // region Ayudantes de texto de `AssistantService` (iOS)

    private val rangeMonths = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|" +
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "janeiro|fevereiro|março|maio|junho|julho|setembro|outubro|novembro|dezembro"
    private val rangeRegex = Regex(
        """\b(de|from|das)\s+(\d{1,2})(?::(\d{2}))?\s*(?:a|to|until|till|-|às|as|até|ate)\s*(\d{1,2})(?::(\d{2}))?(?!\d|:|\s*(?:de\s+)?(?:$rangeMonths))""" +
            """(\s*(?:de la (?:mañana|madrugada|tarde|noche)|en la (?:mañana|tarde|noche)|in the (?:morning|afternoon|evening)|da (?:manhã|madrugada|tarde|noite)|a\.?\s?m\b\.?|p\.?\s?m\b\.?))?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Escribe en 24 h los rangos antes de que los vea el modelo: «de 2 a 4 y
     * de 9 a 11» → «de 14:00 a 16:00 y de 21:00 a 23:00». Sin mañana/tarde/am/pm,
     * un rango que empieza de 1 a 5 es de tarde, y uno que empezaría antes que
     * el anterior es el de la noche. Lo demás queda como está.
     */
    fun normalizeTimeRanges(text: String): String {
        val result = StringBuilder()
        var cursor = 0
        var previousStart: Int? = null
        for (match in rangeRegex.findAll(text)) {
            val g = match.groups
            val word = g[1]?.value ?: continue
            val startHour = g[2]?.value?.toIntOrNull() ?: continue
            val endHour = g[4]?.value?.toIntOrNull() ?: continue
            if (startHour > 23 || endHour > 23) continue
            val startMinute = g[3]?.value?.toIntOrNull() ?: 0
            val endMinute = g[5]?.value?.toIntOrNull() ?: 0
            val qualifier = (g[6]?.value ?: "").lowercase()
            val saysMorning = listOf("mañana", "madrugada", "morning", "manhã").any { qualifier.contains(it) } ||
                Regex("""^\s*a\.?\s?m""").containsMatchIn(qualifier)
            val saysEvening = listOf("tarde", "noche", "afternoon", "evening", "noite").any { qualifier.contains(it) } ||
                Regex("""^\s*p\.?\s?m""").containsMatchIn(qualifier)

            val afternoon = when {
                startHour > 12 || endHour > 12 || saysMorning -> false
                saysEvening -> true
                previousStart != null && startHour * 60 + startMinute < previousStart -> true
                startHour in 1..5 -> true
                else -> {
                    // «de 9 a 11» solo puede ser cualquiera de las dos: lo decide quien lea.
                    previousStart = startHour * 60 + startMinute
                    continue
                }
            }
            val start = if (afternoon && startHour < 12) startHour + 12 else startHour
            val end = if (afternoon && endHour < 12 && endHour + 12 >= start) endHour + 12 else endHour
            previousStart = start * 60 + startMinute
            if (!afternoon && !saysMorning) continue

            val connector = when (word.lowercase()) {
                "from" -> "to"
                "das" -> "às"
                else -> "a"
            }
            result.append(text, cursor, match.range.first)
            result.append(String.format(Locale.ROOT, "%s %02d:%02d %s %02d:%02d", word, start, startMinute, connector, end, endMinute))
            cursor = match.range.last + 1
        }
        if (cursor == 0) return text
        result.append(text.substring(cursor))
        return result.toString()
    }

    private val startsRegex = Regex("""\b(?:de|from|das)\s+(\d{1,2}):(\d{2})\s+(?:a|to|às)\s+\d{1,2}:\d{2}""", RegexOption.IGNORE_CASE)

    /** Inicios (minutos desde medianoche) de los rangos ya en 24 h: «de 2 a 4 y de 9 a 11» → [840, 1260]. */
    fun rangeStartTimes(text: String): List<Int> =
        startsRegex.findAll(normalizeTimeRanges(text)).mapNotNull { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val min = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (h < 24 && min < 60) h * 60 + min else null
        }.toList()

    /** Avisos pedidos, en minutos: «un día antes» → 1440, «media hora antes» → 30, «2 hours before» → 120. */
    fun advanceNoticeMinutes(text: String): List<Int> {
        val lower = fold(text)
        val minutes = mutableSetOf<Int>()
        val fixed = listOf(
            """\b(un|1) dia antes\b|\bel dia anterior\b|\b(a|one|1) day before\b|\bthe day before\b|\b(um|1) dia antes\b|\bna vespera\b|\bno dia anterior\b""" to 1440,
            """\b(una|1) hora antes\b|\b(an|one|1) hour before\b|\b(uma|1) hora antes\b""" to 60,
            """\bmedia hora antes\b|\bhalf an hour before\b|\bmeia hora antes\b""" to 30,
            """\b(una|1) semana antes\b|\b(a|one|1) week before\b|\b(uma|1) semana antes\b""" to 10080,
        )
        for ((pattern, value) in fixed) if (Regex(pattern).containsMatchIn(lower)) minutes += value
        val counted = listOf(
            """\b(\d{1,3}) (?:minutos|min|minutes|mins)\s+(?:antes|before)\b""" to 1,
            """\b(\d{1,2}) (?:horas|hours)\s+(?:antes|before)\b""" to 60,
            """\b(\d{1,2}) (?:dias|days)\s+(?:antes|before)\b""" to 1440,
        )
        for ((pattern, unit) in counted) {
            for (m in Regex(pattern).findAll(lower)) {
                val count = m.groupValues[1].toIntOrNull() ?: continue
                if (count > 0) minutes += count * unit
            }
        }
        return minutes.sorted()
    }

    /**
     * ¿Nombró UN día que viene («este sábado», «el sábado 14», «this Friday») y
     * ninguno que se repite («los sábados», «cada lunes», «every Friday»)? El
     * modelo convertía «este sábado 14» en semanal; deciden las palabras.
     */
    fun meansSingleDay(text: String): Boolean {
        val t = fold(text)
        val days = "lunes|martes|miercoles|jueves|viernes|sabado|domingo|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "segunda|terca|quarta|quinta|sexta"
        val repeating = listOf(
            """\b(los|cada|todas as|todos os|toda|todo|as|nas|nos|aos)\s+(?:$days)""",
            """\b(sabados|domingos|mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays|segundas|tercas|quartas|quintas|sextas)\b""",
            """\bevery\s+(?:$days|day|week)\b""",
            """\b(entre semana|fines? de semana|cada semana|todas las semanas|todos los dias|cada dia|weekdays|weekends?|todos os dias|todo dia|toda semana|fins de semana)\b""",
        )
        val single = listOf(
            """\b(este|esta|el|proximo|proxima|this|next|on|nesta|neste|na|no)\s+(?:$days)\b""",
            """\b(?:$days)\s+\d{1,2}\b""",
        )
        return single.any { Regex(it).containsMatchIn(t) } && repeating.none { Regex(it).containsMatchIn(t) }
    }

    /**
     * Las próximas dos semanas como `2026-11-14 = Saturday / sábado / sábado`.
     * El modelo pequeño no sabe hacer cuentas de calendario: con la tabla, «el
     * sábado 14» pasa a ser una búsqueda.
     */
    fun upcomingDaysText(today: LocalDate, days: Int = 14): String =
        (0 until days).joinToString("\n") { offset ->
            val day = today.plusDays(offset.toLong())
            val marker = when (offset) {
                0 -> " (today)"
                1 -> " (tomorrow)"
                else -> ""
            }
            val names = listOf(AssistantLang.EN, AssistantLang.ES, AssistantLang.PT).joinToString(" / ") { day.dayOfWeek.getDisplayName(TextStyle.FULL, it.locale) }
            "- $day = $names$marker"
        }

    // endregion
}
