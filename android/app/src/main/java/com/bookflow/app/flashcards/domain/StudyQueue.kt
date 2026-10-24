package com.bookflow.app.flashcards.domain

/**
 * What to study now and the deck counts shown before studying. Order: learning steps that are due (including
 * up to [LEARN_AHEAD_MS] early, so a session does not stall on a 1-minute step), then reviews due today, then
 * new cards in the order they were added. Daily limits subtract what was already studied today.
 */
object StudyQueue {
    const val LEARN_AHEAD_MS = 20 * 60_000L

    data class Limits(val newPerDay: Int, val reviewsPerDay: Int, val newStudiedToday: Int, val reviewsDoneToday: Int) {
        val newLeft get() = (newPerDay - newStudiedToday).coerceAtLeast(0)
        val reviewsLeft get() = (reviewsPerDay - reviewsDoneToday).coerceAtLeast(0)
    }

    /** New cards keep their creation time in [Schedule.dueAt], which gives their study order. */
    fun build(schedules: List<Schedule>, limits: Limits, now: Long, dayEnd: Long): List<Schedule> {
        val learning = schedules.filter { it.isLearning && it.dueAt <= now + LEARN_AHEAD_MS }.sortedBy { it.dueAt }
        val reviews = schedules.filter { it.state == CardState.REVIEW && it.dueAt < dayEnd }.sortedBy { it.dueAt }.take(limits.reviewsLeft)
        val new = schedules.filter { it.state == CardState.NEW }.sortedWith(compareBy({ it.dueAt }, { it.cardId }, { it.ordinal })).take(limits.newLeft)
        return learning + reviews + new
    }

    fun counts(schedules: List<Schedule>, limits: Limits, dayEnd: Long) = DeckCounts(
        new = schedules.count { it.state == CardState.NEW }.coerceAtMost(limits.newLeft),
        learning = schedules.count { it.isLearning && it.dueAt < dayEnd },
        due = schedules.count { it.state == CardState.REVIEW && it.dueAt < dayEnd }.coerceAtMost(limits.reviewsLeft),
        total = schedules.map { it.cardId }.distinct().size
    )

    private val Schedule.isLearning get() = state == CardState.LEARNING || state == CardState.RELEARNING

    /** Reviewable sides of a card: forward only, forward and reverse, or one per cloze number. */
    fun ordinals(card: Card): List<Int> = when (card.type) {
        CardType.BASIC -> listOf(0)
        CardType.REVERSED -> listOf(0, 1)
        CardType.CLOZE -> Cloze.ordinals(card.front.html).ifEmpty { listOf(1) }
    }
}
