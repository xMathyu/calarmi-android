package com.mathyusolutions.calarmi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.AlarmSchedule
import com.mathyusolutions.calarmi.model.AlarmTone
import com.mathyusolutions.calarmi.model.CountdownEvent
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.RecurrenceRule
import com.mathyusolutions.calarmi.model.Reminder
import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

class Converters {
    @TypeConverter fun recurrenceToJson(value: RecurrenceRule): String = AppJson.encodeToString(RecurrenceRule.serializer(), value)
    @TypeConverter fun recurrenceFromJson(value: String): RecurrenceRule =
        runCatching { AppJson.decodeFromString(RecurrenceRule.serializer(), value) }.getOrDefault(RecurrenceRule.Once)

    @TypeConverter fun leadTimesToString(value: List<AlarmLeadTime>): String = value.joinToString(",") { it.seconds.toString() }
    @TypeConverter fun leadTimesFromString(value: String): List<AlarmLeadTime> =
        value.split(",").mapNotNull { it.trim().toIntOrNull()?.let(AlarmLeadTime::fromSeconds) }
            .ifEmpty { listOf(AlarmLeadTime.AT_START) }

    @TypeConverter fun schedulesToJson(value: List<AlarmSchedule>): String =
        AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(AlarmSchedule.serializer()), value)
    @TypeConverter fun schedulesFromJson(value: String): List<AlarmSchedule> = runCatching {
        AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AlarmSchedule.serializer()), value)
    }.getOrDefault(emptyList())

    @TypeConverter fun toneToString(value: AlarmTone?): String = value?.raw ?: ""
    @TypeConverter fun toneFromString(value: String): AlarmTone? = AlarmTone.fromRaw(value)

    @TypeConverter fun challengeToJson(value: WakeChallenge?): String =
        value?.let { AppJson.encodeToString(WakeChallenge.serializer(), it) } ?: ""
    @TypeConverter fun challengeFromJson(value: String): WakeChallenge? =
        if (value.isEmpty()) null else runCatching { AppJson.decodeFromString(WakeChallenge.serializer(), value) }.getOrNull()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY date")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY date")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: String): Reminder?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: String): Flow<Reminder?>

    @Upsert suspend fun upsert(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE reminders SET customCategoryId = NULL WHERE customCategoryId = :categoryId")
    suspend fun clearCustomCategory(categoryId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<CustomCategory>>

    @Query("SELECT * FROM custom_categories ORDER BY sortOrder, createdAt")
    suspend fun all(): List<CustomCategory>

    @Upsert suspend fun upsert(category: CustomCategory)
    @Delete suspend fun delete(category: CustomCategory)
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns ORDER BY date")
    fun observeAll(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdowns ORDER BY date")
    suspend fun all(): List<CountdownEvent>

    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun byId(id: String): CountdownEvent?

    @Query("SELECT * FROM countdowns WHERE id = :id")
    fun observe(id: String): Flow<CountdownEvent?>

    @Upsert suspend fun upsert(event: CountdownEvent)

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [Reminder::class, CustomCategory::class, CountdownEvent::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun categories(): CategoryDao
    abstract fun countdowns(): CountdownDao

    companion object {
        /**
         * La base vive en el almacenamiento protegido por dispositivo: así una
         * alarma suena aunque el teléfono se haya reiniciado de noche y nadie lo
         * haya desbloqueado todavía (como el Reloj del sistema).
         */
        fun create(storage: Context): AppDatabase =
            Room.databaseBuilder(storage, AppDatabase::class.java, "calarmi.db").build()
    }
}
