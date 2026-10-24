package com.bookflow.app.flashcards.domain

enum class CardType(val title: String) {
    BASIC("Basic"),
    /** Also asks back → front. */
    REVERSED("Basic + reversed"),
    /** Fill in the blank: each {{cN::…}} becomes its own card. */
    CLOZE("Cloze")
}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

enum class Rating(val title: String) { AGAIN("Again"), HARD("Hard"), GOOD("Good"), EASY("Easy") }

/** One side of a card: rich text plus an optional picture (photo, PDF clip, or handwriting). */
data class CardFace(val html: String = "", val imagePath: String? = null) {
    val isBlank: Boolean get() = imagePath == null && com.bookflow.app.notes.domain.HtmlText.toPlain(html).isBlank()
}

data class Deck(
    val id: String,
    val name: String,
    val colorHex: String,
    val parentId: String? = null,
    val bookId: String? = null,
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200
)

data class Card(
    val id: String,
    val deckId: String,
    val type: CardType,
    val front: CardFace,
    val back: CardFace,
    val sourceBookId: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/** Scheduling state of one reviewable side of a card (forward, reverse, or one cloze number). */
data class Schedule(
    val cardId: String,
    val ordinal: Int,
    val state: CardState = CardState.NEW,
    val ease: Float = Scheduler.START_EASE,
    val intervalDays: Float = 0f,
    val dueAt: Long = 0L,
    /** Index into the learning/relearning steps. */
    val step: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)

data class DeckCounts(val new: Int, val learning: Int, val due: Int, val total: Int) {
    val studyable: Int get() = new + learning + due
}

/** A side ready to study: the card, which side, and its schedule. */
data class StudyItem(val card: Card, val schedule: Schedule)

data class ReviewStats(
    /** Reviews per day for the last 30 days, oldest first. */
    val reviewsPerDay: List<Int>,
    /** Share of review-state answers that were not Again, 0..1; null with no reviews. */
    val retention: Float?,
    /** Cards due on each of the next 7 days, today first. */
    val forecast: List<Int>
)
