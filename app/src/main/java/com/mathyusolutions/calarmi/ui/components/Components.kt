package com.mathyusolutions.calarmi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent

/**
 * El ícono de una alarma, categoría o cuenta regresiva: símbolo sobre su
 * color, emoji o foto.
 */
@Composable
fun ReminderIcon(
    iconKind: ReminderIconKind,
    iconValue: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    photoPath: String? = null,
    fallbackSymbol: String = "bell.fill",
    size: Dp = DS.avatarMd,
    shape: Shape = CircleShape,
) {
    val context = LocalContext.current
    val photo = remember(photoPath) {
        if (iconKind != ReminderIconKind.PHOTO || photoPath == null) null
        else runCatching {
            val file = CalarmiApp.container(context).photos.file(photoPath)
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.14f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            (iconKind == ReminderIconKind.EMOJI || isEmojiIcon(iconValue)) && !iconValue.isNullOrEmpty() ->
                Text(iconValue, fontSize = (size.value * 0.5f).sp)
            else -> Icon(
                SymbolIcons.of(iconValue?.takeIf { SymbolIcons.has(it) } ?: fallbackSymbol),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** Una sección agrupada al estilo de las listas de iOS: título, tarjeta y pie. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.sm)) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DS.lg, bottom = DS.xs + 2.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(DS.radiusMd),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DS.lg, end = DS.lg, top = DS.xs + 2.dp),
            )
        }
    }
}

/** Fila de una `SectionCard`: ícono opcional, título, valor a la derecha y flecha si navega. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = LocalAccent.current,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = DS.lg, vertical = DS.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(iconTint),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(DS.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = DS.sm),
                )
            }
            trailing?.invoke(this)
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = DS.xs),
                )
            }
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 58.dp else DS.lg),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

/** El círculo con ícono de las pantallas de bienvenida y permisos. */
@Composable
fun HeroIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalAccent.current, size: Dp = DS.avatarHero) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(DS.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.md),
    ) {
        HeroIcon(icon, size = DS.avatarXl)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
    }
}

/** La etiqueta «Plus» junto a lo que hace falta Plus para elegir. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Text(
        androidx.compose.ui.res.stringResource(R.string.plus_badge),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(accent, Color(0xFFAF52DE))))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * El ícono de la app (o una de sus variantes de color) como imagen. Los íconos son
 * adaptativos (`<adaptive-icon>`), que `painterResource` no sabe dibujar: se pintan
 * aquí sus dos capas a sangre completa, como en el lanzador, y se recortan con
 * esquinas redondeadas.
 */
@Composable
fun AppIconImage(res: Int, size: Dp, modifier: Modifier = Modifier, corner: Dp = size * 0.225f) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(res, px) {
        runCatching {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, res) ?: return@runCatching null
            val out = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
                // Cada capa mide 108 dp de los que se ven 72: un 25 % de sobra por lado.
                val extra = px / 4
                listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                    layer.setBounds(-extra, -extra, px + extra, px + extra)
                    layer.draw(canvas)
                }
            } else {
                drawable.setBounds(0, 0, px, px)
                drawable.draw(canvas)
            }
            out.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size).clip(RoundedCornerShape(corner)))
    } else {
        Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(LocalAccent.current))
    }
}
