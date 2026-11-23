package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** El parser determinista en los tres idiomas, con el reloj del eval (martes 10/11/2026 18:59). */
class AssistantParserTest {
    private val now = EvalClock.now

    private fun parse(text: String, context: AssistantParser.Context = AssistantParser.Context()): Interpretation =
        AssistantParser.parse(text, now, LanguageDetector.detect(text) ?: AssistantLang.ES, context)

    private fun draft(text: String): AlarmDraft {
        val action = parse(text).action
        if (action !is AssistantAction.Create) fail("«$text» → $action") else return action.draft
        error("unreachable")
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)
    private fun weekly(vararg days: Weekday, interval: Int = 1) = RecurrenceRule.Weekly(interval, days.toSet())
    private val workdays = setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY)

    private fun check(text: String, date: LocalDateTime, rule: RecurrenceRule = RecurrenceRule.Once, title: String? = "§") {
        val d = draft(text)
        assertEquals("fecha de «$text»", date, d.date)
        assertEquals("repetición de «$text»", rule, d.recurrence)
        if (title != "§") assertEquals("título de «$text»", title, d.title)
    }

    // region Español

    @Test fun es_pillEveryDay() = check("Recuérdame la pastilla todos los días a las 9", at(2026, 11, 11, 9), RecurrenceRule.Daily(), "Pastilla")

    @Test fun es_momsBirthdayWithoutRecurrenceIsYearlyAt9() {
        val d = draft("cumple de mamá el 15 de marzo")
        assertEquals(at(2027, 3, 15, 9), d.date)
        assertEquals(RecurrenceRule.Yearly(), d.recurrence)
        assertEquals(CategoryChoice.Builtin(ReminderCategory.BIRTHDAY), d.category)
        assertEquals(listOf(0, 1440), d.leadMinutes)
        assertEquals("Cumple de mamá", d.title)
    }

    @Test fun es_meetingMondaysAndWednesdays5pm() {
        val d = draft("reunión los lunes y miércoles a las 5 pm")
        assertEquals(weekly(Weekday.MONDAY, Weekday.WEDNESDAY), d.recurrence)
        assertEquals(at(2026, 11, 11, 17), d.date)
        assertEquals(CategoryChoice.Builtin(ReminderCategory.EVENT), d.category)
        assertEquals("Reunión", d.title)
    }

    @Test fun es_tomorrowAt730WithoutTitle() = check("mañana a las 7:30", at(2026, 11, 11, 7, 30), title = null)

    @Test fun es_in20Minutes() = check("en 20 minutos", at(2026, 11, 10, 19, 19), title = null)

    @Test fun es_inHalfAnHour() = check("en media hora sacar la ropa", at(2026, 11, 10, 19, 29), title = "Sacar la ropa")

    @Test fun es_inAnHourAndAHalf() = check("en una hora y media", at(2026, 11, 10, 20, 29), title = null)

    @Test fun es_everyTwoWeeksOnTuesday() =
        check("cada 2 semanas el martes a las 10 limpiar el filtro", at(2026, 11, 17, 10), weekly(Weekday.TUESDAY, interval = 2), "Limpiar el filtro")

    @Test fun es_everyThirdOfTheMonth() =
        check("el 3 de cada mes a las 9 pagar la renta", at(2026, 12, 3, 9), RecurrenceRule.Monthly(), "Pagar la renta")

    @Test fun es_everyMonthOnThe15th() =
        check("todos los meses el 15 a las 12 pagar la tarjeta", at(2026, 11, 15, 12), RecurrenceRule.Monthly(), "Pagar la tarjeta")

    @Test fun es_leadTimesHourAndDayBefore() {
        val d = draft("reunión con Ana el jueves a las 5, una hora antes y un día antes")
        assertEquals(at(2026, 11, 12, 17), d.date)
        assertEquals(listOf(0, 60, 1440), d.leadMinutes)
        assertEquals("Reunión con Ana", d.title)
    }

    @Test fun es_halfHourBefore() = assertEquals(listOf(0, 30), draft("dentista el viernes a las 4, media hora antes").leadMinutes)

    @Test fun es_anniversaryIsYearly() {
        val d = draft("aniversario de bodas el 2 de febrero")
        assertEquals(CategoryChoice.Builtin(ReminderCategory.ANNIVERSARY), d.category)
        assertEquals(at(2027, 2, 2, 9), d.date)
        assertEquals(RecurrenceRule.Yearly(), d.recurrence)
    }

    @Test fun es_tonightAt9() = check("hoy a las 9 de la noche sacar al perro", at(2026, 11, 10, 21), title = "Sacar al perro")

    @Test fun es_estaNocheMovesBareHourToEvening() = check("esta noche a las 10 llamar a papá", at(2026, 11, 10, 22), title = "Llamar a papá")

    @Test fun es_wakeMeUpAt5IsMorning() {
        val d = draft("despiértame a las 5")
        assertEquals(at(2026, 11, 11, 5), d.date)
        assertTrue(d.wakeUp)
        assertNull(d.title)
    }

    @Test fun es_dayAfterTomorrow() = check("pasado mañana a las 10 llamar al banco", at(2026, 11, 12, 10), title = "Llamar al banco")

    @Test fun es_nextMonday() = check("el próximo lunes a las 8 dentista", at(2026, 11, 16, 8), title = "Dentista")

    @Test fun es_bareAfternoonHourRollsToTomorrow() = check("a las 3 recoger a los niños", at(2026, 11, 11, 15), title = "Recoger a los niños")

    @Test fun es_mondayToFriday() = check("de lunes a viernes a las 7 gimnasio", at(2026, 11, 11, 7), RecurrenceRule.Weekly(1, workdays), "Gimnasio")

    @Test fun es_entreSemana() = check("entre semana a las 6:30 caminar", at(2026, 11, 11, 6, 30), RecurrenceRule.Weekly(1, workdays), "Caminar")

    @Test fun es_weekends() = check("los fines de semana a las 10 yoga", at(2026, 11, 14, 10), weekly(Weekday.SATURDAY, Weekday.SUNDAY), "Yoga")

    @Test fun es_everyThreeDays() = check("cada 3 días regar las plantas a las 8", at(2026, 11, 11, 8), RecurrenceRule.Daily(3), "Regar las plantas")

    @Test fun es_halfPast() = check("a las 8 y media de la mañana desayuno", at(2026, 11, 11, 8, 30), title = "Desayuno")

    @Test fun es_quarterTo() = check("a las 8 menos cuarto salir", at(2026, 11, 11, 7, 45), title = "Salir")

    @Test fun es_saturday14() = check("el sábado 14 a las 10 corte de pelo", at(2026, 11, 14, 10), title = "Corte de pelo")

    @Test fun es_numericDate() = check("15/12 a las 18 cena de navidad", at(2026, 12, 15, 18), title = "Cena de navidad")

    @Test fun es_monthlyReportOnFridaysIsWeekly() =
        check("recuérdame el reporte mensual todos los viernes a las 4", at(2026, 11, 13, 16), weekly(Weekday.FRIDAY), "Reporte mensual")

    @Test fun es_myDailyKeepsName() = check("mi daily todos los lunes a las 9", at(2026, 11, 16, 9), weekly(Weekday.MONDAY), "Daily")

    @Test fun es_standupDiarioIsDaily() = check("standup diario a las 9", at(2026, 11, 11, 9), RecurrenceRule.Daily(), "Standup")

    @Test fun es_twoTimesSameAlarm() {
        val d = draft("a las 2 y a las 9 tomar la pastilla")
        assertEquals(at(2026, 11, 11, 14), d.date)
        assertEquals(listOf(at(2026, 11, 11, 9)), d.extraDates)
        assertEquals("Tomar la pastilla", d.title)
    }

    @Test fun es_doctorAppointment() {
        val d = draft("cita con el médico el 5 de diciembre a las 4:30 pm")
        assertEquals(at(2026, 12, 5, 16, 30), d.date)
        assertEquals(CategoryChoice.Builtin(ReminderCategory.EVENT), d.category)
        assertEquals("Cita con el médico", d.title)
    }

    @Test fun es_namedDateWithoutTimeAsks() {
        val action = parse("Reunión con Ana el jueves").action
        assertEquals(AssistantAction.NeedTime("Reunión con Ana", CategoryChoice.Builtin(ReminderCategory.EVENT)), action)
    }

    @Test fun es_cancelSubscriptionIsAReminderNotADelete() =
        check("cancela la suscripción de netflix el 5 a las 10", at(2026, 12, 5, 10), title = "Cancela la suscripción de netflix")

    @Test fun es_noon() = check("almuerzo con Lucía mañana al mediodía", at(2026, 11, 11, 12), title = "Almuerzo con Lucía")

    @Test fun es_greetingThenRequest() = check("Hola, recuérdame la pastilla mañana a las 9", at(2026, 11, 11, 9), title = "Pastilla")

    @Test fun es_twoSentencesTitleFromTheOtherOne() = check("Recuérdame comprar leche. Mañana a las 8", at(2026, 11, 11, 8), title = "Comprar leche")

    @Test fun es_morningTomorrow() = check("mañana por la mañana a las 9 correr", at(2026, 11, 11, 9), title = "Correr")

    @Test fun es_pmTimeTodayStillAhead() = check("a las 7 pm cerrar la tienda", at(2026, 11, 10, 19), title = "Cerrar la tienda")

    @Test fun es_everyMorning() = check("todas las mañanas a las 7 meditar", at(2026, 11, 11, 7), RecurrenceRule.Daily(), "Meditar")

    @Test fun es_customCategoryWins() {
        val action = parse("reunión de trabajo mañana a las 9", AssistantParser.Context(customCategories = listOf("Trabajo"))).action
        assertEquals(CategoryChoice.Custom("Trabajo"), (action as AssistantAction.Create).draft.category)
    }

    @Test fun es_unknownGibberish() = assertEquals(AssistantAction.Unknown, parse("asdf qwer").action)

    @Test fun es_vagueTimeIsNotConfident() = assertFalse(parse("mañana después del almuerzo llevar el carro").confident)

    @Test fun es_tipoIsAnApproximateTime() = check("mañana tipo 3 llevar el carro", at(2026, 11, 11, 15), title = "Llevar el carro")

    @Test fun es_bareWeekdayListRepeats() {
        val d = draft("clase de inglés martes y jueves de 7 a 9 de la noche")
        assertEquals(weekly(Weekday.TUESDAY, Weekday.THURSDAY), d.recurrence)
        assertEquals(at(2026, 11, 10, 19), d.date)
        assertEquals("Clase de inglés", d.title)
    }

    @Test fun es_articlesMakeTwoSingleDays() {
        val d = draft("el lunes y el miércoles a las 8 llevar el auto")
        assertEquals(RecurrenceRule.Once, d.recurrence)
        assertEquals(at(2026, 11, 16, 8), d.date)
        assertEquals(listOf(at(2026, 11, 11, 8)), d.extraDates)
    }

    @Test fun es_birthdayIsSentence() =
        check("el cumpleaños de mi hermana es el 3 de julio", at(2027, 7, 3, 9), RecurrenceRule.Yearly(), "Cumpleaños de mi hermana")

    @Test fun es_noOlvides() = check("no olvides comprar pan mañana a las 8", at(2026, 11, 11, 8), title = "Comprar pan")

    @Test fun es_quieroQueMeRecuerdes() = check("quiero que me recuerdes llamar a Lucía el viernes a las 6 pm", at(2026, 11, 13, 18), title = "Llamar a Lucía")

    @Test fun en_dontForget() = check("don't forget to buy milk tomorrow at 8am", at(2026, 11, 11, 8), title = "Buy milk")

    @Test fun es_spaceSeparatedDays() =
        check("gym lunes miércoles y viernes 6 am", at(2026, 11, 11, 6), weekly(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY), "Gym")

    @Test fun es_everyDayExceptWeekends() =
        check("todos los días a las 6:30 menos los fines de semana correr", at(2026, 11, 11, 6, 30), RecurrenceRule.Weekly(1, workdays), "Correr")

    @Test fun en_everyDayExceptSunday() {
        val d = draft("stretch every day at 7am except Sunday")
        assertEquals(RecurrenceRule.Weekly(1, Weekday.entries.toSet() - Weekday.SUNDAY), d.recurrence)
        assertEquals("Stretch", d.title)
    }

    @Test fun es_infinitiveStart() = check("recordar pagar tarjeta 15 de cada mes 10am", at(2026, 11, 15, 10), RecurrenceRule.Monthly(), "Pagar tarjeta")

    @Test fun es_fullDateWithYear() = check("el 1 de enero de 2027 a las 0:00 año nuevo", at(2027, 1, 1, 0), title = "Año nuevo")

    // endregion

    // region Inglés

    @Test fun en_wakeUp630() = check("wake me up at 6:30", at(2026, 11, 11, 6, 30), title = null)

    @Test fun en_dentistDecember3rd() {
        val d = draft("Dentist appointment on December 3rd at 4pm")
        assertEquals(at(2026, 12, 3, 16), d.date)
        assertEquals(CategoryChoice.Builtin(ReminderCategory.EVENT), d.category)
        assertEquals("Dentist appointment", d.title)
    }

    @Test fun en_momsBirthdaySuggestion() {
        val d = draft("Mom's birthday March 15 every year")
        assertEquals(at(2027, 3, 15, 9), d.date)
        assertEquals(RecurrenceRule.Yearly(), d.recurrence)
        assertEquals("Mom's birthday", d.title)
    }

    @Test fun en_pillsEveryDay() = check("take my pills every day at 8am", at(2026, 11, 11, 8), RecurrenceRule.Daily(), "Take my pills")

    @Test fun en_teamMeetingTwoDays() =
        check("team meeting every Monday and Wednesday at 10", at(2026, 11, 11, 10), weekly(Weekday.MONDAY, Weekday.WEDNESDAY), "Team meeting")

    @Test fun en_rentFirstOfEveryMonth() = check("pay rent on the 1st of every month at 9", at(2026, 12, 1, 9), RecurrenceRule.Monthly(), "Pay rent")

    @Test fun en_inTwoHours() = check("in 2 hours check the oven", at(2026, 11, 10, 20, 59), title = "Check the oven")

    @Test fun en_everyOtherWeekOnTuesday() = check("gym every other week on Tuesday at 7pm", at(2026, 11, 10, 19), weekly(Weekday.TUESDAY, interval = 2), "Gym")

    @Test fun en_nextMonday() = check("doctor next Monday at 9:15", at(2026, 11, 16, 9, 15), title = "Doctor")

    @Test fun en_noon() = check("call John at noon", at(2026, 11, 11, 12), title = "Call John")

    @Test fun en_weekends() = check("weekends at 9am yoga", at(2026, 11, 14, 9), weekly(Weekday.SATURDAY, Weekday.SUNDAY), "Yoga")

    @Test fun en_mondaysPlural() = check("trash out Mondays at 8pm", at(2026, 11, 16, 20), weekly(Weekday.MONDAY), "Trash out")

    @Test fun en_myDailyEveryMonday() = check("my daily every Monday at 9", at(2026, 11, 16, 9), weekly(Weekday.MONDAY), "Daily")

    @Test fun en_dayBeforeToo() {
        val d = draft("remind me to pay the electricity bill on the 20th at 10, a day before too")
        assertEquals(at(2026, 11, 20, 10), d.date)
        assertEquals(listOf(0, 1440), d.leadMinutes)
        assertEquals("Pay the electricity bill", d.title)
    }

    @Test fun en_numericDateIsMonthFirst() = check("12/15 at 6pm christmas dinner", at(2026, 12, 15, 18), title = "Christmas dinner")

    @Test fun en_inTenMinutes() = check("in 10 minutes", at(2026, 11, 10, 19, 9), title = null)

    @Test fun en_tonight() = check("call grandma tonight at 9", at(2026, 11, 10, 21), title = "Call grandma")

    // endregion

    // region Portugués

    @Test fun pt_medicineEveryDay() = check("Me lembre do remédio todos os dias às 9", at(2026, 11, 11, 9), RecurrenceRule.Daily(), "Remédio")

    @Test fun pt_tomorrowAt8h() = check("Crie um alarme para amanhã às 8h para o remédio", at(2026, 11, 11, 8), title = "Remédio")

    @Test fun pt_birthdayIsAniversario() {
        val d = draft("Aniversário da minha mãe dia 15 de março todo ano")
        assertEquals(CategoryChoice.Builtin(ReminderCategory.BIRTHDAY), d.category)
        assertEquals(at(2027, 3, 15, 9), d.date)
        assertEquals(RecurrenceRule.Yearly(), d.recurrence)
        assertEquals("Aniversário da minha mãe", d.title)
    }

    @Test fun pt_weddingAnniversary() =
        assertEquals(CategoryChoice.Builtin(ReminderCategory.ANNIVERSARY), draft("aniversário de casamento dia 10 de junho").category)

    @Test fun pt_meetingEveryMonday() = check("reunião toda segunda às 10", at(2026, 11, 16, 10), weekly(Weekday.MONDAY), "Reunião")

    @Test fun pt_mondayToFriday() = check("academia de segunda a sexta às 7", at(2026, 11, 11, 7), RecurrenceRule.Weekly(1, workdays), "Academia")

    @Test fun pt_thursdayOnce() {
        val d = draft("consulta no dentista na quinta às 15h")
        assertEquals(at(2026, 11, 12, 15), d.date)
        assertEquals(RecurrenceRule.Once, d.recurrence)
        assertEquals(CategoryChoice.Builtin(ReminderCategory.EVENT), d.category)
        assertEquals("Consulta no dentista", d.title)
    }

    @Test fun pt_in15Minutes() = check("daqui a 15 minutos tirar o bolo do forno", at(2026, 11, 10, 19, 14), title = "Tirar o bolo do forno")

    @Test fun pt_dayBeforeToo() {
        val d = draft("pagar a conta de luz dia 20 às 10, um dia antes também")
        assertEquals(at(2026, 11, 20, 10), d.date)
        assertEquals(listOf(0, 1440), d.leadMinutes)
        assertEquals("Pagar a conta de luz", d.title)
    }

    @Test fun pt_everyDay5() = check("todo dia 5 às 9 pagar aluguel", at(2026, 12, 5, 9), RecurrenceRule.Monthly(), "Pagar aluguel")

    @Test fun pt_everyTwoWeeks() = check("a cada 2 semanas na terça às 18h futebol", at(2026, 11, 17, 18), weekly(Weekday.TUESDAY, interval = 2), "Futebol")

    @Test fun pt_tonight() = check("hoje à noite às 9 ligar para o João", at(2026, 11, 10, 21), title = "Ligar para o João")

    @Test fun pt_saturdaysPlural() = check("feira aos sábados às 8", at(2026, 11, 14, 8), weekly(Weekday.SATURDAY), "Feira")

    // endregion

    // region Listar

    private fun listed(text: String, context: AssistantParser.Context = AssistantParser.Context()): AssistantAction.ListAlarms {
        val action = parse(text, context).action
        if (action !is AssistantAction.ListAlarms) fail("«$text» → $action") else return action
        error("unreachable")
    }

    @Test fun list_today() = assertEquals(ListRange.Today, listed("¿Qué tengo hoy?").range)
    @Test fun list_tomorrowAlarms() = assertEquals(ListRange.Tomorrow, listed("¿qué alarmas tengo mañana?").range)
    @Test fun list_thisWeekEnglish() = assertEquals(ListRange.ThisWeek, listed("what do I have this week").range)
    @Test fun list_followUpThisWeek() = assertEquals(ListRange.ThisWeek, listed("this week", AssistantParser.Context(lastWasList = true)).range)
    @Test fun list_followUpAndTomorrow() = assertEquals(ListRange.Tomorrow, listed("¿y mañana?").range)
    @Test fun list_birthdaysThisMonth() = assertEquals(AssistantAction.ListAlarms(ListRange.ThisMonth, ReminderCategory.BIRTHDAY), listed("¿Cuántos cumpleaños hay este mes?"))
    @Test fun list_portugueseToday() = assertEquals(ListRange.Today, listed("O que eu tenho hoje?").range)
    @Test fun list_portugueseBirthdays() = assertEquals(AssistantAction.ListAlarms(ListRange.ThisMonth, ReminderCategory.BIRTHDAY), listed("Quantos aniversários tem este mês?"))
    @Test fun list_showMyAlarms() = assertEquals(ListRange.All, listed("show my alarms").range)
    @Test fun list_misAlarmas() = assertEquals(ListRange.All, listed("mis alarmas").range)
    @Test fun list_whatsOnFriday() = assertEquals(ListRange.Day(LocalDate.of(2026, 11, 13)), listed("what's on Friday?").range)
    @Test fun list_tengoAlgoElViernes() = assertEquals(ListRange.Day(LocalDate.of(2026, 11, 13)), listed("¿Tengo algo el viernes?").range)
    @Test fun list_meetingsNextWeek() = assertEquals(AssistantAction.ListAlarms(ListRange.NextWeek, ReminderCategory.EVENT), listed("¿qué reuniones tengo la próxima semana?"))

    @Test fun list_tengoWithoutQuestionIsAReminder() =
        assertTrue(parse("Tengo que ir al banco mañana").action is AssistantAction.NeedTime)

    // endregion

    // region Borrar, apagar, cambiar

    @Test fun manage_deleteDentist() = assertEquals(AssistantAction.Manage(ManageOp.DELETE, "dentista"), parse("borra la alarma del dentista").action)
    @Test fun manage_disableGym() = assertEquals(AssistantAction.Manage(ManageOp.DISABLE, "gym"), parse("desactiva la alarma del gym").action)
    @Test fun manage_apagaIsDisableInSpanish() = assertEquals(AssistantAction.Manage(ManageOp.DISABLE, "pastillas"), parse("apaga la alarma de las pastillas").action)
    @Test fun manage_enable() = assertEquals(AssistantAction.Manage(ManageOp.ENABLE, "gym"), parse("activa la alarma del gym").action)
    @Test fun manage_deleteEnglish() = assertEquals(AssistantAction.Manage(ManageOp.DELETE, "gym"), parse("delete the gym alarm").action)
    @Test fun manage_turnOffEnglish() = assertEquals(AssistantAction.Manage(ManageOp.DISABLE, "wake up"), parse("turn off the wake up alarm").action)
    @Test fun manage_apagueIsDeleteInPortuguese() = assertEquals(AssistantAction.Manage(ManageOp.DELETE, "academia"), parse("apague o alarme da academia").action)
    @Test fun manage_disablePortuguese() = assertEquals(AssistantAction.Manage(ManageOp.DISABLE, "remedio"), parse("desative o alarme do remédio").action)
    @Test fun manage_politeDelete() = assertEquals(AssistantAction.Manage(ManageOp.DELETE, "gym"), parse("¿Puedes borrar la alarma del gym?").action)

    @Test fun manage_cancelMeetingTomorrowFiltersDay() =
        assertEquals(AssistantAction.Manage(ManageOp.DELETE, "reunion", LocalDate.of(2026, 11, 11)), parse("cancela la reunión de mañana").action)

    @Test fun manage_pronounDelete() = assertEquals(AssistantAction.Manage(ManageOp.DELETE, ""), parse("bórrala").action)

    @Test fun manage_updateTime() {
        val action = parse("cambia el del dentista a las 11").action as AssistantAction.Update
        assertEquals("dentista", action.query)
        assertEquals(LocalTime.of(11, 0), action.changes.time)
        assertNull(action.changes.date)
    }

    @Test fun manage_moveToFriday() {
        val action = parse("mueve la reunión al viernes").action as AssistantAction.Update
        assertEquals("reunion", action.query)
        assertEquals(LocalDate.of(2026, 11, 13), action.changes.date)
    }

    @Test fun manage_whenIsBirthday() = assertEquals(AssistantAction.Manage(ManageOp.FIND, "cumple de mama"), parse("¿Cuándo es el cumple de mamá?").action)

    @Test fun manage_rememberToDeleteIsCreate() = check("recuérdame borrar las fotos mañana a las 9", at(2026, 11, 11, 9), title = "Borrar las fotos")

    // endregion

    // region Charla

    @Test fun chat_greeting() = assertEquals(AssistantAction.Greeting, parse("hola").action)
    @Test fun chat_thanks() = assertEquals(AssistantAction.Thanks, parse("¡gracias!").action)
    @Test fun chat_help() = assertEquals(AssistantAction.Help, parse("¿qué puedes hacer?").action)
    @Test fun chat_helpEnglish() = assertEquals(AssistantAction.Help, parse("what can you do?").action)

    // endregion

    // region Búsqueda por nombre

    @Test fun match_scores() {
        assertEquals(100, AssistantParser.matchScore("Gym", "gym"))
        assertEquals(80, AssistantParser.matchScore("Clase de gym", "gym"))
        assertEquals(100, AssistantParser.matchScore("Dentista", "dentista"))
        assertEquals(60, AssistantParser.matchScore("Reunión con Ana del equipo", "reunion equipo"))
        assertEquals(0, AssistantParser.matchScore("Pastilla", "gym"))
    }

    // endregion
}
