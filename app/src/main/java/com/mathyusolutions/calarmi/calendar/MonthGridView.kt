package com.mathyusolutions.calarmi.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.ui.theme.DS
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

//
// La rejilla de un mes: iniciales de los días de la semana y un botón por día
// con hasta tres puntos debajo. Solo pinta; quién decide qué puntos van en cada
// día es `CalendarScreen`.
//

/**
 * Un punto bajo un día: el color de la categoría de una alarma (hueco cuando
 * está apagada y no va a sonar) o el color del calendario de un evento.
 */
internal data class DayMark(val color: Color, val hollow: Boolean)

private const val MAX_MARKS = 3
private val CELL_HEIGHT = 50.dp

/** Cabecera, rejilla y gesto de deslizar: lo que cambia de mes. */
@Composable
internal fun MonthSection(
    grid: MonthGrid,
    marks: Map<LocalDate, List<DayMark>>,
    selectedDay: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shift by rememberUpdatedState(onShift)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DS.sm)
            // Deslizar de lado cambia de mes, como en iOS. Solo cuenta un gesto
            // claramente horizontal: el vertical es del scroll de la lista.
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > 56.dp.toPx()) shift(if (total < 0) 1 else -1) },
                ) { change, amount ->
                    total += amount
                    change.consume()
                }
            },
    ) {
        MonthHeader(grid.month, locale, onPrevious = { onShift(-1) }, onNext = { onShift(1) })
        AnimatedContent(
            targetState = grid,
            transitionSpec = {
                val forward = targetState.month > initialState.month
                (slideInHorizontally { w -> if (forward) w / 3 else -w / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w / 3 else w / 3 } + fadeOut()) using
                    SizeTransform(clip = true)
            },
            label = "month",
        ) { shown ->
            MonthGridView(shown, marks, selectedDay, today, locale, onSelect)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, locale: Locale, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = DS.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_previous_month))
        }
        Text(
            monthTitle(month, locale),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun MonthGridView(
    grid: MonthGrid,
    marks: Map<LocalDate, List<DayMark>>,
    selectedDay: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
) {
    val symbols = remember(locale, grid.firstDayOfWeek) { weekdaySymbols(grid.weekdays, locale) }
    val hasItems = stringResource(R.string.calendar_has_items)
    val dayFormat = remember(locale) { fullDayFormat(locale) }
    Column(Modifier.fillMaxWidth().padding(vertical = DS.sm), verticalArrangement = Arrangement.spacedBy(DS.xs)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            symbols.forEach { symbol ->
                Text(
                    symbol,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        grid.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).heightIn(min = CELL_HEIGHT))
                    } else {
                        val dayMarks = marks[day].orEmpty()
                        val description = dayFormat.formatDay(day) + if (dayMarks.isEmpty()) "" else ", $hasItems"
                        DayCell(
                            day = day,
                            isSelected = day == selectedDay,
                            isToday = day == today,
                            marks = dayMarks.take(MAX_MARKS),
                            description = description,
                            onClick = { if (day != selectedDay) onSelect(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    marks: List<DayMark>,
    description: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = CELL_HEIGHT)
            .clip(RoundedCornerShape(DS.radiusSm))
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> accent
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.height(5.dp))
        // Alto fijo aunque no haya puntos, para que todas las filas midan igual.
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            marks.forEach { mark ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .then(if (mark.hollow) Modifier.border(1.2.dp, mark.color, CircleShape) else Modifier.background(mark.color)),
                )
            }
        }
    }
}

/**
 * Iniciales de los días en el orden de las columnas («L M X J V S D»). Con los
 * símbolos de ICU: son los «muy cortos independientes» que usa iOS.
 */
private fun weekdaySymbols(order: List<DayOfWeek>, locale: Locale): List<String> {
    val icu = android.icu.text.DateFormatSymbols.getInstance(locale)
        .getWeekdays(android.icu.text.DateFormatSymbols.STANDALONE, android.icu.text.DateFormatSymbols.NARROW)
    // ICU numera domingo = 1 … sábado = 7; java.time, lunes = 1 … domingo = 7.
    return order.map { day -> icu[day.value % 7 + 1].uppercase(locale) }
}
