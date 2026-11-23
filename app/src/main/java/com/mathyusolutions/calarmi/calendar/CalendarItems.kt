package com.mathyusolutions.calarmi.calendar

import com.mathyusolutions.calarmi.domain.RecurrenceEngine
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

//
// Lo que la vista de mes pinta en un día: una ocurrencia de una alarma propia
// o un evento del calendario del teléfono, ordenables por hora entre sí. Y la
// rejilla del mes (semanas × días) que los acomoda. Sin nada de Android para
// poder probarlo en la JVM.
//

/** Una ocurrencia de alarma propia o un evento del calendario. */
sealed interface CalendarItem {
    val time: Long
    /** Identidad estable para las listas perezosas. */
    val key: String

    data class Alarm(val reminder: Reminder, val occurrence: Long) : CalendarItem {
        override val time: Long get() = occurrence
        override val key: String get() = "alarm:${reminder.id}:$occurrence"
    }

    data class Event(val event: CalendarEvent) : CalendarItem {
        override val time: Long get() = event.begin
        // El id de Android se repite en cada ocurrencia de un evento que se repite:
        // el inicio las distingue.
        override val key: String get() = "event:${event.eventId}:${event.begin}"
    }
}

object CalendarItems {
    /**
     * Cada ocurrencia de cada horario de `reminders` en `[start, end)`, una por
     * instante distinto: dos horarios que coinciden cuentan una vez, porque
     * suenan una vez.
     */
    fun alarms(
        reminders: List<Reminder>,
        start: Long,
        end: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<CalendarItem.Alarm> {
        val seen = HashSet<String>()
        val items = ArrayList<CalendarItem.Alarm>()
        for (reminder in reminders) {
            for (schedule in reminder.allSchedules) {
                for (occurrence in RecurrenceEngine.occurrences(schedule.recurrence, schedule.date, start, end, zone, locale)) {
                    val item = CalendarItem.Alarm(reminder, occurrence)
                    if (seen.add(item.key)) items += item
                }
            }
        }
        return items
    }

    /**
     * Alarmas y eventos juntos, agrupados por día y en orden de hora. A la misma
     * hora la alarma va antes que el evento: es lo que suena.
     */
    fun byDay(
        alarms: List<CalendarItem.Alarm>,
        events: List<CalendarEvent>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<CalendarItem>> {
        val all: List<CalendarItem> = alarms + events.map { CalendarItem.Event(it) }
        return all
            .sortedWith(compareBy<CalendarItem> { it.time }.thenBy { if (it is CalendarItem.Alarm) 0 else 1 })
            .groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
    }
}

/**
 * Las semanas de un mes para una rejilla de 7 columnas, empezando en el primer
 * día de la semana del idioma. `null` rellena la primera y la última semana.
 */
data class MonthGrid(val month: YearMonth, val firstDayOfWeek: DayOfWeek) {
    val weeks: List<List<LocalDate?>> = run {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(leading) { cells += null }
        for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
        while (cells.size % 7 != 0) cells += null
        cells.chunked(7)
    }

    /** Los días de la semana en el orden de las columnas. */
    val weekdays: List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

    /** Primer instante del mes. */
    fun start(zone: ZoneId = ZoneId.systemDefault()): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Primer instante del mes siguiente (fin exclusivo). */
    fun end(zone: ZoneId = ZoneId.systemDefault()): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * Qué avisos tiene un evento del calendario. Mismas reglas que
 * `CalendarRepository.alarmEvents` (lo que de verdad programa el motor), para
 * que lo que se ve en la pestaña sea lo que suena.
 */
object EventAlerts {
    /** Los avisos que se editan en la pantalla del evento: los suyos o el predeterminado. */
    fun configured(own: EventPrefs?, default: AlarmLeadTime): List<AlarmLeadTime> {
        if (own == null) return listOf(default)
        val leads = own.leadTimes.mapNotNull(AlarmLeadTime::fromSeconds).distinct().sortedBy { it.seconds }
        // Una lista vacía y encendida el motor la trata como «el predeterminado»;
        // vacía y apagada es «sin avisos» (así la guarda esta pantalla).
        return if (leads.isEmpty() && own.enabled) listOf(default) else leads
    }

    /** Lo que de verdad suena (vacío = sin alarma). */
    fun active(own: EventPrefs?, default: AlarmLeadTime, onlyAttending: Boolean, isParticipating: Boolean): List<AlarmLeadTime> {
        if (own != null && !own.enabled) return emptyList()
        if (skippedByAttendance(own, onlyAttending, isParticipating)) return emptyList()
        return own?.leadTimes?.mapNotNull(AlarmLeadTime::fromSeconds)?.ifEmpty { null } ?: listOf(default)
    }

    /**
     * El evento queda sin alarma por la regla global, no por sus propios
     * ajustes: no es suyo y nunca lo configuró a mano.
     */
    fun skippedByAttendance(own: EventPrefs?, onlyAttending: Boolean, isParticipating: Boolean): Boolean =
        own == null && onlyAttending && !isParticipating

    /**
     * Lo que se guarda al tocar «Guardar». Sin avisos se guarda apagado: el
     * motor leería una lista vacía como «el predeterminado» y sonaría igual.
     */
    fun toSave(leadTimes: List<AlarmLeadTime>, enabled: Boolean): EventPrefs =
        if (leadTimes.isEmpty()) EventPrefs(emptyList(), enabled = false)
        else EventPrefs(leadTimes.map { it.seconds }.distinct().sorted(), enabled)
}
