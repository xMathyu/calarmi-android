package com.mathyusolutions.calarmi.challenge

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.ui.components.FullScreenSheet
import com.mathyusolutions.calarmi.ui.components.PlusBadge
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS

/**
 * Elige el reto de una alarma: tipo, dificultad, si se puede posponer y, para
 * el QR, el código que habrá que escanear. «Probar reto» abre el juego en seco
 * para que nadie descubra al sonar la alarma que no entiende el reto.
 * `onSelect(null)` = sin reto.
 */
@Composable
fun ChallengePickerSheet(
    selection: WakeChallenge?,
    alarmTitle: String,
    onDismiss: () -> Unit,
    onSelect: (WakeChallenge?) -> Unit,
) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val haptics = rememberChallengeHaptics()
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    val latestSelection by rememberUpdatedState(selection)
    var scanFailed by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    // Al abrir el paywall o la práctica la hoja se esconde enseguida en vez de
    // quedarse encima durante la transición; si se vuelve con el editor aún
    // abierto, reaparece.
    var away by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        away = false
        onPauseOrDispose { }
    }
    // «Caminar» cuenta mejor con el detector de pasos, que pide el permiso de
    // actividad física: se pide aquí, al elegirlo, y no medio dormido al sonar.
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun select(kind: WakeChallenge.Kind?) {
        haptics.selection()
        if (kind == null) {
            onSelect(null)
            return
        }
        if (selection?.kind == kind) return
        onSelect(
            WakeChallenge(
                kind = kind,
                difficulty = selection?.difficulty ?: WakeChallenge.Difficulty.MEDIUM,
                allowsSnooze = selection?.allowsSnooze ?: true,
                code = if (kind == WakeChallenge.Kind.QR) selection?.code else null,
            ),
        )
        if (kind == WakeChallenge.Kind.STEPS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityPermission(context)) {
            runCatching { activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        }
    }

    fun registerCode() {
        val activity = context.findActivity() ?: return
        if (scanning) return
        scanning = true
        scanFailed = false
        CodeScanner.scan(activity) { result ->
            scanning = false
            when (result) {
                is CodeScanner.Result.Code -> if (result.value.isNotEmpty()) {
                    haptics.success()
                    latestSelection?.let { onSelect(it.copy(code = result.value)) }
                }
                CodeScanner.Result.Cancelled -> Unit
                CodeScanner.Result.Unavailable -> scanFailed = true
            }
        }
    }

    if (away) return
    FullScreenSheet(title = stringResource(R.string.editor_challenge), onDismiss = onDismiss) {
        Spacer(Modifier.height(DS.sm))
        SectionCard(
            title = stringResource(R.string.challenges_section),
            footer = if (selection == null) {
                stringResource(R.string.challenges_footer_none)
            } else {
                stringResource(R.string.challenges_footer_active, (WakeChallenge.MAX_RING_MILLIS / 60_000).toInt())
            },
        ) {
            KindRow(
                icon = Icons.Filled.NotificationsOff,
                title = stringResource(R.string.editor_challenge_none),
                description = null,
                selected = selection == null,
                locked = false,
                divider = false,
                onClick = { select(null) },
            )
        }

        WakeChallenge.Group.entries.forEach { group ->
            val kinds = WakeChallenge.Kind.inGroup(group)
            SectionCard(
                title = stringResource(group.titleRes),
                footer = if (group == WakeChallenge.Group.ARCADE && !isPlus) stringResource(R.string.challenges_arcade_footer) else null,
            ) {
                kinds.forEachIndexed { i, kind ->
                    val selected = selection?.kind == kind
                    // El que ya está elegido nunca se bloquea: puede venir de antes
                    // de Plus o de una alarma que compartió alguien que sí lo tiene.
                    val locked = kind.requiresPlus && !isPlus && !selected
                    KindRow(
                        icon = SymbolIcons.of(kind.symbol),
                        title = stringResource(kind.titleRes),
                        description = stringResource(kind.descriptionRes),
                        selected = selected,
                        locked = locked,
                        divider = i < kinds.lastIndex,
                        onClick = {
                            if (locked) {
                                haptics.light()
                                away = true
                                navigator.openPaywall(PlusFeature.ARCADE_CHALLENGES)
                            } else {
                                select(kind)
                            }
                        },
                    )
                }
            }
        }

        val current = selection
        if (current != null) {
            SectionCard(title = stringResource(R.string.challenges_difficulty)) {
                val levels = WakeChallenge.Difficulty.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(DS.md)) {
                    levels.forEachIndexed { i, level ->
                        SegmentedButton(
                            selected = current.difficulty == level,
                            onClick = {
                                if (current.difficulty != level) {
                                    haptics.selection()
                                    onSelect(current.copy(difficulty = level))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                        ) { Text(stringResource(level.titleRes)) }
                    }
                }
            }

            if (current.kind == WakeChallenge.Kind.QR) {
                val code = current.code
                SectionCard(
                    footer = when {
                        scanFailed -> stringResource(R.string.challenges_qr_unavailable)
                        code.isNullOrEmpty() -> stringResource(R.string.editor_challenge_missing_code)
                        else -> null
                    },
                ) {
                    SettingRow(
                        title = stringResource(if (code.isNullOrEmpty()) R.string.challenges_register_code else R.string.challenges_change_code),
                        icon = Icons.Filled.QrCode,
                        subtitle = if (code.isNullOrEmpty()) stringResource(R.string.challenges_register_hint) else null,
                        divider = !code.isNullOrEmpty(),
                        enabled = !scanning,
                        onClick = ::registerCode,
                    )
                    if (!code.isNullOrEmpty()) {
                        SettingRow(
                            title = stringResource(R.string.challenges_registered_code),
                            divider = false,
                            trailing = {
                                Text(
                                    code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    modifier = Modifier.padding(start = DS.sm).widthIn(max = 180.dp),
                                )
                            },
                        )
                    }
                }
            }

            SectionCard {
                SettingRow(
                    title = stringResource(R.string.challenges_allow_snooze),
                    divider = false,
                    onClick = { onSelect(current.copy(allowsSnooze = !current.allowsSnooze)) },
                    trailing = {
                        Switch(
                            checked = current.allowsSnooze,
                            onCheckedChange = { onSelect(current.copy(allowsSnooze = it)) },
                        )
                    },
                )
            }

            SectionCard {
                SettingRow(
                    title = stringResource(R.string.challenges_try),
                    icon = Icons.Filled.PlayCircle,
                    divider = false,
                    enabled = current.isReady,
                    onClick = {
                        away = true
                        navigator.practiceChallenge(current, alarmTitle)
                    },
                )
            }
        }
        Spacer(Modifier.height(DS.xxxl))
    }
}

/** Una fila de tipo de reto: ícono, nombre, descripción y la marca de elegido. */
@Composable
private fun KindRow(
    icon: ImageVector,
    title: String,
    description: String?,
    selected: Boolean,
    locked: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = selected }
                .padding(horizontal = DS.lg, vertical = DS.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DS.md),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (locked) PlusBadge()
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = DS.lg + 24.dp + DS.md),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}
