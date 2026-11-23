package com.mathyusolutions.calarmi.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Un horario (fecha y hora + repetición) dentro de una alarma. Una alarma tiene
 * el principal (`Reminder.date` / `recurrence`) y puede sumar otros, para que
 * una sola suene p. ej. lunes y viernes a las 17 y el sábado a las 11.
 */
@Serializable
data class AlarmSchedule(
    val id: String = UUID.randomUUID().toString(),
    /** Instante en milisegundos desde 1970. */
    val date: Long,
    val recurrence: RecurrenceRule = RecurrenceRule.Once,
)
