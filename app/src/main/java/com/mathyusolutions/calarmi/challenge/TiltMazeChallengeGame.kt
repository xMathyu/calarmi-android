package com.mathyusolutions.calarmi.challenge

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlin.math.min

/**
 * Inclinar el teléfono para llevar una bola hasta la bandera de salida. La
 * gravedad que mide el sensor empuja la bola por un laberinto de casillas y las
 * paredes la frenan; con rozamiento, para que se pare sola al dejar el teléfono
 * plano (ver `TiltPhysics`). Sin sensores la bola se arrastra con el dedo,
 * también bloqueada por las paredes.
 */
@Composable
fun TiltMazeChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberChallengeHaptics()
    val view = LocalView.current
    val maze = remember(difficulty) { TiltMazes.random(difficulty) }
    var ball by remember(maze) { mutableStateOf(TiltMazeBall.at(maze.start.first, maze.start.second)) }
    var done by remember { mutableStateOf(false) }
    // La gravedad directa si el teléfono la ofrece; si no, el acelerómetro filtrado.
    val gravitySensor = rememberSensor(Sensor.TYPE_GRAVITY)
    val accelerometer = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    val sensor = gravitySensor ?: accelerometer
    // Gravedad en g con la convención de iOS (x a la derecha, y hacia arriba).
    val gravity = remember { DoubleArray(2) }
    val lastBump = remember { longArrayOf(0L) }

    fun finish() {
        if (done) return
        done = true
        onSolved()
    }

    SensorListenerEffect(if (done) null else sensor) { event ->
        // Los ejes del sensor son los del teléfono en vertical: si la pantalla
        // está girada se rotan para que «derecha» sea la derecha de la pantalla.
        val x = event.values[0]
        val y = event.values[1]
        val (sx, sy) = when (view.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> -y to x
            Surface.ROTATION_180 -> -x to -y
            Surface.ROTATION_270 -> y to -x
            else -> x to y
        }
        // Android mide la reacción a la gravedad (en reposo y vertical, y = +9,8);
        // iOS, la gravedad misma (y = −1). Se cambia el signo y se pasa a g.
        val gx = -sx / SensorManager.GRAVITY_EARTH.toDouble()
        val gy = -sy / SensorManager.GRAVITY_EARTH.toDouble()
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            gravity[0] = gx
            gravity[1] = gy
        } else {
            // Filtro paso bajo: del acelerómetro solo interesa la parte lenta.
            gravity[0] += LOW_PASS * (gx - gravity[0])
            gravity[1] += LOW_PASS * (gy - gravity[1])
        }
    }

    // Un toque al golpear una pared con ganas, como mucho uno cada 150 ms.
    fun bump(speed: Double) {
        if (speed <= 2.5) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBump[0] <= 150) return
        lastBump[0] = now
        haptics.light()
    }

    // Simulación a la frecuencia de la pantalla mientras el reto sigue abierto.
    // Solo hace falta con sensores: sin ellos la bola se mueve con el dedo.
    LaunchedEffect(sensor, maze) {
        if (sensor == null) return@LaunchedEffect
        var last = 0L
        while (!done) {
            withFrameNanos { now ->
                // Si la pantalla estuvo en pausa, no se integra el tiempo perdido.
                val dt = if (last == 0L) 0.0 else min((now - last) / 1e9, 1.0 / 30.0)
                last = now
                if (dt > 0) {
                    val result = TiltPhysics.step(ball, gravity[0], gravity[1], dt, maze)
                    bump(result.bumpSpeed)
                    if (result.ball != ball) ball = result.ball
                    if (maze.isExit(result.ball)) finish()
                }
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.md)) {
        Text(
            stringResource(if (sensor != null) R.string.challenges_tilt_hint else R.string.challenges_tilt_drag_hint),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 24.dp),
        )

        val shape = RoundedCornerShape(DS.radiusLg)
        val wallColor = MaterialTheme.colorScheme.outline
        val accent = MaterialTheme.colorScheme.primary
        val flag = rememberVectorPainter(Icons.Filled.Flag)
        val dragModifier = if (sensor != null) {
            Modifier
        } else {
            Modifier.pointerInput(maze) {
                fun dragTo(position: Offset) {
                    if (done || size.width == 0) return
                    val cell = size.width.toDouble() / maze.size
                    val moved = TiltPhysics.drag(ball, position.x / cell, position.y / cell, maze)
                    if (moved != ball) ball = moved
                    if (maze.isExit(moved)) finish()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragTo(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dragTo(change.position)
                        change.consume()
                    }
                }
            }
        }
        Canvas(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = GameMaxWidth)
                .aspectRatio(1f)
                .clip(shape)
                .background(tileColor)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
                .then(dragModifier),
        ) {
            val n = maze.size
            val cell = size.width / n

            // Paredes: un poco más grandes que la casilla para que no queden juntas.
            for (row in 0 until n) {
                for (col in 0 until n) {
                    if (maze.isWall(row, col)) {
                        drawRect(wallColor, Offset(col * cell, row * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }

            // Salida: casilla verde con bandera.
            val inset = cell * 0.08f
            val exitTopLeft = Offset(maze.exit.second * cell + inset, maze.exit.first * cell + inset)
            val exitSize = Size(cell - inset * 2, cell - inset * 2)
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color(0xFF34C759), Color(0xFF00C7BE)),
                    start = exitTopLeft,
                    end = exitTopLeft + Offset(exitSize.width, exitSize.height),
                ),
                topLeft = exitTopLeft,
                size = exitSize,
                cornerRadius = CornerRadius(cell * 0.2f),
            )
            val flagSize = cell * 0.5f
            translate(maze.exit.second * cell + (cell - flagSize) / 2, maze.exit.first * cell + (cell - flagSize) / 2) {
                with(flag) { draw(Size(flagSize, flagSize), colorFilter = ColorFilter.tint(Color.White)) }
            }

            // Bola con sombra suave y un brillo descentrado que le da volumen.
            val r = cell * 0.34f
            val center = Offset(ball.x.toFloat() * cell, ball.y.toFloat() * cell)
            drawCircle(Color.Black.copy(alpha = 0.22f), radius = r * 1.08f, center = center + Offset(0f, r * 0.3f))
            drawCircle(accent, radius = r, center = center)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = center + Offset(-r * 0.35f, -r * 0.4f),
                    radius = r * 1.1f,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

private const val LOW_PASS = 0.2
