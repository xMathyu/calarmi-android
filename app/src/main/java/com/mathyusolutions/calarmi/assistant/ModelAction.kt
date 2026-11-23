package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.Weekday
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * El pedido al modelo y la validación de lo que devuelve. El modelo responde
 * un JSON con los mismos campos que los argumentos de las tools de iOS; nada de
 * lo que dice se muestra tal cual: se valida y se normaliza con las reglas del
 * parser, y lo que el mensaje dice explícitamente (una hora, «todos los lunes»,
 * «un día antes») le gana a lo que haya interpretado el modelo.
 */
object ModelPrompt {
    fun build(request: EngineRequest): String {
        val now = request.now
        val tomorrow9 = now.toLocalDate().plusDays(1).atTime(9, 0)
        val customs = request.context.customCategories.filter { it.isNotBlank() }
        val customLine = if (customs.isEmpty()) "" else " or one of the user's own categories: ${customs.joinToString(", ")}"
        // Las fechas de los ejemplos salen del reloj: con fechas fijas el modelo pequeño las copiaba.
        return """
You turn one message for the alarm app Calarmi into ONE JSON object. Reply with JSON only.
Now (user's local time): ${now.toLocalDate()}T${now.toLocalTime()} (${now.dayOfWeek.name.lowercase()}). Time zone: ${request.zone.id}.
Next days (look dates up here, never compute them):
${DraftRules.upcomingDaysText(now.toLocalDate())}

Fields:
"action": "create" | "list" | "delete" | "disable" | "enable" | "update" | "other"
"title": short alarm name in the user's language, without date, time or frequency words
"dateISO": "YYYY-MM-DDTHH:MM:SS" in local time, in the future
"hasTime": true only if the user said a time of day
"category": "birthday" | "anniversary" | "event" | "reminder" | "other"$customLine
"recurrence": "once" | "daily" | "weekly" | "monthly" | "yearly"
"interval": 1 (2 = every other)
"weekdays": English lowercase day names, only when the user names repeating days, else []
"leadTimesMinutes": minutes before to also notify, e.g. [0, 60, 1440]
"range": "today" | "tomorrow" | "this_week" | "this_month" | "all" (for list)
"query": name of the existing alarm (for delete, disable, enable, update)

Rules:
- Recurrence comes only from how often the user says it repeats, never from words in the alarm's name: "mi daily todos los lunes" is weekly, ["monday"], title "Daily".
- One upcoming day ("este sábado", "this Friday", "el sábado 14") is once. Plural or every ("los sábados", "every Saturday") is weekly with weekdays.
- A bare time is the next time that clock time comes around. "tipo 3", "after lunch" and similar are approximate times.
- Birthdays and anniversaries without a time are at 09:00:00.
- Questions about which alarms exist are "list".

Examples:
"Recuérdame la pastilla todos los días a las 9" -> {"action":"create","title":"Pastilla","dateISO":"${tomorrow9}:00","hasTime":true,"category":"reminder","recurrence":"daily","weekdays":[],"leadTimesMinutes":[0]}
"What do I have tomorrow?" -> {"action":"list","range":"tomorrow"}
"borra la alarma del dentista" -> {"action":"delete","query":"dentista"}

Message: "${request.text.replace("\"", "'")}"
JSON:
        """.trimIndent()
    }
}

object ModelActionMapper {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** El primer objeto JSON de la respuesta, aunque venga entre ``` o con texto alrededor. */
    fun parseJson(text: String?): JsonObject? {
        val t = text ?: return null
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(t.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    fun map(obj: JsonObject, request: EngineRequest): AssistantAction? {
        val text = request.text
        val now = request.now
        val signals = AssistantParser.signals(text, now, request.lang, request.context.customCategories)
        return when (obj.str("action")?.lowercase()) {
            "create" -> create(obj, request, signals)
            "list" -> AssistantAction.ListAlarms(range(obj.str("range")), obj.str("category")?.let(ReminderCategory::fromSlug))
            "delete" -> manage(obj, ManageOp.DELETE)
            "disable" -> manage(obj, ManageOp.DISABLE)
            "enable" -> manage(obj, ManageOp.ENABLE)
            "update" -> update(obj, request, signals)
            else -> null
        }
    }

    private fun create(obj: JsonObject, request: EngineRequest, signals: AssistantParser.Signals): AssistantAction? {
        val now = request.now
        val text = request.text
        val title = obj.str("title")?.let(AssistantParser::cleanTitle)
        val category = category(obj.str("category"), request.context.customCategories) ?: signals.categoryChoice
        val explicitTime = signals.explicitTimes.firstOrNull()
        val modelDate = DraftRules.parseIsoDate(obj.str("dateISO"), request.zone)
        val hasTime = obj.bool("hasTime") ?: true

        signals.relative?.let { minutes ->
            val date = now.plusMinutes(minutes)
            return AssistantAction.Create(AlarmDraft(title, date, RecurrenceRule.Once, category, notices(text, signals)))
        }
        var date: LocalDateTime
        if (explicitTime != null) {
            date = (modelDate?.toLocalDate() ?: now.toLocalDate()).atTime(explicitTime)
        } else if (modelDate == null) {
            if (!category.isDayEvent) return AssistantAction.NeedTime(title, category)
            date = signals.explicitDate(LocalTime.of(9, 0), now) ?: return AssistantAction.NeedTime(title, category)
        } else if (!hasTime || (modelDate.hour == 0 && modelDate.minute == 0 && !signals.saysMidnight)) {
            // Medianoche sin que nadie la pidiera es «no dijo la hora» (regla de iOS).
            if (!category.isDayEvent) return AssistantAction.NeedTime(title, category)
            date = modelDate.with(LocalTime.of(9, 0))
        } else {
            date = modelDate
        }
        // La fecha que el mensaje dice con todas las letras le gana a la del modelo.
        signals.explicitDate(date.toLocalTime(), now)?.let { date = it }

        val weekdays = obj.list("weekdays").mapNotNull(Weekday::fromSlug).toSet()
        var rule = signals.explicitRecurrence
            ?: DraftRules.recurrence(obj.str("recurrence"), weekdays, obj.int("interval") ?: 1)
        // «este sábado 14» es una vez aunque el modelo diga semanal.
        if (rule is RecurrenceRule.Weekly && signals.explicitRecurrence == null && DraftRules.meansSingleDay(text)) rule = RecurrenceRule.Once
        if (rule == RecurrenceRule.Once && category.isDayEvent && signals.explicitRecurrence == null) rule = RecurrenceRule.Yearly()
        date = DraftRules.resolveDate(DraftRules.defaultingDayEventTime(date, category), rule, now)

        val leads = notices(text, signals)
            ?: obj.intList("leadTimesMinutes").takeIf { it.isNotEmpty() }
            ?: if (category.isDayEvent) listOf(0, 1440) else null

        // «de 2 a 4 y de 9 a 11»: una alarma que suena en cada inicio, aunque el modelo vea solo uno.
        val minuteOfDay = date.hour * 60 + date.minute
        val starts = DraftRules.rangeStartTimes(text)
        val extraTimes = (if (starts.size > 1 && minuteOfDay in starts) starts.filter { it != minuteOfDay }.map { LocalTime.of(it / 60, it % 60) } else emptyList()) +
            signals.explicitTimes.drop(1)
        val extras = extraTimes.distinct().map { date.toLocalDate().atTime(it) }.filter { it != date }
        return AssistantAction.Create(AlarmDraft(title, date, rule, category, leads, extras, signals.isWakeUp))
    }

    private fun manage(obj: JsonObject, op: ManageOp): AssistantAction? {
        val query = AssistantParser.cleanQuery(obj.str("query") ?: obj.str("title") ?: return null)
        return if (query.isEmpty()) null else AssistantAction.Manage(op, query)
    }

    private fun update(obj: JsonObject, request: EngineRequest, signals: AssistantParser.Signals): AssistantAction? {
        val query = AssistantParser.cleanQuery(obj.str("query") ?: obj.str("title") ?: return null)
        if (query.isEmpty()) return null
        val modelDate = DraftRules.parseIsoDate(obj.str("dateISO"), request.zone)
        val time = signals.explicitTimes.firstOrNull() ?: modelDate?.takeIf { obj.bool("hasTime") != false }?.toLocalTime()
        val changes = UpdateChanges(
            time = time,
            // El modelo suele rellenar la fecha de hoy aunque solo cambie la hora: solo cuenta si el mensaje la dice.
            date = signals.explicitDate(time ?: LocalTime.of(23, 59), request.now)?.toLocalDate(),
            recurrence = signals.explicitRecurrence,
            leadMinutes = notices(request.text, signals),
        )
        return AssistantAction.Update(query, changes)
    }

    private fun notices(text: String, signals: AssistantParser.Signals): List<Int>? {
        val all = (DraftRules.advanceNoticeMinutes(text) + signals.noticeMinutes).distinct().sorted()
        return if (all.isEmpty()) null else (listOf(0) + all).distinct()
    }

    private fun category(slug: String?, customs: List<String>): CategoryChoice? {
        val s = slug?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        customs.firstOrNull { it.equals(s, ignoreCase = true) }?.let { return CategoryChoice.Custom(it) }
        return ReminderCategory.fromSlug(s)?.let { CategoryChoice.Builtin(it) }
    }

    private fun range(slug: String?): ListRange = when (slug?.lowercase()) {
        "today" -> ListRange.Today
        "tomorrow" -> ListRange.Tomorrow
        "this_week", "week" -> ListRange.ThisWeek
        "this_month", "month" -> ListRange.ThisMonth
        else -> ListRange.All
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.list(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun JsonObject.intList(key: String): List<Int> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.let { p -> p.intOrNull ?: p.contentOrNull?.toIntOrNull() } } ?: emptyList()
}
