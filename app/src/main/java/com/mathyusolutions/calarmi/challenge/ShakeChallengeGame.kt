package com.mathyusolutions.calarmi.challenge

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sacudir el teléfono con ganas. El acelerómetro cuenta cada sacudida (ver
 * `ShakeDetector`) y hay que llegar a la cuota de la dificultad. Sin
 * acelerómetro no hay nada que contar: se avisa y un botón da el reto por
 * resuelto.
 */
@Composable
fun ShakeChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val sensor = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    var done by remember { mutableStateOf(false) }
    fun finish() {
        if (done) return
        done = true
        onSolved()
    }
    if (sensor == null) {
        MotionUnavailable(onContinue = ::finish, modifier = modifier)
        return
    }

    val haptics = rememberChallengeHaptics()
    val required = ShakeRules.required(difficulty)
    val detector = remember { ShakeDetector() }
    var shakes by remember { mutableIntStateOf(0) }

    SensorListenerEffect(if (done) null else sensor) { event ->
        val g = SensorManager.GRAVITY_EARTH
        val magnitude = ShakeDetector.magnitude(event.values[0] / g, event.values[1] / g, event.values[2] / g)
        if (detector.process(magnitude, event.timestamp / 1e9)) {
            shakes = detector.shakes
            if (shakes >= required) finish() else haptics.soft()
        }
    }

    val progress = (shakes.toFloat() / required).coerceAtMost(1f)
    // 0 al empezar, 1 a mitad, 2 cerca del final: cambia la frase de ánimo.
    val stage = if (progress >= 0.8f) 2 else if (progress >= 0.5f) 1 else 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_shake_title))
        CounterRing(
            icon = Icons.Filled.Vibration,
            count = shakes,
            required = required,
            unit = stringResource(R.string.challenges_shake_unit),
            wiggle = true,
        )
        Text(
            stringResource(
                when (stage) {
                    2 -> R.string.challenges_shake_almost
                    1 -> R.string.challenges_shake_keep_going
                    else -> R.string.challenges_shake_go
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (stage == 2) successGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 24.dp),
        )
    }
}

/**
 * El contador dentro de su anillo de progreso (Sacudir, Caminar). Ocupa hasta
 * 180 dp, menos si el teléfono es bajito.
 */
@Composable
internal fun ColumnScope.CounterRing(icon: ImageVector, count: Int, required: Int, unit: String, wiggle: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.challenges_count_of, count, required)
    // Un meneo (Sacudir) o un saltito (Caminar) con cada unidad que cuenta.
    val kick = remember { Animatable(0f) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        kick.snapTo(0f)
        kick.animateTo(1f, tween(320))
    }
    Box(
        Modifier
            .weight(1f, fill = false)
            .widthIn(max = 180.dp)
            .heightIn(max = 180.dp)
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = "$label $unit" },
        contentAlignment = Alignment.Center,
    ) {
        ProgressRing(count.toFloat() / required, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        val k = kick.value
                        if (wiggle) {
                            rotationZ = 12f * sin(k * PI * 4).toFloat() * (1 - k)
                        } else {
                            translationY = -6.dp.toPx() * sin(k * PI).toFloat()
                        }
                    },
            )
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
            )
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Sin sensores de movimiento: se avisa y «Continuar» da el reto por resuelto. */
@Composable
internal fun MotionUnavailable(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = DS.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.lg),
    ) {
        Icon(Icons.Filled.MobileOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(
            stringResource(R.string.challenges_no_motion_sensors),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onContinue, modifier = Modifier.widthIn(max = GameMaxWidth).fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.challenges_continue))
        }
    }
}

// MARK: - Sensores

@Composable
internal fun rememberSensor(type: Int): Sensor? {
    val context = LocalContext.current
    return remember(type) { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) }
}

/**
 * Escucha un sensor solo mientras la pantalla está delante: se da de baja al
 * pausarse (pantalla apagada, otra app encima) y al salir de la composición.
 * Con `sensor == null` no escucha nada. Los eventos llegan en el hilo principal.
 */
@Composable
internal fun SensorListenerEffect(sensor: Sensor?, delay: Int = SensorManager.SENSOR_DELAY_GAME, onEvent: (SensorEvent) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onEvent)
    LifecycleResumeEffect(sensor, delay) {
        val manager = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = latest(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager?.registerListener(listener, sensor, delay)
        onPauseOrDispose { manager?.unregisterListener(listener) }
    }
}
