package com.mathyusolutions.calarmi.domain

import com.mathyusolutions.calarmi.model.Reminder

/** La próxima vez que suena la alarma (sin contar avisos), en cualquiera de sus horarios. */
fun Reminder.nextOccurrence(now: Long = System.currentTimeMillis()): Long? =
    allSchedules.mapNotNull { RecurrenceEngine.nextOccurrences(it.recurrence, it.date, 1, now).firstOrNull() }.minOrNull()

/**
 * El próximo instante en que la alarma tiene que SONAR después de `after`
 * (ocurrencia menos anticipación), o `null` si ya no suena más.
 */
fun Reminder.nextFireAfter(after: Long): Long? {
    var best: Long? = null
    for (schedule in allSchedules) {
        for (lead in sortedLeadTimes) {
            val occurrence = RecurrenceEngine.nextOccurrences(
                schedule.recurrence, schedule.date, count = 1, now = after + lead.millis,
            ).firstOrNull() ?: continue
            val fire = occurrence - lead.millis
            if (best == null || fire < best) best = fire
        }
    }
    return best
}

/** La ocurrencia a la que corresponde un disparo en `fireAt` (para el texto «en 15 min»). */
fun Reminder.occurrenceForFire(fireAt: Long): Pair<Long, Long>? {
    for (schedule in allSchedules) {
        for (lead in sortedLeadTimes) {
            val occurrence = fireAt + lead.millis
            val hit = RecurrenceEngine.nextOccurrences(schedule.recurrence, schedule.date, 1, occurrence - 1).firstOrNull()
            if (hit == occurrence) return occurrence to lead.millis
        }
    }
    return null
}
