package com.mathyusolutions.calarmi.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Lo que necesita Calarmi para que una alarma SUENE: notificaciones (sin ellas
 * no hay pantalla completa), alarmas exactas y la pantalla completa sobre el
 * bloqueo (Android 14+). Si falta algo, la lista de alarmas lo dice arriba.
 */
object AlarmPermissions {
    enum class Missing { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN }

    fun missing(context: Context): List<Missing> = buildList {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) add(Missing.NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) add(Missing.EXACT_ALARMS)
        if (!AlarmNotifications.canUseFullScreen(context)) add(Missing.FULL_SCREEN)
    }

    fun needsNotificationRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** La pantalla de Ajustes del sistema donde se arregla `missing`. */
    fun settingsIntent(context: Context, missing: Missing): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (missing) {
            Missing.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Missing.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            Missing.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34)
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
