package com.mathyusolutions.calarmi.calendar

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// STUB (frente «calendario»).

/** La pestaña «Calendario»: vista de mes con alarmas y eventos. */
@Composable
fun CalendarScreen() {
    Text("Calendar")
}

/** Ajustes › Calendarios: cuáles mirar. */
@Composable
fun CalendarSelectionScreen(onBack: () -> Unit) {
    Text("Calendars")
}
