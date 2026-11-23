package com.mathyusolutions.calarmi.calendar

import com.mathyusolutions.calarmi.model.AlarmLeadTime

/** Un evento del calendario del teléfono que lleva alarma. */
data class CalendarAlarmEvent(
    val eventId: Long,
    val title: String,
    /** Inicio de ESTA ocurrencia (los eventos que se repiten traen una por día). */
    val begin: Long,
    val end: Long,
    val leadTimes: List<AlarmLeadTime>,
    /** Enlace de Teams, Zoom o Meet, si lo tiene y el ajuste está activo. */
    val meetingUrl: String?,
    val location: String?,
    /** Color del calendario, `#RRGGBB`. */
    val colorHex: String?,
)

/** Lo que el motor de alarmas necesita del calendario. */
interface CalendarAlarmSource {
    /** Eventos con alarma cuyo INICIO cae en `[from, to)`. Vacío sin permiso o con el ajuste apagado. */
    suspend fun alarmEvents(from: Long, to: Long): List<CalendarAlarmEvent>
}
