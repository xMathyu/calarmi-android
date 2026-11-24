package com.mathyusolutions.calarmi.account.ui

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.account.AccountException
import com.mathyusolutions.calarmi.account.AccountForm
import com.mathyusolutions.calarmi.account.AuthFailure
import com.mathyusolutions.calarmi.account.AuthMethod
import com.mathyusolutions.calarmi.account.authFailure
import com.mathyusolutions.calarmi.sharing.ShareMapping
import com.mathyusolutions.calarmi.ui.components.AppIconImage
import com.mathyusolutions.calarmi.ui.plus.AppIcon
import com.mathyusolutions.calarmi.ui.settings.WEBSITE
import com.mathyusolutions.calarmi.ui.settings.localizedPath
import com.mathyusolutions.calarmi.ui.settings.openUrl
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent
import kotlinx.coroutines.launch

/** Los mensajes de la cuenta: `null` cuando no hay nada que decir (la persona canceló). */
object AccountTexts {
    fun message(context: Context, failure: AuthFailure): String? = when (failure) {
        AuthFailure.CANCELLED -> null
        AuthFailure.INVALID_CREDENTIALS -> context.getString(R.string.auth_error_invalid_credentials)
        AuthFailure.EMAIL_IN_USE -> context.getString(R.string.auth_error_email_in_use)
        AuthFailure.WEAK_PASSWORD -> context.getString(R.string.auth_error_weak_password)
        AuthFailure.INVALID_EMAIL -> context.getString(R.string.auth_error_invalid_email)
        AuthFailure.OTHER_PROVIDER -> context.getString(R.string.auth_error_other_provider)
        AuthFailure.USER_DISABLED -> context.getString(R.string.auth_error_disabled)
        AuthFailure.NETWORK -> context.getString(R.string.sharing_error_offline)
        AuthFailure.TOO_MANY_REQUESTS -> context.getString(R.string.auth_error_too_many)
        AuthFailure.NO_GOOGLE_ACCOUNT -> context.getString(R.string.auth_error_no_google_account)
        AuthFailure.GOOGLE_UNAVAILABLE -> context.getString(R.string.auth_error_google_unavailable)
        AuthFailure.OTHER -> context.getString(R.string.sharing_error_generic)
    }

    fun message(context: Context, error: Throwable, method: AuthMethod): String? =
        message(context, (error as? AccountException)?.failure ?: error.authFailure(method))
}

private enum class EmailMode(val title: Int) { CREATE(R.string.signin_mode_create), SIGN_IN(R.string.signin_mode_signin) }

private enum class Working { GOOGLE, EMAIL, RESET }

/**
 * Lo primero que se ve (antes de la bienvenida) y lo que vuelve tras cerrar sesión o
 * borrar la cuenta: «Continuar con Google» y, debajo, correo y contraseña
 * (DESIGN.md › Identidad). Las alarmas ya programadas suenan igual sin sesión.
 */
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val account = CalarmiApp.container(context).account
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var working by remember { mutableStateOf<Working?>(null) }
    var googleError by remember { mutableStateOf<String?>(null) }
    var showEmail by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(EmailMode.CREATE) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var resetSentTo by remember { mutableStateOf<String?>(null) }

    fun google() {
        val host = activity ?: return
        googleError = null
        emailError = null
        working = Working.GOOGLE
        scope.launch {
            try {
                account.signInWithGoogle(host)
            } catch (e: Exception) {
                googleError = AccountTexts.message(context, e, AuthMethod.GOOGLE)
            } finally {
                working = null
            }
        }
    }

    fun submit() {
        googleError = null
        val problem = when {
            mode == EmailMode.CREATE && name.isBlank() -> context.getString(R.string.signin_error_name_required)
            !AccountForm.looksLikeEmail(email) -> context.getString(R.string.auth_error_invalid_email)
            mode == EmailMode.CREATE && !AccountForm.passwordLongEnough(password) ->
                context.getString(R.string.signin_error_password_short, AccountForm.MIN_PASSWORD)
            else -> null
        }
        emailError = problem
        if (problem != null) return
        focus.clearFocus()
        working = Working.EMAIL
        scope.launch {
            try {
                when (mode) {
                    EmailMode.CREATE -> account.createAccount(name.trim(), email, password)
                    EmailMode.SIGN_IN -> account.signInWithEmail(email, password)
                }
            } catch (e: Exception) {
                emailError = AccountTexts.message(context, e, if (mode == EmailMode.CREATE) AuthMethod.EMAIL_CREATE else AuthMethod.EMAIL_SIGN_IN)
            } finally {
                working = null
            }
        }
    }

    fun forgot() {
        googleError = null
        if (!AccountForm.looksLikeEmail(email)) {
            emailError = context.getString(R.string.signin_forgot_need_email)
            return
        }
        emailError = null
        working = Working.RESET
        scope.launch {
            try {
                account.sendPasswordReset(email)
                resetSentTo = email.trim()
            } catch (e: Exception) {
                emailError = AccountTexts.message(context, e, AuthMethod.EMAIL_SIGN_IN)
            } finally {
                working = null
            }
        }
    }

    AccountBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DS.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            AppIconHero()
            Spacer(Modifier.height(DS.xl))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(DS.sm))
            Text(
                stringResource(R.string.signin_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(DS.xxxl))

            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GoogleSignInButton(onClick = ::google, loading = working == Working.GOOGLE, enabled = working == null)
                googleError?.let { InlineError(it) }

                if (!showEmail) {
                    Spacer(Modifier.height(DS.md))
                    OutlinedButton(
                        onClick = { showEmail = true; googleError = null },
                        enabled = working == null,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Mail, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(R.string.signin_email), fontWeight = FontWeight.SemiBold)
                    }
                }
                AnimatedVisibility(visible = showEmail, enter = fadeIn() + expandVertically()) {
                    Column {
                        OrDivider()
                        EmailForm(
                            mode = mode,
                            onMode = { mode = it; emailError = null },
                            name = name,
                            onName = { name = it.take(ShareMapping.MAX_NAME) },
                            email = email,
                            onEmail = { email = it.trim() },
                            password = password,
                            onPassword = { password = it },
                            showPassword = showPassword,
                            onTogglePassword = { showPassword = !showPassword },
                            error = emailError,
                            working = working,
                            onSubmit = ::submit,
                            onForgot = ::forgot,
                        )
                    }
                }
            }

            Spacer(Modifier.height(DS.xl))
            TextButton(onClick = { openUrl(context, "$WEBSITE/" + localizedPath(context, "privacy")) }) {
                Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(DS.lg))
        }
    }

    resetSentTo?.let { address ->
        AlertDialog(
            onDismissRequest = { resetSentTo = null },
            icon = { Icon(Icons.Filled.Mail, null) },
            title = { Text(stringResource(R.string.signin_reset_sent_title)) },
            text = { Text(stringResource(R.string.signin_reset_sent_message, address)) },
            confirmButton = { TextButton(onClick = { resetSentTo = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/**
 * Antes del primer desbloqueo la sesión no se puede leer (vive en el almacenamiento
 * cifrado): solo se pide desbloquear. Las alarmas suenan igual.
 */
@Composable
fun AccountLockedScreen() {
    AccountBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(DS.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIconHero()
            Spacer(Modifier.height(DS.xl))
            Text(
                stringResource(R.string.signin_locked),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** El mismo fondo que la bienvenida: el acento que se desvanece hacia abajo. */
@Composable
private fun AccountBackground(content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)),
        ),
    ) { content() }
}

/** El ícono de la app (el que la persona eligió en Ajustes › Ícono). */
@Composable
private fun AppIconHero() {
    val context = LocalContext.current
    val icon = remember { AppIcon.current(context) }
    AppIconImage(icon.previewRes, size = 96.dp, modifier = Modifier.shadow(12.dp, RoundedCornerShape(22.dp)), corner = 22.dp)
}

/**
 * El botón de Google según sus pautas de marca: la «G» de cuatro colores sin cambios,
 * «Continuar con Google» en Roboto Medium, relleno blanco con borde #747775 (o #131314
 * con borde #8E918F en oscuro) y forma de píldora. Ancho completo, como los demás botones.
 */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, loading: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (dark) Color(0xFF131314) else Color.White
    val border = if (dark) Color(0xFF8E918F) else Color(0xFF747775)
    val content = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = container,
        border = BorderStroke(1.dp, border),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp).alpha(if (enabled || loading) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = content)
            } else {
                Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.signin_google),
                    color = content,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = DS.lg), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        Text(
            stringResource(R.string.signin_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DS.md),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

@Composable
private fun EmailForm(
    mode: EmailMode,
    onMode: (EmailMode) -> Unit,
    name: String,
    onName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    error: String?,
    working: Working?,
    onSubmit: () -> Unit,
    onForgot: () -> Unit,
) {
    val idle = working == null
    val ready = email.isNotBlank() && password.isNotEmpty() && (mode == EmailMode.SIGN_IN || name.isNotBlank())
    Surface(shape = RoundedCornerShape(DS.radiusLg), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(DS.lg), verticalArrangement = Arrangement.spacedBy(DS.md)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EmailMode.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { onMode(option) },
                        enabled = idle,
                        shape = SegmentedButtonDefaults.itemShape(i, EmailMode.entries.size),
                    ) { Text(stringResource(option.title), maxLines = 1) }
                }
            }
            if (mode == EmailMode.CREATE) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.signin_name)) },
                    singleLine = true,
                    enabled = idle,
                    shape = RoundedCornerShape(DS.radiusSm),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = onEmail,
                label = { Text(stringResource(R.string.signin_email_field)) },
                singleLine = true,
                enabled = idle,
                shape = RoundedCornerShape(DS.radiusSm),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress + ContentType.Username },
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                label = { Text(stringResource(R.string.signin_password)) },
                supportingText = if (mode == EmailMode.CREATE) {
                    { Text(stringResource(R.string.signin_password_hint, AccountForm.MIN_PASSWORD)) }
                } else null,
                singleLine = true,
                enabled = idle,
                shape = RoundedCornerShape(DS.radiusSm),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.signin_hide_password else R.string.signin_show_password),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (ready && idle) onSubmit() }),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentType = if (mode == EmailMode.CREATE) ContentType.NewPassword else ContentType.Password
                },
            )
            error?.let { InlineError(it, top = 0.dp) }
            Button(
                onClick = onSubmit,
                enabled = idle && ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (working == Working.EMAIL) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(mode.title), fontWeight = FontWeight.SemiBold)
                }
            }
            if (mode == EmailMode.SIGN_IN) {
                TextButton(onClick = onForgot, enabled = idle, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    if (working == Working.RESET) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.signin_forgot))
                }
            }
        }
    }
}

@Composable
private fun InlineError(message: String, top: androidx.compose.ui.unit.Dp = DS.sm) {
    Row(Modifier.fillMaxWidth().padding(top = top), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(DS.xs + 2.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
