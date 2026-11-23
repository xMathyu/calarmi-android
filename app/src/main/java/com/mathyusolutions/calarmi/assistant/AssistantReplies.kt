package com.mathyusolutions.calarmi.assistant

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Un globo del chat. Las alarmas van como tarjetas que abren el editor. */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val cards: List<Reminder> = emptyList(),
    /** Botones de respuesta rápida («Sí, borrar»): al tocarlos se envían como mensaje. */
    val quickReplies: List<String> = emptyList(),
    /** Alarmas borradas en este turno: su tarjeta ya no abre nada. */
    val deleted: Boolean = false,
) {
    companion object {
        private val ids = AtomicLong()
        fun nextId(): Long = ids.incrementAndGet()
    }
}

/**
 * Convierte el resultado de un turno en el globo de respuesta, en el idioma en
 * que escribió la persona (que puede no ser el de la app): se usan los recursos
 * de ese idioma con un contexto configurado para él.
 */
class AssistantReplies(private val app: Context) {
    private val contexts = HashMap<AssistantLang, Context>()

    fun localized(lang: AssistantLang): Context = contexts.getOrPut(lang) {
        val config = Configuration(app.resources.configuration)
        config.setLocale(lang.locale)
        app.createConfigurationContext(config)
    }

    fun defaultTitle(lang: AssistantLang, draft: AlarmDraft): String {
        val c = localized(lang)
        return when {
            draft.wakeUp -> c.getString(R.string.assistant_wake_title)
            draft.category.isDayEvent -> c.getString(draft.category.builtin!!.titleRes)
            else -> c.getString(R.string.assistant_default_title)
        }
    }

    fun render(turn: Turn, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): ChatMessage {
        val c = localized(turn.lang)
        fun s(id: Int, vararg args: Any): String = c.getString(id, *args)
        fun whenText(millis: Long) = whenText(c, turn.lang.locale, millis, zone, now)
        fun reply(text: String, cards: List<Reminder> = emptyList(), quick: List<String> = emptyList(), deleted: Boolean = false) =
            ChatMessage(ChatMessage.nextId(), fromUser = false, text = text.replace(Regex(""" +([.:,])"""), "$1").trim(), cards = cards, quickReplies = quick, deleted = deleted)

        return when (val o = turn.outcome) {
            is Outcome.Created -> {
                val r = o.item.reminder
                val parts = mutableListOf<String>()
                val next = o.item.next
                parts += s(R.string.assistant_reply_created, r.title, whenText(next ?: r.date))
                if (r.recurrence.isRecurring) parts += s(R.string.assistant_reply_repeats, r.recurrence.summary(c))
                if (r.additionalSchedules.isNotEmpty()) {
                    parts += s(R.string.assistant_reply_also_at, r.additionalSchedules.joinToString(", ") { whenText(it.date) })
                }
                val leads = r.sortedLeadTimes.filter { it != AlarmLeadTime.AT_START }
                if (leads.isNotEmpty()) parts += s(R.string.assistant_reply_leads, leads.joinToString(", ") { c.getString(it.titleRes).lowercase(turn.lang.locale) })
                if (next == null) parts += s(R.string.assistant_reply_past)
                reply(parts.joinToString(" "), listOf(r))
            }
            is Outcome.AlreadyExists -> reply(s(R.string.assistant_reply_exists, o.item.reminder.title, whenText(o.item.next ?: o.item.reminder.date)), listOf(o.item.reminder))
            is Outcome.AskTime -> reply(o.title?.let { s(R.string.assistant_reply_ask_time, it) } ?: s(R.string.assistant_reply_ask_time_untitled))
            is Outcome.Listed -> {
                val shown = o.items.take(MAX_CARDS)
                val rangeText = rangeText(c, o.range)
                val header = when {
                    o.items.isEmpty() && o.category != null -> s(R.string.assistant_reply_list_none_category, c.getString(o.category.titleRes), rangeText)
                    o.items.isEmpty() && o.range == ListRange.All -> s(R.string.assistant_reply_list_none_all)
                    o.items.isEmpty() -> s(R.string.assistant_reply_list_none, rangeText)
                    o.category != null && o.range == ListRange.All -> s(R.string.assistant_reply_list_category, c.getString(o.category.titleRes), o.items.size)
                    o.category != null -> s(R.string.assistant_reply_list_category_range, c.getString(o.category.titleRes), rangeText, o.items.size)
                    o.range == ListRange.All -> c.resources.getQuantityString(R.plurals.assistant_reply_list_all, o.items.size, o.items.size)
                    else -> c.resources.getQuantityString(R.plurals.assistant_reply_list, o.items.size, o.items.size, rangeText)
                }
                val more = o.items.size - shown.size
                val text = if (more > 0) header + "\n" + c.resources.getQuantityString(R.plurals.assistant_reply_list_more, more, more) else header
                reply(text, shown.map { it.reminder })
            }
            is Outcome.Found -> reply(
                c.resources.getQuantityString(R.plurals.assistant_reply_found, o.items.size, o.items.size),
                o.items.take(MAX_CARDS).map { it.reminder },
            )
            is Outcome.ConfirmDelete -> reply(
                s(R.string.assistant_reply_confirm_delete, o.item.reminder.title),
                listOf(o.item.reminder),
                quick = listOf(s(R.string.assistant_quick_yes_delete), s(R.string.assistant_quick_no)),
            )
            is Outcome.Deleted -> reply(s(R.string.assistant_reply_deleted, o.title), deleted = true)
            Outcome.Cancelled -> reply(s(R.string.assistant_reply_cancelled))
            is Outcome.Toggled -> {
                val r = o.item.reminder
                val text = when {
                    !o.enabled -> s(R.string.assistant_reply_disabled, r.title)
                    o.item.next != null -> s(R.string.assistant_reply_enabled, r.title, whenText(o.item.next))
                    else -> s(R.string.assistant_reply_enabled_never, r.title)
                }
                reply(text, listOf(r))
            }
            is Outcome.Updated -> {
                val r = o.item.reminder
                val parts = mutableListOf(s(R.string.assistant_reply_updated, r.title, whenText(o.item.next ?: r.date)))
                if (r.recurrence.isRecurring) parts += s(R.string.assistant_reply_repeats, r.recurrence.summary(c))
                if (o.item.next == null) parts += s(R.string.assistant_reply_past)
                reply(parts.joinToString(" "), listOf(r))
            }
            is Outcome.NotFound -> reply(s(R.string.assistant_reply_not_found, o.query))
            is Outcome.Ambiguous -> reply(s(R.string.assistant_reply_ambiguous), o.items.take(MAX_CARDS).map { it.reminder })
            is Outcome.AskWhich -> reply(s(R.string.assistant_reply_ask_which))
            is Outcome.AskChange -> reply(s(R.string.assistant_reply_ask_change, o.item.reminder.title), listOf(o.item.reminder))
            Outcome.Greeting -> reply(s(R.string.assistant_reply_greeting))
            Outcome.Thanks -> reply(s(R.string.assistant_reply_thanks))
            Outcome.Help -> reply(s(R.string.assistant_reply_help))
            Outcome.NotUnderstood -> reply(s(R.string.assistant_reply_not_understood))
        }
    }

    fun error(lang: AssistantLang): ChatMessage =
        ChatMessage(ChatMessage.nextId(), fromUser = false, text = localized(lang).getString(R.string.assistant_reply_error))

    private fun rangeText(c: Context, range: ListRange): String = when (range) {
        ListRange.Today -> c.getString(R.string.assistant_range_today)
        ListRange.Tomorrow -> c.getString(R.string.assistant_range_tomorrow)
        ListRange.ThisWeek -> c.getString(R.string.assistant_range_week)
        ListRange.NextWeek -> c.getString(R.string.assistant_range_next_week)
        ListRange.ThisMonth -> c.getString(R.string.assistant_range_month)
        is ListRange.Day -> c.getString(
            R.string.assistant_range_day,
            range.date.format(DateTimeFormatter.ofPattern(c.getString(R.string.assistant_date_short), c.resources.configuration.locales[0])),
        )
        ListRange.All -> ""
    }

    companion object {
        const val MAX_CARDS = 8

        /** «hoy a las 20:00», «mañana a la 1:00», «el sábado 14 de noviembre a las 14:00», en el idioma de `c`. */
        fun whenText(c: Context, locale: Locale, millis: Long, zone: ZoneId, now: Long = System.currentTimeMillis()): String {
            val dt = Instant.ofEpochMilli(millis).atZone(zone)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val is24 = DateFormat.is24HourFormat(c)
            val time = dt.format(DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm a", locale))
            val hour12 = if (is24) dt.hour else (dt.hour % 12).let { if (it == 0) 12 else it }
            val at = c.getString(if (hour12 == 1) R.string.assistant_at_time_one else R.string.assistant_at_time, time)
            val day = when (dt.toLocalDate()) {
                today -> c.getString(R.string.assistant_day_today)
                today.plusDays(1) -> c.getString(R.string.assistant_day_tomorrow)
                else -> dateText(c, locale, dt.toLocalDate(), today)
            }
            return c.getString(R.string.assistant_when, day, at)
        }

        private fun dateText(c: Context, locale: Locale, date: LocalDate, today: LocalDate): String {
            val pattern = c.getString(if (date.year == today.year) R.string.assistant_date_pattern else R.string.assistant_date_pattern_year)
            return date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }

        /** El subtítulo de una tarjeta, en el idioma de la app. */
        fun cardSubtitle(c: Context, next: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            whenText(c, c.resources.configuration.locales[0], next, zone)
    }
}
