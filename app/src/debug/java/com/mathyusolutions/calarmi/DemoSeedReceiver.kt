package com.mathyusolutions.calarmi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import com.mathyusolutions.calarmi.model.WakeChallenge
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Siembra los mismos datos de demostración que iOS (`DemoData.swift`) para las
 * capturas de Google Play. Solo existe en Debug:
 * `adb shell am broadcast -a com.mathyusolutions.calarmi.debug.SEED_DEMO --es lang es -p com.mathyusolutions.calarmi.debug`
 */
class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lang = intent.getStringExtra("lang") ?: "es"
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                seed(context, lang)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(context: Context, lang: String) {
        val c = CalarmiApp.container(context)
        fun t(es: String, en: String, pt: String) = when (lang) { "en" -> en; "pt" -> pt; else -> es }
        val zone = ZoneId.systemDefault()
        fun at(hour: Int, minute: Int, dayOffset: Long): Long =
            LocalDate.now(zone).plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

        c.database.reminders().all().forEach { c.database.reminders().deleteById(it.id) }
        c.database.countdowns().all().forEach { c.database.countdowns().deleteById(it.id) }

        val reminders = listOf(
            Reminder(title = t("Despertador", "Wake up", "Despertador"), date = at(7, 0, 0), symbolName = "alarm.fill",
                recurrence = RecurrenceRule.Weekly(1, Weekday.workdays), tone = AlarmTone.Marimba,
                challenge = WakeChallenge(WakeChallenge.Kind.MATH)),
            Reminder(title = t("Pastilla de la mañana", "Morning pill", "Remédio da manhã"), date = at(8, 0, 1),
                symbolName = "pills.fill", recurrence = RecurrenceRule.Daily(1)),
            Reminder(title = t("Reunión con el equipo", "Team sync", "Reunião com a equipe"), date = at(17, 30, 0),
                categoryRaw = ReminderCategory.EVENT.raw, symbolName = "person.2.fill",
                recurrence = RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY, Weekday.WEDNESDAY)),
                leadTimes = listOf(AlarmLeadTime.AT_START, AlarmLeadTime.MIN10)),
            Reminder(title = t("Cita con el dentista", "Dentist appointment", "Consulta no dentista"), date = at(16, 0, 3),
                categoryRaw = ReminderCategory.EVENT.raw, symbolName = "cross.case.fill",
                leadTimes = listOf(AlarmLeadTime.AT_START, AlarmLeadTime.HOUR1)),
            Reminder(title = t("Entrenamiento", "Training", "Treino"), date = at(17, 0, 2), symbolName = "figure.run",
                recurrence = RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY, Weekday.FRIDAY)),
                additionalSchedules = listOf(AlarmSchedule(date = at(11, 0, 5), recurrence = RecurrenceRule.Weekly(1, setOf(Weekday.SATURDAY))))),
            Reminder(title = t("Pagar la renta", "Pay the rent", "Pagar o aluguel"), date = at(9, 0, 12),
                symbolName = "creditcard.fill", recurrence = RecurrenceRule.Monthly(1)),
            Reminder(title = t("Cumpleaños de mamá", "Mom's birthday", "Aniversário da mamãe"), date = at(9, 0, 26),
                categoryRaw = ReminderCategory.BIRTHDAY.raw, symbolName = "birthday.cake.fill",
                recurrence = RecurrenceRule.Yearly(1), leadTimes = listOf(AlarmLeadTime.AT_START, AlarmLeadTime.DAY1)),
            Reminder(title = t("Aniversario con Ana", "Anniversary with Ana", "Aniversário de casamento"), date = at(20, 0, 48),
                categoryRaw = ReminderCategory.ANNIVERSARY.raw, symbolName = "heart.fill", recurrence = RecurrenceRule.Yearly(1)),
        )
        reminders.forEach { c.database.reminders().upsert(it) }

        val countdowns = listOf(
            CountdownEvent(title = t("Concierto en el estadio", "Stadium concert", "Show no estádio"), date = at(21, 0, 23),
                includesTime = true, colorHex = "#AF52DE", iconValue = "🎸"),
            CountdownEvent(title = t("Viaje a Japón", "Trip to Japan", "Viagem ao Japão"), date = at(0, 0, 74),
                colorHex = "#FF2D55", iconValue = "🗾"),
            CountdownEvent(title = t("Cumpleaños de Sofía", "Sofía's birthday", "Aniversário da Sofia"),
                date = LocalDate.now(zone).plusDays(9).minusYears(29).atStartOfDay(zone).toInstant().toEpochMilli(),
                repeatsYearly = true, colorHex = "#FF9500", iconValue = "🎂"),
            CountdownEvent(title = t("Final del campeonato", "Championship final", "Final do campeonato"), date = at(18, 30, 1),
                includesTime = true, colorHex = "#34C759", iconKindRaw = ReminderIconKind.EMOJI.raw, iconValue = "🏆"),
        )
        countdowns.forEach { c.database.countdowns().upsert(it) }
        c.settings.update { it.copy(onboardingCompleted = true) }
        c.onDataChanged()
    }
}
