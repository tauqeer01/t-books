package com.bookflow.app.flashcards.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun decks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks")
    suspend fun decksOnce(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deck(id: String): DeckEntity?

    // @Upsert, never REPLACE: REPLACE deletes the old row first, which cascades to cards and schedules
    @Upsert
    suspend fun upsertDeck(deck: DeckEntity)

    @Query("UPDATE decks SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentDecks(oldParent: String, newParent: String?)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: String)

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun cards(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId IN (:deckIds)")
    suspend fun cardsIn(deckIds: List<String>): List<CardEntity>

    /** Plain substring match over both faces; cards are short, so this stays fast. */
    @Query("SELECT * FROM cards WHERE frontJson LIKE :pattern OR backJson LIKE :pattern ORDER BY updatedAt DESC LIMIT 50")
    fun search(pattern: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: String): CardEntity?

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("SELECT * FROM card_schedule WHERE cardId = :cardId")
    suspend fun schedules(cardId: String): List<ScheduleEntity>

    @Query("SELECT s.* FROM card_schedule s JOIN cards c ON c.id = s.cardId WHERE c.deckId IN (:deckIds)")
    suspend fun schedulesIn(deckIds: List<String>): List<ScheduleEntity>

    /** Emits whenever any schedule changes; counts are computed from it. */
    @Query("SELECT s.cardId, s.ordinal, s.state, s.dueAt, c.deckId FROM card_schedule s JOIN cards c ON c.id = s.cardId")
    fun scheduleSummaries(): Flow<List<ScheduleSummary>>

    @Upsert
    suspend fun upsertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM card_schedule WHERE cardId = :cardId AND ordinal NOT IN (:keep)")
    suspend fun deleteSchedulesExcept(cardId: String, keep: List<Int>)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity): Long

    @Query("DELETE FROM review_log WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM review_log WHERE reviewedAt >= :since")
    fun logsSince(since: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_log WHERE reviewedAt >= :since AND deckId IN (:deckIds)")
    suspend fun logsSinceIn(since: Long, deckIds: List<String>): List<ReviewLogEntity>

    @Transaction
    suspend fun answer(schedule: ScheduleEntity, log: ReviewLogEntity): Long {
        upsertSchedules(listOf(schedule))
        return insertLog(log)
    }

    @Transaction
    suspend fun undoAnswer(previous: ScheduleEntity, logId: Long) {
        upsertSchedules(listOf(previous))
        deleteLog(logId)
    }
}

data class ScheduleSummary(val cardId: String, val ordinal: Int, val state: String, val dueAt: Long, val deckId: String)
