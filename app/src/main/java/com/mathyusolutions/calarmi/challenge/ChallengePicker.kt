package com.mathyusolutions.calarmi.challenge

import androidx.compose.runtime.Composable
import com.mathyusolutions.calarmi.model.WakeChallenge

/**
 * STUB (frente «retos»): el selector de reto del editor de alarma, a pantalla
 * completa. `onSelect(null)` = sin reto. Los Arcade piden Plus
 * (`LocalAppNavigator.current.openPaywall(PlusFeature.ARCADE_CHALLENGES)`).
 */
@Composable
fun ChallengePickerSheet(
    selection: WakeChallenge?,
    alarmTitle: String,
    onDismiss: () -> Unit,
    onSelect: (WakeChallenge?) -> Unit,
) {
    onDismiss()
}
