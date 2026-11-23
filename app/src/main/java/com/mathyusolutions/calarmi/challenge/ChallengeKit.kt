package com.mathyusolutions.calarmi.challenge

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// Piezas compartidas por los juegos de reto: vibraciones, rótulos, colores y
// la sacudida de «fallo».

/** Ancho máximo de los juegos, como en iOS: en una tableta no se estiran. */
internal val GameMaxWidth = 360.dp

/**
 * Las vibraciones de iOS (`Haptics`) traducidas a las de Android. Pasan por la
 * vista, así que respetan el ajuste de «respuesta táctil» del sistema.
 */
internal class ChallengeHaptics(private val feedback: HapticFeedback) {
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun error() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    fun light() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    fun soft() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    fun selection() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
}

@Composable
internal fun rememberChallengeHaptics(): ChallengeHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { ChallengeHaptics(feedback) }
}

/** El rótulo de progreso de cada juego («Ronda 2 de 5»). */
@Composable
internal fun GameCaption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/**
 * El fondo de casillas, cartas y tableros: un gris que se distingue del fondo
 * de la pantalla del reto tanto en claro como en oscuro (la alarma siempre se
 * ve en oscuro).
 */
internal val tileColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

internal val isDarkScheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** El verde de «acierto» de iOS, en su versión clara u oscura. */
internal val successGreen: Color
    @Composable get() = if (isDarkScheme) Color(0xFF30D158) else Color(0xFF34C759)

/** El rojo de «fallo». */
internal val errorRed: Color
    @Composable get() = MaterialTheme.colorScheme.error

/**
 * Sacudida horizontal para señalar un fallo: cada vez que `count` sube en uno
 * hay tres vaivenes. Sin movimiento al aparecer.
 */
@Composable
internal fun Modifier.shakeOnChange(count: Int, amplitude: Dp = 7.dp): Modifier {
    val progress = remember { Animatable(count.toFloat()) }
    LaunchedEffect(count) {
        progress.animateTo(count.toFloat(), tween(400, easing = LinearEasing))
    }
    return graphicsLayer {
        translationX = amplitude.toPx() * sin(progress.value * PI * 6).toFloat()
    }
}

/** Anillo de progreso alrededor del contador (Sacudir, Caminar). */
@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier, lineWidth: Dp = 12.dp) {
    val accent = MaterialTheme.colorScheme.primary
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "ring")
    Canvas(modifier) {
        val stroke = lineWidth.toPx()
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        drawArc(accent.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (animated > 0f) {
            drawArc(accent, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** La actividad detrás de un `Context` de Compose (puede venir envuelto). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
