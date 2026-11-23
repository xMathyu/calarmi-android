package com.mathyusolutions.calarmi.countdown

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lo que llega de la base: `event` nulo = ya no existe (se borró). */
private class Loaded(val event: CountdownEvent?)

/**
 * Una cuenta regresiva a pantalla grande: la foto (o el color) de fondo, los
 * días en grande y, si tiene hora, la cuenta segundo a segundo.
 */
@Composable
fun CountdownDetailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val loaded by remember(id) { container.countdowns.observe(id).map(::Loaded) }
        .collectAsStateWithLifecycle(initialValue = null)
    // Se sale una sola vez: borrar desde aquí ya volvió atrás, y un segundo
    // «atrás» al vaciarse la cuenta sacaría también la lista.
    var left by remember { mutableStateOf(false) }
    fun leave() {
        if (left) return
        left = true
        onBack()
    }

    val state = loaded ?: return
    val event = state.event
    if (event == null) {
        // Borrada (desde el editor, o el widget apuntaba a una que ya no está).
        LaunchedEffect(Unit) { leave() }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    val content = event.cardContent()
    val photo by rememberCountdownPhoto(content.backgroundPhoto)

    // Con hora, segundo a segundo; sin ella, los días cambian a medianoche.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(event.includesTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (event.includesTime) 1_000L - now % 1_000L else 30_000L)
        }
    }

    // El texto de la barra de estado va sobre la foto o el color: en blanco.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hero(content, photo, now)
            Column(Modifier.padding(top = DS.lg).navigationBarsPadding()) {
                val notes = event.notes
                if (!notes.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(DS.radiusMd),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.sm),
                    ) {
                        Text(notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(DS.lg))
                    }
                }
                // Mientras el evento no haya pasado: una alarma ese día, a su hora o a las 9:00.
                if (!event.status(now).isPast) {
                    val navigator = com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator.current
                    SectionCard {
                        SettingRow(
                            title = stringResource(R.string.countdowns_set_alarm),
                            icon = Icons.Filled.Alarm,
                            divider = false,
                            onClick = { navigator.newReminder(initialDate = alarmDate(event, now), initialTitle = event.title) },
                        )
                    }
                }
                SectionCard {
                    SettingRow(
                        title = stringResource(R.string.action_delete),
                        icon = Icons.Filled.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        divider = false,
                        onClick = { confirmDelete = true },
                    )
                }
                Spacer(Modifier.height(DS.xxxl))
            }
        }

        // La barra va encima del fondo, en blanco, como en iOS.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = DS.xs, vertical = DS.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = ::leave,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.22f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { navigator.editCountdown(event.id) },
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)),
            ) { Text(stringResource(R.string.countdowns_edit), color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.countdowns_delete_confirm)) },
            text = { Text(event.title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Primero se va la pantalla y después se borra: si no, se ve
                    // un instante vacía mientras sale.
                    leave()
                    container.scope.launch {
                        delay(350)
                        container.countdowns.delete(event.id)
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Hero(content: CountdownCardContent, photo: CountdownPhotoState, now: Long) {
    val shadow = TextStyle(
        shadow = Shadow(Color.Black.copy(alpha = if (photo.hasPhoto) 0.35f else 0.12f), Offset(0f, 2f), blurRadius = 8f),
    )
    val status = content.status(now)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = DS.radiusXl * 1.5f, bottomEnd = DS.radiusXl * 1.5f)),
    ) {
        CountdownBackground(content, photo, modifier = Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 460.dp)
                .statusBarsPadding()
                .padding(start = DS.xl, end = DS.xl, top = 72.dp, bottom = DS.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DS.lg, Alignment.Bottom),
        ) {
            if (!photo.hasPhoto) CountdownIconTile(content.iconKind, content.iconValue, size = 96.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.xs)) {
                Text(
                    content.title,
                    style = MaterialTheme.typography.headlineLarge.merge(shadow),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                CountdownDateLabel(
                    content, now,
                    style = MaterialTheme.typography.titleMedium.merge(shadow),
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            CountdownCounter(status, numberSize = 96.sp, style = shadow)
            // Solo con hora: sin ella el desglose contaría hasta la medianoche y
            // desdiría al número grande.
            val parts = if (content.includesTime) CountdownBreakdown.between(now, content.target(now)) else null
            if (parts != null) Breakdown(parts)
        }
    }
}

@Composable
private fun Breakdown(parts: CountdownBreakdown) {
    Row(horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
        UnitBox(parts.days.toString(), stringResource(R.string.countdowns_unit_days))
        UnitBox(parts.hours.toString(), stringResource(R.string.countdowns_unit_hours))
        UnitBox(parts.minutes.toString(), stringResource(R.string.countdowns_unit_minutes))
        UnitBox(parts.seconds.toString(), stringResource(R.string.countdowns_unit_seconds))
    }
}

@Composable
private fun UnitBox(value: String, label: String) {
    val locale = LocalContext.current.currentLocale()
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(DS.radiusMd))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(vertical = DS.sm, horizontal = DS.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            label.uppercase(locale),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** La alarma suena a la hora del evento o, si no tiene, a las 9:00 de ese día. */
private fun alarmDate(event: CountdownEvent, now: Long): Long {
    val target = event.target(now)
    if (event.includesTime) return target
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(target).atZone(zone).toLocalDate().atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}
