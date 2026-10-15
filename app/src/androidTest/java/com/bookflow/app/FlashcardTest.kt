package com.bookflow.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookflow.app.data.local.BookFlowDatabase
import com.bookflow.app.flashcards.data.FlashcardRepository
import com.bookflow.app.flashcards.domain.Card
import com.bookflow.app.flashcards.domain.CardFace
import com.bookflow.app.flashcards.domain.CardState
import com.bookflow.app.flashcards.domain.CardType
import com.bookflow.app.flashcards.domain.Cloze
import com.bookflow.app.flashcards.domain.Rating
import com.bookflow.app.notes.domain.HtmlText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlashcardTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BookFlowDatabase::class.java).build()
    private var now = 1_800_000_000_000L
    private val repository = FlashcardRepository(context, db.flashcardDao(), clock = { now })

    @After fun tearDown() = db.close()

    /** Android's ICU regex is stricter than the desktop JDK; this runs the cloze code on the device engine. */
    @Test fun clozeParsesEditorHtmlOnDevice() {
        val editor = "<p>gas &lcub;&lcub;c1&colon;&colon;turbine&rcub;&rcub; and {{c2::fan::part}}</p>"
        val html = HtmlText.normalize(editor)
        assertEquals(listOf(1, 2), Cloze.ordinals(html))
        assertTrue(Cloze.render(html, 2, revealed = false).contains("[part]"))
        assertEquals("gas [turbine] and [fan]", Cloze.preview(HtmlText.toPlain(html)))
    }

    @Test fun cardSidesScheduleStudyAndUndo(): Unit = runBlocking {
        val deck = repository.createDeck("Physics", "#000000")
        repository.saveCard(Card("basic", deck, CardType.BASIC, CardFace("<p>Q</p>"), CardFace("<p>A</p>")))
        repository.saveCard(Card("rev", deck, CardType.REVERSED, CardFace("<p>Front</p>"), CardFace("<p>Back</p>")))
        repository.saveCard(Card("cloze", deck, CardType.CLOZE, CardFace("<p>{{c1::a}} {{c2::b}} {{c3::c}}</p>"), CardFace()))
        val counts = repository.deckCounts().first().getValue(deck)
        assertEquals(6, counts.new)         // 1 + 2 + 3 sides
        assertEquals(3, counts.total)

        val queue = repository.studyQueue(deck)
        assertEquals(6, queue.size)
        val record = repository.answer(queue.first(), Rating.GOOD, 1_000)
        assertEquals(CardState.LEARNING, repository.studyQueue(deck).first { it.card.id == queue.first().card.id && it.schedule.ordinal == queue.first().schedule.ordinal }.schedule.state)
        repository.undo(record)
        assertEquals(6, repository.deckCounts().first().getValue(deck).new)

        // Editing a cloze card keeps progress on surviving blanks and drops removed ones
        val item = repository.studyQueue(deck).first { it.card.id == "cloze" && it.schedule.ordinal == 1 }
        repository.answer(item, Rating.EASY, 1_000)
        repository.saveCard(Card("cloze", deck, CardType.CLOZE, CardFace("<p>{{c1::a}} {{c2::b}}</p>"), CardFace()))
        val sides = db.flashcardDao().schedules("cloze").associateBy { it.ordinal }
        assertEquals(setOf(1, 2), sides.keys)
        assertEquals(CardState.REVIEW.name, sides.getValue(1).state)
    }

    @Test fun dailyNewLimitAndDeckDeletion(): Unit = runBlocking {
        val deck = repository.createDeck("Limits", "#000000")
        repository.updateDeck(repository.decks().first().first { it.id == deck }.copy(newPerDay = 2))
        repeat(5) { i -> repository.saveCard(Card("c$i", deck, CardType.BASIC, CardFace("<p>$i</p>"), CardFace("<p>a</p>"))) }
        assertEquals(2, repository.studyQueue(deck).size)
        repository.answer(repository.studyQueue(deck).first(), Rating.EASY, 500)
        // One new card studied today: one more new card allowed
        assertEquals(1, repository.studyQueue(deck).size)
        val stats = repository.stats(deck)
        assertEquals(1, stats.reviewsPerDay.last())
        repository.deleteDeck(deck)
        assertTrue(db.flashcardDao().cardsIn(listOf(deck)).isEmpty())
    }
}
