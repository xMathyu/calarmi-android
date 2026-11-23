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
import kotlin.math.min
import kotlin.math.sin

/**
 * «Ovejas»: la oveja corre hacia el amanecer y salta las vallas; mantener el
 * dedo la eleva más. Cada valla saltada sube el sol. La lógica vive en
 * [HurdlesGame].
 */
@Composable
fun HurdlesChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val game = remember(difficulty) { HurdlesGame(difficulty) }
    val win = rememberArcadeWin(onSolved)
    val haptics = rememberArcadeHaptics()
    val frame = remember { mutableLongStateOf(0L) }
    var hud by remember(game) { mutableStateOf(HurdlesHud.of(game)) }

    ArcadeLoop(game) { dt ->
        for (event in game.step(dt)) {
            when (event) {
                HurdlesGame.Event.CLEARED -> haptics.light()
                HurdlesGame.Event.CRASHED -> haptics.warning()
                HurdlesGame.Event.SOLVED -> win.win()
            }
        }
        hud = HurdlesHud.of(game)
        frame.longValue++
    }

    val sheep = remember { EmojiSprite("🐑") }

    ArcadeScaffold(
        aspectRatio = (HurdlesGame.WIDTH / HurdlesGame.HEIGHT).toFloat(),
        modifier = modifier,
        header = {
            ArcadeProgressHeader(
                label = stringResource(R.string.arcade_hurdles_progress, hud.cleared, game.required),
                value = hud.cleared,
                total = game.required,
            )
        },
        arena = {
            ArcadeArena(
                description = stringResource(R.string.challenge_hurdles_desc),
                modifier = Modifier.arcadePress(
                    game,
                    HurdlesGame.WIDTH,
                    onDown = { _, _ -> game.press() },
                    onUp = { game.release() },
                ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.longValue
                    logical(HurdlesGame.WIDTH) { drawHurdles(game, sheep) }
                }
                ArcadeBanner(hud.phase == HurdlesGame.Phase.READY, stringResource(R.string.arcade_hurdles_start), Alignment.TopCenter)
                ArcadeBanner(hud.canContinue, stringResource(R.string.arcade_hurdles_crash), Alignment.TopCenter)
                ArcadeSuccess(win.won)
            }
        },
    )
}

private data class HurdlesHud(val cleared: Int, val phase: HurdlesGame.Phase, val canContinue: Boolean) {
    companion object {
        fun of(game: HurdlesGame) = HurdlesHud(game.cleared, game.phase, game.canContinue)
    }
}

private class Star(val x: Float, val y: Float, val size: Float)

/** Siempre las mismas estrellas: semilla fija. */
private val stars: List<Star> = SplitMix64(7).let { rng ->
    List(26) {
        Star(
            x = rng.nextDouble(4.0, 356.0).toFloat(),
            y = rng.nextDouble(6.0, 130.0).toFloat(),
            size = rng.nextDouble(1.2, 2.6).toFloat(),
        )
    }
}

/** Mezcla dos colores RGB: 0 es la noche y 1 el día. */
private fun mix(a: FloatArray, b: FloatArray, t: Float) =
    Color(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)

private val skyTopNight = floatArrayOf(0.05f, 0.07f, 0.20f)
private val skyTopDay = floatArrayOf(0.42f, 0.70f, 0.95f)
private val skyBottomNight = floatArrayOf(0.17f, 0.14f, 0.32f)
private val skyBottomDay = floatArrayOf(1.00f, 0.80f, 0.58f)
private val hillsNight = floatArrayOf(0.10f, 0.14f, 0.24f)
private val hillsDay = floatArrayOf(0.42f, 0.62f, 0.45f)
private val grassNight = floatArrayOf(0.13f, 0.25f, 0.17f)
private val grassDay = floatArrayOf(0.40f, 0.70f, 0.33f)
private val wood = Color(0.62f, 0.42f, 0.24f)
private val woodDark = Color(0.45f, 0.29f, 0.15f)
private val sunCore = Color(1f, 0.78f, 0.30f)
private val sunGlow = Color(1f, 0.85f, 0.4f)

private fun DrawScope.drawHurdles(game: HurdlesGame, sheep: EmojiSprite) {
    val width = HurdlesGame.WIDTH.toFloat()
    val height = HurdlesGame.HEIGHT.toFloat()
    val groundY = HurdlesGame.GROUND_Y.toFloat()
    // De la noche (0) al día (1) según el progreso. El escenario es de noche
    // por diseño, así que se ve igual en tema claro y oscuro.
    val dawn = min(game.cleared.toFloat() / game.required, 1f)

    // Cielo: azul noche → amanecer.
    drawRect(
        Brush.verticalGradient(listOf(mix(skyTopNight, skyTopDay, dawn), mix(skyBottomNight, skyBottomDay, dawn)), startY = 0f, endY = groundY),
        size = Size(width, height),
    )

    // Estrellas que se apagan al amanecer.
    if (dawn < 1f) {
        val starColor = Color.White.copy(alpha = 0.8f * (1 - dawn))
        for (star in stars) drawCircle(starColor, star.size / 2, Offset(star.x, star.y))
    }

    // El sol sale por detrás de las colinas.
    val sun = Offset(280f, groundY + 26 - dawn * 150)
    drawCircle(
        Brush.radialGradient(
            0f to sunGlow.copy(alpha = 0.55f),
            18f / 44f to sunGlow.copy(alpha = 0.55f),
            1f to Color.Transparent,
            center = sun,
            radius = 44f,
        ),
        radius = 44f,
        center = sun,
    )
    drawCircle(sunCore, 20f, sun)

    // Colinas lejanas, que se mueven despacio.
    val hills = Path()
    hills.moveTo(0f, groundY)
    var x = 0.0
    while (x <= HurdlesGame.WIDTH) {
        val worldX = x + game.scroll * 0.2
        val y = HurdlesGame.GROUND_Y - 26 - 14 * sin(worldX / 53) - 8 * sin(worldX / 21 + 1.3)
        hills.lineTo(x.toFloat(), y.toFloat())
        x += 6
    }
    hills.lineTo(width, groundY)
    hills.close()
    drawPath(hills, mix(hillsNight, hillsDay, dawn))

    // Hierba con matas que corren con el suelo.
    drawRect(mix(grassNight, grassDay, dawn), Offset(0f, groundY), Size(width, height - groundY))
    drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, groundY), Size(width, 3f))
    val tufts = Path()
    val gap = 34.0
    var tx = -arcadeWrap(game.scroll, gap)
    while (tx < HurdlesGame.WIDTH + gap) {
        val fx = tx.toFloat()
        tufts.moveTo(fx, groundY + 18)
        tufts.lineTo(fx + 3, groundY + 11)
        tufts.moveTo(fx + 6, groundY + 18)
        tufts.lineTo(fx + 6, groundY + 10)
        tx += gap
    }
    drawPath(tufts, Color.Black.copy(alpha = 0.18f), style = Stroke(width = 2f, cap = StrokeCap.Round))

    // Vallas de madera.
    val fenceWidth = HurdlesGame.FENCE_WIDTH.toFloat()
    val corner = CornerRadius(2f)
    for (obstacle in game.obstacles) {
        val h = obstacle.height.toFloat()
        for (fenceXd in obstacle.fenceXs) {
            val fenceX = fenceXd.toFloat()
            drawRoundRect(woodDark, Offset(fenceX, groundY - h), Size(5f, h), corner)
            drawRoundRect(woodDark, Offset(fenceX + fenceWidth - 5, groundY - h), Size(5f, h), corner)
            drawRoundRect(wood, Offset(fenceX - 2, groundY - h + 4), Size(fenceWidth + 4, 5f), corner)
            drawRoundRect(wood, Offset(fenceX - 2, groundY - h * 0.45f), Size(fenceWidth + 4, 5f), corner)
        }
    }

    // La oveja: los pies a `jumpHeight` del suelo; al tropezar rueda.
    val spin = if (game.phase == HurdlesGame.Phase.CRASHED) -min(game.crashElapsed / 0.3, 1.0) * 100 else 0.0
    sheep.draw(
        this,
        HurdlesGame.SHEEP_X.toFloat(),
        (HurdlesGame.GROUND_Y - game.jumpHeight - 17).toFloat(),
        40f,
        spin.toFloat(),
        flipped = true,
    )
}
