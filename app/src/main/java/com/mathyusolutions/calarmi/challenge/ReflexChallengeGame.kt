package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Un objetivo en pantalla: qué emoji, dónde está (px) y en qué punto de su vida va. */
private class ReflexTarget(val id: Long, val emoji: String, val center: Offset) {
    var popped by mutableStateOf(false)
    var expired by mutableStateOf(false)
    val live: Boolean get() = !popped && !expired
}

private val TargetSize = 64.dp

/**
 * «Atrapa»: emojis que asoman en puntos al azar del escenario y se esconden
 * enseguida. Hay que tocarlos antes de que desaparezcan; dejar escapar uno no
 * resta, solo no suma. El bucle que los hace aparecer vive en un efecto, así
 * muere con la vista; cada objetivo lleva su propia cuenta atrás.
 */
@Composable
fun ReflexChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val density = LocalDensity.current
    val required = ReflexRules.required(difficulty)
    val lifetime = ReflexRules.lifetimeMillis(difficulty)
    val maxVisible = ReflexRules.maxVisible(difficulty)
    val targets = remember { mutableStateListOf<ReflexTarget>() }
    var caught by remember { mutableIntStateOf(0) }
    var arena by remember { mutableStateOf(IntSize.Zero) }
    // Dónde estuvo el último objetivo: el siguiente sale en otro sitio, para
    // que haya que mover el dedo.
    var lastSpot by remember { mutableStateOf<Offset?>(null) }
    var solved by remember { mutableStateOf(false) }
    val nextId = remember { longArrayOf(0L) }

    fun spawn() {
        val occupied = targets.map { it.center } + listOfNotNull(lastSpot)
        val sizePx = with(density) { TargetSize.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val center = ReflexRules.freeSpot(arena.width.toFloat(), arena.height.toFloat(), sizePx, gap, occupied) ?: return
        lastSpot = center
        targets += ReflexTarget(nextId[0]++, ReflexRules.emojis.random(), center)
    }

    fun catchTarget(target: ReflexTarget) {
        if (solved || !target.live) return
        haptics.light()
        target.popped = true
        caught += 1
        if (caught >= required) {
            solved = true
            onSolved()
        }
    }

    // Saca objetivos mientras haya hueco, con una pausa corta y variable entre
    // uno y otro. Para en cuanto se resuelve.
    LaunchedEffect(Unit) {
        // El escenario aún no se ha medido: espera a tener tamaño.
        snapshotFlow { arena }.first { it != IntSize.Zero }
        delay(600)
        while (!solved) {
            if (targets.count { it.live } < maxVisible) spawn()
            delay(Random.nextLong(300, 651))
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.md)) {
        Column(
            Modifier.widthIn(max = GameMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            GameCaption(stringResource(R.string.challenges_reflex_caught_of, caught, required))
            val progress by animateFloatAsState(minOf(caught, required).toFloat() / required, label = "progress")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        val accent = MaterialTheme.colorScheme.primary
        val shape = RoundedCornerShape(DS.radiusXl)
        // 320 dp de alto como en iOS, o lo que quede en un teléfono bajito.
        Box(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = GameMaxWidth)
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .fillMaxHeight()
                .clip(shape)
                .background(tileColor)
                .border(1.5.dp, accent.copy(alpha = 0.2f), shape)
                .onSizeChanged { arena = it },
        ) {
            if (caught == 0 && targets.isEmpty()) {
                Text(
                    stringResource(R.string.challenges_reflex_ready),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            targets.forEach { target ->
                key(target.id) {
                    ReflexTargetView(
                        target = target,
                        lifetime = lifetime,
                        onTap = { catchTarget(target) },
                        onGone = { targets.remove(target) },
                    )
                }
            }
        }
    }
}

/**
 * El emoji dentro de un disco, con un anillo que se consume mientras dura. Al
 * atraparlo se infla y se desvanece; si se le acaba el tiempo, se esconde.
 */
@Composable
private fun ReflexTargetView(target: ReflexTarget, lifetime: Int, onTap: () -> Unit, onGone: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val enter = remember { Animatable(0f) }
    val ring = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        ring.animateTo(0f, tween(lifetime, easing = LinearEasing))
    }
    // Espera a que caduque (o a que termine la animación de captura) y lo retira.
    LaunchedEffect(target.popped) {
        if (target.popped) {
            delay(240)
        } else {
            delay(lifetime.toLong())
            target.expired = true
            delay(200)
        }
        onGone()
    }
    val popScale by animateFloatAsState(if (target.popped) 1.5f else if (target.expired) 0.6f else 1f, tween(220), label = "pop")
    val fade by animateFloatAsState(if (target.live) 1f else 0f, tween(200), label = "fade")
    val density = LocalDensity.current
    val half = with(density) { (TargetSize / 2).toPx() }
    // En dp y no en sp: con letra grande el emoji no debe salirse del disco.
    val emojiSize = with(density) { (TargetSize * 0.55f).toSp() }
    Box(
        Modifier
            .offset { IntOffset((target.center.x - half).roundToInt(), (target.center.y - half).roundToInt()) }
            .size(TargetSize)
            .graphicsLayer {
                val scale = (0.3f + 0.7f * enter.value) * popScale
                scaleX = scale
                scaleY = scale
                alpha = enter.value.coerceIn(0f, 1f) * fade
            }
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(
                enabled = target.live,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = target.emoji },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                accent,
                startAngle = -90f,
                sweepAngle = 360f * ring.value,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(target.emoji, fontSize = emojiSize)
    }
}
