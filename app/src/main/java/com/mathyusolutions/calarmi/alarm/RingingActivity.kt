package com.mathyusolutions.calarmi.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.challenge.ChallengeScreen
import com.mathyusolutions.calarmi.model.AppearanceMode
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.theme.CalarmiTheme
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.util.Date
import kotlinx.coroutines.delay

/**
 * La pantalla que aparece cuando suena una alarma, también sobre la pantalla
 * bloqueada. Con reto, «Detener» se convierte en «Resolver reto» y la alarma no
 * calla hasta resolverlo (o posponer, si la alarma lo permite).
 */
class RingingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = CalarmiApp.container(this)
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val queue by RingingState.queue.collectAsStateWithLifecycle()
            val current = queue.firstOrNull()
            LaunchedEffect(current == null) {
                // Sin nada sonando (ya se atendió desde la notificación), la pantalla se va.
                if (current == null) {
                    delay(300)
                    if (RingingState.current == null) finish()
                }
            }
            // La pantalla de alarma siempre en oscuro, como el Reloj.
            CalarmiTheme(settings.copy(appearance = AppearanceMode.DARK)) {
                if (current != null) RingingContent(current, settings.snooze.seconds / 60)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RingingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun RingingContent(item: RingItem, snoozeMinutes: Int) {
    val context = LocalContext.current
    val tint = parseHex(item.colorHex) ?: Color(0xFFFF6633)
    var solving by remember(item.key) { mutableStateOf(false) }
    // Atrás no apaga nada: se sale del reto, pero la alarma sigue.
    BackHandler { if (solving) solving = false }

    if (solving && item.challenge != null) {
        DisposableEffect(item.key) {
            RingService.send(context, RingService.ACTION_MUTE)
            onDispose { RingService.send(context, RingService.ACTION_UNMUTE) }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            ChallengeScreen(
                challenge = item.challenge,
                title = item.title,
                isPractice = false,
                onSolved = { RingService.send(context, RingService.ACTION_SOLVED, item.key) },
                onSnooze = if (item.challenge.allowsSnooze) ({ RingService.send(context, RingService.ACTION_SNOOZE, item.key) }) else null,
                onClose = null,
            )
        }
        return
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), Color.Black, Color.Black))),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(DS.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DS.xxxl))
            Text(
                DateFormat.getTimeFormat(context).format(Date(now)),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            ReminderIcon(
                iconKind = ReminderIconKind.fromRaw(item.iconKindRaw),
                iconValue = item.symbol,
                tint = tint,
                photoPath = item.photoPath,
                fallbackSymbol = if (item.isEvent) "calendar" else "alarm.fill",
                size = 120.dp,
                modifier = Modifier.scale(scale),
            )
            Spacer(Modifier.height(DS.xl))
            Text(
                item.title.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DS.sm))
            Text(AlarmNotifications.subtitle(context, item), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.weight(1f))

            val big = Modifier.fillMaxWidth().height(60.dp)
            val challenge = item.challenge
            if (challenge != null) {
                Button(onClick = { solving = true }, modifier = big, shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = tint)) {
                    Icon(Icons.Filled.Extension, null)
                    Spacer(Modifier.width(DS.sm))
                    Text(stringResource(R.string.ring_solve_challenge), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    stringResource(challenge.kind.titleRes) + " · " + stringResource(challenge.difficulty.titleRes),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = DS.sm),
                )
            } else {
                // Evento con enlace o dirección: «Unirse» / «Ir» en lugar de posponer.
                when {
                    item.meetingUrl != null -> FilledTonalButton(onClick = {
                        RingService.send(context, RingService.ACTION_STOP, item.key)
                        openUri(context, Uri.parse(item.meetingUrl))
                    }, modifier = big, shape = RoundedCornerShape(30.dp)) {
                        Icon(Icons.Filled.Videocam, null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(R.string.ring_join), fontSize = 18.sp)
                    }
                    item.location != null -> FilledTonalButton(onClick = {
                        RingService.send(context, RingService.ACTION_STOP, item.key)
                        openUri(context, Uri.parse("geo:0,0?q=" + Uri.encode(item.location)))
                    }, modifier = big, shape = RoundedCornerShape(30.dp)) {
                        Icon(Icons.Filled.Directions, null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(R.string.ring_go), fontSize = 18.sp)
                    }
                    else -> FilledTonalButton(onClick = { RingService.send(context, RingService.ACTION_SNOOZE, item.key) }, modifier = big, shape = RoundedCornerShape(30.dp)) {
                        Icon(Icons.Filled.Snooze, null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(R.string.ring_snooze_minutes, snoozeMinutes), fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(DS.md))
                Button(
                    onClick = { RingService.send(context, RingService.ACTION_STOP, item.key) },
                    modifier = big,
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tint),
                ) { Text(stringResource(R.string.ring_stop), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(DS.lg))
        }
    }
}

private fun openUri(context: Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
