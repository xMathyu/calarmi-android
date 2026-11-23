package com.mathyusolutions.calarmi.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mathyusolutions.calarmi.MainActivity
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.countdown.CountdownFormat
import com.mathyusolutions.calarmi.countdown.currentLocale
import com.mathyusolutions.calarmi.ui.navigation.HomeTab
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * Widget «Próxima alarma»: la siguiente (o las tres siguientes si cabe). Tocarlo
 * abre la app en Alarmas; el «+» crea una nueva.
 */
class NextAlarmsWidget : GlanceAppWidget() {
    // Exact: el tamaño real decide si caben una o tres filas.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetData.nextAlarms(context)
        val initial = source.first()
        provideContent {
            val state by source.collectAsState(initial)
            NextAlarmsContent(state)
        }
    }
}

class NextAlarmsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlarmsWidget()

    /** El primero puesto: hay que programar el refresco de medianoche. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.update(context)
    }

    /** El último quitado: si no queda ningún widget, el refresco se cancela. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.update(context)
    }
}

private val primaryText = ColorProvider(day = Color.Black, night = Color.White)
private val secondaryText = ColorProvider(day = Color(0xFF6C6C70), night = Color(0xFF98989F))

internal fun openTabIntent(context: Context, tab: HomeTab): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_TAB)
        .putExtra(MainActivity.EXTRA_TAB, tab.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun newAlarmIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_NEW_ALARM)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun NextAlarmsContent(state: NextAlarmsState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= 200.dp
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(day = Color.White, night = Color(0xFF1C1C1E))
            .clickable(actionStartActivity(openTabIntent(context, HomeTab.ALARMS))),
    ) {
        // Un velo del color de la próxima alarma sobre el fondo, como en iOS.
        if (state.veil != null && state.alarmsEnabled) {
            Image(ImageProvider(state.veil), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Header(state, if (wide) R.string.widget_next_alarms_header else R.string.widget_next_alarm_name)
            // Lo de debajo del encabezado ocupa el resto (con peso: «llenar» se saldría por abajo).
            val rest = GlanceModifier.defaultWeight().fillMaxWidth()
            when {
                !state.alarmsEnabled -> Placeholder(state, R.string.widget_alarms_off, rest)
                state.rows.isEmpty() -> Placeholder(state, R.string.widget_no_alarms, rest)
                wide -> {
                    Spacer(GlanceModifier.height(8.dp))
                    // Las filas que quepan (hasta tres): padding, encabezado y 36 dp por fila.
                    val fit = ((size.height.value - 28f - 34f) / 36f).toInt().coerceIn(1, 3)
                    state.rows.take(fit).forEach { row ->
                        AlarmRow(row, state.now)
                    }
                }
                else -> Small(state.rows.first(), state.now, tall = size.height >= 150.dp, modifier = rest)
            }
        }
    }
}

@Composable
private fun Header(state: NextAlarmsState, title: Int) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(state.headerIcon), contentDescription = null, modifier = GlanceModifier.size(14.dp))
        Spacer(GlanceModifier.width(4.dp))
        Text(
            context.getString(title),
            style = TextStyle(color = ColorProvider(Color(state.accent)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Image(
            ImageProvider(state.plusIcon),
            contentDescription = context.getString(R.string.alarm_new),
            modifier = GlanceModifier.size(26.dp).clickable(actionStartActivity(newAlarmIntent(context))),
        )
    }
}

@Composable
private fun Placeholder(state: NextAlarmsState, text: Int, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(state.emptyIcon), contentDescription = null, modifier = GlanceModifier.size(26.dp))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            context.getString(text),
            style = TextStyle(color = secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 2,
        )
    }
}

/** El tamaño pequeño: la siguiente, con la hora en grande. */
@Composable
private fun Small(row: WidgetAlarmRow, now: Long, tall: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier) {
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(row.icon), contentDescription = null, modifier = GlanceModifier.size(22.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                dayLabel(context, row.at, now),
                style = TextStyle(color = secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Text(
            CountdownFormat.time(context, row.at),
            style = TextStyle(color = primaryText, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            row.title,
            style = TextStyle(color = primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = if (tall) 2 else 1,
        )
    }
}

/** Una fila del tamaño ancho: ícono, nombre y día, y la hora a la derecha. */
@Composable
private fun AlarmRow(row: WidgetAlarmRow, now: Long) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(row.icon), contentDescription = null, modifier = GlanceModifier.size(30.dp))
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(row.title, style = TextStyle(color = primaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(dayLabel(context, row.at, now), style = TextStyle(color = secondaryText, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(4.dp))
        Text(
            CountdownFormat.time(context, row.at),
            style = TextStyle(color = primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** «Hoy», «Mañana» o «jue, 12 nov». */
private fun dayLabel(context: Context, at: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> context.getString(R.string.group_today)
        today.plusDays(1) -> context.getString(R.string.group_tomorrow)
        else -> {
            val locale = context.currentLocale()
            DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(day)
        }
    }
}
