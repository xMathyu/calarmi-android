package com.mathyusolutions.calarmi.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.alarm.toHex
import com.mathyusolutions.calarmi.countdown.CountdownOrder
import com.mathyusolutions.calarmi.countdown.CountdownPhoto
import com.mathyusolutions.calarmi.countdown.countdownSymbol
import com.mathyusolutions.calarmi.data.Settings
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

/** Qué alarmas enseña el widget. Sin Android: se prueba en la JVM. */
object NextAlarms {
    /** Una alarma sigue a la vista el minuto en que suena. */
    const val GRACE_MS = 60_000L

    /**
     * Las próximas alarmas activas, una fila por alarma (una diaria no ocupa
     * las tres con sus próximos días), de la más cercana a la más lejana.
     */
    fun upcoming(reminders: List<Reminder>, now: Long, limit: Int = 3): List<Pair<Reminder, Long>> =
        reminders.asSequence()
            .filter { it.isEnabled }
            .mapNotNull { reminder -> reminder.nextOccurrence(now - GRACE_MS)?.let { reminder to it } }
            .sortedBy { it.second }
            .take(limit)
            .toList()

    /** Cuándo cambia lo que enseña el widget: cuando pasa la primera (más el minuto de gracia). */
    fun nextChange(reminders: List<Reminder>, now: Long): Long? =
        upcoming(reminders, now, limit = 1).firstOrNull()?.second?.plus(GRACE_MS)
}

/** Una fila del widget de alarmas, ya lista para pintar. */
internal data class WidgetAlarmRow(
    val reminderId: String,
    val title: String,
    val at: Long,
    val icon: Bitmap,
)

internal data class NextAlarmsState(
    val alarmsEnabled: Boolean,
    val rows: List<WidgetAlarmRow>,
    val now: Long,
    val accent: Int,
    /** El velo del color de la próxima alarma sobre el fondo. */
    val veil: Bitmap?,
    val headerIcon: Bitmap,
    val emptyIcon: Bitmap,
    val plusIcon: Bitmap,
)

internal data class CountdownWidgetState(
    /** Nulo = no hay ninguna cuenta todavía. */
    val event: CountdownEvent?,
    val now: Long,
    /** La foto de fondo, reducida para el widget. */
    val photo: Bitmap?,
    /** El degradado del color, si no hay foto. */
    val background: Bitmap,
    val tile: Bitmap?,
    val emptyIcon: Bitmap,
    /** El símbolo de «cada año» junto a la fecha. */
    val repeatIcon: Bitmap?,
)

/**
 * Los datos de los widgets como `Flow`: se vuelven a emitir cuando cambian las
 * alarmas o las cuentas (Room) y cada vez que `WidgetUpdater` avisa (medianoche,
 * hora cambiada, configuración del widget), así una sesión de Glance abierta
 * también se entera.
 */
internal object WidgetData {
    private const val ICON_DP = 54

    fun nextAlarms(context: Context): Flow<NextAlarmsState> {
        val app = context.applicationContext
        val container = CalarmiApp.container(app)
        return combine(
            container.reminders.reminders,
            container.categories.categories,
            container.settings.state,
            WidgetUpdater.version,
        ) { reminders, customs, settings, _ -> nextAlarmsState(app, reminders, customs, settings) }
            .flowOn(Dispatchers.IO)
    }

    fun countdown(context: Context, appWidgetId: Int): Flow<CountdownWidgetState> {
        val app = context.applicationContext
        val container = CalarmiApp.container(app)
        return combine(container.countdowns.countdowns, WidgetUpdater.version) { events, _ ->
            countdownState(app, events, appWidgetId)
        }.flowOn(Dispatchers.IO)
    }

    private fun px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    private fun nextAlarmsState(
        context: Context,
        reminders: List<Reminder>,
        customs: List<CustomCategory>,
        settings: Settings,
    ): NextAlarmsState {
        val now = System.currentTimeMillis()
        val byId = customs.associateBy { it.id }
        val accent = WidgetGraphics.parse(settings.accentColorHex ?: Settings.DEFAULT_ACCENT, 0xFFFF6633.toInt())
        val iconPx = px(context, ICON_DP)
        val rows = NextAlarms.upcoming(reminders, now).map { (reminder, at) ->
            val custom = reminder.customCategoryId?.let(byId::get)
            val color = WidgetGraphics.parse(custom?.colorHex ?: reminder.category.tint.toHex(), accent)
            val (emoji, symbol) = alarmIcon(reminder, custom)
            WidgetAlarmRow(
                reminderId = reminder.id,
                title = reminder.title,
                at = at,
                icon = WidgetGraphics.tile(
                    iconPx, WidgetGraphics.withAlpha(color, 0.2f), cornerFraction = 0.5f,
                    emoji = emoji, symbol = symbol?.let(SymbolIcons::of), symbolColor = color,
                    contentFraction = if (emoji != null) 0.55f else 0.5f,
                ),
            )
        }
        val first = rows.firstOrNull()?.let { row ->
            reminders.firstOrNull { it.id == row.reminderId }?.let { r ->
                WidgetGraphics.parse(r.customCategoryId?.let(byId::get)?.colorHex ?: r.category.tint.toHex(), accent)
            }
        }
        val small = px(context, 20)
        return NextAlarmsState(
            alarmsEnabled = settings.alarmsEnabled,
            rows = rows,
            now = now,
            accent = accent,
            veil = first?.let { WidgetGraphics.gradient(WidgetGraphics.withAlpha(it, 0.22f), WidgetGraphics.withAlpha(it, 0.04f)) },
            headerIcon = WidgetGraphics.vector(Icons.Filled.Alarm, small, accent),
            emptyIcon = WidgetGraphics.vector(
                if (settings.alarmsEnabled) Icons.Filled.Alarm else Icons.Filled.NotificationsOff,
                px(context, 32), WidgetGraphics.withAlpha(0xFF8E8E93.toInt(), 1f),
            ),
            plusIcon = WidgetGraphics.tile(
                px(context, 32), accent, cornerFraction = 0.5f,
                emoji = null, symbol = Icons.Filled.Add, symbolColor = android.graphics.Color.WHITE, contentFraction = 0.6f,
            ),
        )
    }

    /** Emoji o símbolo; una foto (que el widget no carga) cae al ícono de la categoría. */
    private fun alarmIcon(reminder: Reminder, custom: CustomCategory?): Pair<String?, String?> {
        val value = reminder.symbolName
        when (reminder.iconKind) {
            ReminderIconKind.EMOJI -> if (isEmojiIcon(value)) return value to null
            ReminderIconKind.SYMBOL -> if (!value.isNullOrEmpty()) {
                return if (isEmojiIcon(value)) value to null else null to (value.takeIf(SymbolIcons::has) ?: reminder.category.defaultSymbol)
            }
            ReminderIconKind.PHOTO -> Unit
        }
        if (custom != null && custom.iconValue.isNotEmpty()) {
            return if (isEmojiIcon(custom.iconValue)) custom.iconValue to null
            else null to (custom.iconValue.takeIf(SymbolIcons::has) ?: reminder.category.defaultSymbol)
        }
        return null to reminder.category.defaultSymbol
    }

    private fun countdownState(context: Context, events: List<CountdownEvent>, appWidgetId: Int): CountdownWidgetState {
        val now = System.currentTimeMillis()
        val chosenId = WidgetPrefs.countdownFor(context, appWidgetId)
        // El elegido; si se borró (o no se eligió), el próximo.
        val event = events.firstOrNull { it.id == chosenId } ?: CountdownOrder.default(events, now)
        val color = WidgetGraphics.parse(event?.colorHex ?: CountdownEvent.DEFAULT_COLOR_HEX, 0xFFFF2D55.toInt())
        val photoName = event?.photoPath?.takeIf { event.iconKind == ReminderIconKind.PHOTO }
        // Con más resolución, el widget pasa del límite de memoria de las RemoteViews.
        val photo = photoName?.let { CountdownPhoto.load(CalarmiApp.container(context).photos.file(it), maxSide = 720) }
        val tile = if (event != null && photo == null) {
            val emoji = if (event.iconKind == ReminderIconKind.EMOJI) {
                event.iconValue.takeIf(::isEmojiIcon) ?: CountdownEvent.DEFAULT_EMOJI
            } else null
            WidgetGraphics.tile(
                px(context, ICON_DP), WidgetGraphics.withAlpha(android.graphics.Color.WHITE, 0.22f), cornerFraction = 0.28f,
                emoji = emoji,
                symbol = if (emoji == null) SymbolIcons.of(countdownSymbol(event.iconKind, event.iconValue)) else null,
                symbolColor = android.graphics.Color.WHITE,
                contentFraction = if (emoji != null) 0.56f else 0.46f,
            )
        } else null
        return CountdownWidgetState(
            event = event,
            now = now,
            photo = photo,
            background = WidgetGraphics.gradient(
                WidgetGraphics.mix(color, android.graphics.Color.WHITE, 0.12f),
                WidgetGraphics.mix(color, android.graphics.Color.BLACK, 0.3f),
            ),
            tile = tile,
            emptyIcon = WidgetGraphics.vector(Icons.Filled.HourglassBottom, px(context, 32), android.graphics.Color.WHITE),
            repeatIcon = if (event?.repeatsYearly == true) {
                WidgetGraphics.vector(Icons.Filled.Repeat, px(context, 14), WidgetGraphics.withAlpha(android.graphics.Color.WHITE, 0.85f))
            } else null,
        )
    }
}

/**
 * Qué cuenta muestra cada widget de cuenta regresiva, por `appWidgetId`. En el
 * almacenamiento protegido por dispositivo, como el resto de los datos.
 */
internal object WidgetPrefs {
    private fun prefs(context: Context): SharedPreferences =
        CalarmiApp.container(context).storage.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "countdown.$appWidgetId"

    fun countdownFor(context: Context, appWidgetId: Int): String? = prefs(context).getString(key(appWidgetId), null)

    /** `null` = sin elegir: el widget sigue a la próxima. */
    fun setCountdown(context: Context, appWidgetId: Int, countdownId: String?) {
        prefs(context).edit().apply {
            if (countdownId == null) remove(key(appWidgetId)) else putString(key(appWidgetId), countdownId)
        }.apply()
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply { appWidgetIds.forEach { remove(key(it)) } }.apply()
    }

    /** Al restaurar una copia los widgets cambian de id: la elección se muda con ellos. */
    fun migrate(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = prefs(context)
        val values = oldIds.map { prefs.getString(key(it), null) }
        prefs.edit().apply {
            oldIds.forEach { remove(key(it)) }
            newIds.forEachIndexed { i, id -> values.getOrNull(i)?.let { putString(key(id), it) } }
        }.apply()
    }
}
