package com.mathyusolutions.calarmi.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import com.mathyusolutions.calarmi.domain.CountdownStatus
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.ui.navigation.HomeTab
import java.text.NumberFormat
import kotlinx.coroutines.flow.first

/**
 * Widget «Cuenta regresiva»: los días que faltan para un evento, con su foto o
 * su emoji. Cada widget puede mostrar un evento elegido (al ponerlo o en sus
 * ajustes); sin elegir, enseña el próximo.
 */
class CountdownWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val source = WidgetData.countdown(context, appWidgetId)
        val initial = source.first()
        provideContent {
            val state by source.collectAsState(initial)
            CountdownContent(state)
        }
    }

    /** Un widget quitado no deja su elección guardada. */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        WidgetPrefs.forget(context, intArrayOf(GlanceAppWidgetManager(context).getAppWidgetId(glanceId)))
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.update(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.update(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetPrefs.migrate(context, oldWidgetIds, newWidgetIds)
    }
}

private val white = ColorProvider(Color.White)
private val whiteSoft = ColorProvider(Color.White.copy(alpha = 0.85f))

private fun openCountdownIntent(context: Context, id: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_COUNTDOWN)
        .putExtra(MainActivity.EXTRA_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun CountdownContent(state: CountdownWidgetState) {
    val context = LocalContext.current
    val event = state.event
    val open = if (event != null) openCountdownIntent(context, event.id) else openTabIntent(context, HomeTab.COUNTDOWNS)
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(open)),
    ) {
        // El fondo: la foto (oscurecida abajo, donde va el texto) o el degradado del color.
        if (state.photo != null) {
            Image(ImageProvider(state.photo), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Image(ImageProvider(R.drawable.widget_scrim), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else {
            Image(ImageProvider(state.background), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        when {
            event == null -> Empty(state)
            LocalSize.current.width >= 200.dp -> Medium(state, event)
            else -> Small(state, event)
        }
    }
}

@Composable
private fun Empty(state: CountdownWidgetState) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(state.emptyIcon), contentDescription = null, modifier = GlanceModifier.size(28.dp))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            context.getString(R.string.widget_countdown_empty),
            style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 3,
        )
    }
}

/** Pequeño: el ícono arriba y, abajo, los días y el nombre. */
@Composable
private fun Small(state: CountdownWidgetState, event: CountdownEvent) {
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        if (state.tile != null) Image(ImageProvider(state.tile), contentDescription = null, modifier = GlanceModifier.size(34.dp))
        Spacer(GlanceModifier.defaultWeight())
        Counter(event.status(state.now), numberSize = 38.sp, alignment = Alignment.Start)
        Text(
            event.title,
            style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
    }
}

/** Ancho: ícono, nombre y fecha, y los días a la derecha. */
@Composable
private fun Medium(state: CountdownWidgetState, event: CountdownEvent) {
    val context = LocalContext.current
    val target = event.target(state.now)
    Row(
        GlanceModifier.fillMaxSize().padding(16.dp),
        verticalAlignment = if (state.photo != null) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        if (state.tile != null) {
            Image(ImageProvider(state.tile), contentDescription = null, modifier = GlanceModifier.size(54.dp))
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(event.title, style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.repeatIcon != null) {
                    Image(ImageProvider(state.repeatIcon), contentDescription = context.getString(R.string.countdowns_yearly), modifier = GlanceModifier.size(13.dp))
                    Spacer(GlanceModifier.width(3.dp))
                }
                Text(
                    // Con hora, sin el año: no cabe todo en una línea.
                    CountdownFormat.date(context, target, event.includesTime, withYear = !event.includesTime),
                    style = TextStyle(color = whiteSoft, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Counter(event.status(state.now), numberSize = 46.sp, alignment = Alignment.CenterHorizontally)
    }
}

/** El número grande de días y su unidad, o «¡Hoy!». */
@Composable
private fun Counter(status: CountdownStatus, numberSize: TextUnit, alignment: Alignment.Horizontal) {
    val context = LocalContext.current
    val locale = context.currentLocale()
    when (status) {
        CountdownStatus.Today -> Text(
            context.getString(R.string.countdowns_today),
            style = TextStyle(color = white, fontSize = numberSize * 0.62f, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        is CountdownStatus.Upcoming, is CountdownStatus.Past -> {
            val days = (status as? CountdownStatus.Upcoming)?.days ?: (status as CountdownStatus.Past).days
            val unit = context.resources.getQuantityString(
                if (status.isPast) R.plurals.countdowns_days_ago else R.plurals.countdowns_days,
                days,
            )
            Column(horizontalAlignment = alignment) {
                Text(
                    NumberFormat.getIntegerInstance(locale).format(days),
                    style = TextStyle(color = white, fontSize = numberSize, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    unit.uppercase(locale),
                    style = TextStyle(color = whiteSoft, fontSize = if (numberSize.value * 0.26f < 10f) 10.sp else numberSize * 0.26f, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}
