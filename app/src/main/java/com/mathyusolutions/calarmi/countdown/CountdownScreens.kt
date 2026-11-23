package com.mathyusolutions.calarmi.countdown

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mathyusolutions.calarmi.model.CountdownPrefill

// STUB (frente «cuentas regresivas»).

/** La pestaña «Cuenta regresiva». */
@Composable
fun CountdownListScreen() {
    Text("Countdowns")
}

@Composable
fun CountdownDetailScreen(id: String, onBack: () -> Unit) {
    Text(id)
}

/** Nueva (`id` nulo, opcionalmente con `prefill`) o edición. `onDone` vuelve atrás. */
@Composable
fun CountdownEditorScreen(id: String?, prefill: CountdownPrefill?, onDone: () -> Unit) {
    Text("Editor")
}
