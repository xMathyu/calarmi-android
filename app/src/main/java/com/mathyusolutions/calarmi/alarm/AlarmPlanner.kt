package com.mathyusolutions.calarmi.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mathyusolutions.calarmi.MainActivity
import com.mathyusolutions.calarmi.calendar.CalendarAlarmEvent
import com.mathyusolutions.calarmi.calendar.CalendarAlarmSource
import com.mathyusolutions.calarmi.data.CategoryDao
import com.mathyusolutions.calarmi.data.ReminderDao
import com.mathyusolutions.calarmi.data.SettingsStore
import com.mathyusolutions.calarmi.domain.nextFireAfter
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decide qué suena y cuándo, y deja programado en el sistema SOLO el próximo
 * disparo. Es más robusto que programar cientos de alarmas sueltas (lo que en
 * iOS obliga a reconciliar con AlarmKit): cada vez que algo cambia —una alarma,
 * un ajuste, la hora del teléfono, un reinicio— se recalcula desde los datos.
 *
 * `setAlarmClock` es la API para despertadores: el sistema la cumple en reposo
 * profundo, enseña el ícono de alarma y la próxima hora en la pantalla de bloqueo.
 */
class AlarmPlanner(
    private val context: Context,
    private val reminders: ReminderDao,
    private val categories: CategoryDao,
    private val settings: SettingsStore,
    private val state: AlarmStateStore,
    private val calendar: CalendarAlarmSource,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** ¿Puede la app programar alarmas exactas? (con `USE_EXACT_ALARM` siempre sí). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Todo lo que suena en `(from, to]`, en orden. */
    suspend fun firesBetween(from: Long, to: Long): List<RingItem> {
        if (to <= from) return emptyList()
        val s = settings.current
        val result = mutableListOf<RingItem>()
        if (s.alarmsEnabled) {
            val cats = categories.all().associateBy { it.id }
            for (reminder in reminders.all()) {
                if (!reminder.isEnabled) continue
                var cursor = from
                var guard = 0
                while (guard++ < 32) {
                    val fire = reminder.nextFireAfter(cursor) ?: break
                    if (fire > to) break
                    result += ringItem(reminder, fire, cats)
                    cursor = fire
                }
            }
            for (event in calendarEvents(from, to)) {
                for (lead in event.leadTimes) {
                    val fire = event.begin - lead.millis
                    if (fire > from && fire <= to) result += ringItem(event, fire)
                }
            }
        }
        // Lo pospuesto suena aunque el interruptor general se apague después.
        state.snoozes().filter { it.fireAt > from && it.fireAt <= to }.forEach { result += it.item }
        val skipped = state.skipped()
        return result.filter { it.key !in skipped }.sortedBy { it.fireAt }
    }

    /** El próximo disparo después de `after`, o `null` si no queda ninguno. */
    suspend fun nextFireAfter(after: Long): RingItem? {
        val s = settings.current
        val candidates = mutableListOf<RingItem>()
        val skipped = state.skipped()
        if (s.alarmsEnabled) {
            val cats = categories.all().associateBy { it.id }
            for (reminder in reminders.all()) {
                if (!reminder.isEnabled) continue
                var cursor = after
                for (attempt in 0 until 4) {
                    val fire = reminder.nextFireAfter(cursor) ?: break
                    val item = ringItem(reminder, fire, cats)
                    if (item.key !in skipped) {
                        candidates += item
                        break
                    }
                    cursor = fire
                }
            }
            calendarEvents(after, after + CALENDAR_WINDOW).forEach { event ->
                event.leadTimes.map { event.begin - it.millis }.filter { it > after }.forEach { fire ->
                    val item = ringItem(event, fire)
                    if (item.key !in skipped) candidates += item
                }
            }
        }
        state.snoozes().filter { it.fireAt > after }.forEach { candidates += it.item }
        return candidates.minByOrNull { it.fireAt }
    }

    /** Recalcula y deja programado el próximo disparo (y su cuenta regresiva previa). */
    suspend fun reschedule() = mutex.withLock {
        val now = System.currentTimeMillis()
        // Si el reloj retrocedió (se corrigió la hora, o volvió atrás al reiniciar),
        // lo «ya atendido» quedaría en el futuro y se saltaría las alarmas de hoy.
        if (state.lastHandledAt > now + CLOCK_SLACK) state.lastHandledAt = now
        state.pruneSnoozes(now - STALE_SNOOZE)
        val next = nextFireAfter(maxOf(now, state.lastHandledAt))
        val fireIntent = AlarmReceiver.firePendingIntent(context)
        val preIntent = AlarmReceiver.preAlertPendingIntent(context)
        alarmManager.cancel(preIntent)
        if (next == null) {
            alarmManager.cancel(fireIntent)
            state.scheduledFor = 0L
            Log.i(TAG, "Sin alarmas por delante")
            return@withLock
        }
        val show = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            if (canScheduleExact()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next.fireAt, show), fireIntent)
            } else {
                // Sin permiso de alarmas exactas (solo Android 12 si lo revocan): lo mejor posible.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.fireAt, fireIntent)
            }
            state.scheduledFor = next.fireAt
            val countdown = settings.current.countdown
            val preAt = next.fireAt - countdown.millis
            if (countdown.millis > 0 && preAt > now && canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAt, preIntent)
            }
            Log.i(TAG, "Próxima alarma: ${next.title} a las ${java.util.Date(next.fireAt)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "No se pudo programar la alarma", e)
        }
    }

    private suspend fun calendarEvents(from: Long, to: Long): List<CalendarAlarmEvent> {
        if (!settings.current.calendarAlarmsEnabled) return emptyList()
        // Un aviso puede llegar hasta una semana antes del evento.
        return runCatching { calendar.alarmEvents(from, to + MAX_LEAD) }
            .onFailure { Log.w(TAG, "No se pudo leer el calendario", it) }
            .getOrDefault(emptyList())
    }

    private fun ringItem(reminder: Reminder, fire: Long, cats: Map<String, CustomCategory>): RingItem {
        val occurrence = reminder.sortedLeadTimes
            .map { fire + it.millis }
            .firstOrNull { occ ->
                reminder.allSchedules.any { sched ->
                    com.mathyusolutions.calarmi.domain.RecurrenceEngine
                        .nextOccurrences(sched.recurrence, sched.date, 1, occ - 1).firstOrNull() == occ
                }
            } ?: fire
        val custom = reminder.customCategoryId?.let(cats::get)
        val color = custom?.colorHex ?: reminder.category.tint.toHex()
        return RingItem(
            key = "${RingItem.reminderSource(reminder.id)}@$fire",
            sourceId = RingItem.reminderSource(reminder.id),
            fireAt = fire,
            occurrenceAt = occurrence,
            title = reminder.title,
            toneRaw = (reminder.tone ?: settings.current.alarmTone).raw,
            challenge = reminder.readyChallenge,
            iconKindRaw = reminder.iconKindRaw,
            symbol = reminder.symbolName ?: custom?.iconValue ?: reminder.category.defaultSymbol,
            photoPath = reminder.photoPath,
            colorHex = color,
        )
    }

    private fun ringItem(event: CalendarAlarmEvent, fire: Long): RingItem {
        val source = RingItem.eventSource(event.eventId, event.begin)
        return RingItem(
            key = "$source@$fire",
            sourceId = source,
            fireAt = fire,
            occurrenceAt = event.begin,
            title = event.title,
            toneRaw = settings.current.alarmTone.raw,
            meetingUrl = event.meetingUrl,
            location = event.location,
            symbol = if (event.meetingUrl != null) "video.fill" else "calendar",
            colorHex = event.colorHex ?: "#007AFF",
        )
    }

    companion object {
        private const val TAG = "AlarmPlanner"
        private const val MAX_LEAD = 7L * 24 * 3600 * 1000
        private const val CALENDAR_WINDOW = 8L * 24 * 3600 * 1000
        private const val STALE_SNOOZE = 60L * 60 * 1000
        private const val CLOCK_SLACK = 60L * 1000
    }
}

fun androidx.compose.ui.graphics.Color.toHex(): String {
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )
    return String.format("#%06X", argb and 0xFFFFFF)
}
