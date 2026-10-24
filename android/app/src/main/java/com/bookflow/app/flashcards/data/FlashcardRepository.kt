package com.bookflow.app.flashcards.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bookflow.app.flashcards.domain.Card
import com.bookflow.app.flashcards.domain.CardState
import com.bookflow.app.flashcards.domain.Deck
import com.bookflow.app.flashcards.domain.DeckCounts
import com.bookflow.app.flashcards.domain.Rating
import com.bookflow.app.flashcards.domain.ReviewStats
import com.bookflow.app.flashcards.domain.Schedule
import com.bookflow.app.flashcards.domain.Scheduler
import com.bookflow.app.flashcards.domain.StudyItem
import com.bookflow.app.flashcards.domain.StudyQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Result of answering, kept by the review screen so the last answer can be undone. */
data class AnswerRecord(val previous: Schedule, val logId: Long)

class FlashcardRepository(
    private val context: Context,
    private val dao: FlashcardDao,
    private val scheduler: Scheduler = Scheduler(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val images = com.bookflow.app.notes.data.NoteFileStore(context)

    fun decks(): Flow<List<Deck>> = dao.decks().map { list -> list.map { it.toDomain() } }
    fun cards(deckId: String): Flow<List<Card>> = dao.cards(deckId).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Card>> {
        val q = query.trim()
        if (q.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val pattern = "%" + q.replace("%", "").replace("_", "") + "%"
        // Matched on stored JSON, then confirmed on the visible text so markup and keys never match
        return dao.search(pattern).map { list -> list.map { it.toDomain() }.filter { c ->
            com.bookflow.app.notes.domain.HtmlText.toPlain(c.front.html + " " + c.back.html).contains(q, ignoreCase = true)
        } }
    }

    /** Counts per deck, including its sub-decks, refreshed whenever cards are studied or edited. */
    fun deckCounts(): Flow<Map<String, DeckCounts>> = combine(dao.decks(), dao.scheduleSummaries(), dao.logsSince(scheduler.reviewDayStart(clock()))) { decks, summaries, logs ->
        val now = clock()
        val dayEnd = scheduler.reviewDayEnd(now)
        decks.associate { deck ->
            val ids = subtree(deck.id, decks)
            val schedules = summaries.filter { it.deckId in ids }.map { Schedule(it.cardId, it.ordinal, CardState.valueOf(it.state), dueAt = it.dueAt) }
            deck.id to StudyQueue.counts(schedules, limits(deck, logs.filter { it.deckId in ids }), dayEnd)
        }
    }

    /** Reviews due today across all decks, for the Home screen. */
    fun dueToday(): Flow<Int> = deckCounts().combine(dao.decks()) { counts, decks ->
        decks.filter { it.parentId == null }.sumOf { counts[it.id]?.studyable ?: 0 }
    }

    suspend fun createDeck(name: String, colorHex: String, parentId: String? = null, bookId: String? = null): String = withContext(Dispatchers.IO) {
        UUID.randomUUID().toString().also { dao.upsertDeck(DeckEntity(it, name.trim(), colorHex, parentId, bookId, 20, 200, clock())) }
    }

    suspend fun updateDeck(deck: Deck) = withContext(Dispatchers.IO) {
        val existing = dao.deck(deck.id) ?: return@withContext
        dao.upsertDeck(existing.copy(name = deck.name.trim(), colorHex = deck.colorHex, newPerDay = deck.newPerDay.coerceIn(0, 9999),
            reviewsPerDay = deck.reviewsPerDay.coerceIn(0, 9999)))
    }

    /** Sub-decks move up a level; this deck's own cards are deleted with it. */
    suspend fun deleteDeck(id: String) = withContext(Dispatchers.IO) {
        val parent = dao.deck(id)?.parentId
        val cards = dao.cardsIn(listOf(id))
        dao.reparentDecks(id, parent)
        dao.deleteDeck(id)
        cards.forEach { cardDir(it.id).deleteRecursively() }
    }

    suspend fun card(id: String): Card? = withContext(Dispatchers.IO) { dao.card(id)?.toDomain() }

    /** Saves a card and keeps one schedule per reviewable side; edits never reset existing progress. */
    suspend fun saveCard(card: Card): Unit = withContext(Dispatchers.IO) {
        val now = clock()
        val existing = dao.card(card.id)
        val saved = card.copy(createdAt = existing?.createdAt ?: now, updatedAt = now)
        dao.upsertCard(CardEntity.from(saved))
        val ordinals = StudyQueue.ordinals(saved)
        val current = dao.schedules(saved.id).map { it.ordinal }.toSet()
        dao.upsertSchedules(ordinals.filterNot { it in current }.map { ScheduleEntity.from(Schedule(saved.id, it, dueAt = saved.createdAt)) })
        dao.deleteSchedulesExcept(saved.id, ordinals)
        // Remove pictures no longer referenced by either face
        val keep = setOfNotNull(saved.front.imagePath, saved.back.imagePath)
        cardDir(saved.id).listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
    }

    suspend fun deleteCard(id: String) = withContext(Dispatchers.IO) { dao.deleteCard(id); cardDir(id).deleteRecursively() }

    /** Sides to study now in [deckId] and its sub-decks. */
    suspend fun studyQueue(deckId: String): List<StudyItem> = withContext(Dispatchers.IO) {
        val decks = dao.decksOnce()
        val ids = subtree(deckId, decks)
        val deck = decks.first { it.id == deckId }
        val now = clock()
        val logs = dao.logsSinceIn(scheduler.reviewDayStart(now), ids.toList())
        val schedules = dao.schedulesIn(ids.toList()).map { it.toDomain() }
        val cards = dao.cardsIn(ids.toList()).associate { it.id to it.toDomain() }
        StudyQueue.build(schedules, limits(deck, logs), now, scheduler.reviewDayEnd(now)).mapNotNull { s -> cards[s.cardId]?.let { StudyItem(it, s) } }
    }

    fun preview(schedule: Schedule): Map<Rating, String> = scheduler.preview(schedule, clock())

    suspend fun answer(item: StudyItem, rating: Rating, elapsedMs: Long): AnswerRecord = withContext(Dispatchers.IO) {
        val now = clock()
        val next = scheduler.answer(item.schedule, rating, now)
        val logId = dao.answer(ScheduleEntity.from(next), ReviewLogEntity(cardId = item.card.id, deckId = item.card.deckId, ordinal = item.schedule.ordinal,
            rating = rating.name, previousState = item.schedule.state.name, elapsedMs = elapsedMs.coerceIn(0, 10 * 60_000),
            previousIntervalDays = item.schedule.intervalDays, newIntervalDays = next.intervalDays, reviewedAt = now))
        AnswerRecord(item.schedule, logId)
    }

    suspend fun undo(record: AnswerRecord) = withContext(Dispatchers.IO) { dao.undoAnswer(ScheduleEntity.from(record.previous), record.logId) }

    suspend fun stats(deckId: String?): ReviewStats = withContext(Dispatchers.IO) {
        val decks = dao.decksOnce()
        val ids = (if (deckId == null) decks.map { it.id }.toSet() else subtree(deckId, decks)).toList()
        val now = clock()
        val start = scheduler.dayStartAfter(now, -29)
        val logs = dao.logsSinceIn(start, ids)
        val perDay = List(30) { i ->
            val from = scheduler.dayStartAfter(now, (i - 29).toLong())
            val to = scheduler.dayStartAfter(now, (i - 28).toLong())
            logs.count { it.reviewedAt in from until to }
        }
        val reviews = logs.filter { it.previousState == CardState.REVIEW.name }
        val retention = if (reviews.isEmpty()) null else reviews.count { it.rating != Rating.AGAIN.name }.toFloat() / reviews.size
        val schedules = dao.schedulesIn(ids).map { it.toDomain() }.filter { it.state != CardState.NEW }
        val forecast = List(7) { i ->
            val to = scheduler.dayStartAfter(now, (i + 1).toLong())
            val from = if (i == 0) Long.MIN_VALUE else scheduler.dayStartAfter(now, i.toLong())
            schedules.count { it.dueAt in from until to }
        }
        ReviewStats(perDay, retention, forecast)
    }

    fun cardDir(cardId: String) = File(context.filesDir, "cards/$cardId").apply { mkdirs() }

    /** Stores a picture (photo, PDF clip, or handwriting) for a card face as WebP. */
    fun saveImage(cardId: String, bitmap: Bitmap): String = images.saveBitmapTo(cardDir(cardId), bitmap).path

    fun importImage(cardId: String, uri: Uri): String = images.importImageTo(cardDir(cardId), uri).path

    private fun limits(deck: DeckEntity, todaysLogs: List<ReviewLogEntity>) = StudyQueue.Limits(
        newPerDay = deck.newPerDay, reviewsPerDay = deck.reviewsPerDay,
        newStudiedToday = todaysLogs.filter { it.previousState == CardState.NEW.name }.map { it.cardId to it.ordinal }.distinct().size,
        reviewsDoneToday = todaysLogs.count { it.previousState == CardState.REVIEW.name }
    )

    private fun subtree(id: String, decks: List<DeckEntity>): Set<String> {
        val result = mutableSetOf(id)
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = decks.filter { it.parentId in frontier && it.id !in result }.map { it.id }.toSet()
            result += frontier
        }
        return result
    }
}
