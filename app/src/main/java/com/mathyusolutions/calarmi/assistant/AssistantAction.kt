package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.ReminderCategory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lo que la persona pidió, ya entendido y validado. Lo producen los dos motores
 * (el parser y Gemini Nano) y lo ejecuta `AssistantBrain`: así las reglas de
 * fechas, repeticiones y avisos son las mismas venga de donde venga.
 */
sealed interface AssistantAction {
    data class Create(val draft: AlarmDraft) : AssistantAction

    /** Hay qué recordar pero falta la hora (y no es un cumpleaños, que va a las 9). */
    data class NeedTime(val title: String?, val category: CategoryChoice) : AssistantAction

    data class ListAlarms(val range: ListRange, val category: ReminderCategory? = null) : AssistantAction

    /** Borrar, apagar, encender o buscar una alarma por su nombre. */
    data class Manage(val op: ManageOp, val query: String, val day: LocalDate? = null) : AssistantAction

    data class Update(val query: String, val changes: UpdateChanges) : AssistantAction

    data object Greeting : AssistantAction
    data object Thanks : AssistantAction
    data object Help : AssistantAction
    data object Unknown : AssistantAction
}

enum class ManageOp { DELETE, DISABLE, ENABLE, FIND }

/** Una alarma lista para guardar: fecha ya en el futuro y en el día pedido. */
data class AlarmDraft(
    /** `null` = sin nombre («mañana a las 7:30»): se usa el genérico del idioma. */
    val title: String?,
    val date: LocalDateTime,
    val recurrence: RecurrenceRule,
    val category: CategoryChoice,
    /** Minutos de anticipación (0 = a la hora). `null` = los de Ajustes. */
    val leadMinutes: List<Int>?,
    /** Otros momentos en que suena la MISMA alarma («de 2 a 4 y de 9 a 11»). */
    val extraDates: List<LocalDateTime> = emptyList(),
    /** «Despiértame…»: sin nombre se titula «Despertar». */
    val wakeUp: Boolean = false,
)

/** Categoría de fábrica o una creada por la persona (por su nombre exacto). */
sealed interface CategoryChoice {
    data class Builtin(val category: ReminderCategory) : CategoryChoice
    data class Custom(val name: String) : CategoryChoice

    val builtin: ReminderCategory? get() = (this as? Builtin)?.category

    /** Cumpleaños y aniversarios: suenan a las 9 si no hay hora y avisan un día antes. */
    val isDayEvent: Boolean
        get() = builtin == ReminderCategory.BIRTHDAY || builtin == ReminderCategory.ANNIVERSARY

    companion object {
        val REMINDER = Builtin(ReminderCategory.REMINDER)
    }
}

/** Ventanas de «¿qué tengo…?», con la misma semántica que `ListRemindersTool` de iOS. */
sealed interface ListRange {
    data object Today : ListRange
    data object Tomorrow : ListRange
    /** Los próximos 7 días desde hoy. */
    data object ThisWeek : ListRange
    data object NextWeek : ListRange
    /** Hasta dentro de un mes desde hoy. */
    data object ThisMonth : ListRange
    data class Day(val date: LocalDate) : ListRange
    data object All : ListRange
}

/** Lo que cambia «cambia el del dentista a las 11». `null` = se queda como estaba. */
data class UpdateChanges(
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val recurrence: RecurrenceRule? = null,
    val leadMinutes: List<Int>? = null,
) {
    val isEmpty: Boolean get() = time == null && date == null && recurrence == null && leadMinutes == null
}

/**
 * El resultado de un motor. `confident` = el parser entendió todo sin dudas;
 * si no, y la IA del teléfono está disponible, se le pregunta a ella.
 */
data class Interpretation(
    val action: AssistantAction,
    val confident: Boolean,
    val source: EngineKind = EngineKind.RULES,
)

enum class EngineKind { RULES, ON_DEVICE_AI }
