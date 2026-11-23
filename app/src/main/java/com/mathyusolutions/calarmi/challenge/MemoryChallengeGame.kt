package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Un «Simón» en una cuadrícula de 3×3: se ilumina una secuencia de casillas y
 * hay que repetirla. Tres rondas, cada una con una casilla más.
 */
@Composable
fun MemoryChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val scope = rememberCoroutineScope()
    var sequence by remember { mutableStateOf(emptyList<Int>()) }
    var round by remember { mutableIntStateOf(1) }
    // Cambia en cada ronda y en cada repetición tras un fallo: reinicia la
    // tarea que reproduce la secuencia.
    var attempt by remember { mutableIntStateOf(0) }
    var inputPhase by remember { mutableStateOf(false) }
    var litTile by remember { mutableStateOf<Int?>(null) }
    var inputCount by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        inputPhase = false
        inputCount = 0
        litTile = null
        if (sequence.isEmpty()) {
            sequence = MemoryRules.makeSequence(MemoryRules.startLength(difficulty) + round - 1)
        }
        delay(700)
        for (tile in sequence) {
            litTile = tile
            haptics.soft()
            delay(520)
            litTile = null
            delay(260)
        }
        wrong = false
        inputPhase = true
    }

    fun flash(tile: Int) {
        litTile = tile
        haptics.selection()
        scope.launch {
            delay(180)
            if (litTile == tile) litTile = null
        }
    }

    fun tap(tile: Int) {
        if (solved || !inputPhase || inputCount >= sequence.size) return
        if (sequence[inputCount] == tile) {
            flash(tile)
            inputCount += 1
            if (inputCount == sequence.size) {
                inputPhase = false
                if (round >= MemoryRules.TOTAL_ROUNDS) {
                    solved = true
                    onSolved()
                } else {
                    haptics.light()
                    sequence = emptyList()
                    round += 1
                    attempt += 1
                }
            }
        } else {
            inputPhase = false
            wrong = true
            haptics.error()
            // Misma secuencia otra vez.
            attempt += 1
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_round_of, round, MemoryRules.TOTAL_ROUNDS))
        Text(
            stringResource(
                when {
                    wrong -> R.string.challenges_memory_wrong
                    inputPhase -> R.string.challenges_memory_repeat
                    else -> R.string.challenges_memory_watch
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (wrong) errorRed else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 24.dp),
        )

        // Cuadrada y lo más grande que quepa: el alto que queda manda en un
        // teléfono bajito, el ancho (hasta 360 dp) en uno alto.
        Column(
            Modifier.weight(1f, fill = false).widthIn(max = GameMaxWidth).aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            for (row in 0 until 3) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                    for (col in 0 until 3) {
                        val tile = row * 3 + col
                        MemoryTile(
                            number = tile + 1,
                            lit = litTile == tile,
                            active = inputPhase,
                            onClick = { tap(tile) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTile(number: Int, lit: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(DS.radiusLg)
    val fill by animateColorAsState(if (lit) accent else tileColor, tween(120), label = "tile")
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.5.dp, accent.copy(alpha = if (active) 0.35f else 0.1f), shape)
            .clickable(enabled = active, onClick = onClick)
            .semantics { contentDescription = number.toString() },
    )
}
