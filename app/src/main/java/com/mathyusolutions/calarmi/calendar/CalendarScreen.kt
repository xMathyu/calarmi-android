package com.mathyusolutions.calarmi.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import com.mathyusolutions.calarmi.data.Settings
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.EmptyState
import com.mathyusolutions.calarmi.ui.components.ScreenScaffold
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch

//
// La pestaña Calendario: por defecto la vista de mes (alarmas propias y eventos
// del calendario del teléfono juntos) y, con las alarmas del calendario
// encendidas, la lista de eventos próximos detrás de un botón, como en iOS.
//

/** Cuánto mira hacia adelante la lista de eventos (lo mismo que la sincronización de iOS). */
private const val AGENDA_DAYS = 90L

/** La pestaña «Calendario»: vista de mes con alarmas y eventos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()

    val settings by container.settings.state.collectAsStateWithLifecycle()
    // Las alarmas propias salen siempre, haya o no permiso del calendario.
    val reminders by container.reminders.reminders.collectAsStateWithLifecycle(initialValue = emptyList())
    val customs by container.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventPrefs by container.calendar.eventPrefs.collectAsStateWithLifecycle()
    // Sube cuando el proveedor de calendario cambia (lo avisa `CalendarSyncWorker`).
    val revision by container.calendar.revision.collectAsStateWithLifecycle()
    val access = rememberCalendarAccess()
    val ui = remember { CalendarUiPrefs(context) }

    var agenda by rememberSaveable { mutableStateOf(ui.agenda) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var openEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var resumes by remember { mutableIntStateOf(0) }

    // Al volver a la app: puede haber pasado la medianoche o cambiado un evento
    // en la app de calendario sin que el aviso del proveedor haya llegado aún.
    // La primera vez no: la carga inicial ya está en curso.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        today = LocalDate.now()
        if (resumes++ > 0) reloadTick++
    }

    // Los eventos del teléfono solo se muestran con el ajuste encendido y permiso.
    val showsEvents = settings.calendarAlarmsEnabled && access.granted
    val inAgenda = agenda && settings.calendarAlarmsEnabled

    val grid = remember(month, locale) { MonthGrid(month, WeekFields.of(locale).firstDayOfWeek) }
    var monthEvents by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    LaunchedEffect(grid, showsEvents, settings.selectedCalendarIds, revision, reloadTick) {
        monthEvents = if (showsEvents) container.calendar.events(grid.start(), grid.end()) else emptyList()
    }
    var agendaEvents by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    LaunchedEffect(inAgenda, showsEvents, settings.selectedCalendarIds, revision, reloadTick) {
        if (!inAgenda || !showsEvents) return@LaunchedEffect
        val now = System.currentTimeMillis()
        agendaEvents = container.calendar.events(now, now + AGENDA_DAYS * 24 * 3600 * 1000)
    }

    val alarmItems = remember(reminders, grid) { CalendarItems.alarms(reminders, grid.start(), grid.end()) }
    val itemsByDay = remember(alarmItems, monthEvents) { CalendarItems.byDay(alarmItems, monthEvents) }
    val fallbackColor = MaterialTheme.colorScheme.outline
    val marks = remember(itemsByDay, customs, fallbackColor) { dayMarks(context, itemsByDay, customs, fallbackColor) }

    fun refresh() {
        scope.launch {
            refreshing = true
            reloadTick++
            // Como el «Actualizar» de iOS: además de releer, reprograma las alarmas.
            container.onDataChanged()
            refreshing = false
        }
    }

    fun shiftMonth(by: Int) {
        val target = month.plusMonths(by.toLong())
        month = target
        // Al volver al mes actual se elige hoy; en cualquier otro, su día 1.
        selectedDay = if (target == YearMonth.from(today)) today else target.atDay(1)
    }

    val isShowingToday = selectedDay == today && month == YearMonth.from(today)
    val topScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(topScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_calendar), fontWeight = FontWeight.Bold) },
                actions = {
                    if (!inAgenda && !isShowingToday) {
                        TextButton(onClick = {
                            month = YearMonth.from(today)
                            selectedDay = today
                        }) { Text(stringResource(R.string.group_today)) }
                    }
                    if (inAgenda) {
                        IconButton(onClick = ::refresh, enabled = !refreshing) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.calendar_refresh))
                        }
                    }
                    // Sin alarmas del calendario no hay eventos que listar: solo vista de mes.
                    if (settings.calendarAlarmsEnabled) {
                        IconButton(onClick = {
                            agenda = !inAgenda
                            ui.agenda = agenda
                        }) {
                            if (inAgenda) Icon(Icons.Filled.CalendarMonth, stringResource(R.string.calendar_month_view))
                            else Icon(Icons.AutoMirrored.Filled.ViewList, stringResource(R.string.calendar_event_list))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = topScroll,
            )
        },
        floatingActionButton = {
            if (!inAgenda) {
                FloatingActionButton(onClick = { navigator.newReminder() }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, stringResource(R.string.alarm_new))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (inAgenda) {
                AgendaContent(
                    access = access,
                    events = agendaEvents,
                    settings = settings,
                    eventPrefs = eventPrefs,
                    today = today,
                    onRefresh = ::refresh,
                    onOpen = { openEvent = it },
                )
            } else {
                val dayItems = itemsByDay[selectedDay].orEmpty()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item(key = "month") {
                        MonthSection(
                            grid = grid,
                            marks = marks,
                            selectedDay = selectedDay,
                            today = today,
                            locale = locale,
                            onSelect = { selectedDay = it },
                            onShift = ::shiftMonth,
                        )
                    }
                    if (!showsEvents) {
                        item(key = "access") {
                            MonthAccessCard(access, settings, onEnable = {
                                container.settings.update { it.copy(calendarAlarmsEnabled = true) }
                            })
                        }
                    }
                    item(key = "day-header") {
                        DayHeader(dayTitle(context, selectedDay, today, locale), dayItems.size)
                    }
                    if (dayItems.isEmpty()) {
                        item(key = "empty") {
                            EmptyDay(canAdd = !selectedDay.isBefore(today), onAdd = { navigator.newReminder() })
                        }
                    } else {
                        dayRows(dayItems, settings, eventPrefs, customs, onOpenEvent = { openEvent = it })
                    }
                }
            }
        }
    }

    openEvent?.let { event -> EventDetailSheet(event, onDismiss = { openEvent = null }) }
}

/** Las filas de un día: alarmas (abren su editor) y eventos (abren su pantalla). */
private fun LazyListScope.dayRows(
    dayItems: List<CalendarItem>,
    settings: Settings,
    eventPrefs: Map<String, EventPrefs>,
    customs: List<CustomCategory>,
    onOpenEvent: (CalendarEvent) -> Unit,
) {
    items(dayItems, key = { it.key }) { item ->
        val rowModifier = Modifier.padding(horizontal = DS.lg, vertical = 3.dp)
        when (item) {
            is CalendarItem.Alarm -> {
                val context = LocalContext.current
                val navigator = LocalAppNavigator.current
                AlarmDayRow(
                    reminder = item.reminder,
                    occurrence = item.occurrence,
                    style = CategoryStyle.of(context, item.reminder, customs),
                    onClick = { navigator.openReminder(item.reminder.id) },
                    modifier = rowModifier,
                )
            }
            is CalendarItem.Event -> EventItemRow(item.event, settings, eventPrefs, rowModifier) { onOpenEvent(item.event) }
        }
    }
}

@Composable
private fun EventItemRow(
    event: CalendarEvent,
    settings: Settings,
    eventPrefs: Map<String, EventPrefs>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    EventRow(
        event = event,
        leadTimes = EventAlerts.active(eventPrefs[event.prefsKey], settings.defaultLeadTime, settings.onlyAttendingEvents, event.isParticipating),
        alarmsOn = settings.alarmsEnabled,
        showJoin = settings.meetingLinksEnabled,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Por qué no salen los eventos del teléfono y cómo arreglarlo: pedir el
 * permiso, mandar a los ajustes del sistema si Android ya no lo pide, o
 * encender las alarmas del calendario si el permiso ya está.
 */
@Composable
private fun MonthAccessCard(access: CalendarAccess, settings: Settings, onEnable: () -> Unit) {
    val context = LocalContext.current
    val modifier = Modifier.padding(horizontal = DS.lg, vertical = DS.sm)
    when {
        !access.granted && access.denied -> AccessCard(
            title = stringResource(R.string.calendar_no_access_title),
            message = stringResource(R.string.calendar_denied_message),
            action = stringResource(R.string.calendar_open_settings),
            onAction = { openAppSettings(context) },
            modifier = modifier,
        )
        !access.granted -> AccessCard(
            title = stringResource(R.string.calendar_connect_title),
            message = stringResource(R.string.calendar_connect_message),
            action = stringResource(R.string.calendar_allow),
            onAction = { access.request() },
            modifier = modifier,
        )
        !settings.calendarAlarmsEnabled -> AccessCard(
            title = stringResource(R.string.calendar_connect_title),
            message = stringResource(R.string.calendar_off_message),
            action = stringResource(R.string.calendar_enable),
            onAction = onEnable,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyDay(canAdd: Boolean, onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = 3.dp),
    ) {
        Column(
            Modifier.padding(vertical = DS.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DS.sm),
        ) {
            Icon(Icons.Filled.EventAvailable, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.calendar_nothing_this_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Un día pasado no admite alarma nueva.
            if (canAdd) {
                FilledTonalButton(onClick = onAdd) {
                    Text(stringResource(R.string.alarm_new), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** La lista de eventos próximos del calendario del teléfono, agrupados por día. */
@Composable
private fun AgendaContent(
    access: CalendarAccess,
    events: List<CalendarEvent>?,
    settings: Settings,
    eventPrefs: Map<String, EventPrefs>,
    today: LocalDate,
    onRefresh: () -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // Los estados vacíos van en una columna desplazable para que el gesto de
    // tirar hacia abajo (actualizar) funcione también ahí.
    val emptyModifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    when {
        // Sin permiso la lista siempre sale vacía, y «Sin eventos próximos» hacía
        // creer que Calarmi no encontraba eventos que sí estaban en el calendario.
        !access.granted && access.denied -> Column(emptyModifier) {
            EmptyState(
                Icons.Filled.EventBusy,
                stringResource(R.string.calendar_no_access_title),
                stringResource(R.string.calendar_denied_message),
            ) { Button(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.calendar_open_settings)) } }
        }
        !access.granted -> Column(emptyModifier) {
            EmptyState(
                Icons.Filled.EventBusy,
                stringResource(R.string.calendar_connect_title),
                stringResource(R.string.calendar_permission_message),
            ) { Button(onClick = { access.request() }) { Text(stringResource(R.string.calendar_allow_access)) } }
        }
        events == null -> Box(Modifier.fillMaxSize())
        events.isEmpty() -> Column(emptyModifier) {
            EmptyState(
                Icons.Filled.EventBusy,
                stringResource(R.string.calendar_no_upcoming),
                stringResource(R.string.calendar_no_upcoming_message),
            ) { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.calendar_refresh)) } }
        }
        else -> {
            val zone = ZoneId.systemDefault()
            val groups = remember(events) {
                events.sortedBy { it.begin }.groupBy { Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate() }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DS.xxl)) {
                groups.forEach { (day, dayEvents) ->
                    item(key = "h-$day") { DayHeader(dayTitle(context, day, today, locale), dayEvents.size) }
                    items(dayEvents, key = { "event:${it.eventId}:${it.begin}" }) { event ->
                        EventItemRow(event, settings, eventPrefs, Modifier.padding(horizontal = DS.lg, vertical = 3.dp)) { onOpen(event) }
                    }
                }
            }
        }
    }
}

/**
 * Un punto por color distinto en cada día: la categoría de cada alarma (hueco
 * si está apagada) y el calendario de cada evento.
 */
private fun dayMarks(
    context: android.content.Context,
    itemsByDay: Map<LocalDate, List<CalendarItem>>,
    customs: List<CustomCategory>,
    fallback: Color,
): Map<LocalDate, List<DayMark>> = itemsByDay.mapValues { (_, items) ->
    items.map { item ->
        when (item) {
            is CalendarItem.Alarm -> DayMark(CategoryStyle.of(context, item.reminder, customs).color, hollow = !item.reminder.isEnabled)
            is CalendarItem.Event -> DayMark(parseHex(item.event.colorHex) ?: fallback, hollow = false)
        }
    }.distinct()
}

/**
 * Ajustes › Calendarios: cuáles mira Calarmi. Mientras «Todos los calendarios»
 * esté activo no se guarda ninguna selección (`selectedCalendarIds` = null), lo
 * que hace que un calendario nuevo entre solo.
 */
@Composable
fun CalendarSelectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (resumes++ > 0) reloadTick++ }
    LaunchedEffect(reloadTick) {
        val list = container.calendar.calendars()
        calendars = list
        healStaleSelection(container.settings, list)
    }

    // Cambiar `selectedCalendarIds` ya reprograma las alarmas: `AppContainer`
    // observa ese ajuste y llama a `onDataChanged()`. Llamarlo aquí también lo
    // haría dos veces.
    val selection = settings.selectedCalendarIds
    val isAll = selection == null

    ScreenScaffold(title = stringResource(R.string.settings_calendars), onBack = onBack) {
        SectionCard(footer = stringResource(if (isAll) R.string.calendar_all_footer else R.string.calendar_some_footer)) {
            // Apagar «todos» congela la selección actual —todos los calendarios de
            // hoy— para que nadie pierda una alarma solo por abrir esta pantalla.
            fun setAll(wantsAll: Boolean) {
                val known = calendars ?: return
                container.settings.update { it.copy(selectedCalendarIds = if (wantsAll) null else known.map { c -> c.id }.toSet()) }
            }
            SettingRow(
                title = stringResource(R.string.calendar_all_calendars),
                icon = Icons.Filled.CalendarMonth,
                divider = false,
                onClick = { setAll(!isAll) },
                trailing = { Switch(checked = isAll, onCheckedChange = { setAll(it) }) },
            )
        }

        val list = calendars
        if (!isAll && list != null) {
            if (list.isEmpty()) {
                SectionCard {
                    Text(
                        stringResource(R.string.calendar_none_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = DS.lg, vertical = DS.md + 2.dp),
                    )
                }
            }
            val other = stringResource(R.string.calendar_other_account)
            list.groupBy { it.account }.toSortedMap().forEach { (account, group) ->
                // El nombre de la cuenta tal cual (suele ser un correo): sin mayúsculas forzadas.
                Text(
                    account.ifBlank { other },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = DS.xxxl, end = DS.xxl, top = DS.md),
                )
                SectionCard {
                    val sorted = group.sortedBy { it.name.lowercase() }
                    sorted.forEachIndexed { index, info ->
                        CalendarRow(
                            info = info,
                            checked = selection?.contains(info.id) ?: true,
                            divider = index < sorted.lastIndex,
                            onToggle = {
                                val current = selection ?: list.map { it.id }.toSet()
                                val next = if (info.id in current) current - info.id else current + info.id
                                container.settings.update { it.copy(selectedCalendarIds = next) }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.heightIn(min = DS.xxl))
    }
}

@Composable
private fun CalendarRow(info: CalendarInfo, checked: Boolean, divider: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 52.dp)
                .padding(horizontal = DS.lg, vertical = DS.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(parseHex(info.colorHex) ?: MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(DS.md))
            Text(info.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (checked) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        if (divider) {
            HorizontalDivider(Modifier.padding(start = 42.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

/**
 * Una selección guardada cuyos calendarios ya no existen (los ids cambian al
 * quitar y volver a poner una cuenta) vuelve a «todos», igual que hace la
 * lectura de eventos. Si no, esta pantalla mostraría todo sin marcar mientras
 * las alarmas suenan.
 */
private fun healStaleSelection(settings: com.mathyusolutions.calarmi.data.SettingsStore, calendars: List<CalendarInfo>) {
    if (calendars.isEmpty()) return
    val selection = settings.current.selectedCalendarIds ?: return
    if (selection.isEmpty()) return
    val ids = calendars.map { it.id }.toSet()
    if (selection.none { it in ids }) settings.update { it.copy(selectedCalendarIds = null) }
}
