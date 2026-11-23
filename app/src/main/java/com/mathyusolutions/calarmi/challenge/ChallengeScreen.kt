package com.mathyusolutions.calarmi.challenge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mathyusolutions.calarmi.model.WakeChallenge

/**
 * STUB (frente «retos»): la pantalla del reto. En modo alarma, `onSnooze` es
 * null si la alarma no permite posponer; en práctica, `onClose` cierra.
 */
@Composable
fun ChallengeScreen(
    challenge: WakeChallenge,
    title: String,
    isPractice: Boolean,
    onSolved: () -> Unit,
    onSnooze: (() -> Unit)?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title)
            Button(onClick = onSolved) { Text("OK") }
        }
    }
}
