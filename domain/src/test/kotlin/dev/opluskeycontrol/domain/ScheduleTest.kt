package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private fun at(value: String) = ZonedDateTime.parse(value)
    private val daily = Schedule(360, 1080)
    @Test fun startInclusiveEndExclusive() {
        assertFalse(daily.matches(at("2026-10-22T05:59:59+07:00")))
        assertTrue(daily.matches(at("2026-10-22T06:00:00+07:00")))
        assertTrue(daily.matches(at("2026-10-22T17:59:59+07:00")))
        assertFalse(daily.matches(at("2026-10-22T18:00:00+07:00")))
    }
    @Test fun midnightAnd2359() {
        val night = Schedule(1320, 360)
        assertTrue(night.matches(at("2026-10-22T00:00:00+07:00")))
        assertTrue(night.matches(at("2026-10-22T23:59:59+07:00")))
        assertFalse(night.matches(at("2026-10-22T06:00:00+07:00")))
    }
    @Test fun overnightDaysAreStartDays() {
        val friday = Schedule(1320, 360, setOf(DayOfWeek.FRIDAY))
        assertTrue(friday.matches(at("2026-10-23T22:00:00+07:00")))
        assertTrue(friday.matches(at("2026-10-24T05:59:00+07:00")))
        assertFalse(friday.matches(at("2026-10-23T05:59:00+07:00")))
        assertFalse(friday.matches(at("2026-10-24T22:00:00+07:00")))
    }
    @Test fun sundayWrapsToMonday() {
        val sunday = Schedule(1380, 60, setOf(DayOfWeek.SUNDAY))
        assertTrue(sunday.matches(at("2026-10-26T00:30:00+07:00")))
        assertTrue(sunday.overlaps(Schedule(0, 30, setOf(DayOfWeek.MONDAY))))
    }
    @Test fun weekdays() {
        val monday = Schedule(0, 1439, setOf(DayOfWeek.MONDAY))
        assertTrue(monday.matches(at("2026-10-26T12:00:00+07:00")))
        assertFalse(monday.matches(at("2026-10-27T12:00:00+07:00")))
    }
    @Test fun zoneChangeUsesCurrentLocalTime() {
        val instant = Instant.parse("2026-10-22T00:00:00Z")
        assertTrue(daily.matches(instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))))
        assertFalse(daily.matches(instant.atZone(ZoneId.of("UTC"))))
    }
    @Test fun dstRepeatedTimeMatchesBothOffsets() {
        val schedule = Schedule(60, 120)
        assertTrue(schedule.matches(Instant.parse("2026-11-01T05:30:00Z").atZone(ZoneId.of("America/New_York"))))
        assertTrue(schedule.matches(Instant.parse("2026-11-01T06:30:00Z").atZone(ZoneId.of("America/New_York"))))
    }
    @Test fun dstSkippedHourDoesNotInventEvents() {
        val schedule = Schedule(120, 180)
        val zone = ZoneId.of("America/New_York")
        assertFalse(schedule.matches(Instant.parse("2026-03-08T07:00:00Z").atZone(zone)))
    }
    @Test fun adjacentWindowsDoNotOverlap() { assertFalse(Schedule(360, 720).overlaps(Schedule(720, 1080))) }
    @Test fun overlappingWindowsDetected() { assertTrue(Schedule(1320, 360).overlaps(Schedule(0, 400))) }
    @Test fun differentDaysDoNotOverlap() { assertFalse(Schedule(0, 60, setOf(DayOfWeek.MONDAY)).overlaps(Schedule(0, 60, setOf(DayOfWeek.TUESDAY)))) }
    @Test(expected = IllegalArgumentException::class) fun equalBoundsRejected() { Schedule(0, 0) }
    @Test(expected = IllegalArgumentException::class) fun noDaysRejected() { Schedule(0, 60, emptySet()) }
}
