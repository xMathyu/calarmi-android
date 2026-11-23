package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay

/**
 * El clásico juego de memoria con cartas: parejas de emojis boca abajo que se
 * levantan de dos en dos. Las que coinciden se quedan a la vista; las que no,
 * vuelven a taparse. Se acaba cuando no queda ninguna carta boca abajo.
 */
@Composable
fun PairsChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val cards = remember(difficulty) { PairsRules.deal(difficulty) }
    val pairCount = PairsRules.pairCount(difficulty)
    val columns = PairsRules.columns(difficulty)
    val rows = (cards.size + columns - 1) / columns
    // Cartas levantadas, incluidas las ya emparejadas.
    var faceUp by remember { mutableStateOf(emptySet<Int>()) }
    var matched by remember { mutableStateOf(emptySet<Int>()) }
    // La primera carta del intento en curso, si ya hay una levantada.
    var firstPick by remember { mutableStateOf<Int?>(null) }
    // Las dos cartas que no coincidieron: mientras esperan para taparse se
    // ignoran los toques.
    var mismatch by remember { mutableStateOf(emptyList<Int>()) }
    var solved by remember { mutableStateOf(false) }

    // Deja ver las dos cartas un momento y las vuelve a tapar.
    LaunchedEffect(mismatch) {
        if (mismatch.isEmpty()) return@LaunchedEffect
        delay(700)
        haptics.error()
        faceUp = faceUp - mismatch.toSet()
        mismatch = emptyList()
    }

    fun tap(id: Int) {
        if (solved || mismatch.isNotEmpty() || id in faceUp) return
        haptics.selection()
        faceUp = faceUp + id
        val first = firstPick
        if (first == null) {
            firstPick = id
            return
        }
        firstPick = null
        if (cards[first] == cards[id]) {
            haptics.light()
            matched = matched + first + id
            if (matched.size == cards.size) {
                solved = true
                onSolved()
            }
        } else {
            mismatch = listOf(first, id)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_pairs_of, matched.size / 2, pairCount))

        // Cartas de proporción 0,78 (como en iOS), lo más grandes que quepan.
        Column(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = GameMaxWidth)
                .aspectRatio(columns * CARD_RATIO / rows),
            verticalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            for (row in 0 until rows) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                    for (col in 0 until columns) {
                        val id = row * columns + col
                        if (id < cards.size) {
                            PairCard(
                                emoji = cards[id],
                                faceUp = id in faceUp,
                                matched = id in matched,
                                maxEmojiSize = if (difficulty == WakeChallenge.Difficulty.EASY) 44f else 36f,
                                onClick = { tap(id) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private const val CARD_RATIO = 0.78f

/**
 * Una carta que gira sobre su eje vertical. Hasta 90° se ve el dorso; a partir
 * de ahí, el frente, girado otros 180° para que no salga en espejo.
 */
@Composable
private fun PairCard(emoji: String, faceUp: Boolean, matched: Boolean, maxEmojiSize: Float, onClick: () -> Unit, modifier: Modifier) {
    val rotation by animateFloatAsState(if (faceUp) 180f else 0f, tween(380), label = "flip")
    // Un botecito cuando la carta encuentra a su pareja.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(matched) {
        if (matched) {
            bounce.animateTo(1.1f, spring(stiffness = Spring.StiffnessMedium))
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val density = LocalDensity.current.density
    val faceDown = stringResource(R.string.challenges_pairs_face_down)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clickable(enabled = !faceUp, onClick = onClick)
            .semantics { contentDescription = if (faceUp) emoji else faceDown },
    ) {
        if (rotation < 90f) {
            CardBack(Modifier.fillMaxSize())
        } else {
            CardFront(emoji, matched, maxEmojiSize, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
        }
    }
}

/** Dorso: tinte de acento con un destello discreto. */
@Composable
private fun CardBack(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(DS.radiusMd)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun CardFront(emoji: String, matched: Boolean, maxEmojiSize: Float, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(DS.radiusMd)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(tileColor)
            .border(if (matched) 2.dp else 1.dp, if (matched) accent else accent.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val size = with(LocalDensity.current) { minOf(maxEmojiSize.sp.toDp(), minOf(maxWidth, maxHeight) * 0.55f).toSp() }
        Text(emoji, fontSize = size)
    }
}
