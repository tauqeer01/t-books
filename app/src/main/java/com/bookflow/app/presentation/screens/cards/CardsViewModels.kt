package com.bookflow.app.presentation.screens.cards

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.flashcards.data.AnswerRecord
import com.bookflow.app.flashcards.data.FlashcardRepository
import com.bookflow.app.flashcards.domain.Card
import com.bookflow.app.flashcards.domain.CardFace
import com.bookflow.app.flashcards.domain.CardType
import com.bookflow.app.flashcards.domain.Deck
import com.bookflow.app.flashcards.domain.DeckCounts
import com.bookflow.app.flashcards.domain.Rating
import com.bookflow.app.flashcards.domain.ReviewStats
import com.bookflow.app.flashcards.domain.StudyItem
import com.bookflow.app.notes.domain.HtmlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A card started from the reader: text or a clip plus where it came from. */
data class CardDraft(
    val type: CardType,
    val front: CardFace = CardFace(),
    val back: CardFace = CardFace(),
    val sourceBookId: String? = null,
    val sourcePage: Int? = null,
    val suggestedDeckName: String? = null
)

/** Hands a draft from the reader to the card editor without putting long text in a route. */
class CardDraftHolder { var draft: CardDraft? = null }

private val deckColors = listOf("#6D28D9", "#2563EB", "#059669", "#D97706", "#DC2626", "#DB2777")

data class DeckRow(val deck: Deck, val depth: Int, val counts: DeckCounts)

class CardsHomeViewModel(private val repository: FlashcardRepository) : ViewModel() {
    val rows: StateFlow<List<DeckRow>> = combine(repository.decks(), repository.deckCounts()) { decks, counts ->
        val byParent = decks.groupBy { it.parentId }
        buildList {
            fun add(parent: String?, depth: Int) {
                byParent[parent].orEmpty().forEach { deck ->
                    add(DeckRow(deck, depth, counts[deck.id] ?: DeckCounts(0, 0, 0, 0)))
                    add(deck.id, depth + 1)
                }
            }
            add(null, 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueToday: StateFlow<Int> = repository.dueToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun createDeck(name: String, parentId: String? = null) = viewModelScope.launch {
        repository.createDeck(name, deckColors[(rows.value.size) % deckColors.size], parentId)
    }
    fun updateDeck(deck: Deck) = viewModelScope.launch { repository.updateDeck(deck) }
    fun deleteDeck(id: String) = viewModelScope.launch { repository.deleteDeck(id) }

    class Factory(private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CardsHomeViewModel(repository) as T
    }
}

class DeckViewModel(private val deckId: String, private val repository: FlashcardRepository) : ViewModel() {
    val deck: StateFlow<Deck?> = repository.decks().map { decks -> decks.firstOrNull { it.id == deckId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val counts: StateFlow<DeckCounts?> = repository.deckCounts().map { it[deckId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val cards: StateFlow<List<Card>> = repository.cards(deckId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(card: Card) = viewModelScope.launch { repository.deleteCard(card.id) }

    class Factory(private val deckId: String, private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeckViewModel(deckId, repository) as T
    }
}

data class CardEditorState(
    val loading: Boolean = true,
    val cardId: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val deckId: String? = null,
    val decks: List<Deck> = emptyList(),
    val type: CardType = CardType.BASIC,
    val front: CardFace = CardFace(),
    val back: CardFace = CardFace(),
    val sourceBookId: String? = null,
    val sourcePage: Int? = null,
    /** Bumped when faces are replaced from outside the editors (load, image, "save and add another"). */
    val revision: Int = 0,
    val error: String? = null,
    val saved: Boolean = false,
    val savedCount: Int = 0
) {
    val canSave: Boolean get() = deckId != null && !front.isBlank && (type == CardType.CLOZE || !back.isBlank)
}

class CardEditorViewModel(
    private val cardId: String?,
    private val initialDeckId: String?,
    private val repository: FlashcardRepository,
    drafts: CardDraftHolder
) : ViewModel() {
    private val _state = MutableStateFlow(CardEditorState())
    val state: StateFlow<CardEditorState> = _state.asStateFlow()

    init {
        val draft = drafts.draft.also { drafts.draft = null }
        viewModelScope.launch {
            repository.decks().collect { decks -> _state.update { it.copy(decks = decks, deckId = it.deckId ?: initialDeckId ?: decks.firstOrNull()?.id) } }
        }
        viewModelScope.launch {
            // Wait for the deck list so the deck choice below never races it
            val decks = repository.decks().first()
            val existing = cardId?.let { repository.card(it) }
            _state.update { s ->
                when {
                    existing != null -> s.copy(loading = false, cardId = existing.id, isNew = false, deckId = existing.deckId, type = existing.type,
                        front = existing.front, back = existing.back, sourceBookId = existing.sourceBookId, sourcePage = existing.sourcePage, revision = s.revision + 1)
                    draft != null -> s.copy(loading = false, type = draft.type, front = draft.front, back = draft.back,
                        sourceBookId = draft.sourceBookId, sourcePage = draft.sourcePage, revision = s.revision + 1)
                    else -> s.copy(loading = false)
                }
            }
            // Cards from a book go to that book's deck, created the first time
            val bookDeck = draft?.suggestedDeckName
            if (bookDeck != null && initialDeckId == null) {
                val id = decks.firstOrNull { it.name.equals(bookDeck, ignoreCase = true) }?.id
                    ?: repository.createDeck(bookDeck, deckColors[decks.size % deckColors.size])
                _state.update { it.copy(deckId = id) }
            }
        }
    }

    fun setDeck(id: String) = _state.update { it.copy(deckId = id) }
    fun setType(type: CardType) = _state.update { it.copy(type = type) }
    // Normalized so cloze markers and punctuation are plain characters (the editor writes &colon;, &lcub;…)
    fun setFrontHtml(html: String) = _state.update { it.copy(front = it.front.copy(html = HtmlText.normalize(html))) }
    fun setBackHtml(html: String) = _state.update { it.copy(back = it.back.copy(html = HtmlText.normalize(html))) }
    fun clearImage(front: Boolean) = _state.update { if (front) it.copy(front = it.front.copy(imagePath = null)) else it.copy(back = it.back.copy(imagePath = null)) }

    fun addImage(front: Boolean, uri: Uri) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { repository.importImage(_state.value.cardId, uri) } }
            .onSuccess { path -> setImage(front, path) }
            .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't add the image") } }
    }

    fun addSketch(front: Boolean, bitmap: Bitmap) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { repository.saveImage(_state.value.cardId, bitmap) }
        setImage(front, path)
    }

    private fun setImage(front: Boolean, path: String) = _state.update {
        if (front) it.copy(front = it.front.copy(imagePath = path)) else it.copy(back = it.back.copy(imagePath = path))
    }

    fun createDeck(name: String) = viewModelScope.launch { val id = repository.createDeck(name, deckColors[_state.value.decks.size % deckColors.size]); setDeck(id) }

    /** Saves; with [addAnother] the editor clears for the next card in the same deck. */
    fun save(addAnother: Boolean) = viewModelScope.launch {
        val s = _state.value
        val deckId = s.deckId ?: return@launch
        if (s.type == CardType.CLOZE && com.bookflow.app.flashcards.domain.Cloze.ordinals(s.front.html).isEmpty()) {
            _state.update { it.copy(error = "Select a word in the text and tap […] to make a blank") }
            return@launch
        }
        repository.saveCard(Card(s.cardId, deckId, s.type, s.front, s.back, s.sourceBookId, s.sourcePage))
        _state.update {
            if (addAnother) CardEditorState(loading = false, deckId = deckId, decks = it.decks, type = it.type, revision = it.revision + 1, savedCount = it.savedCount + 1)
            else it.copy(saved = true)
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    class Factory(private val cardId: String?, private val deckId: String?, private val repository: FlashcardRepository, private val drafts: CardDraftHolder) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CardEditorViewModel(cardId, deckId, repository, drafts) as T
    }
}

data class ReviewState(
    val loading: Boolean = true,
    val current: StudyItem? = null,
    val revealed: Boolean = false,
    val intervals: Map<Rating, String> = emptyMap(),
    val remaining: Int = 0,
    val reviewedThisSession: Int = 0,
    val canUndo: Boolean = false
)

class ReviewViewModel(private val deckId: String, private val repository: FlashcardRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()
    private val history = ArrayDeque<AnswerRecord>()
    private var shownAt = System.currentTimeMillis()

    init { viewModelScope.launch { next() } }

    private suspend fun next() {
        val queue = repository.studyQueue(deckId)
        val item = queue.firstOrNull()
        shownAt = System.currentTimeMillis()
        _state.update { it.copy(loading = false, current = item, revealed = false, remaining = queue.size,
            intervals = item?.let { i -> repository.preview(i.schedule) }.orEmpty(), canUndo = history.isNotEmpty()) }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    fun answer(rating: Rating) {
        val item = _state.value.current ?: return
        viewModelScope.launch {
            history.addLast(repository.answer(item, rating, System.currentTimeMillis() - shownAt))
            if (history.size > 20) history.removeFirst()
            _state.update { it.copy(reviewedThisSession = it.reviewedThisSession + 1) }
            next()
        }
    }

    fun undo() {
        val record = history.removeLastOrNull() ?: return
        viewModelScope.launch {
            repository.undo(record)
            _state.update { it.copy(reviewedThisSession = (it.reviewedThisSession - 1).coerceAtLeast(0)) }
            next()
        }
    }

    class Factory(private val deckId: String, private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewViewModel(deckId, repository) as T
    }
}

class StatsViewModel(private val deckId: String?, private val repository: FlashcardRepository) : ViewModel() {
    private val _stats = MutableStateFlow<ReviewStats?>(null)
    val stats: StateFlow<ReviewStats?> = _stats.asStateFlow()
    init { viewModelScope.launch { _stats.value = repository.stats(deckId) } }

    class Factory(private val deckId: String?, private val repository: FlashcardRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(deckId, repository) as T
    }
}

/** Question/answer HTML for one study side, handling reversed and cloze cards. */
fun StudyItem.questionAnswer(revealed: Boolean): Pair<CardFace, CardFace?> = when (card.type) {
    CardType.CLOZE -> {
        val shown = card.front.copy(html = com.bookflow.app.flashcards.domain.Cloze.render(card.front.html, schedule.ordinal, revealed))
        shown to (if (revealed && !card.back.isBlank) card.back else null)
    }
    else -> if (schedule.ordinal == 1) card.back to (if (revealed) card.front else null) else card.front to (if (revealed) card.back else null)
}
