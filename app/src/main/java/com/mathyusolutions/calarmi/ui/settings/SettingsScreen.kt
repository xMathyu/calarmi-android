package com.mathyusolutions.calarmi.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.BuildConfig
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.alarm.AlarmPermissions
import com.mathyusolutions.calarmi.data.Settings
import com.mathyusolutions.calarmi.model.AlarmCountdown
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AppLanguage
import com.mathyusolutions.calarmi.model.AppearanceMode
import com.mathyusolutions.calarmi.model.SnoozeInterval
import com.mathyusolutions.calarmi.ui.alarms.TonePickerSheet
import com.mathyusolutions.calarmi.ui.alarms.toneTitle
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.components.SymbolIcons
import com.mathyusolutions.calarmi.ui.navigation.LocalAppNavigator
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex

const val WEBSITE = "https://calarmi.mathyusolutions.com"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val navigator = LocalAppNavigator.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val isPlus by container.plus.isPlus.collectAsStateWithLifecycle()
    var showTone by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(AlarmPermissions.missing(context)) }
    var hasCalendar by remember { mutableStateOf(container.calendar.hasPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        missing = AlarmPermissions.missing(context)
        hasCalendar = container.calendar.hasPermission()
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        if (granted) container.settings.update { it.copy(calendarAlarmsEnabled = true) }
    }
    fun update(transform: (Settings) -> Settings) = container.settings.update(transform)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionCard(footer = stringResource(R.string.settings_alarms_enabled_footer)) {
                SettingRow(
                    title = stringResource(R.string.settings_alarms_enabled),
                    icon = Icons.Filled.Notifications,
                    divider = false,
                    trailing = { Switch(settings.alarmsEnabled, { v -> update { it.copy(alarmsEnabled = v) } }) },
                )
            }

            // Calarmi Plus
            SectionCard(title = stringResource(R.string.plus_name)) {
                SettingRow(
                    title = stringResource(R.string.plus_name),
                    subtitle = stringResource(if (isPlus) R.string.plus_active else R.string.plus_pitch_short),
                    icon = Icons.Filled.WorkspacePremium,
                    iconTint = Color(0xFFAF52DE),
                    showChevron = !isPlus,
                    onClick = { navigator.openPaywall() },
                )
                SettingRow(title = stringResource(R.string.settings_app_icon), icon = Icons.Filled.Apps, showChevron = true, onClick = { navigator.openAppIcons() })
                SettingRow(title = stringResource(R.string.plus_restore), icon = Icons.Filled.Refresh, divider = false, onClick = { container.plus.refresh() })
            }

            // Apariencia
            SectionCard(title = stringResource(R.string.settings_appearance), footer = stringResource(R.string.settings_appearance_footer)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(DS.md)) {
                    AppearanceMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.appearance == mode,
                            onClick = { update { it.copy(appearance = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, AppearanceMode.entries.size),
                        ) { Text(stringResource(mode.titleRes)) }
                    }
                }
            }
            SectionCard(title = stringResource(R.string.settings_accent)) {
                FlowRow(Modifier.padding(DS.md), horizontalArrangement = Arrangement.spacedBy(DS.md), verticalArrangement = Arrangement.spacedBy(DS.md)) {
                    Settings.accentPresets.forEach { hex ->
                        val color = parseHex(hex)!!
                        val selected = (settings.accentColorHex ?: Settings.DEFAULT_ACCENT).equals(hex, true)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(color)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
                                .clickable { update { it.copy(accentColorHex = if (hex == Settings.DEFAULT_ACCENT) null else hex) } },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            SectionCard(footer = stringResource(R.string.settings_categories_footer)) {
                SettingRow(title = stringResource(R.string.settings_categories), icon = Icons.Filled.Sell, showChevron = true, divider = false, onClick = { navigator.openCategories() })
            }

            // Idioma
            SectionCard(title = stringResource(R.string.settings_language), footer = stringResource(R.string.settings_language_footer)) {
                val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val current = AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && currentTag.startsWith(it.tag.substringBefore('-')) } ?: AppLanguage.SYSTEM
                MenuRow(
                    title = stringResource(R.string.settings_language),
                    icon = Icons.Filled.Language,
                    value = stringResource(current.titleRes),
                    options = AppLanguage.entries.map { stringResource(it.titleRes) to it },
                    onPick = { lang -> AppCompatDelegate.setApplicationLocales(if (lang.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang.tag)) },
                )
            }

            // Sonido y avisos
            SectionCard(title = stringResource(R.string.settings_sound_section), footer = stringResource(R.string.settings_sound_footer)) {
                SettingRow(
                    title = stringResource(R.string.settings_alarm_sound),
                    icon = SymbolIcons.of(settings.alarmTone.symbol),
                    value = toneTitle(context, settings.alarmTone),
                    showChevron = true,
                    onClick = { showTone = true },
                )
                SettingRow(
                    title = stringResource(R.string.settings_vibrate),
                    icon = Icons.Filled.Vibration,
                    divider = false,
                    trailing = { Switch(settings.vibrate, { v -> update { it.copy(vibrate = v) } }) },
                )
            }
            SectionCard(footer = stringResource(R.string.settings_default_lead_footer)) {
                MenuRow(
                    title = stringResource(R.string.settings_default_lead),
                    icon = Icons.Filled.NotificationsActive,
                    value = stringResource(settings.defaultLeadTime.titleRes),
                    options = AlarmLeadTime.entries.map { stringResource(it.titleRes) to it },
                    onPick = { v -> update { it.copy(defaultLeadTime = v) } },
                )
            }
            SectionCard(footer = stringResource(R.string.settings_countdown_footer)) {
                MenuRow(
                    title = stringResource(R.string.settings_countdown),
                    icon = Icons.Filled.Timer,
                    value = stringResource(settings.countdown.titleRes),
                    options = AlarmCountdown.entries.map { stringResource(it.titleRes) to it },
                    onPick = { v -> update { it.copy(countdown = v) } },
                )
            }
            SectionCard(footer = stringResource(R.string.settings_snooze_footer)) {
                MenuRow(
                    title = stringResource(R.string.settings_snooze),
                    icon = Icons.Filled.Snooze,
                    value = stringResource(settings.snooze.titleRes),
                    options = SnoozeInterval.entries.map { stringResource(it.titleRes) to it },
                    onPick = { v -> update { it.copy(snooze = v) } },
                )
            }

            // Calendario
            SectionCard(title = stringResource(R.string.settings_calendar_section), footer = stringResource(R.string.settings_calendar_footer)) {
                SettingRow(
                    title = stringResource(R.string.settings_calendar_alarms),
                    icon = Icons.Filled.CalendarMonth,
                    divider = settings.calendarAlarmsEnabled,
                    trailing = {
                        Switch(settings.calendarAlarmsEnabled && hasCalendar, { v ->
                            if (v && !hasCalendar) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            else update { it.copy(calendarAlarmsEnabled = v) }
                        })
                    },
                )
                if (settings.calendarAlarmsEnabled && hasCalendar) {
                    SettingRow(title = stringResource(R.string.settings_calendars), icon = Icons.Filled.EventAvailable, showChevron = true, onClick = { navigator.openCalendarSelection() })
                    SettingRow(
                        title = stringResource(R.string.settings_only_attending),
                        icon = Icons.Filled.HowToReg,
                        trailing = { Switch(settings.onlyAttendingEvents, { v -> update { it.copy(onlyAttendingEvents = v) } }) },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_meeting_links),
                        icon = Icons.Filled.Videocam,
                        divider = false,
                        trailing = { Switch(settings.meetingLinksEnabled, { v -> update { it.copy(meetingLinksEnabled = v) } }) },
                    )
                }
            }

            // Permisos
            SectionCard(title = stringResource(R.string.settings_permissions), footer = stringResource(R.string.settings_permissions_footer)) {
                AlarmPermissions.Missing.entries.forEachIndexed { i, item ->
                    val ok = item !in missing
                    SettingRow(
                        title = stringResource(
                            when (item) {
                                AlarmPermissions.Missing.NOTIFICATIONS -> R.string.permission_notifications
                                AlarmPermissions.Missing.EXACT_ALARMS -> R.string.permission_exact
                                AlarmPermissions.Missing.FULL_SCREEN -> R.string.permission_fullscreen
                            },
                        ),
                        icon = Icons.Filled.Lock,
                        iconTint = if (ok) Color(0xFF34C759) else MaterialTheme.colorScheme.error,
                        value = stringResource(if (ok) R.string.permission_granted else R.string.permission_missing),
                        divider = i < AlarmPermissions.Missing.entries.lastIndex,
                        onClick = { context.startActivity(AlarmPermissions.settingsIntent(context, item)) },
                    )
                }
            }

            // Acerca de
            SectionCard(title = stringResource(R.string.settings_about), footer = stringResource(R.string.settings_about_footer)) {
                SettingRow(title = stringResource(R.string.settings_version), icon = Icons.Filled.Info, value = BuildConfig.VERSION_NAME)
                SettingRow(title = stringResource(R.string.settings_help), icon = Icons.AutoMirrored.Filled.HelpOutline, showChevron = true, onClick = { openUrl(context, "$WEBSITE/" + localizedPath(context, "help")) })
                SettingRow(title = stringResource(R.string.settings_privacy), icon = Icons.Filled.PrivacyTip, showChevron = true, onClick = { openUrl(context, "$WEBSITE/" + localizedPath(context, "privacy")) })
                SettingRow(title = stringResource(R.string.settings_rate), icon = Icons.Filled.Star, showChevron = true, onClick = {
                    openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName.removeSuffix(".debug")}")
                })
                SettingRow(title = stringResource(R.string.settings_share), icon = Icons.Filled.Share, divider = false, onClick = {
                    val text = context.getString(R.string.share_app_text) + "\n" + "https://play.google.com/store/apps/details?id=${context.packageName.removeSuffix(".debug")}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                })
            }
            if (BuildConfig.DEBUG) {
                SectionCard(title = "Debug") {
                    var forced by remember { mutableStateOf(container.plus.debugForcePlus) }
                    SettingRow(title = "Simular Plus", divider = false, trailing = { Switch(forced, { forced = it; container.plus.debugForcePlus = it }) })
                }
            }
            Spacer(Modifier.height(DS.xxxl))
        }
    }

    if (showTone) {
        TonePickerSheet(settings.alarmTone, fallback = null, onDismiss = { showTone = false }, onSelect = { t -> if (t != null) update { it.copy(alarmTone = t) } })
    }
}

/** Fila que abre un menú con opciones. */
@Composable
fun <T> MenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    options: List<Pair<String, T>>,
    divider: Boolean = false,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(title = title, icon = icon, value = value, divider = divider, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, option) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(option) })
            }
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Las rutas de la web en el idioma de la app: /ayuda/, /en/help/, /pt/ajuda/. */
fun localizedPath(context: android.content.Context, page: String): String {
    val lang = context.resources.configuration.locales[0].language
    return when (page) {
        "help" -> when (lang) { "es" -> "ayuda/"; "pt" -> "pt/ajuda/"; "de" -> "de/hilfe/"; else -> "en/help/" }
        else -> when (lang) { "es" -> "privacidad/"; "pt" -> "pt/privacidade/"; "de" -> "de/datenschutz/"; else -> "en/privacy/" }
    }
}
