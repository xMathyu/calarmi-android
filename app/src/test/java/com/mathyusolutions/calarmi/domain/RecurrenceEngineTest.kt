package com.mathyusolutions.calarmi.domain

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port de `CalarmTests/RecurrenceEngineTests.swift`: mismas fechas, mismos resultados. */
class RecurrenceEngineTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val locale = Locale.forLanguageTag("es-MX")
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun date(s: String) = LocalDateTime.parse(s, fmt).atZone(zone).toInstant().toEpochMilli()
    private fun z(m: Long): ZonedDateTime = java.time.Instant.ofEpochMilli(m).atZone(zone)

    private fun next(rule: RecurrenceRule, base: Long, count: Int, now: Long) =
        RecurrenceEngine.nextOccurrences(rule, base, count, now, zone, locale)

    @Test fun onceFutureReturnsSingle() {
        val base = date("2026-06-01 09:00")
        assertEquals(listOf(base), next(RecurrenceRule.Once, base, 12, date("2026-01-01 10:00")))
    }

    @Test fun oncePastReturnsEmpty() {
        assertTrue(next(RecurrenceRule.Once, date("2026-06-01 09:00"), 12, date("2026-06-02 10:00")).isEmpty())
    }

    @Test fun dailyEveryTwoDays() {
        val r = next(RecurrenceRule.Daily(2), date("2026-01-01 10:00"), 3, date("2026-01-01 00:00"))
        assertEquals(listOf(date("2026-01-01 10:00"), date("2026-01-03 10:00"), date("2026-01-05 10:00")), r)
    }

    @Test fun dailyFastForwardsPastBaseDate() {
        val r = next(RecurrenceRule.Daily(3), date("2026-01-01 10:00"), 2, date("2026-01-10 12:00"))
        assertEquals(date("2026-01-13 10:00"), r.first())
        assertEquals(2, r.size)
    }

    @Test fun weeklyMondayWednesdayFridayInterval1() {
        val r = next(RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY)), date("2026-01-04 09:00"), 4, date("2026-01-04 00:00"))
        assertEquals(4, r.size)
        assertTrue(r.all { z(it).dayOfWeek.value in setOf(1, 3, 5) })
        assertTrue(r.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun weeklyInterval2SkipsAWeek() {
        val r = next(RecurrenceRule.Weekly(2, setOf(Weekday.TUESDAY)), date("2026-01-04 09:00"), 3, date("2026-01-04 00:00"))
        assertEquals(3, r.size)
        r.zipWithNext().forEach { (a, b) -> assertEquals(14, ChronoUnit.DAYS.between(z(a), z(b))) }
    }

    @Test fun weeklyEmptyWeekdaysFallsBackToInterval() {
        val r = next(RecurrenceRule.Weekly(1, emptySet()), date("2026-01-04 09:00"), 3, date("2026-01-04 00:00"))
        assertEquals(3, r.size)
        r.zipWithNext().forEach { (a, b) -> assertEquals(7, ChronoUnit.DAYS.between(z(a), z(b))) }
    }

    @Test fun weeklySundayStaysInItsOwnWeekEveryTwoWeeks() {
        // Con la semana empezando en lunes, el domingo es el último día del ciclo.
        val base = date("2026-01-05 09:00") // lunes
        // es-ES: la semana empieza en lunes (en es-MX empieza en domingo, como en iOS).
        val r = RecurrenceEngine.nextOccurrences(
            RecurrenceRule.Weekly(2, setOf(Weekday.MONDAY, Weekday.SUNDAY)), base, 4, date("2026-01-05 00:00"), zone, Locale.forLanguageTag("es-ES"),
        )
        assertEquals(listOf("2026-01-05", "2026-01-11", "2026-01-19", "2026-01-25"), r.map { z(it).toLocalDate().toString() })
    }

    @Test fun monthlyHandlesEndOfMonth() {
        val r = next(RecurrenceRule.Monthly(1), date("2026-01-31 09:00"), 3, date("2026-01-01 00:00"))
        assertEquals(date("2026-01-31 09:00"), r[0])
        assertEquals(2, z(r[1]).monthValue)
        assertEquals(3, z(r[2]).monthValue)
    }

    @Test fun yearlyAnnualBirthday() {
        val r = next(RecurrenceRule.Yearly(1), date("2026-07-14 09:00"), 5, date("2026-01-01 00:00"))
        assertEquals(listOf(2026, 2027, 2028, 2029, 2030), r.map { z(it).year })
    }

    @Test fun yearlyLeapDay() {
        val r = next(RecurrenceRule.Yearly(1), date("2024-02-29 09:00"), 4, date("2024-01-01 00:00"))
        assertEquals(listOf(2, 2, 2, 2), r.map { z(it).monthValue })
    }

    @Test fun rangeMatchesNextOccurrencesForWeekly() {
        val rule = RecurrenceRule.Weekly(2, setOf(Weekday.TUESDAY, Weekday.SATURDAY))
        val base = date("2026-01-06 07:30")
        val start = date("2026-03-01 00:00")
        val end = date("2026-04-01 00:00")
        val range = RecurrenceEngine.occurrences(rule, base, start, end, zone, locale)
        val nexts = next(rule, base, 20, start - 1).filter { it < end }
        assertEquals(nexts, range)
    }

    @Test fun keepsWallClockAcrossDst() {
        val ny = ZoneId.of("America/New_York")
        val base = LocalDateTime.parse("2026-03-07 08:00", fmt).atZone(ny).toInstant().toEpochMilli()
        val r = RecurrenceEngine.nextOccurrences(RecurrenceRule.Daily(1), base, 3, base - 1, ny, locale)
        assertTrue(r.all { java.time.Instant.ofEpochMilli(it).atZone(ny).hour == 8 })
    }
}
