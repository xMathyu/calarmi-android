package com.mathyusolutions.calarmi.domain

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Weekday
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Las ocurrencias de una `RecurrenceRule` a partir de su fecha ancla. Port
 * directo del `RecurrenceEngine` de iOS: avanza paso a paso desde el ancla
 * (sumar meses recorta: 31 de enero + 1 mes = 28 de febrero, y de ahí sigue el
 * 28), y conserva la hora de reloj a través de los cambios de horario.
 */
object RecurrenceEngine {

    /** Hasta `count` ocurrencias estrictamente posteriores a `now`. */
    fun nextOccurrences(
        rule: RecurrenceRule,
        baseDate: Long,
        count: Int = 12,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<Long> {
        if (count <= 0) return emptyList()
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(baseDate), zone)
        return when (rule) {
            RecurrenceRule.Once -> if (baseDate > now) listOf(baseDate) else emptyList()
            is RecurrenceRule.Daily -> advancing(base, ChronoUnit.DAYS, maxOf(1, rule.interval).toLong(), count, now)
            is RecurrenceRule.Weekly ->
                if (rule.weekdays.isEmpty()) advancing(base, ChronoUnit.DAYS, 7L * maxOf(1, rule.interval), count, now)
                else weeklyNext(base, maxOf(1, rule.interval), rule.weekdays, count, now, locale)
            is RecurrenceRule.Monthly -> advancing(base, ChronoUnit.MONTHS, maxOf(1, rule.interval).toLong(), count, now)
            is RecurrenceRule.Yearly -> advancing(base, ChronoUnit.YEARS, maxOf(1, rule.interval).toLong(), count, now)
        }
    }

    /** Todas las ocurrencias en `[start, end)`, pasadas incluidas (la vista de mes). */
    fun occurrences(
        rule: RecurrenceRule,
        baseDate: Long,
        start: Long,
        end: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<Long> {
        if (start >= end) return emptyList()
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(baseDate), zone)
        return when (rule) {
            RecurrenceRule.Once -> if (baseDate in start until end) listOf(baseDate) else emptyList()
            is RecurrenceRule.Daily -> stepping(base, ChronoUnit.DAYS, maxOf(1, rule.interval).toLong(), start, end)
            is RecurrenceRule.Weekly ->
                if (rule.weekdays.isEmpty()) stepping(base, ChronoUnit.DAYS, 7L * maxOf(1, rule.interval), start, end)
                else weeklyRange(base, maxOf(1, rule.interval), rule.weekdays, start, end, locale)
            is RecurrenceRule.Monthly -> stepping(base, ChronoUnit.MONTHS, maxOf(1, rule.interval).toLong(), start, end)
            is RecurrenceRule.Yearly -> stepping(base, ChronoUnit.YEARS, maxOf(1, rule.interval).toLong(), start, end)
        }
    }

    // region Helpers

    private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()

    /** `base + k·step` (sumando de a un paso desde el ancla, como iOS). */
    private fun ZonedDateTime.plusStep(unit: ChronoUnit, step: Long): ZonedDateTime = plus(step, unit)

    private fun advancing(base: ZonedDateTime, unit: ChronoUnit, step: Long, count: Int, now: Long): List<Long> {
        var candidate = base
        if (unit == ChronoUnit.DAYS && candidate.millis() <= now) {
            // Sumar días no recorta: se puede saltar de golpe a justo antes de `now`.
            val nowDate = Instant.ofEpochMilli(now).atZone(base.zone)
            val days = ChronoUnit.DAYS.between(candidate.toLocalDate(), nowDate.toLocalDate())
            if (days >= step) candidate = candidate.plusDays((days / step) * step)
        }
        var guard = 0
        while (candidate.millis() <= now && guard < 100_000) {
            candidate = candidate.plusStep(unit, step)
            guard++
        }
        val results = ArrayList<Long>(count)
        while (results.size < count) {
            results += candidate.millis()
            candidate = candidate.plusStep(unit, step)
        }
        return results
    }

    private fun stepping(base: ZonedDateTime, unit: ChronoUnit, step: Long, start: Long, end: Long): List<Long> {
        var candidate = base
        if (unit == ChronoUnit.DAYS && candidate.millis() < start) {
            val startDate = Instant.ofEpochMilli(start).atZone(base.zone)
            val days = ChronoUnit.DAYS.between(candidate.toLocalDate(), startDate.toLocalDate())
            if (days >= step) candidate = candidate.plusDays((days / step) * step)
        }
        val results = ArrayList<Long>()
        var steps = 0
        while (candidate.millis() < end && steps < 10_000) {
            if (candidate.millis() >= start) results += candidate.millis()
            candidate = candidate.plusStep(unit, step)
            steps++
        }
        return results
    }

    /** Inicio (00:00) de la semana que contiene `date`, según el primer día de la semana del idioma. */
    private fun weekStart(date: ZonedDateTime, locale: Locale): ZonedDateTime {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        return date.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(firstDay))
            .atStartOfDay(date.zone)
    }

    /**
     * Los días elegidos de UNA semana, a la hora de la alarma, en orden. El
     * desplazamiento va de 0 a 6 para que cada día caiga en su propia semana
     * (con la semana empezando en lunes, el domingo es el ÚLTIMO día).
     */
    private fun weeklyCandidates(cycleStart: ZonedDateTime, weekdays: Set<Weekday>, time: LocalTime): List<Long> {
        val startDow = cycleStart.dayOfWeek.value % 7 // domingo = 0
        return weekdays.map { weekday ->
            val target = weekday.dayOfWeek.value % 7
            val offset = ((target - startDow) + 7) % 7
            val day = cycleStart.toLocalDate().plusDays(offset.toLong())
            ZonedDateTime.of(day, time, cycleStart.zone).millis()
        }.sorted()
    }

    private fun weeklyNext(
        base: ZonedDateTime,
        interval: Int,
        weekdays: Set<Weekday>,
        count: Int,
        now: Long,
        locale: Locale,
    ): List<Long> {
        val time = base.toLocalTime()
        var cycleStart = weekStart(base, locale)
        val nowZ = Instant.ofEpochMilli(now).atZone(base.zone)
        // Salta al ciclo que contiene `now`: los anteriores no aportan nada.
        val weeks = ChronoUnit.WEEKS.between(cycleStart.toLocalDate(), nowZ.toLocalDate())
        if (weeks >= interval) cycleStart = cycleStart.plusWeeks((weeks / interval) * interval)
        val results = ArrayList<Long>(count)
        val maxCycles = maxOf(count * 4, 52)
        var cycles = 0
        while (results.size < count && cycles < maxCycles) {
            for (candidate in weeklyCandidates(cycleStart, weekdays, time)) {
                if (candidate > now) {
                    results += candidate
                    if (results.size >= count) return results
                }
            }
            cycleStart = cycleStart.plusWeeks(interval.toLong())
            cycles++
        }
        return results
    }

    private fun weeklyRange(
        base: ZonedDateTime,
        interval: Int,
        weekdays: Set<Weekday>,
        start: Long,
        end: Long,
        locale: Locale,
    ): List<Long> {
        val time = base.toLocalTime()
        var cycleStart = weekStart(base, locale)
        if (cycleStart.millis() < start) {
            val startZ = Instant.ofEpochMilli(start).atZone(base.zone)
            val weeks = ChronoUnit.WEEKS.between(cycleStart.toLocalDate(), startZ.toLocalDate())
            if (weeks >= interval) cycleStart = cycleStart.plusWeeks((weeks / interval) * interval)
        }
        val results = ArrayList<Long>()
        var cycles = 0
        while (cycleStart.millis() < end && cycles < 1_000) {
            for (candidate in weeklyCandidates(cycleStart, weekdays, time)) {
                if (candidate >= base.millis() && candidate >= start && candidate < end) results += candidate
            }
            cycleStart = cycleStart.plusWeeks(interval.toLong())
            cycles++
        }
        return results
    }

    // endregion
}
