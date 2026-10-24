package com.bookflow.app.domain

import com.bookflow.app.domain.model.ReadingLog
import com.bookflow.app.domain.model.ReadingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadingStatsTest {
    private val today = LocalDate.of(2026, 10, 2)
    private val goal = 15
    private val met = 15 * 60L

    @Test fun unfinishedTodayKeepsStreakThroughYesterday() {
        val log = mapOf(today to 120L, today.minusDays(1) to met, today.minusDays(2) to met + 30, today.minusDays(3) to 60L)
        val stats = ReadingStats.from(log, goal, today)
        assertEquals(2, stats.streakDays)
        assertEquals(120L, stats.todaySeconds)
        assertFalse(stats.isGoalMet)
    }

    @Test fun meetingTodayExtendsStreak() {
        val log = mapOf(today to met, today.minusDays(1) to met)
        val stats = ReadingStats.from(log, goal, today)
        assertEquals(2, stats.streakDays)
        assertTrue(stats.isGoalMet)
        assertEquals(1f, stats.goalProgress)
    }

    @Test fun gapBreaksStreak() {
        val log = mapOf(today.minusDays(2) to met, today.minusDays(3) to met)
        assertEquals(0, ReadingStats.from(log, goal, today).streakDays)
    }

    @Test fun lastSevenDaysEndsTodayOldestFirst() {
        val stats = ReadingStats.from(mapOf(today.minusDays(6) to 300L), goal, today)
        assertEquals(7, stats.lastSevenDays.size)
        assertEquals(today.minusDays(6), stats.lastSevenDays.first().date)
        assertEquals(300L, stats.lastSevenDays.first().seconds)
        assertEquals(today, stats.lastSevenDays.last().date)
    }

    @Test fun logRoundTripsAndDropsOldDays() {
        val log = mapOf(today to 900L, today.minusDays(1) to 30L, today.minusDays(ReadingLog.RETAINED_DAYS + 1) to 50L)
        val decoded = ReadingLog.decode(ReadingLog.encode(log, today))
        assertEquals(mapOf(today to 900L, today.minusDays(1) to 30L), decoded)
    }

    @Test fun malformedLogEntriesAreSkipped() {
        assertEquals(mapOf(today to 60L), ReadingLog.decode("garbage;2026-10-02=60;2026-13-40=5;=;2026-10-01=x"))
        assertTrue(ReadingLog.decode(null).isEmpty())
    }
}
