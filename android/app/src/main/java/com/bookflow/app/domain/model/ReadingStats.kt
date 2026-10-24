package com.bookflow.app.domain.model

import java.time.LocalDate

/** Seconds spent reading on one calendar day. */
data class DayReading(val date: LocalDate, val seconds: Long) {
    val minutes: Int get() = (seconds / 60).toInt()
}

/**
 * Daily reading goal progress, like Apple Books' Reading Goals ring.
 *
 * @property streakDays consecutive days that met the goal, counting today only once it is met
 *   (so an unfinished today doesn't break a streak that ran through yesterday).
 * @property lastSevenDays oldest first, ending today.
 */
data class ReadingStats(
    val todaySeconds: Long = 0,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
    val streakDays: Int = 0,
    val lastSevenDays: List<DayReading> = emptyList()
) {
    val todayMinutes: Int get() = (todaySeconds / 60).toInt()
    val goalSeconds: Long get() = dailyGoalMinutes * 60L
    val goalProgress: Float get() = if (goalSeconds == 0L) 0f else (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    val isGoalMet: Boolean get() = todaySeconds >= goalSeconds

    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 15
        val GOAL_RANGE_MINUTES = 5..180

        fun from(secondsByDay: Map<LocalDate, Long>, dailyGoalMinutes: Int, today: LocalDate): ReadingStats {
            val goalSeconds = dailyGoalMinutes * 60L
            fun met(day: LocalDate) = (secondsByDay[day] ?: 0L) >= goalSeconds
            var day = if (met(today)) today else today.minusDays(1)
            var streak = 0
            while (met(day)) {
                streak++
                day = day.minusDays(1)
            }
            return ReadingStats(
                todaySeconds = secondsByDay[today] ?: 0L,
                dailyGoalMinutes = dailyGoalMinutes,
                streakDays = streak,
                lastSevenDays = (6 downTo 0).map { back ->
                    val date = today.minusDays(back.toLong())
                    DayReading(date, secondsByDay[date] ?: 0L)
                }
            )
        }
    }
}

/** Compact storage for per-day reading seconds: "2026-10-02=900;2026-10-01=300". */
object ReadingLog {
    /** Days older than this are dropped on write; plenty for streaks and the weekly chart. */
    const val RETAINED_DAYS = 400L

    fun decode(raw: String?): Map<LocalDate, Long> =
        raw.orEmpty().split(";").mapNotNull { entry ->
            val (date, seconds) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
            val parsedSeconds = seconds.toLongOrNull() ?: return@mapNotNull null
            parsedDate to parsedSeconds
        }.toMap()

    fun encode(secondsByDay: Map<LocalDate, Long>, today: LocalDate): String =
        secondsByDay
            .filterKeys { !it.isBefore(today.minusDays(RETAINED_DAYS)) }
            .toSortedMap(compareByDescending { it })
            .entries.joinToString(";") { (date, seconds) -> "$date=$seconds" }
}
