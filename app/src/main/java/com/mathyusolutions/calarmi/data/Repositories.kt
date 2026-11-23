package com.mathyusolutions.calarmi.data

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Lo que tiene que pasar fuera del teléfono cuando cambia algo propio: hoy,
 * avisar a lo compartido en Firebase (`sharing/`). Los repositorios lo llaman
 * después de guardar; nunca bloquea (el que lo implementa lanza su trabajo).
 *
 * Lo que llega DE Firebase no pasa por aquí: la capa de lo compartido escribe
 * directo en la base, así un cambio recibido no rebota de vuelta.
 */
interface SyncHooks {
    fun reminderSaved(saved: Reminder, previous: Reminder?) {}
    fun reminderDeleted(deleted: Reminder) {}
    fun countdownSaved(saved: CountdownEvent, previous: CountdownEvent?) {}
    fun countdownDeleted(deleted: CountdownEvent) {}

    companion object {
        val NONE = object : SyncHooks {}
    }
}

/** Avisos como texto («0,600»), igual que la columna `leadTimes`. */
fun leadTimesCsv(leadTimes: List<AlarmLeadTime>): String =
    leadTimes.distinct().sortedBy { it.seconds }.joinToString(",") { it.seconds.toString() }

fun parseLeadTimes(csv: String?): List<AlarmLeadTime>? =
    csv?.split(",")?.mapNotNull { it.trim().toIntOrNull()?.let(AlarmLeadTime::fromSeconds) }
        ?.distinct()?.sortedBy { it.seconds }?.ifEmpty { null }

/** Lo que el editor no conoce y no debe perder al guardar: a qué share está unida. */
fun Reminder.keepingSyncFields(from: Reminder?): Reminder = if (from == null) this else copy(
    shareId = from.shareId,
    isReceivedShare = from.isReceivedShare,
    sharedByName = from.sharedByName,
    shareRevision = from.shareRevision,
    sharePhotoRevision = from.sharePhotoRevision,
    ownerLeadTimes = from.ownerLeadTimes,
    personalLeadTimes = from.personalLeadTimes,
)

fun CountdownEvent.keepingSyncFields(from: CountdownEvent?): CountdownEvent = if (from == null) this else copy(
    shareId = from.shareId,
    isReceivedShare = from.isReceivedShare,
    sharedByName = from.sharedByName,
    shareRevision = from.shareRevision,
    sharePhotoRevision = from.sharePhotoRevision,
)

/**
 * Todo cambio de alarma pasa por aquí: guarda, reprograma el sistema y refresca
 * los widgets, venga del editor, la lista, el asistente o un atajo.
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val photos: PhotoStore,
    private val hooks: () -> SyncHooks = { SyncHooks.NONE },
    private val onChanged: suspend () -> Unit,
) {
    val reminders: Flow<List<Reminder>> = dao.observeAll()

    fun observe(id: String): Flow<Reminder?> = dao.observe(id)
    suspend fun all(): List<Reminder> = dao.all()
    suspend fun byId(id: String): Reminder? = dao.byId(id)

    /**
     * `syncRemote = false` guarda sin avisar a lo compartido (quien llama ya va a
     * subir el contenido él mismo, p. ej. «Invitar» justo después de guardar).
     */
    suspend fun save(reminder: Reminder, syncRemote: Boolean = true) {
        val previous = dao.byId(reminder.id)
        val saved = if (previous?.isReceivedShare == true) {
            // Una recibida es de quien la compartió: aquí solo cambian los avisos,
            // que son de esta persona. Si vuelven a ser los del dueño, se le sigue.
            val chosen = reminder.sortedLeadTimes
            val owner = parseLeadTimes(previous.ownerLeadTimes)
            previous.copy(
                leadTimes = chosen,
                personalLeadTimes = if (owner == chosen) null else leadTimesCsv(chosen),
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            reminder.keepingSyncFields(previous).copy(updatedAt = System.currentTimeMillis())
        }
        dao.upsert(saved)
        onChanged()
        if (syncRemote) hooks().reminderSaved(saved, previous)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = dao.byId(id) ?: return
        // La de otra persona la enciende o la apaga su dueño.
        if (current.isEnabled == enabled || current.isReceivedShare) return
        save(current.copy(isEnabled = enabled))
    }

    /** Borrar una recibida es salir del share; una propia compartida deja de estarlo. */
    suspend fun delete(id: String) {
        val current = dao.byId(id) ?: return
        dao.deleteById(id)
        current.photoPath?.let(photos::delete)
        onChanged()
        hooks().reminderDeleted(current)
    }
}

class CategoryRepository(private val dao: CategoryDao, private val reminders: ReminderDao) {
    val categories: Flow<List<CustomCategory>> = dao.observeAll()
    suspend fun all(): List<CustomCategory> = dao.all()
    suspend fun save(category: CustomCategory) = dao.upsert(category)

    /** Las alarmas de la categoría borrada vuelven a su categoría de fábrica. */
    suspend fun delete(category: CustomCategory) {
        reminders.clearCustomCategory(category.id)
        dao.delete(category)
    }
}

class CountdownRepository(
    private val dao: CountdownDao,
    private val photos: PhotoStore,
    private val hooks: () -> SyncHooks = { SyncHooks.NONE },
    private val onChanged: suspend () -> Unit,
) {
    val countdowns: Flow<List<CountdownEvent>> = dao.observeAll()

    fun observe(id: String): Flow<CountdownEvent?> = dao.observe(id)
    suspend fun all(): List<CountdownEvent> = dao.all()
    suspend fun byId(id: String): CountdownEvent? = dao.byId(id)

    suspend fun save(event: CountdownEvent, syncRemote: Boolean = true) {
        val previous = dao.byId(event.id)
        // Una recibida no se edita: es de quien la compartió.
        if (previous?.isReceivedShare == true) return
        val saved = event.keepingSyncFields(previous).copy(updatedAt = System.currentTimeMillis())
        dao.upsert(saved)
        onChanged()
        if (syncRemote) hooks().countdownSaved(saved, previous)
    }

    suspend fun delete(id: String) {
        val current = dao.byId(id) ?: return
        dao.deleteById(id)
        current.photoPath?.let(photos::delete)
        onChanged()
        hooks().countdownDeleted(current)
    }
}
