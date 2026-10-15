# BookFlow: Large PDFs, Notes, Reader Capture, and Flashcards — Design

Date: 2026-10-15
Status: Approved in conversation; awaiting written-spec review

## 1. Intent

Students use BookFlow to study from PDF textbooks. Today, PDFs over roughly 120 pages fail to load, and there is nowhere in the app to take structured notes or study with flashcards.

Success means:

- Any PDF, regardless of page count, opens in BookFlow, read in place from device storage.
- A Samsung Notes–class notebook: handwriting with stylus pressure, rich typed text, images, PDF clips, folders, search, export.
- While reading, a student selects text or a page region (finger or stylus) and sends it straight into a note or a flashcard.
- Flashcards with spaced repetition, image/ink faces, and cloze deletions.

### Decisions made

| Topic | Decision |
|---|---|
| Large PDFs | BookFlow keeps rendering every PDF itself, read in place. No hand-off to another viewer. |
| Notes v1 scope | Handwriting canvas, rich typed text, images & PDF clips, organize & export — all in v1. |
| Note page model | Fixed A4-ratio pages, free-positioned elements, ink layer on top. |
| Flashcards v1 scope | Spaced repetition, create from PDF, image & ink faces, cloze — all in v1. |
| Ink/text engine | `androidx.ink` 1.0.0 (stable) + `compose-rich-editor` 1.2.1 for Notes and flashcards. Reader annotations stay on the current engine; migration is a later, separate project. |
| Reader side panel | In v1: side-by-side panel on tablets (≥ 840 dp), bottom sheet on phones. |
| Navigation | Home · Library · Notes · Cards · Settings. Collections moves into Library; Search moves to the top bar. |

### Assumptions

- Offline only. No cloud sync, no accounts. Data lives in Room and app-private files.
- Notes and decks are standalone; a note/card element can optionally link back to a book page.
- OCR for scanned PDFs is out of scope; the Clip tool covers scanned content.

## 2. Phase 1 — Large-PDF fix

### Current state

There is no explicit page limit in the code. Suspected causes, to be confirmed by reproduction before fixing:

- `BookRepositoryImpl.importBook` calls `engine.documentMetadata()`, forcing a PDFBox parse during import.
- `ReaderViewModel.loadBookAndInitializeEngine` calls `getTableOfContents()`, which runs `doc.pages.indexOf(page)` per outline entry (`PdfTextIndex.outline`), giving O(pages × entries) page-tree walks.
- `catch (e: Exception)` blocks do not catch `OutOfMemoryError`, so memory failures crash rather than degrade.
- "Open with" imports with non-persistable grants copy the whole file synchronously during import.

### Method

1. Generate fixtures: 150-, 500-, and 1,500-page text PDFs and one 300-page image-heavy (scanned-style) PDF.
2. Import and open each on an emulator; capture logcat and `CrashLog` output to identify the real failure (OOM, ANR/hang, or error).
3. Apply only the fixes the reproduction justifies, each with a regression test.

### Planned fixes

1. **Import without PDFBox.** Import uses `PdfRenderer` only: page count and cover thumbnail. Title/author extraction runs afterwards in a background coroutine and updates the `BookEntity`. Import never blocks on PDFBox.
2. **Single-pass outline.** Iterate `doc.pages` once to build a `Map<COSDictionary, Int>`, then resolve each outline entry against the map.
3. **Graceful degradation.** All PDFBox entry points in `PdfTextIndex` catch both `Exception` and `OutOfMemoryError`. On failure the text layer is marked unavailable; rendering continues; the reader shows a one-time notice: "Text features unavailable for this file."
4. **In-place reading.** SAF picker imports remain in place via persisted URI permission. "Open with" imports first attempt `takePersistableUriPermission`; only when that fails is the file copied, and the copy runs with progress in the background before the book entry is finalized.
5. **Bounded reader memory.** Keep the bitmap `LruCache` hard-capped; ensure continuous-scroll mode only requests renders for visible ± 1 pages and cancels off-screen render jobs.

### Acceptance

- The 1,500-page fixture imports in under ~3 s and opens at the saved page.
- Scroll, page jump, search, and outline work on all fixtures.
- Java heap stays flat (no monotonic growth) over a 200-page continuous scroll.
- A PDF whose text layer fails to parse still opens and renders.

## 3. Phase 2 — Notes

### Package layout

- `notes/domain` — models and repository interfaces.
- `notes/data` — Room DAOs and file storage.
- `notes/ink` — shared wrapper over `androidx.ink` (authoring, brushes, stroke serialization, eraser geometry). Reused by flashcards.
- `presentation/screens/notes` — Notes home and editor.

### Data model (Room, migration to the next schema version)

| Table | Columns |
|---|---|
| `note_folders` | `id`, `name`, `colorHex`, `parentId` (nullable), `sortOrder` |
| `notes` | `id`, `folderId` (nullable), `title`, `pinned`, `tags` (comma-separated), `createdAt`, `updatedAt`, `thumbnailPath` |
| `note_pages` | `id`, `noteId`, `index`, `background` (`BLANK`, `LINED`, `GRID`, `DOTTED`), `inkPath` |
| `note_elements` | `id`, `pageId`, `kind` (`TEXT`, `IMAGE`, `PDF_CLIP`), `x`, `y`, `w`, `h`, `rotation`, `z`, `html` (TEXT), `imagePath` (IMAGE/PDF_CLIP), `quoteText` (PDF_CLIP text quote), `sourceBookId`, `sourcePage` |
| `notes_fts` | FTS4 over note title, element text (HTML stripped), tags, and clip quote text |

- Element geometry is stored as fractions of the page (0..1), so layout is identical on phone and tablet and in export.
- Cascade deletes: folder → (notes moved to root), note → pages → elements; file cleanup runs in the repository.
- Files: `filesDir/notes/<noteId>/page_<pageId>.ink` (Jetpack Ink serialized strokes), `img_<uuid>.webp`, `thumb.webp`.

### Editor

- Vertical lazy list of A4-ratio pages (only nearby pages composed). Pinch zoom 50–400%, two-finger pan.
- Per-page layer order: background → elements → ink. Ink above elements allows annotating clips and text.
- Tools:
  - **Pen**: pen, pencil, fountain pen, highlighter. Pressure and tilt via Ink brush families. Up to 10 saved color/width presets.
  - **Eraser**: whole-stroke or partial (geometry-based).
  - **Lasso**: selects strokes and elements together; move, resize, recolor, delete, copy.
  - **Text**: tap to place a text box; format bar with bold, italic, underline, H1/H2, bullets, numbered list, checklist, text color. Stored as HTML.
  - **Insert**: Photo Picker (gallery), camera capture, PDF clip.
  - Page background chooser per page; add/delete/reorder pages.
- Undo/redo: note-wide command stack (ink add/erase, element add/move/resize/delete/edit), 100 steps.
- Autosave: ink on stroke end; elements debounced 500 ms. No save button. Thumbnail regenerated on editor exit.

### Input rules (Notes, flashcard ink faces, and reader capture)

- Stylus present: stylus draws; finger scrolls, zooms, selects. Palm rejection via `PointerType`.
- No stylus: finger draws while a pen tool is active; two fingers always pan/zoom.
- S Pen side button held: temporary lasso.
- Stylus eraser end (`PointerType.Eraser`): temporary eraser.

### Organize & export

- Notes home: folder chips (nested), pinned section, grid/list toggle, sort (updated, created, title), full-text search, tag filter.
- Export: whole note to PDF via `android.graphics.pdf.PdfDocument` (text and ink drawn as vectors), or current page as PNG. Shared through the system share sheet via the existing FileProvider.
- Tapping a PDF clip element offers "Open source", navigating to `reader/{bookId}?page={page}`.

## 4. Phase 3 — Reader capture and side panel

### Text selection

- Finger: long-press a word, drag handles (extends the existing selection toolbar).
- Stylus: S Pen button + drag, or the new **Select** rail tool + drag. Selection snaps to words via `PdfTextIndex.selectRange`.
- Selection toolbar adds **Add to note**, **Flashcard**, **Cloze** alongside existing Copy, Highlight, Share.

### Region clip

- New **Clip** rail tool: drag a rectangle (finger or stylus) on any page, including scanned pages.
- The region is re-rendered from `PdfRenderer` at 2× display scale, capped at 4 MP, saved as WebP.

### Destination sheet

- Notes: **Last used note** (one tap), **Choose note…**, **New note**.
- Cards: **Last used deck**, **Choose deck…**, **New deck**.
- Text becomes a TEXT element formatted as a quote with attribution "— {book title}, p. {n}". Clips become PDF_CLIP elements.
- Placement: below the lowest element on the note's last page; if it does not fit, a new page is appended.
- Snackbar: "Added to {note}" with **Undo** and **Open note**.
- The last-used note/deck is stored in DataStore.

### Side panel

- Width ≥ 840 dp: reader and the active note side by side, with a draggable divider.
- Phones: resizable bottom sheet (peek, half, full).
- The panel hosts the full note editor. Selected text and clips can be dragged from the reader into the panel (Compose drag-and-drop) or sent via the destination sheet.

## 5. Phase 4 — Flashcards

### Data model

| Table | Columns |
|---|---|
| `decks` | `id`, `name`, `colorHex`, `parentId`, `bookId` (nullable), `newPerDay` (default 20), `reviewsPerDay` (default 200) |
| `cards` | `id`, `deckId`, `type` (`BASIC`, `REVERSED`, `CLOZE`), `frontJson`, `backJson`, `sourceBookId`, `sourcePage`, `tags`, `createdAt`, `updatedAt` |
| `card_schedule` | `cardId`, `ordinal` (0 = forward, 1 = reverse, n = cloze cn), `state` (`NEW`, `LEARNING`, `REVIEW`, `RELEARNING`), `ease`, `intervalDays`, `dueAt`, `step`, `reps`, `lapses` |
| `review_log` | `id`, `cardId`, `ordinal`, `rating`, `elapsedMs`, `prevIntervalDays`, `newIntervalDays`, `reviewedAt` |

Face content (`frontJson`/`backJson`) is an ordered list of blocks: `{type: "html", html}`, `{type: "image", path}`, `{type: "ink", path}`. Files live under `filesDir/cards/<cardId>/`.

### Scheduler (SM-2 variant; pure Kotlin, no Android dependencies)

- New/learning steps: 1 min → 10 min → graduate at 1 day. **Easy** on a new card graduates at 4 days.
- Review ratings:
  - **Again**: lapse +1, ease −0.20, enter relearning (10 min step), new interval = max(1, interval × 0.5) on graduation.
  - **Hard**: interval × 1.2, ease −0.15.
  - **Good**: interval × ease.
  - **Easy**: interval × ease × 1.3, ease +0.15.
- Ease floor 1.3, starting ease 2.5. Intervals ≥ 1 day receive ±5% fuzz (seeded per card for testability).
- Daily limits per deck from `newPerDay` and `reviewsPerDay`; day boundary at 04:00 local time.

### Cloze

- Syntax `{{c1::text}}` or `{{c1::text::hint}}` inside HTML blocks.
- Each distinct cN creates one `card_schedule` row.
- In review, the active cloze renders as **[…]** (or **[hint]**); other clozes render as plain text.
- The **Cloze** action from the reader opens the card editor pre-filled with the selected sentence.

### Screens

- **Decks home**: deck tree with New / Learning / Due counts; total "Due today" also shown on BookFlow Home.
- **Card editor**: front/back editors reusing the Notes rich-text and ink components; insert clip and photo; type selector; cloze button.
- **Review**: front → tap to reveal → Again / Hard / Good / Easy, each labeled with its next interval; undo last rating; "Show source page" link.
- **Stats**: reviews per day (30 days), retention percentage, 7-day due forecast.

## 6. Phase 5 — Navigation, global search, Home

- Bottom bar: Home · Library · Notes · Cards · Settings.
- Library gains a **Books | Collections** segmented switch; the Collections screen content moves there.
- Search becomes a top-bar action on Home, Library, Notes, and Cards. Results are grouped: Books (title/author), Book text (existing search), Notes (FTS), Cards (front/back text).
- Home adds a "Due today" flashcard card and a "Recent notes" shelf.

## 7. Error handling

- Room migrations are explicit and tested; no destructive fallback.
- Missing or corrupt ink/image file: that element/page shows a placeholder; the rest of the note opens. File write failures surface as a snackbar and the in-memory state is retained for retry.
- Clip/photo insert checks free space (require ≥ 50 MB) before writing.
- Deleted source book: "Open source" shows "Book no longer in library"; the clip remains.
- Large-PDF text failures degrade to render-only (Section 2).

## 8. Testing

- **Unit**: scheduler transitions for every state × rating; fuzz bounds; daily limits; cloze parsing and rendering; page-fraction ↔ pixel conversion; HTML-to-plain-text stripping for FTS; capture placement rule.
- **Room (instrumented, in-memory)**: each migration; FTS queries; cascade deletes and file cleanup.
- **Instrumented UI/integration**: import + open 150/500/1,500-page fixtures; select text → add to note → element appears → open source navigates to the page; create card → review → schedule updates; export note to PDF produces a file with the right page count.
- **Manual device checklist** (delivered with Phase 2): stylus latency, pressure and tilt, palm rejection, S Pen button lasso, eraser end, finger-only drawing on a stylus-less phone.

## 9. Dependencies and build changes

- Add `androidx.ink:ink-authoring`, `ink-brush`, `ink-geometry`, `ink-rendering`, `ink-strokes`, `ink-storage` at 1.0.0.
- Add `com.mohamedrejeb.richeditor:richeditor-compose` 1.2.1.
- Toolchain bumps only as required by these libraries (Kotlin, AGP, Compose BOM, compileSdk). Verified in Phase 2's first task.

## 10. Delivery order

Each phase gets its own implementation plan and its own commits:

1. Large-PDF fix.
2. Shared ink wrapper → Notes editor → Notes home and export.
3. Reader capture (selection actions, Clip tool, destination sheet) → side panel.
4. Flashcards (data, scheduler, editor, review, stats).
5. Navigation restructure, global search, Home cards.

## 11. Out of scope

- Cloud sync, accounts, collaboration.
- OCR for scanned PDFs.
- Migrating reader annotations to `androidx.ink`.
- Handwriting-to-text recognition.
- Audio recording in notes.
