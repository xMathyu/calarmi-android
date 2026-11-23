package com.mathyusolutions.calarmi.assistant

import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** El reloj del reporte original de iOS (Tools/assistant-eval): martes 2026-11-10 18:59, Lima. */
object EvalClock {
    val zone: ZoneId = ZoneId.of("America/Lima")
    val now: LocalDateTime = LocalDateTime.of(2026, 11, 10, 18, 59)
    val millis: Long = now.atZone(zone).toInstant().toEpochMilli()

    fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun millis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()
}

class FakeStore(
    var customs: List<CustomCategory> = emptyList(),
    var lead: AlarmLeadTime = AlarmLeadTime.AT_START,
) : AssistantStore {
    val items = mutableListOf<Reminder>()
    var saves = 0
    var deletes = 0

    override suspend fun all(): List<Reminder> = items.toList()
    override suspend fun save(reminder: Reminder) {
        saves++
        items.removeAll { it.id == reminder.id }
        items += reminder
    }
    override suspend fun delete(id: String) {
        deletes++
        items.removeAll { it.id == id }
    }
    override suspend fun customCategories(): List<CustomCategory> = customs
    override fun defaultLeadTime(): AlarmLeadTime = lead
}

fun brain(store: FakeStore, model: ModelInterpreter? = null, clock: () -> Long = { EvalClock.millis }) = AssistantBrain(
    store = store,
    engine = HybridEngine(RuleEngine(), model),
    zone = { EvalClock.zone },
    clock = clock,
    defaultTitle = { lang, _ -> if (lang == AssistantLang.EN) "Alarm" else "Alarma" },
)
