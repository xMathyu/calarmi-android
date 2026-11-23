package com.mathyusolutions.calarmi.ui.alarms

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.toHex
import com.mathyusolutions.calarmi.billing.PlusFeature
import com.mathyusolutions.calarmi.billing.PlusStore
import com.mathyusolutions.calarmi.challenge.ChallengePickerSheet
import com.mathyusolutions.calarmi.domain.RecurrenceEngine
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.AlarmTemplate
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.CountdownPrefill
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.components.CategorySelection
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MAX_LEAD_TIMES = 5

/** El estado que se edita: se guarda como una alarma al cerrar. */
private data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val date: Long = defaultDate(),
    val recurrence: RecurrenceRule = RecurrenceRule.Once,
    val additional: List<AlarmSchedule> = emptyList(),
    val leadTimes: List<AlarmLeadTime> = listOf(AlarmLeadTime.AT_START),
    val category: CategorySelection = CategorySelection.Builtin(ReminderCategory.REMINDER),
    val iconKind: ReminderIconKind = ReminderIconKind.SYMBOL,
    val symbol: String? = null,
    val photoPath: String? = null,
    val tone: AlarmTone? = null,
    val challenge: WakeChallenge? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toReminder(): Reminder = Reminder(
        id = id,
        title = title.trim(),
        notes = notes.trim().ifEmpty { null },
        date = date,
        categoryRaw = ((category as? CategorySelection.Builtin)?.category ?: ReminderCategory.REMINDER).raw,
        customCategoryId = (category as? CategorySelection.Custom)?.id,
        iconKindRaw = iconKind.raw,
        symbolName = symbol,
        photoPath = if (iconKind == ReminderIconKind.PHOTO) photoPath else null,
        recurrence = recurrence,
        leadTimes = leadTimes.distinct().sortedBy { it.seconds },
        additionalSchedules = additional,
        tone = tone,
        challenge = challenge,
        isEnabled = isEnabled,
        createdAt = createdAt,
    )

    companion object {
        fun from(r: Reminder) = Draft(
            id = r.id, isNew = false, title = r.title, notes = r.notes.orEmpty(), date = r.date,
            recurrence = r.recurrence, additional = r.additionalSchedules, leadTimes = r.sortedLeadTimes,
            category = r.customCategoryId?.let { CategorySelection.Custom(it) } ?: CategorySelection.Builtin(r.category),
            iconKind = r.iconKind, symbol = r.symbolName, photoPath = r.photoPath, tone = r.tone,
            challenge = r.challenge, isEnabled = r.isEnabled, createdAt = r.createdAt,
        )

        /** La próxima hora en punto. */
        fun defaultDate(): Long {
            val now = ZonedDateTime.now()
            return now.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(id: String?, template: String?, onDone: () -> Unit, initialDate: Long? = null) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val customs by container.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf<Draft?>(null) }
    var original by remember { mutableStateOf<Draft?>(null) }
    LaunchedEffect(id, template) {
        val initial = when {
            id != null -> container.reminders.byId(id)?.let(Draft::from)
            else -> AlarmTemplate.byId(template)?.let { t ->
                Draft(
                    title = context.getString(t.titleRes),
                    date = t.firstDate(),
                    recurrence = t.recurrence,
                    leadTimes = t.leadTimes,
                    category = CategorySelection.Builtin(t.category),
                    symbol = t.symbol,
                    challenge = t.challenge,
                )
            } ?: Draft(date = initialDate ?: Draft.defaultDate(), leadTimes = listOf(settings.defaultLeadTime))
        }
        if (initial == null) onDone() else {
            draft = initial
            // Una plantilla ya es «trabajo»: cerrar el editor la crea.
            original = if (template != null) null else initial
        }
    }
    val d = draft ?: return

    var showTitleError by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val style = when (val c = d.category) {
        is CategorySelection.Builtin -> CategoryStyle.of(context, c.category)
        is CategorySelection.Custom -> customs.firstOrNull { it.id == c.id }?.let(CategoryStyle::of) ?: CategoryStyle.of(context, ReminderCategory.REMINDER)
    }
    val builtin = (d.category as? CategorySelection.Builtin)?.category ?: ReminderCategory.REMINDER
    val fallbackSymbol = if (style.iconKind == ReminderIconKind.SYMBOL) style.iconValue else builtin.defaultSymbol

    fun update(transform: (Draft) -> Draft) {
        draft = transform(d)
        showTitleError = false
    }

    fun close() {
        val current = draft ?: return onDone()
        val changed = current != original
        if (current.title.isBlank()) {
            // Sin nombre: si no se tocó nada, se descarta; si hay trabajo, se avisa.
            if (!changed || (current.isNew && current.copy(title = "") == original?.copy(title = ""))) onDone()
            else showTitleError = true
            return
        }
        if (!changed) return onDone()
        scope.launch {
            container.reminders.save(current.toReminder())
            onDone()
        }
    }

    BackHandler { close() }

    ScreenScaffold(
        title = stringResource(if (d.isNew) R.string.alarm_new else R.string.alarm_edit),
        onBack = ::close,
        actions = { TextButton(onClick = ::close) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) } },
    ) {
        // Título, notas e ícono
        SectionCard(
            footer = when {
                showTitleError -> stringResource(R.string.editor_title_required)
                d.title.isBlank() -> stringResource(R.string.editor_title_hint)
                else -> null
            },
        ) {
            Row(Modifier.padding(DS.md), verticalAlignment = Alignment.Top) {
                Box(Modifier.clickable { sheet = "icon" }) {
                    ReminderIcon(d.iconKind, d.symbol, style.color, photoPath = d.photoPath, fallbackSymbol = fallbackSymbol, size = 56.dp, shape = RoundedCornerShape(DS.radiusMd))
                    Icon(Icons.Filled.Edit, stringResource(R.string.editor_change_icon), tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).padding(2.dp))
                }
                Spacer(Modifier.width(DS.md))
                Column(verticalArrangement = Arrangement.spacedBy(DS.sm)) {
                    EditorTextField(d.title, { v -> update { it.copy(title = v) } }, stringResource(R.string.editor_title_placeholder), isError = showTitleError, textStyle = MaterialTheme.typography.titleMedium)
                    EditorTextField(d.notes, { v -> update { it.copy(notes = v) } }, stringResource(R.string.editor_notes_placeholder), singleLine = false)
                }
            }
        }

        // Horario principal
        SectionCard(title = if (d.additional.isEmpty()) stringResource(R.string.editor_when) else stringResource(R.string.editor_schedule_n, 1)) {
            ScheduleRows(
                key = "main",
                date = d.date,
                recurrence = d.recurrence,
                openSheet = { sheet = it },
            )
        }
        d.additional.forEachIndexed { index, schedule ->
            SectionCard(title = stringResource(R.string.editor_schedule_n, index + 2)) {
                ScheduleRows(key = schedule.id, date = schedule.date, recurrence = schedule.recurrence, openSheet = { sheet = it })
                SettingRow(
                    title = stringResource(R.string.editor_remove_schedule),
                    icon = Icons.Filled.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    divider = false,
                    onClick = { update { it.copy(additional = it.additional.filter { s -> s.id != schedule.id }) } },
                )
            }
        }
        // Avisos y horarios extra
        SectionCard(
            footer = stringResource(if (d.leadTimes.size > 1) R.string.editor_leads_footer else R.string.editor_schedules_footer),
        ) {
            d.leadTimes.forEachIndexed { index, lead ->
                LeadTimeRow(
                    label = if (index == 0) stringResource(R.string.editor_alert) else stringResource(R.string.editor_alert_n, index + 1),
                    value = lead,
                    excluded = d.leadTimes.toSet() - lead,
                    canRemove = d.leadTimes.size > 1,
                    onChange = { new -> update { it.copy(leadTimes = it.leadTimes.map { l -> if (l == lead) new else l }.distinct()) } },
                    onRemove = { update { it.copy(leadTimes = it.leadTimes - lead) } },
                )
            }
            if (d.leadTimes.size < MAX_LEAD_TIMES) {
                var adding by remember { mutableStateOf(false) }
                Box {
                    SettingRow(title = stringResource(R.string.editor_add_alert), icon = Icons.Filled.AddCircle, onClick = { adding = true })
                    DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                        AlarmLeadTime.entries.filter { it !in d.leadTimes }.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.titleRes)) }, onClick = {
                                adding = false
                                update { it.copy(leadTimes = (it.leadTimes + option).sortedBy { l -> l.seconds }) }
                            })
                        }
                    }
                }
            }
            SettingRow(
                title = stringResource(R.string.editor_add_schedule),
                icon = Icons.Filled.EditCalendar,
                divider = false,
                onClick = {
                    update { it.copy(additional = it.additional + AlarmSchedule(date = it.date + 24 * 3600 * 1000L, recurrence = it.recurrence)) }
                },
            )
        }

        // Sonido
        val effectiveTone = d.tone ?: settings.alarmTone
        SectionCard(footer = if (d.tone == null) stringResource(R.string.editor_tone_default_footer) else null) {
            SettingRow(
                title = stringResource(R.string.editor_sound),
                icon = SymbolIcons.of(effectiveTone.symbol),
                value = toneTitle(context, effectiveTone),
                showChevron = true,
                divider = false,
                onClick = { sheet = "tone" },
            )
        }

        // Reto
        val challenge = d.challenge
        SectionCard(
            footer = when {
                challenge == null -> null
                !challenge.isReady -> stringResource(R.string.editor_challenge_missing_code)
                else -> stringResource(R.string.editor_challenge_footer)
            },
        ) {
            SettingRow(
                title = stringResource(R.string.editor_challenge),
                icon = Icons.Filled.Extension,
                value = challenge?.let { stringResource(it.kind.titleRes) + " · " + stringResource(it.difficulty.titleRes) } ?: stringResource(R.string.editor_challenge_none),
                showChevron = true,
                divider = false,
                onClick = { sheet = "challenge" },
            )
        }

        // Categoría
        SectionCard(title = stringResource(R.string.editor_category)) {
            var open by remember { mutableStateOf(false) }
            Box {
                SettingRow(
                    title = style.title,
                    icon = if (style.iconKind == ReminderIconKind.SYMBOL) SymbolIcons.of(style.iconValue) else Icons.Filled.Sell,
                    iconTint = style.color,
                    showChevron = true,
                    divider = false,
                    onClick = { open = true },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ReminderCategory.displayOrder.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(stringResource(category.titleRes)) },
                            leadingIcon = { Icon(SymbolIcons.of(category.defaultSymbol), null, tint = category.tint) },
                            onClick = {
                                open = false
                                update { cur ->
                                    // El ícono sigue a la categoría mientras no se haya elegido uno propio.
                                    val followsCategory = cur.iconKind == ReminderIconKind.SYMBOL &&
                                        (cur.symbol == null || ReminderCategory.entries.any { it.defaultSymbol == cur.symbol })
                                    cur.copy(category = CategorySelection.Builtin(category), symbol = if (followsCategory) category.defaultSymbol else cur.symbol)
                                }
                            },
                        )
                    }
                    if (customs.isNotEmpty()) HorizontalDivider()
                    customs.forEach { custom ->
                        val s = CategoryStyle.of(custom)
                        DropdownMenuItem(
                            text = { Text(custom.name) },
                            leadingIcon = { ReminderIcon(s.iconKind, s.iconValue, s.color, size = 24.dp) },
                            onClick = {
                                open = false
                                update { it.copy(category = CategorySelection.Custom(custom.id)) }
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.categories_manage)) }, onClick = { open = false; navigator.openCategories() })
                }
            }
        }

        // Activa
        SectionCard(footer = stringResource(if (d.isEnabled) R.string.editor_enabled_footer_on else R.string.editor_enabled_footer_off)) {
            SettingRow(
                title = stringResource(R.string.editor_enabled),
                icon = if (d.isEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                iconTint = style.color,
                divider = false,
                trailing = { Switch(checked = d.isEnabled, onCheckedChange = { v -> update { it.copy(isEnabled = v) } }) },
            )
        }

        // Cuenta regresiva (una vez, mensual, anual)
        val next = nextRing(d)
        if (next != null && (next.second is RecurrenceRule.Once || next.second is RecurrenceRule.Monthly || next.second is RecurrenceRule.Yearly)) {
            SectionCard(footer = stringResource(R.string.editor_countdown_footer)) {
                SettingRow(
                    title = stringResource(R.string.editor_create_countdown),
                    icon = Icons.Filled.HourglassBottom,
                    divider = false,
                    onClick = {
                        scope.launch {
                            val count = container.countdowns.countdowns.first().size
                            if (count >= PlusStore.FREE_COUNTDOWN_LIMIT && !container.plus.isPlus.value) {
                                navigator.openPaywall(PlusFeature.UNLIMITED_COUNTDOWNS)
                                return@launch
                            }
                            val usesPhoto = d.iconKind == ReminderIconKind.PHOTO && d.photoPath != null
                            navigator.newCountdown(
                                CountdownPrefill(
                                    title = d.title.trim(),
                                    date = next.first,
                                    includesTime = true,
                                    repeatsYearly = next.second == RecurrenceRule.Yearly(1),
                                    colorHex = style.color.toHex(),
                                    iconKindRaw = when {
                                        usesPhoto -> ReminderIconKind.PHOTO.raw
                                        isEmojiIcon(d.symbol) -> ReminderIconKind.EMOJI.raw
                                        else -> ReminderIconKind.SYMBOL.raw
                                    },
                                    iconValue = d.symbol ?: fallbackSymbol,
                                    photoPath = if (usesPhoto) d.photoPath else null,
                                ),
                            )
                        }
                    },
                )
            }
        }

        if (!d.isNew) {
            SectionCard {
                SettingRow(
                    title = stringResource(R.string.alarm_delete),
                    icon = Icons.Filled.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    divider = false,
                    onClick = { confirmDelete = true },
                )
            }
        }
        Spacer(Modifier.height(DS.xxxl))
    }

    // Hojas
    when {
        sheet == "icon" -> IconPickerSheet(
            tint = style.color,
            suggested = builtin.suggestedSymbols,
            defaultSymbol = fallbackSymbol,
            iconKind = d.iconKind,
            iconValue = d.symbol,
            photoPath = d.photoPath,
            onDismiss = { sheet = null },
            onChange = { kind, value, photo -> draft = draft?.copy(iconKind = kind, symbol = value, photoPath = photo) },
        )
        sheet == "tone" -> TonePickerSheet(d.tone, settings.alarmTone, onDismiss = { sheet = null }, onSelect = { t -> draft = draft?.copy(tone = t) })
        sheet == "challenge" -> ChallengePickerSheet(
            selection = d.challenge,
            alarmTitle = d.title,
            onDismiss = { sheet = null },
            onSelect = { c -> draft = draft?.copy(challenge = c) },
        )
        sheet?.startsWith("repeat:") == true -> {
            val key = sheet!!.removePrefix("repeat:")
            val rule = if (key == "main") d.recurrence else d.additional.firstOrNull { it.id == key }?.recurrence ?: RecurrenceRule.Once
            RecurrencePickerSheet(rule, onDismiss = { sheet = null }) { new ->
                draft = draft?.let { cur ->
                    if (key == "main") cur.copy(recurrence = new)
                    else cur.copy(additional = cur.additional.map { if (it.id == key) it.copy(recurrence = new) else it })
                }
            }
        }
        sheet?.startsWith("date:") == true -> {
            val key = sheet!!.removePrefix("date:")
            val current = if (key == "main") d.date else d.additional.firstOrNull { it.id == key }?.date ?: d.date
            DateDialog(current, onDismiss = { sheet = null }) { new -> draft = draft?.withDate(key, new) }
        }
        sheet?.startsWith("time:") == true -> {
            val key = sheet!!.removePrefix("time:")
            val current = if (key == "main") d.date else d.additional.firstOrNull { it.id == key }?.date ?: d.date
            TimeDialog(current, onDismiss = { sheet = null }) { new -> draft = draft?.withDate(key, new) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.alarm_delete_confirm_title)) },
            text = { Text(d.title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.reminders.delete(d.id)
                        onDone()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun Draft.withDate(key: String, date: Long): Draft =
    if (key == "main") copy(date = date) else copy(additional = additional.map { if (it.id == key) it.copy(date = date) else it })

/** La próxima vez que suena (y la regla de ese horario). */
private fun nextRing(d: Draft): Pair<Long, RecurrenceRule>? =
    (listOf(d.date to d.recurrence) + d.additional.map { it.date to it.recurrence })
        .mapNotNull { (base, rule) -> RecurrenceEngine.nextOccurrences(rule, base, 1).firstOrNull()?.let { it to rule } }
        .minByOrNull { it.first }

fun toneTitle(context: android.content.Context, tone: AlarmTone): String =
    if (tone is AlarmTone.Imported) CalarmiApp.container(context).importedTones.name(tone.id) ?: context.getString(tone.titleRes)
    else context.getString(tone.titleRes)

@Composable
private fun ScheduleRows(key: String, date: Long, recurrence: RecurrenceRule, openSheet: (String) -> Unit) {
    val context = LocalContext.current
    val zoned = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
    val showsDate = when (recurrence) {
        is RecurrenceRule.Daily -> recurrence.interval > 1
        is RecurrenceRule.Weekly -> recurrence.weekdays.isEmpty() || recurrence.interval > 1
        else -> true
    }
    if (showsDate) {
        SettingRow(
            title = stringResource(if (recurrence.isRecurring) R.string.editor_from else R.string.editor_date),
            icon = Icons.Filled.CalendarMonth,
            value = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(zoned),
            onClick = { openSheet("date:$key") },
        )
    }
    SettingRow(
        title = stringResource(R.string.editor_time),
        icon = Icons.Filled.Schedule,
        value = DateFormat.getTimeFormat(context).format(Date(date)),
        onClick = { openSheet("time:$key") },
    )
    val never = RecurrenceEngine.nextOccurrences(recurrence, date, 1).isEmpty()
    SettingRow(
        title = stringResource(R.string.editor_repeat),
        icon = Icons.Filled.Repeat,
        value = recurrence.summary(context),
        showChevron = true,
        divider = never,
        onClick = { openSheet("repeat:$key") },
    )
    if (never) {
        Row(Modifier.padding(horizontal = DS.lg, vertical = DS.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(DS.xs))
            Text(stringResource(R.string.editor_past_warning), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9500))
        }
    }
}

@Composable
private fun LeadTimeRow(
    label: String,
    value: AlarmLeadTime,
    excluded: Set<AlarmLeadTime>,
    canRemove: Boolean,
    onChange: (AlarmLeadTime) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            title = label,
            icon = Icons.Filled.Notifications,
            value = stringResource(value.titleRes),
            onClick = { open = true },
            trailing = {
                if (canRemove) {
                    androidx.compose.material3.IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AlarmLeadTime.entries.filter { it !in excluded }.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.titleRes)) }, onClick = { open = false; onChange(option) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(current).atZone(zone)
    // El DatePicker de Material trabaja en UTC a medianoche.
    val state = rememberDatePickerState(initialSelectedDateMillis = zoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(ZonedDateTime.of(day, zoned.toLocalTime(), zone).toInstant().toEpochMilli())
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(current).atZone(zone)
    val state = rememberTimePickerState(zoned.hour, zoned.minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                var day: LocalDate = zoned.toLocalDate()
                val time = LocalTime.of(state.hour, state.minute)
                var picked = ZonedDateTime.of(day, time, zone)
                onPick(picked.toInstant().toEpochMilli())
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state) },
    )
}
