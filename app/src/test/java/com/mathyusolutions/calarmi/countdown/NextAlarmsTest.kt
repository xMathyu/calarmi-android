package com.mathyusolutions.calarmi.countdown

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.widget.NextAlarms
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que enseña el widget «Próxima alarma». */
class NextAlarmsTest {
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 11, 23, 18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(hour: Int, minute: Int, day: Int = 23) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun nearestFirstOneRowPerAlarm() {
        val reminders = listOf(
            Reminder(id = "late", title = "late", date = at(22, 0)),
            Reminder(id = "daily", title = "daily", date = at(7, 30, day = 1), recurrence = RecurrenceRule.Daily(1)),
            Reminder(id = "soon", title = "soon", date = at(18, 30)),
        )
        val upcoming = NextAlarms.upcoming(reminders, now)
        // La diaria aparece una sola vez (mañana a las 7:30), no con sus próximos días.
        assertEquals(listOf("soon", "late", "daily"), upcoming.map { it.first.id })
        assertEquals(at(7, 30, day = 24), upcoming.last().second)
    }

    @Test fun skipsDisabledAndLimitsToThree() {
        val reminders = (1..5).map { Reminder(id = "r$it", title = "r$it", date = at(19, it)) } +
            Reminder(id = "off", title = "off", date = at(18, 10), isEnabled = false)
        assertEquals(listOf("r1", "r2", "r3"), NextAlarms.upcoming(reminders, now).map { it.first.id })
    }

    @Test fun stillShownDuringTheMinuteItRings() {
        val ringing = Reminder(id = "now", title = "now", date = now - 30_000)
        val gone = Reminder(id = "gone", title = "gone", date = now - 2 * 60_000)
        assertEquals(listOf("now"), NextAlarms.upcoming(listOf(ringing, gone), now).map { it.first.id })
        assertEquals(now - 30_000 + NextAlarms.GRACE_MS, NextAlarms.nextChange(listOf(ringing, gone), now))
    }
}
