package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.Weekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * El motor que siempre está: entiende en español, inglés y portugués las
 * formas comunes de pedir una alarma, listarlas o borrarlas/apagarlas, sin
 * modelo ni red. Aplica las mismas reglas que `CreateReminderTool` de iOS:
 * el título sin palabras de fecha u hora; la repetición sale de las palabras de
 * frecuencia y nunca del nombre («mi daily todos los lunes» → semanal, lunes);
 * los días nombrados en plural son semanales; la fecha siempre en el futuro;
 * los cumpleaños a las 9 con aviso un día antes.
 *
 * Trabaja sobre el texto plegado (minúsculas, sin tildes) que mide lo mismo que
 * el original: cada dato reconocido se «tacha» y lo que queda es el título.
 */
object AssistantParser {

    data class Context(
        /** Nombres de las categorías propias: si el mensaje nombra una, gana a las de fábrica. */
        val customCategories: List<String> = emptyList(),
        /** Títulos de las alarmas existentes, para distinguir «cancela la reunión» de «recuérdame cancelar…». */
        val existingTitles: List<String> = emptyList(),
        /** El turno anterior fue un listado: «¿y mañana?» sigue siendo listar. */
        val lastWasList: Boolean = false,
    )

    fun parse(text: String, now: LocalDateTime, lang: AssistantLang, context: Context = Context()): Interpretation {
        val raw = text.trim()
        if (raw.isEmpty()) return Interpretation(AssistantAction.Unknown, confident = true)
        val f = fold(raw)
        if (GREETING_ONLY.matches(f)) return Interpretation(AssistantAction.Greeting, true)
        if (THANKS_ONLY.matches(f)) return Interpretation(AssistantAction.Thanks, true)
        val body = raw.substring(LEADING_FILLER.find(f)?.let { it.range.last + 1 } ?: 0).trim()
        if (body.isEmpty()) return Interpretation(AssistantAction.Greeting, true)
        val fb = fold(body)
        if (fb.length < 60 && HELP.containsMatchIn(fb)) return Interpretation(AssistantAction.Help, true)
        manage(body, now, lang, context)?.let { return it }
        list(body, now, lang, context)?.let { return it }
        return create(body, now, lang, context)
    }

    /** Los datos sueltos del mensaje, para validar lo que propone el modelo con las mismas reglas. */
    fun signals(text: String, now: LocalDateTime, lang: AssistantLang, customCategories: List<String> = emptyList()): Signals {
        val scan = Scan(text.trim())
        return extract(scan, now, lang, customCategories)
    }

    // region Crear

    private fun create(body: String, now: LocalDateTime, lang: AssistantLang, context: Context): Interpretation {
        val scan = Scan(body)
        val s = extract(scan, now, lang, context.customCategories)
        stripCommands(scan, s)
        val title = title(scan)
        val action = buildCreate(s, title, now)
        val hasSignal = s.hasDateOrTime || s.recurrence != null || s.commandFound || s.notices.isNotEmpty() ||
            s.categoryFromKeyword
        if (action == null) {
            // Sin hora: si se entiende QUÉ recordar se pregunta la hora; si no, no se entendió.
            return if (hasSignal && (title != null || s.hasDateOrTime)) {
                Interpretation(AssistantAction.NeedTime(title, s.category), confident = false)
            } else {
                Interpretation(AssistantAction.Unknown, confident = false)
            }
        }
        // Números o «después de comer» en el título: algo de la hora no se entendió.
        val leftovers = title != null && UNPARSED_TIMEISH.containsMatchIn(fold(title))
        return Interpretation(action, confident = !leftovers)
    }

    /** `null` si falta la hora (y no es un cumpleaños/aniversario, que va a las 9). */
    internal fun buildCreate(s: Signals, title: String?, now: LocalDateTime): AssistantAction.Create? {
        s.relativeMinutes?.let { minutes ->
            val date = now.plusMinutes(minutes).withSecond(0).withNano(0)
            return AssistantAction.Create(
                AlarmDraft(title, date, RecurrenceRule.Once, s.category, leads(s), wakeUp = s.wakeUp),
            )
        }
        val times = s.times.ifEmpty {
            if (s.category.isDayEvent) listOf(LocalTime.of(9, 0)) else return null
        }
        val time = times.first()

        var rule = s.recurrence?.toRule() ?: RecurrenceRule.Once
        val weekdayMentions = s.dates.filterIsInstance<DateSpec.WeekdayNext>()
        // «cada 2 semanas el martes»: el día suelto le da los días a la repetición semanal.
        if (rule is RecurrenceRule.Weekly && rule.weekdays.isEmpty() && weekdayMentions.isNotEmpty()) {
            rule = rule.copy(weekdays = weekdayMentions.map { it.weekday }.toSet())
        }
        val dateSpecs = if (rule is RecurrenceRule.Weekly && rule.weekdays.isNotEmpty()) {
            s.dates.filterNot { it is DateSpec.WeekdayNext }
        } else {
            s.dates
        }
        // Un cumpleaños con fecha del calendario se repite cada año aunque no lo diga:
        // nadie quiere que el de su mamá suene una sola vez.
        if (rule == RecurrenceRule.Once && s.category.isDayEvent && dateSpecs.any { it is DateSpec.Calendar }) {
            rule = RecurrenceRule.Yearly()
        }
        val monthDay = s.monthDay
        val main = when {
            monthDay != null -> DateSpec.DayOfMonth(monthDay).resolve(time, now)
            dateSpecs.isNotEmpty() -> dateSpecs.first().resolve(time, now)
            // Sin día: la próxima vez que llega esa hora (hoy o mañana), también para «cada 3 días».
            else -> now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }
        }
        val resolved = DraftRules.resolveDate(main, rule, now)
        val extras = mutableListOf<LocalDateTime>()
        for (t in times.drop(1)) extras += resolved.toLocalDate().atTime(t)
        for (spec in dateSpecs.drop(1)) {
            val d = DraftRules.resolveDate(spec.resolve(time, now), rule, now)
            extras += d
            for (t in times.drop(1)) extras += d.toLocalDate().atTime(t)
        }
        return AssistantAction.Create(
            AlarmDraft(
                title = title,
                date = resolved,
                recurrence = rule,
                category = s.category,
                leadMinutes = leads(s),
                extraDates = extras.distinct().filter { it != resolved },
                wakeUp = s.wakeUp,
            ),
        )
    }

    /** Como iOS: lo que se pidió «antes» se suma a «a la hora»; los cumpleaños avisan además un día antes. */
    private fun leads(s: Signals): List<Int>? = when {
        s.notices.isNotEmpty() -> (listOf(0) + s.notices).distinct()
        s.category.isDayEvent -> listOf(0, 1440)
        else -> null
    }

    // endregion

    // region Listar

    private fun list(body: String, now: LocalDateTime, lang: AssistantLang, context: Context): Interpretation? {
        val f = fold(body)
        val question = LIST_START.containsMatchIn(f) || (LIST_START_HAVE.containsMatchIn(f) && (body.contains('?') || body.contains('¿')))
        val mine = MY_ALARMS.matches(f)
        val followUp = RANGE_ONLY.matches(f) && (context.lastWasList || f.trimStart('¿', ' ').startsWith("y ") ||
            f.trimStart().startsWith("and ") || f.trimStart().startsWith("e "))
        if (!question && !mine && !followUp) return null
        if (question && !mine && !followUp) {
            if (CREATE_HINT.containsMatchIn(f)) return null
            val probe = Scan(body)
            val s = extract(probe, now, lang, emptyList())
            if (s.times.isNotEmpty() || s.relativeMinutes != null) return null
            if (FIND_START.containsMatchIn(f)) {
                stripCommands(probe, s)
                val query = cleanQuery(probe.residue().replace(FIND_WORDS, " "))
                if (query.isNotEmpty()) return Interpretation(AssistantAction.Manage(ManageOp.FIND, query), true)
            }
            if (!AGENDA.containsMatchIn(f) && rangeOf(f, now, lang) == null) return null
        }
        val range = rangeOf(f, now, lang) ?: ListRange.All
        return Interpretation(AssistantAction.ListAlarms(range, categoryFilter(f, lang)), confident = true)
    }

    private fun rangeOf(f: String, now: LocalDateTime, lang: AssistantLang): ListRange? {
        val today = now.toLocalDate()
        return when {
            Regex("""\b(?:pasado manana|day after tomorrow|depois de amanha)\b""").containsMatchIn(f) -> ListRange.Day(today.plusDays(2))
            Regex("""\b(?:tomorrow|amanha)\b""").containsMatchIn(f) -> ListRange.Tomorrow
            Regex("""\bmanana\b""").findAll(f).any { m -> !Regex("""(?:de|por|en)\s+la\s+$|\bla\s+$|\besta\s+$""").containsMatchIn(f.substring(0, m.range.first)) } -> ListRange.Tomorrow
            Regex("""\b(?:hoy|today|hoje|tonight|esta noche|esta noite)\b""").containsMatchIn(f) -> ListRange.Today
            Regex("""\b(?:proxima semana|semana que viene|next week|semana que vem)\b""").containsMatchIn(f) -> ListRange.NextWeek
            Regex("""\b(?:esta semana|this week|la semana|essa semana|nesta semana|the week|semana|week)\b""").containsMatchIn(f) -> ListRange.ThisWeek
            Regex("""\b(?:este mes|this month|el mes|esse mes|neste mes|mes|month)\b""").containsMatchIn(f) -> ListRange.ThisMonth
            Regex("""\b(?:todas|todos|all|everything|todo)\b""").containsMatchIn(f) -> ListRange.All
            else -> {
                val scan = Scan(f)
                val s = extract(scan, now, lang, emptyList())
                s.dates.firstOrNull()?.resolve(LocalTime.of(23, 59), now)?.toLocalDate()?.let { ListRange.Day(it) }
            }
        }
    }

    private fun categoryFilter(f: String, lang: AssistantLang): ReminderCategory? = when {
        Regex("""\b(?:cumpleanos|cumples|birthdays?|bdays?|aniversarios? de casamento|anniversar(?:y|ies)|bodas)\b""").find(f)?.value?.let {
            it.startsWith("anniversar") || it.startsWith("aniversario") || it == "bodas"
        } == true -> ReminderCategory.ANNIVERSARY
        Regex("""\b(?:cumpleanos|cumples|birthdays?|bdays?)\b""").containsMatchIn(f) -> ReminderCategory.BIRTHDAY
        Regex("""\baniversarios?\b""").containsMatchIn(f) ->
            if (lang == AssistantLang.PT) ReminderCategory.BIRTHDAY else ReminderCategory.ANNIVERSARY
        Regex("""\b(?:eventos?|events?|reuniones|reunion|meetings?|citas?|appointments?|reunioes|reuniao|compromissos?|consultas?)\b""").containsMatchIn(f) -> ReminderCategory.EVENT
        else -> null
    }

    // endregion

    // region Borrar, apagar, encender, cambiar

    private fun manage(body: String, now: LocalDateTime, lang: AssistantLang, context: Context): Interpretation? {
        val f = fold(body)
        val prefix = POLITE_PREFIX.find(f)?.let { it.range.last + 1 } ?: 0
        val rest = f.substring(prefix)
        val (op, verb) = verbs(lang).firstNotNullOfOrNull { (op, regex) -> regex.find(rest)?.let { op to it } } ?: return null
        val rawRest = body.substring(prefix + verb.range.last + 1)
        // «bórrala», «cámbiala»: el pronombre pegado al verbo apunta a la alarma de la que se habla.
        val pronoun = Regex("(?:la|lo|las|los)$").find(verb.value)?.let { verb.value.removeSuffix(it.value) in ENCLITIC_BASES } == true
        val scan = Scan(rawRest)
        val s = extract(scan, now, lang, context.customCategories)
        val query = cleanQuery(scan.residue())
        val hasAlarmNoun = ALARM_NOUN.containsMatchIn(fold(rawRest))
        val known = query.isNotEmpty() && context.existingTitles.any { titleMatches(it, query) }

        if (op == null) {
            // Cambiar: lo nuevo (hora, día, repetición, avisos) sale de los mismos extractores.
            val changes = UpdateChanges(
                time = s.times.firstOrNull() ?: s.relativeMinutes?.let { now.plusMinutes(it).toLocalTime().withSecond(0).withNano(0) },
                date = (s.dates.firstOrNull { it !is DateSpec.WeekdayNext || s.recurrence == null })
                    ?.resolve(s.times.firstOrNull() ?: LocalTime.of(23, 59), now)?.toLocalDate()
                    ?: s.relativeMinutes?.let { now.plusMinutes(it).toLocalDate() },
                recurrence = s.recurrence?.toRule()?.let { rule ->
                    val wd = s.dates.filterIsInstance<DateSpec.WeekdayNext>().map { it.weekday }.toSet()
                    if (rule is RecurrenceRule.Weekly && rule.weekdays.isEmpty() && wd.isNotEmpty()) rule.copy(weekdays = wd) else rule
                },
                leadMinutes = s.notices.takeIf { it.isNotEmpty() }?.let { (listOf(0) + it).distinct() },
            )
            val q = if (pronoun || query in PRONOUNS) "" else query
            return Interpretation(AssistantAction.Update(q, changes), confident = q.isNotEmpty() || pronoun)
        }
        // «Cancela la suscripción el 5 a las 10» es un recordatorio, no borrar una alarma.
        if (!hasAlarmNoun && !known && !pronoun && (s.times.isNotEmpty() || s.relativeMinutes != null)) return null
        val day = s.dates.firstOrNull()?.resolve(LocalTime.of(23, 59), now)?.toLocalDate()
        val q = if (pronoun || query in PRONOUNS) "" else query
        return Interpretation(AssistantAction.Manage(op, q, day), confident = q.isNotEmpty() || pronoun)
    }

    /** `null` en el par = cambiar. El orden importa: «apaga» borra en portugués y apaga en español. */
    private fun verbs(lang: AssistantLang): List<Pair<ManageOp?, Regex>> {
        val delete = ManageOp.DELETE to DELETE_VERBS
        val disable = ManageOp.DISABLE to DISABLE_VERBS
        val enable = ManageOp.ENABLE to ENABLE_VERBS
        val update = null to UPDATE_VERBS
        return if (lang == AssistantLang.PT) {
            listOf(ManageOp.DELETE to DELETE_VERBS_PT, disable, enable, delete, update)
        } else {
            listOf(disable, enable, delete, update)
        }
    }

    // endregion

    // region Extracción

    internal enum class Period { MORNING, AFTERNOON, NIGHT, NOON }

    internal sealed interface DateSpec {
        fun resolve(time: LocalTime, now: LocalDateTime): LocalDateTime

        data class Calendar(val month: Int, val day: Int, val year: Int?) : DateSpec {
            override fun resolve(time: LocalTime, now: LocalDateTime): LocalDateTime {
                fun at(year: Int): LocalDateTime {
                    val ym = YearMonth.of(year, month)
                    return ym.atDay(day.coerceIn(1, ym.lengthOfMonth())).atTime(time)
                }
                if (year != null) return at(year)
                val thisYear = at(now.year)
                // Sin año, la próxima vez que llega esa fecha.
                return if (thisYear.isAfter(now)) thisYear else at(now.year + 1)
            }
        }

        data class DayOfMonth(val day: Int) : DateSpec {
            override fun resolve(time: LocalTime, now: LocalDateTime): LocalDateTime {
                val start = YearMonth.from(now)
                for (k in 0L..24L) {
                    val ym = start.plusMonths(k)
                    if (day > ym.lengthOfMonth()) continue
                    val candidate = ym.atDay(day).atTime(time)
                    if (candidate.isAfter(now)) return candidate
                }
                return now.toLocalDate().atTime(time)
            }
        }

        data class WeekdayNext(val weekday: Weekday, val strict: Boolean, val dayNumber: Int?) : DateSpec {
            override fun resolve(time: LocalTime, now: LocalDateTime): LocalDateTime {
                val today = now.toLocalDate()
                if (dayNumber != null) {
                    // «el sábado 14»: el próximo 14 que cae sábado; si no hay, el próximo 14.
                    for (k in 0L..400L) {
                        val d = today.plusDays(k)
                        if (d.dayOfMonth == dayNumber && d.dayOfWeek == weekday.dayOfWeek && d.atTime(time).isAfter(now)) return d.atTime(time)
                    }
                    return DayOfMonth(dayNumber).resolve(time, now)
                }
                for (k in (if (strict) 1L else 0L)..14L) {
                    val d = today.plusDays(k)
                    if (d.dayOfWeek == weekday.dayOfWeek && d.atTime(time).isAfter(now)) return d.atTime(time)
                }
                return today.atTime(time)
            }
        }

        data class Relative(val days: Long) : DateSpec {
            override fun resolve(time: LocalTime, now: LocalDateTime): LocalDateTime = now.toLocalDate().plusDays(days).atTime(time)
        }
    }

    internal data class RecurrenceSpec(
        val kind: String,
        val interval: Int = 1,
        val weekdays: Set<Weekday> = emptySet(),
    ) {
        fun toRule(): RecurrenceRule = DraftRules.recurrence(kind, weekdays, interval)
    }

    /** Todo lo que se reconoció en el mensaje (sin el título). */
    class Signals internal constructor(val lang: AssistantLang) {
        internal val times = mutableListOf<LocalTime>()
        internal var relativeMinutes: Long? = null
        internal val dates = mutableListOf<DateSpec>()
        internal var monthDay: Int? = null
        internal var recurrence: RecurrenceSpec? = null
        internal val notices = mutableListOf<Int>()
        internal var period: Period? = null
        internal var wakeUp = false
        internal var midnight = false
        internal var commandFound = false
        internal var category: CategoryChoice = CategoryChoice.REMINDER
        internal var categoryFromKeyword = false
        internal val excluded = mutableSetOf<Weekday>()

        val hasDateOrTime: Boolean get() = times.isNotEmpty() || relativeMinutes != null || dates.isNotEmpty() || monthDay != null

        /** Las horas explícitas del mensaje (para corregir la del modelo). */
        val explicitTimes: List<LocalTime> get() = times.toList()
        val noticeMinutes: List<Int> get() = notices.toList()
        val explicitRecurrence: RecurrenceRule? get() = recurrence?.toRule()?.let { rule ->
            val wd = dates.filterIsInstance<DateSpec.WeekdayNext>().map { it.weekday }.toSet()
            if (rule is RecurrenceRule.Weekly && rule.weekdays.isEmpty() && wd.isNotEmpty()) rule.copy(weekdays = wd) else rule
        }
        val relative: Long? get() = relativeMinutes
        val categoryChoice: CategoryChoice get() = category
        val hasKeywordCategory: Boolean get() = categoryFromKeyword
        val saysMidnight: Boolean get() = midnight
        val isWakeUp: Boolean get() = wakeUp

        /** La fecha explícita del mensaje resuelta a una hora dada (para corregir la del modelo). */
        fun explicitDate(time: LocalTime, now: LocalDateTime): LocalDateTime? {
            monthDay?.let { return DateSpec.DayOfMonth(it).resolve(time, now) }
            val rule = explicitRecurrence
            val specs = if (rule is RecurrenceRule.Weekly && rule.weekdays.isNotEmpty()) dates.filterNot { it is DateSpec.WeekdayNext } else dates
            return specs.firstOrNull()?.resolve(time, now)
        }
    }

    private fun extract(scan: Scan, now: LocalDateTime, lang: AssistantLang, customs: List<String>): Signals {
        val s = Signals(lang)
        s.wakeUp = WAKE.containsMatchIn(scan.folded)
        s.midnight = MIDNIGHT_WORD.containsMatchIn(scan.folded)
        extractNotices(scan, s)
        extractRelative(scan, s)
        extractRecurrence(scan, s, lang)
        extractCalendarDates(scan, s, lang)
        extractTimes(scan, s, lang)
        extractDayWords(scan, s)
        extractWeekdays(scan, s, lang)
        extractDayOfMonth(scan, s)
        extractWeakFrequency(scan, s)
        detectCategory(scan.folded, s, lang, customs)
        return s
    }

    private fun extractNotices(scan: Scan, s: Signals) {
        for (m in scan.matches(LEAD_COUNT)) {
            val n = Vocab.number(m.g("n") ?: continue) ?: continue
            val unit = unitMinutes(m.g("u") ?: continue) ?: continue
            if (n <= 0) continue
            s.notices += n * unit
            scan.take(m.range)
        }
        for (m in scan.matches(LEAD_ADVANCE)) {
            val n = Vocab.number(m.g("n") ?: continue) ?: continue
            val unit = unitMinutes(m.g("u") ?: continue) ?: continue
            s.notices += n * unit
            scan.take(m.range)
        }
        for (m in scan.matches(LEAD_HALF)) {
            s.notices += 30
            scan.take(m.range)
        }
        for (m in scan.matches(LEAD_DAY_BEFORE)) {
            s.notices += 1440
            scan.take(m.range)
        }
        val unique = s.notices.distinct().sorted()
        s.notices.clear()
        s.notices += unique
    }

    private fun extractRelative(scan: Scan, s: Signals) {
        for (m in scan.matches(RELATIVE)) {
            val unit = unitMinutes(m.g("u") ?: continue) ?: continue
            val half = m.g("half") != null
            val n = if (half) 0.5 else (Vocab.number(m.g("n") ?: continue) ?: continue).toDouble()
            val plusHalf = if (m.g("plus") != null) 0.5 else 0.0
            val minutes = ((n + plusHalf) * unit).toLong()
            if (minutes <= 0) continue
            if (unit >= 1440) {
                s.dates += DateSpec.Relative(minutes / 1440)
            } else {
                s.relativeMinutes = (s.relativeMinutes ?: 0) + minutes
            }
            scan.take(m.range)
        }
    }

    private fun extractRecurrence(scan: Scan, s: Signals, lang: AssistantLang) {
        val wdAll = Vocab.weekdays(lang)
        // «todos los días menos los fines de semana»: se leen antes que los días, que si no se sumarían.
        val except = Regex(
            """\b(?:menos|excepto|salvo|except|but not|exceto|tirando|sin contar)\s+(?:(?:los|el|la|las|on|os|o|a|as|aos|nos)\s+)?(?<what>fines\s+de\s+semana|fin\s+de\s+semana|weekends?|fins\s+de\s+semana|fim\s+de\s+semana|(?:$wdAll)(?:(?:\s*(?:,|\by\b|\be\b|\band\b|\bor\b|\bni\b)\s*|\s+)(?:(?:los|el|os|o)\s+)?(?:$wdAll))*)\b""",
        )
        for (m in scan.matches(except)) {
            val what = m.g("what")!!
            s.excluded += if (what.contains("seman") || what.startsWith("weekend")) {
                setOf(Weekday.SATURDAY, Weekday.SUNDAY)
            } else {
                Regex(wdAll).findAll(what).mapNotNull { Vocab.weekdayOf(it.value) }.toSet()
            }
            scan.take(m.range)
        }
        for (regex in MONTHLY_DAY) {
            for (m in scan.matches(regex)) {
                val d = m.g("d")?.toIntOrNull()?.takeIf { it in 1..31 } ?: continue
                s.recurrence = RecurrenceSpec("monthly", s.recurrence?.interval ?: 1)
                s.monthDay = d
                scan.take(m.range)
            }
        }
        for (m in scan.matches(INTERVAL)) {
            val n = Vocab.number(m.g("n") ?: continue)?.takeIf { it in 1..99 } ?: continue
            val kind = when (m.g("u")!!.take(3)) {
                "dia", "day" -> "daily"
                "sem", "wee" -> "weekly"
                "mes", "mon" -> "monthly"
                else -> "yearly"
            }
            s.recurrence = RecurrenceSpec(kind, n, s.recurrence?.weekdays.orEmpty())
            scan.take(m.range)
        }
        for (m in scan.matches(PERIODIC_WORDS)) {
            val w = m.value
            s.recurrence = when {
                w.startsWith("quin") || w.startsWith("biweek") || w.contains("fortnight") -> RecurrenceSpec("weekly", 2, s.recurrence?.weekdays.orEmpty())
                w.startsWith("bimes") || w.startsWith("bimonth") -> RecurrenceSpec("monthly", 2)
                w.startsWith("trimes") || w.startsWith("quarter") -> RecurrenceSpec("monthly", 3)
                else -> RecurrenceSpec("monthly", 6)
            }
            scan.take(m.range)
        }
        val wd = Vocab.weekdays(lang)
        val weekdayRange = Regex("""\b(?:(?:de|from|da|desde)\s+)?(?<a>$wd)\s+(?:a|to|through|thru|till|until|-|ate|al)\s+(?<b>$wd)\b""")
        for (m in scan.matches(weekdayRange)) {
            val a = Vocab.weekdayOf(m.g("a")!!) ?: continue
            val b = Vocab.weekdayOf(m.g("b")!!) ?: continue
            val days = mutableSetOf<Weekday>()
            var d: DayOfWeek = a.dayOfWeek
            for (i in 0..7) {
                days += Weekday.of(d)
                if (d == b.dayOfWeek) break
                d = d.plus(1)
            }
            addWeekdays(s, days)
            scan.take(m.range)
        }
        for (m in scan.matches(WORKDAYS)) {
            addWeekdays(s, Weekday.workdays)
            scan.take(m.range)
        }
        for (m in scan.matches(WEEKENDS)) {
            val singular = !m.value.contains("fines") && !m.value.contains("weekends") && !m.value.contains("fins")
            val repeatingPrefix = Regex("""^(?:cada|every|todo|todos|los|on|nos|aos)\b""").containsMatchIn(m.value)
            if (singular && !repeatingPrefix) continue
            addWeekdays(s, setOf(Weekday.SATURDAY, Weekday.SUNDAY))
            scan.take(m.range)
        }
        val list = """(?:$wd)(?:(?:\s*(?:,|\by\b|\be\b|\band\b|&|\bor\b)\s*|\s+)(?:(?:los|las|as|os|el|la|the|on|nas|nos|aos)\s+)?(?:$wd))*"""
        val setRegex = Regex("""\b(?<p>todos\s+los|todas\s+las|cada|los|every|each|on|todas\s+as|todos\s+os|toda|todo|as|nas|nos|aos)\s+(?<list>$list)\b""")
        for (m in scan.matches(setRegex)) {
            val words = Regex(wd).findAll(m.g("list")!!).map { it.value }.toList()
            val prefix = m.g("p")!!
            val plural = words.any(Vocab::isPluralWeekday)
            if ((prefix == "on" || prefix == "as") && !plural) continue
            val days = words.mapNotNull(Vocab::weekdayOf).toSet()
            if (days.isEmpty()) continue
            addWeekdays(s, days)
            scan.take(m.range)
        }
        val plurals = "sabados|domingos|mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays" +
            if (lang == AssistantLang.PT) "|segundas|tercas|quartas|quintas|sextas" else ""
        val pluralRegex = Regex("""\b(?<list>(?:$plurals)(?:\s*(?:,|\by\b|\be\b|\band\b|&)\s*(?:(?:los|las|as|os|on)\s+)?(?:$wd))*)\b""")
        for (m in scan.matches(pluralRegex)) {
            val days = Regex(wd).findAll(m.value).mapNotNull { Vocab.weekdayOf(it.value) }.toSet()
            if (days.isEmpty()) continue
            addWeekdays(s, days)
            scan.take(m.range)
        }
        // «martes y jueves» sin artículo es un horario que se repite; «el martes y el jueves», dos días sueltos.
        val bareList = Regex("""\b(?<list>(?:$wd)(?:(?:\s*(?:,|\by\b|\be\b|\band\b|&)\s*|\s+)(?:$wd))+)\b""")
        for (m in scan.matches(bareList)) {
            val before = scan.folded.substring(0, m.range.first)
            if (SINGLE_PREFIX_BEFORE.containsMatchIn(before)) continue
            val days = Regex(wd).findAll(m.value).mapNotNull { Vocab.weekdayOf(it.value) }.toSet()
            if (days.size < 2) continue
            addWeekdays(s, days)
            scan.take(m.range)
        }
        for ((regex, kind, period) in STRONG_FREQUENCY) {
            for (m in scan.matches(regex)) {
                if (period != null) s.period = period
                if (s.recurrence == null || (kind == "weekly" && s.recurrence?.kind == "weekly")) {
                    s.recurrence = RecurrenceSpec(kind, s.recurrence?.interval ?: 1, s.recurrence?.weekdays.orEmpty())
                } else if (s.recurrence?.kind != kind && s.recurrence?.weekdays.isNullOrEmpty() && s.monthDay == null) {
                    // La frase más explícita gana; con días nombrados, «cada semana» ya está dicho.
                    s.recurrence = RecurrenceSpec(kind, s.recurrence?.interval ?: 1)
                }
                scan.take(m.range)
            }
        }
        if (s.excluded.isNotEmpty()) {
            val current = s.recurrence
            val base = if (current?.kind == "weekly" && current.weekdays.isNotEmpty()) current.weekdays else Weekday.entries.toSet()
            val days = base - s.excluded
            if (days.isNotEmpty() && (current == null || current.kind == "daily" || current.kind == "weekly")) {
                s.recurrence = RecurrenceSpec("weekly", if (current?.kind == "weekly") current.interval else 1, days)
            }
        }
    }

    private fun addWeekdays(s: Signals, days: Set<Weekday>) {
        val current = s.recurrence
        s.recurrence = if (current != null && current.kind == "weekly") {
            current.copy(weekdays = current.weekdays + days)
        } else {
            RecurrenceSpec("weekly", current?.interval?.takeIf { current.kind == "weekly" } ?: 1, days)
        }
    }

    private fun extractCalendarDates(scan: Scan, s: Signals, lang: AssistantLang) {
        val wd = Vocab.weekdays(lang)
        val dayFirst = Regex(
            """\b(?:(?:el|este|o|no|on|para el|para o)\s+)?(?:(?:$wd),?\s+)?(?:(?:el|o|the)\s+)?(?:dia\s+)?(?<d>\d{1,2})(?:º|o|st|nd|rd|th)?\s+(?:de\s+|of\s+)?(?<m>${Vocab.MONTHS})\b(?:,?\s+(?:de\s+|del\s+)?(?<y>\d{4}))?""",
        )
        for (m in scan.matches(dayFirst)) {
            val day = m.g("d")!!.toInt()
            val month = Vocab.monthOf(m.g("m")!!) ?: continue
            if (day !in 1..31) continue
            s.dates += DateSpec.Calendar(month, day, m.g("y")?.toIntOrNull())
            scan.take(m.range)
        }
        val monthFirst = Regex(
            """\b(?:(?:on|this|el)\s+)?(?:(?:$wd),?\s+)?(?:the\s+)?(?<m>january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|apr|aug|sept|oct|nov|dec)\s+(?<d>\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(?<y>\d{4}))?""",
        )
        for (m in scan.matches(monthFirst)) {
            val day = m.g("d")!!.toInt()
            val month = Vocab.monthOf(m.g("m")!!) ?: continue
            if (day !in 1..31) continue
            s.dates += DateSpec.Calendar(month, day, m.g("y")?.toIntOrNull())
            scan.take(m.range)
        }
        val numeric = Regex("""\b(?:(?:el|on|the|o|dia|el dia|o dia)\s+)?(?<a>\d{1,2})/(?<b>\d{1,2})(?:/(?<y>\d{2,4}))?\b""")
        for (m in scan.matches(numeric)) {
            val a = m.g("a")!!.toInt()
            val b = m.g("b")!!.toInt()
            // En inglés va primero el mes, salvo que no pueda serlo (15/3).
            val (day, month) = if (lang == AssistantLang.EN && a <= 12 && b <= 31) b to a else a to b
            if (day !in 1..31 || month !in 1..12) continue
            val year = m.g("y")?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            s.dates += DateSpec.Calendar(month, day, year)
            scan.take(m.range)
        }
    }

    private fun extractTimes(scan: Scan, s: Signals, lang: AssistantLang) {
        // Rangos primero: «de 2 a 4 y de 9 a 11» suena a las 14:00 y a las 21:00 (regla de iOS).
        var previousStart: Int? = null
        for (m in scan.matches(TIME_RANGE)) {
            val sh = m.g("sh")!!.toInt()
            val eh = m.g("eh")!!.toInt()
            if (sh > 23 || eh > 23) continue
            val sm = m.g("sm")?.toIntOrNull() ?: 0
            if (sm > 59) continue
            val qualifier = periodOf(m.g("q")) ?: ampmPeriod(m.g("ap")) ?: ampmPeriod(m.g("sap"))
            val morning = qualifier == Period.MORNING
            val evening = qualifier == Period.AFTERNOON || qualifier == Period.NIGHT
            val afternoon = when {
                sh > 12 || eh > 12 || morning -> false
                evening -> true
                previousStart != null && sh * 60 + sm < previousStart!! -> true
                sh in 1..5 -> true
                s.period == Period.AFTERNOON || s.period == Period.NIGHT -> true
                else -> false
            }
            val start = if (afternoon && sh < 12) sh + 12 else if (morning && sh == 12) 0 else sh
            previousStart = start * 60 + sm
            s.times += LocalTime.of(start, sm)
            scan.take(m.range)
        }
        for (m in scan.matches(timeAt(lang))) {
            val h = Vocab.number(m.g("h")!!) ?: continue
            var minute = m.g("m")?.toIntOrNull() ?: m.g("m3")?.toIntOrNull() ?: 0
            var hour = h
            m.g("plus")?.let { p ->
                minute = when (p) {
                    "media", "meia" -> 30
                    "cuarto", "quinze" -> 15
                    else -> p.toIntOrNull() ?: 0
                }
            }
            m.g("minus")?.let { p ->
                val sub = if (p == "cuarto") 15 else p.toIntOrNull() ?: 0
                hour = (hour + 23) % 24
                minute = 60 - sub
            }
            val literal = m.g("hsuffix") != null || m.g("h")!!.startsWith("0")
            val t = resolveTime(hour, minute, m.g("ap"), m.g("q"), literal, s) ?: continue
            s.times += t
            scan.take(m.range)
        }
        for (m in scan.matches(TIME_HM)) {
            val h = m.g("h")!!.toInt()
            val minute = (m.g("m") ?: m.g("m2"))?.toIntOrNull() ?: 0
            val literal = m.g("hh") != null || m.g("h")!!.startsWith("0")
            val t = resolveTime(h, minute, m.g("ap"), m.g("q"), literal, s) ?: continue
            s.times += t
            scan.take(m.range)
        }
        for (m in scan.matches(TIME_AMPM)) {
            val t = resolveTime(m.g("h")!!.toInt(), 0, m.g("ap"), m.g("q"), false, s) ?: continue
            s.times += t
            scan.take(m.range)
        }
        for (m in scan.matches(TIME_QUAL)) {
            val t = resolveTime(m.g("h")!!.toInt(), m.g("m")?.toIntOrNull() ?: 0, null, m.g("q"), false, s) ?: continue
            s.times += t
            scan.take(m.range)
        }
        for (m in scan.matches(NOON_MIDNIGHT)) {
            val w = m.g("w")!!
            s.times += if (w.startsWith("medi") && w.contains("noche") || w == "midnight" || w.startsWith("meia")) LocalTime.MIDNIGHT else LocalTime.NOON
            scan.take(m.range)
        }
    }

    /** La hora de reloj según am/pm, «de la tarde», la pista del día y, si no hay nada, la regla de iOS. */
    private fun resolveTime(h: Int, m: Int, ap: String?, q: String?, literal24: Boolean, s: Signals): LocalTime? {
        if (h > 23 || m > 59 || m < 0) return null
        val apPeriod = ampmPeriod(ap)
        val qPeriod = periodOf(q)
        val hour = when {
            apPeriod == Period.AFTERNOON -> if (h in 1..11) h + 12 else if (h == 12) 12 else return null
            apPeriod == Period.MORNING -> if (h == 12) 0 else if (h <= 12) h else return null
            qPeriod == Period.NOON -> if (h == 12 || h == 0) 12 else h
            qPeriod == Period.MORNING -> if (h == 12) 0 else h
            qPeriod == Period.AFTERNOON -> if (h in 1..11) h + 12 else h
            // «a la 1 de la noche» es de madrugada; «las 12 de la noche», medianoche.
            qPeriod == Period.NIGHT -> when (h) {
                12 -> 0
                in 1..3 -> h
                in 4..11 -> h + 12
                else -> h
            }
            h >= 13 || h == 0 || literal24 -> h
            s.wakeUp -> h
            s.period == Period.MORNING -> if (h == 12) 12 else h
            s.period == Period.AFTERNOON || s.period == Period.NIGHT -> if (h in 1..11) h + 12 else h
            // Sin pistas, de 1 a 5 es de tarde: nadie pide «la reunión a las 3» de madrugada.
            h in 1..5 -> h + 12
            else -> h
        }
        return LocalTime.of(hour, m)
    }

    private fun ampmPeriod(ap: String?): Period? = when {
        ap == null -> null
        ap.startsWith("a") -> Period.MORNING
        ap.startsWith("p") -> Period.AFTERNOON
        else -> null
    }

    private fun periodOf(q: String?): Period? {
        val t = q?.trim() ?: return null
        return when {
            t.contains("medi") || t.contains("meio") -> Period.NOON
            t.contains("manana") || t.contains("madrugada") || t.contains("morning") || t.contains("manha") -> Period.MORNING
            t.contains("tarde") || t.contains("afternoon") -> Period.AFTERNOON
            t.contains("noche") || t.contains("evening") || t.contains("night") || t.contains("noite") -> Period.NIGHT
            else -> null
        }
    }

    private fun extractDayWords(scan: Scan, s: Signals) {
        for ((regex, days, period) in DAY_PERIODS) {
            for (m in scan.matches(regex)) {
                if (days != null) s.dates += DateSpec.Relative(days)
                if (period != null) {
                    s.period = period
                    applyPeriodToBareTimes(s, period)
                }
                scan.take(m.range)
            }
        }
        for (m in scan.matches(DAY_AFTER_TOMORROW)) {
            s.dates += DateSpec.Relative(2)
            scan.take(m.range)
        }
        for (m in scan.matches(TOMORROW)) {
            // «mañana» después de «de la/por la/esta» es la mañana (el período), no el día.
            if (m.value == "manana" && Regex("""(?:\b(?:de|por|en|toda|cada)\s+la|\blas|\besta|\btodas\s+las)\s+$""").containsMatchIn(scan.folded.substring(0, m.range.first))) continue
            s.dates += DateSpec.Relative(1)
            scan.take(m.range)
        }
        for (m in scan.matches(TODAY)) {
            s.dates += DateSpec.Relative(0)
            scan.take(m.range)
        }
    }

    /** «esta noche a las 9»: la pista llega después de la hora, así que se corrige la hora ya leída. */
    private fun applyPeriodToBareTimes(s: Signals, period: Period) {
        if (period != Period.AFTERNOON && period != Period.NIGHT) return
        for (i in s.times.indices) {
            val t = s.times[i]
            if (t.hour in 6..11) s.times[i] = t.plusHours(12)
        }
    }

    private fun extractWeekdays(scan: Scan, s: Signals, lang: AssistantLang) {
        val wd = Vocab.weekdays(lang)
        val single = Regex(
            """\b(?:(?<p>(?:el\s+|la\s+|o\s+|a\s+)?(?:proximo|proxima)|este|esta|el|la|this|on|next|coming|nesta|neste|na|no|nessa|nesse|essa|esse|o|a)\s+)?(?<w>$wd)\b(?:\s+(?<n>que viene|que vem|next))?(?:,?\s+(?:the\s+)?(?<d>\d{1,2})(?:st|nd|rd|th|º)?\b(?!\s*(?::|h\b|am\b|pm\b|a\.m|p\.m|de la|da )))?""",
        )
        for (m in scan.matches(single)) {
            val word = m.g("w")!!
            val weekday = Vocab.weekdayOf(word) ?: continue
            val prefix = m.g("p")
            if (Vocab.isPluralWeekday(word) && prefix == null) {
                addWeekdays(s, setOf(weekday))
            } else {
                val strict = prefix?.contains("proxim") == true || prefix == "next" || m.g("n") != null
                s.dates += DateSpec.WeekdayNext(weekday, strict, m.g("d")?.toIntOrNull()?.takeIf { it in 1..31 })
            }
            scan.take(m.range)
        }
    }

    private fun extractDayOfMonth(scan: Scan, s: Signals) {
        for (m in scan.matches(DAY_OF_MONTH)) {
            val d = m.g("d")!!.toInt()
            if (d !in 1..31) continue
            s.dates += DateSpec.DayOfMonth(d)
            scan.take(m.range)
        }
    }

    /**
     * «daily», «mensual»…: solo son la frecuencia si no hay otra frase y no van
     * detrás de un determinante. «mi daily» es el nombre de la reunión.
     */
    private fun extractWeakFrequency(scan: Scan, s: Signals) {
        for (m in scan.matches(WEAK_FREQUENCY)) {
            if (s.recurrence != null) continue
            val before = scan.folded.substring(0, m.range.first)
            if (DETERMINER_BEFORE.containsMatchIn(before)) continue
            val kind = when (m.value.take(3)) {
                "dai", "dia" -> "daily"
                "wee", "sem" -> "weekly"
                "mon", "men" -> "monthly"
                else -> "yearly"
            }
            s.recurrence = RecurrenceSpec(kind)
            scan.take(m.range)
        }
    }

    private fun detectCategory(f: String, s: Signals, lang: AssistantLang, customs: List<String>) {
        // Una categoría propia nombrada gana a las de fábrica (como pide la instrucción de iOS).
        customs.filter { fold(it).trim().length >= 3 }.sortedByDescending { it.length }.firstOrNull { name ->
            Regex("""\b${Regex.escape(fold(name).trim())}\b""").containsMatchIn(f)
        }?.let {
            s.category = CategoryChoice.Custom(it)
            s.categoryFromKeyword = true
            return
        }
        val category = when {
            ANNIVERSARY_WORDS.containsMatchIn(f) -> ReminderCategory.ANNIVERSARY
            BIRTHDAY_WORDS.containsMatchIn(f) -> ReminderCategory.BIRTHDAY
            // «aniversario» es aniversario en español y cumpleaños en portugués.
            Regex("""\baniversario\b""").containsMatchIn(f) ->
                if (lang == AssistantLang.PT) ReminderCategory.BIRTHDAY else ReminderCategory.ANNIVERSARY
            EVENT_WORDS.containsMatchIn(f) -> ReminderCategory.EVENT
            else -> null
        }
        if (category != null) {
            s.category = CategoryChoice.Builtin(category)
            s.categoryFromKeyword = true
        }
    }

    private fun stripCommands(scan: Scan, s: Signals) {
        for (regex in COMMANDS) {
            for (m in scan.matches(regex)) {
                s.commandFound = true
                scan.take(m.range, info = false)
            }
        }
        for (m in scan.matches(POLITE)) scan.take(m.range, info = false)
    }

    private fun unitMinutes(unit: String): Int? = when {
        unit.startsWith("min") -> 1
        unit.startsWith("h") -> 60
        unit.startsWith("d") -> 1440
        unit.startsWith("sem") || unit.startsWith("w") -> 10080
        else -> null
    }

    // endregion

    // region Título y búsqueda

    /**
     * El título es lo que queda sin tachar. Con varias oraciones («…de 9 a 11.
     * colócalo porfa») vale la que tenía la fecha/hora; si esa quedó vacía
     * («Recuérdame comprar leche. Mañana a las 8»), la primera con texto.
     */
    private fun title(scan: Scan): String? {
        val segments = scan.segments()
        val ordered = segments.filter { it.second } + segments.filterNot { it.second }
        for ((text, _) in ordered) {
            cleanTitle(text)?.let { return it }
        }
        return null
    }

    internal fun cleanTitle(text: String): String? {
        var t = text.replace(Regex("""[¿¡"“”«»()\[\]]"""), " ").replace(Regex("""\s+"""), " ").trim()
        var guard = 0
        while (guard++ < 12) {
            val before = t
            t = t.trim(' ', ',', ':', '-', '.', '!', '?', ';', '…')
            TITLE_LEAD.find(fold(t))?.let { t = t.substring(it.range.last + 1) }
            TITLE_TAIL.find(fold(t))?.let { t = t.substring(0, it.range.first) }
            if (t == before) break
        }
        t = t.trim()
        if (t.isEmpty() || !t.any { it.isLetter() }) return null
        if (t.length > 60) t = t.take(60).substringBeforeLast(' ').ifEmpty { t.take(60) }
        return t.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /** El nombre que busca «borra la alarma del dentista» → «dentista». */
    internal fun cleanQuery(text: String): String {
        var t = fold(text).replace(Regex("""[¿¡?!.,;:"“”«»()]"""), " ").replace(ALARM_NOUN, " ").replace(Regex("""\s+"""), " ").trim()
        var guard = 0
        while (guard++ < 10) {
            val before = t
            t = t.replace(QUERY_LEAD, "").replace(QUERY_TAIL, "").trim()
            if (t == before) break
        }
        return t
    }

    /** ¿El título corresponde a lo que se busca? Sin tildes ni mayúsculas, por palabras. */
    fun titleMatches(title: String, query: String): Boolean = matchScore(title, query) > 0

    /** 100 igual, 80 lo contiene, 60 todas las palabras, 30 alguna palabra. */
    fun matchScore(title: String, query: String): Int {
        val t = fold(title).trim()
        val q = fold(query).trim()
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return 100
        if (Regex("""\b${Regex.escape(q)}""").containsMatchIn(t)) return 80
        val qWords = q.split(Regex("""\W+""")).filter { it.length >= 3 && it !in STOPWORDS }
        if (qWords.isEmpty()) return 0
        val tWords = t.split(Regex("""\W+"""))
        val hits = qWords.count { qw -> tWords.any { it.startsWith(qw) || qw.startsWith(it) && it.length >= 4 } }
        return when {
            hits == qWords.size -> 60
            hits > 0 -> 30
            else -> 0
        }
    }

    // endregion

    // region Patrones (sobre texto plegado)

    private const val AMPM = """(?<ap>a\.?\s?m\b\.?|p\.?\s?m\b\.?)"""
    private const val QUAL_MORNING = "de la manana|en la manana|por la manana|de la madrugada|en la madrugada|de madrugada|in the morning|da manha|de manha|pela manha|na manha|da madrugada"
    private const val QUAL_AFTERNOON = "de la tarde|en la tarde|por la tarde|in the afternoon|da tarde|a tarde|de tarde|pela tarde|na tarde"
    private const val QUAL_NIGHT = "de la noche|en la noche|por la noche|de noche|in the evening|at night|da noite|a noite|de noite|pela noite|na noite"
    private const val QUAL_NOON = "del mediodia|al mediodia|del medio dia|ao meio-dia|ao meio dia"
    private const val QUAL = "$QUAL_MORNING|$QUAL_AFTERNOON|$QUAL_NIGHT|$QUAL_NOON"
    private const val JOIN = """(?:(?:,\s*|\b(?:y|and|e)\s+))?"""
    private const val UNITS = "minutos|minuto|minutes|minute|mins|min|horas|hora|hours|hour|hrs|hr|h|dias|dia|days|day|semanas|semana|weeks|week"
    private const val ALSO = """(?:\s+(?:tambien|too|also|tambem))?"""

    private val LEAD_COUNT = Regex(
        """$JOIN(?:\b(?:avisame|recuerdame|remind me|alert me|notify me|me avisa|me avise|me lembre|lembre-me)\s+)?\b(?<n>\d{1,3}|${Vocab.COUNT})\s*(?<u>$UNITS)\s+(?:antes|before|early|beforehand|previo)\b$ALSO""",
    )
    private val LEAD_ADVANCE = Regex(
        """$JOIN\b(?:con|with|com)\s+(?<n>\d{1,3}|${Vocab.COUNT})\s*(?<u>$UNITS)\s+(?:de\s+)?(?:anticipacion|antelacion|antecedencia|notice|in advance|de adelanto)\b$ALSO""",
    )
    private val LEAD_HALF = Regex("""$JOIN\b(?:media hora|half an hour|meia hora|meia-hora)\s+(?:antes|before)\b$ALSO""")
    private val LEAD_DAY_BEFORE = Regex(
        """$JOIN\b(?:el dia (?:de )?antes|el dia anterior|la noche anterior|la noche antes|la vispera|the day before|the night before|the eve|na vespera|no dia anterior|a vespera|na noite anterior)\b$ALSO""",
    )
    private val RELATIVE = Regex(
        """\b(?:en|dentro de|in|em|daqui a|daqui|within|after)\s+(?:(?<n>\d{1,3}|${Vocab.COUNT})\s*|(?<half>media|half an?|meia)\s+)(?<u>$UNITS)(?<plus>\s+(?:y|and|e)\s+(?:media|a half|meia))?\b""",
    )
    private val MONTHLY_DAY = listOf(
        Regex("""\b(?:(?:el|los|the|on the|o|todo|todos os|cada)\s+)?(?:dia\s+)?(?<d>\d{1,2})(?:st|nd|rd|th|º|o)?\s+(?:de|of|do)\s+(?:cada|every|each|todo|todos los|todos os)\s+(?:mes|month)\b"""),
        Regex("""\b(?:every|each)\s+month\s+on\s+the\s+(?<d>\d{1,2})(?:st|nd|rd|th)?\b"""),
        Regex("""\bmonthly\s+on\s+the\s+(?<d>\d{1,2})(?:st|nd|rd|th)?\b"""),
        Regex("""\b(?:todo|todos os)\s+dias?\s+(?<d>\d{1,2})\b(?!\s*(?:de\s+)?(?:${Vocab.MONTHS}))"""),
        Regex("""\b(?:cada|todos los)\s+(?:dias?\s+)?(?<d>\d{1,2})\s+(?:de|del)\s+(?:cada\s+)?mes\b"""),
        Regex("""\bel\s+(?:dia\s+)?(?<d>\d{1,2})\s+de\s+todos\s+los\s+meses\b"""),
        Regex("""\b(?:mensualmente|cada mes|todos los meses|every month|monthly|todo mes|todos os meses|mensalmente)\s+(?:el|on the|the|no dia|o dia|dia)\s+(?<d>\d{1,2})(?:st|nd|rd|th)?\b"""),
    )
    private val INTERVAL = Regex(
        """\b(?:cada|every|a cada)\s+(?<n>\d{1,2}|otro|otra|other|${Vocab.COUNT})\s+(?<u>dias|dia|days|day|semanas|semana|weeks|week|meses|mes|months|month|anos|ano|years|year)\b""",
    )
    private val PERIODIC_WORDS = Regex(
        """\b(?:quincenal(?:mente)?|biweekly|fortnightly|every fortnight|quinzenal(?:mente)?|bimestral(?:mente)?|bimonthly|trimestral(?:mente)?|quarterly|semestral(?:mente)?)\b""",
    )
    private val WORKDAYS = Regex(
        """\b(?:(?:todos\s+los\s+|los\s+|en\s+|on\s+|every\s+|nos\s+|todos\s+os\s+)?(?:dias\s+de\s+(?:la\s+)?semana|dias\s+laborables|dias\s+laborales|dias\s+habiles|dias\s+uteis|weekdays|weekday|workdays)|entre\s+semana|durante\s+a\s+semana)\b""",
    )
    private val WEEKENDS = Regex(
        """\b(?:(?:todos\s+los|los|cada|en\s+los|on|every|nos|todos\s+os|aos|todo)\s+)?(?:fines\s+de\s+semana|fin\s+de\s+semana|weekends|weekend|fins\s+de\s+semana|fim\s+de\s+semana)\b""",
    )
    private val STRONG_FREQUENCY: List<Triple<Regex, String, Period?>> = listOf(
        Triple(Regex("""\b(?:todas las mananas|cada manana|every morning|todas as manhas|toda manha|todas las madrugadas)\b"""), "daily", Period.MORNING),
        Triple(Regex("""\b(?:todas las tardes|cada tarde|every afternoon|todas as tardes|toda tarde)\b"""), "daily", Period.AFTERNOON),
        Triple(Regex("""\b(?:todas las noches|cada noche|every night|every evening|nightly|todas as noites|toda noite)\b"""), "daily", Period.NIGHT),
        Triple(Regex("""\b(?:todos los santos dias|todos los dias|cada dia|a diario|diariamente|every ?day|each day|every single day|todos os dias|todo santo dia|todo dia|dia a dia)\b"""), "daily", null),
        Triple(Regex("""\b(?:cada semana|todas las semanas|semanalmente|una vez (?:a|por|cada|en) (?:la )?semana|every week|each week|once a week|toda semana|todas as semanas|uma vez por semana)\b"""), "weekly", null),
        Triple(Regex("""\b(?:cada mes|todos los meses|mensualmente|una vez (?:al|por|cada) mes|every month|each month|once a month|todo mes|todos os meses|mensalmente|uma vez por mes)\b"""), "monthly", null),
        Triple(Regex("""\b(?:cada ano|todos los anos|anualmente|una vez al ano|every year|each year|once a year|annually|todo ano|todos os anos|uma vez por ano)\b"""), "yearly", null),
    )
    private val SINGLE_PREFIX_BEFORE = Regex("""\b(?:el|este|esta|this|next|on|coming|na|no|o|a|proximo|proxima|nesta|neste|essa|esse)\s+$""")
    private val WEAK_FREQUENCY = Regex("""\b(?:daily|diario|diaria|weekly|semanal|monthly|mensual|mensal|yearly|anual)\b""")
    private val DETERMINER_BEFORE = Regex(
        """\b(?:mi|mis|tu|tus|su|sus|el|la|los|las|un|una|my|the|our|your|his|her|a|an|meu|minha|meus|minhas|o|os|as|nosso|nossa|seu|sua|reporte|informe|reunion|report|meeting|relatorio|reuniao|pago|pagamento|payment)\s+$""",
    )

    private val TIME_RANGE = Regex(
        """$JOIN\b(?:de|from|entre|between|das|desde)\s+(?:las?\s+|as\s+)?(?<sh>\d{1,2})(?:(?::|h)(?<sm>\d{2}))?h?\s*(?:(?<sap>a\.?\s?m\b\.?|p\.?\s?m\b\.?)\s*)?(?:a|to|until|till|-|y\s+las?|and|hasta\s+las?|hasta|ate\s+as|ate|as)\s*(?:las?\s+|as\s+)?(?<eh>\d{1,2})(?:(?::|h)(?<em>\d{2}))?h?(?!\d|:|\s*(?:de\s+)?(?:${Vocab.MONTHS}))\s*(?:$AMPM|(?<q>$QUAL))?""",
    )

    private val timeAtCache = HashMap<AssistantLang, Regex>()
    private fun timeAt(lang: AssistantLang): Regex = timeAtCache.getOrPut(lang) {
        Regex(
            """$JOIN\b(?:a\s+eso\s+de\s+las?|a\s+las?|sobre\s+las?|hacia\s+las?|como\s+a\s+las?|para\s+las?|desde\s+las?|tipo|at\s+around|at\s+about|at|around|about|by|as|pelas|por\s+volta\s+das?)\s+(?<h>\d{1,2}|${Vocab.hourWordsRegex(lang)})(?:(?::|\.)(?<m>\d{2})|(?<hsuffix>h)(?<m3>\d{2})?|\s+(?:y|e|and)\s+(?<plus>media|cuarto|meia|quinze|\d{1,2})\b|\s+menos\s+(?<minus>cuarto|\d{1,2})\b)?(?:\s*(?:hs|hrs|horas|hora|hours|o'?clock)\b)?\s*(?:$AMPM)?\s*(?:(?<q>$QUAL))?""",
        )
    }

    private val TIME_HM = Regex(
        """$JOIN\b(?<h>\d{1,2})(?::(?<m>\d{2})|(?<hh>h)(?<m2>\d{2})?)(?![\d:])\b\s*(?:$AMPM)?\s*(?:(?<q>$QUAL))?""",
    )
    private val TIME_AMPM = Regex("""$JOIN\b(?<h>\d{1,2})\s*$AMPM\s*(?:(?<q>$QUAL))?""")
    private val TIME_QUAL = Regex("""$JOIN\b(?<h>\d{1,2})(?::(?<m>\d{2}))?\s+(?<q>$QUAL)""")
    private val NOON_MIDNIGHT = Regex(
        """(?:\b(?:a|al|at|ao|as|para el|para o)\s+)?\b(?<w>mediodia|medio dia|noon|midday|meio-dia|meio dia|medianoche|media noche|midnight|meia-noite|meia noite)\b""",
    )
    private val MIDNIGHT_WORD = Regex("""\b(?:medianoche|media noche|midnight|meia-noite|meia noite|00:00)\b""")

    private val DAY_PERIODS: List<Triple<Regex, Long?, Period?>> = listOf(
        Triple(Regex("""\b(?:esta noche|hoy en la noche|hoy por la noche|tonight|this evening|hoje a noite|hoje de noite|esta noite|nesta noite)\b"""), 0L, Period.NIGHT),
        Triple(Regex("""\b(?:esta tarde|hoy en la tarde|hoy por la tarde|this afternoon|hoje a tarde|nesta tarde)\b"""), 0L, Period.AFTERNOON),
        Triple(Regex("""\b(?:esta manana|hoy en la manana|this morning|hoje de manha|esta manha|nesta manha)\b"""), 0L, Period.MORNING),
        Triple(Regex("""\b(?:$QUAL_MORNING)\b"""), null, Period.MORNING),
        Triple(Regex("""\b(?:$QUAL_AFTERNOON)\b"""), null, Period.AFTERNOON),
        Triple(Regex("""\b(?:$QUAL_NIGHT)\b"""), null, Period.NIGHT),
    )
    private val DAY_AFTER_TOMORROW = Regex("""\b(?:pasado manana|the day after tomorrow|day after tomorrow|depois de amanha)\b""")
    private val TOMORROW = Regex("""\b(?:manana|tomorrow|amanha)\b""")
    private val TODAY = Regex("""\b(?:hoy|today|hoje)\b""")
    private val DAY_OF_MONTH = Regex(
        """\b(?:el dia|o dia|no dia|para el|para o dia|pro dia|on the|the|el|on|dia|o|no)\s+(?<d>\d{1,2})(?:st|nd|rd|th|º)?\b(?!\s*(?::|h\b|am\b|pm\b|a\.m|p\.m|de la|minutos?|min\b|horas?|hours?|dias?|days?|semanas?|weeks?|personas|people|pessoas|veces|times|vezes))""",
    )

    private val WAKE = Regex("""\b(?:despiertame|despertarme|despertar|levantame|levantarme|wake me|wake up|get me up|me acorde|acorde-me|me acorda|acordar)\b""")

    private val BIRTHDAY_WORDS = Regex("""\b(?:cumple|cumples|cumpleanos|birthday|birthdays|bday|b-day|niver|aniver)\b""")
    private val ANNIVERSARY_WORDS = Regex(
        """\b(?:anniversary|anniversaries|aniversario de (?:casamento|casados|namoro|boda|bodas|novios|noivado)|bodas de|mesiversario|mesversario)\b""",
    )
    private val EVENT_WORDS = Regex(
        """\b(?:reunion|reuniones|junta|cita|citas|evento|eventos|conferencia|meeting|meetings|appointment|appointments|event|events|conference|interview|entrevista|reuniao|reunioes|consulta|compromisso|webinar)\b""",
    )

    private val COMMANDS: List<Regex> = listOf(
        // Español
        Regex("""\b(?:me\s+)?(?:puedes|podrias|podes)\s+"""),
        Regex("""\b(?:pon|ponme|poner|ponerme|ponle|pones|crea|creame|crear|programa|programame|programar|agenda|agendame|agendar|anota|anotame|apunta|apuntame|configura|configurame|establece|haz|hazme|coloca|colocame|agrega|agregame|anade|anademe|necesito|quiero|dame|guarda|guardame)\s+(?:una?\s+)?(?:nueva\s+|nuevo\s+)?(?:alarmas?|recordatorios?|aviso|alerta|alarmita)(?:\s+(?:para|de|del|que diga|llamada|con el nombre|sobre))?\b"""),
        Regex("""\b(?:quiero|necesito)\s+que\s+me\s+(?:recuerdes|avises|despiertes)(?:\s+(?:que|de|a|para))?\b"""),
        Regex("""\b(?:me\s+)?(?:recuerdas|recuerdes|avisas|avises)(?:\s+(?:que|de|a|para))?\b"""),
        Regex("""\b(?:que\s+no\s+se\s+me\s+olvide|no\s+se\s+me\s+(?:puede\s+)?olvidar|no\s+(?:me\s+)?(?:dejes\s+)?olvid(?:es|ar))(?:\s+(?:de|que))?\b"""),
        Regex("""\b(?:recuerdame|recordarme|recordame|acuerdame|recuerda)(?:\s+(?:que|de|a|para))?\b"""),
        Regex("""\b(?:avisame|avisarme|alertame|notificame)(?:\s+(?:que|de|para|cuando))?\b"""),
        Regex("""\b(?:despiertame|despertarme|levantame|levantarme)(?:\s+(?:para|que))?\b"""),
        Regex("""^\W*(?:una?\s+)?(?:alarma|recordatorio)\s+(?:para|de|del|que diga|sobre)\b"""),
        Regex("""^\W*(?:pon|ponme|anota|anotame|apunta|apuntame|agenda|agendame|agrega|anade)\b"""),
        Regex("""^\W*(?:recordar|acordarme de|acordarse de|remember to|remember|lembrar de|lembrar)(?:\s+(?:que|de|a))?\b"""),
        Regex("""\b(?:colocalo|colocala|ponlo|ponla|agendalo|agendala|programalo|programala|guardalo|guardala|anotalo|anotala|crealo|creala)\b"""),
        // Inglés
        Regex("""\b(?:please\s+)?(?:can|could|would|will)\s+you\s+(?:please\s+)?"""),
        Regex("""\bremind\s+me(?:\s+(?:to|that|about|of))?\b"""),
        Regex("""\b(?:wake\s+me(?:\s+up)?|get\s+me\s+up)(?:\s+(?:for|to))?\b"""),
        Regex("""\b(?:alert|notify|ping)\s+me(?:\s+(?:to|that|about|when))?\b"""),
        Regex("""\b(?:set|create|add|make|schedule|put|save|give)\s+(?:me\s+)?(?:up\s+)?(?:an?\s+)?(?:new\s+)?(?:alarms?|reminders?|alerts?)(?:\s+(?:for|to|called|named|about|that says))?\b"""),
        Regex("""^\W*(?:an?\s+)?(?:alarm|reminder)\s+(?:for|to|about)\b"""),
        Regex("""\b(?:don'?t|do not)\s+let\s+me\s+forget(?:\s+(?:to|about))?\b"""),
        Regex("""\b(?:don'?t|do not)\s+forget(?:\s+(?:to|about))?\b"""),
        // Portugués
        Regex("""\b(?:voce\s+)?(?:pode|poderia|consegue)\s+"""),
        Regex("""\b(?:me\s+)?(?:lembre|lembra|lembrar)(?:-me)?(?:\s+(?:de|que|da|do|para|pra))?\b"""),
        Regex("""\b(?:me\s+)?(?:avise|avisa|avisar)(?:-me)?(?:\s+(?:de|que|para|pra|quando))?\b"""),
        Regex("""\b(?:me\s+)?(?:acorde|acorda)(?:-me)?(?:\s+(?:para|pra))?\b"""),
        Regex("""\b(?:crie|cria|criar|coloca|coloque|colocar|bota|bote|botar|poe|ponha|agenda|agende|agendar|programa|programe|programar|adiciona|adicione|adicionar|define|defina|marca|marque|anota|anote|salva|salve|quero|preciso de)\s+(?:um\s+|uma\s+)?(?:novo\s+|nova\s+)?(?:alarmes?|lembretes?|aviso|alerta)(?:\s+(?:para|pra|de|do|da|sobre|chamado))?\b"""),
        Regex("""^\W*(?:um\s+)?(?:alarme|lembrete)\s+(?:para|pra|de|do|da|sobre)\b"""),
        Regex("""\b(?:nao\s+me\s+deixe\s+esquecer|nao\s+deixa\s+eu\s+esquecer|nao\s+(?:posso\s+)?esquecer)(?:\s+(?:de|do|da))?\b"""),
    )
    private val POLITE = Regex("""\b(?:por favor|porfa|porfis|plis|please|pls|plz|thanks|thank you|gracias|obrigad[oa]|pfv)\b""")

    private val LEADING_FILLER = Regex(
        """^\W*(?:(?:hola|holi|buenas|buenos dias|buenas tardes|buenas noches|hi|hello|hey|good morning|good afternoon|good evening|oi|ola|bom dia|boa tarde|boa noite|oye|ok|okay|vale|bueno|calarmi)\b[\s,.!]*)+""",
    )
    private val GREETING_ONLY = Regex(
        """^\W*(?:hola|holi|buenas|buenos dias|buenas tardes|buenas noches|hi|hello|hey|hey there|good morning|good afternoon|good evening|oi|ola|bom dia|boa tarde|boa noite|que tal)(?:\s+calarmi)?\W*""",
    )
    private val THANKS_ONLY = Regex(
        """^\W*(?:muchas\s+|muito\s+)?(?:gracias|thanks|thank you|thx|ty|obrigad[oa]|valeu|perfecto|perfect|genial|great|ok|okay|vale|listo|dale|beleza|otimo|cool|nice|super|excelente|excellent|de acuerdo|entendido|got it|bien|bueno|perfeito)(?:\s+(?:calarmi|mucho|so much|a lot))?\W*""",
    )
    private val HELP = Regex(
        """\b(?:ayuda|ayudame|help|ajuda|me ajuda|que puedes hacer|que sabes hacer|what can you do|how does this work|como funciona|como te uso|o que voce (?:pode|sabe) fazer)\b""",
    )
    private val POLITE_PREFIX = Regex(
        """^\W*(?:(?:por favor|porfa|please|pls|oye|ahora|now|agora|entonces|then|y|and|e|ok|vale|bueno)[,\s]+)*(?:(?:puedes|podrias|me puedes|can you|could you|would you|pode|voce pode|podes|consegue)\s+)?""",
    )

    private val DELETE_VERBS = Regex(
        """^(?:borra|borrar|borrame|borrala|borralo|borralas|borralos|elimina|eliminar|eliminala|eliminalo|eliminalas|quita|quitar|quitala|quitalo|cancela|cancelar|cancelala|cancelalo|suprime|delete|remove|cancel|erase|get rid of|clear)\b""",
    )
    private val DELETE_VERBS_PT = Regex(
        """^(?:apaga|apagar|apague|exclui|excluir|exclua|remove|remover|remova|deleta|deletar|delete|cancela|cancelar|cancele|tira|tirar|tire)\b""",
    )
    private val DISABLE_VERBS = Regex(
        """^(?:desactiva|desactivar|desactivala|desactivalo|apaga|apagar|apagala|apagalo|silencia|silenciar|silenciala|silencialo|pausa|pausar|pausala|pausalo|deten|detener|turn off|switch off|disable|pause|mute|deactivate|silence|stop|desativa|desativar|desative|desliga|desligar|desligue|silencie)\b""",
    )
    private val ENABLE_VERBS = Regex(
        """^(?:activa|activar|activala|activalo|reactiva|reactivar|reactivala|reactivalo|enciende|encender|enciendela|prende|prender|habilita|habilitar|turn on|switch on|turn back on|enable|activate|reactivate|re-enable|resume|unpause|ativa|ativar|ative|reativa|reativar|reative|liga|ligar|ligue|habilite)\b""",
    )
    private val UPDATE_VERBS = Regex(
        """^(?:cambia|cambiar|cambiala|cambialo|mueve|mover|muevela|muevelo|modifica|modificar|modificala|modificalo|pasala|pasalo|reprograma|reprogramar|reprogramala|reprogramalo|retrasala|retrasalo|adelantala|adelantalo|ponla|ponlo|change|move|reschedule|update|modify|postpone|muda|mudar|mude|altera|alterar|altere|mova|remarca|remarcar|remarque|troca|trocar|troque|adia|adiar|adie)\b""",
    )
    private val ENCLITIC_BASES = setOf(
        "borra", "elimina", "quita", "cancela", "desactiva", "apaga", "silencia", "pausa", "activa", "reactiva", "enciende",
        "cambia", "mueve", "modifica", "pasa", "reprograma", "retrasa", "adelanta", "pon",
    )
    private val ALARM_NOUN = Regex("""\b(?:alarmas?|alarms?|alarmes?|recordatorios?|reminders?|lembretes?|avisos?)\b""")
    private val PRONOUNS = setOf("", "it", "that", "that one", "this", "this one", "esa", "ese", "esta", "este", "eso", "isso", "ela", "ele", "essa", "esse", "la", "lo")
    private val QUERY_LEAD = Regex(
        """^(?:la|las|el|los|lo|mi|mis|the|my|o|a|os|as|meu|minha|meus|minhas|de|del|da|do|das|dos|of|for|called|named|que se llama|que dice|llamada|llamado|chamado|chamada|sobre|about|para|pra|un|una|um|uma|an|todas|todos|all|al|ao)\s+""",
    )
    private val QUERY_TAIL = Regex("""\s+(?:de|del|da|do|for|of|the|la|el|para|pra|a|al|ao|to|en|em|on|at|y|and|e)$""")
    private val STOPWORDS = setOf("los", "las", "del", "the", "con", "with", "com", "para", "por", "que", "una", "uma", "dos", "das")

    private val LIST_START = Regex(
        """^\W*(?:y\s+|and\s+|e\s+)?(?:que|cuales|cuantos|cuantas|cual|what|whats|what's|which|how many|o que|quais|quantos|quantas|qual|muestrame|ensename|dime|lista|listame|listar|show|list|tell me|mostra|mostre|me mostra|me mostre|me diz|ver|see|do i have|have i got|is there|are there|any|cuando|when|quando|a que hora|what time)\b""",
    )
    /** «Tengo/hay/tenho/tem…» solo preguntan con signo de pregunta: «Tengo dentista el sábado» es una alarma. */
    private val LIST_START_HAVE = Regex("""^\W*(?:y\s+|e\s+)?(?:tengo|hay|tenho|eu tenho|tem|existe)\b""")
    private val FIND_START = Regex("""^\W*(?:cuando|when|quando|a que hora|what time)\b""")
    private val FIND_WORDS = Regex(
        """(?i)\b(?:cuando|cuándo|when|quando|a que hora|what time|es|is|e|é|sera|será|will|be|suena|rings|toca|tengo|have|do i have|is my|el|la|mi|my|the|o|meu|minha|does|do|proxima|próxima|proximo|próximo|next|siguiente)\b""",
    )
    private val MY_ALARMS = Regex(
        """^\W*(?:mis|my|meus|minhas|ver|see|show|lista de|list)\s+(?:alarmas|alarms|alarmes|recordatorios|reminders|lembretes)\W*""",
    )
    private val RANGE_ONLY = Regex(
        """^\W*(?:y|and|e)?\s*(?:para\s+|for\s+|pra\s+)?(?:pasado manana|manana|tomorrow|amanha|hoy|today|hoje|esta semana|this week|essa semana|la proxima semana|next week|proxima semana|este mes|this month|esse mes|el fin de semana|the weekend|o fim de semana)\W*""",
    )
    private val AGENDA = Regex(
        """\b(?:alarmas?|alarms?|alarmes?|recordatorios?|reminders?|lembretes?|tengo|tenho|have|hay|tem|pendientes?|planned|scheduled|programad[oa]s?|agendad[oa]s?|agenda|plans?|planes|cumpleanos|birthdays?|aniversarios?|anniversaries|eventos?|events?|reuniones|reunioes|meetings?|citas?|appointments?|compromissos?|consultas?)\b""",
    )
    private val CREATE_HINT = Regex(
        """\b(?:recuerdame|remind me|pon una|ponme una|pones una|set an?|crea una|creame|lembre|avisame|me avisa|programa una|agenda una|wake me|despiertame|crie um|coloca um)\b""",
    )
    private val UNPARSED_TIMEISH = Regex("""\d|\b(?:despues|luego|later|temprano|early|almuerzo|lunch|cena|dinner|desayuno|breakfast|almoco|jantar|cafe da manha)\b""")

    private val TITLE_LEAD = Regex(
        """^(?:y|e|and|o|or|para|pra|for|to|de|del|da|do|que|about|sobre|el|la|los|las|lo|the|os|as|un|una|um|uma|an|a|mi|mis|my|meu|minha|meus|minhas|tengo que|tengo|tenho que|tenho|hay que|hay|tem que|tem|i have to|i need to|i have|i've got|there is|there's|need to|have to|necesito|preciso|debo|devo|tambien|also|too|tambem|porfa|por favor|please|pls|alarma|alarm|alarme|recordatorio|reminder|lembrete|aviso|me|en|em|at|on)(?:\s+|$)""",
    )
    private val TITLE_TAIL = Regex(
        """(?:^|\s+)(?:y|e|and|o|or|para|pra|for|to|de|del|da|do|que|el|la|los|las|the|a|at|on|en|em|no|na|in|es|is|sera|tambien|too|also|tambem|porfa|por favor|please|pls|ok|gracias|thanks|obrigado|obrigada|alarma|alarm|alarme)$""",
    )

    // endregion
}

private fun MatchResult.g(name: String): String? = runCatching { groups[name]?.value }.getOrNull()

/**
 * El texto con lo ya reconocido tachado. Los tramos tachados se marcan con un
 * carácter que no es letra ni espacio: dos datos vecinos nunca se juntan en uno
 * nuevo, y los límites de palabra siguen valiendo.
 */
internal class Scan(val raw: String) {
    val folded: String = fold(raw)
    private val work = folded.toCharArray()
    private val kind = IntArray(raw.length)

    fun matches(regex: Regex): List<MatchResult> = regex.findAll(String(work)).toList()

    fun take(range: IntRange, info: Boolean = true) {
        for (i in range) {
            if (i !in work.indices) continue
            work[i] = MASK
            kind[i] = if (info) 1 else 2
        }
    }

    fun residue(): String = buildString { raw.forEachIndexed { i, c -> append(if (kind[i] != 0) ' ' else c) } }

    /** Oraciones del texto sin tachar, cada una con si tenía datos de fecha/hora. */
    fun segments(): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        val current = StringBuilder()
        var hasInfo = false
        for (i in raw.indices) {
            val c = raw[i]
            if (kind[i] != 0) {
                if (kind[i] == 1) hasInfo = true
                current.append(' ')
                continue
            }
            if (c == '.' || c == '!' || c == '?' || c == ';' || c == '\n') {
                out += current.toString() to hasInfo
                current.clear()
                hasInfo = false
            } else {
                current.append(c)
            }
        }
        out += current.toString() to hasInfo
        return out
    }

    private companion object {
        const val MASK = '\u0001'
    }
}
