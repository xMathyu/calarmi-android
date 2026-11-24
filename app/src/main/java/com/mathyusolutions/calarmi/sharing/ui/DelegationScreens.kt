package com.mathyusolutions.calarmi.sharing.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.ui.components.EmptyState
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.launch

/**
 * «Personas de confianza» (lado de quien delega): encenderlo, invitar y ver o
 * revocar a quienes administran sus alarmas (el `DelegationSettingsView` de iOS).
 */
@Composable
fun TrustedPeopleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharing = CalarmiApp.container(context).sharing
    val delegation = sharing.delegation
    val enabled by sharing.profile.delegationEnabled.collectAsStateWithLifecycle()
    val signedIn by sharing.signedIn.collectAsStateWithLifecycle()
    val helpers by remember(enabled, signedIn) { delegation.observeHelpers() }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf<com.mathyusolutions.calarmi.sharing.ShareMapping.Person?>(null) }

    fun run(block: suspend () -> Unit) {
        working = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = ShareTexts.failure(context, e)
            } finally {
                working = false
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.sharing_trusted_people), onBack = onBack) {
        SectionCard(title = stringResource(R.string.sharing_delegation_section), footer = stringResource(R.string.sharing_allow_trusted_footer)) {
            SettingRow(
                title = stringResource(R.string.sharing_allow_trusted),
                icon = Icons.Filled.Shield,
                divider = false,
                trailing = {
                    if (working && !enabled) RowProgress()
                    else Switch(
                        checked = enabled,
                        enabled = !working,
                        onCheckedChange = { on -> if (on) run { delegation.enable() } else confirmDisable = true },
                    )
                },
            )
        }
        if (enabled) {
            SectionCard(footer = stringResource(R.string.sharing_invite_trusted_footer)) {
                SettingRow(
                    title = stringResource(R.string.sharing_invite_trusted),
                    icon = Icons.Filled.PersonAdd,
                    divider = false,
                    enabled = !working,
                    onClick = {
                        run {
                            val invite = delegation.invite()
                            ShareTexts.send(context, ShareTexts.delegation(context, invite.url))
                        }
                    },
                    trailing = { if (working) RowProgress() },
                )
            }
            SectionCard(title = stringResource(R.string.sharing_people_with_access)) {
                val list = helpers
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(DS.lg), contentAlignment = Alignment.Center) { RowProgress() }
                    list.isEmpty() -> Text(
                        stringResource(R.string.sharing_no_helpers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(DS.lg),
                    )
                    else -> list.forEachIndexed { i, person ->
                        PersonRow(person.name, divider = i < list.lastIndex) {
                            TextButton(onClick = { confirmRevoke = person }) {
                                Text(stringResource(R.string.sharing_revoke), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(DS.xxxl))
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.sharing_disable_title)) },
            text = { Text(stringResource(R.string.sharing_disable_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; run { delegation.disable() } }) {
                    Text(stringResource(R.string.sharing_disable), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirmRevoke?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            title = { Text(stringResource(R.string.sharing_revoke_title, person.name)) },
            confirmButton = {
                TextButton(onClick = { confirmRevoke = null; run { delegation.removeHelper(person.uid) } }) {
                    Text(stringResource(R.string.sharing_revoke), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    ShareErrorDialog(error) { error = null }
}

/** «Listas que administro»: las personas que invitaron a esta como persona de confianza. */
@Composable
fun ManagedListsScreen(onBack: () -> Unit) {
    val sharing = CalarmiApp.container(LocalContext.current).sharing
    val navigator = LocalAppNavigator.current
    val signedIn by sharing.signedIn.collectAsStateWithLifecycle()
    val principals by remember(signedIn) { sharing.delegation.observePrincipals() }.collectAsStateWithLifecycle(initialValue = null)

    ScreenScaffold(title = stringResource(R.string.sharing_managed_lists), onBack = onBack) {
        val list = principals
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(DS.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(
                Icons.Filled.ManageAccounts,
                stringResource(R.string.sharing_no_lists_title),
                stringResource(R.string.sharing_no_lists_message),
            )
            else -> SectionCard {
                list.forEachIndexed { i, principal ->
                    Box(Modifier.clickable { navigator.openPrincipal(principal.uid, principal.name) }) {
                        PersonRow(principal.name, divider = i < list.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Las alarmas de otra persona, que esta administra: crear, editar y borrar van
 * directo a su documento (el editor de alarmas en modo «de otra persona»).
 * Nada de esto suena en este teléfono.
 */
@Composable
fun PrincipalAlarmsScreen(principal: String, name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val sharing = CalarmiApp.container(context).sharing
    val navigator = LocalAppNavigator.current
    val scope = rememberCoroutineScope()
    val items by remember(principal) { sharing.delegation.observeDelegated(principal) }.collectAsStateWithLifecycle(initialValue = null)
    val principals by remember { sharing.delegation.observePrincipals() }.collectAsStateWithLifecycle(initialValue = null)
    var confirmLeave by remember { mutableStateOf(false) }
    // Si quien delega revocó el acceso (o apagó la delegación), la lista ya no está.
    val gone = principals?.none { it.uid == principal } == true

    ScreenScaffold(
        title = name,
        onBack = onBack,
        actions = {
            if (!gone) {
                IconButton(onClick = { navigator.newDelegatedReminder(principal, name) }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.sharing_new_alarm_for, name))
                }
            }
        },
    ) {
        val list = items
        when {
            gone -> EmptyState(Icons.Filled.ManageAccounts, stringResource(R.string.sharing_managed_lists), stringResource(R.string.sharing_list_gone))
            list == null -> Box(Modifier.fillMaxWidth().padding(DS.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(
                Icons.Filled.NotificationsOff,
                stringResource(R.string.sharing_no_alarms_title),
                stringResource(R.string.sharing_no_alarms_message, name),
            )
            else -> {
                val now = System.currentTimeMillis()
                list.sortedBy { it.remote.reminder.nextOccurrence(now) ?: Long.MAX_VALUE }.forEach { item ->
                    DelegatedRow(item.remote) { navigator.openDelegatedReminder(principal, name, item.remote.reminder.id) }
                }
            }
        }
        if (!gone) {
            SectionCard {
                SettingRow(
                    title = stringResource(R.string.sharing_leave_list),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = MaterialTheme.colorScheme.error,
                    divider = false,
                    onClick = { confirmLeave = true },
                )
            }
        }
        Spacer(Modifier.height(DS.xxxl))
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.sharing_leave_list_title, name)) },
            text = { Text(stringResource(R.string.sharing_leave_list_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch { runCatching { sharing.delegation.leaveList(principal) } }
                    onBack()
                }) { Text(stringResource(R.string.sharing_leave), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DelegatedRow(remote: com.mathyusolutions.calarmi.sharing.ShareMapping.RemoteAlarm, onClick: () -> Unit) {
    val context = LocalContext.current
    val reminder = remote.reminder
    val tint = remote.customCategory?.let { parseHex(it.colorHex) } ?: reminder.category.tint
    val next = reminder.nextOccurrence()
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = 3.dp),
    ) {
        Row(Modifier.clickable(onClick = onClick).padding(DS.md), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).alpha(if (reminder.isEnabled && next != null) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                ReminderIcon(reminder.iconKind, reminder.symbolName, tint, fallbackSymbol = reminder.category.defaultSymbol)
                Spacer(Modifier.width(DS.md))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val at = next ?: reminder.date
                    Text(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())) +
                            " · " + DateFormat.getTimeFormat(context).format(Date(at)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reminder.recurrence.isRecurring) {
                        Text(reminder.recurrence.summary(context), style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
