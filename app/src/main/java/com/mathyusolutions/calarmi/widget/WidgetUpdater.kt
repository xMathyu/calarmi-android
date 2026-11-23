package com.mathyusolutions.calarmi.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.mathyusolutions.calarmi.CalarmiApp
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Refresca los widgets tras cualquier cambio de datos (lo llaman los
 * repositorios, `AppContainer.onDataChanged` y cada vuelta a la app).
 *
 * Se puede llamar tan seguido como se quiera: las llamadas seguidas (el editor
 * guarda, se reprograma, vuelve la app…) se juntan en un solo refresco.
 *
 * Antes del primer desbloqueo no hace nada: Glance guarda su estado y WorkManager
 * su base en el almacenamiento normal, que todavía no se puede leer. Al
 * desbloquear, el sistema manda `APPWIDGET_UPDATE` a cada widget y estos se
 * redibujan solos.
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    /** Lo que se espera para juntar llamadas seguidas. */
    private const val DEBOUNCE_MS = 400L
    /** Un poco después de medianoche, para no caer en el día que termina. */
    private const val MIDNIGHT_MARGIN_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<Context>(Channel.CONFLATED)
    private val started = AtomicBoolean(false)
    /** Ya se canceló el refresco programado porque no hay widgets puestos. */
    @Volatile private var idle = false

    private val _version = MutableStateFlow(0L)

    /**
     * Sube en cada refresco. Los datos de los widgets dependen de él: una sesión
     * de Glance que siga abierta recalcula «hoy» aunque la base no haya cambiado.
     */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun update(context: Context) {
        val app = context.applicationContext
        if (!isUnlocked(app)) return
        if (started.compareAndSet(false, true)) scope.launch { loop() }
        requests.trySend(app)
    }

    /**
     * Que las sesiones abiertas recalculen ya (p. ej. tras elegir evento en la
     * configuración), sin esperar al refresco agrupado.
     */
    internal fun touch() {
        _version.update { it + 1 }
    }

    private suspend fun loop() {
        for (first in requests) {
            delay(DEBOUNCE_MS)
            // Lo que llegó durante la espera ya queda cubierto por este refresco.
            val context = requests.tryReceive().getOrNull() ?: first
            runCatching { refresh(context, fromWorker = false) }
                .onFailure { Log.w(TAG, "No se pudieron refrescar los widgets", it) }
        }
    }

    /** Redibuja los widgets puestos y programa el próximo refresco. */
    internal suspend fun refresh(context: Context, fromWorker: Boolean) {
        val alarms = ids(context, NextAlarmsWidgetReceiver::class.java)
        val countdowns = ids(context, CountdownWidgetReceiver::class.java)
        if (alarms.isEmpty() && countdowns.isEmpty()) {
            // Sin widgets no hay nada que mantener al día (se cancela una vez,
            // no en cada cambio de datos).
            if (!idle) WidgetRefreshWorker.cancel(context)
            idle = true
            return
        }
        idle = false
        _version.update { it + 1 }
        // Si uno falla, el otro se pinta igual y el próximo refresco queda programado.
        if (alarms.isNotEmpty()) attempt { NextAlarmsWidget().updateAll(context) }
        if (countdowns.isNotEmpty()) attempt { CountdownWidget().updateAll(context) }
        WidgetRefreshWorker.schedule(context, nextRefreshAt(context, includeAlarms = alarms.isNotEmpty()), fromWorker)
    }

    /**
     * A medianoche cambian los días que faltan y «Mañana» pasa a «Hoy»; y el
     * widget de alarmas avanza cuando pasa la que enseña primero.
     */
    private suspend fun nextRefreshAt(context: Context, includeAlarms: Boolean): Long {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + MIDNIGHT_MARGIN_MS
        if (!includeAlarms) return midnight
        val reminders = CalarmiApp.container(context).reminders.all()
        val alarmChange = NextAlarms.nextChange(reminders, now)
        return if (alarmChange != null && alarmChange < midnight) alarmChange + 1_000L else midnight
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo redibujar un widget", e)
        }
    }

    private fun ids(context: Context, receiver: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver)) }
            .getOrNull() ?: IntArray(0)

    private fun isUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
