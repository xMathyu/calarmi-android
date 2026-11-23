package com.mathyusolutions.calarmi.challenge

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Una tabla de Schulte: los números del 1 al N revueltos en una cuadrícula y
 * hay que tocarlos en orden ascendente. Cada acierto apaga su casilla; en
 * difícil, cada fallo vuelve a revolver los números que faltan, así que no
 * vale ir de memoria. El cronómetro es solo para picarse.
 */
@Composable
fun OrderChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val count = OrderRules.count(difficulty)
    val columns = OrderRules.columns(difficulty)
    // Los números en el orden en que se pintan en la cuadrícula.
    var numbers by remember(difficulty) { mutableStateOf((1..count).shuffled()) }
    var next by remember { mutableIntStateOf(1) }
    // El número que se acaba de tocar por error; mientras parpadea en rojo se
    // ignoran los toques, para que no sirva aporrear la pantalla.
    var wrongNumber by remember { mutableStateOf<Int?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    // Cronómetro cosmético: se detiene al resolver y muere con la vista.
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (!solved) {
            delay(1000)
            if (!solved) elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
        }
    }

    // Apaga el rojo del fallo y, en difícil, revuelve lo que queda.
    LaunchedEffect(wrongNumber) {
        if (wrongNumber == null) return@LaunchedEffect
        delay(320)
        wrongNumber = null
        if (difficulty == WakeChallenge.Difficulty.HARD) numbers = OrderRules.reshuffleRemaining(numbers, next)
    }

    fun tap(number: Int) {
        if (solved || wrongNumber != null || number < next) return
        if (number == next) {
            haptics.selection()
            next += 1
            if (next > count) {
                solved = true
                onSolved()
            }
        } else {
            haptics.error()
            wrongNumber = number
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        val clock = String.format(Locale.ROOT, "%d:%02d", elapsed / 60, elapsed % 60)
        val timeLabel = stringResource(R.string.challenges_order_time)
        Row(Modifier.widthIn(max = GameMaxWidth).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.challenges_order_next, minOf(next, count)),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$timeLabel $clock" },
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DS.xs))
                Text(
                    clock,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val rows = (count + columns - 1) / columns
        BoxWithConstraints(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = GameMaxWidth)
                .aspectRatio(OrderRules.aspectRatio(difficulty)),
        ) {
            val spacing = DS.sm
            val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
            val tileHeight = (maxHeight - spacing * (rows - 1)) / rows
            numbers.forEachIndexed { position, number ->
                // Cada número conserva su casilla al revolverse: se desliza a su sitio nuevo.
                key(number) {
                    val x by animateDpAsState((tileWidth + spacing) * (position % columns), spring(stiffness = Spring.StiffnessMediumLow), label = "x")
                    val y by animateDpAsState((tileHeight + spacing) * (position / columns), spring(stiffness = Spring.StiffnessMediumLow), label = "y")
                    OrderTile(
                        number = number,
                        done = number < next,
                        wrong = wrongNumber == number,
                        onClick = { tap(number) },
                        modifier = Modifier.offset(x, y).size(tileWidth, tileHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTile(number: Int, done: Boolean, wrong: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val red = errorRed
    val fill by animateColorAsState(
        when {
            wrong -> red
            done -> accent
            else -> tileColor
        },
        label = "fill",
    )
    val fade by animateFloatAsState(if (done) 0.35f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "fade")
    val scale by animateFloatAsState(if (done) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "scale")
    BoxWithConstraints(
        modifier
            .graphicsLayer {
                alpha = fade
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(DS.radiusMd))
            .background(fill)
            .clickable(enabled = !done, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 26 sp como en iOS, salvo que la casilla sea tan baja que no quepa.
        val fontSize = with(LocalDensity.current) { minOf(26.sp.toDp(), maxHeight * 0.5f).toSp() }
        Text(
            number.toString(),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (done || wrong) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        )
    }
}
