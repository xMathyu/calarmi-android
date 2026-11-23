package com.mathyusolutions.calarmi.calendar

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.ui.components.CategoryStyle
import com.mathyusolutions.calarmi.ui.components.ReminderIcon
import com.mathyusolutions.calarmi.ui.theme.DS
import com.mathyusolutions.calarmi.ui.theme.parseHex
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// region Fechas

/** La hora corta, respetando 12/24 h del teléfono. */
internal fun timeText(context: Context, millis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(millis))

/**
 * Una fecha con el patrón que el idioma prefiere para `skeleton`. Con
 * `SimpleDateFormat` (ICU en Android) y no `java.time`: en varias versiones de
 * Android el mes «independiente» (LLLL) de `java.time` sale como número.
 */
internal fun skeletonText(locale: Locale, skeleton: String, millis: Long): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(millis))

internal fun String.capitalizedFirst(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private fun LocalDate.millis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/** «Noviembre de 2026». */
internal fun monthTitle(month: YearMonth, locale: Locale): String =
    skeletonText(locale, "MMMMyyyy", month.atDay(1).millis()).capitalizedFirst(locale)

/** «Hoy» / «Mañana» / «Martes, 24 de noviembre» (con año si no es este). */
internal fun dayTitle(context: Context, day: LocalDate, today: LocalDate, locale: Locale): String = when (day) {
    today -> context.getString(R.string.group_today)
    today.plusDays(1) -> context.getString(R.string.group_tomorrow)
    else -> skeletonText(locale, if (day.year == today.year) "EEEEdMMMM" else "EEEEdMMMMyyyy", day.millis())
        .capitalizedFirst(locale)
}

/**
 * El formato del día completo, para lectores de pantalla. Se crea una vez por
 * rejilla y no por celda: armar el patrón con ICU no es gratis.
 */
internal fun fullDayFormat(locale: Locale): SimpleDateFormat =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale)

// Con otro nombre: `format(Any)` de `java.text.Format` ganaría a la extensión y fallaría con un LocalDate.
internal fun SimpleDateFormat.formatDay(day: LocalDate): String = format(Date(day.millis()))

// endregion

/** Encabezado de un día: título y cuántas cosas tiene (igual que en Alarmas). */
@Composable
internal fun DayHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = DS.xxxl, end = DS.lg, top = DS.lg, bottom = DS.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.xs),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Una ocurrencia de alarma propia dentro de un día. Tocarla abre su editor. */
@Composable
internal fun AlarmDayRow(reminder: Reminder, occurrence: Long, style: CategoryStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = DS.md, vertical = DS.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Apagada no suena: se ve atenuada, como su punto hueco en la rejilla.
            Row(Modifier.weight(1f).alpha(if (reminder.isEnabled) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
                ReminderIcon(
                    iconKind = reminder.iconKind,
                    iconValue = reminder.symbolName,
                    tint = style.color,
                    photoPath = reminder.photoPath,
                    fallbackSymbol = if (style.iconKind == ReminderIconKind.SYMBOL) style.iconValue else "bell.fill",
                    size = DS.avatarSm + 4.dp,
                )
                Spacer(Modifier.width(DS.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        reminder.title.ifBlank { style.title },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.xs)) {
                        Text(
                            timeText(context, occurrence),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = style.color,
                        )
                        if (reminder.recurrence.isRecurring || reminder.additionalSchedules.isNotEmpty()) {
                            Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * Un evento del calendario: hora, organizador, sus avisos y, si es una
 * reunión, el botón para unirse (o la dirección, que abre el mapa).
 */
@Composable
internal fun EventRow(
    event: CalendarEvent,
    leadTimes: List<AlarmLeadTime>,
    alarmsOn: Boolean,
    showJoin: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val calendarColor = parseHex(event.colorHex) ?: MaterialTheme.colorScheme.outline
    val link = event.meetingLink?.takeIf { showJoin }
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(DS.md), verticalArrangement = Arrangement.spacedBy(DS.md)) {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                // La franja del color del calendario, como en la app de calendario.
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(calendarColor),
                )
                Spacer(Modifier.width(DS.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconLine(Icons.Filled.Schedule, "${timeText(context, event.begin)} – ${timeText(context, event.end)}")
                    event.organizer?.takeIf { it.isNotBlank() }?.let { IconLine(Icons.Filled.Person, it) }
                }
                Spacer(Modifier.width(DS.sm))
                AlarmBadge(leadTimes, alarmsOn)
            }
            if (link != null) {
                FilledTonalButton(onClick = { openUri(context, android.net.Uri.parse(link.url)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Videocam, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DS.sm))
                    Text(stringResource(R.string.calendar_join_in, link.provider.displayName), fontWeight = FontWeight.SemiBold)
                }
            } else if (event.location != null && event.meetingLink == null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(DS.radiusSm))
                        .clickable { openUri(context, mapsUri(event.location)) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        event.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Campana tachada (no suena), el único aviso, o «3 avisos». */
@Composable
private fun AlarmBadge(leadTimes: List<AlarmLeadTime>, alarmsOn: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    if (!alarmsOn || leadTimes.isEmpty()) {
        val description = stringResource(R.string.calendar_no_alarm)
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        return
    }
    val single = leadTimes.singleOrNull()
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(if (single != null) Icons.Filled.Notifications else Icons.Filled.NotificationsActive, null, tint = accent, modifier = Modifier.size(14.dp))
        Text(
            if (single != null) stringResource(single.shortRes)
            else pluralStringResource(R.plurals.calendar_n_alerts, leadTimes.size, leadTimes.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
        )
    }
}

/** La tarjeta que explica por qué no salen los eventos y ofrece arreglarlo. */
@Composable
internal fun AccessCard(title: String, message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(DS.radiusMd),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(DS.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventBusy, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(DS.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(DS.sm))
            Button(onClick = onAction, contentPadding = PaddingValues(horizontal = DS.md, vertical = DS.xs)) {
                Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Un punto de color (el del calendario en Ajustes › Calendarios y en el evento). */
@Composable
internal fun ColorDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
