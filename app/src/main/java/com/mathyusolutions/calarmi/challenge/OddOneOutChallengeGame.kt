package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
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
 * Una cuadrícula llena del mismo emoji salvo una casilla, que lleva otro muy
 * parecido. Hay que tocarla ronda tras ronda; cuanto más difícil, más casillas
 * y más rondas. Un fallo bloquea un instante los toques y cambia la cuadrícula
 * sin pasar de ronda, para que no valga ir probando a toda prisa.
 */
@Composable
fun OddOneOutChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val totalRounds = OddRules.totalRounds(difficulty)
    val columns = OddRules.columns(difficulty)
    val cellCount = columns * columns
    var current by remember(difficulty) { mutableStateOf(OddRules.newRound(1, null, cellCount)) }
    // La casilla tocada por error; mientras parpadea en rojo se ignoran los toques.
    var wrongIndex by remember { mutableStateOf<Int?>(null) }
    var solved by remember { mutableStateOf(false) }

    LaunchedEffect(wrongIndex) {
        if (wrongIndex == null) return@LaunchedEffect
        delay(350)
        // Cuadrícula nueva con la misma ronda: fallar no puede servir para ir
        // barriendo casillas hasta dar con la distinta.
        current = OddRules.newRound(current.round, current.common, cellCount)
        wrongIndex = null
    }

    fun tap(index: Int) {
        if (solved || wrongIndex != null) return
        if (index == current.oddIndex) {
            if (current.round >= totalRounds) {
                solved = true
                onSolved()
            } else {
                haptics.light()
                current = OddRules.newRound(current.round + 1, current.common, cellCount)
            }
        } else {
            haptics.error()
            wrongIndex = index
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_round_of, current.round, totalRounds))

        // La cuadrícula saliente y la entrante se solapan durante la transición
        // (solo al cambiar de ronda) sin empujar el resto de la pantalla.
        AnimatedContent(
            targetState = current,
            contentKey = { it.round },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)).togetherWith(fadeOut()) },
            modifier = Modifier.weight(1f, fill = false).widthIn(max = GameMaxWidth).aspectRatio(1f),
            label = "odd",
        ) { shown ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DS.sm)) {
                for (row in 0 until columns) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                        for (col in 0 until columns) {
                            val index = row * columns + col
                            OddCell(
                                emoji = if (index == shown.oddIndex) shown.odd else shown.common,
                                wrong = wrongIndex == index,
                                onClick = { tap(index) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OddCell(emoji: String, wrong: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val red = errorRed
    val shape = RoundedCornerShape(DS.radiusMd)
    val fill by animateColorAsState(if (wrong) red.copy(alpha = 0.25f) else tileColor, label = "fill")
    val border by animateColorAsState(if (wrong) red else Color.Transparent, label = "border")
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(fill)
            .border(2.dp, border, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = emoji },
        contentAlignment = Alignment.Center,
    ) {
        // Más pequeño cuantas más columnas, para que el 5×5 quepa.
        val size = with(LocalDensity.current) { minOf(44.sp.toDp(), minOf(maxWidth, maxHeight) * 0.6f).toSp() }
        Text(emoji, fontSize = size)
    }
}
