package com.mathyusolutions.calarmi.model

import androidx.annotation.StringRes
import com.mathyusolutions.calarmi.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Las alarmas de arranque de la lista vacía: un toque abre el editor ya armado.
 * Abre con el despertador porque la mayoría llega buscando eso.
 */
data class AlarmTemplate(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    /** El nombre de la alarma que se crea (en el idioma de la app al crearla). */
    @StringRes val titleRes: Int,
    val symbol: String,
    val category: ReminderCategory,
    val recurrence: RecurrenceRule,
    val hour: Int,
    val minute: Int,
    val leadTimes: List<AlarmLeadTime>,
    val challenge: WakeChallenge?,
    val showsTime: Boolean,
) {
    /** La próxima vez que el reloj marca `hour:minute`. */
    fun firstDate(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Long {
        val today = ZonedDateTime.of(LocalDate.from(now), LocalTime.of(hour, minute), now.zone)
        val first = if (today.isAfter(now)) today else today.plusDays(1)
        return first.toInstant().toEpochMilli()
    }

    companion object {
        val starters: List<AlarmTemplate> = listOf(
            AlarmTemplate(
                "wake", R.string.template_wake, R.string.template_weekdays, R.string.template_wake,
                "alarm.fill", ReminderCategory.REMINDER, RecurrenceRule.Weekly(1, Weekday.workdays),
                7, 0, listOf(AlarmLeadTime.AT_START), null, true,
            ),
            AlarmTemplate(
                "wake-challenge", R.string.template_wake_challenge, R.string.template_wake_challenge_detail, R.string.template_wake,
                "gamecontroller.fill", ReminderCategory.REMINDER, RecurrenceRule.Weekly(1, Weekday.workdays),
                7, 0, listOf(AlarmLeadTime.AT_START), WakeChallenge(WakeChallenge.Kind.MATH), true,
            ),
            AlarmTemplate(
                "pill", R.string.template_pill, R.string.template_every_day, R.string.template_pill,
                "pills.fill", ReminderCategory.REMINDER, RecurrenceRule.Daily(1),
                9, 0, listOf(AlarmLeadTime.AT_START), null, true,
            ),
            AlarmTemplate(
                "birthday", R.string.category_birthday, R.string.template_birthday_detail, R.string.category_birthday,
                ReminderCategory.BIRTHDAY.defaultSymbol, ReminderCategory.BIRTHDAY, RecurrenceRule.Yearly(1),
                9, 0, listOf(AlarmLeadTime.AT_START, AlarmLeadTime.DAY1), null, false,
            ),
        )

        fun byId(id: String?): AlarmTemplate? = starters.firstOrNull { it.id == id }
    }
}
