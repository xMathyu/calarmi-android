package com.mathyusolutions.calarmi.challenge

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay

/**
 * Prueba de Stroop: el nombre de un color escrito con una tinta distinta
 * («Rojo» pintado de azul) y, debajo, muestras redondas sin texto. Hay que
 * tocar la del color de la TINTA, no la del color que dice la palabra. Leer es
 * automático y mirar el color no, así que cuesta lo justo para despejarse.
 * Fallar sacude la muestra, la marca en rojo y repite la ronda con otros colores.
 */
@Composable
fun ColorsChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val totalRounds = ColorsRules.totalRounds(difficulty)
    val optionCount = ColorsRules.optionCount(difficulty)
    var round by remember { mutableIntStateOf(1) }
    var current by remember(difficulty) { mutableStateOf(StroopRound.random(optionCount, avoiding = null)) }
    // Hubo un fallo en esta ronda: el aviso se pone en rojo hasta acertar.
    var wrong by remember { mutableStateOf(false) }
    // La muestra que se tocó mal, para el borde rojo mientras dura el bloqueo.
    var wrongSwatch by remember { mutableStateOf<StroopColor?>(null) }
    // Cuántas veces se ha sacudido cada muestra.
    val shakes = remember { mutableStateMapOf<StroopColor, Int>() }
    // Cambia con cada fallo y reinicia la espera que bloquea los toques un
    // instante: así no sirve de nada aporrear las muestras al azar.
    var lockToken by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }

    LaunchedEffect(lockToken) {
        if (lockToken == 0) return@LaunchedEffect
        delay(450)
        wrongSwatch = null
        // Ronda nueva con la misma cuenta: fallar no puede servir para ir
        // descartando colores hasta dar con el de la tinta.
        current = StroopRound.random(optionCount, avoiding = current)
        locked = false
    }

    fun tap(color: StroopColor) {
        if (locked || solved) return
        if (color == current.ink) {
            wrong = false
            wrongSwatch = null
            if (round >= totalRounds) {
                solved = true
                onSolved()
            } else {
                haptics.light()
                round += 1
                current = StroopRound.random(optionCount, avoiding = current)
            }
        } else {
            haptics.error()
            wrong = true
            wrongSwatch = color
            shakes[color] = (shakes[color] ?: 0) + 1
            // Se bloquea AQUÍ, de forma síncrona: la espera arranca un fotograma
            // después y, mientras tanto, otros toques (o varios dedos a la vez)
            // seguirían entrando.
            locked = true
            lockToken += 1
        }
    }

    val palette = stroopPalette()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_round_of, round, totalRounds))

        val wordName = stringResource(current.word.nameRes)
        val inkName = stringResource(current.ink.nameRes)
        val a11y = stringResource(R.string.challenges_colors_a11y, wordName, inkName)
        Box(
            Modifier
                .widthIn(max = GameMaxWidth)
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(DS.radiusLg))
                .background(tileColor)
                .semantics { contentDescription = a11y },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut()) },
                label = "word",
            ) { shown ->
                BasicText(
                    stringResource(shown.word.nameRes),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg),
                    style = TextStyle(
                        color = palette.getValue(shown.ink),
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 48.sp),
                )
            }
        }

        Text(
            stringResource(if (wrong) R.string.challenges_colors_wrong else R.string.challenges_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (wrong) errorRed else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 18.dp),
        )

        val perRow = if (optionCount == 6) 3 else 4
        BoxWithConstraints(Modifier.widthIn(max = GameMaxWidth).fillMaxWidth()) {
            val gap = DS.md
            // 72 dp como en iOS, o menos si la fila no cabe en un teléfono estrecho.
            val swatchSize = minOf(72.dp, (maxWidth - gap * (perRow - 1)) / perRow)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                current.options.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { color ->
                            Swatch(
                                color = palette.getValue(color),
                                name = stringResource(color.nameRes),
                                wrong = wrongSwatch == color,
                                shakeCount = shakes[color] ?: 0,
                                onClick = { tap(color) },
                                modifier = Modifier.size(swatchSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@get:StringRes
private val StroopColor.nameRes: Int
    get() = when (this) {
        StroopColor.RED -> R.string.challenges_color_red
        StroopColor.GREEN -> R.string.challenges_color_green
        StroopColor.BLUE -> R.string.challenges_color_blue
        StroopColor.YELLOW -> R.string.challenges_color_yellow
        StroopColor.PURPLE -> R.string.challenges_color_purple
        StroopColor.ORANGE -> R.string.challenges_color_orange
    }

/** Los colores del sistema de iOS, en su versión clara u oscura. */
@Composable
private fun stroopPalette(): Map<StroopColor, Color> = if (isDarkScheme) {
    mapOf(
        StroopColor.RED to Color(0xFFFF453A),
        StroopColor.GREEN to Color(0xFF30D158),
        StroopColor.BLUE to Color(0xFF0A84FF),
        StroopColor.YELLOW to Color(0xFFFFD60A),
        StroopColor.PURPLE to Color(0xFFBF5AF2),
        StroopColor.ORANGE to Color(0xFFFF9F0A),
    )
} else {
    mapOf(
        StroopColor.RED to Color(0xFFFF3B30),
        StroopColor.GREEN to Color(0xFF34C759),
        StroopColor.BLUE to Color(0xFF007AFF),
        StroopColor.YELLOW to Color(0xFFFFCC00),
        StroopColor.PURPLE to Color(0xFFAF52DE),
        StroopColor.ORANGE to Color(0xFFFF9500),
    )
}

@Composable
private fun Swatch(color: Color, name: String, wrong: Boolean, shakeCount: Int, onClick: () -> Unit, modifier: Modifier) {
    val red = errorRed
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val scale by animateFloatAsState(if (wrong) 0.92f else 1f, label = "scale")
    val ring by animateFloatAsState(if (wrong) 1f else 0f, label = "ring")
    Box(
        modifier
            .shakeOnChange(shakeCount)
            .scale(scale)
            .shadow(6.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.18f), color)))
            .border(1.dp, outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = name },
    ) {
        Box(Modifier.fillMaxSize().border(4.dp, red.copy(alpha = ring), CircleShape))
    }
}
