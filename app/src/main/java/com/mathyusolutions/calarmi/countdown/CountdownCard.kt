package com.mathyusolutions.calarmi.countdown

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.domain.CountdownMath
import com.mathyusolutions.calarmi.domain.CountdownStatus
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Lo que pinta la tarjeta, sacado del modelo para que el editor pueda enseñar
 * un borrador que todavía no está guardado.
 */
data class CountdownCardContent(
    val id: String,
    val title: String,
    val date: Long,
    val includesTime: Boolean,
    val repeatsYearly: Boolean,
    val colorHex: String,
    val iconKind: ReminderIconKind,
    val iconValue: String,
    val photoPath: String?,
) {
    val color: Color get() = parseHex(colorHex) ?: Color(0xFFFF2D55)

    fun target(now: Long): Long = CountdownMath.target(date, includesTime, repeatsYearly, now)
    fun status(now: Long): CountdownStatus = CountdownMath.status(target(now), now)

    /** La foto solo cuando el ícono elegido es la foto. */
    val backgroundPhoto: String? get() = if (iconKind == ReminderIconKind.PHOTO) photoPath else null
}

fun CountdownEvent.cardContent() = CountdownCardContent(
    id = id,
    title = title,
    date = date,
    includesTime = includesTime,
    repeatsYearly = repeatsYearly,
    colorHex = colorHex,
    iconKind = iconKind,
    iconValue = iconValue,
    photoPath = photoPath,
)

/**
 * La tarjeta de una cuenta regresiva: la foto de fondo (o el color con el
 * emoji/símbolo), el nombre, la fecha y, grande, los días que faltan. La misma
 * tarjeta sirve de vista previa en el editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CountdownCard(
    content: CountdownCardContent,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val photo by rememberCountdownPhoto(content.backgroundPhoto)
    val status = content.status(now)
    val past = status.isPast
    val shape = RoundedCornerShape(DS.radiusXl)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            // Los pasados se ven apagados, para que lo próximo resalte.
            .graphicsLayer { alpha = if (past) 0.85f else 1f }
            .then(
                if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier,
            ),
    ) {
        CountdownBackground(content, photo, desaturated = past, modifier = Modifier.matchParentSize())
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (photo.hasPhoto) 156.dp else 96.dp)
                .padding(DS.lg),
            verticalAlignment = if (photo.hasPhoto) Alignment.Bottom else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DS.md),
        ) {
            if (!photo.hasPhoto) CountdownIconTile(content.iconKind, content.iconValue, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.xs)) {
                Text(
                    content.title.ifBlank { stringResource(R.string.countdowns_new_event) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CountdownDateLabel(
                    content, now,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            CountdownCounter(status, numberSize = 42.sp)
        }
    }
}

/** El fondo compartido por la tarjeta y el detalle: la foto oscurecida abajo, o el degradado del color. */
@Composable
fun CountdownBackground(
    content: CountdownCardContent,
    photo: CountdownPhotoState,
    modifier: Modifier = Modifier,
    desaturated: Boolean = false,
) {
    val image = photo.image
    if (image != null) {
        Box(modifier) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (desaturated) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) }) else null,
                modifier = Modifier.matchParentSize(),
            )
            // Oscurece la parte de abajo, donde va el texto blanco.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.65f)),
                    ),
                ),
            )
        }
    } else {
        val base = content.color.let { if (desaturated) it.desaturated(0.35f) else it }
        Box(modifier.background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.3f)))))
    }
}

/** `amount` = cuánto color queda (1 = intacto, 0 = gris). */
internal fun Color.desaturated(amount: Float): Color {
    val l = luminance()
    return lerp(Color(l, l, l, alpha), this, amount)
}

/** El emoji o símbolo sobre un cuadro translúcido. */
@Composable
fun CountdownIconTile(iconKind: ReminderIconKind, iconValue: String, size: Dp, modifier: Modifier = Modifier) {
    // En dp y no en sp: con letra grande del sistema, el emoji no se sale del cuadro.
    val emojiSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconKind == ReminderIconKind.EMOJI) {
            Text(if (isEmojiIcon(iconValue)) iconValue else CountdownEvent.DEFAULT_EMOJI, fontSize = emojiSize, maxLines = 1)
        } else {
            // Sin foto todavía: el símbolo de calendario ocupa su lugar.
            Icon(
                SymbolIcons.of(countdownSymbol(iconKind, iconValue)),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/** El símbolo que se pinta: el elegido o, si no hay (o es una foto), el calendario. */
fun countdownSymbol(iconKind: ReminderIconKind, iconValue: String): String =
    if (iconKind == ReminderIconKind.SYMBOL && !isEmojiIcon(iconValue) && SymbolIcons.has(iconValue)) iconValue else "calendar"

/** «sáb, 15 nov 2026 · 21:00», y el símbolo de repetir si vuelve cada año. */
@Composable
fun CountdownDateLabel(
    content: CountdownCardContent,
    now: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.xs)) {
        if (content.repeatsYearly) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.countdowns_yearly),
                tint = color,
                modifier = Modifier.size(with(LocalDensity.current) { style.fontSize.toDp() }),
            )
        }
        Text(
            CountdownFormat.date(context, content.target(now), content.includesTime),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** El número grande de días y su unidad, o «¡Hoy!». */
@Composable
fun CountdownCounter(
    status: CountdownStatus,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    style: TextStyle = TextStyle.Default,
) {
    when (status) {
        CountdownStatus.Today -> Text(
            stringResource(R.string.countdowns_today),
            modifier = modifier,
            style = style.merge(TextStyle(fontSize = numberSize * 0.6f, fontWeight = FontWeight.Black, color = color)),
            maxLines = 1,
            softWrap = false,
        )
        is CountdownStatus.Upcoming ->
            CounterStack(status.days, pluralStringResource(R.plurals.countdowns_days, status.days), numberSize, color, style, modifier)
        is CountdownStatus.Past ->
            CounterStack(status.days, pluralStringResource(R.plurals.countdowns_days_ago, status.days), numberSize, color, style, modifier)
    }
}

@Composable
private fun CounterStack(days: Int, unit: String, numberSize: TextUnit, color: Color, style: TextStyle, modifier: Modifier) {
    val locale = LocalContext.current.currentLocale()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            NumberFormat.getIntegerInstance(locale).format(days),
            style = style.merge(
                TextStyle(
                    fontSize = numberSize,
                    lineHeight = numberSize,
                    fontWeight = FontWeight.Black,
                    color = color,
                    // Cifras del mismo ancho: el número no baila al cambiar.
                    fontFeatureSettings = "tnum",
                ),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            unit.uppercase(locale),
            style = style.merge(
                TextStyle(
                    fontSize = if (numberSize.value * 0.3f < 11f) 11.sp else numberSize * 0.3f,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = color.alpha * 0.9f),
                ),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Textos de fecha compartidos por la app y los widgets. */
object CountdownFormat {
    /** «sáb, 15 nov 2026», con « · 21:00» si tiene hora. */
    fun date(context: Context, target: Long, includesTime: Boolean, withYear: Boolean = true): String {
        val locale = context.currentLocale()
        val pattern = DateFormat.getBestDateTimePattern(locale, if (withYear) "EEEdMMMyyyy" else "EEEdMMM")
        val day = DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()))
        return if (includesTime) "$day · ${time(context, target)}" else day
    }

    /** La hora con el formato de 12/24 h del teléfono. */
    fun time(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))
}

/** El idioma de la app (puede no ser el del sistema). */
fun Context.currentLocale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()
