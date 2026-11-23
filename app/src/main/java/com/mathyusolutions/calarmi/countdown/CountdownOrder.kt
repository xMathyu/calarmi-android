package com.mathyusolutions.calarmi.countdown

import com.mathyusolutions.calarmi.model.CountdownEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * El orden de las cuentas regresivas, igual en la lista, el widget y su
 * configuración. Sin nada de Android para poder probarlo en la JVM.
 */
object CountdownOrder {
    data class Groups(val upcoming: List<CountdownEvent>, val past: List<CountdownEvent>)

    /**
     * Los próximos (y los de hoy) del más cercano al más lejano; los pasados,
     * del más reciente al más antiguo.
     */
    fun groups(events: List<CountdownEvent>, now: Long): Groups {
        val withTargets = events.map { it to it.target(now) }
        val (past, upcoming) = withTargets.partition { (event, _) -> event.status(now).isPast }
        return Groups(
            upcoming = upcoming.sortedBy { it.second }.map { it.first },
            past = past.sortedByDescending { it.second }.map { it.first },
        )
    }

    /**
     * La que enseña un widget sin evento elegido (o si el elegido ya no existe):
     * la próxima; si todas pasaron, la más reciente.
     */
    fun default(events: List<CountdownEvent>, now: Long): CountdownEvent? {
        val groups = groups(events, now)
        return groups.upcoming.firstOrNull() ?: groups.past.firstOrNull()
    }

    /** Para elegir evento al configurar el widget: próximos primero. */
    fun forPicker(events: List<CountdownEvent>, now: Long): List<CountdownEvent> =
        groups(events, now).let { it.upcoming + it.past }
}

/** Días, horas, minutos y segundos que faltan hasta la hora exacta. */
data class CountdownBreakdown(val days: Long, val hours: Int, val minutes: Int, val seconds: Int) {
    companion object {
        /**
         * Nulo si la hora ya llegó. Los días son de calendario (un cambio de
         * horario no deja «0 días 23 horas» donde se esperaba «1 día»).
         */
        fun between(now: Long, target: Long, zone: ZoneId = ZoneId.systemDefault()): CountdownBreakdown? {
            if (target <= now) return null
            val from = Instant.ofEpochMilli(now).atZone(zone)
            val to = Instant.ofEpochMilli(target).atZone(zone)
            var days = ChronoUnit.DAYS.between(from, to)
            if (from.plusDays(days).isAfter(to)) days -= 1
            val rest = Duration.between(from.plusDays(days), to).seconds
            return CountdownBreakdown(
                days = days,
                hours = (rest / 3600).toInt(),
                minutes = ((rest % 3600) / 60).toInt(),
                seconds = (rest % 60).toInt(),
            )
        }
    }
}

object CountdownDefaults {
    /**
     * Dentro de un mes a las 20:00: la hora típica de un concierto, por si la
     * persona enciende «Hora».
     */
    fun date(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val day: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusMonths(1)
        return ZonedDateTime.of(day, LocalTime.of(20, 0), zone).toInstant().toEpochMilli()
    }

    /** Sin hora se guarda el inicio del día, como en iOS. */
    fun normalized(date: Long, includesTime: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long =
        if (includesTime) {
            Instant.ofEpochMilli(date).atZone(zone).withSecond(0).withNano(0).toInstant().toEpochMilli()
        } else {
            Instant.ofEpochMilli(date).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        }

    /** Los símbolos que se ofrecen primero en el selector de ícono. */
    val suggestedSymbols = listOf(
        "music.mic", "ticket.fill", "airplane", "suitcase.rolling.fill",
        "birthday.cake.fill", "gift.fill", "heart.fill", "party.popper.fill",
        "graduationcap.fill", "sportscourt.fill", "film.fill", "star.fill",
    )
}
