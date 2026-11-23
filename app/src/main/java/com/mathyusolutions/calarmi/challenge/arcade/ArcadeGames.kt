package com.mathyusolutions.calarmi.challenge.arcade

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mathyusolutions.calarmi.model.WakeChallenge

// STUB (frente «arcade»): cada juego llama a `onSolved` una sola vez al ganar.

@Composable
fun FlapChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onSolved, modifier = modifier) { Text("Flap") }
}

@Composable
fun HurdlesChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onSolved, modifier = modifier) { Text("Hurdles") }
}

@Composable
fun CannonChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onSolved, modifier = modifier) { Text("Cannon") }
}

@Composable
fun SlingshotChallengeGame(difficulty: WakeChallenge.Difficulty, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onSolved, modifier = modifier) { Text("Sling") }
}
