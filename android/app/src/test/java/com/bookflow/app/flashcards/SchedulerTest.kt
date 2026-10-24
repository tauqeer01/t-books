package com.bookflow.app.flashcards

import com.bookflow.app.flashcards.domain.CardState
import com.bookflow.app.flashcards.domain.Cloze
import com.bookflow.app.flashcards.domain.Rating
import com.bookflow.app.flashcards.domain.Schedule
import com.bookflow.app.flashcards.domain.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val scheduler = Scheduler(utc)
    /** 2026-10-15 10:00 UTC */
    private val now = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 15, 10, 0) }.timeInMillis
    private val minute = 60_000L
    private val new = Schedule("card", 0)

    private fun dayStart(daysFromToday: Int) = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 15 + daysFromToday, 4, 0) }.timeInMillis

    @Test fun newCardWalksLearningStepsThenGraduatesAtOneDay() {
        val again = scheduler.answer(new, Rating.AGAIN, now)
        assertEquals(CardState.LEARNING, again.state); assertEquals(now + minute, again.dueAt)
        val hard = scheduler.answer(new, Rating.HARD, now)
        assertEquals(now + (5.5 * minute).toLong(), hard.dueAt)
        val good = scheduler.answer(new, Rating.GOOD, now)
        assertEquals(1, good.step); assertEquals(now + 10 * minute, good.dueAt)
        val graduated = scheduler.answer(good, Rating.GOOD, now)
        assertEquals(CardState.REVIEW, graduated.state); assertEquals(1f, graduated.intervalDays); assertEquals(dayStart(1), graduated.dueAt)
    }

    @Test fun easyOnNewCardGraduatesAtFourDays() {
        val easy = scheduler.answer(new, Rating.EASY, now)
        assertEquals(CardState.REVIEW, easy.state); assertEquals(4f, easy.intervalDays); assertEquals(dayStart(4), easy.dueAt)
    }

    @Test fun reviewIntervalsGrowAndEachButtonBeatsTheLastOne() {
        val review = Schedule("card", 0, CardState.REVIEW, ease = 2.5f, intervalDays = 10f, reps = 5)
        val hard = scheduler.answer(review, Rating.HARD, now)
        val good = scheduler.answer(review, Rating.GOOD, now)
        val easy = scheduler.answer(review, Rating.EASY, now)
        assertTrue(hard.intervalDays in 11.4f..12.6f)      // 12 ± 5%
        assertTrue(good.intervalDays in 23.75f..26.25f)    // 25 ± 5%
        assertTrue(easy.intervalDays in 30.8f..34.2f)      // 32.5 ± 5%
        assertTrue(hard.intervalDays < good.intervalDays && good.intervalDays < easy.intervalDays)
        assertEquals(2.35f, hard.ease, 1e-4f); assertEquals(2.5f, good.ease, 1e-4f); assertEquals(2.65f, easy.ease, 1e-4f)
        assertEquals(6, good.reps)
    }

    @Test fun lapseHalvesIntervalAndRelearnsInTenMinutes() {
        val review = Schedule("card", 0, CardState.REVIEW, ease = 2.5f, intervalDays = 20f)
        val lapsed = scheduler.answer(review, Rating.AGAIN, now)
        assertEquals(CardState.RELEARNING, lapsed.state)
        assertEquals(10f, lapsed.intervalDays); assertEquals(2.3f, lapsed.ease, 1e-4f); assertEquals(1, lapsed.lapses)
        assertEquals(now + 10 * minute, lapsed.dueAt)
        val back = scheduler.answer(lapsed, Rating.GOOD, now)
        assertEquals(CardState.REVIEW, back.state); assertEquals(10f, back.intervalDays); assertEquals(dayStart(10), back.dueAt)
    }

    @Test fun easeNeverDropsBelowFloor() {
        var s = Schedule("card", 0, CardState.REVIEW, ease = 1.35f, intervalDays = 2f)
        repeat(5) { s = scheduler.answer(s.copy(state = CardState.REVIEW), Rating.AGAIN, now) }
        assertEquals(Scheduler.MIN_EASE, s.ease, 1e-6f)
    }

    @Test fun shortIntervalsAreNotFuzzedAndFuzzIsDeterministic() {
        val review = Schedule("card", 0, CardState.REVIEW, ease = 1.3f, intervalDays = 1f)
        assertEquals(2f, scheduler.answer(review, Rating.HARD, now).intervalDays)
        val long = Schedule("card", 0, CardState.REVIEW, intervalDays = 40f, reps = 3)
        assertEquals(scheduler.answer(long, Rating.GOOD, now).intervalDays, scheduler.answer(long, Rating.GOOD, now).intervalDays)
    }

    @Test fun reviewDayRollsOverAtFourInTheMorning() {
        val threeAm = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.OCTOBER, 16, 3, 0) }.timeInMillis
        assertEquals(dayStart(0), scheduler.reviewDayStart(threeAm))
        assertEquals(dayStart(1), scheduler.reviewDayEnd(now))
    }

    @Test fun previewLabelsMatchAnswers() {
        val labels = scheduler.preview(new, now)
        assertEquals("1m", labels[Rating.AGAIN]); assertEquals("10m", labels[Rating.GOOD]); assertEquals("4d", labels[Rating.EASY])
        assertEquals("<1m", Scheduler.label(10_000)); assertEquals("2.0mo", Scheduler.label(60L * 86_400_000))
        // Late in the day the Easy interval still reads as four days
        val evening = now + 8 * 60 * minute
        assertEquals("4d", scheduler.preview(new, evening)[Rating.EASY])
    }

    @Test fun clozeOrdinalsRenderAndWrap() {
        val html = "<p>{{c1::Lift}} acts {{c2::perpendicular::direction}} to the {{c1::relative wind}}.</p>"
        assertEquals(listOf(1, 2), Cloze.ordinals(html))
        val q1 = Cloze.render(html, 1, revealed = false)
        assertTrue(q1.contains("[…]") && q1.contains("perpendicular") && !q1.contains("Lift") && !q1.contains("{{"))
        val q2 = Cloze.render(html, 2, revealed = false)
        assertTrue(q2.contains("[direction]") && q2.contains("Lift"))
        assertTrue(Cloze.render(html, 2, revealed = true).contains(">perpendicular<"))
        assertEquals("Lift is {{c1::up}}", Cloze.wrap("Lift is up", 8, 10))
        assertEquals("{{c2::a}} {{c1::b}}", Cloze.wrap("a {{c1::b}}", 0, 1))
        assertEquals("unchanged", Cloze.wrap("unchanged", 3, 3))
    }
}
