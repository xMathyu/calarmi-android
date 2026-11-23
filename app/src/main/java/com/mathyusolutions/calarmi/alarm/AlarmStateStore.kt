package com.mathyusolutions.calarmi.alarm

import android.content.Context
import com.mathyusolutions.calarmi.data.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Pospuestas pendientes. */
@Serializable
data class SnoozeEntry(val fireAt: Long, val item: RingItem)

/**
 * El estado del motor que tiene que sobrevivir a que el sistema mate el proceso:
 * hasta dónde se atendieron los disparos, lo pospuesto y lo saltado.
 */
class AlarmStateStore(storage: Context) {
    private val prefs = storage.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)
    private val lock = Any()

    /** Los disparos hasta este instante ya se atendieron (o se descartaron). */
    var lastHandledAt: Long
        get() = prefs.getLong(K_LAST, 0L)
        set(value) = prefs.edit().putLong(K_LAST, value).apply()

    fun snoozes(): List<SnoozeEntry> = synchronized(lock) {
        prefs.getString(K_SNOOZES, null)?.let {
            runCatching { AppJson.decodeFromString(ListSerializer(SnoozeEntry.serializer()), it) }.getOrNull()
        } ?: emptyList()
    }

    private fun writeSnoozes(list: List<SnoozeEntry>) {
        prefs.edit().putString(K_SNOOZES, AppJson.encodeToString(ListSerializer(SnoozeEntry.serializer()), list)).apply()
    }

    /** Pospone `item`: queda UNA sola pospuesta por alarma (los avisos no se apilan). */
    fun addSnooze(item: RingItem, fireAt: Long) = synchronized(lock) {
        val others = snoozes().filter { it.item.sourceId != item.sourceId }
        val snoozed = item.copy(key = "snooze:${item.sourceId}:$fireAt", fireAt = fireAt, isSnooze = true)
        writeSnoozes(others + SnoozeEntry(fireAt, snoozed))
    }

    /** «Detener» calla también lo pospuesto de la misma alarma. */
    fun clearSnoozes(sourceId: String) = synchronized(lock) {
        writeSnoozes(snoozes().filter { it.item.sourceId != sourceId })
    }

    fun removeSnoozes(keys: Set<String>) = synchronized(lock) {
        writeSnoozes(snoozes().filter { it.item.key !in keys })
    }

    fun pruneSnoozes(before: Long) = synchronized(lock) {
        writeSnoozes(snoozes().filter { it.fireAt >= before })
    }

    /** Disparos que la persona saltó desde la cuenta regresiva previa. */
    fun skipped(): Set<String> = prefs.getString(K_SKIPPED, null)?.let {
        runCatching { AppJson.decodeFromString(SetSerializer(String.serializer()), it) }.getOrNull()
    } ?: emptySet()

    fun skip(key: String) = synchronized(lock) {
        // Las claves llevan la hora: con las de los últimos días alcanza.
        val keep = skipped().takeLast(50).toSet() + key
        prefs.edit().putString(K_SKIPPED, AppJson.encodeToString(SetSerializer(String.serializer()), keep)).apply()
    }

    /** La hora para la que se programó la alarma del sistema (diagnóstico y widget). */
    var scheduledFor: Long
        get() = prefs.getLong(K_SCHEDULED, 0L)
        set(value) = prefs.edit().putLong(K_SCHEDULED, value).apply()

    private fun Set<String>.takeLast(n: Int): List<String> = toList().takeLast(n)

    private companion object {
        const val K_LAST = "lastHandledAt"
        const val K_SNOOZES = "snoozes"
        const val K_SKIPPED = "skipped"
        const val K_SCHEDULED = "scheduledFor"
    }
}
