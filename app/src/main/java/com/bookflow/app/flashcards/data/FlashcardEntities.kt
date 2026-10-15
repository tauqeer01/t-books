package com.bookflow.app.flashcards.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookflow.app.flashcards.domain.Card
import com.bookflow.app.flashcards.domain.CardFace
import com.bookflow.app.flashcards.domain.CardState
import com.bookflow.app.flashcards.domain.CardType
import com.bookflow.app.flashcards.domain.Deck
import com.bookflow.app.flashcards.domain.Schedule
import org.json.JSONObject

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val parentId: String?,
    val bookId: String?,
    val newPerDay: Int,
    val reviewsPerDay: Int,
    val createdAt: Long
) {
    fun toDomain() = Deck(id, name, colorHex, parentId, bookId, newPerDay, reviewsPerDay)
}

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(entity = DeckEntity::class, parentColumns = ["id"], childColumns = ["deckId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deckId")]
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val type: String,
    val frontJson: String,
    val backJson: String,
    val sourceBookId: String?,
    val sourcePage: Int?,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain() = Card(id, deckId, runCatching { CardType.valueOf(type) }.getOrDefault(CardType.BASIC), face(frontJson), face(backJson),
        sourceBookId, sourcePage, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, createdAt, updatedAt)

    companion object {
        fun from(card: Card) = CardEntity(card.id, card.deckId, card.type.name, json(card.front), json(card.back), card.sourceBookId, card.sourcePage,
            card.tags.joinToString(","), card.createdAt, card.updatedAt)

        // A face is a small JSON object so future blocks (audio, ink layers) can be added without a migration
        private fun json(face: CardFace) = JSONObject().put("html", face.html).apply { face.imagePath?.let { put("image", it) } }.toString()

        private fun face(json: String): CardFace = runCatching {
            val o = JSONObject(json)
            CardFace(o.optString("html"), o.optString("image").takeIf { it.isNotEmpty() })
        }.getOrDefault(CardFace())
    }
}

@Entity(
    tableName = "card_schedule",
    primaryKeys = ["cardId", "ordinal"],
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dueAt")]
)
data class ScheduleEntity(
    val cardId: String,
    val ordinal: Int,
    val state: String,
    val ease: Float,
    val intervalDays: Float,
    val dueAt: Long,
    val step: Int,
    val reps: Int,
    val lapses: Int
) {
    fun toDomain() = Schedule(cardId, ordinal, runCatching { CardState.valueOf(state) }.getOrDefault(CardState.NEW), ease, intervalDays, dueAt, step, reps, lapses)

    companion object {
        fun from(s: Schedule) = ScheduleEntity(s.cardId, s.ordinal, s.state.name, s.ease, s.intervalDays, s.dueAt, s.step, s.reps, s.lapses)
    }
}

@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId"), Index("reviewedAt")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val deckId: String,
    val ordinal: Int,
    val rating: String,
    /** State before this answer: NEW reviews count against the daily new-card limit. */
    val previousState: String,
    val elapsedMs: Long,
    val previousIntervalDays: Float,
    val newIntervalDays: Float,
    val reviewedAt: Long
)
