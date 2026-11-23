package com.mathyusolutions.calarmi.challenge.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.LocalAccent
import kotlin.math.max

/**
 * «Vuelo»: una abeja que cae sola y sube un poco con cada toque; hay que
 * pasarla por los huecos de las columnas. La lógica vive en [FlapGame].
 */
@Composable
fun FlapChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val game = remember(difficulty) { FlapGame(difficulty) }
    val win = rememberArcadeWin(onSolved)
    val haptics = rememberArcadeHaptics()
    // Se lee solo al dibujar: cada fotograma repinta el lienzo sin recomponer.
    val frame = remember { mutableLongStateOf(0L) }
    var hud by remember(game) { mutableStateOf(FlapHud.of(game)) }

    ArcadeLoop(game) { dt ->
        for (event in game.step(dt)) {
            when (event) {
                FlapGame.Event.PASSED -> haptics.light()
                FlapGame.Event.CRASHED -> haptics.warning()
                FlapGame.Event.SOLVED -> win.win()
            }
        }
        hud = FlapHud.of(game)
        frame.longValue++
    }

    val dark = isArcadeDark()
    val accent = LocalAccent.current
    val bee = remember { EmojiSprite("🐝") }

    ArcadeScaffold(
        aspectRatio = (FlapGame.WIDTH / FlapGame.HEIGHT).toFloat(),
        modifier = modifier,
        header = {
            ArcadeProgressHeader(
                label = stringResource(R.string.arcade_flap_progress, hud.passed, game.required),
                value = hud.passed,
                total = game.required,
            )
        },
        arena = {
            ArcadeArena(
                description = stringResource(R.string.challenge_flap_desc),
                modifier = Modifier.arcadePress(game, FlapGame.WIDTH, onDown = { _, _ -> game.press() }),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.longValue
                    logical(FlapGame.WIDTH) { drawFlap(game, dark, accent, bee) }
                }
                ArcadeBanner(hud.phase == FlapGame.Phase.READY, stringResource(R.string.arcade_flap_start), Alignment.Center)
                ArcadeBanner(hud.canContinue, stringResource(R.string.arcade_flap_crash), Alignment.Center)
                ArcadeSuccess(win.won)
            }
        },
    )
}

/** Lo que la interfaz enseña fuera del lienzo: solo recompone si cambia. */
private data class FlapHud(val passed: Int, val phase: FlapGame.Phase, val canContinue: Boolean) {
    companion object {
        fun of(game: FlapGame) = FlapHud(game.passed, game.phase, game.canContinue)
    }
}

private class Cloud(val x: Double, val y: Double, val scale: Float)

private val clouds = listOf(
    Cloud(40.0, 60.0, 1.0f),
    Cloud(210.0, 110.0, 0.8f),
    Cloud(330.0, 40.0, 1.2f),
    Cloud(130.0, 200.0, 0.7f),
)

private fun DrawScope.drawFlap(game: FlapGame, dark: Boolean, accent: Color, bee: EmojiSprite) {
    val width = FlapGame.WIDTH.toFloat()
    val height = FlapGame.HEIGHT.toFloat()
    val groundY = FlapGame.GROUND_Y.toFloat()

    // Cielo.
    val sky = if (dark) {
        listOf(Color(0.05f, 0.09f, 0.20f), Color(0.16f, 0.22f, 0.38f))
    } else {
        listOf(Color(0.55f, 0.80f, 0.98f), Color(0.92f, 0.97f, 1.00f))
    }
    drawRect(Brush.verticalGradient(sky, startY = 0f, endY = height), size = Size(width, height))

    // Nubes lejanas, más lentas que las columnas.
    val cloudColor = Color.White.copy(alpha = if (dark) 0.10f else 0.85f)
    for (cloud in clouds) {
        val x = (arcadeWrap(cloud.x - game.scroll * 0.3, FlapGame.WIDTH + 140) - 70).toFloat()
        val y = cloud.y.toFloat()
        val s = cloud.scale
        drawOval(cloudColor, Offset(x - 26 * s, y - 8 * s), Size(52 * s, 22 * s))
        drawOval(cloudColor, Offset(x - 14 * s, y - 18 * s), Size(30 * s, 26 * s))
    }

    // Columnas: se prolongan por fuera del escenario para que solo se vean
    // redondeadas las esquinas del hueco.
    val columnWidth = FlapGame.COLUMN_WIDTH.toFloat()
    val shine = Color.White.copy(alpha = 0.22f)
    for (column in game.columns) {
        val x = column.x.toFloat()
        val brush = Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.72f)), startX = x, endX = x + columnWidth)
        val top = column.gapTop.toFloat()
        val bottom = column.gapBottom.toFloat()
        for ((rectTop, rectHeight) in listOf(-30f to top + 30f, bottom to groundY - bottom + 30f)) {
            drawRoundRect(brush, Offset(x, rectTop), Size(columnWidth, rectHeight), CornerRadius(14f))
            drawRoundRect(shine, Offset(x + 8, rectTop + 10), Size(7f, max(0f, rectHeight - 20)), CornerRadius(3.5f))
        }
    }

    // Suelo con marcas que corren a la velocidad de las columnas.
    val ground = if (dark) Color(0.20f, 0.30f, 0.18f) else Color(0.55f, 0.78f, 0.40f)
    drawRect(ground, Offset(0f, groundY), Size(width, height - groundY))
    drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, groundY), Size(width, 4f))
    val marks = Path()
    val gap = 26.0
    var x = -arcadeWrap(game.scroll, gap)
    while (x < FlapGame.WIDTH + gap) {
        marks.moveTo(x.toFloat(), groundY + 12)
        marks.lineTo(x.toFloat() + 8, groundY + 20)
        x += gap
    }
    drawPath(marks, Color.Black.copy(alpha = 0.15f), style = Stroke(width = 3f, cap = StrokeCap.Round))

    // La abeja: se inclina hacia arriba al subir y hacia abajo al caer.
    val tilt = if (game.phase == FlapGame.Phase.CRASHED) 80.0 else (game.beeVY / 700 * 40).coerceIn(-25.0, 55.0)
    bee.draw(this, FlapGame.BEE_X.toFloat(), game.beeY.toFloat(), 34f, tilt.toFloat(), flipped = true)
}
