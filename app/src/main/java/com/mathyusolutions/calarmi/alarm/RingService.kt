package com.mathyusolutions.calarmi.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.data.AppJson
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * El servicio que hace sonar las alarmas: tono en bucle, vibración y la
 * notificación de pantalla completa. Sigue sonando hasta que se detiene, se
 * pospone, se resuelve el reto o llega el tope (10 min, o 30 con reto).
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: TonePlayer
    private lateinit var vibrator: AlarmVibrator
    private var wakeLock: PowerManager.WakeLock? = null
    private var playingKey: String? = null
    private var muted = false
    private val timeout = Runnable { onTimeout() }
    private val unmute = Runnable { setMuted(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = CalarmiApp.container(this)
        player = TonePlayer(this, container.importedTones)
        vibrator = AlarmVibrator(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Calarmi:ring")
            .apply { acquire(WakeChallenge.MAX_RING_MILLIS + 60_000) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getStringExtra(EXTRA_KEY)
        when (intent?.action) {
            ACTION_RING -> {
                val json = intent.getStringExtra(EXTRA_ITEMS)
                val items = json?.let { runCatching { AppJson.decodeFromString(ListSerializer(RingItem.serializer()), it) }.getOrNull() }.orEmpty()
                // Una alarma que vuelve a sonar reemplaza a su versión anterior.
                val sources = items.map { it.sourceId }.toSet()
                RingingState.set(RingingState.queue.value.filter { it.sourceId !in sources } + items)
            }
            ACTION_STOP, ACTION_SOLVED -> key?.let(::stopItem)
            ACTION_SNOOZE -> key?.let(::snoozeItem)
            ACTION_MUTE -> setMuted(true)
            ACTION_UNMUTE -> setMuted(false)
        }
        refresh()
        return START_NOT_STICKY
    }

    /** Pone en marcha (o actualiza) lo que suena según la cola. */
    private fun refresh() {
        val current = RingingState.current
        if (current == null) {
            stopEverything()
            return
        }
        val settings = CalarmiApp.container(this).settings.current
        val notification = AlarmNotifications.ringing(this, current, settings.snooze.seconds / 60)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        try {
            ServiceCompat.startForeground(this, AlarmNotifications.ID_RINGING, notification, type)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo pasar a primer plano", e)
        }
        if (playingKey != current.key) {
            playingKey = current.key
            muted = false
            handler.removeCallbacks(unmute)
            player.play(AlarmTone.fromRaw(current.toneRaw) ?: AlarmTone.DEFAULT)
            if (settings.vibrate) vibrator.start()
            handler.removeCallbacks(timeout)
            val limit = if (current.challenge != null) WakeChallenge.MAX_RING_MILLIS else PLAIN_RING_MILLIS
            handler.postDelayed(timeout, limit)
        }
    }

    private fun stopItem(key: String) {
        val item = RingingState.queue.value.firstOrNull { it.key == key } ?: return
        val container = CalarmiApp.container(this)
        // «Detener» calla todo lo de la misma alarma: lo que suena y lo pospuesto.
        container.alarmState.clearSnoozes(item.sourceId)
        RingingState.set(RingingState.queue.value.filter { it.sourceId != item.sourceId })
        if (playingKey == key) playingKey = null
        scope.launch(Dispatchers.Default) { container.planner.reschedule() }
    }

    private fun snoozeItem(key: String) {
        val item = RingingState.queue.value.firstOrNull { it.key == key } ?: return
        val container = CalarmiApp.container(this)
        val at = System.currentTimeMillis() + container.settings.current.snooze.millis
        container.alarmState.addSnooze(item, at)
        RingingState.set(RingingState.queue.value.filter { it.sourceId != item.sourceId })
        if (playingKey == key) playingKey = null
        scope.launch(Dispatchers.Default) { container.planner.reschedule() }
    }

    /** Mientras se resuelve el reto, el tono calla; si pasan 3 minutos sin resolverlo, vuelve. */
    private fun setMuted(value: Boolean) {
        if (muted == value) return
        muted = value
        handler.removeCallbacks(unmute)
        if (value) {
            player.pause()
            vibrator.stop()
            handler.postDelayed(unmute, CHALLENGE_QUIET_MILLIS)
        } else if (RingingState.current != null) {
            player.resume()
            if (CalarmiApp.container(this).settings.current.vibrate) vibrator.start()
        }
    }

    private fun onTimeout() {
        val current = RingingState.current ?: return
        AlarmNotifications.showMissed(this, current)
        RingingState.set(RingingState.queue.value.filter { it.sourceId != current.sourceId })
        playingKey = null
        refresh()
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        vibrator.stop()
        playingKey = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        vibrator.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingService"
        const val ACTION_RING = "com.mathyusolutions.calarmi.ring.RING"
        const val ACTION_STOP = "com.mathyusolutions.calarmi.ring.STOP"
        const val ACTION_SNOOZE = "com.mathyusolutions.calarmi.ring.SNOOZE"
        const val ACTION_SOLVED = "com.mathyusolutions.calarmi.ring.SOLVED"
        const val ACTION_MUTE = "com.mathyusolutions.calarmi.ring.MUTE"
        const val ACTION_UNMUTE = "com.mathyusolutions.calarmi.ring.UNMUTE"
        const val EXTRA_ITEMS = "items"
        const val EXTRA_KEY = "key"
        private const val PLAIN_RING_MILLIS = 10L * 60 * 1000
        private const val CHALLENGE_QUIET_MILLIS = 3L * 60 * 1000

        fun ring(context: Context, items: List<RingItem>) {
            val intent = Intent(context, RingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_ITEMS, AppJson.encodeToString(ListSerializer(RingItem.serializer()), items))
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String, key: String? = null) {
            context.startService(Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_KEY, key))
        }

        fun actionIntent(context: Context, action: String, key: String): PendingIntent = PendingIntent.getService(
            context, (action + key).hashCode(),
            Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
