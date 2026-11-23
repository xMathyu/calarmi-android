package com.mathyusolutions.calarmi.challenge.arcade

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Piezas comunes de los retos de arcade (Vuelo, Ovejas, Cañón y Tirachinas):
// el bucle de fotogramas, la cabecera de progreso, el marco del escenario, la
// pulsación mantenida, el estado de «ganado» y un generador aleatorio con
// semilla para que la lógica de cada juego se pueda probar fuera de la app.

// MARK: - Lógica pura (sin Compose)

/**
 * Generador con semilla (SplitMix64): la app lo siembra al azar y las pruebas
 * con una semilla fija, para repetir la misma partida.
 */
class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** En [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * UNIT

    /** En [from, to]. */
    fun nextDouble(from: Double, to: Double): Double = from + (to - from) * nextDouble()

    fun nextBoolean(): Boolean = nextLong() < 0

    /** En [0, until). */
    fun nextInt(until: Int): Int = ((nextLong() ushr 1) % until).toInt()

    private companion object {
        val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
        val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX2 = 0x94D049BB133111EBuL.toLong()
        const val UNIT = 1.0 / 9007199254740992.0
    }
}

/** Resto siempre positivo, para que los fondos que se desplazan den la vuelta. */
fun arcadeWrap(value: Double, period: Double): Double {
    val r = value % period
    return if (r < 0) r + period else r
}

/** Un punto lógico de un escenario. */
data class Pt(val x: Double, val y: Double)

// MARK: - Bucle de fotogramas

/** Paso máximo: si la app estuvo parada no se integra de golpe el tiempo perdido. */
private const val MAX_STEP = 1.0 / 30.0

/**
 * Llama a `step` en cada fotograma con los segundos transcurridos desde el
 * anterior. Solo corre con la pantalla al frente (RESUMED): al salir el
 * composable, apagarse la pantalla o tapar la app, el juego queda en pausa y
 * al volver sigue donde estaba, sin saltos.
 */
@Composable
internal fun ArcadeLoop(key: Any, step: (dt: Double) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentStep by rememberUpdatedState(step)
    LaunchedEffect(key, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1e9).coerceIn(0.0, MAX_STEP)
                    last = now
                    currentStep(dt)
                }
            }
        }
    }
}

// MARK: - Ganar

/** Lo que tarda el «¡Lo lograste!» antes de avisar a la pantalla del reto. */
private const val SUCCESS_DELAY_MS = 700L

/**
 * El final de la partida: `win()` enseña el aviso de éxito y, pasado un
 * instante, llama a `onSolved` una sola vez aunque se gane dos veces.
 */
@Stable
internal class ArcadeWinState {
    var won by mutableStateOf(false)
        private set
    internal var reported = false

    fun win() {
        won = true
    }
}

@Composable
internal fun rememberArcadeWin(onSolved: () -> Unit): ArcadeWinState {
    val state = remember { ArcadeWinState() }
    val currentOnSolved by rememberUpdatedState(onSolved)
    val haptics = rememberArcadeHaptics()
    LaunchedEffect(state.won) {
        if (!state.won || state.reported) return@LaunchedEffect
        haptics.success()
        delay(SUCCESS_DELAY_MS)
        if (!state.reported) {
            state.reported = true
            currentOnSolved()
        }
    }
    return state
}

// MARK: - Vibración

/** Los `Haptics` de iOS con los tipos de Compose más parecidos. */
internal class ArcadeHaptics(private val feedback: HapticFeedback) {
    fun light() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun soft() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    fun rigid() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    fun warning() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
}

@Composable
internal fun rememberArcadeHaptics(): ArcadeHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { ArcadeHaptics(feedback) }
}

// MARK: - Maquetación

/** Ancho máximo del escenario: en tablet crece, pero sin volverse enorme. */
private val MAX_ARENA_WIDTH = 520.dp
private val MIN_ARENA_HEIGHT = 140.dp

/**
 * Cabecera, escenario y controles en columna. El escenario conserva su
 * proporción y ocupa todo lo que deja el resto: si el alto no alcanza (móvil
 * en horizontal, pantallas bajas) se achica a lo ancho en lugar de cortarse.
 * Funciona tanto con alto acotado como dentro de un scroll.
 */
@Composable
internal fun ArcadeScaffold(
    aspectRatio: Float,
    header: @Composable () -> Unit,
    arena: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Layout(contents = listOf(header, arena, footer), modifier = modifier) { (headerM, arenaM, footerM), constraints ->
        val gap = DS.md.roundToPx()
        val cap = MAX_ARENA_WIDTH.roundToPx()
        val outerWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else cap
        val contentWidth = min(outerWidth, cap)
        val slot = Constraints(minWidth = contentWidth, maxWidth = contentWidth)
        val headerP = headerM.map { it.measure(slot) }
        val footerP = footerM.map { it.measure(slot) }
        val headerH = headerP.sumOf { it.height }
        val footerH = footerP.sumOf { it.height }
        val gaps = (if (headerH > 0) gap else 0) + (if (footerH > 0) gap else 0)

        var arenaW = contentWidth.toFloat()
        var arenaH = arenaW / aspectRatio
        if (constraints.hasBoundedHeight) {
            val available = (constraints.maxHeight - headerH - footerH - gaps).toFloat()
                .coerceAtLeast(MIN_ARENA_HEIGHT.toPx())
            if (arenaH > available) {
                arenaH = available
                arenaW = arenaH * aspectRatio
            }
        }
        val aw = arenaW.roundToInt().coerceAtLeast(1)
        val ah = arenaH.roundToInt().coerceAtLeast(1)
        val arenaP = arenaM.map { it.measure(Constraints.fixed(aw, ah)) }

        val totalH = headerH + ah + footerH + gaps
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else contentWidth
        val height = totalH.coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else totalH)
        layout(width, height) {
            // Si sobra alto (el padre pide llenar), el conjunto queda centrado.
            var y = ((height - totalH) / 2).coerceAtLeast(0)
            headerP.forEach {
                it.place((width - it.width) / 2, y)
                y += it.height
            }
            if (headerH > 0) y += gap
            arenaP.forEach { it.place((width - aw) / 2, y) }
            y += ah
            if (footerH > 0) y += gap
            footerP.forEach {
                it.place((width - it.width) / 2, y)
                y += it.height
            }
        }
    }
}

/** «Huecos 3 de 8» con su barra. */
@Composable
internal fun ArcadeProgressHeader(label: String, value: Int, total: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) min(value, total).toFloat() / total else 0f,
        label = "arcadeProgress",
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(DS.sm))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * El marco de todos los escenarios: esquinas redondeadas y borde suave. Los
 * toques se reciben en este contenedor, así que los avisos que flotan encima
 * no los interceptan.
 */
@Composable
internal fun ArcadeArena(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DS.radiusLg)
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .semantics { contentDescription = description },
        content = content,
    )
}

/** Un aviso flotante sobre el escenario («Toca para volar»). */
@Composable
internal fun BoxScope.ArcadeBanner(visible: Boolean, text: String, alignment: Alignment) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(alignment).padding(DS.md),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f), CircleShape)
                .padding(horizontal = DS.lg, vertical = DS.sm),
        )
    }
}

/** El instante de éxito antes de que la pantalla del reto tome el relevo. */
@Composable
internal fun BoxScope.ArcadeSuccess(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut(),
    ) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
                .padding(horizontal = DS.xl, vertical = DS.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SUCCESS_GREEN, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(DS.sm))
            Text(
                stringResource(R.string.arcade_success),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val SUCCESS_GREEN = Color(0xFF34C759)

/** Si el tema que se está usando es oscuro (la pantalla de alarma lo fuerza). */
@Composable
internal fun isArcadeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// MARK: - Toques

/**
 * Avisa al apoyar el dedo, al moverlo y al levantarlo, sin esperar a que
 * termine el toque como un `clickable`. Las coordenadas llegan ya en unidades
 * lógicas del escenario (y hacia abajo). Si el sistema cancela el gesto
 * también llega `onUp`, así que nunca se queda «pulsado».
 */
internal fun Modifier.arcadePress(
    key: Any,
    logicalWidth: Double,
    onDown: (x: Double, y: Double) -> Unit,
    onMove: ((x: Double, y: Double) -> Unit)? = null,
    onUp: () -> Unit = {},
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val scale = if (size.width > 0) logicalWidth / size.width else 1.0
        onDown(down.position.x * scale, down.position.y * scale)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (onMove != null && change.positionChanged()) {
                    onMove(change.position.x * scale, change.position.y * scale)
                    change.consume()
                }
            }
        } finally {
            onUp()
        }
    }
}

// MARK: - Dibujo

/**
 * Dibuja en coordenadas lógicas: el escenario mide `logicalWidth` de ancho
 * sea cual sea la pantalla, y todo se escala a la vez.
 */
internal inline fun DrawScope.logical(logicalWidth: Double, block: DrawScope.() -> Unit) {
    val s = size.width / logicalWidth.toFloat()
    withTransform({ scale(s, s, pivot = Offset.Zero) }) { block() }
}

/**
 * Un emoji como «sprite», centrado en un punto, girado y, si hace falta,
 * volteado en horizontal: los emojis de animales miran a la izquierda y en
 * los juegos avanzan hacia la derecha. Se dibuja con el lienzo nativo para que
 * el tamaño sea el lógico (sin escala de fuente del sistema) y no haya que
 * medir texto en cada fotograma.
 */
internal class EmojiSprite(private val text: String) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun draw(
        scope: DrawScope,
        x: Float,
        y: Float,
        size: Float,
        rotationDegrees: Float = 0f,
        flipped: Boolean = false,
        alpha: Float = 1f,
    ) {
        if (alpha <= 0f || size <= 0f) return
        paint.textSize = size
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        val metrics = paint.fontMetrics
        val baseline = -(metrics.ascent + metrics.descent) / 2f
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(x, y)
            native.rotate(rotationDegrees)
            if (flipped) native.scale(-1f, 1f)
            native.drawText(text, 0f, baseline, paint)
            native.restore()
        }
    }
}

/** Un rótulo de texto pequeño dentro del escenario («45°», «Viento 3»). */
internal class CanvasLabel {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun draw(scope: DrawScope, text: String, x: Float, y: Float, size: Float, color: Color) {
        paint.textSize = size
        paint.color = color.toArgb()
        val metrics = paint.fontMetrics
        val baseline = y - (metrics.ascent + metrics.descent) / 2f
        scope.drawIntoCanvas { it.nativeCanvas.drawText(text, x, baseline, paint) }
    }
}
