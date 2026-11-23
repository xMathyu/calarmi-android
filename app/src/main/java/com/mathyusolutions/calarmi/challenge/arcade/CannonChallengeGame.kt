package com.mathyusolutions.calarmi.challenge.arcade

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.LocalAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * «Cañón»: se apunta arrastrando por el campo y se dispara manteniendo el
 * botón; la fuerza sube y baja mientras se mantiene. La barra de fuerza marca
 * la del tiro anterior y queda dibujada su trayectoria, para corregir. La
 * lógica vive en [CannonGame].
 */
@Composable
fun CannonChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val game = remember(difficulty) { CannonGame(difficulty) }
    val win = rememberArcadeWin(onSolved)
    val haptics = rememberArcadeHaptics()
    val frame = remember { mutableLongStateOf(0L) }
    var hud by remember(game) { mutableStateOf(CannonHud.of(game)) }

    ArcadeLoop(game) { dt ->
        for (event in game.step(dt)) {
            when (event) {
                CannonGame.Event.HIT -> haptics.rigid()
                CannonGame.Event.MISS -> haptics.soft()
                CannonGame.Event.SOLVED -> win.win()
            }
        }
        hud = CannonHud.of(game)
        frame.longValue++
    }

    val dark = isArcadeDark()
    val accent = LocalAccent.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val clock = remember { EmojiSprite("⏰") }
    val label = remember { CanvasLabel() }
    val windTitle = stringResource(R.string.arcade_cannon_wind, hud.windStrength)

    ArcadeScaffold(
        aspectRatio = (CannonGame.WIDTH / CannonGame.HEIGHT).toFloat(),
        modifier = modifier,
        header = {
            ArcadeProgressHeader(
                label = stringResource(R.string.arcade_cannon_progress, hud.hits, game.required),
                value = hud.hits,
                total = game.required,
            )
        },
        arena = {
            ArcadeArena(
                description = stringResource(R.string.challenge_cannon_desc),
                modifier = Modifier.arcadePress(
                    game,
                    CannonGame.WIDTH,
                    onDown = { x, y -> game.aim(x, y) },
                    onMove = { x, y -> game.aim(x, y) },
                ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame.longValue
                    logical(CannonGame.WIDTH) {
                        drawCannonScene(game, dark, accent, secondary, clock, label, windTitle)
                    }
                }
                ArcadeBanner(
                    visible = !hud.hasAimed && hud.phase == CannonGame.Phase.AIMING,
                    text = stringResource(R.string.arcade_cannon_aim),
                    alignment = Alignment.BottomCenter,
                )
                ArcadeSuccess(win.won)
            }
        },
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerBar(game, frame)
                Spacer(Modifier.height(12.dp))
                FireButton(game, hud.phase, accent)
            }
        },
    )
}

private data class CannonHud(val hits: Int, val phase: CannonGame.Phase, val hasAimed: Boolean, val windStrength: Int) {
    companion object {
        fun of(game: CannonGame) = CannonHud(game.hits, game.phase, game.hasAimed, game.windStrength)
    }
}

/** La fuerza actual y, con una raya, la del tiro anterior. Se pinta en cada fotograma sin recomponer. */
@Composable
private fun PowerBar(game: CannonGame, frame: MutableLongState) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val marker = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val title = stringResource(R.string.arcade_cannon_power)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics {
                contentDescription = title
                progressBarRangeInfo = ProgressBarRangeInfo(game.power.toFloat(), 0f..1f)
            },
    ) {
        frame.longValue
        val barHeight = 12.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        val filled = (size.width * game.power.toFloat()).coerceIn(0f, size.width)
        if (filled > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color.Yellow, Color(0xFFFF9500), Color.Red), startX = 0f, endX = size.width),
                Offset(0f, top),
                Size(filled, barHeight),
                radius,
            )
        }
        game.lastPower?.let { last ->
            val markerWidth = 3.dp.toPx()
            drawRoundRect(
                marker,
                Offset((size.width * last.toFloat() - markerWidth / 2), 0f),
                Size(markerWidth, size.height),
                CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun FireButton(game: CannonGame, phase: CannonGame.Phase, accent: Color) {
    val charging = phase == CannonGame.Phase.CHARGING
    val ready = phase == CannonGame.Phase.AIMING || charging
    val scale by animateFloatAsState(if (charging) 0.97f else 1f, label = "fireScale")
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(scale)
            .background(accent.copy(alpha = if (ready) 1f else 0.4f), CircleShape)
            .arcadePress(
                game,
                1.0,
                onDown = { _, _ -> game.beginCharge() },
                onUp = { game.releaseCharge() },
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (charging) R.string.arcade_cannon_release else R.string.arcade_cannon_hold),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

// MARK: - Dibujo

private fun DrawScope.drawCannonScene(
    game: CannonGame,
    dark: Boolean,
    accent: Color,
    secondary: Color,
    clock: EmojiSprite,
    label: CanvasLabel,
    windTitle: String,
) {
    val width = CannonGame.WIDTH.toFloat()
    val height = CannonGame.HEIGHT.toFloat()

    // Cielo.
    val sky = if (dark) {
        listOf(Color(0.06f, 0.10f, 0.20f), Color(0.18f, 0.17f, 0.30f))
    } else {
        listOf(Color(0.70f, 0.86f, 1.00f), Color(1.00f, 0.95f, 0.86f))
    }
    drawRect(Brush.verticalGradient(sky, startY = 0f, endY = height), size = Size(width, height))

    drawWind(game, accent, secondary, label, windTitle)

    // Trayectorias: la del tiro anterior, tenue; la del actual, marcada.
    val dotColor = if (dark) Color.White else Color.Black
    for ((trail, alpha) in listOf(game.previousTrail to 0.18f, game.trail to 0.45f)) {
        val color = dotColor.copy(alpha = alpha)
        for (p in trail) drawCircle(color, 1.5f, Offset(p.x.toFloat(), p.y.toFloat()))
    }

    // Terreno: tierra con una franja de hierba encima.
    val ground = Path()
    val surface = Path()
    ground.moveTo(0f, height)
    game.terrain.forEachIndexed { i, top ->
        val x = (i * CannonGame.SAMPLE_STEP).toFloat()
        val y = top.toFloat()
        ground.lineTo(x, y)
        if (i == 0) surface.moveTo(x, y) else surface.lineTo(x, y)
    }
    ground.lineTo(width, height)
    ground.close()
    val earth = if (dark) {
        listOf(Color(0.30f, 0.22f, 0.16f), Color(0.18f, 0.13f, 0.10f))
    } else {
        listOf(Color(0.66f, 0.50f, 0.34f), Color(0.48f, 0.35f, 0.24f))
    }
    drawPath(ground, Brush.verticalGradient(earth, startY = 120f, endY = height))
    drawPath(
        surface,
        if (dark) Color(0.30f, 0.55f, 0.30f) else Color(0.38f, 0.68f, 0.32f),
        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // El despertador.
    val target = game.target
    clock.draw(this, target.x.toFloat(), target.y.toFloat(), (target.radius * 2.3).toFloat())

    drawCannon(game, accent, secondary, label)

    // El proyectil; si sale por arriba, una marca en el borde dice dónde va.
    game.shell?.let { shell ->
        val sx = shell.x.toFloat()
        if (shell.y < 0) {
            val marker = Path().apply {
                moveTo(sx, 2f)
                lineTo(sx - 5, 10f)
                lineTo(sx + 5, 10f)
                close()
            }
            drawPath(marker, dotColor.copy(alpha = 0.6f))
        } else {
            drawCircle(
                if (dark) Color.White else Color(0.15f, 0.15f, 0.15f),
                CannonGame.SHELL_RADIUS.toFloat(),
                Offset(sx, shell.y.toFloat()),
            )
        }
    }

    // La explosión: crece y se apaga.
    game.blast?.let { blast ->
        val t = min(blast.age / CannonGame.BLAST_DURATION, 1.0).toFloat()
        val r = (CannonGame.BLAST_RADIUS * (0.5 + t * 0.8)).toFloat()
        val center = Offset(blast.x.toFloat(), blast.y.toFloat())
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Yellow.copy(alpha = 1 - t), Color(0xFFFF9500).copy(alpha = 0.8f * (1 - t)), Color.Red.copy(alpha = 0f)),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }
}

private val barrelColor = Color(0.28f, 0.28f, 0.28f)
private val muzzleColor = Color(0.18f, 0.18f, 0.18f)

private fun DrawScope.drawCannon(game: CannonGame, accent: Color, secondary: Color, label: CanvasLabel) {
    val pivot = Offset(CannonGame.CANNON_X.toFloat(), game.cannonY.toFloat())
    val radians = game.angle * PI / 180
    val barrel = CannonGame.BARREL_LENGTH.toFloat()

    // Guía de puntería mientras se apunta.
    if (game.phase == CannonGame.Phase.AIMING || game.phase == CannonGame.Phase.CHARGING) {
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        drawLine(
            accent.copy(alpha = 0.7f),
            Offset(pivot.x + c * (barrel + 4), pivot.y - s * (barrel + 4)),
            Offset(pivot.x + c * (barrel + 46), pivot.y - s * (barrel + 46)),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)),
        )
    }

    // Cañón: tubo girado sobre una base redonda.
    rotate(degrees = -game.angle.toFloat(), pivot = pivot) {
        drawRoundRect(barrelColor, Offset(pivot.x, pivot.y - 4.5f), Size(barrel, 9f), CornerRadius(3f))
        drawRoundRect(muzzleColor, Offset(pivot.x + barrel - 5, pivot.y - 5.5f), Size(5f, 11f), CornerRadius(2f))
    }
    drawCircle(accent, 10f, pivot)
    drawCircle(Color.White.copy(alpha = 0.5f), 4f, pivot)

    // Ángulo actual.
    label.draw(this, "${game.angle.roundToInt()}°", pivot.x + 4, pivot.y - 44, 12f, secondary)
}

/** «Viento» con una flecha cuyo largo depende de la fuerza. */
private fun DrawScope.drawWind(game: CannonGame, accent: Color, secondary: Color, label: CanvasLabel, title: String) {
    val cx = (CannonGame.WIDTH / 2).toFloat()
    val cy = 18f
    label.draw(this, title, cx, cy, 12f, secondary)
    val strength = game.windStrength
    if (strength <= 0) return
    val direction = if (game.wind > 0) 1f else -1f
    val length = 14f + strength * 7f
    val y = cy + 16
    val tail = Offset(cx - direction * length / 2, y)
    val head = Offset(cx + direction * length / 2, y)
    val arrow = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(head.x, head.y)
        moveTo(head.x - direction * 6, y - 5)
        lineTo(head.x, head.y)
        lineTo(head.x - direction * 6, y + 5)
    }
    drawPath(arrow, accent, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
