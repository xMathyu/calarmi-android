package com.mathyusolutions.calarmi.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mathyusolutions.calarmi.MainActivity
import com.mathyusolutions.calarmi.R
import java.text.DateFormat
import java.util.Date

/** Canales y notificaciones del motor de alarmas. */
object AlarmNotifications {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_UPCOMING = "upcoming"
    const val CHANNEL_MISSED = "missed"
    /** Con sonido de alarma propio: solo si el servicio no pudo arrancar. */
    const val CHANNEL_FALLBACK = "fallback"

    const val ID_RINGING = 1001
    const val ID_UPCOMING = 1002
    private const val ID_MISSED_BASE = 2000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                // Sin sonido propio: el tono lo pone el servicio, en el canal de alarma.
                NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_ringing_desc)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
                NotificationChannel(CHANNEL_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_upcoming_desc)
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_missed_desc)
                },
                NotificationChannel(CHANNEL_FALLBACK, context.getString(R.string.channel_fallback), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_fallback_desc)
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    fun ringing(context: Context, item: RingItem, snoozeMinutes: Int): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 10, RingingActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(item.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(subtitle(context, item))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val challenge = item.challenge
        if (challenge != null) {
            // Con reto no hay «Detener» aquí: se apaga resolviéndolo en la app.
            builder.addAction(0, context.getString(R.string.ring_solve_challenge), fullScreen)
        } else {
            if (!item.isEvent || (item.meetingUrl == null && item.location == null)) {
                builder.addAction(0, context.getString(R.string.ring_snooze_minutes, snoozeMinutes), RingService.actionIntent(context, RingService.ACTION_SNOOZE, item.key))
            }
            builder.addAction(0, context.getString(R.string.ring_stop), RingService.actionIntent(context, RingService.ACTION_STOP, item.key))
        }
        return builder.build()
    }

    /** «Suena en 5 min»: la cuenta regresiva previa, con «Saltar esta vez». */
    fun showUpcoming(context: Context, item: RingItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 11, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(item.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(context.getString(R.string.upcoming_rings_at, time(context, item.fireAt)))
            .setWhen(item.fireAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((item.fireAt - System.currentTimeMillis()).coerceAtLeast(1000))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.upcoming_skip), AlarmReceiver.skipPendingIntent(context, item.key))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_UPCOMING, notification) }
    }

    fun cancelUpcoming(context: Context) = NotificationManagerCompat.from(context).cancel(ID_UPCOMING)

    /** La alarma sonó hasta el tope sin que nadie la atendiera. */
    fun showMissed(context: Context, item: RingItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 12, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.missed_title))
            .setContentText(context.getString(R.string.missed_text, item.title, time(context, item.fireAt)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_MISSED_BASE + (item.key.hashCode() and 0xFFF), notification) }
    }

    /** Plan B cuando el servicio no arranca: notificación de alarma con sonido del sistema. */
    fun showFallback(context: Context, item: RingItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 13, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(item.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(subtitle(context, item))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_MISSED_BASE + 0x1000 + (item.key.hashCode() and 0xFFF), notification) }
    }

    fun subtitle(context: Context, item: RingItem): String {
        val at = time(context, item.occurrenceAt)
        val lead = item.leadMillis
        if (lead < 60_000) return at
        val minutes = (lead / 60_000).toInt()
        val inText = when {
            minutes < 60 -> context.resources.getQuantityString(R.plurals.in_minutes, minutes, minutes)
            minutes < 24 * 60 -> context.resources.getQuantityString(R.plurals.in_hours, minutes / 60, minutes / 60)
            else -> context.resources.getQuantityString(R.plurals.in_days, minutes / (24 * 60), minutes / (24 * 60))
        }
        return "$inText · $at"
    }

    fun time(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    @Suppress("unused")
    private fun dateTime(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
