package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los ayudantes puros de `AssistantService`/`ToolHelpers` con los casos de `HARNESS_UNIT` de iOS. */
class DraftRulesTest {
    private val now = EvalClock.now

    @Test fun normalizeTimeRanges_iosCases() {
        val cases = listOf(
            "hay treasure hours en fortnite este sabado 14 en peru de 2 a 4 y de 9 a 11. colocalo porfa" to
                "hay treasure hours en fortnite este sabado 14 en peru de 14:00 a 16:00 y de 21:00 a 23:00. colocalo porfa",
            "fútbol todos los sábados de 3 a 5" to "fútbol todos los sábados de 15:00 a 17:00",
            "gym de 9 a 11" to "gym de 9 a 11",
            "gym de 6 a 8 de la mañana" to "gym de 06:00 a 08:00",
            "clase de 7 a 9 de la noche" to "clase de 19:00 a 21:00",
            "meeting from 2 to 3pm" to "meeting from 14:00 to 15:00",
            "vacaciones de 15 a 20 de noviembre" to "vacaciones de 15 a 20 de noviembre",
            "de 14 a 16 y de 9 a 11" to "de 14 a 16 y de 21:00 a 23:00",
            "de 2:30 a 4" to "de 14:30 a 16:00",
            "Pon una alarma mañana a las 8am" to "Pon una alarma mañana a las 8am",
            "de 11 a 1" to "de 11 a 1",
        )
        for ((input, want) in cases) assertEquals(input, want, DraftRules.normalizeTimeRanges(input))
    }

    @Test fun rangeStartTimes_iosCases() {
        assertEquals(listOf(840, 1260), DraftRules.rangeStartTimes("hay treasure hours este sabado 14 de 2 a 4 y de 9 a 11"))
        assertEquals(listOf(900), DraftRules.rangeStartTimes("fútbol todos los sábados de 3 a 5"))
        assertEquals(emptyList<Int>(), DraftRules.rangeStartTimes("gym de 9 a 11"))
    }

    @Test fun advanceNotice_iosCases() {
        assertEquals(listOf(1440), DraftRules.advanceNoticeMinutes("avísame el 20 a las 10 que pague la luz, un día antes también"))
        assertEquals(listOf(30, 60), DraftRules.advanceNoticeMinutes("recuérdame 30 minutos antes y una hora antes"))
        assertEquals(listOf(120), DraftRules.advanceNoticeMinutes("remind me 2 hours before"))
        assertEquals(listOf(1440), DraftRules.advanceNoticeMinutes("call mom the day before"))
        assertEquals(emptyList<Int>(), DraftRules.advanceNoticeMinutes("Pon uma alarma mañana a las 8am"))
        assertEquals(listOf(1440), DraftRules.advanceNoticeMinutes("pagar a luz, um dia antes"))
    }

    @Test fun meansSingleDay_iosCases() {
        val cases = listOf(
            "hay treasure hours en fortnite este sabado 14 en peru de 2 a 4" to true,
            "este sábado a las 10 tengo dentista" to true,
            "Set an alarm this Friday at 7am for the gym" to true,
            "el próximo lunes a las 8" to true,
            "fútbol todos los sábados de 3 a 5" to false,
            "pon una alarma de mi daily todos los lunes a las 9" to false,
            "recuérdame sacar la basura los martes y jueves a las 8 pm" to false,
            "gym every Friday at 7" to false,
            "misa los domingos a las 8" to false,
            "Pon una alarma mañana a las 8am" to false,
            "cada viernes y este sábado" to false,
            "consulta na quinta às 15h" to true,
            "academia todas as segundas" to false,
        )
        for ((text, want) in cases) assertEquals(text, want, DraftRules.meansSingleDay(text))
    }

    @Test fun rollIntoFuture_onlyMovesSlipsUnder24h() {
        val nineToday = LocalDateTime.of(2026, 11, 10, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), DraftRules.rollIntoFuture(nineToday, RecurrenceRule.Once, now))
        assertEquals(LocalDateTime.of(2026, 11, 17, 9, 0), DraftRules.rollIntoFuture(nineToday, RecurrenceRule.Weekly(), now))
        assertEquals(LocalDateTime.of(2026, 12, 10, 9, 0), DraftRules.rollIntoFuture(nineToday, RecurrenceRule.Monthly(), now))
        val lastWeek = LocalDateTime.of(2026, 11, 2, 9, 0)
        assertEquals(lastWeek, DraftRules.rollIntoFuture(lastWeek, RecurrenceRule.Once, now))
        val future = LocalDateTime.of(2026, 11, 10, 20, 0)
        assertEquals(future, DraftRules.rollIntoFuture(future, RecurrenceRule.Once, now))
    }

    @Test fun resolveDate_snapsOntoNamedDay() {
        val rule = RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY))
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), DraftRules.resolveDate(LocalDateTime.of(2026, 11, 10, 9, 0), rule, now))
        val tue = RecurrenceRule.Weekly(1, setOf(Weekday.TUESDAY))
        assertEquals(LocalDateTime.of(2026, 11, 17, 9, 0), DraftRules.resolveDate(LocalDateTime.of(2026, 11, 10, 9, 0), tue, now))
        assertEquals(LocalDateTime.of(2026, 11, 10, 20, 0), DraftRules.resolveDate(LocalDateTime.of(2026, 11, 10, 20, 0), tue, now))
    }

    @Test fun defaultingDayEventTime_onlyBirthdaysAtMidnight() {
        val midnight = LocalDateTime.of(2027, 3, 15, 0, 0)
        assertEquals(midnight.withHour(9), DraftRules.defaultingDayEventTime(midnight, CategoryChoice.Builtin(ReminderCategory.BIRTHDAY)))
        assertEquals(midnight.withHour(9), DraftRules.defaultingDayEventTime(midnight, CategoryChoice.Builtin(ReminderCategory.ANNIVERSARY)))
        assertEquals(midnight, DraftRules.defaultingDayEventTime(midnight, CategoryChoice.REMINDER))
        val eight = LocalDateTime.of(2027, 3, 15, 8, 0)
        assertEquals(eight, DraftRules.defaultingDayEventTime(eight, CategoryChoice.Builtin(ReminderCategory.BIRTHDAY)))
    }

    @Test fun leadTimes_snapToClosestAndDedupe() {
        assertEquals(listOf(AlarmLeadTime.AT_START), DraftRules.leadTimes(null))
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.HOUR1, AlarmLeadTime.DAY1), DraftRules.leadTimes(listOf(1440, 0, 60, 61)))
        assertEquals(listOf(AlarmLeadTime.MIN30), DraftRules.leadTimes(listOf(25)))
        assertEquals(listOf(AlarmLeadTime.AT_START), DraftRules.leadTimes(listOf(-5)))
    }

    @Test fun recurrence_weekdaysAlwaysWeeklyExplicitOnceWins() {
        assertEquals(RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY)), DraftRules.recurrence("daily", setOf(Weekday.MONDAY)))
        assertEquals(RecurrenceRule.Once, DraftRules.recurrence("once", setOf(Weekday.SATURDAY)))
        assertEquals(RecurrenceRule.Weekly(2, setOf(Weekday.TUESDAY)), DraftRules.recurrence("weekly", setOf(Weekday.TUESDAY), 2))
        assertEquals(RecurrenceRule.Yearly(), DraftRules.recurrence("yearly", emptySet()))
        assertEquals(RecurrenceRule.Once, DraftRules.recurrence(null, emptySet()))
        assertEquals(RecurrenceRule.Once, DraftRules.recurrence("sometimes", emptySet()))
    }

    @Test fun parseIsoDate_naiveIsLocalAndOffsetConverts() {
        val zone = EvalClock.zone
        assertEquals(LocalDateTime.of(2026, 5, 25, 14, 0), DraftRules.parseIsoDate("2026-05-25T14:00:00", zone))
        assertEquals(LocalDateTime.of(2026, 5, 25, 14, 0), DraftRules.parseIsoDate("2026-05-25T14:00", zone))
        assertEquals(LocalDateTime.of(2026, 5, 25, 14, 0), DraftRules.parseIsoDate("2026-05-25 14:00", zone))
        // 19:00Z son las 14:00 en Lima (UTC−5).
        assertEquals(LocalDateTime.of(2026, 5, 25, 14, 0), DraftRules.parseIsoDate("2026-05-25T19:00:00Z", zone))
        assertEquals(LocalDate.of(2026, 5, 25).atStartOfDay(), DraftRules.parseIsoDate("2026-05-25", zone))
        assertNull(DraftRules.parseIsoDate("mañana", zone))
        assertNull(DraftRules.parseIsoDate(null, zone))
    }

    @Test fun willEverRing() {
        assertTrue(DraftRules.willEverRing(LocalDateTime.of(2026, 11, 11, 9, 0), RecurrenceRule.Once, now, EvalClock.zone))
        assertFalse(DraftRules.willEverRing(LocalDateTime.of(2026, 11, 1, 9, 0), RecurrenceRule.Once, now, EvalClock.zone))
        assertTrue(DraftRules.willEverRing(LocalDateTime.of(2026, 11, 1, 9, 0), RecurrenceRule.Daily(), now, EvalClock.zone))
    }

    @Test fun upcomingDaysText_marksTodayInThreeLanguages() {
        val text = DraftRules.upcomingDaysText(now.toLocalDate(), days = 5)
        val lines = text.lines()
        assertEquals(5, lines.size)
        assertTrue(lines[0], lines[0].startsWith("- 2026-11-10 = Tuesday / martes / terça-feira") && lines[0].endsWith("(today)"))
        assertTrue(lines[4], lines[4].contains("2026-11-14") && lines[4].contains("Saturday"))
    }
}
