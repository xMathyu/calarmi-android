package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.domain.RecurrenceEngine
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.ReminderCategory
import com.mathyusolutions.calarmi.model.ReminderIconKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** Lo que el asistente necesita de los datos. En la app es el `ReminderRepository`; en los tests, una lista. */
interface AssistantStore {
    suspend fun all(): List<Reminder>
    suspend fun save(reminder: Reminder)
    suspend fun delete(id: String)
    suspend fun customCategories(): List<CustomCategory>
    fun defaultLeadTime(): AlarmLeadTime
}

/** Una alarma con su próxima vez (null = ya no suena). */
data class AlarmItem(val reminder: Reminder, val next: Long?)

/** Lo que pasó en un turno; `AssistantReplies` lo convierte en texto en el idioma de la persona. */
sealed interface Outcome {
    data class Created(val item: AlarmItem) : Outcome
    data class AlreadyExists(val item: AlarmItem) : Outcome
    data class AskTime(val title: String?) : Outcome
    data class Listed(val range: ListRange, val category: ReminderCategory?, val items: List<AlarmItem>) : Outcome
    data class Found(val items: List<AlarmItem>) : Outcome
    data class ConfirmDelete(val item: AlarmItem) : Outcome
    data class Deleted(val title: String) : Outcome
    data object Cancelled : Outcome
    data class Toggled(val item: AlarmItem, val enabled: Boolean) : Outcome
    data class Updated(val item: AlarmItem) : Outcome
    data class NotFound(val query: String) : Outcome
    data class Ambiguous(val items: List<AlarmItem>) : Outcome
    data class AskWhich(val op: ManageOp?) : Outcome
    data class AskChange(val item: AlarmItem) : Outcome
    data object Greeting : Outcome
    data object Thanks : Outcome
    data object Help : Outcome
    data object NotUnderstood : Outcome
}

data class Turn(val lang: AssistantLang, val outcome: Outcome, val source: EngineKind)

/**
 * La conversación: decide qué hacer con cada mensaje (con lo pendiente del
 * turno anterior: confirmar un borrado, la hora que faltaba, cuál alarma) y lo
 * ejecuta sobre las alarmas. No conoce Android: se prueba con un reloj fijo.
 */
class AssistantBrain(
    private val store: AssistantStore,
    private val engine: AssistantEngine,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** El nombre de una alarma sin título («mañana a las 7:30») en el idioma de la respuesta. */
    private val defaultTitle: (AssistantLang, AlarmDraft) -> String = { _, _ -> "Alarm" },
) {
    private sealed interface Pending {
        data class Delete(val reminderId: String) : Pending
        data class Time(val text: String) : Pending
        data class Which(val op: ManageOp?, val changes: UpdateChanges?) : Pending
        data class Change(val reminderId: String) : Pending
    }

    private var pending: Pending? = null
    private var lastLang: AssistantLang? = null
    private var lastReminderId: String? = null
    private var lastWasList = false

    suspend fun handle(text: String, appLang: AssistantLang): Turn {
        val trimmed = text.trim()
        // Un «ok» o «9am» no dice el idioma: sigue el del turno anterior y si no, el de la app.
        val lang = LanguageDetector.detect(trimmed) ?: lastLang ?: appLang
        lastLang = lang
        val z = zone()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), z).withSecond(0).withNano(0)
        val reminders = store.all()
        val customs = store.customCategories()
        val context = AssistantParser.Context(customs.map { it.name }, reminders.map { it.title }, lastWasList)
        val f = fold(trimmed)
        val wasList = lastWasList
        lastWasList = false

        when (val p = pending) {
            is Pending.Delete -> {
                pending = null
                if (isYes(f)) return turn(lang, delete(p.reminderId, reminders))
                if (isNo(f)) return turn(lang, Outcome.Cancelled)
            }
            is Pending.Time -> {
                pending = null
                if (isNo(f)) return turn(lang, Outcome.Cancelled)
                val alone = AssistantParser.parse(trimmed, now, lang, context).action
                // «a las 5» responde a «¿a qué hora?»: se lee junto con lo que faltaba.
                val fragment = (alone is AssistantAction.Create && alone.draft.title == null) ||
                    (alone is AssistantAction.NeedTime && alone.title == null) ||
                    (alone is AssistantAction.Unknown && AssistantParser.signals(trimmed, now, lang).hasDateOrTime)
                if (fragment) {
                    val combined = "${p.text} $trimmed"
                    val interpretation = engine.interpret(EngineRequest(combined, now, z, lang, context))
                    return Turn(lang, execute(interpretation.action, now, z, lang, reminders, customs, combined), interpretation.source)
                }
            }
            is Pending.Which -> {
                pending = null
                if (isNo(f)) return turn(lang, Outcome.Cancelled)
                val alone = AssistantParser.parse(trimmed, now, lang, context)
                if (alone.action is AssistantAction.Unknown || alone.action is AssistantAction.NeedTime ||
                    (alone.action is AssistantAction.Create && !alone.confident)
                ) {
                    val query = AssistantParser.cleanQuery(trimmed)
                    if (query.isNotEmpty()) {
                        val action = if (p.op == null) {
                            AssistantAction.Update(query, p.changes ?: UpdateChanges())
                        } else {
                            AssistantAction.Manage(p.op, query)
                        }
                        return turn(lang, execute(action, now, z, lang, reminders, customs, trimmed))
                    }
                }
            }
            is Pending.Change -> {
                pending = null
                if (isNo(f)) return turn(lang, Outcome.Cancelled)
                val target = reminders.firstOrNull { it.id == p.reminderId }
                val changes = changesFrom(trimmed, now, lang)
                if (target != null && !changes.isEmpty) return turn(lang, update(target, changes, now, z))
            }
            null -> Unit
        }

        val interpretation = engine.interpret(EngineRequest(trimmed, now, z, lang, context.copy(lastWasList = wasList)))
        return Turn(lang, execute(interpretation.action, now, z, lang, reminders, customs, trimmed), interpretation.source)
    }

    fun reset() {
        pending = null
        lastLang = null
        lastReminderId = null
        lastWasList = false
    }

    private fun turn(lang: AssistantLang, outcome: Outcome) = Turn(lang, outcome, EngineKind.RULES)

    private suspend fun execute(
        action: AssistantAction,
        now: LocalDateTime,
        z: ZoneId,
        lang: AssistantLang,
        reminders: List<Reminder>,
        customs: List<CustomCategory>,
        text: String,
    ): Outcome = when (action) {
        is AssistantAction.Create -> create(action.draft, now, z, lang, reminders, customs)
        is AssistantAction.NeedTime -> {
            pending = Pending.Time(text)
            Outcome.AskTime(action.title)
        }
        is AssistantAction.ListAlarms -> {
            lastWasList = true
            Outcome.Listed(action.range, action.category, list(action.range, action.category, reminders, now, z))
        }
        is AssistantAction.Manage -> manage(action, reminders, now, z)
        is AssistantAction.Update -> {
            val target = target(action.query, ManageOpOrUpdate.UPDATE, reminders, now, z, null)
            when (target) {
                is Target.One -> if (action.changes.isEmpty) {
                    pending = Pending.Change(target.reminder.id)
                    Outcome.AskChange(item(target.reminder, now, z))
                } else {
                    update(target.reminder, action.changes, now, z)
                }
                is Target.Many -> {
                    pending = Pending.Which(null, action.changes)
                    Outcome.Ambiguous(target.reminders.map { item(it, now, z) })
                }
                Target.None -> Outcome.NotFound(action.query)
                Target.Ask -> {
                    pending = Pending.Which(null, action.changes)
                    Outcome.AskWhich(null)
                }
            }
        }
        AssistantAction.Greeting -> Outcome.Greeting
        AssistantAction.Thanks -> Outcome.Thanks
        AssistantAction.Help -> Outcome.Help
        AssistantAction.Unknown -> Outcome.NotUnderstood
    }

    // region Crear

    private suspend fun create(
        draft: AlarmDraft,
        now: LocalDateTime,
        z: ZoneId,
        lang: AssistantLang,
        reminders: List<Reminder>,
        customs: List<CustomCategory>,
    ): Outcome {
        val title = draft.title ?: defaultTitle(lang, draft)
        val dateMillis = draft.date.atZone(z).toInstant().toEpochMilli()
        // Pedir dos veces lo mismo no puede dejar dos alarmas iguales (regla de iOS).
        reminders.firstOrNull { other ->
            other.isEnabled && other.title.equals(title, ignoreCase = true) &&
                other.allSchedules.any { abs(it.date - dateMillis) < 60_000 && it.recurrence == draft.recurrence }
        }?.let {
            lastReminderId = it.id
            return Outcome.AlreadyExists(item(it, now, z))
        }
        val custom = (draft.category as? CategoryChoice.Custom)?.let { choice ->
            customs.firstOrNull { it.name.equals(choice.name, ignoreCase = true) }
        }
        val builtin = draft.category.builtin ?: ReminderCategory.REMINDER
        val leadMinutes = draft.leadMinutes ?: listOf(store.defaultLeadTime().seconds / 60)
        val reminder = Reminder(
            title = title,
            date = dateMillis,
            categoryRaw = if (custom != null) ReminderCategory.REMINDER.raw else builtin.raw,
            customCategoryId = custom?.id,
            iconKindRaw = (custom?.iconKind ?: ReminderIconKind.SYMBOL).raw,
            symbolName = custom?.iconValue ?: builtin.defaultSymbol,
            recurrence = draft.recurrence,
            leadTimes = DraftRules.leadTimes(leadMinutes),
            additionalSchedules = draft.extraDates.map {
                AlarmSchedule(date = it.atZone(z).toInstant().toEpochMilli(), recurrence = draft.recurrence)
            },
        )
        store.save(reminder)
        lastReminderId = reminder.id
        return Outcome.Created(item(reminder, now, z))
    }

    // endregion

    // region Listar

    private fun list(range: ListRange, category: ReminderCategory?, reminders: List<Reminder>, now: LocalDateTime, z: ZoneId): List<AlarmItem> {
        val today = now.toLocalDate()
        val (start, end) = when (range) {
            ListRange.Today -> today.atStartOfDay() to today.plusDays(1).atStartOfDay()
            ListRange.Tomorrow -> today.plusDays(1).atStartOfDay() to today.plusDays(2).atStartOfDay()
            ListRange.ThisWeek -> today.atStartOfDay() to today.plusDays(7).atStartOfDay()
            ListRange.NextWeek -> today.plusDays(7).atStartOfDay() to today.plusDays(14).atStartOfDay()
            ListRange.ThisMonth -> today.atStartOfDay() to today.plusMonths(1).atStartOfDay()
            is ListRange.Day -> range.date.atStartOfDay() to range.date.plusDays(1).atStartOfDay()
            ListRange.All -> now to null
        }
        val nowMs = now.atZone(z).toInstant().toEpochMilli()
        // Lo que ya sonó hoy no cuenta: como en iOS, vale la PRÓXIMA vez.
        val from = maxOf(start.atZone(z).toInstant().toEpochMilli(), nowMs)
        val to = end?.atZone(z)?.toInstant()?.toEpochMilli()
        return reminders
            .filter { it.isEnabled && (category == null || (it.customCategoryId == null && it.category == category)) }
            .mapNotNull { r ->
                val next = r.allSchedules.mapNotNull { s ->
                    if (to == null) {
                        RecurrenceEngine.nextOccurrences(s.recurrence, s.date, 1, nowMs, z).firstOrNull()
                    } else if (from < to) {
                        RecurrenceEngine.occurrences(s.recurrence, s.date, from, to, z).firstOrNull { it > nowMs }
                    } else {
                        null
                    }
                }.minOrNull() ?: return@mapNotNull null
                AlarmItem(r, next)
            }
            .sortedBy { it.next }
    }

    // endregion

    // region Borrar, apagar, encender, buscar, cambiar

    private enum class ManageOpOrUpdate { MANAGE, UPDATE }

    private sealed interface Target {
        data class One(val reminder: Reminder) : Target
        data class Many(val reminders: List<Reminder>) : Target
        data object None : Target
        data object Ask : Target
    }

    private fun target(
        query: String,
        kind: ManageOpOrUpdate,
        reminders: List<Reminder>,
        now: LocalDateTime,
        z: ZoneId,
        day: java.time.LocalDate?,
        preferDisabled: Boolean = false,
    ): Target {
        if (query.isBlank()) {
            // «bórrala», «cámbiala a las 7»: la alarma de la que se venía hablando.
            val last = lastReminderId?.let { id -> reminders.firstOrNull { it.id == id } }
            return if (last != null) Target.One(last) else Target.Ask
        }
        val scored = reminders.map { r ->
            val byTitle = AssistantParser.matchScore(r.title, query)
            val byNotes = r.notes?.let { AssistantParser.matchScore(it, query) - 10 } ?: 0
            r to maxOf(byTitle, byNotes)
        }.filter { it.second > 0 }
        if (scored.isEmpty()) return Target.None
        val best = scored.maxOf { it.second }
        var matches = scored.filter { it.second == best }.map { it.first }
        if (day != null && matches.size > 1) {
            val start = day.atStartOfDay(z).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
            val onDay = matches.filter { r -> r.allSchedules.any { RecurrenceEngine.occurrences(it.recurrence, it.date, start, end, z).isNotEmpty() } }
            if (onDay.isNotEmpty()) matches = onDay
        }
        if (matches.size > 1 && kind == ManageOpOrUpdate.MANAGE) {
            val preferred = matches.filter { it.isEnabled != preferDisabled }
            if (preferred.size == 1) matches = preferred
        }
        return if (matches.size == 1) Target.One(matches.first()) else Target.Many(matches)
    }

    private suspend fun manage(action: AssistantAction.Manage, reminders: List<Reminder>, now: LocalDateTime, z: ZoneId): Outcome {
        if (action.op == ManageOp.FIND) {
            val found = reminders.filter { AssistantParser.matchScore(it.title, action.query) > 0 }
            if (found.isEmpty()) return Outcome.NotFound(action.query)
            val best = found.maxOf { AssistantParser.matchScore(it.title, action.query) }
            return Outcome.Found(found.filter { AssistantParser.matchScore(it.title, action.query) == best }.map { item(it, now, z) })
        }
        return when (val t = target(action.query, ManageOpOrUpdate.MANAGE, reminders, now, z, action.day, preferDisabled = action.op == ManageOp.ENABLE)) {
            Target.Ask -> {
                pending = Pending.Which(action.op, null)
                Outcome.AskWhich(action.op)
            }
            Target.None -> Outcome.NotFound(action.query)
            is Target.Many -> {
                pending = Pending.Which(action.op, null)
                Outcome.Ambiguous(t.reminders.map { item(it, now, z) })
            }
            is Target.One -> {
                val r = t.reminder
                lastReminderId = r.id
                when (action.op) {
                    // Borrar siempre se confirma (regla de iOS: «Always confirm with the user»).
                    ManageOp.DELETE -> {
                        pending = Pending.Delete(r.id)
                        Outcome.ConfirmDelete(item(r, now, z))
                    }
                    ManageOp.DISABLE, ManageOp.ENABLE -> {
                        val enabled = action.op == ManageOp.ENABLE
                        val updated = r.copy(isEnabled = enabled)
                        if (r.isEnabled != enabled) store.save(updated)
                        Outcome.Toggled(item(updated, now, z), enabled)
                    }
                    ManageOp.FIND -> Outcome.Found(listOf(item(r, now, z)))
                }
            }
        }
    }

    private suspend fun delete(id: String, reminders: List<Reminder>): Outcome {
        val r = reminders.firstOrNull { it.id == id } ?: return Outcome.Cancelled
        store.delete(id)
        if (lastReminderId == id) lastReminderId = null
        return Outcome.Deleted(r.title)
    }

    private suspend fun update(r: Reminder, changes: UpdateChanges, now: LocalDateTime, z: ZoneId): Outcome {
        val current = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.date), z)
        var date = current
        val rule = changes.recurrence ?: r.recurrence
        changes.date?.let { date = it.atTime(date.toLocalTime()) }
        changes.time?.let { time ->
            date = date.with(time)
            // Una alarma única que ya pasó y solo cambia de hora: la próxima vez que llega esa hora.
            if (changes.date == null && rule == RecurrenceRule.Once && !date.isAfter(now)) date = now.toLocalDate().atTime(time)
        }
        date = DraftRules.resolveDate(date, rule, now)
        val updated = r.copy(
            date = date.atZone(z).toInstant().toEpochMilli(),
            recurrence = rule,
            leadTimes = changes.leadMinutes?.let(DraftRules::leadTimes) ?: r.leadTimes,
            isEnabled = true,
        )
        store.save(updated)
        lastReminderId = updated.id
        return Outcome.Updated(item(updated, now, z))
    }

    private fun changesFrom(text: String, now: LocalDateTime, lang: AssistantLang): UpdateChanges {
        val interpretation = AssistantParser.parse("cambia $text", now, AssistantLang.ES, AssistantParser.Context())
        val es = (interpretation.action as? AssistantAction.Update)?.changes
        if (es != null && !es.isEmpty) return es
        val other = AssistantParser.parse("change $text", now, lang, AssistantParser.Context()).action as? AssistantAction.Update
        return other?.changes ?: UpdateChanges()
    }

    // endregion

    private fun item(r: Reminder, now: LocalDateTime, z: ZoneId): AlarmItem {
        val nowMs = now.atZone(z).toInstant().toEpochMilli()
        val next = if (!r.isEnabled) null else r.allSchedules.mapNotNull {
            RecurrenceEngine.nextOccurrences(it.recurrence, it.date, 1, nowMs, z).firstOrNull()
        }.minOrNull()
        return AlarmItem(r, next)
    }

    companion object {
        private val YES = Regex(
            """^\W*(?:si|yes|yeah|yep|yup|sim|ok|okay|vale|dale|claro|confirmo|confirm|hazlo|do it|sure|of course|por supuesto|borrala|borralo|borrar|delete it|delete|apaga|apagar|apagala|exclui|exclua|excluir|isso|eso|correcto|correct|afirmativo|go ahead|adelante|pode)\b""",
        )
        private val NO = Regex(
            """^\W*(?:no|nope|nah|nao|cancel|cancela|cancelar|mejor no|dejala|dejalo|deja|keep|keep it|don'?t|deixa|deixe|olvidalo|forget it|esquece|nevermind|never mind)\b""",
        )

        fun isYes(folded: String): Boolean = folded.length <= 40 && YES.containsMatchIn(folded)
        fun isNo(folded: String): Boolean = folded.length <= 40 && NO.containsMatchIn(folded)
    }
}
