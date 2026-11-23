package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los 15 casos calificados de `Tools/assistant-eval/driver.swift`, con el mismo
 * reloj fijo. Como allá, se califica lo que QUEDA GUARDADO (horarios extra,
 * avisos, una vez frente a semanal), no solo lo que se entendió, y el idioma
 * de la respuesta.
 */
class AssistantEvalTest {

    private class Run(val turn: Turn, val saved: List<Reminder>)

    private fun run(text: String, setup: (FakeStore) -> Unit = {}): Run = runBlocking {
        val store = FakeStore()
        setup(store)
        val before = store.items.map { it.id }.toSet()
        val turn = brain(store).handle(text, AssistantLang.ES)
        Run(turn, store.items.filter { it.id !in before })
    }

    /** Todas las fechas en que suena lo creado (principal + horarios extra). */
    private fun times(saved: List<Reminder>): Set<LocalDateTime> =
        saved.flatMap { r -> r.allSchedules.map { EvalClock.local(it.date) } }.toSet()

    private fun one(r: Run, date: LocalDateTime?, rule: RecurrenceRule) {
        assertEquals("se esperaba exactamente una alarma", 1, r.saved.size)
        val saved = r.saved.single()
        if (date != null) assertEquals(date, EvalClock.local(saved.date))
        assertEquals(rule, saved.recurrence)
    }

    private fun weekly(vararg days: Weekday) = RecurrenceRule.Weekly(1, days.toSet())

    @Test fun case00_treasureHoursTwoRangesSameAlarm() {
        val r = run("hay treasure hours en fortnite este sabado 14 en peru de 2 a 4 y de 9 a 11. colocalo porfa para entrar a buscar el token dorado")
        assertEquals(setOf(LocalDateTime.of(2026, 11, 14, 14, 0), LocalDateTime.of(2026, 11, 14, 21, 0)), times(r.saved))
        assertTrue("todo debe ser una vez", r.saved.all { s -> s.allSchedules.all { it.recurrence == RecurrenceRule.Once } })
        assertEquals(AssistantLang.ES, r.turn.lang)
    }

    @Test fun case01_birthdayEveryYear() {
        val r = run("Cumple de mi mamá el 15 de marzo cada año")
        one(r, LocalDateTime.of(2027, 3, 15, 9, 0), RecurrenceRule.Yearly())
        assertEquals("Cumple de mi mamá", r.saved.single().title)
        assertEquals(listOf(AlarmLeadTime.AT_START, AlarmLeadTime.DAY1), r.saved.single().sortedLeadTimes)
    }

    @Test fun case02_tomorrowAt8am() {
        val r = run("Pon una alarma mañana a las 8am para el pastillero")
        one(r, LocalDateTime.of(2026, 11, 11, 8, 0), RecurrenceRule.Once)
        assertEquals("Pastillero", r.saved.single().title)
    }

    @Test fun case03_dailyIsTheNameMondayIsTheFrequency() {
        val r = run("pon una alarma de mi daily todos los lunes a las 9")
        one(r, LocalDateTime.of(2026, 11, 16, 9, 0), weekly(Weekday.MONDAY))
        assertEquals("Daily", r.saved.single().title)
    }

    @Test fun case04_tuesdaysAndThursdays() {
        val r = run("recuérdame sacar la basura los martes y jueves a las 8 pm")
        one(r, LocalDateTime.of(2026, 11, 10, 20, 0), weekly(Weekday.TUESDAY, Weekday.THURSDAY))
        assertEquals("Sacar la basura", r.saved.single().title)
    }

    @Test fun case05_whatDoIHaveToday() {
        val r = run("¿Qué tengo hoy?")
        val outcome = r.turn.outcome
        assertTrue(outcome is Outcome.Listed && outcome.range == ListRange.Today)
        assertTrue(r.saved.isEmpty())
    }

    @Test fun case06_changeDentistSearchesFirst() {
        val r = run("cambia el del dentista a las 11")
        assertEquals(Outcome.NotFound("dentista"), r.turn.outcome)
        assertTrue(r.saved.isEmpty())
    }

    @Test fun case07_deleteNeverWithoutConfirmation() {
        val store = FakeStore()
        val gym = Reminder(title = "Gym", date = EvalClock.millis(LocalDateTime.of(2026, 11, 11, 7, 0)))
        store.items += gym
        val turn = runBlocking { brain(store).handle("borra la alarma del gym", AssistantLang.ES) }
        assertTrue(turn.outcome is Outcome.ConfirmDelete)
        assertEquals(0, store.deletes)
        assertEquals(listOf(gym), store.items)
    }

    @Test fun case08_englishThisFriday() {
        val r = run("Set an alarm this Friday at 7am for the gym")
        one(r, LocalDateTime.of(2026, 11, 13, 7, 0), RecurrenceRule.Once)
        assertEquals("Gym", r.saved.single().title)
        assertEquals(AssistantLang.EN, r.turn.lang)
    }

    @Test fun case09_noTimeNoAlarm() {
        val r = run("Reunión con Ana el jueves")
        assertTrue(r.saved.isEmpty())
        assertEquals(Outcome.AskTime("Reunión con Ana"), r.turn.outcome)
    }

    @Test fun case10_footballEverySaturdayRange() {
        val r = run("fútbol todos los sábados de 3 a 5")
        one(r, LocalDateTime.of(2026, 11, 14, 15, 0), weekly(Weekday.SATURDAY))
        assertEquals("Fútbol", r.saved.single().title)
    }

    @Test fun case11_dayBeforeIsALeadTimeNotASecondAlarm() {
        val r = run("avísame el 20 a las 10 que pague la luz, un día antes también")
        one(r, LocalDateTime.of(2026, 11, 20, 10, 0), RecurrenceRule.Once)
        assertTrue(AlarmLeadTime.DAY1 in r.saved.single().leadTimes)
        assertTrue(AlarmLeadTime.AT_START in r.saved.single().leadTimes)
        assertEquals("Pague la luz", r.saved.single().title)
    }

    @Test fun case12_englishTomorrow6pm() {
        val r = run("remind me to call mom tomorrow at 6pm")
        one(r, LocalDateTime.of(2026, 11, 11, 18, 0), RecurrenceRule.Once)
        assertEquals("Call mom", r.saved.single().title)
        assertEquals(AssistantLang.EN, r.turn.lang)
    }

    @Test fun case13_thisSaturdayIsOnce() {
        val r = run("este sábado a las 10 tengo dentista")
        one(r, LocalDateTime.of(2026, 11, 14, 10, 0), RecurrenceRule.Once)
        assertEquals("Dentista", r.saved.single().title)
    }

    @Test fun case14_massEverySunday() {
        val r = run("misa todos los domingos a las 8 de la mañana")
        one(r, LocalDateTime.of(2026, 11, 15, 8, 0), weekly(Weekday.SUNDAY))
        assertEquals("Misa", r.saved.single().title)
    }
}
