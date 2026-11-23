package com.mathyusolutions.calarmi.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.HeroIcon
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.LocalAccent

/**
 * Pestaña «Asistente» («Díselo y listo»): crear, buscar, apagar y borrar
 * alarmas conversando, por texto o voz. Todo ocurre en el teléfono: el parser
 * siempre, y Gemini Nano cuando el teléfono lo tiene.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen() {
    val vm: AssistantViewModel = viewModel()
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val modelStatus by vm.modelStatus.collectAsStateWithLifecycle()
    val reminders by container.reminders.reminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val customs by container.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val appLocale = LocalConfiguration.current.locales[0]
    var input by rememberSaveable { mutableStateOf("") }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        vm.send(text, appLocale)
        input = ""
    }

    // Dictado del sistema: no pide permiso de micrófono a la app, y se pide sin conexión.
    val speechPrompt = stringResource(R.string.assistant_speak_prompt)
    val speechUnavailable = stringResource(R.string.assistant_speech_unavailable)
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        // Como en iOS: lo dictado queda en el campo para revisarlo antes de enviar.
        if (!heard.isNullOrEmpty()) input = if (input.isBlank()) heard else "$input $heard"
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, appLocale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, speechUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // El teclado tapa la barra de pestañas; solo hay que subir lo que sobresale de ella.
    val density = LocalDensity.current
    val rootView = LocalView.current
    var gapBelow by remember { mutableIntStateOf(0) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboardLift = with(density) { (imeBottom - gapBelow).coerceAtLeast(0).toDp() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.onGloballyPositioned { gapBelow = (rootView.height - it.boundsInRoot().bottom).toInt().coerceAtLeast(0) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_assistant), fontWeight = FontWeight.Bold) },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { vm.reset() }) {
                            Icon(Icons.Filled.RestartAlt, stringResource(R.string.assistant_new_chat))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(bottom = keyboardLift).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyChat(enabled = !busy, onPick = ::send)
                } else {
                    val byId = remember(reminders) { reminders.associateBy { it.id } }
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size, busy) {
                        val last = messages.size + (if (busy) 1 else 0) - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = DS.md),
                        verticalArrangement = Arrangement.spacedBy(DS.md),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            if (message.fromUser) {
                                UserBubble(message.text)
                            } else {
                                AssistantBubble(
                                    message = message,
                                    live = byId,
                                    customs = customs,
                                    quickEnabled = !busy && message.id == messages.last().id,
                                    onCard = { navigator.openReminder(it) },
                                    onQuick = ::send,
                                )
                            }
                        }
                        if (busy) item(key = "typing") { TypingIndicator() }
                    }
                }
            }
            ModeNote(modelStatus)
            InputBar(
                value = input,
                onValueChange = { input = it },
                busy = busy,
                onSend = { send(input) },
                onMic = ::listen,
            )
        }
    }
}

// region Vacío

@Composable
private fun EmptyChat(enabled: Boolean, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DS.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(DS.xxl))
        HeroIcon(Icons.Filled.AutoAwesome, size = DS.avatarXl)
        Spacer(Modifier.height(DS.lg))
        Text(stringResource(R.string.assistant_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(DS.xs))
        Text(
            stringResource(R.string.assistant_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DS.lg),
        )
        Spacer(Modifier.height(DS.xl))
        val suggestions: List<Pair<ImageVector, Int>> = listOf(
            Icons.Filled.CalendarMonth to R.string.assistant_suggestion_today,
            Icons.Filled.Medication to R.string.assistant_suggestion_pill,
            Icons.Filled.Cake to R.string.assistant_suggestion_birthday,
            Icons.Filled.Search to R.string.assistant_suggestion_month,
            Icons.Filled.NotificationsActive to R.string.assistant_suggestion_tomorrow,
        )
        Column(verticalArrangement = Arrangement.spacedBy(DS.sm)) {
            suggestions.forEach { (icon, res) ->
                val text = stringResource(res)
                SuggestionChip(icon, text, enabled) { onPick(text) }
            }
        }
        Spacer(Modifier.height(DS.xl))
    }
}

@Composable
private fun SuggestionChip(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = DS.md, vertical = DS.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(DS.md))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}

// endregion

// region Globos

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(start = DS.xxxl + DS.lg, end = DS.lg), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(LocalAccent.current)
                .padding(horizontal = DS.md, vertical = DS.sm),
        )
    }
}

@Composable
private fun AssistantAvatar() {
    val accent = LocalAccent.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(
    message: ChatMessage,
    live: Map<String, Reminder>,
    customs: List<com.mathyusolutions.calarmi.model.CustomCategory>,
    quickEnabled: Boolean,
    onCard: (String) -> Unit,
    onQuick: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = DS.lg, end = DS.xxl), verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(DS.sm))
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(DS.sm)) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = DS.md, vertical = DS.sm),
            )
            message.cards.forEach { snapshot ->
                AlarmCard(
                    reminder = live[snapshot.id] ?: snapshot,
                    exists = live.containsKey(snapshot.id),
                    customs = customs,
                    onClick = { onCard(snapshot.id) },
                )
            }
            if (message.quickReplies.isNotEmpty() && quickEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                    message.quickReplies.forEach { label ->
                        OutlinedButton(onClick = { onQuick(label) }) { Text(label) }
                    }
                }
            }
        }
    }
}

/** La alarma creada, cambiada o encontrada: ícono, nombre y próxima vez. Al tocarla, el editor. */
@Composable
private fun AlarmCard(
    reminder: Reminder,
    exists: Boolean,
    customs: List<com.mathyusolutions.calarmi.model.CustomCategory>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val style = CategoryStyle.of(context, reminder, customs)
    val next = remember(reminder) { if (reminder.isEnabled) reminder.nextOccurrence() else null }
    val subtitle = when {
        !exists -> stringResource(R.string.assistant_card_deleted)
        !reminder.isEnabled -> stringResource(R.string.assistant_card_off)
        next == null -> stringResource(R.string.assistant_card_expired)
        else -> AssistantReplies.cardSubtitle(context, next)
    }
    val openLabel = stringResource(R.string.assistant_open_alarm)
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(enabled = exists, onClick = onClick)
                .semantics { if (exists) contentDescription = openLabel }
                .padding(horizontal = DS.md, vertical = DS.sm + 2.dp)
                .alpha(if (exists && reminder.isEnabled && next != null) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReminderIcon(
                iconKind = reminder.iconKind,
                iconValue = reminder.symbolName,
                tint = style.color,
                photoPath = reminder.photoPath,
                fallbackSymbol = if (style.iconKind == ReminderIconKind.SYMBOL) style.iconValue else "bell.fill",
                size = DS.avatarSm,
            )
            Spacer(Modifier.width(DS.md))
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (exists) null else TextDecoration.LineThrough,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (next != null && exists) style.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (exists) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val label = stringResource(R.string.assistant_typing)
    Row(
        Modifier.padding(start = DS.lg).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantAvatar()
        Spacer(Modifier.width(DS.sm))
        Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = DS.md, vertical = DS.md),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

// endregion

// region Barra inferior

/** Qué motor responde: la IA del teléfono o el modo básico. Nada sale del teléfono en ninguno. */
@Composable
private fun ModeNote(status: OnDeviceModel.Status) {
    val (icon, text) = when (status) {
        OnDeviceModel.Status.AVAILABLE -> Icons.Filled.AutoAwesome to R.string.assistant_mode_ai
        OnDeviceModel.Status.DOWNLOADING -> Icons.Filled.Downloading to R.string.assistant_mode_downloading
        else -> Icons.Filled.Lock to R.string.assistant_mode_basic
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(DS.xs))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val accent = LocalAccent.current
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DS.md, vertical = DS.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!busy) onSend() }),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            val canSend = value.isNotBlank()
            // Como en iOS: micrófono con el campo vacío, enviar cuando hay texto.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (canSend && busy) 0.5f else 1f))
                    .clickable(enabled = !(canSend && busy)) { if (canSend) onSend() else onMic() },
                contentAlignment = Alignment.Center,
            ) {
                if (canSend) {
                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.assistant_send), tint = Color.White)
                } else {
                    Icon(Icons.Filled.Mic, stringResource(R.string.assistant_speak), tint = Color.White)
                }
            }
        }
    }
}

// endregion
