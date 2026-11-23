package com.mathyusolutions.calarmi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.data.Settings
import com.mathyusolutions.calarmi.model.AppearanceMode

/** Medidas compartidas (los `DS.Spacing` / `DS.Radius` de iOS). */
object DS {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 20.dp

    val avatarXs = 28.dp
    val avatarSm = 32.dp
    val avatarMd = 44.dp
    val avatarLg = 56.dp
    val avatarXl = 72.dp
    val avatarHero = 120.dp
}

/** El acento elegido, para pintar rellenos y degradados fuera de los controles. */
val LocalAccent = staticCompositionLocalOf { Color(0xFFFF6633) }

fun parseHex(hex: String?): Color? {
    var s = hex?.trim() ?: return null
    if (s.startsWith("#")) s = s.drop(1)
    if (s.length != 6) return null
    val value = s.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}

@Composable
fun CalarmiTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val accent = parseHex(settings.accentColorHex) ?: parseHex(Settings.DEFAULT_ACCENT)!!
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.28f).compositeOver(Color(0xFF1C1C1E)),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(Color(0xFF1C1C1E)),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFF64D2FF),
            background = Color(0xFF000000),
            onBackground = Color.White,
            surface = Color(0xFF000000),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFF98989F),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = Color(0xFF48484A),
            outlineVariant = Color(0xFF38383A),
            error = Color(0xFFFF453A),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.16f).compositeOver(Color.White),
            onPrimaryContainer = Color(0xFF1C1C1E),
            secondary = accent,
            onSecondary = Color.White,
            secondaryContainer = accent.copy(alpha = 0.14f).compositeOver(Color.White),
            onSecondaryContainer = Color(0xFF1C1C1E),
            tertiary = Color(0xFF007AFF),
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFF2F2F7),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFE5E5EA),
            onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF2F2F7),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            outline = Color(0xFFC6C6C8),
            outlineVariant = Color(0xFFE5E5EA),
            error = Color(0xFFFF3B30),
        )
    }
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
