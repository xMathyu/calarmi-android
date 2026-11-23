package com.mathyusolutions.calarmi.ui.alarms

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.AlarmPermissions
import com.mathyusolutions.calarmi.domain.nextOccurrence
import com.mathyusolutions.calarmi.model.AlarmTemplate
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.EmptyState
import com.mathyusolutions.calarmi.ui.components.HeroIcon
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Group(val title: String, val items: List<Pair<Reminder, Long?>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val reminders by container.reminders.reminders.collectAsStateWithLifecycle(initialValue = null)
    val customs by container.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(emptySet<ReminderCategory>()) }
    var showFilter by remember { mutableStateOf(false) }
    // Para que «Hoy», «Mañana» y lo vencido se recalculen con el reloj.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_alarms), fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Filled.FilterList, stringResource(R.string.alarms_filter), tint = if (filter.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                            ReminderCategory.displayOrder.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(category.titleRes)) },
                                    leadingIcon = { Icon(SymbolIcons.of(category.defaultSymbol), null, tint = category.tint) },
                                    trailingIcon = { Checkbox(checked = category in filter, onCheckedChange = null) },
                                    onClick = { filter = if (category in filter) filter - category else filter + category },
                                )
                            }
                            if (filter.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.alarms_clear_filters)) }, onClick = { filter = emptySet(); showFilter = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (!reminders.isNullOrEmpty()) {
                FloatingActionButton(onClick = { navigator.newReminder() }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, stringResource(R.string.alarm_new))
                }
            }
        },
    ) { padding ->
        val list = reminders ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            PermissionBanner()
            // Las que compartió otra persona van aparte, al final («Compartidos conmigo», como en iOS),
            // y el filtro de categorías mira solo las propias.
            val own = list.filter { !it.isReceivedShare }
            val shared = list.filter { it.isReceivedShare }
            val visible = if (filter.isEmpty()) own else own.filter { it.category in filter }
            when {
                list.isEmpty() -> StarterAlarms(onPick = { navigator.newReminder(it.id) }, onBlank = { navigator.newReminder() })
                visible.isEmpty() && shared.isEmpty() -> EmptyState(Icons.Filled.SearchOff, stringResource(R.string.alarms_no_results), stringResource(R.string.alarms_no_results_message))
                else -> {
                    val groups = remember(visible, now) { group(context, visible, now) }
                    val sharedItems = remember(shared, now) { shared.map { it to it.nextOccurrence(now) }.sortedBy { it.second ?: Long.MAX_VALUE } }
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        groups.forEach { group ->
                            item(key = "h-" + group.title) {
                                SectionHeader(group.title, group.items.size)
                            }
                            items(group.items, key = { it.first.id }) { (reminder, next) ->
                                SwipeToDelete(onDelete = { scope.launch { container.reminders.delete(reminder.id) } }) {
                                    ReminderRow(
                                        reminder = reminder,
                                        next = next,
                                        now = now,
                                        style = CategoryStyle.of(context, reminder, customs),
                                        onClick = { navigator.openReminder(reminder.id) },
                                        onToggle = { enabled -> scope.launch { container.reminders.setEnabled(reminder.id, enabled) } },
                                    )
                                }
                            }
                        }
                        if (sharedItems.isNotEmpty()) {
                            item(key = "h-shared") {
                                SectionHeader(stringResource(R.string.sharing_shared_with_me), sharedItems.size, Icons.Filled.People)
                            }
                            // Deslizar una recibida es salir de ella (el repositorio avisa al share).
                            items(sharedItems, key = { "s-" + it.first.id }) { (reminder, next) ->
                                SwipeToDelete(onDelete = { scope.launch { container.reminders.delete(reminder.id) } }) {
                                    ReminderRow(
                                        reminder = reminder,
                                        next = next,
                                        now = now,
                                        style = CategoryStyle.of(context, reminder, customs),
                                        onClick = { navigator.openReminder(reminder.id) },
                                        onToggle = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.padding(start = DS.xxxl, end = DS.lg, top = DS.lg, bottom = DS.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.xs),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(horizontal = DS.lg, vertical = 3.dp),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(DS.radiusMd)).background(MaterialTheme.colorScheme.error).padding(end = DS.xl),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = Color.White) }
        },
    ) { content() }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    next: Long?,
    now: Long,
    style: CategoryStyle,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val expired = next == null
    val soon = next != null && next - now in 0..(24 * 3600 * 1000L)
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = DS.md, vertical = DS.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).alpha(if (reminder.isEnabled && !expired) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                ReminderIcon(
                    iconKind = reminder.iconKind,
                    iconValue = reminder.symbolName,
                    tint = style.color,
                    photoPath = reminder.photoPath,
                    fallbackSymbol = if (style.iconKind == ReminderIconKind.SYMBOL) style.iconValue else "bell.fill",
                )
                Spacer(Modifier.width(DS.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (next != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(DS.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateFormat.getTimeFormat(context).format(Date(next)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (soon) style.color else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(dayLabel(context, next, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    } else {
                        Text(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(reminder.date).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Badges(reminder, expired, style.color)
                }
            }
            // Una recibida la enciende o la apaga su dueño: aquí el interruptor solo informa.
            Switch(
                checked = reminder.isEnabled && !expired,
                onCheckedChange = onToggle,
                enabled = !expired && !reminder.isReceivedShare,
                colors = SwitchDefaults.colors(checkedTrackColor = style.color, checkedThumbColor = Color.White, checkedBorderColor = style.color),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Badges(reminder: Reminder, expired: Boolean, tint: Color) {
    val context = LocalContext.current
    val items = buildList {
        if (expired) add(Triple(Icons.Filled.Warning, stringResource(R.string.alarm_expired), MaterialTheme.colorScheme.onSurfaceVariant))
        if (reminder.recurrence.isRecurring) add(Triple(Icons.Filled.Repeat, reminder.recurrence.summary(context), tint))
        if (reminder.additionalSchedules.isNotEmpty()) add(Triple(Icons.Filled.EventNote, "+${reminder.additionalSchedules.size}", tint))
        if (reminder.readyChallenge != null) add(Triple(Icons.Filled.Extension, stringResource(reminder.readyChallenge!!.kind.titleRes), tint))
        if (reminder.isReceivedShare) add(Triple(Icons.Filled.People, stringResource(R.string.sharing_badge), MaterialTheme.colorScheme.onSurfaceVariant))
    }
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DS.sm)) {
        items.forEach { (icon, text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
    }
}

/** «hoy» / «mañana» / día de la semana / fecha corta. */
private fun dayLabel(context: android.content.Context, millis: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        day == today -> context.getString(R.string.day_today_lower)
        day == today.plusDays(1) -> context.getString(R.string.day_tomorrow_lower)
        millis - now < 7L * 24 * 3600 * 1000 -> DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(day)
        else -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM")).format(day)
    }
}

private fun group(context: android.content.Context, reminders: List<Reminder>, now: Long): List<Group> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val weekEnd = now + 7L * 24 * 3600 * 1000
    val buckets = List(5) { mutableListOf<Pair<Reminder, Long?>>() }
    for (reminder in reminders) {
        val next = reminder.nextOccurrence(now)
        val index = when {
            next == null -> 4
            Instant.ofEpochMilli(next).atZone(zone).toLocalDate() == today -> 0
            Instant.ofEpochMilli(next).atZone(zone).toLocalDate() == today.plusDays(1) -> 1
            next <= weekEnd -> 2
            else -> 3
        }
        buckets[index] += reminder to next
    }
    val titles = listOf(R.string.group_today, R.string.group_tomorrow, R.string.group_this_week, R.string.group_later, R.string.group_expired)
    return buckets.mapIndexedNotNull { i, items ->
        if (items.isEmpty()) null
        else Group(context.getString(titles[i]), items.sortedBy { it.second ?: Long.MAX_VALUE })
    }
}

/** Lo que pide permisos que faltan para que las alarmas suenen. */
@Composable
fun PermissionBanner() {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(AlarmPermissions.missing(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missing = AlarmPermissions.missing(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        missing = AlarmPermissions.missing(context)
    }
    val first = missing.firstOrNull() ?: return
    val message = when (first) {
        AlarmPermissions.Missing.NOTIFICATIONS -> R.string.permission_notifications_banner
        AlarmPermissions.Missing.EXACT_ALARMS -> R.string.permission_exact_banner
        AlarmPermissions.Missing.FULL_SCREEN -> R.string.permission_fullscreen_banner
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(DS.radiusMd),
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.sm).animateContentSize(),
    ) {
        Row(Modifier.padding(DS.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(DS.md))
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (first == AlarmPermissions.Missing.NOTIFICATIONS && AlarmPermissions.needsNotificationRuntimePermission(context)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(AlarmPermissions.settingsIntent(context, first))
                }
            }) { Text(stringResource(R.string.permission_fix)) }
        }
    }
}

@Composable
private fun StarterAlarms(onPick: (AlarmTemplate) -> Unit, onBlank: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(DS.lg),
        verticalArrangement = Arrangement.spacedBy(DS.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DS.md), modifier = Modifier.padding(vertical = DS.lg)) {
                HeroIcon(Icons.Filled.NotificationsActive, size = DS.avatarXl)
                Text(stringResource(R.string.starter_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.starter_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        items(AlarmTemplate.starters, key = { it.id }) { template ->
            Surface(
                shape = RoundedCornerShape(DS.radiusLg),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().clickable { onPick(template) },
            ) {
                Row(Modifier.padding(DS.lg), verticalAlignment = Alignment.CenterVertically) {
                    ReminderIcon(ReminderIconKind.SYMBOL, template.symbol, template.category.tint)
                    Spacer(Modifier.width(DS.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(template.labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(template.detailRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (template.showsTime) {
                        Text(
                            DateFormat.getTimeFormat(context).format(Date(template.firstDate())),
                            style = MaterialTheme.typography.titleMedium,
                            color = template.category.tint,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBlank, modifier = Modifier.fillMaxWidth().padding(top = DS.sm)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(DS.sm))
                Text(stringResource(R.string.starter_blank))
            }
        }
    }
}
