package com.mathyusolutions.calarmi.sharing.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.domain.CountdownStatus
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.sharing.IncomingShare
import com.mathyusolutions.calarmi.sharing.ShareFailure
import com.mathyusolutions.calarmi.sharing.ShareMapping
import com.mathyusolutions.calarmi.sharing.SharingService
import com.mathyusolutions.calarmi.sharing.shareFailure
import com.mathyusolutions.calarmi.ui.components.HeroIcon
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------- Textos y enviar

/** El texto de una invitación, como en iOS (`InviteMessage`): la noticia primero, luego la llamada y el enlace. */
object ShareTexts {
    data class Message(val subject: String, val text: String)

    /** «⏰ Despertar — mañana, 7:30» + «Únete a mi alarma en Calarmi…» + enlace. */
    fun alarm(context: Context, reminder: Reminder, url: String): Message {
        val emoji = reminder.symbolName?.takeIf { reminder.iconKind == ReminderIconKind.EMOJI || isEmojiIcon(it) } ?: "⏰"
        val headline = "$emoji ${reminder.title} — ${whenText(context, reminder.nextOccurrence() ?: reminder.date)}"
        return Message(headline, headline + "\n" + context.getString(R.string.sharing_message_alarm) + "\n" + url)
    }

    /** «🎂 ¡Faltan 12 días para Cumpleaños de Ana!» + «Únete a mi cuenta regresiva en Calarmi:» + enlace. */
    fun countdown(context: Context, event: CountdownEvent, url: String): Message {
        val emoji = event.iconValue.takeIf { event.iconKind == ReminderIconKind.EMOJI && isEmojiIcon(it) } ?: "⏳"
        val news = when (val status = event.status()) {
            is CountdownStatus.Upcoming -> context.resources.getQuantityString(R.plurals.sharing_message_days, status.days, status.days, event.title)
            CountdownStatus.Today -> context.getString(R.string.sharing_message_today, event.title)
            is CountdownStatus.Past -> event.title
        }
        val headline = "$emoji $news"
        return Message(headline, headline + "\n" + context.getString(R.string.sharing_message_countdown) + "\n" + url)
    }

    fun delegation(context: Context, url: String): Message {
        val text = context.getString(R.string.sharing_message_delegation)
        return Message(text, text + "\n" + url)
    }

    /** «mañana, 7:30» / «tomorrow at 7:30 AM» / «24 nov 2026, 7:30». */
    fun whenText(context: Context, millis: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val dayText = when (day) {
            today -> context.getString(R.string.day_today_lower)
            today.plusDays(1) -> context.getString(R.string.day_tomorrow_lower)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(context.resources.configuration.locales[0]).format(day)
        }
        return context.getString(R.string.sharing_when, dayText, DateFormat.getTimeFormat(context).format(Date(millis)))
    }

    /** La hoja de compartir de Android (WhatsApp, Mensajes, correo…). */
    fun send(context: Context, message: Message) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, message.subject)
            .putExtra(Intent.EXTRA_TEXT, message.text)
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    fun failure(context: Context, error: Throwable): String = context.getString(
        when (error.shareFailure()) {
            ShareFailure.OFFLINE -> R.string.sharing_error_offline
            ShareFailure.INVITE_MISSING -> R.string.sharing_invite_missing
            ShareFailure.INVITE_REVOKED -> R.string.sharing_invite_revoked
            else -> R.string.sharing_error_generic
        },
    )
}

/** «Error al compartir» con su explicación. */
@Composable
fun ShareErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_error_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

// ------------------------------------------------------------------ Personas

/** El círculo con la inicial de alguien (no hay fotos de perfil: solo el nombre). */
@Composable
fun PersonAvatar(name: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val palette = listOf(0xFFFF6633, 0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFF34C759, 0xFF00C7BE, 0xFFFF9500)
    val color = Color(palette[(name.hashCode() and 0x7FFFFFFF) % palette.size])
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()
        if (initial != null) Text(initial.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        else Icon(Icons.Filled.Person, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun PersonRow(name: String, subtitle: String? = null, divider: Boolean = true, trailing: @Composable (() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = DS.lg, vertical = DS.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonAvatar(name)
            Spacer(Modifier.width(DS.md))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        if (divider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

/**
 * «Personas» de algo compartido (vistas por el dueño): cada una con su botón
 * para quitarla, que pregunta antes.
 */
@Composable
fun SharePeopleSection(people: List<ShareMapping.Person>, onRemove: (ShareMapping.Person) -> Unit) {
    var confirm by remember { mutableStateOf<ShareMapping.Person?>(null) }
    SectionCard(title = stringResource(R.string.sharing_people)) {
        if (people.isEmpty()) {
            Text(
                stringResource(R.string.sharing_no_one_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(DS.lg),
            )
        }
        people.forEachIndexed { i, person ->
            PersonRow(person.name, divider = i < people.lastIndex) {
                IconButton(onClick = { confirm = person }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    confirm?.let { person ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.sharing_remove_person, person.name)) },
            text = { Text(stringResource(R.string.sharing_remove_person_message)) },
            confirmButton = {
                TextButton(onClick = { confirm = null; onRemove(person) }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** «Compartido»: quién la compartió (vista de quien la recibió). */
@Composable
fun SharedBySection(ownerName: String?, isCountdown: Boolean) {
    SectionCard(title = stringResource(R.string.sharing_shared_header)) {
        if (!ownerName.isNullOrBlank()) {
            PersonRow(
                ownerName,
                subtitle = stringResource(if (isCountdown) R.string.sharing_shared_this_countdown else R.string.sharing_shared_this_alarm),
                divider = false,
            )
        } else {
            SettingRow(
                title = stringResource(if (isCountdown) R.string.sharing_shared_with_you_countdown else R.string.sharing_shared_with_you),
                icon = Icons.Filled.People,
                divider = false,
            )
        }
    }
}

/** «Dejar de compartir», con su confirmación. */
@Composable
fun StopSharingRow(isCountdown: Boolean, onConfirm: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SettingRow(
        title = stringResource(R.string.sharing_stop),
        icon = Icons.Filled.LinkOff,
        iconTint = MaterialTheme.colorScheme.error,
        divider = false,
        onClick = { confirm = true },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.sharing_stop_title)) },
            text = { Text(stringResource(if (isCountdown) R.string.sharing_stop_countdown_message else R.string.sharing_stop_alarm_message)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onConfirm() }) {
                    Text(stringResource(R.string.sharing_stop), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** «Salir» de algo recibido: pregunta, y al confirmar se quita de la lista y del share. */
@Composable
fun LeaveConfirmDialog(isCountdown: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isCountdown) R.string.sharing_leave_countdown_title else R.string.sharing_leave_alarm_title)) },
        text = { Text(stringResource(if (isCountdown) R.string.sharing_leave_countdown_message else R.string.sharing_leave_alarm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sharing_leave), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Un ícono de progreso pequeño para la derecha de una fila que está trabajando. */
@Composable
fun RowProgress() {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
}

// -------------------------------------------------------------- Aviso de arriba

/**
 * El aviso de arriba mientras se agrega una invitación y justo después (el
 * `IncomingShareToast` de iOS). Tocar la confirmación abre lo agregado.
 */
@Composable
fun IncomingShareToast(status: IncomingShare?, onOpen: (IncomingShare.Added) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Lo último que se mostró, para que la salida no se quede en blanco.
    var shown by remember { mutableStateOf(status) }
    if (status != null) shown = status
    LaunchedEffect(status) {
        if (status is IncomingShare.Added) {
            delay(4_500)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = status != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        val (title, subtitle) = when (current) {
            is IncomingShare.Adding -> stringResource(
                if (current.kind == ShareMapping.KIND_COUNTDOWN) R.string.sharing_toast_adding_countdown else R.string.sharing_toast_adding_alarm,
            ) to null
            is IncomingShare.Added -> when (current.kind) {
                ShareMapping.KIND_COUNTDOWN -> stringResource(R.string.sharing_toast_countdown, current.title.ifBlank { stringResource(R.string.sharing_kind_countdown) })
                ShareMapping.KIND_DELEGATION -> stringResource(R.string.sharing_toast_delegation, current.title)
                else -> stringResource(R.string.sharing_toast_alarm, current.title.ifBlank { stringResource(R.string.sharing_kind_alarm) })
            } to current.ownerName?.takeIf { it.isNotBlank() && current.kind != ShareMapping.KIND_DELEGATION }?.let { stringResource(R.string.sharing_toast_by, it) }
        }
        Surface(
            shape = RoundedCornerShape(DS.radiusLg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = DS.lg, vertical = DS.sm)
                .shadow(12.dp, RoundedCornerShape(DS.radiusLg))
                .then(if (current is IncomingShare.Added) Modifier.clickable { onOpen(current) } else Modifier),
        ) {
            Row(Modifier.padding(horizontal = DS.lg, vertical = DS.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (current is IncomingShare.Adding) RowProgress()
                    else Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759))
                }
                Spacer(Modifier.width(DS.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// --------------------------------------------------------- Aceptar invitación

private sealed interface InviteLoad {
    data object Loading : InviteLoad
    data class Ready(val info: SharingService.InviteInfo) : InviteLoad
    data class Failed(val message: String) : InviteLoad
}

/**
 * Lo que se ve al abrir un enlace de invitación: qué es, de quién y cuándo, con
 * «Aceptar» y «Ahora no». La invitación se lee sin sesión (el token es la
 * llave); al aceptar se entra con la cuenta, que ya existe (es obligatoria).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(token: String, onDismiss: () -> Unit, onAccepted: (SharingService.Accepted) -> Unit) {
    val context = LocalContext.current
    val sharing = CalarmiApp.container(context).sharing
    val scope = rememberCoroutineScope()
    var load by remember(token) { mutableStateOf<InviteLoad>(InviteLoad.Loading) }
    var accepting by remember { mutableStateOf(false) }
    var acceptError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(token) {
        load = try {
            val info = sharing.loadInvite(token)
            if (info.revoked) InviteLoad.Failed(context.getString(R.string.sharing_invite_revoked)) else InviteLoad.Ready(info)
        } catch (e: Exception) {
            InviteLoad.Failed(ShareTexts.failure(context, e))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = DS.xl, end = DS.xl, bottom = DS.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DS.md),
        ) {
            when (val state = load) {
                InviteLoad.Loading -> {
                    CircularProgressIndicator(Modifier.padding(top = DS.xl))
                    Text(stringResource(R.string.sharing_invite_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is InviteLoad.Failed -> {
                    HeroIcon(Icons.Filled.LinkOff, size = DS.avatarXl, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.sharing_invite_unavailable), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
                is InviteLoad.Ready -> {
                    val info = state.info
                    InviteTile(info)
                    Text(
                        stringResource(
                            when (info.kind) {
                                ShareMapping.KIND_COUNTDOWN -> R.string.sharing_kind_countdown
                                ShareMapping.KIND_DELEGATION -> R.string.sharing_kind_delegation
                                else -> R.string.sharing_kind_alarm
                            },
                        ).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (info.kind != ShareMapping.KIND_DELEGATION && info.date != null) {
                        Text(inviteDate(context, info), style = MaterialTheme.typography.titleMedium, color = LocalAccent.current)
                    }
                    Text(
                        stringResource(
                            when (info.kind) {
                                ShareMapping.KIND_COUNTDOWN -> R.string.sharing_invite_from_countdown
                                ShareMapping.KIND_DELEGATION -> R.string.sharing_invite_from_delegation
                                else -> R.string.sharing_invite_from_alarm
                            },
                            info.ownerName.ifBlank { "Calarmi" },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(
                            when (info.kind) {
                                ShareMapping.KIND_COUNTDOWN -> R.string.sharing_invite_countdown_detail
                                ShareMapping.KIND_DELEGATION -> R.string.sharing_invite_delegation_detail
                                else -> R.string.sharing_invite_alarm_detail
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(DS.sm))
                    Button(
                        onClick = {
                            accepting = true
                            scope.launch {
                                try {
                                    onAccepted(sharing.accept(info))
                                } catch (e: Exception) {
                                    acceptError = ShareTexts.failure(context, e)
                                } finally {
                                    accepting = false
                                }
                            }
                        },
                        enabled = !accepting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (accepting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.sharing_accept))
                    }
                    TextButton(onClick = onDismiss, enabled = !accepting) { Text(stringResource(R.string.sharing_not_now)) }
                }
            }
        }
    }
    if (acceptError != null) {
        AlertDialog(
            onDismissRequest = { acceptError = null },
            title = { Text(stringResource(R.string.sharing_accept_error)) },
            text = { Text(acceptError.orEmpty()) },
            confirmButton = { TextButton(onClick = { acceptError = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** La tarjeta de la invitación: la foto, el emoji o el símbolo sobre su color. */
@Composable
private fun InviteTile(info: SharingService.InviteInfo) {
    val tint = parseHex(info.tintHex) ?: LocalAccent.current
    val photo = remember(info.photo) {
        info.photo?.let { bytes -> runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        Modifier.padding(top = DS.md).size(96.dp).clip(RoundedCornerShape(DS.radiusXl)).background(tint.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
            !info.emoji.isNullOrEmpty() -> Text(info.emoji, fontSize = 48.sp)
            info.kind == ShareMapping.KIND_DELEGATION -> Icon(Icons.Filled.People, null, tint = tint, modifier = Modifier.size(48.dp))
            else -> Icon(
                SymbolIcons.of(info.symbol?.takeIf { SymbolIcons.has(it) } ?: if (info.kind == ShareMapping.KIND_COUNTDOWN) "calendar" else "bell.fill"),
                null,
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private fun inviteDate(context: Context, info: SharingService.InviteInfo): String {
    val millis = info.date ?: return ""
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val locale = context.resources.configuration.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(zoned)
    return if (info.hasTime) context.getString(R.string.sharing_when, date, DateFormat.getTimeFormat(context).format(Date(millis))) else date
}
