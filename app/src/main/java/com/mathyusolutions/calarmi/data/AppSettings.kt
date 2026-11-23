package com.mathyusolutions.calarmi.data

import android.content.Context
import android.content.SharedPreferences
import com.mathyusolutions.calarmi.model.AlarmCountdown
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.AppearanceMode
import com.mathyusolutions.calarmi.model.SnoozeInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Los ajustes de la app, con los mismos valores por defecto que en iOS. */
data class Settings(
    val snooze: SnoozeInterval = SnoozeInterval.DEFAULT,
    /** Anticipación de las alarmas nuevas y de los eventos del calendario sin ajustes propios. */
    val defaultLeadTime: AlarmLeadTime = AlarmLeadTime.AT_START,
    /** Cuenta regresiva antes de que suene. Apagada por defecto. */
    val countdown: AlarmCountdown = AlarmCountdown.OFF,
    val alarmTone: AlarmTone = AlarmTone.DEFAULT,
    /** Interruptor general: apagado, no suena ninguna alarma. */
    val alarmsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    /** Alarmas para los eventos del calendario del teléfono. */
    val calendarAlarmsEnabled: Boolean = false,
    /** Botón «Unirse» para Teams, Zoom y Meet. */
    val meetingLinksEnabled: Boolean = true,
    /** Los calendarios que se miran. `null` = todos. */
    val selectedCalendarIds: Set<String>? = null,
    /** Sin alarma para eventos declinados o ajenos. */
    val onlyAttendingEvents: Boolean = false,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    /** Color de acento elegido; `null` = el naranja de Calarmi. */
    val accentColorHex: String? = null,
    /** Vibrar mientras suena. */
    val vibrate: Boolean = true,
) {
    companion object {
        /** Presets de color, el primero es el de la app. */
        val accentPresets = listOf(
            "#FF6633", "#007AFF", "#5856D6", "#AF52DE", "#FF2D55",
            "#FF3B30", "#34C759", "#00C7BE", "#FFCC00", "#8E8E93",
        )
        const val DEFAULT_ACCENT = "#FF6633"
    }
}

class SettingsStore(storage: Context) {
    private val prefs: SharedPreferences = storage.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        write(next)
        _state.value = next
    }

    private fun read(): Settings = Settings(
        snooze = SnoozeInterval.fromSeconds(prefs.getInt(K.SNOOZE, -1)) ?: SnoozeInterval.DEFAULT,
        defaultLeadTime = AlarmLeadTime.fromSeconds(prefs.getInt(K.LEAD, 0)) ?: AlarmLeadTime.AT_START,
        countdown = AlarmCountdown.fromSeconds(prefs.getInt(K.COUNTDOWN, 0)) ?: AlarmCountdown.OFF,
        alarmTone = AlarmTone.fromRaw(prefs.getString(K.TONE, null)) ?: AlarmTone.DEFAULT,
        alarmsEnabled = prefs.getBoolean(K.ALARMS, true),
        onboardingCompleted = prefs.getBoolean(K.ONBOARDING, false),
        calendarAlarmsEnabled = prefs.getBoolean(K.CALENDAR, false),
        meetingLinksEnabled = prefs.getBoolean(K.MEETINGS, true),
        selectedCalendarIds = if (prefs.contains(K.CALENDARS)) prefs.getStringSet(K.CALENDARS, emptySet())!!.toSet() else null,
        onlyAttendingEvents = prefs.getBoolean(K.ONLY_ATTENDING, false),
        appearance = AppearanceMode.fromRaw(prefs.getInt(K.APPEARANCE, 0)),
        accentColorHex = prefs.getString(K.ACCENT, null),
        vibrate = prefs.getBoolean(K.VIBRATE, true),
    )

    private fun write(s: Settings) {
        prefs.edit().apply {
            putInt(K.SNOOZE, s.snooze.seconds)
            putInt(K.LEAD, s.defaultLeadTime.seconds)
            putInt(K.COUNTDOWN, s.countdown.seconds)
            putString(K.TONE, s.alarmTone.raw)
            putBoolean(K.ALARMS, s.alarmsEnabled)
            putBoolean(K.ONBOARDING, s.onboardingCompleted)
            putBoolean(K.CALENDAR, s.calendarAlarmsEnabled)
            putBoolean(K.MEETINGS, s.meetingLinksEnabled)
            if (s.selectedCalendarIds == null) remove(K.CALENDARS) else putStringSet(K.CALENDARS, s.selectedCalendarIds)
            putBoolean(K.ONLY_ATTENDING, s.onlyAttendingEvents)
            putInt(K.APPEARANCE, s.appearance.raw)
            if (s.accentColorHex == null) remove(K.ACCENT) else putString(K.ACCENT, s.accentColorHex)
            putBoolean(K.VIBRATE, s.vibrate)
        }.apply()
    }

    private object K {
        const val SNOOZE = "settings.snooze"
        const val LEAD = "settings.defaultLeadTime"
        const val COUNTDOWN = "settings.countdown"
        const val TONE = "settings.alarmTone"
        const val ALARMS = "settings.alarmsEnabled"
        const val ONBOARDING = "settings.onboardingCompleted"
        const val CALENDAR = "settings.calendarAlarmsEnabled"
        const val MEETINGS = "settings.meetingLinksEnabled"
        const val CALENDARS = "settings.selectedCalendarIDs"
        const val ONLY_ATTENDING = "settings.onlyAttendingEvents"
        const val APPEARANCE = "settings.appearance"
        const val ACCENT = "settings.accentColorHex"
        const val VIBRATE = "settings.vibrate"
    }
}
