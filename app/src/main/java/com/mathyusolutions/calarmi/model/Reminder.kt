package com.mathyusolutions.calarmi.model

import androidx.room.ColumnInfo
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
    // --- Compartir (Firestore, `sharing/`). El editor no los toca: el repositorio
    // los conserva al guardar y solo los cambia la capa de lo compartido.
    /**
     * El `shares/{id}` al que está unida. En una propia es su mismo `id` (está
     * compartida); en una recibida, el del dueño (que también es su `id` local).
     */
    val shareId: String? = null,
    /** La compartió otra persona: el contenido es suyo y aquí solo se mira. */
    @ColumnInfo(defaultValue = "0") val isReceivedShare: Boolean = false,
    /** Recibidas: el nombre de quien la compartió. */
    val sharedByName: String? = null,
    /** Recibidas: la `revision` del share que ya se aplicó. */
    @ColumnInfo(defaultValue = "0") val shareRevision: Long = 0,
    /** El `photoRevision` de la foto ya subida (propias) o ya bajada (recibidas). */
    @ColumnInfo(defaultValue = "0") val sharePhotoRevision: Long = 0,
    /** Recibidas: los avisos del dueño tal como llegaron (segundos separados por comas). */
    val ownerLeadTimes: String? = null,
    /** Recibidas: los avisos que puso esta persona; `null` sigue los del dueño. */
    val personalLeadTimes: String? = null,
) {
    /** Propia y compartida con otras personas. */
    val isSharedByMe: Boolean get() = shareId != null && !isReceivedShare

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
