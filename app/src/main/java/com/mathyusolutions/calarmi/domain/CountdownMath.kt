package com.mathyusolutions.calarmi.domain

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * En qué punto está una cuenta, en días de calendario (no en bloques de 24 h:
 * un evento mañana a las 8 «falta 1 día» aunque sean las 23:00 de hoy).
 */
sealed interface CountdownStatus {
    data class Upcoming(val days: Int) : CountdownStatus
    data object Today : CountdownStatus
    data class Past(val days: Int) : CountdownStatus

    val isPast: Boolean get() = this is Past
}

object CountdownMath {
    /**
     * El instante al que apunta la cuenta: la fecha guardada o, para las anuales,
     * el próximo aniversario (hoy incluido). Un 29 de febrero cae el 28 en los
     * años que no son bisiestos.
     */
    fun target(
        date: Long,
        includesTime: Boolean,
        repeatsYearly: Boolean,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val original = Instant.ofEpochMilli(date).atZone(zone)
        val anchor = if (includesTime) original else original.toLocalDate().atStartOfDay(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone)
        if (!repeatsYearly || !anchor.isBefore(startOfToday)) return anchor.toInstant().toEpochMilli()

        val monthDay = MonthDay.from(original)
        fun onYear(year: Int): ZonedDateTime {
            val day: LocalDate = if (monthDay.isValidYear(year)) monthDay.atYear(year)
            else LocalDate.of(year, monthDay.month, monthDay.month.length(java.time.Year.isLeap(year.toLong())))
            return if (includesTime) ZonedDateTime.of(day, original.toLocalTime().withSecond(0).withNano(0), zone)
            else day.atStartOfDay(zone)
        }
        var candidate = onYear(today.year)
        // «Hoy» cuenta: solo se pasa al año siguiente si el día ya quedó atrás.
        if (candidate.toLocalDate().isBefore(today)) candidate = onYear(today.year + 1)
        return candidate.toInstant().toEpochMilli()
    }

    fun status(target: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): CountdownStatus {
        val days = dayDelta(now, target, zone)
        return when {
            days > 0 -> CountdownStatus.Upcoming(days)
            days == 0 -> CountdownStatus.Today
            else -> CountdownStatus.Past(-days)
        }
    }

    /** Días de calendario entre las dos fechas (negativo si `target` ya pasó). */
    fun dayDelta(now: Long, target: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val a = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(target).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(a, b).toInt()
    }
}
