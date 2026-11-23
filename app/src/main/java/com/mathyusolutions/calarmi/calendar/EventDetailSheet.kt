package com.mathyusolutions.calarmi.calendar

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.ui.components.FullScreenSheet
import com.mathyusolutions.calarmi.ui.components.SectionCard
import com.mathyusolutions.calarmi.ui.components.SettingRow
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * La pantalla de un evento del calendario: cuándo es, de qué calendario, quién
 * lo organiza, el botón para unirse y sus avisos (hasta tres, ninguno o los
 * de siempre). Los cambios se aplican al tocar «Guardar».
 */
@Composable
internal fun EventDetailSheet(event: CalendarEvent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = CalarmiApp.container(context)
    val locale = LocalConfiguration.current.locales[0]
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val eventPrefs by container.calendar.eventPrefs.collectAsStateWithLifecycle()
    val own = eventPrefs[event.prefsKey]

    // Lo que se edita: se guarda solo al tocar «Guardar», como en iOS.
    var leadTimes by remember { mutableStateOf(EventAlerts.configured(container.calendar.prefs(event), container.settings.current.defaultLeadTime)) }
    var enabled by remember { mutableStateOf(container.calendar.prefs(event)?.enabled ?: true) }
    var picking by remember { mutableStateOf(false) }
    val calendar by produceState<CalendarInfo?>(null, event.calendarId) {
        value = container.calendar.calendars().firstOrNull { it.id == event.calendarId }
    }

    fun reschedule() {
        // En el ámbito del contenedor y no en el de la pantalla: esta se cierra
        // enseguida y cancelaría la reprogramación a medias.
        container.scope.launch { container.onDataChanged() }
    }

    FullScreenSheet(
        title = stringResource(R.string.calendar_event),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = {
                container.calendar.setPrefs(event, EventAlerts.toSave(leadTimes, enabled))
                reschedule()
                onDismiss()
            }) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Text(
            event.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = DS.xxxl, end = DS.xxl, top = DS.sm),
        )

        SectionCard {
            val zone = ZoneId.systemDefault()
            val sameDay = Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(event.end).atZone(zone).toLocalDate()
            val end = if (sameDay) timeText(context, event.end)
            else skeletonText(locale, "EEEdMMM", event.end) + ", " + timeText(context, event.end)
            DetailRow(
                label = stringResource(R.string.calendar_start),
                value = skeletonText(locale, "EEEEdMMMMyyyy", event.begin).capitalizedFirst(locale) + ", " + timeText(context, event.begin),
                secondary = stringResource(R.string.calendar_until, end),
            )
            calendar?.let { info ->
                DetailRow(
                    label = stringResource(R.string.tab_calendar),
                    value = info.name,
                    secondary = info.account.takeIf { it.isNotBlank() && it != info.name },
                    dot = parseHex(info.colorHex) ?: parseHex(event.colorHex),
                )
            }
            event.organizer?.takeIf { it.isNotBlank() }?.let {
                DetailRow(label = stringResource(R.string.calendar_organizer), value = it)
            }
            if (event.location != null && event.meetingLink == null) {
                DetailRow(
                    label = stringResource(R.string.calendar_location),
                    value = event.location,
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = { openUri(context, mapsUri(event.location)) },
                )
            }
        }

        val link = event.meetingLink?.takeIf { settings.meetingLinksEnabled }
        if (link != null) {
            Button(
                onClick = { openUri(context, Uri.parse(link.url)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = DS.lg, vertical = DS.sm).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Videocam, null)
                Spacer(Modifier.width(DS.sm))
                Text(stringResource(R.string.calendar_join_in, link.provider.displayName), fontWeight = FontWeight.SemiBold)
            }
        }

        if (EventAlerts.skippedByAttendance(own, settings.onlyAttendingEvents, event.isParticipating)) {
            SectionCard(footer = stringResource(R.string.calendar_not_attending_footer)) {
                SettingRow(
                    title = stringResource(R.string.calendar_not_attending),
                    icon = Icons.Filled.PersonOff,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    divider = false,
                )
            }
        }

        SectionCard(footer = stringResource(R.string.calendar_enabled_footer)) {
            SettingRow(
                title = stringResource(R.string.editor_enabled),
                icon = Icons.Filled.NotificationsActive,
                divider = false,
                onClick = { enabled = !enabled },
                trailing = { Switch(checked = enabled, onCheckedChange = { enabled = it }) },
            )
        }

        SectionCard(
            title = stringResource(R.string.calendar_alerts_max, CalendarRepository.MAX_ALARMS_PER_EVENT),
            footer = stringResource(R.string.calendar_alerts_footer),
        ) {
            if (leadTimes.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_no_alerts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = DS.lg, vertical = DS.md + 2.dp),
                )
                HorizontalDivider(Modifier.padding(start = DS.lg), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            } else {
                leadTimes.forEach { lead ->
                    LeadRow(
                        lead = lead,
                        fireText = fireText(context, locale, event.begin, lead),
                        active = enabled,
                        onRemove = { leadTimes = leadTimes - lead },
                    )
                }
            }
            if (leadTimes.size < CalendarRepository.MAX_ALARMS_PER_EVENT) {
                SettingRow(
                    title = stringResource(R.string.editor_add_alert),
                    icon = Icons.Filled.AddCircle,
                    divider = false,
                    onClick = { picking = true },
                )
            }
        }

        if (own != null) {
            SectionCard {
                SettingRow(
                    title = stringResource(R.string.calendar_reset_default),
                    icon = Icons.AutoMirrored.Filled.Undo,
                    iconTint = MaterialTheme.colorScheme.error,
                    divider = false,
                    onClick = {
                        // Se aplica al momento (como en iOS): el evento vuelve a
                        // seguir el aviso predeterminado de Ajustes.
                        container.calendar.setPrefs(event, null)
                        reschedule()
                        leadTimes = EventAlerts.configured(null, settings.defaultLeadTime)
                        enabled = true
                    },
                )
            }
        }
        Spacer(Modifier.height(DS.xxl))
    }

    if (picking) {
        LeadTimePickerDialog(
            excluded = leadTimes.toSet(),
            onPick = { picked -> leadTimes = (leadTimes + picked).distinct().sortedBy { it.seconds } },
            onDismiss = { picking = false },
        )
    }
}

/** Cuándo suena un aviso: la hora, y el día si cae otro día que el evento. */
private fun fireText(context: android.content.Context, locale: java.util.Locale, begin: Long, lead: AlarmLeadTime): String {
    val fire = begin - lead.millis
    val zone = ZoneId.systemDefault()
    val sameDay = Instant.ofEpochMilli(fire).atZone(zone).toLocalDate() == Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
    return if (sameDay) timeText(context, fire) else skeletonText(locale, "EEEdMMM", fire) + ", " + timeText(context, fire)
}

/** Una fila de dato: etiqueta a la izquierda y valor (que puede ocupar varias líneas) a la derecha. */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    secondary: String? = null,
    dot: Color? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 52.dp)
                .padding(horizontal = DS.lg, vertical = DS.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(DS.lg))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dot != null) ColorDot(dot)
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, textAlign = TextAlign.End)
                }
                if (secondary != null) {
                    Text(
                        secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = DS.lg), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
private fun LeadRow(lead: AlarmLeadTime, fireText: String, active: Boolean, onRemove: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = DS.lg, end = DS.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Notifications,
                null,
                tint = if (active) MaterialTheme.colorScheme.primary else muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(DS.md))
            Text(
                stringResource(lead.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.onSurface else muted,
                modifier = Modifier.weight(1f),
            )
            Text(fireText, style = MaterialTheme.typography.bodyMedium, color = muted)
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_remove), tint = muted, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

/** «Cuándo sonar»: las anticipaciones que todavía no tiene el evento. */
@Composable
private fun LeadTimePickerDialog(excluded: Set<AlarmLeadTime>, onPick: (AlarmLeadTime) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_when_to_ring)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AlarmLeadTime.entries.filter { it !in excluded }.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(option)
                                onDismiss()
                            }
                            .padding(vertical = DS.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(DS.md))
                        Text(stringResource(option.titleRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
