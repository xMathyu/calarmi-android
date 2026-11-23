package com.mathyusolutions.calarmi.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mathyusolutions.calarmi.CalarmiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Recibe la alarma del sistema: hace sonar lo que toque y programa la siguiente. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = CalarmiApp.container(context)
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, container)
                    ACTION_PRE_ALERT -> {
                        val next = container.planner.nextFireAfter(System.currentTimeMillis())
                        if (next != null) AlarmNotifications.showUpcoming(context, next)
                    }
                    ACTION_SKIP -> {
                        intent.getStringExtra(EXTRA_KEY)?.let(container.alarmState::skip)
                        AlarmNotifications.cancelUpcoming(context)
                        container.planner.reschedule()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al atender la alarma", e)
                runCatching { container.planner.reschedule() }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, container: com.mathyusolutions.calarmi.AppContainer) {
        val now = System.currentTimeMillis()
        val state = container.alarmState
        // Lo que llegue muy tarde (teléfono apagado, proceso congelado) no suena de golpe.
        // Un «ya atendido» en el futuro es de un reloj que retrocedió: no cuenta.
        val handled = state.lastHandledAt.takeIf { it <= now + EARLY_SLACK } ?: now - 1
        val from = maxOf(handled, now - LATE_LIMIT)
        val to = now + EARLY_SLACK
        val due = container.planner.firesBetween(from, to)
        state.lastHandledAt = to
        state.removeSnoozes(due.filter { it.isSnooze }.map { it.key }.toSet())
        AlarmNotifications.cancelUpcoming(context)
        // Varios avisos de la misma alarma a la vez suenan una sola vez.
        val unique = due.distinctBy { it.sourceId }
        if (unique.isNotEmpty()) {
            Log.i(TAG, "Suenan ${unique.size}: ${unique.joinToString { it.title }}")
            RingService.ring(context, unique)
        }
        container.planner.reschedule()
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_FIRE = "com.mathyusolutions.calarmi.action.FIRE"
        const val ACTION_PRE_ALERT = "com.mathyusolutions.calarmi.action.PRE_ALERT"
        const val ACTION_SKIP = "com.mathyusolutions.calarmi.action.SKIP"
        const val EXTRA_KEY = "key"
        private const val LATE_LIMIT = 10L * 60 * 1000
        private const val EARLY_SLACK = 1_500L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun firePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun preAlertPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_PRE_ALERT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun skipPendingIntent(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_SKIP).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Reprograma tras un reinicio, un cambio de hora o una actualización de la app. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = CalarmiApp.container(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                        // Lo que debía sonar con el teléfono apagado no suena todo junto al
                        // encender. Y si el reloj volvió atrás, lo atendido «en el futuro» se olvida.
                        container.alarmState.lastHandledAt = System.currentTimeMillis()
                    }
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        container.alarmState.lastHandledAt = System.currentTimeMillis()
                    }
                }
                container.planner.reschedule()
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    container.onUserUnlocked()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
