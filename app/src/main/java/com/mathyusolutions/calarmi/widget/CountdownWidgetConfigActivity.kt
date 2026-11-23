package com.mathyusolutions.calarmi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.countdown.CountdownFormat
import com.mathyusolutions.calarmi.countdown.CountdownOrder
import com.mathyusolutions.calarmi.domain.CountdownStatus
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.theme.CalarmiTheme
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/**
 * «Elegir evento» del widget de cuenta regresiva: se abre al ponerlo y desde
 * sus ajustes. Sin elegir, el widget sigue siempre a la próxima.
 */
class CountdownWidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Salir sin elegir cancela (el lanzador no pone el widget, salvo que ya lo
        // hubiera puesto por ser la configuración opcional).
        setResult(RESULT_CANCELED, result())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = CalarmiApp.container(this)
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CalarmiTheme(settings) {
                val events by container.countdowns.countdowns.collectAsStateWithLifecycle(initialValue = null)
                val chosen = remember { WidgetPrefs.countdownFor(this, appWidgetId) }
                ScreenScaffold(title = stringResource(R.string.widget_choose_event), onBack = ::finish) {
                    val list = events ?: return@ScreenScaffold
                    val now = remember { System.currentTimeMillis() }
                    val ordered = remember(list) { CountdownOrder.forPicker(list, now) }
                    // Si la elegida ya no existe, el widget está mostrando la próxima.
                    val automatic = chosen == null || list.none { it.id == chosen }
                    SectionCard(footer = stringResource(R.string.widget_choose_event_footer)) {
                        SettingRow(
                            title = stringResource(R.string.widget_choose_automatic),
                            icon = Icons.Filled.AutoAwesome,
                            divider = ordered.isNotEmpty(),
                            onClick = { choose(null) },
                            trailing = { if (automatic) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                        ordered.forEachIndexed { index, event ->
                            EventRow(event, now, selected = !automatic && event.id == chosen, divider = index < ordered.lastIndex) {
                                choose(event.id)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun choose(countdownId: String?) {
        WidgetPrefs.setCountdown(this, appWidgetId, countdownId)
        val context = applicationContext
        lifecycleScope.launch {
            // Que el widget cambie ya, aunque su sesión siguiera abierta.
            WidgetUpdater.touch()
            runCatching { CountdownWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) }
            WidgetUpdater.update(context)
            setResult(RESULT_OK, result())
            finish()
        }
    }
}

@Composable
private fun EventRow(event: CountdownEvent, now: Long, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val tint = parseHex(event.colorHex) ?: Color(0xFFFF2D55)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = DS.lg, vertical = DS.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReminderIcon(
                event.iconKind, event.iconValue, tint,
                photoPath = event.photoPath,
                fallbackSymbol = "calendar",
                size = 36.dp,
                shape = RoundedCornerShape(DS.radiusSm),
            )
            Spacer(Modifier.width(DS.md))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusLine(event.status(now)) + " · " + CountdownFormat.date(context, event.target(now), event.includesTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
        if (divider) {
            HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

/** «23 días», «1 día atrás» o «¡Hoy!», en una línea. */
@Composable
private fun statusLine(status: CountdownStatus): String = when (status) {
    CountdownStatus.Today -> stringResource(R.string.countdowns_today)
    is CountdownStatus.Upcoming -> "${status.days} ${pluralStringResource(R.plurals.countdowns_days, status.days)}"
    is CountdownStatus.Past -> "${status.days} ${pluralStringResource(R.plurals.countdowns_days_ago, status.days)}"
}
