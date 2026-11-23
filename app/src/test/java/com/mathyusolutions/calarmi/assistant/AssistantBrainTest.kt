package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversaciones completas: lo pendiente entre turnos y lo que queda guardado. */
class AssistantBrainTest {
    private fun at(m: Int, d: Int, h: Int, min: Int = 0) = EvalClock.millis(LocalDateTime.of(2026, m, d, h, min))

    private fun reminder(title: String, date: Long, enabled: Boolean = true, recurrence: RecurrenceRule = RecurrenceRule.Once, category: ReminderCategory = ReminderCategory.REMINDER) =
        Reminder(title = title, date = date, isEnabled = enabled, recurrence = recurrence, categoryRaw = category.raw)

    private class Chat(val store: FakeStore = FakeStore()) {
        val brain = brain(store)
        fun say(text: String, appLang: AssistantLang = AssistantLang.ES): Turn = runBlocking { brain.handle(text, appLang) }
    }

    @Test fun create_savesWithIconAndDefaultLead() {
        val chat = Chat(FakeStore(lead = AlarmLeadTime.MIN15))
        val outcome = chat.say("Pon una alarma mañana a las 8am para el pastillero").outcome as Outcome.Created
        val saved = chat.store.items.single()
        assertEquals("Pastillero", saved.title)
        assertEquals(ReminderCategory.REMINDER.defaultSymbol, saved.symbolName)
        assertEquals(listOf(AlarmLeadTime.MIN15), saved.leadTimes)
        assertEquals(at(11, 11, 8), outcome.item.next)
    }

    @Test fun create_twiceDoesNotDuplicate() {
        val chat = Chat()
        chat.say("Pon una alarma mañana a las 8am para el pastillero")
        val second = chat.say("Pon una alarma mañana a las 8am para el pastillero").outcome
        assertTrue(second is Outcome.AlreadyExists)
        assertEquals(1, chat.store.items.size)
    }

    @Test fun create_untitledUsesDefaultTitleInReplyLanguage() {
        val chat = Chat()
        chat.say("in 10 minutes", AssistantLang.ES)
        assertEquals("Alarm", chat.store.items.single().title)
    }

    @Test fun create_customCategory() {
        val trabajo = CustomCategory(name = "Trabajo", iconValue = "briefcase.fill")
        val chat = Chat(FakeStore(customs = listOf(trabajo)))
        chat.say("reunión de trabajo mañana a las 9")
        val saved = chat.store.items.single()
        assertEquals(trabajo.id, saved.customCategoryId)
        assertEquals("briefcase.fill", saved.symbolName)
    }

    @Test fun askTime_thenFollowUpCreatesTheAlarm() {
        val chat = Chat()
        assertEquals(Outcome.AskTime("Reunión con Ana"), chat.say("Reunión con Ana el jueves").outcome)
        assertTrue(chat.store.items.isEmpty())
        chat.say("a las 5")
        val saved = chat.store.items.single()
        assertEquals("Reunión con Ana", saved.title)
        assertEquals(at(11, 12, 17), saved.date)
        assertEquals(ReminderCategory.EVENT, saved.category)
    }

    @Test fun askTime_newRequestIsHandledOnItsOwn() {
        val chat = Chat()
        chat.say("Reunión con Ana el jueves")
        chat.say("recuérdame la pastilla mañana a las 9")
        assertEquals(listOf("Pastilla"), chat.store.items.map { it.title })
    }

    @Test fun delete_confirmYes() {
        val chat = Chat()
        chat.store.items += reminder("Dentista", at(11, 12, 9))
        assertTrue(chat.say("borra la alarma del dentista").outcome is Outcome.ConfirmDelete)
        assertEquals(1, chat.store.items.size)
        assertEquals(Outcome.Deleted("Dentista"), chat.say("Sí, borrar").outcome)
        assertTrue(chat.store.items.isEmpty())
    }

    @Test fun delete_confirmNoKeepsIt() {
        val chat = Chat()
        chat.store.items += reminder("Gym", at(11, 11, 7))
        chat.say("delete the gym alarm")
        assertEquals(Outcome.Cancelled, chat.say("No, keep it").outcome)
        assertEquals(1, chat.store.items.size)
    }

    @Test fun delete_somethingElseDropsTheConfirmation() {
        val chat = Chat()
        chat.store.items += reminder("Gym", at(11, 11, 7))
        chat.say("borra la alarma del gym")
        assertTrue(chat.say("¿qué tengo mañana?").outcome is Outcome.Listed)
        chat.say("sí")
        assertEquals(1, chat.store.items.size)
    }

    @Test fun delete_pronounUsesLastAlarm() {
        val chat = Chat()
        chat.say("recuérdame la pastilla mañana a las 9")
        val outcome = chat.say("bórrala").outcome as Outcome.ConfirmDelete
        assertEquals("Pastilla", outcome.item.reminder.title)
    }

    @Test fun delete_ambiguousThenMoreSpecific() {
        val chat = Chat()
        chat.store.items += reminder("Reunión de equipo", at(11, 11, 10))
        chat.store.items += reminder("Reunión con Ana", at(11, 12, 17))
        val first = chat.say("borra la reunión").outcome
        assertTrue(first is Outcome.Ambiguous && first.items.size == 2)
        val second = chat.say("la de Ana").outcome as Outcome.ConfirmDelete
        assertEquals("Reunión con Ana", second.item.reminder.title)
    }

    @Test fun delete_notFound() = assertEquals(Outcome.NotFound("gym"), Chat().say("borra la alarma del gym").outcome)

    @Test fun disableAndEnableByName() {
        val chat = Chat()
        chat.store.items += reminder("Gym", at(11, 11, 7), recurrence = RecurrenceRule.Daily())
        val off = chat.say("desactiva la alarma del gym").outcome as Outcome.Toggled
        assertFalse(off.enabled)
        assertFalse(chat.store.items.single().isEnabled)
        val on = chat.say("activa la alarma del gym").outcome as Outcome.Toggled
        assertTrue(on.enabled)
        assertTrue(chat.store.items.single().isEnabled)
        assertEquals(at(11, 11, 7), on.item.next)
    }

    @Test fun update_timeKeepsTheDay() {
        val chat = Chat()
        chat.store.items += reminder("Dentista", at(11, 12, 9))
        val outcome = chat.say("cambia el del dentista a las 11").outcome as Outcome.Updated
        assertEquals(at(11, 12, 11), outcome.item.reminder.date)
    }

    @Test fun update_withoutChangesAsksWhatThenApplies() {
        val chat = Chat()
        chat.store.items += reminder("Dentista", at(11, 12, 9))
        assertTrue(chat.say("cambia la alarma del dentista").outcome is Outcome.AskChange)
        chat.say("al viernes a las 10")
        assertEquals(at(11, 13, 10), chat.store.items.single().date)
    }

    @Test fun list_todayOnlyUpcomingEnabledSorted() {
        val chat = Chat()
        chat.store.items += reminder("Cena", at(11, 10, 21))
        chat.store.items += reminder("Gym", at(11, 10, 19, 30))
        chat.store.items += reminder("Ya pasó", at(11, 10, 8))
        chat.store.items += reminder("Apagada", at(11, 10, 22), enabled = false)
        chat.store.items += reminder("Mañana", at(11, 11, 9))
        val listed = chat.say("¿Qué tengo hoy?").outcome as Outcome.Listed
        assertEquals(listOf("Gym", "Cena"), listed.items.map { it.reminder.title })
    }

    @Test fun list_followUpKeepsListing() {
        val chat = Chat()
        chat.store.items += reminder("Mañana", at(11, 11, 9))
        chat.say("¿Qué tengo hoy?")
        val listed = chat.say("this week").outcome as Outcome.Listed
        assertEquals(ListRange.ThisWeek, listed.range)
        assertEquals(1, listed.items.size)
    }

    @Test fun list_birthdaysThisMonth() {
        val chat = Chat()
        chat.store.items += reminder("Cumple de Ana", at(11, 20, 9), recurrence = RecurrenceRule.Yearly(), category = ReminderCategory.BIRTHDAY)
        chat.store.items += reminder("Cumple de Leo", at(3, 2, 9), recurrence = RecurrenceRule.Yearly(), category = ReminderCategory.BIRTHDAY)
        chat.store.items += reminder("Gym", at(11, 12, 9))
        val listed = chat.say("¿Cuántos cumpleaños hay este mes?").outcome as Outcome.Listed
        assertEquals(listOf("Cumple de Ana"), listed.items.map { it.reminder.title })
    }

    @Test fun find_whenIsIt() {
        val chat = Chat()
        chat.store.items += reminder("Cumple de mamá", at(3, 15, 9), recurrence = RecurrenceRule.Yearly(), category = ReminderCategory.BIRTHDAY)
        val found = chat.say("¿Cuándo es el cumple de mamá?").outcome as Outcome.Found
        assertEquals(EvalClock.millis(LocalDateTime.of(2027, 3, 15, 9, 0)), found.items.single().next)
    }

    @Test fun replyLanguage_followsTheMessageAndSticks() {
        val chat = Chat()
        assertEquals(AssistantLang.EN, chat.say("remind me to call mom tomorrow at 6pm", AssistantLang.ES).lang)
        assertEquals(AssistantLang.EN, chat.say("ok", AssistantLang.ES).lang)
        assertEquals(AssistantLang.PT, chat.say("O que eu tenho hoje?", AssistantLang.ES).lang)
        assertEquals(AssistantLang.ES, Chat().say("9am", AssistantLang.ES).lang)
    }

    @Test fun pastDateIsSavedButFlagged() {
        val chat = Chat()
        val outcome = chat.say("el 1 de enero de 2026 a las 10 algo viejo").outcome as Outcome.Created
        assertEquals(null, outcome.item.next)
    }
}
