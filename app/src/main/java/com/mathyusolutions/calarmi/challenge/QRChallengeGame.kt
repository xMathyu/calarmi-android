package com.mathyusolutions.calarmi.challenge

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hay que escanear EL código registrado al configurar el reto: el de la pasta
 * de dientes, uno pegado en la cocina… lo que obligue a levantarse.
 *
 * El lector es el de Google Play services (`CodeScanner`): abre su propia
 * pantalla de cámara, así que aquí solo hay un botón para abrirlo (se abre
 * solo al empezar) y el resultado. Si el teléfono no puede escanear, en vez de
 * dejar la alarma sonando sin salida se ofrece sacudir el teléfono.
 *
 * `returnToHost`: con la alarma sonando, el lector se abre en la tarea
 * principal de la app; al cerrarlo sin acertar se vuelve a traer al frente la
 * pantalla de la alarma.
 */
@Composable
fun QRChallengeGame(
    expectedCode: String,
    difficulty: WakeChallenge.Difficulty,
    onSolved: () -> Unit,
    returnToHost: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberChallengeHaptics()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var scanning by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    var useShakes by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }

    if (useShakes) {
        ShakeChallengeGame(difficulty, onSolved, modifier)
        return
    }

    fun scan() {
        val activity = context.findActivity() ?: return
        if (scanning || solved) return
        scanning = true
        CodeScanner.scan(activity) { result ->
            scanning = false
            when (result) {
                is CodeScanner.Result.Code -> if (result.value == expectedCode) {
                    solved = true
                    onSolved()
                } else {
                    mismatch = true
                    haptics.error()
                }
                CodeScanner.Result.Cancelled -> Unit
                CodeScanner.Result.Unavailable -> {
                    unavailable = true
                    haptics.error()
                }
            }
            if (!solved && returnToHost) {
                scope.launch {
                    // Si al cerrarse el lector quedó delante otra pantalla de la
                    // app, se vuelve a la de la alarma.
                    delay(600)
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) CodeScanner.bringToFront(activity)
                }
            }
        }
    }

    // Directo al lector: medio dormido, un toque menos es un toque menos.
    LaunchedEffect(Unit) {
        delay(350)
        scan()
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.md)) {
        Text(
            stringResource(R.string.challenges_qr_point),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Surface(
            shape = RoundedCornerShape(DS.radiusLg),
            color = tileColor,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = GameMaxWidth)
                .fillMaxWidth()
                .heightIn(max = 340.dp),
        ) {
            Box(Modifier.fillMaxSize().padding(DS.lg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.md)) {
                    if (unavailable) {
                        Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Text(
                            stringResource(R.string.challenges_qr_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                    }
                    Button(onClick = { unavailable = false; scan() }, enabled = !scanning, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(if (unavailable) R.string.action_retry else R.string.challenges_qr_scan))
                    }
                    if (unavailable) {
                        OutlinedButton(onClick = { useShakes = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Vibration, contentDescription = null)
                            Spacer(Modifier.width(DS.sm))
                            Text(stringResource(R.string.challenges_use_shakes))
                        }
                    }
                }
            }
        }
        val mismatchAlpha by animateFloatAsState(if (mismatch) 1f else 0f, label = "mismatch")
        Text(
            stringResource(R.string.challenges_qr_mismatch),
            style = MaterialTheme.typography.bodySmall,
            color = errorRed,
            modifier = Modifier.alpha(mismatchAlpha),
        )
    }
}

/**
 * El lector de códigos QR y de barras de Google Play services. No necesita
 * permiso de cámara: la cámara la usa Play services en su propia pantalla. Se
 * usa para registrar el código en el editor y para escanearlo al sonar.
 */
internal object CodeScanner {
    sealed interface Result {
        data class Code(val value: String) : Result
        data object Cancelled : Result
        data object Unavailable : Result
    }

    /**
     * Abre el lector. Si el teléfono está bloqueado pide desbloquearlo antes: la
     * pantalla del lector no puede mostrarse sobre la pantalla de bloqueo.
     */
    fun scan(activity: Activity, onResult: (Result) -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                activity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = start(activity, onResult)
                    override fun onDismissCancelled() = onResult(Result.Cancelled)
                    override fun onDismissError() = start(activity, onResult)
                },
            )
        } else {
            start(activity, onResult)
        }
    }

    private fun start(activity: Activity, onResult: (Result) -> Unit) {
        // Sin `allowManualInput`: teclear el código sería un atajo para no levantarse.
        val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        try {
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { barcode -> onResult(Result.Code(barcode.rawValue ?: barcode.displayValue.orEmpty())) }
                .addOnCanceledListener { onResult(Result.Cancelled) }
                .addOnFailureListener { onResult(Result.Unavailable) }
        } catch (_: Exception) {
            onResult(Result.Unavailable)
        }
    }

    /** Vuelve a poner delante la pantalla que abrió el lector. */
    fun bringToFront(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            activity.startActivity(
                Intent(activity, activity.javaClass)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
