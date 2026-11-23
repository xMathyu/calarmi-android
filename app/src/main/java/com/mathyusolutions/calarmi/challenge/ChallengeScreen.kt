package com.mathyusolutions.calarmi.challenge

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.challenge.arcade.CannonChallengeGame
import com.mathyusolutions.calarmi.challenge.arcade.FlapChallengeGame
import com.mathyusolutions.calarmi.challenge.arcade.HurdlesChallengeGame
import com.mathyusolutions.calarmi.challenge.arcade.SlingshotChallengeGame
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay

/**
 * La pantalla que apaga una alarma con reto. Mientras está abierta la alarma
 * sigue en marcha (`RingingActivity` solo baja el tono): resolver el juego, o
 * posponer si la alarma lo permite, es lo único que la calla. En modo de
 * prueba, desde el editor, no hay alarma detrás y `onClose` la cierra.
 *
 * `onSolved` se llama una sola vez, tras un instante de «Reto resuelto».
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
    val settings by CalarmiApp.container(context).settings.state.collectAsStateWithLifecycle()
    val haptics = rememberChallengeHaptics()
    var solved by remember(challenge) { mutableStateOf(false) }
    val latestOnSolved by rememberUpdatedState(onSolved)
    // Los juegos pueden avisar más de una vez (dos dedos a la vez, una animación
    // que termina tarde): solo cuenta la primera.
    val handleSolved: () -> Unit = remember(challenge) { { if (!solved) solved = true } }

    // Girar el teléfono recrea la actividad y el reto empezaría de cero (con la
    // alarma, incluso se volvería a la pantalla de «Resolver reto»). Sacudir o
    // inclinar el teléfono lo provocarían sin querer, así que mientras el reto
    // está en pantalla se queda en la orientación actual.
    val activity = context.findActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            if (activity != null && previous != null) activity.requestedOrientation = previous
        }
    }

    LaunchedEffect(solved) {
        if (!solved) return@LaunchedEffect
        haptics.success()
        // Un instante de «resuelto» antes de que la pantalla desaparezca.
        delay(SOLVED_PAUSE_MILLIS)
        latestOnSolved()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .safeDrawingPadding(),
    ) {
        // Con el teclado fuera (Escribir) o en un teléfono bajito la cabecera se
        // encoge para que el juego quepa sin desplazarse.
        val imeVisible = WindowInsets.isImeVisible
        val compact = maxHeight < 640.dp || imeVisible
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = DS.lg)
                .padding(bottom = DS.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(stringResource(challenge.kind.titleRes), onClose)
            Header(
                title = title,
                isPractice = isPractice,
                ringing = !isPractice && !solved,
                compact = compact,
                showSubtitle = !imeVisible,
            )
            Spacer(Modifier.height(if (compact) DS.md else DS.xl))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = solved,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.7f))
                            .togetherWith(fadeOut())
                    },
                    label = "challenge",
                ) { isSolved ->
                    if (isSolved) {
                        SolvedBanner()
                    } else {
                        ChallengeGame(challenge, isPractice, handleSolved, Modifier.fillMaxSize())
                    }
                }
            }
            if (onSnooze != null && !solved) {
                Spacer(Modifier.height(DS.md))
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier.widthIn(max = GameMaxWidth).fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.Snooze, contentDescription = null)
                    Spacer(Modifier.width(DS.sm))
                    Text(stringResource(R.string.ring_snooze_minutes, settings.snooze.seconds / 60))
                }
            }
        }
    }
}

private const val SOLVED_PAUSE_MILLIS = 700L

/** El juego de cada tipo de reto. */
@Composable
private fun ChallengeGame(challenge: WakeChallenge, isPractice: Boolean, onSolved: () -> Unit, modifier: Modifier) {
    val difficulty = challenge.difficulty
    when (challenge.kind) {
        WakeChallenge.Kind.MATH -> MathChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.MEMORY -> MemoryChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.ORDER -> OrderChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.PAIRS -> PairsChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.COLORS -> ColorsChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.ODD -> OddOneOutChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.TYPING -> TypingChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.FLAP -> FlapChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.HURDLES -> HurdlesChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.CANNON -> CannonChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.SLING -> SlingshotChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.SHAKE -> ShakeChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.STEPS -> StepsChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.TILT -> TiltMazeChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.REFLEX -> ReflexChallengeGame(difficulty, onSolved, modifier)
        WakeChallenge.Kind.QR -> QRChallengeGame(
            expectedCode = challenge.code.orEmpty(),
            difficulty = difficulty,
            onSolved = onSolved,
            returnToHost = !isPractice,
            modifier = modifier,
        )
    }
}

/** Barra superior: «Cerrar» (solo en práctica) y el nombre del reto. */
@Composable
private fun TopBar(kindTitle: String, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        }
        Text(
            kindTitle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun Header(title: String, isPractice: Boolean, ringing: Boolean, compact: Boolean, showSubtitle: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else DS.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) DS.xs else DS.sm),
    ) {
        if (!compact) RingingBell(ringing)
        Text(
            title.ifBlank { stringResource(R.string.challenges_alarm_fallback) },
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSubtitle) {
            Text(
                stringResource(if (isPractice) R.string.challenges_practice_note else R.string.challenges_solve_to_stop),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** La campana de la cabecera: se balancea mientras la alarma de verdad suena. */
@Composable
private fun RingingBell(ringing: Boolean) {
    val transition = rememberInfiniteTransition(label = "bell")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                0f at 0
                14f at 100
                -14f at 250
                10f at 400
                -8f at 550
                0f at 700
            },
        ),
        label = "bellAngle",
    )
    Icon(
        Icons.Filled.NotificationsActive,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { rotationZ = if (ringing) angle else 0f },
    )
}

@Composable
private fun SolvedBanner() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = DS.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.md),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successGreen, modifier = Modifier.size(64.dp))
        Text(
            stringResource(R.string.challenges_solved),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
