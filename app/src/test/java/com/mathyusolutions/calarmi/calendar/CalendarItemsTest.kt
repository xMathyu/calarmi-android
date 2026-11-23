package com.mathyusolutions.calarmi.calendar

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La rejilla del mes, lo que va en cada día y las reglas de avisos de los eventos. */
class CalendarItemsTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val locale = Locale.forLanguageTag("es-MX")

    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    private fun event(id: Long, begin: Long) = CalendarEvent(
        eventId = id, title = "Evento $id", begin = begin, end = begin + 3_600_000, location = null, notes = null,
        organizer = null, calendarId = "1", colorHex = "#FF0000", meetingLink = null, isParticipating = true,
    )

    // region MonthGrid

    @Test fun noviembre2026EmpiezaEnDomingo() {
        // 1 de noviembre de 2026 es domingo: con la semana en lunes, seis huecos antes.
        val monday = MonthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(6, monday.weeks.size)
        assertEquals(List(6) { null } + LocalDate.of(2026, 11, 1), monday.weeks[0])
        assertEquals(LocalDate.of(2026, 11, 30), monday.weeks.last()[0])
        assertTrue(monday.weeks.all { it.size == 7 })

        // Con la semana en domingo cabe en cinco semanas y empieza sin huecos.
        val sunday = MonthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(5, sunday.weeks.size)
        assertEquals(LocalDate.of(2026, 11, 1), sunday.weeks[0][0])
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), sunday.weekdays.take(2))
    }

    @Test fun febreroBisiestoTieneSusVeintinueveDias() {
        val grid = MonthGrid(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        val days = grid.weeks.flatten().filterNotNull()
        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2028, 2, 29), days.last())
    }

    @Test fun elRangoCubreElMesEntero() {
        val grid = MonthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(at("2026-11-01T00:00"), grid.start(zone))
        assertEquals(at("2026-12-01T00:00"), grid.end(zone))
    }

    // endregion

    // region CalendarItems

    @Test fun alarmaDiariaSaleCadaDiaDelMes() {
        val reminder = Reminder(id = "a", date = at("2026-10-15T07:00"), recurrence = RecurrenceRule.Daily())
        val items = CalendarItems.alarms(reminder.let(::listOf), at("2026-11-01T00:00"), at("2026-12-01T00:00"), zone, locale)
        assertEquals(30, items.size)
        assertEquals(at("2026-11-01T07:00"), items.first().occurrence)
    }

    @Test fun dosHorariosQueCoincidenCuentanUnaVez() {
        val date = at("2026-11-10T09:00")
        val reminder = Reminder(
            id = "b", date = date,
            additionalSchedules = listOf(AlarmSchedule(date = date), AlarmSchedule(date = at("2026-11-11T09:00"))),
        )
        val items = CalendarItems.alarms(listOf(reminder), at("2026-11-01T00:00"), at("2026-12-01T00:00"), zone, locale)
        assertEquals(listOf(date, at("2026-11-11T09:00")), items.map { it.occurrence })
    }

    @Test fun porDiaEnOrdenYLaAlarmaAntesQueElEventoALaMismaHora() {
        val nine = at("2026-11-23T09:00")
        val alarm = CalendarItems.alarms(listOf(Reminder(id = "c", date = nine)), at("2026-11-01T00:00"), at("2026-12-01T00:00"), zone, locale)
        val events = listOf(event(2, at("2026-11-23T08:00")), event(1, nine), event(3, at("2026-11-24T10:00")))
        val byDay = CalendarItems.byDay(alarm, events, zone)

        val monday = byDay.getValue(LocalDate.of(2026, 11, 23))
        assertEquals(3, monday.size)
        assertEquals(at("2026-11-23T08:00"), monday[0].time)
        assertTrue(monday[1] is CalendarItem.Alarm)
        assertTrue(monday[2] is CalendarItem.Event)
        assertEquals(1, byDay.getValue(LocalDate.of(2026, 11, 24)).size)
    }

    // endregion

    // region EventAlerts

    @Test fun sinAjustesSigueAlPredeterminado() {
        assertEquals(listOf(AlarmLeadTime.MIN10), EventAlerts.configured(null, AlarmLeadTime.MIN10))
        assertEquals(listOf(AlarmLeadTime.MIN10), EventAlerts.active(null, AlarmLeadTime.MIN10, onlyAttending = false, isParticipating = true))
    }

    @Test fun conAvisosPropiosIgnoraElPredeterminado() {
        val own = EventPrefs(listOf(900, 0))
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.MIN15), EventAlerts.configured(own, AlarmLeadTime.HOUR1))
        assertEquals(2, EventAlerts.active(own, AlarmLeadTime.HOUR1, onlyAttending = true, isParticipating = false).size)
    }

    @Test fun apagadoNoSuena() {
        val own = EventPrefs(listOf(300), enabled = false)
        assertTrue(EventAlerts.active(own, AlarmLeadTime.AT_START, onlyAttending = false, isParticipating = true).isEmpty())
    }

    @Test fun soloAsistoDejaSinAlarmaLoAjenoQueNuncaSeConfiguro() {
        assertTrue(EventAlerts.skippedByAttendance(null, onlyAttending = true, isParticipating = false))
        assertTrue(EventAlerts.active(null, AlarmLeadTime.AT_START, onlyAttending = true, isParticipating = false).isEmpty())
        assertFalse(EventAlerts.skippedByAttendance(EventPrefs(listOf(0)), onlyAttending = true, isParticipating = false))
        assertFalse(EventAlerts.skippedByAttendance(null, onlyAttending = false, isParticipating = false))
    }

    @Test fun guardarSinAvisosLoApaga() {
        // El motor leería una lista vacía y encendida como «el predeterminado».
        val saved = EventAlerts.toSave(emptyList(), enabled = true)
        assertFalse(saved.enabled)
        assertTrue(EventAlerts.active(saved, AlarmLeadTime.AT_START, onlyAttending = false, isParticipating = true).isEmpty())
        assertTrue(EventAlerts.configured(saved, AlarmLeadTime.AT_START).isEmpty())
    }

    @Test fun guardarOrdenaYQuitaRepetidos() {
        val saved = EventAlerts.toSave(listOf(AlarmLeadTime.HOUR1, AlarmLeadTime.MIN5, AlarmLeadTime.MIN5), enabled = true)
        assertEquals(listOf(300, 3600), saved.leadTimes)
        assertTrue(saved.enabled)
    }

    @Test fun listaVaciaYEncendidaSeVeComoElPredeterminado() {
        // Lo mismo que hace `CalendarRepository.alarmEvents`.
        val own = EventPrefs(emptyList(), enabled = true)
        assertEquals(listOf(AlarmLeadTime.MIN30), EventAlerts.configured(own, AlarmLeadTime.MIN30))
        assertEquals(listOf(AlarmLeadTime.MIN30), EventAlerts.active(own, AlarmLeadTime.MIN30, onlyAttending = false, isParticipating = true))
    }

    // endregion
}
