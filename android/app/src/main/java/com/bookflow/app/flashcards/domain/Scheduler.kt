package com.bookflow.app.flashcards.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * SM-2 style spaced repetition (as popularised by Anki). Pure Kotlin: time zone and clock are inputs.
 *
 * - New/learning steps: 1 min → 10 min → graduate at 1 day; Easy graduates at 4 days.
 * - Review: Again lapses into relearning (10 min) with the interval halved; Hard ×1.2; Good × ease;
 *   Easy × ease × 1.3. Ease starts at 2.5, never below 1.3. Each button gives a longer interval than the one
 *   before it, and intervals of 3+ days get ±5% fuzz so cards added together spread out.
 * - Review days roll over at 04:00 local time.
 */
class Scheduler(private val timeZone: TimeZone = TimeZone.getDefault()) {

    fun answer(s: Schedule, rating: Rating, now: Long): Schedule = when (s.state) {
        CardState.NEW, CardState.LEARNING -> learning(s, rating, now, LEARNING_STEPS_MIN)
        CardState.RELEARNING -> relearning(s, rating, now)
        CardState.REVIEW -> review(s, rating, now)
    }

    /** Next-interval label for each button, e.g. "<1m", "10m", "4d", "3.2mo". */
    fun preview(s: Schedule, now: Long): Map<Rating, String> = Rating.entries.associateWith { r ->
        val next = answer(s, r, now)
        // Review intervals read as days ("4d"), not the hours left until the 04:00 rollover
        if (next.state == CardState.REVIEW) label(next.intervalDays.roundToLong().coerceAtLeast(1) * 86_400_000L) else label(next.dueAt - now)
    }

    private fun learning(s: Schedule, rating: Rating, now: Long, steps: List<Float>): Schedule {
        val step = if (s.state == CardState.NEW) 0 else s.step
        return when (rating) {
            Rating.AGAIN -> s.copy(state = CardState.LEARNING, step = 0, dueAt = now + minutes(steps[0]), reps = s.reps + 1)
            Rating.HARD -> {
                // Hard repeats the current step; on the first step it waits halfway to the next one
                val delay = if (step == 0 && steps.size > 1) (steps[0] + steps[1]) / 2 else steps[step.coerceAtMost(steps.lastIndex)]
                s.copy(state = CardState.LEARNING, step = step, dueAt = now + minutes(delay), reps = s.reps + 1)
            }
            Rating.GOOD -> if (step + 1 < steps.size) s.copy(state = CardState.LEARNING, step = step + 1, dueAt = now + minutes(steps[step + 1]), reps = s.reps + 1)
                else graduate(s, GRADUATING_DAYS, now)
            Rating.EASY -> graduate(s, EASY_DAYS, now)
        }
    }

    private fun relearning(s: Schedule, rating: Rating, now: Long): Schedule = when (rating) {
        Rating.AGAIN -> s.copy(step = 0, dueAt = now + minutes(RELEARNING_STEP_MIN), reps = s.reps + 1)
        Rating.HARD -> s.copy(dueAt = now + minutes(RELEARNING_STEP_MIN * 1.5f), reps = s.reps + 1)
        Rating.GOOD -> graduate(s, max(1f, s.intervalDays), now)
        Rating.EASY -> graduate(s, max(1f, s.intervalDays) + 1f, now)
    }

    private fun review(s: Schedule, rating: Rating, now: Long): Schedule {
        val interval = max(1f, s.intervalDays)
        if (rating == Rating.AGAIN) {
            return s.copy(state = CardState.RELEARNING, step = 0, ease = (s.ease - 0.2f).coerceAtLeast(MIN_EASE),
                intervalDays = max(1f, interval * LAPSE_MULTIPLIER), dueAt = now + minutes(RELEARNING_STEP_MIN),
                lapses = s.lapses + 1, reps = s.reps + 1)
        }
        val hard = max(interval + 1, interval * HARD_MULTIPLIER)
        val good = max(hard + 1, interval * s.ease)
        val easy = max(good + 1, interval * s.ease * EASY_BONUS)
        val (days, ease) = when (rating) {
            Rating.HARD -> hard to s.ease - 0.15f
            Rating.GOOD -> good to s.ease
            else -> easy to s.ease + 0.15f
        }
        val fuzzed = fuzz(days, s.cardId, s.ordinal, s.reps)
        return s.copy(state = CardState.REVIEW, ease = ease.coerceAtLeast(MIN_EASE), intervalDays = fuzzed,
            dueAt = dayStartAfter(now, fuzzed.roundToLong().coerceAtLeast(1)), reps = s.reps + 1)
    }

    private fun graduate(s: Schedule, days: Float, now: Long) =
        s.copy(state = CardState.REVIEW, step = 0, intervalDays = days, dueAt = dayStartAfter(now, days.roundToLong().coerceAtLeast(1)), reps = s.reps + 1)

    private fun fuzz(days: Float, cardId: String, ordinal: Int, reps: Int): Float {
        if (days < 3f) return days
        val random = Random((cardId.hashCode() * 31 + ordinal) * 31 + reps)
        return days * (1f + (random.nextFloat() * 2f - 1f) * FUZZ)
    }

    /** 04:00 local time on the day [days] review days after [now]'s review day. */
    // Calendar arithmetic, so daylight-saving changes never move the 04:00 boundary
    fun dayStartAfter(now: Long, days: Long): Long =
        Calendar.getInstance(timeZone).apply { timeInMillis = reviewDayStart(now); add(Calendar.DAY_OF_YEAR, days.toInt()) }.timeInMillis

    /** Start (04:00 local) of the review day containing [now]. */
    fun reviewDayStart(now: Long): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        if (get(Calendar.HOUR_OF_DAY) < DAY_ROLLOVER_HOUR) add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, DAY_ROLLOVER_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** End of today's review day: reviews due before this count as due today. */
    fun reviewDayEnd(now: Long): Long = dayStartAfter(now, 1)

    companion object {
        const val START_EASE = 2.5f
        const val MIN_EASE = 1.3f
        const val GRADUATING_DAYS = 1f
        const val EASY_DAYS = 4f
        const val RELEARNING_STEP_MIN = 10f
        const val HARD_MULTIPLIER = 1.2f
        const val EASY_BONUS = 1.3f
        const val LAPSE_MULTIPLIER = 0.5f
        const val FUZZ = 0.05f
        const val DAY_ROLLOVER_HOUR = 4
        val LEARNING_STEPS_MIN = listOf(1f, 10f)

        private fun minutes(m: Float) = (m * 60_000).roundToLong()

        fun label(deltaMs: Long): String {
            val minutes = deltaMs / 60_000.0
            return when {
                minutes < 1 -> "<1m"
                minutes < 60 -> "${minutes.roundToLong()}m"
                minutes < 60 * 24 -> "${(minutes / 60).roundToLong()}h"
                minutes < 60 * 24 * 30 -> "${(minutes / 1440).roundToLong()}d"
                minutes < 60 * 24 * 365 -> String.format(Locale.ROOT, "%.1fmo", minutes / 43_200)
                else -> String.format(Locale.ROOT, "%.1fy", minutes / 525_600)
            }
        }
    }
}
