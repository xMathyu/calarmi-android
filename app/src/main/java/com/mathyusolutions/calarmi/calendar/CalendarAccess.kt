package com.mathyusolutions.calarmi.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mathyusolutions.calarmi.CalarmiApp
import kotlinx.coroutines.launch

/**
 * Preferencias propias de la pestaña (no de la app): el modo elegido y si el
 * permiso del calendario quedó negado para siempre. Viven en el almacenamiento
 * normal porque solo las usa la interfaz, que nunca corre antes del primer
 * desbloqueo.
 */
internal class CalendarUiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("calendar_ui", Context.MODE_PRIVATE)

    /** Lista de eventos próximos en lugar de la vista de mes. */
    var agenda: Boolean
        get() = prefs.getString(K_MODE, MODE_MONTH) == MODE_AGENDA
        set(value) = prefs.edit().putString(K_MODE, if (value) MODE_AGENDA else MODE_MONTH).apply()

    /** Android ya no vuelve a mostrar el aviso: solo se arregla desde los ajustes del sistema. */
    var permissionDenied: Boolean
        get() = prefs.getBoolean(K_DENIED, false)
        set(value) = prefs.edit().putBoolean(K_DENIED, value).apply()

    private companion object {
        // Mismo nombre y valores que el `calendar.viewMode` de iOS.
        const val K_MODE = "calendar.viewMode"
        const val MODE_MONTH = "month"
        const val MODE_AGENDA = "agenda"
        const val K_DENIED = "calendar.permissionDenied"
    }
}

/** En qué punto está el permiso para leer el calendario, y cómo pedirlo. */
@Stable
internal class CalendarAccess(granted: Boolean, denied: Boolean) {
    var granted by mutableStateOf(granted)
        internal set

    /** Lo negó y Android no vuelve a preguntar (equivale al `.denied` de iOS). */
    var denied by mutableStateOf(denied)
        internal set

    internal var launch: () -> Unit = {}

    /** Muestra el aviso del sistema. Al aceptarlo se encienden las alarmas del calendario. */
    fun request() = launch()
}

@Composable
internal fun rememberCalendarAccess(): CalendarAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val container = CalarmiApp.container(context)
    val ui = remember { CalendarUiPrefs(context) }
    val access = remember {
        val granted = container.calendar.hasPermission()
        CalendarAccess(granted, !granted && ui.permissionDenied)
    }
    // El permiso se puede quitar o dar desde los ajustes del sistema en cualquier momento.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access.granted = container.calendar.hasPermission()
        if (access.granted) {
            access.denied = false
            ui.permissionDenied = false
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access.granted = granted
        if (granted) {
            access.denied = false
            ui.permissionDenied = false
            val wasOn = container.settings.current.calendarAlarmsEnabled
            container.settings.update { it.copy(calendarAlarmsEnabled = true) }
            // Encender el ajuste ya reprograma (el contenedor lo observa); si ya
            // estaba encendido y solo faltaba el permiso, hay que pedirlo aquí.
            if (wasOn) container.scope.launch { container.onDataChanged() }
            container.calendar.notifyChanged()
        } else {
            // Sin «explicación» después de un no, Android ya no mostrará el aviso:
            // el botón tiene que llevar a los ajustes o no haría nada. Si en
            // realidad solo cerró el aviso, los ajustes igual sirven.
            val denied = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
            access.denied = denied
            ui.permissionDenied = denied
        }
    }
    access.launch = { launcher.launch(Manifest.permission.READ_CALENDAR) }
    return access
}

/** Los ajustes del sistema de Calarmi, donde se da el permiso que Android ya no pide. */
internal fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Abre un enlace (Teams, Zoom, Meet) o una dirección en la app que lo maneje. */
internal fun openUri(context: Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Una dirección en la app de mapas que haya (Google Maps, Waze…). */
internal fun mapsUri(query: String): Uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
