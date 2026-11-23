package com.mathyusolutions.calarmi.ui.alarms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.TonePlayer
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.Weekday
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.components.FullScreenSheet
import com.mathyusolutions.calarmi.ui.components.PlusBadge
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS

/** Cada cuánto se repite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrencePickerSheet(rule: RecurrenceRule, onDismiss: () -> Unit, onChange: (RecurrenceRule) -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(rule) }
    fun set(value: RecurrenceRule) {
        current = value
        onChange(value)
    }
    val kinds = listOf(
        R.string.recurrence_kind_once to RecurrenceRule.Once,
        R.string.recurrence_kind_daily to RecurrenceRule.Daily(1),
        R.string.recurrence_kind_weekly to RecurrenceRule.Weekly(1),
        R.string.recurrence_kind_monthly to RecurrenceRule.Monthly(1),
        R.string.recurrence_kind_yearly to RecurrenceRule.Yearly(1),
    )
    FullScreenSheet(title = stringResource(R.string.editor_repeat), onDismiss = onDismiss) {
        SectionCard {
            kinds.forEachIndexed { index, (label, value) ->
                val selected = current::class == value::class
                SettingRow(
                    title = stringResource(label),
                    divider = index < kinds.lastIndex,
                    onClick = { if (!selected) set(value) },
                    trailing = { if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        val interval = when (val r = current) {
            is RecurrenceRule.Daily -> r.interval
            is RecurrenceRule.Weekly -> r.interval
            is RecurrenceRule.Monthly -> r.interval
            is RecurrenceRule.Yearly -> r.interval
            RecurrenceRule.Once -> null
        }
        if (interval != null) {
            SectionCard(footer = current.summary(context)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recurrence_every), modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (interval > 1) set(withInterval(current, interval - 1)) }) { Text("−", fontSize = 22.sp) }
                    Text(interval.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (interval < 99) set(withInterval(current, interval + 1)) }) { Text("+", fontSize = 22.sp) }
                }
            }
        }
        val weekly = current as? RecurrenceRule.Weekly
        if (weekly != null) {
            SectionCard(title = stringResource(R.string.recurrence_on_days)) {
                FlowRow(Modifier.padding(DS.md), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                    weekOrder().forEach { day ->
                        FilterChip(
                            selected = day in weekly.weekdays,
                            onClick = {
                                val days = if (day in weekly.weekdays) weekly.weekdays - day else weekly.weekdays + day
                                set(weekly.copy(weekdays = days))
                            },
                            label = { Text(stringResource(day.shortRes)) },
                        )
                    }
                }
            }
        }
    }
}

private fun withInterval(rule: RecurrenceRule, n: Int): RecurrenceRule = when (rule) {
    is RecurrenceRule.Daily -> rule.copy(interval = n)
    is RecurrenceRule.Weekly -> rule.copy(interval = n)
    is RecurrenceRule.Monthly -> rule.copy(interval = n)
    is RecurrenceRule.Yearly -> rule.copy(interval = n)
    RecurrenceRule.Once -> rule
}

/** Los días en el orden de la semana del idioma (lunes primero en es/pt). */
fun weekOrder(): List<Weekday> {
    val first = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
    val start = Weekday.of(first)
    return Weekday.entries.let { all ->
        val i = all.indexOf(start)
        all.drop(i) + all.take(i)
    }
}

/** El tono de la alarma. `selection` nulo = sigue el predeterminado. */
@Composable
fun TonePickerSheet(
    selection: AlarmTone?,
    fallback: AlarmTone?,
    onDismiss: () -> Unit,
    onSelect: (AlarmTone?) -> Unit,
) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    val player = remember { TonePlayer(context, container.importedTones) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    var current by remember { mutableStateOf(selection) }
    var imported by remember { mutableStateOf(container.importedTones.all()) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            container.importedTones.import(context, uri)?.let { tone ->
                imported = container.importedTones.all()
                val chosen = AlarmTone.Imported(tone.id)
                current = chosen
                onSelect(chosen)
                player.preview(chosen)
            }
        }
    }
    fun choose(tone: AlarmTone?) {
        current = tone
        onSelect(tone)
        player.preview(tone ?: fallback ?: AlarmTone.DEFAULT)
    }
    FullScreenSheet(title = stringResource(R.string.editor_sound), onDismiss = onDismiss) {
        if (fallback != null) {
            SectionCard(footer = stringResource(R.string.tone_follow_default_footer)) {
                SettingRow(
                    title = stringResource(R.string.tone_follow_default),
                    value = stringResource(fallback.titleRes),
                    divider = false,
                    onClick = { choose(null) },
                    trailing = { if (current == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        SectionCard(title = stringResource(R.string.tone_section_builtin)) {
            AlarmTone.builtins.forEachIndexed { index, tone ->
                SettingRow(
                    title = stringResource(tone.titleRes),
                    icon = SymbolIcons.of(tone.symbol),
                    divider = index < AlarmTone.builtins.lastIndex,
                    onClick = { choose(tone) },
                    trailing = { if (current == tone) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        SectionCard(title = stringResource(R.string.tone_section_imported), footer = stringResource(R.string.tone_import_footer)) {
            imported.forEach { tone ->
                val value = AlarmTone.Imported(tone.id)
                SettingRow(
                    title = tone.name,
                    icon = SymbolIcons.of(value.symbol),
                    onClick = { choose(value) },
                    trailing = {
                        if (current == value) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = {
                            container.importedTones.delete(tone.id)
                            imported = container.importedTones.all()
                            if (current == value) choose(null)
                        }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.tone_import),
                icon = Icons.Filled.FileOpen,
                divider = false,
                onClick = {
                    if (isPlus) importer.launch(arrayOf("audio/*"))
                    else navigator.openPaywall(PlusFeature.CUSTOM_TONES)
                },
                trailing = { if (!isPlus) PlusBadge() },
            )
        }
    }
}

/** Ícono: símbolo, emoji o foto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerSheet(
    tint: Color,
    suggested: List<String>,
    defaultSymbol: String,
    iconKind: ReminderIconKind,
    iconValue: String?,
    photoPath: String?,
    allowsPhoto: Boolean = true,
    onDismiss: () -> Unit,
    onChange: (ReminderIconKind, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    var kind by remember { mutableStateOf(iconKind) }
    var value by remember { mutableStateOf(iconValue) }
    var photo by remember { mutableStateOf(photoPath) }
    var lastSymbol by remember { mutableStateOf(iconValue?.takeIf { !isEmojiIcon(it) } ?: defaultSymbol) }
    var lastEmoji by remember { mutableStateOf(iconValue?.takeIf { isEmojiIcon(it) } ?: "🎉") }
    fun publish() = onChange(kind, value, photo)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            container.photos.import(context, uri)?.let {
                photo = it
                kind = ReminderIconKind.PHOTO
                publish()
            }
        }
    }
    FullScreenSheet(title = stringResource(R.string.editor_icon), onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(DS.lg), contentAlignment = Alignment.Center) {
            ReminderIcon(kind, value, tint, photoPath = photo, fallbackSymbol = defaultSymbol, size = 88.dp, shape = RoundedCornerShape(DS.radiusXl))
        }
        val kinds = buildList {
            add(ReminderIconKind.SYMBOL to R.string.icon_kind_symbol)
            add(ReminderIconKind.EMOJI to R.string.icon_kind_emoji)
            if (allowsPhoto) add(ReminderIconKind.PHOTO to R.string.icon_kind_photo)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = DS.lg)) {
            kinds.forEachIndexed { i, (k, label) ->
                SegmentedButton(
                    selected = kind == k,
                    onClick = {
                        if (kind == ReminderIconKind.SYMBOL && value != null && !isEmojiIcon(value)) lastSymbol = value!!
                        if (kind == ReminderIconKind.EMOJI && isEmojiIcon(value)) lastEmoji = value!!
                        kind = k
                        when (k) {
                            ReminderIconKind.SYMBOL -> value = lastSymbol
                            ReminderIconKind.EMOJI -> value = lastEmoji
                            ReminderIconKind.PHOTO -> if (photo == null) photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        publish()
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(DS.md))
        when (kind) {
            ReminderIconKind.SYMBOL -> {
                val symbols = (suggested + SymbolIcons.pickerSymbols).distinct()
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = DS.lg),
                    horizontalArrangement = Arrangement.spacedBy(DS.sm),
                    verticalArrangement = Arrangement.spacedBy(DS.sm),
                ) {
                    symbols.forEach { symbol ->
                        val selected = value == symbol
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(if (selected) tint else tint.copy(alpha = 0.12f))
                                .clickable {
                                    value = symbol
                                    lastSymbol = symbol
                                    publish()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(SymbolIcons.of(symbol), null, tint = if (selected) Color.White else tint) }
                    }
                }
            }
            ReminderIconKind.EMOJI -> {
                val common = listOf("🎉", "⭐️", "❤️", "🔥", "✅", "⏰", "📅", "💼", "💊", "🎂", "🎁", "✈️")
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = DS.lg), horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
                    common.forEach { emoji ->
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(DS.radiusMd))
                                .border(BorderStroke(if (value == emoji) 2.dp else 0.dp, tint), RoundedCornerShape(DS.radiusMd))
                                .clickable { value = emoji; lastEmoji = emoji; publish() },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { picked ->
                                value = picked.emoji
                                lastEmoji = picked.emoji
                                publish()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(DS.lg),
                )
            }
            ReminderIconKind.PHOTO -> {
                Column(Modifier.fillMaxWidth().padding(DS.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.sm)) {
                    OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(DS.sm))
                        Text(stringResource(if (photo == null) R.string.icon_choose_photo else R.string.icon_change_photo))
                    }
                    if (photo != null) {
                        OutlinedButton(onClick = {
                            photo = null
                            kind = ReminderIconKind.SYMBOL
                            value = lastSymbol
                            publish()
                        }) { Text(stringResource(R.string.icon_remove_photo)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(DS.xxl))
    }
}

/** Campo de texto con el estilo del editor. */
@Composable
fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        textStyle = textStyle,
        shape = RoundedCornerShape(DS.radiusSm),
    )
}

@Suppress("unused")
private val playIcon = Icons.Filled.PlayArrow
