package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que devuelve Gemini Nano pasa por las mismas reglas que el parser: cada
 * caso es un error real que cometía el modelo de iOS (Tools/assistant-eval).
 */
class ModelActionMapperTest {
    private fun request(text: String, customs: List<String> = emptyList()) =
        EngineRequest(text, EvalClock.now, EvalClock.zone, LanguageDetector.detect(text) ?: AssistantLang.ES, AssistantParser.Context(customs))

    private fun map(text: String, json: String, customs: List<String> = emptyList()): AssistantAction? =
        ModelActionMapper.parseJson(json)?.let { ModelActionMapper.map(it, request(text, customs)) }

    private fun draft(text: String, json: String): AlarmDraft = (map(text, json) as AssistantAction.Create).draft

    @Test fun parsesJsonInsideFencesAndProse() {
        val obj = ModelActionMapper.parseJson("Claro:\n```json\n{\"action\":\"list\",\"range\":\"today\"}\n```")
        assertEquals(AssistantAction.ListAlarms(ListRange.Today), obj?.let { ModelActionMapper.map(it, request("¿qué tengo hoy?")) })
    }

    @Test fun invalidJsonIsNull() {
        assertNull(ModelActionMapper.parseJson("no sé"))
        assertNull(ModelActionMapper.parseJson("{\"action\": "))
    }

    @Test fun otherActionFallsBack() = assertNull(map("cuéntame un chiste", "{\"action\":\"other\"}"))

    @Test fun nameIsNotTheFrequency() {
        val d = draft(
            "pon una alarma de mi daily todos los lunes a las 9",
            """{"action":"create","title":"Daily","dateISO":"2026-11-10T09:00:00","hasTime":true,"recurrence":"daily","weekdays":[]}""",
        )
        assertEquals(RecurrenceRule.Weekly(1, setOf(Weekday.MONDAY)), d.recurrence)
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), d.date)
    }

    @Test fun thisSaturdayIsOnceEvenIfModelSaysWeekly() {
        val d = draft(
            "este sábado 14 a las 10 tengo dentista",
            """{"action":"create","title":"Dentista","dateISO":"2026-11-14T10:00:00","hasTime":true,"recurrence":"weekly","weekdays":["saturday"]}""",
        )
        assertEquals(RecurrenceRule.Once, d.recurrence)
        assertEquals(LocalDateTime.of(2026, 11, 14, 10, 0), d.date)
    }

    @Test fun explicitTimeBeatsModelTime() {
        val d = draft(
            "recuérdame sacar la basura mañana a las 8 pm",
            """{"action":"create","title":"Sacar la basura","dateISO":"2026-11-11T08:00:00","hasTime":true,"recurrence":"once"}""",
        )
        assertEquals(LocalDateTime.of(2026, 11, 11, 20, 0), d.date)
    }

    @Test fun midnightWithoutAskingMeansNoTime() {
        val action = map("dentista el jueves", """{"action":"create","title":"Dentista","dateISO":"2026-11-12T00:00:00","recurrence":"once"}""")
        assertEquals(AssistantAction.NeedTime("Dentista", CategoryChoice.REMINDER), action)
    }

    @Test fun hasTimeFalseAsksForTime() {
        val action = map("reunión con Ana el jueves", """{"action":"create","title":"Reunión con Ana","dateISO":"2026-11-12T10:00:00","hasTime":false,"category":"event"}""")
        assertEquals(AssistantAction.NeedTime("Reunión con Ana", CategoryChoice.Builtin(ReminderCategory.EVENT)), action)
    }

    @Test fun birthdayAtMidnightRingsAt9AndRepeatsYearly() {
        val d = draft(
            "el cumple de Juan es el 3 de abril",
            """{"action":"create","title":"Cumple de Juan","dateISO":"2027-04-03T00:00:00","category":"birthday","recurrence":"once"}""",
        )
        assertEquals(LocalDateTime.of(2027, 4, 3, 9, 0), d.date)
        assertEquals(RecurrenceRule.Yearly(), d.recurrence)
        assertEquals(listOf(0, 1440), d.leadMinutes)
    }

    @Test fun pastBareTimeRollsToTomorrow() {
        val d = draft(
            "pon una alarma a las 9 para la pastilla",
            """{"action":"create","title":"Pastilla","dateISO":"2026-11-10T09:00:00","hasTime":true,"recurrence":"once"}""",
        )
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), d.date)
    }

    @Test fun dayBeforeIsALeadTimeFromTheText() {
        val d = draft(
            "avísame el 20 a las 10 que pague la luz, un día antes también",
            """{"action":"create","title":"Pagar la luz","dateISO":"2026-11-20T10:00:00","hasTime":true,"leadTimesMinutes":[60]}""",
        )
        assertEquals(listOf(0, 1440), d.leadMinutes)
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), d.date)
    }

    @Test fun rangesAddTheOtherStartTimes() {
        val d = draft(
            "hay treasure hours este sabado 14 de 2 a 4 y de 9 a 11",
            """{"action":"create","title":"Treasure hours","dateISO":"2026-11-14T14:00:00","hasTime":true,"recurrence":"once"}""",
        )
        assertEquals(LocalDateTime.of(2026, 11, 14, 14, 0), d.date)
        assertEquals(listOf(LocalDateTime.of(2026, 11, 14, 21, 0)), d.extraDates)
    }

    @Test fun approximateTimeFromModel() {
        val d = draft(
            "mañana después del almuerzo llevar el carro al taller",
            """{"action":"create","title":"Llevar el carro al taller","dateISO":"2026-11-11T14:00:00","hasTime":true,"recurrence":"once","category":"reminder"}""",
        )
        assertEquals(LocalDateTime.of(2026, 11, 11, 14, 0), d.date)
        assertEquals("Llevar el carro al taller", d.title)
    }

    @Test fun customCategoryByExactName() {
        val action = map(
            "junta del proyecto mañana a las 10",
            """{"action":"create","title":"Junta del proyecto","dateISO":"2026-11-11T10:00:00","hasTime":true,"category":"trabajo"}""",
            customs = listOf("Trabajo"),
        ) as AssistantAction.Create
        assertEquals(CategoryChoice.Custom("Trabajo"), action.draft.category)
    }

    @Test fun listDeleteDisableUpdate() {
        assertEquals(AssistantAction.ListAlarms(ListRange.ThisWeek, ReminderCategory.BIRTHDAY), map("cumples de la semana", """{"action":"list","range":"this_week","category":"birthday"}"""))
        assertEquals(AssistantAction.Manage(ManageOp.DELETE, "dentista"), map("quita lo del dentista", """{"action":"delete","query":"el dentista"}"""))
        assertEquals(AssistantAction.Manage(ManageOp.DISABLE, "gym"), map("silencia lo del gym", """{"action":"disable","query":"gym"}"""))
        assertNull(map("borra eso", """{"action":"delete","query":""}"""))
        val update = map("pasa lo del dentista a las 11", """{"action":"update","query":"dentista","dateISO":"2026-11-10T11:00:00","hasTime":true}""") as AssistantAction.Update
        assertEquals("dentista", update.query)
        assertEquals(LocalTime.of(11, 0), update.changes.time)
        assertNull(update.changes.date)
    }

    @Test fun promptCarriesClockMessageAndCustoms() {
        val prompt = ModelPrompt.build(request("junta mañana", listOf("Trabajo")))
        assertTrue(prompt.contains("2026-11-10T18:59"))
        assertTrue(prompt.contains("Message: \"junta mañana\""))
        assertTrue(prompt.contains("Trabajo"))
        assertTrue(prompt.contains("2026-11-11T09:00:00"))
    }

    // region Motor híbrido

    private class FakeModel(val result: Interpretation?, val throws: Boolean = false) : ModelInterpreter {
        var calls = 0
        override val isReady = true
        override suspend fun interpret(request: EngineRequest): Interpretation? {
            calls++
            if (throws) error("AICore BUSY")
            return result
        }
    }

    @Test fun hybrid_confidentParserSkipsModel() = runBlocking {
        val model = FakeModel(Interpretation(AssistantAction.Help, true, EngineKind.ON_DEVICE_AI))
        val result = HybridEngine(RuleEngine(), model).interpret(request("mañana a las 8 gym"))
        assertEquals(EngineKind.RULES, result.source)
        assertEquals(0, model.calls)
    }

    @Test fun hybrid_doubtfulParserAsksModel() = runBlocking {
        val modeled = Interpretation(AssistantAction.Help, true, EngineKind.ON_DEVICE_AI)
        val model = FakeModel(modeled)
        val result = HybridEngine(RuleEngine(), model).interpret(request("mañana después del almuerzo llevar el carro"))
        assertSame(modeled, result)
        assertEquals(1, model.calls)
    }

    @Test fun hybrid_modelFailureFallsBackToParser() = runBlocking {
        val result = HybridEngine(RuleEngine(), FakeModel(null, throws = true)).interpret(request("Reunión con Ana el jueves"))
        assertEquals(EngineKind.RULES, result.source)
        assertTrue(result.action is AssistantAction.NeedTime)
        val nothing = HybridEngine(RuleEngine(), FakeModel(null)).interpret(request("asdf qwer"))
        assertEquals(AssistantAction.Unknown, nothing.action)
    }

    // endregion
}
