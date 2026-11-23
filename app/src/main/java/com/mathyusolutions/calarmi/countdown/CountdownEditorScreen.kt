package com.mathyusolutions.calarmi.countdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.toHex
import com.mathyusolutions.calarmi.data.Settings
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CountdownPrefill
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.isEmojiIcon
import com.mathyusolutions.calarmi.ui.alarms.EditorTextField
import com.mathyusolutions.calarmi.ui.alarms.IconPickerSheet
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.launch

/** Lo que se edita, aparte del modelo: cancelar no toca la base. */
private data class Draft(
    val title: String = "",
    val notes: String = "",
    val date: Long,
    val includesTime: Boolean = false,
    val repeatsYearly: Boolean = false,
    val colorHex: String = CountdownEvent.DEFAULT_COLOR_HEX,
    val iconKind: ReminderIconKind = ReminderIconKind.EMOJI,
    val iconValue: String = CountdownEvent.DEFAULT_EMOJI,
    val photoPath: String? = null,
) {
    val trimmedTitle: String get() = title.trim()

    fun content(id: String) = CountdownCardContent(
        id = id,
        title = trimmedTitle,
        date = date,
        includesTime = includesTime,
        repeatsYearly = repeatsYearly,
        colorHex = colorHex,
        iconKind = iconKind,
        iconValue = iconValue,
        photoPath = photoPath,
    )

    companion object {
        fun from(event: CountdownEvent) = Draft(
            title = event.title,
            notes = event.notes.orEmpty(),
            date = event.date,
            includesTime = event.includesTime,
            repeatsYearly = event.repeatsYearly,
            colorHex = event.colorHex,
            iconKind = event.iconKind,
            iconValue = event.iconValue,
            photoPath = event.photoPath,
        )

        /** Para sobrevivir a girar la pantalla (o a que el sistema cierre la app). */
        val Saver = listSaver<Draft?, Any?>(
            save = { d ->
                if (d == null) emptyList()
                else listOf(d.title, d.notes, d.date, d.includesTime, d.repeatsYearly, d.colorHex, d.iconKind.raw, d.iconValue, d.photoPath)
            },
            restore = { l ->
                if (l.size < 9) null
                else Draft(
                    title = l[0] as String,
                    notes = l[1] as String,
                    date = l[2] as Long,
                    includesTime = l[3] as Boolean,
                    repeatsYearly = l[4] as Boolean,
                    colorHex = l[5] as String,
                    iconKind = ReminderIconKind.fromRaw(l[6] as Int),
                    iconValue = l[7] as String,
                    photoPath = l[8] as String?,
                )
            },
        )
    }
}

/**
 * Crear o editar una cuenta regresiva: nombre, fecha (con hora opcional), si
 * vuelve cada año, y su imagen —emoji, símbolo o una foto de fondo— y color.
 * Arriba, la tarjeta tal como va a quedar en la lista.
 *
 * Nueva (`id` nulo, opcionalmente con `prefill`) o edición. `onDone` vuelve atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEditorScreen(id: String?, prefill: CountdownPrefill?, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val haptics = LocalHapticFeedback.current

    val draftId = rememberSaveable(id) { id ?: UUID.randomUUID().toString() }
    var draft by rememberSaveable(stateSaver = Draft.Saver) { mutableStateOf<Draft?>(null) }
    var initial by rememberSaveable(stateSaver = Draft.Saver) { mutableStateOf<Draft?>(null) }
    // Fotos creadas en esta edición (elegidas o copiadas de la alarma): las
    // que no queden guardadas se borran al salir, para no dejar archivos sueltos.
    var created by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var original by remember { mutableStateOf<CountdownEvent?>(null) }

    LaunchedEffect(id) {
        if (id != null) {
            val event = container.countdowns.byId(id)
            if (event == null) {
                onDone()
                return@LaunchedEffect
            }
            original = event
            if (draft == null) {
                draft = Draft.from(event)
                initial = draft
            }
        } else if (draft == null) {
            val start = if (prefill != null) {
                // La foto de la alarma se COPIA: si compartieran archivo, borrar
                // la alarma dejaría a la cuenta sin foto (y al revés).
                val copy = prefill.photoPath?.let { CountdownPhoto.copy(context, it) }
                if (copy != null) created = ArrayList(created + copy)
                val wantsPhoto = ReminderIconKind.fromRaw(prefill.iconKindRaw) == ReminderIconKind.PHOTO
                Draft(
                    title = prefill.title,
                    date = prefill.date,
                    includesTime = prefill.includesTime,
                    repeatsYearly = prefill.repeatsYearly,
                    colorHex = prefill.colorHex,
                    iconKind = when {
                        wantsPhoto && copy != null -> ReminderIconKind.PHOTO
                        wantsPhoto -> if (isEmojiIcon(prefill.iconValue)) ReminderIconKind.EMOJI else ReminderIconKind.SYMBOL
                        else -> ReminderIconKind.fromRaw(prefill.iconKindRaw)
                    },
                    iconValue = prefill.iconValue,
                    photoPath = copy,
                )
            } else {
                Draft(date = CountdownDefaults.date())
            }
            draft = start
            // Lo que llega de una alarma ya es «trabajo»: salir pregunta antes de perderlo.
            initial = if (prefill != null) null else start
        }
    }
    val d = draft ?: return
    val isNew = id == null

    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val color = parseHex(d.colorHex) ?: Color(0xFFFF2D55)

    // Sobre el valor actual, no el de esta composición: dos cambios en el mismo
    // cuadro (escribir rápido) no se pisan.
    fun update(transform: (Draft) -> Draft) {
        draft = draft?.let(transform)
    }

    fun deletePhotos(names: Collection<String>) {
        if (names.isEmpty()) return
        val photos = container.photos
        container.scope.launch { names.forEach(photos::delete) }
    }

    fun cancel() {
        deletePhotos(created)
        created = ArrayList()
        onDone()
    }

    fun requestClose() {
        if (draft != initial) confirmDiscard = true else cancel()
    }

    fun save() {
        val current = draft ?: return
        if (current.trimmedTitle.isEmpty()) return
        // La foto solo se guarda si es el ícono elegido: si no, ocuparía espacio sin verse nunca.
        val photo = if (current.iconKind == ReminderIconKind.PHOTO) current.photoPath else null
        val base = original ?: CountdownEvent(id = draftId)
        val event = base.copy(
            title = current.trimmedTitle,
            notes = current.notes.trim().ifEmpty { null },
            date = CountdownDefaults.normalized(current.date, current.includesTime),
            includesTime = current.includesTime,
            repeatsYearly = current.repeatsYearly,
            colorHex = current.colorHex,
            iconKindRaw = current.iconKind.raw,
            iconValue = current.iconValue,
            photoPath = photo,
        )
        // Las fotos que quedaron atrás: las elegidas y descartadas, y la anterior si se cambió.
        val leftovers = created.filter { it != photo } + listOfNotNull(original?.photoPath?.takeIf { it != photo })
        created = ArrayList()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        container.scope.launch {
            container.countdowns.save(event)
            leftovers.forEach(container.photos::delete)
        }
        onDone()
    }

    BackHandler { requestClose() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.countdowns_new_event else R.string.countdowns_edit_event)) },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel)) }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = d.trimmedTitle.isNotEmpty()) {
                        Text(stringResource(if (isNew) R.string.countdowns_create else R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Vista previa: la tarjeta tal cual quedará en la lista.
            CountdownCard(d.content(draftId), now = System.currentTimeMillis(), modifier = Modifier.padding(horizontal = DS.lg, vertical = DS.sm))

            // Nombre y notas
            val focus = remember { FocusRequester() }
            SectionCard(footer = if (isNew && prefill == null) stringResource(R.string.countdowns_name_hint) else null) {
                Column(Modifier.padding(DS.md), verticalArrangement = Arrangement.spacedBy(DS.sm)) {
                    EditorTextField(
                        d.title, { v -> update { it.copy(title = v) } },
                        stringResource(R.string.countdowns_name_placeholder),
                        modifier = Modifier.focusRequester(focus),
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                    EditorTextField(d.notes, { v -> update { it.copy(notes = v) } }, stringResource(R.string.editor_notes_placeholder), singleLine = false)
                }
            }
            // Una que llega ya con nombre no necesita el teclado encima.
            LaunchedEffect(Unit) { if (isNew && d.title.isBlank()) runCatching { focus.requestFocus() } }

            // Fecha, hora y repetición
            SectionCard(
                footer = when {
                    d.repeatsYearly -> stringResource(R.string.countdowns_repeat_footer)
                    d.includesTime -> stringResource(R.string.countdowns_time_footer)
                    else -> null
                },
            ) {
                SettingRow(
                    title = stringResource(R.string.editor_date),
                    icon = Icons.Filled.CalendarMonth,
                    iconTint = color,
                    value = CountdownFormat.date(context, d.date, includesTime = false),
                    onClick = { sheet = "date" },
                )
                SettingRow(
                    title = stringResource(R.string.editor_time),
                    icon = Icons.Filled.Schedule,
                    iconTint = color,
                    onClick = { update { it.copy(includesTime = !it.includesTime) } },
                    trailing = { Switch(checked = d.includesTime, onCheckedChange = { v -> update { it.copy(includesTime = v) } }) },
                )
                if (d.includesTime) {
                    SettingRow(
                        title = stringResource(R.string.countdowns_at),
                        value = CountdownFormat.time(context, d.date),
                        onClick = { sheet = "time" },
                    )
                }
                SettingRow(
                    title = stringResource(R.string.countdowns_repeat_yearly),
                    icon = Icons.Filled.Repeat,
                    iconTint = color,
                    divider = false,
                    onClick = { update { it.copy(repeatsYearly = !it.repeatsYearly) } },
                    trailing = { Switch(checked = d.repeatsYearly, onCheckedChange = { v -> update { it.copy(repeatsYearly = v) } }) },
                )
            }

            // Imagen
            SectionCard(
                title = stringResource(R.string.countdowns_image),
                footer = if (d.iconKind == ReminderIconKind.PHOTO) stringResource(R.string.countdowns_photo_footer) else null,
            ) {
                SettingRow(
                    title = stringResource(R.string.editor_change_icon),
                    showChevron = true,
                    divider = false,
                    onClick = { sheet = "icon" },
                    trailing = {
                        ReminderIcon(
                            d.iconKind, d.iconValue, color,
                            photoPath = d.photoPath,
                            fallbackSymbol = "calendar",
                            size = 36.dp,
                            shape = RoundedCornerShape(DS.radiusSm),
                        )
                    },
                )
            }

            // Color (con foto no se ve: no se ofrece)
            if (d.iconKind != ReminderIconKind.PHOTO) {
                SectionCard(title = stringResource(R.string.countdowns_color)) {
                    ColorPalette(selected = d.colorHex) { hex ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        update { it.copy(colorHex = hex) }
                    }
                    val isCustom = Settings.accentPresets.none { it.equals(d.colorHex, ignoreCase = true) }
                    SettingRow(
                        title = stringResource(R.string.countdowns_custom_color),
                        icon = Icons.Filled.Colorize,
                        iconTint = color,
                        divider = false,
                        onClick = { sheet = "color" },
                        trailing = { if (isCustom) Box(Modifier.size(22.dp).background(color, CircleShape)) },
                    )
                }
            }

            if (!isNew) {
                SectionCard {
                    SettingRow(
                        title = stringResource(R.string.countdowns_delete_event),
                        icon = Icons.Filled.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        divider = false,
                        onClick = { confirmDelete = true },
                    )
                }
            }
            Spacer(Modifier.height(DS.xxxl))
        }
    }

    when (sheet) {
        "icon" -> IconPickerSheet(
            tint = color,
            suggested = CountdownDefaults.suggestedSymbols,
            defaultSymbol = "star.fill",
            iconKind = d.iconKind,
            iconValue = d.iconValue,
            photoPath = d.photoPath,
            allowsPhoto = true,
            onDismiss = { sheet = null },
            onChange = { kind, value, photo ->
                if (photo != null && photo != original?.photoPath && photo !in created) created = ArrayList(created + photo)
                draft = draft?.let { cur -> cur.copy(iconKind = kind, iconValue = value ?: cur.iconValue, photoPath = photo) }
            },
        )
        "date" -> DateDialog(d.date, onDismiss = { sheet = null }) { new -> update { it.copy(date = new) } }
        "time" -> TimeDialog(d.date, onDismiss = { sheet = null }) { new -> update { it.copy(date = new) } }
        "color" -> CustomColorDialog(color, onDismiss = { sheet = null }) { picked -> update { it.copy(colorHex = picked.toHex()) } }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.countdowns_delete_confirm)) },
            text = { Text(d.trimmedTitle) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deletePhotos(created)
                    created = ArrayList()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Primero se cierra el editor; el detalle, al ver que ya no
                    // existe, vuelve solo a la lista.
                    onDone()
                    if (id != null) container.scope.launch { container.countdowns.delete(id) }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.countdowns_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    cancel()
                }) { Text(stringResource(R.string.countdowns_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.countdowns_keep_editing)) } },
        )
    }
}

/** Los colores de la app en filas de cinco (los mismos que el acento de Ajustes). */
@Composable
private fun ColorPalette(selected: String, onSelect: (String) -> Unit) {
    val presets = Settings.accentPresets
    Column(Modifier.padding(DS.md), verticalArrangement = Arrangement.spacedBy(DS.md)) {
        presets.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { hex ->
                    val isSelected = hex.equals(selected, ignoreCase = true)
                    val swatch = parseHex(hex) ?: Color.Gray
                    Box(
                        Modifier
                            .size(if (isSelected) 38.dp else 34.dp)
                            .background(swatch, CircleShape)
                            .border(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), CircleShape)
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Un color propio: tono, saturación y brillo (el `ColorPicker` de iOS). */
@Composable
private fun CustomColorDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countdowns_custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DS.xs)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(color, RoundedCornerShape(DS.radiusMd)))
                Spacer(Modifier.height(DS.sm))
                Text(stringResource(R.string.countdowns_hue), style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.countdowns_saturation), style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text(stringResource(R.string.countdowns_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { brightness = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(color)
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    val state = rememberTimePickerState(zoned.hour, zoned.minute, android.text.format.DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(ZonedDateTime.of(zoned.toLocalDate(), LocalTime.of(state.hour, state.minute), zone).toInstant().toEpochMilli())
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state) },
    )
}
