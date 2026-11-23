package com.mathyusolutions.calarmi.data

import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Todo cambio de alarma pasa por aquí: guarda, reprograma el sistema y refresca
 * los widgets, venga del editor, la lista, el asistente o un atajo.
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val photos: PhotoStore,
    private val onChanged: suspend () -> Unit,
) {
    val reminders: Flow<List<Reminder>> = dao.observeAll()

    fun observe(id: String): Flow<Reminder?> = dao.observe(id)
    suspend fun all(): List<Reminder> = dao.all()
    suspend fun byId(id: String): Reminder? = dao.byId(id)

    suspend fun save(reminder: Reminder) {
        dao.upsert(reminder.copy(updatedAt = System.currentTimeMillis()))
        onChanged()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = dao.byId(id) ?: return
        if (current.isEnabled == enabled) return
        save(current.copy(isEnabled = enabled))
    }

    suspend fun delete(id: String) {
        val current = dao.byId(id) ?: return
        dao.deleteById(id)
        current.photoPath?.let(photos::delete)
        onChanged()
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
    private val onChanged: suspend () -> Unit,
) {
    val countdowns: Flow<List<CountdownEvent>> = dao.observeAll()

    fun observe(id: String): Flow<CountdownEvent?> = dao.observe(id)
    suspend fun all(): List<CountdownEvent> = dao.all()
    suspend fun byId(id: String): CountdownEvent? = dao.byId(id)

    suspend fun save(event: CountdownEvent) {
        dao.upsert(event.copy(updatedAt = System.currentTimeMillis()))
        onChanged()
    }

    suspend fun delete(id: String) {
        val current = dao.byId(id) ?: return
        dao.deleteById(id)
        current.photoPath?.let(photos::delete)
        onChanged()
    }
}
