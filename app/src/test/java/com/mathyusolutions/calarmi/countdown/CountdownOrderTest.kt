package com.mathyusolutions.calarmi.countdown

import com.mathyusolutions.calarmi.model.CountdownEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownOrderTest {
    // `CountdownEvent.target` usa la zona del sistema: las fechas se arman en ella.
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 11, 23, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun event(id: String, date: Long, yearly: Boolean = false) =
        CountdownEvent(id = id, title = id, date = date, repeatsYearly = yearly)

    @Test fun upcomingNearestFirstPastMostRecentFirst() {
        val events = listOf(
            event("trip", day(2027, 2, 1)),
            event("concert", day(2026, 12, 5)),
            event("old", day(2025, 1, 1)),
            event("recent", day(2026, 11, 20)),
            event("today", day(2026, 11, 23)),
        )
        val groups = CountdownOrder.groups(events, now)
        assertEquals(listOf("today", "concert", "trip"), groups.upcoming.map { it.id })
        assertEquals(listOf("recent", "old"), groups.past.map { it.id })
    }

    @Test fun yearlyNeverGoesPast() {
        val birthday = event("birthday", day(1990, 3, 10), yearly = true)
        val groups = CountdownOrder.groups(listOf(birthday), now)
        assertEquals(listOf("birthday"), groups.upcoming.map { it.id })
        assertEquals(emptyList<CountdownEvent>(), groups.past)
    }

    @Test fun defaultIsNextOrElseMostRecent() {
        val past = listOf(event("a", day(2026, 1, 1)), event("b", day(2026, 6, 1)))
        assertEquals("b", CountdownOrder.default(past, now)?.id)
        val withUpcoming = past + event("c", day(2027, 1, 1))
        assertEquals("c", CountdownOrder.default(withUpcoming, now)?.id)
        assertNull(CountdownOrder.default(emptyList(), now))
    }

    @Test fun pickerListsUpcomingBeforePast() {
        val events = listOf(event("past", day(2026, 11, 1)), event("next", day(2026, 12, 1)))
        assertEquals(listOf("next", "past"), CountdownOrder.forPicker(events, now).map { it.id })
    }

    @Test fun breakdownCountsCalendarDaysAcrossDst() {
        // En Nueva York, el 8 de marzo de 2026 el reloj se adelanta una hora.
        val ny = ZoneId.of("America/New_York")
        val from = LocalDateTime.of(2026, 3, 7, 12, 0).atZone(ny).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 3, 9, 12, 0).atZone(ny).toInstant().toEpochMilli()
        assertEquals(CountdownBreakdown(2, 0, 0, 0), CountdownBreakdown.between(from, to, ny))
    }

    @Test fun breakdownParts() {
        val lima = ZoneId.of("America/Lima")
        val from = LocalDateTime.of(2026, 11, 23, 18, 0, 0).atZone(lima).toInstant().toEpochMilli()
        val to = LocalDateTime.of(2026, 11, 25, 21, 30, 15).atZone(lima).toInstant().toEpochMilli()
        assertEquals(CountdownBreakdown(2, 3, 30, 15), CountdownBreakdown.between(from, to, lima))
        assertNull(CountdownBreakdown.between(to, from, lima))
    }

    @Test fun normalizedDropsTimeWhenNotIncluded() {
        val lima = ZoneId.of("America/Lima")
        val date = LocalDateTime.of(2026, 12, 5, 20, 45, 31).atZone(lima).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 12, 5).atStartOfDay(lima).toInstant().toEpochMilli(), CountdownDefaults.normalized(date, false, lima))
        assertEquals(
            LocalDateTime.of(2026, 12, 5, 20, 45).atZone(lima).toInstant().toEpochMilli(),
            CountdownDefaults.normalized(date, true, lima),
        )
    }

    @Test fun defaultDateIsInAMonthAt8pm() {
        val lima = ZoneId.of("America/Lima")
        val from = LocalDateTime.of(2026, 1, 31, 9, 0).atZone(lima).toInstant().toEpochMilli()
        assertEquals(
            LocalDateTime.of(2026, 2, 28, 20, 0).atZone(lima).toInstant().toEpochMilli(),
            CountdownDefaults.date(from, lima),
        )
    }
}
