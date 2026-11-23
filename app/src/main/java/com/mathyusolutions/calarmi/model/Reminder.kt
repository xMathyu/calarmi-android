package com.mathyusolutions.calarmi.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Una alarma de Calarmi. Mismos campos que el `Reminder` de iOS; las fechas en
 * milisegundos desde 1970 y la foto como archivo propio (`photoPath`).
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String? = null,
    /** Fecha y hora del horario principal. */
    val date: Long = System.currentTimeMillis(),
    val categoryRaw: Int = ReminderCategory.REMINDER.raw,
    /** Categoría creada por la persona; `null` usa `categoryRaw`. */
    val customCategoryId: String? = null,
    val iconKindRaw: Int = ReminderIconKind.SYMBOL.raw,
    /** Nombre de SF Symbol o emoji, según `iconKind`. */
    val symbolName: String? = null,
    /** Nombre del archivo en `PhotoStore`. */
    val photoPath: String? = null,
    val recurrence: RecurrenceRule = RecurrenceRule.Once,
    /** Al menos uno; ordenados de menor a mayor anticipación. */
    val leadTimes: List<AlarmLeadTime> = listOf(AlarmLeadTime.AT_START),
    val additionalSchedules: List<AlarmSchedule> = emptyList(),
    /** `null` sigue el tono predeterminado de Ajustes. */
    val tone: AlarmTone? = null,
    val challenge: WakeChallenge? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val category: ReminderCategory get() = ReminderCategory.fromRaw(categoryRaw)
    val iconKind: ReminderIconKind get() = ReminderIconKind.fromRaw(iconKindRaw)

    /** El horario principal seguido de los adicionales. */
    val allSchedules: List<AlarmSchedule>
        get() = listOf(AlarmSchedule(id = id, date = date, recurrence = recurrence)) + additionalSchedules

    /** El reto, solo si se puede resolver (un QR sin código no cuenta). */
    val readyChallenge: WakeChallenge? get() = challenge?.takeIf { it.isReady }

    val sortedLeadTimes: List<AlarmLeadTime>
        get() = leadTimes.distinct().sortedBy { it.seconds }.ifEmpty { listOf(AlarmLeadTime.AT_START) }
}
