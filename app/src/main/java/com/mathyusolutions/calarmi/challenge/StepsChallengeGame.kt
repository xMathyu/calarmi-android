package com.mathyusolutions.calarmi.challenge

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay

/**
 * Dar unos pasos de verdad: se cuentan los pasos desde que se abre el reto y
 * hay que llegar a la cuota de la dificultad.
 *
 * Cuenta el detector de pasos del sistema, que necesita el permiso de
 * actividad física (Android 10+). Si el teléfono no tiene detector, o no hay
 * permiso, los pasos salen del acelerómetro (`StepPeakDetector`) y una línea lo
 * explica. Sin ningún sensor de movimiento el reto pasa a ser «Sacudir», que a
 * su vez ofrece «Continuar».
 */
@Composable
fun StepsChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val stepSensor = rememberSensor(Sensor.TYPE_STEP_DETECTOR)
    val accelerometer = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    if (stepSensor == null && accelerometer == null) {
        ShakeChallengeGame(difficulty, onSolved, modifier)
        return
    }

    val haptics = rememberChallengeHaptics()
    val required = StepsRules.required(difficulty)
    var permitted by remember { mutableStateOf(hasActivityPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }
    // El detector dio permiso pero no cuenta nada mientras el acelerómetro sí
    // ve pasos (pasa en algunos teléfonos): se deja de confiar en él.
    var detectorSilent by remember { mutableStateOf(false) }
    val useDetector = stepSensor != null && permitted && !detectorSilent

    var steps by remember { mutableIntStateOf(0) }
    var detectorSteps by remember { mutableIntStateOf(0) }
    val peaks = remember { StepPeakDetector() }
    var done by remember { mutableStateOf(false) }

    fun count() {
        if (done) return
        steps += 1
        if (steps >= required) {
            done = true
            onSolved()
        } else {
            // Cada paso se nota: los pasos están contando.
            haptics.soft()
        }
    }

    LaunchedEffect(Unit) {
        // El permiso se pide al elegir el reto en el editor; aquí solo si
        // faltaba y el teléfono está desbloqueado. Sobre la pantalla de bloqueo
        // el aviso del sistema no siempre aparece y mientras tanto se cuenta
        // con el acelerómetro.
        if (stepSensor != null && !permitted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isKeyguardLocked(context)) {
            runCatching { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        }
    }

    SensorListenerEffect(if (!done && stepSensor != null && permitted) stepSensor else null, SensorManager.SENSOR_DELAY_FASTEST) {
        detectorSteps += 1
        if (useDetector) count()
    }
    // El acelerómetro escucha siempre: cuenta cuando no hay detector y, cuando
    // lo hay, sirve para vigilar que el detector funcione.
    SensorListenerEffect(if (!done) accelerometer else null) { event ->
        val g = SensorManager.GRAVITY_EARTH
        val magnitude = ShakeDetector.magnitude(event.values[0] / g, event.values[1] / g, event.values[2] / g)
        if (peaks.process(magnitude, event.timestamp / 1e9) && !useDetector) count()
    }
    LaunchedEffect(useDetector) {
        if (!useDetector || accelerometer == null) return@LaunchedEffect
        val detectorStart = detectorSteps
        val peaksStart = peaks.steps
        delay(DETECTOR_WATCH_MILLIS)
        val walked = peaks.steps - peaksStart
        if (detectorSteps == detectorStart && walked >= DETECTOR_WATCH_STEPS) {
            detectorSilent = true
            // Los pasos de la espera no se pierden: los vio el acelerómetro.
            steps = maxOf(steps, walked - 1)
            count()
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.lg)) {
        GameCaption(stringResource(R.string.challenges_steps_title))
        CounterRing(
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            count = steps,
            required = required,
            unit = stringResource(R.string.challenges_steps_unit),
            wiggle = false,
        )
        val note = when {
            useDetector -> R.string.challenges_steps_note
            stepSensor == null -> R.string.challenges_steps_no_detector
            !permitted -> R.string.challenges_steps_no_permission
            else -> null
        }
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 18.dp),
            )
        }
    }
}

private const val DETECTOR_WATCH_MILLIS = 20_000L
private const val DETECTOR_WATCH_STEPS = 10

/** El permiso de actividad física solo existe como permiso en tiempo de ejecución desde Android 10. */
internal fun hasActivityPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

internal fun isKeyguardLocked(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
