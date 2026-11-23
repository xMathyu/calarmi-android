package com.mathyusolutions.calarmi.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.mathyusolutions.calarmi.data.AppJson
import com.mathyusolutions.calarmi.data.SettingsStore
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Un calendario del teléfono que se puede elegir en Ajustes. */
data class CalendarInfo(val id: String, val name: String, val account: String, val colorHex: String?)

/** Un evento del calendario (una ocurrencia). */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String?,
    val notes: String?,
    val organizer: String?,
    val calendarId: String,
    val colorHex: String?,
    val meetingLink: MeetingLink?,
    /** Lo creó la persona o está invitada sin haberlo declinado. */
    val isParticipating: Boolean,
) {
    /** Clave de las preferencias por evento: vale para todas sus repeticiones. */
    val prefsKey: String get() = eventId.toString()
}

/** Lo que la persona eligió para un evento: sus avisos, o sin alarma. */
@Serializable
data class EventPrefs(val leadTimes: List<Int>, val enabled: Boolean = true)

/**
 * Lee el calendario del teléfono (Google Calendar, Outlook, Samsung… lo que
 * esté sincronizado) mediante `CalendarContract`. Solo lectura: Calarmi nunca
 * escribe en el calendario.
 */
class CalendarRepository(private val context: Context, private val settings: SettingsStore) : CalendarAlarmSource {
    private val prefsStore = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("event_prefs", Context.MODE_PRIVATE)
    private val _eventPrefs = MutableStateFlow(loadPrefs())
    /** Avisos por evento; un evento sin entrada hereda el aviso predeterminado de Ajustes. */
    val eventPrefs: StateFlow<Map<String, EventPrefs>> = _eventPrefs.asStateFlow()

    /** Cambia cada vez que algo del calendario cambia (para refrescar pantallas). */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun notifyChanged() {
        _revision.value++
    }

    /** Los calendarios visibles, sin festivos ni cumpleaños de contactos. */
    suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.VISIBLE,
        )
        val result = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val owner = c.getString(4).orEmpty()
                    if (c.getInt(5) == 0 || isInformational(owner)) continue
                    result += CalendarInfo(
                        id = c.getLong(0).toString(),
                        name = c.getString(1).orEmpty(),
                        account = c.getString(2).orEmpty(),
                        colorHex = colorHex(c.getInt(3)),
                    )
                }
            }
        }
        result.sortedWith(compareBy({ it.account }, { it.name }))
    }

    /** Eventos (no de todo el día) que empiezan en `[from, to)`, de los calendarios elegidos. */
    suspend fun events(from: Long, to: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission() || to <= from) return@withContext emptyList()
        val available = calendars()
        val wanted = calendarsToRead(settings.current.selectedCalendarIds, available.map { it.id }) ?: available.map { it.id }.toSet()
        if (wanted.isEmpty()) return@withContext emptyList()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ORGANIZER,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.OWNER_ACCOUNT,
            CalendarContract.Instances.HAS_ATTENDEE_DATA,
            CalendarContract.Instances.STATUS,
        )
        val result = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(4) == 1) continue // todo el día
                    if (c.getInt(13) == CalendarContract.Events.STATUS_CANCELED) continue
                    val calendarId = c.getLong(8).toString()
                    if (calendarId !in wanted) continue
                    val begin = c.getLong(2)
                    if (begin < from || begin >= to) continue
                    val location = c.getString(5)?.takeIf { it.isNotBlank() }
                    val notes = c.getString(6)?.takeIf { it.isNotBlank() }
                    val organizer = c.getString(7)
                    val owner = c.getString(11)
                    val selfStatus = c.getInt(10)
                    val hasAttendees = c.getInt(12) == 1
                    val participating = when {
                        organizer != null && owner != null && organizer.equals(owner, ignoreCase = true) -> true
                        selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> false
                        selfStatus != CalendarContract.Attendees.ATTENDEE_STATUS_NONE -> true
                        else -> !hasAttendees
                    }
                    result += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: context.getString(com.mathyusolutions.calarmi.R.string.calendar_untitled_event),
                        begin = begin,
                        end = c.getLong(3),
                        location = location,
                        notes = notes,
                        organizer = organizer,
                        calendarId = calendarId,
                        colorHex = colorHex(c.getInt(9)),
                        meetingLink = MeetingLinkDetector.extract(location, notes),
                        isParticipating = participating,
                    )
                }
            }
        }
        result
    }

    override suspend fun alarmEvents(from: Long, to: Long): List<CalendarAlarmEvent> {
        val s = settings.current
        if (!s.calendarAlarmsEnabled || !hasPermission()) return emptyList()
        val prefs = _eventPrefs.value
        return events(from, to).mapNotNull { event ->
            val own = prefs[event.prefsKey]
            if (own != null && !own.enabled) return@mapNotNull null
            // Sin ajustes propios, un evento declinado o ajeno no suena (si el ajuste lo pide).
            if (own == null && s.onlyAttendingEvents && !event.isParticipating) return@mapNotNull null
            val leads = own?.leadTimes?.mapNotNull(AlarmLeadTime::fromSeconds)?.ifEmpty { null }
                ?: listOf(s.defaultLeadTime)
            CalendarAlarmEvent(
                eventId = event.eventId,
                title = event.title,
                begin = event.begin,
                end = event.end,
                leadTimes = leads,
                meetingUrl = event.meetingLink?.url?.takeIf { s.meetingLinksEnabled },
                location = event.location?.takeIf { event.meetingLink == null || !event.location.contains(event.meetingLink.url) },
                colorHex = event.colorHex,
            )
        }
    }

    fun prefs(event: CalendarEvent): EventPrefs? = _eventPrefs.value[event.prefsKey]

    /** Avisos propios de un evento (hasta 3). `null` vuelve al predeterminado. */
    fun setPrefs(event: CalendarEvent, prefs: EventPrefs?) {
        val next = _eventPrefs.value.toMutableMap()
        if (prefs == null) next.remove(event.prefsKey) else next[event.prefsKey] = prefs.copy(leadTimes = prefs.leadTimes.distinct().sorted().take(MAX_ALARMS_PER_EVENT))
        _eventPrefs.value = next
        prefsStore.edit().putString(K_PREFS, AppJson.encodeToString(MapSerializer(String.serializer(), EventPrefs.serializer()), next)).apply()
    }

    private fun loadPrefs(): Map<String, EventPrefs> = prefsStore.getString(K_PREFS, null)?.let {
        runCatching { AppJson.decodeFromString(MapSerializer(String.serializer(), EventPrefs.serializer()), it) }.getOrNull()
    } ?: emptyMap()

    companion object {
        const val MAX_ALARMS_PER_EVENT = 3
        private const val K_PREFS = "eventPrefs.v1"

        /** Festivos y cumpleaños de contactos: no son eventos que alguien agendó. */
        fun isInformational(owner: String): Boolean =
            owner.contains("#holiday@") || owner.contains("#contacts@") || owner.contains("#weeknum@") ||
                owner.endsWith("@group.v.calendar.google.com") && owner.contains("#")

        /**
         * Qué calendarios leer según la selección guardada (`null` = todos). Una
         * selección cuyos ids ya no existen (se quitó y volvió a poner la cuenta)
         * no cuenta como «ninguno»: se leen todos. Sonar de más se ve; no sonar, no.
         */
        fun calendarsToRead(selection: Set<String>?, available: List<String>): Set<String>? {
            if (selection == null) return null
            if (selection.isEmpty()) return emptySet()
            val alive = selection.intersect(available.toSet())
            return alive.ifEmpty { null }
        }

        fun colorHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    }
}
