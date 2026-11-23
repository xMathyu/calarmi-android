package com.mathyusolutions.calarmi.challenge

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS

/**
 * Dar unos pasos de verdad: se cuentan los pasos desde que se abre el reto y
 * hay que llegar a la cuota de la dificultad.
 *
 * Los pasos salen del acelerómetro (`StepPeakDetector`), no del detector de
 * pasos del sistema: el detector exige el permiso de actividad física, que
 * Google Play reserva a las apps de salud, y el acelerómetro no pide nada y
 * cuenta al instante. Sin acelerómetro el reto pasa a ser «Sacudir», que a su
 * vez ofrece «Continuar».
 */
@Composable
fun StepsChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val accelerometer = rememberSensor(Sensor.TYPE_ACCELEROMETER)
    if (accelerometer == null) {
        ShakeChallengeGame(difficulty, onSolved, modifier)
        return
    }

    val haptics = rememberChallengeHaptics()
    val required = StepsRules.required(difficulty)
    var steps by remember { mutableIntStateOf(0) }
    val peaks = remember { StepPeakDetector() }
    var done by remember { mutableStateOf(false) }

    SensorListenerEffect(if (!done) accelerometer else null) { event ->
        val g = SensorManager.GRAVITY_EARTH
        val magnitude = ShakeDetector.magnitude(event.values[0] / g, event.values[1] / g, event.values[2] / g)
        if (!peaks.process(magnitude, event.timestamp / 1e9) || done) return@SensorListenerEffect
        steps += 1
        if (steps >= required) {
            done = true
            onSolved()
        } else {
            // Cada paso se nota: los pasos están contando.
            haptics.soft()
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
    }
}
