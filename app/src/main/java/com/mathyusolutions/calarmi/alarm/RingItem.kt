package com.mathyusolutions.calarmi.alarm

import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlinx.serialization.Serializable

/** Una alarma que suena (o va a sonar): todo lo que la pantalla y el servicio necesitan. */
@Serializable
data class RingItem(
    /** Única por disparo: fuente + hora. */
    val key: String,
    /** La alarma (`reminder:<id>`) o el evento del calendario (`event:<id>:<inicio>`). */
    val sourceId: String,
    val fireAt: Long,
    /** La hora del evento: con aviso previo es posterior a `fireAt`. */
    val occurrenceAt: Long,
    val title: String,
    val toneRaw: String,
    val challenge: WakeChallenge? = null,
    val meetingUrl: String? = null,
    val location: String? = null,
    val iconKindRaw: Int = 0,
    val symbol: String? = null,
    val photoPath: String? = null,
    val colorHex: String = "#FF6633",
    val isSnooze: Boolean = false,
) {
    val reminderId: String? get() = sourceId.takeIf { it.startsWith(REMINDER_PREFIX) }?.removePrefix(REMINDER_PREFIX)
    val isEvent: Boolean get() = sourceId.startsWith(EVENT_PREFIX)
    val leadMillis: Long get() = (occurrenceAt - fireAt).coerceAtLeast(0)

    companion object {
        const val REMINDER_PREFIX = "reminder:"
        const val EVENT_PREFIX = "event:"
        fun reminderSource(id: String) = REMINDER_PREFIX + id
        fun eventSource(eventId: Long, begin: Long) = "$EVENT_PREFIX$eventId:$begin"
    }
}
