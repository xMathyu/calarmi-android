package com.mathyusolutions.calarmi.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.account.AccountException
import com.mathyusolutions.calarmi.account.AccountState
import com.mathyusolutions.calarmi.account.AuthFailure
import com.mathyusolutions.calarmi.account.AuthMethod
import com.mathyusolutions.calarmi.account.authFailure
import com.mathyusolutions.calarmi.sharing.ShareMapping
import com.mathyusolutions.calarmi.sharing.ui.PersonRow
import com.mathyusolutions.calarmi.ui.components.HeroIcon
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.theme.DS
import kotlinx.coroutines.launch

private enum class AccountWork { SIGN_OUT, DELETE }

/**
 * Ajustes › Cuenta: el nombre (que ven los demás) y el correo (que no ve nadie),
 * editar el nombre, «Cerrar sesión» y «Borrar cuenta» (DESIGN.md › Identidad).
 */
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val account = container.account
    val state by account.state.collectAsStateWithLifecycle()
    val cachedName by container.sharing.profile.name.collectAsStateWithLifecycle()
    val name = cachedName ?: container.sharing.myName()
    val email = (state as? AccountState.SignedIn)?.email
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<AccountWork?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    SectionCard(title = stringResource(R.string.account_section), footer = stringResource(R.string.account_footer)) {
        Box(Modifier.clickable { editing = true }) {
            PersonRow(name, subtitle = email?.takeIf { it.isNotBlank() }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.account_edit_name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        SettingRow(
            title = stringResource(R.string.account_sign_out),
            icon = Icons.AutoMirrored.Filled.Logout,
            enabled = working == null,
            onClick = { confirmSignOut = true },
        )
        SettingRow(
            title = stringResource(R.string.account_delete),
            icon = Icons.Filled.PersonRemove,
            iconTint = MaterialTheme.colorScheme.error,
            divider = false,
            enabled = working == null,
            onClick = { confirmDelete = true },
        )
    }

    if (editing) {
        EditNameDialog(
            current = name,
            onDismiss = { editing = false },
            onSave = { newName ->
                editing = false
                account.rename(newName)
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out_title)) },
            text = { Text(stringResource(R.string.account_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    working = AccountWork.SIGN_OUT
                    scope.launch {
                        // Al terminar, la app vuelve sola a la pantalla de inicio de sesión.
                        runCatching { account.signOut(context) }
                        working = null
                    }
                }) { Text(stringResource(R.string.account_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    working = AccountWork.DELETE
                    scope.launch {
                        try {
                            account.deleteAccount(context)
                        } catch (e: Exception) {
                            val failure = (e as? AccountException)?.failure ?: e.authFailure(AuthMethod.ACCOUNT)
                            deleteError = context.getString(
                                if (failure == AuthFailure.NETWORK) R.string.sharing_error_offline else R.string.account_delete_error,
                            )
                        } finally {
                            working = null
                        }
                    }
                }) { Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    working?.let { WorkingDialog(stringResource(if (it == AccountWork.DELETE) R.string.account_deleting else R.string.account_signing_out)) }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text(stringResource(R.string.account_delete_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** Mientras se cierra la sesión o se borra la cuenta: no se puede tocar nada más. */
@Composable
private fun WorkingDialog(text: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(DS.radiusXl), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(DS.xxl), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(DS.lg))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Editar el nombre: el mismo camino que antes (Auth + `users/{uid}` + lo compartido). */
@Composable
private fun EditNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val trimmed = text.trim()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { HeroIcon(Icons.Filled.Person, size = DS.avatarLg) },
        title = { Text(stringResource(R.string.account_name_title), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DS.md)) {
                Text(stringResource(R.string.account_name_message), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(ShareMapping.MAX_NAME) },
                    placeholder = { Text(stringResource(R.string.account_name_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (trimmed.isNotEmpty()) onSave(trimmed) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(trimmed) }, enabled = trimmed.isNotEmpty()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
