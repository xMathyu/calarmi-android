package com.mathyusolutions.calarmi.sharing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mathyusolutions.calarmi.MainActivity
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

/**
 * Le dice a quien recibió una alarma que el dueño la movió a otro día u hora,
 * o que la borró (el `ShareChangeNotifier` de iOS). El cambio ya llegó y la
 * alarma ya se reprogramó sola; sin este aviso, la persona seguiría contando
 * con la hora vieja.
 */
object ShareNotifications {
    const val CHANNEL_CHANGES = "share_changes"
    /** Solo para el trabajo de sincronizar en Android 11 o anterior (servicio en primer plano). */
    const val CHANNEL_SYNC = "share_sync"
    const val ID_SYNC = 3001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CHANGES, context.getString(R.string.sharing_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.sharing_channel_desc)
                },
                NotificationChannel(CHANNEL_SYNC, context.getString(R.string.sharing_sync_channel), NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                },
            ),
        )
    }

    /** Mismos horarios (regla y hora). Un segundo de diferencia es un redondeo, no un cambio. */
    fun sameSchedules(a: Reminder, b: Reminder): Boolean {
        fun norm(r: Reminder): List<AlarmSchedule> =
            listOf(AlarmSchedule(id = "", date = r.date, recurrence = r.recurrence)) +
                r.additionalSchedules.sortedBy { it.date }
        val x = norm(a)
        val y = norm(b)
        return x.size == y.size && x.zip(y).all { (p, q) -> p.recurrence == q.recurrence && abs(p.date - q.date) < 1000 }
    }

    /** ¿Iba a sonar? Borrar una que ya no sonaba (vencida o apagada) no le cambia nada a nadie. */
    fun isUpcoming(reminder: Reminder): Boolean = reminder.isEnabled && reminder.nextOccurrence() != null

    /** «Ana cambió el horario. Sonará el lun, 24 nov, 7:30» (o que ya no sonará). */
    fun scheduleChanged(context: Context, reminder: Reminder, ownerName: String?) {
        val owner = ownerName ?: context.getString(R.string.sharing_unknown_owner)
        val next = reminder.nextOccurrence()
        val body = if (next != null) {
            val locale = context.resources.configuration.locales[0]
            val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "EEEdMMMHHmm" else "EEEdMMMhmma")
            context.getString(R.string.sharing_changed_will_ring, owner, SimpleDateFormat(pattern, locale).format(Date(next)))
        } else {
            context.getString(R.string.sharing_changed_no_more, owner)
        }
        post(context, reminder, body)
    }

    /** «Ana la borró, así que ya no sonará». */
    fun deleted(context: Context, reminder: Reminder, ownerName: String?) {
        val owner = ownerName ?: context.getString(R.string.sharing_unknown_owner)
        post(context, reminder, context.getString(R.string.sharing_deleted, owner))
    }

    private fun post(context: Context, reminder: Reminder, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val title = reminder.title.ifBlank { context.getString(R.string.sharing_kind_alarm) }
        val emoji = reminder.symbolName?.takeIf { reminder.iconKind == ReminderIconKind.EMOJI || isEmojiIcon(it) }
        val open = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_REMINDER)
                .putExtra(MainActivity.EXTRA_ID, reminder.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CHANGES)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(if (emoji != null) "$emoji $title" else title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup("share-changes")
            .setContentIntent(open)
            .build()
        // Un aviso por alarma: el más nuevo (otro cambio, o el borrado) reemplaza al anterior.
        runCatching { NotificationManagerCompat.from(context).notify("share-change:${reminder.id}", 0, notification) }
    }

    /** Lo que se ve mientras el trabajo de sincronizar corre en primer plano (solo Android ≤ 11). */
    fun syncing(context: Context) = NotificationCompat.Builder(context, CHANNEL_SYNC)
        .setSmallIcon(R.drawable.ic_stat_alarm)
        .setContentTitle(context.getString(R.string.sharing_syncing))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .build()
}
