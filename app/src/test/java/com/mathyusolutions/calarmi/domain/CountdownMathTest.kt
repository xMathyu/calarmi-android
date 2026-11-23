package com.mathyusolutions.calarmi.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownMathTest {
    private val zone = ZoneId.of("America/Lima")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun tomorrowMorningIsOneDayEvenLateTonight() {
        val status = CountdownMath.status(at("2026-11-24T08:00"), at("2026-11-23T23:00"), zone)
        assertEquals(CountdownStatus.Upcoming(1), status)
    }

    @Test fun yearlyTodayCountsAsToday() {
        val target = CountdownMath.target(at("1990-11-23T00:00"), includesTime = false, repeatsYearly = true, now = at("2026-11-23T18:00"), zone = zone)
        assertEquals(CountdownStatus.Today, CountdownMath.status(target, at("2026-11-23T18:00"), zone))
    }

    @Test fun yearlyPastJumpsToNextYear() {
        val target = CountdownMath.target(at("1990-03-10T00:00"), false, true, at("2026-11-23T12:00"), zone)
        assertEquals(at("2027-03-10T00:00"), target)
    }

    @Test fun leapDayFallsOn28th() {
        val target = CountdownMath.target(at("2024-02-29T00:00"), false, true, at("2026-01-01T12:00"), zone)
        assertEquals(at("2026-02-28T00:00"), target)
    }

    @Test fun pastOneOff() {
        assertEquals(CountdownStatus.Past(3), CountdownMath.status(at("2026-11-20T10:00"), at("2026-11-23T09:00"), zone))
    }
}
