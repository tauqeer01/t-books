package com.bookflow.app.presentation.screens.esign

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bookflow.app.domain.model.Book
import com.bookflow.app.domain.repository.BookRepository
import com.bookflow.app.esign.data.PdfSigner
import com.bookflow.app.esign.data.SignatureStore
import com.bookflow.app.esign.domain.FieldKind
import com.bookflow.app.esign.domain.SavedSignature
import com.bookflow.app.esign.domain.SignField
import com.bookflow.app.esign.domain.SignatureKind
import com.bookflow.app.pdf.engine.PdfDocumentResult
import com.bookflow.app.pdf.engine.PdfEngine
import com.bookflow.app.pdf.engine.PdfEngineFactory
import com.bookflow.app.pdf.engine.PdfSource
import com.bookflow.app.pdf.engine.RenderQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Signed copies live under `filesDir/books/signed/` (inside the FileProvider share root). */
fun signedDir(context: Context) = File(context.filesDir, "books/signed").apply { mkdirs() }

class ESignHomeViewModel(
    private val context: Context,
    private val store: SignatureStore,
    books: BookRepository
) : ViewModel() {
    private val _signatures = MutableStateFlow(store.list())
    val signatures: StateFlow<List<SavedSignature>> = _signatures.asStateFlow()

    val library: StateFlow<List<Book>> = books.getAllBooks().map { list -> list.sortedByDescending { it.lastReadTimestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Books created by signing, newest first. */
    val signed: StateFlow<List<Book>> = books.getAllBooks().map { list ->
        val dir = signedDir(context).absolutePath
        list.filter { (it.uriString ?: it.filePath).contains(dir) }.sortedByDescending { it.addedTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() { _signatures.value = store.list() }

    fun delete(signature: SavedSignature) { store.delete(signature); refresh() }

    class Factory(private val context: Context, private val store: SignatureStore, private val books: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ESignHomeViewModel(context, store, books) as T
    }
}

/** What the signature creator sheet produces and saves. */
class SignatureCreatorViewModel(private val store: SignatureStore) : ViewModel() {
    fun typed(text: String, style: SignatureStore.TypedStyle, color: Int): Bitmap = store.renderTyped(text, style, color)
    suspend fun fromPhoto(uri: Uri, color: Int): Bitmap = withContext(Dispatchers.IO) { store.fromPhoto(uri, color) }
    suspend fun save(kind: SignatureKind, bitmap: Bitmap): SavedSignature = withContext(Dispatchers.IO) { store.save(kind, bitmap) }

    class Factory(private val store: SignatureStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SignatureCreatorViewModel(store) as T
    }
}

data class SignPage(val index: Int, val aspect: Float)

data class SignEditorState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "Document",
    val pages: List<SignPage> = emptyList(),
    val fields: List<SignField> = emptyList(),
    val selectedId: String? = null,
    /** Tool waiting for a tap on the page. */
    val placing: FieldKind? = null,
    val signatures: List<SavedSignature> = emptyList(),
    val saving: Boolean = false,
    /** Library id of the signed copy once saved. */
    val savedBookId: String? = null,
    val savedFile: File? = null,
    val message: String? = null
) {
    val selected: SignField? get() = fields.firstOrNull { it.id == selectedId }
    fun signature(kind: SignatureKind) = signatures.firstOrNull { it.kind == kind }
}

class SignEditorViewModel(
    private val context: Context,
    private val bookId: String?,
    private val fileUri: Uri?,
    private val books: BookRepository,
    private val engineFactory: PdfEngineFactory,
    private val store: SignatureStore,
    private val signer: PdfSigner
) : ViewModel() {
    private val _state = MutableStateFlow(SignEditorState(signatures = store.list()))
    val state: StateFlow<SignEditorState> = _state.asStateFlow()
    private var engine: PdfEngine? = null
    private var source: PdfSource? = null
    private val undo = ArrayDeque<List<SignField>>()

    init { viewModelScope.launch { open() } }

    private suspend fun open() {
        val (src, title) = when {
            bookId != null -> {
                val book = books.getBookById(bookId).first()
                    ?: run { _state.update { it.copy(loading = false, error = "This book is no longer in your library") }; return }
                val s = book.uriString?.let { PdfSource.UriSource(Uri.parse(it), context) } ?: PdfSource.FileSource(File(book.filePath))
                s to book.title
            }
            fileUri != null -> PdfSource.UriSource(fileUri, context) to (com.bookflow.app.core.util.FileUtils.extractDocumentMetadata(context, fileUri).fileName.removeSuffix(".pdf"))
            else -> { _state.update { it.copy(loading = false, error = "No document") }; return }
        }
        val e = engineFactory.createEngine()
        when (val result = e.openDocument(src)) {
            is PdfDocumentResult.Error -> { e.close(); _state.update { it.copy(loading = false, error = result.message) }; return }
            is PdfDocumentResult.Success -> Unit
        }
        engine = e
        source = src
        val pages = (0 until e.getPageCount()).map { i -> SignPage(i, runCatching { e.loadPageDimensions(i).aspectRatio }.getOrDefault(0.707f)) }
        _state.update { it.copy(loading = false, title = title, pages = pages) }
    }

    suspend fun renderPage(index: Int): Bitmap? = engine?.renderPage(index, 1f, RenderQuality.HIGH_DPI)

    fun refreshSignatures() = _state.update { it.copy(signatures = store.list()) }

    fun startPlacing(kind: FieldKind) = _state.update { it.copy(placing = kind, selectedId = null) }
    fun cancelPlacing() = _state.update { it.copy(placing = null) }

    /** Places the pending tool centred on the tap ([fx], [fy] are page fractions). */
    fun place(page: Int, fx: Float, fy: Float) {
        val s = _state.value
        val kind = s.placing ?: return
        val aspect = s.pages.getOrNull(page)?.aspect ?: 0.707f
        val (w, h, image, text) = when (kind) {
            FieldKind.SIGNATURE, FieldKind.INITIALS -> {
                val sig = s.signature(if (kind == FieldKind.SIGNATURE) SignatureKind.SIGNATURE else SignatureKind.INITIALS) ?: return
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(sig.path, bounds)
                val w = if (kind == FieldKind.SIGNATURE) 0.32f else 0.14f
                // Keep the picture's proportions on this page
                val h = w * aspect * bounds.outHeight / bounds.outWidth.coerceAtLeast(1)
                Quad(w, h, sig.path, "")
            }
            FieldKind.TEXT -> Quad(0.4f, 0.03f, null, "Text")
            FieldKind.DATE -> Quad(0.22f, 0.03f, null, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()))
            FieldKind.CHECK, FieldKind.CROSS -> Quad(0.05f, 0.05f * aspect, null, "")
        }
        val field = SignField(UUID.randomUUID().toString(), page, kind,
            (fx - w / 2).coerceIn(0f, 1f - w), (fy - h / 2).coerceIn(0f, (1f - h).coerceAtLeast(0f)), w, h, text = text, imagePath = image)
        record()
        _state.update { it.copy(fields = it.fields + field, placing = null, selectedId = field.id) }
    }

    private data class Quad(val w: Float, val h: Float, val image: String?, val text: String)

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    private var moveStart: List<SignField>? = null

    /** Live drag; [commitMove] records one undo step. [scale] resizes from the top-left corner. */
    fun moveSelected(dx: Float, dy: Float, scale: Float = 1f) {
        val field = _state.value.selected ?: return
        if (moveStart == null) { moveStart = _state.value.fields; markEdited() }
        val w = (field.width * scale).coerceIn(0.02f, 1f)
        val h = (field.height * scale).coerceIn(0.01f, 1f)
        val moved = field.copy(x = (field.x + dx).coerceIn(0f, 1f - w), y = (field.y + dy).coerceIn(0f, 1f - h), width = w, height = h)
        _state.update { s -> s.copy(fields = s.fields.map { if (it.id == field.id) moved else it }) }
    }

    fun commitMove() { moveStart?.let { undo.addLast(it) }; moveStart = null }

    fun setText(text: String) {
        val field = _state.value.selected ?: return
        record()
        _state.update { s -> s.copy(fields = s.fields.map { if (it.id == field.id) it.copy(text = text) else it }) }
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        record()
        _state.update { s -> s.copy(fields = s.fields.filterNot { it.id == id }, selectedId = null) }
    }

    fun undo() { undo.removeLastOrNull()?.let { previous -> _state.update { it.copy(fields = previous, selectedId = null) } } }
    val canUndo get() = undo.isNotEmpty()

    private fun record() {
        undo.addLast(_state.value.fields); if (undo.size > 50) undo.removeFirst()
        markEdited()
    }

    /** Any change after saving needs a new save; the earlier signed copy stays as it was. */
    private fun markEdited() { if (_state.value.savedFile != null) _state.update { it.copy(savedFile = null, savedBookId = null) } }

    /** Writes the signed copy, adds it to the library, and leaves the original untouched. */
    fun save() {
        val s = _state.value
        val src = source ?: return
        if (s.fields.isEmpty()) { _state.update { it.copy(message = "Add a signature or text first") }; return }
        _state.update { it.copy(saving = true, selectedId = null) }
        viewModelScope.launch {
            try {
                val name = "${s.title} (signed)"
                val file = File(signedDir(context), "${UUID.randomUUID()}/${name.replace(Regex("[/\\\\:*?\"<>|]"), "_")}.pdf")
                signer.sign({ open(src) }, s.fields, file, title = name)
                val result = books.importPdf(Uri.fromFile(file))
                val id = result.getOrNull()?.id
                    ?: (result.exceptionOrNull() as? com.bookflow.app.domain.repository.DuplicateBookException)?.existingBookId
                _state.update { it.copy(saving = false, savedBookId = id, savedFile = file, message = "Saved “$name” to your library") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = "Couldn't save: ${e.message.orEmpty()}") }
            }
        }
    }

    private fun open(src: PdfSource) = when (src) {
        is PdfSource.FileSource -> src.file.inputStream()
        is PdfSource.UriSource -> requireNotNull(context.contentResolver.openInputStream(src.uri)) { "The document is no longer available" }
        is PdfSource.AssetSource -> context.assets.open(src.assetPath)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() { engine?.close(); super.onCleared() }

    class Factory(
        private val context: Context, private val bookId: String?, private val fileUri: Uri?, private val books: BookRepository,
        private val engines: PdfEngineFactory, private val store: SignatureStore, private val signer: PdfSigner
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SignEditorViewModel(context, bookId, fileUri, books, engines, store, signer) as T
    }
}
