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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min

/**
 * «Tirachinas»: se estira la goma desde la mitad izquierda y se suelta para
 * derribar los despertadores de las torres. La lógica y la física viven en
 * [SlingshotGame] y [PhysicsWorld].
 */
@Composable
fun SlingshotChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val game = remember(difficulty) { SlingshotGame(difficulty) }
    val win = rememberArcadeWin(onSolved)
    val haptics = rememberArcadeHaptics()
    val frame = remember { mutableLongStateOf(0L) }
    var hud by remember(game) { mutableStateOf(SlingshotHud.of(game)) }

    ArcadeLoop(game) { dt ->
        for (event in game.step(dt)) {
            when (event) {
                SlingshotGame.Event.KNOCKED -> haptics.light()
                SlingshotGame.Event.SOLVED -> win.win()
            }
        }
        hud = SlingshotHud.of(game)
        frame.longValue++
    }

    val dark = isArcadeDark()
    val clock = remember { EmojiSprite("⏰") }
    // Dónde se apoyó el dedo: la goma se estira lo que el dedo se aleja de ahí.
    val origin = remember { DoubleArray(2) }
    val shotsDescription = stringResource(R.string.arcade_sling_shots, hud.shotsLeft)

    ArcadeScaffold(
        aspectRatio = (SlingshotGame.WIDTH / SlingshotGame.HEIGHT).toFloat(),
        modifier = modifier,
        header = {
            ArcadeProgressHeader(
                label = stringResource(R.string.arcade_sling_progress, hud.knocked, game.required),
                value = hud.knocked,
                total = game.required,
            )
        },
        arena = {
            ArcadeArena(
                description = stringResource(R.string.challenge_sling_desc) + " " + shotsDescription,
                modifier = Modifier.arcadePress(
                    game,
                    SlingshotGame.WIDTH,
                    onDown = { x, y ->
                        // Basta con apoyar el dedo en la mitad izquierda, no hace falta atinar a la bola.
                        val sceneY = SlingshotGame.HEIGHT - y
                        if (x < SlingshotGame.WIDTH * 0.5 && game.beginPull()) {
                            origin[0] = x
                            origin[1] = sceneY
                            game.movePull(0.0, 0.0)
                        }
                    },
                    onMove = { x, y -> game.movePull(x - origin[0], (SlingshotGame.HEIGHT - y) - origin[1]) },
                    onUp = { game.endPull() },
                ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.longValue
                    logical(SlingshotGame.WIDTH) { drawSlingshot(game, dark, clock) }
                }
                ArcadeBanner(hud.message == SlingshotGame.Message.AIM, stringResource(R.string.arcade_sling_aim), Alignment.TopCenter)
                ArcadeBanner(hud.message == SlingshotGame.Message.CLEARED, stringResource(R.string.arcade_sling_cleared), Alignment.TopCenter)
                ArcadeBanner(hud.message == SlingshotGame.Message.OUT_OF_SHOTS, stringResource(R.string.arcade_sling_out), Alignment.TopCenter)
                ArcadeSuccess(win.won)
            }
        },
    )
}

private data class SlingshotHud(val knocked: Int, val shotsLeft: Int, val message: SlingshotGame.Message?) {
    companion object {
        fun of(game: SlingshotGame) = SlingshotHud(game.knocked, game.shotsLeft, if (game.isFinished) null else game.message)
    }
}

// MARK: - Dibujo (la escena tiene la y hacia arriba; el lienzo, hacia abajo)

private val H = SlingshotGame.HEIGHT.toFloat()

private fun sx(x: Double) = x.toFloat()

private fun sy(y: Double) = H - y.toFloat()

private val grass = Color(0.47f, 0.66f, 0.33f)
private val forkWood = Color(0.45f, 0.29f, 0.16f)
private val bandColor = Color(0.30f, 0.18f, 0.10f)
private val woodFill = Color(0.80f, 0.58f, 0.36f)
private val woodLine = Color(0.52f, 0.34f, 0.18f)
private val ballFill = Color(0.93f, 0.42f, 0.24f)
private val ballLine = Color(0.62f, 0.22f, 0.10f)

private fun DrawScope.drawSlingshot(game: SlingshotGame, dark: Boolean, clock: EmojiSprite) {
    val width = SlingshotGame.WIDTH.toFloat()

    // Cielo.
    val sky = if (dark) {
        listOf(Color(0.06f, 0.10f, 0.20f), Color(0.17f, 0.20f, 0.33f))
    } else {
        listOf(Color(0.62f, 0.83f, 0.98f), Color(0.95f, 0.97f, 1.00f))
    }
    drawRect(Brush.verticalGradient(sky, startY = 0f, endY = H), size = Size(width, H))

    // Suelo.
    val groundTop = sy(SlingshotGame.GROUND_Y)
    drawRect(grass, Offset(0f, groundTop), Size(width, H - groundTop))
    drawRect(Color.Black.copy(alpha = 0.15f), Offset(0f, groundTop), Size(width, 3f))

    // Horquilla: el palo y el brazo de atrás quedan detrás de la bola; el de
    // delante, por encima, para que la bola quede dentro.
    val joint = Offset(sx(SlingshotGame.FORK_JOINT_X), sy(SlingshotGame.FORK_JOINT_Y))
    val backTip = Offset(sx(SlingshotGame.BACK_TIP_X), sy(SlingshotGame.BACK_TIP_Y))
    val frontTip = Offset(sx(SlingshotGame.FRONT_TIP_X), sy(SlingshotGame.FRONT_TIP_Y))
    val forkStroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val backFork = Path().apply {
        moveTo(joint.x, groundTop)
        lineTo(joint.x, joint.y)
        lineTo(backTip.x, backTip.y)
    }
    drawPath(backFork, forkWood, style = forkStroke)

    // Goma: con la bola estirada, las dos tiras la abrazan por detrás (el
    // lado contrario al lanzamiento). En reposo, una tira recta entre puntas.
    val ballPos = game.ballPosition
    val pullLength = hypot(game.pullX, game.pullY)
    val grip = if (game.ballState == SlingshotGame.BallState.PULLING && pullLength > 1 && ballPos != null) {
        Offset(
            sx(ballPos.x + game.pullX / pullLength * SlingshotGame.BALL_RADIUS),
            sy(ballPos.y + game.pullY / pullLength * SlingshotGame.BALL_RADIUS),
        )
    } else {
        null
    }
    if (grip != null) {
        drawLine(bandColor, backTip, grip, strokeWidth = 3.5f, cap = StrokeCap.Round)
    } else {
        drawLine(bandColor, backTip, frontTip, strokeWidth = 3.5f, cap = StrokeCap.Round)
    }

    // Rastros: el del tiro anterior, más tenue.
    val trailColor = if (dark) Color.White else Color(0.2f, 0.2f, 0.2f)
    for (p in game.previousTrail) drawCircle(trailColor.copy(alpha = 0.18f), 1.6f, Offset(sx(p.x), sy(p.y)))
    for (p in game.trail) drawCircle(trailColor.copy(alpha = 0.5f), 1.6f, Offset(sx(p.x), sy(p.y)))

    // Tablones.
    for (block in game.blocks) {
        val center = Offset(sx(block.x), sy(block.y))
        val w = (block.halfWidth * 2).toFloat()
        val h = (block.halfHeight * 2).toFloat()
        rotate(degrees = (-block.angle * 180 / PI).toFloat(), pivot = center) {
            val topLeft = Offset(center.x - w / 2, center.y - h / 2)
            drawRoundRect(woodFill, topLeft, Size(w, h), CornerRadius(2f))
            drawRoundRect(woodLine, topLeft, Size(w, h), CornerRadius(2f), style = Stroke(width = 1f))
        }
    }

    // Despertadores; los derribados crecen y se desvanecen.
    val clockSize = (SlingshotGame.TARGET_SIZE + 6) * 0.8
    for (target in game.targets) {
        val body = target.body
        val t = if (target.popped) (target.popAge / SlingshotGame.POP_DURATION).coerceIn(0.0, 1.0) else 0.0
        if (target.popped && t >= 1.0) continue
        clock.draw(
            this,
            sx(body.x),
            sy(body.y),
            (clockSize * (1 + 0.6 * t)).toFloat(),
            rotationDegrees = (-body.angle * 180 / PI).toFloat(),
            alpha = (1 - t).toFloat(),
        )
    }

    // La bola: crece al cargarse y se apaga al terminar el tiro.
    game.ghost?.let { ghost ->
        val alpha = (1 - game.ghostAge / SlingshotGame.FADE_DURATION).coerceIn(0.0, 1.0).toFloat()
        drawBall(Offset(sx(ghost.x), sy(ghost.y)), 1f, alpha)
    }
    if (ballPos != null) {
        val grow = if (game.ballState == SlingshotGame.BallState.FLYING) {
            1f
        } else {
            (0.2 + 0.8 * min(game.loadAge / SlingshotGame.LOAD_DURATION, 1.0)).toFloat()
        }
        drawBall(Offset(sx(ballPos.x), sy(ballPos.y)), grow, 1f)
    }

    // Tira de delante y brazo de delante, por encima de la bola.
    if (grip != null) drawLine(bandColor, frontTip, grip, strokeWidth = 3.5f, cap = StrokeCap.Round)
    drawLine(forkWood, joint, frontTip, strokeWidth = 6f, cap = StrokeCap.Round)

    // Trayectoria prevista.
    val preview = game.previewPoints()
    preview.forEachIndexed { i, p ->
        val alpha = 1f - i.toFloat() / preview.size * 0.7f
        val center = Offset(sx(p.x), sy(p.y))
        drawCircle(Color.White.copy(alpha = 0.85f * alpha), 2.2f, center)
        drawCircle(Color.Black.copy(alpha = 0.25f * alpha), 2.2f, center, style = Stroke(width = 0.5f))
    }

    // Una bolita por tiro, sobre la franja de hierba; las gastadas quedan huecas.
    val shots = game.config.shots
    val dot = 10f
    val spacing = 4f
    val right = width - 12f
    val cy = H - 9f - dot / 2
    for (i in 0 until shots) {
        val cx = right - (shots - i) * dot - (shots - 1 - i) * spacing + dot / 2
        val center = Offset(cx, cy)
        if (i < game.shotsLeft) drawCircle(Color.White, dot / 2, center)
        drawCircle(Color.White, dot / 2 - 0.75f, center, style = Stroke(width = 1.5f))
    }
}

private fun DrawScope.drawBall(center: Offset, grow: Float, alpha: Float) {
    if (alpha <= 0f) return
    val r = SlingshotGame.BALL_RADIUS.toFloat()
    scale(grow, pivot = center) {
        drawCircle(ballFill.copy(alpha = alpha), r, center)
        drawCircle(ballLine.copy(alpha = alpha), r - 0.75f, center, style = Stroke(width = 1.5f))
        drawCircle(Color.White.copy(alpha = 0.55f * alpha), 3f, Offset(center.x - 3.5f, center.y - 3.5f))
    }
}
