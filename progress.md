# BookFlow implementation progress

Updated: 2026-10-01

## Scope and completion rules

- Match the five UI references in `docs/` with working, responsive Android screens.
- Complete the PRD through phase 9. Phases 10–15 remain future work.
- Preserve the user's existing work and library data.
- `[x]` means the stated implementation or check is done. Implementation checkmarks do **not** imply device validation; validation is tracked separately.
- A phase is complete only after its remaining implementation and relevant checks pass. Do not claim an exact visual match without screenshot comparison.

## Review findings

- [x] Read `docs/prd.txt` and inspect all five supplied UI references.
- [x] Review the current navigation, library, collections, preferences, reader, and PDF engine.
- [x] Identify placeholder settings/actions, hard-coded collection counts, and mock PDF text search/selection.
- [x] Identify the baseline Java 21 / Kotlin 17 compilation mismatch and align targets to Java 17.
- [x] Preserve pre-existing changes to Gradle files, wrapper, keystore, and documentation.
- [x] Finish regression review of phases 1–6; code audit confirmed all feature code is implemented (2026-10-01).

## UI replacement

- [x] Apply the reference's violet brand color and pale backgrounds.
- [x] Implement a reusable gradient book emblem.
- [x] Replace Home with Continue Reading, Recently Opened, My Library, and Collections sections backed by real data.
- [x] Add actual PDF cover thumbnails, reading progress, search, filters, and working section navigation.
- [x] Replace Collections with pastel quick cards and real book-preview shelves; wire create, search, sort, open, and remove.
- [x] Replace Settings with grouped reference-style rows and working preference editors.
- [x] Add adaptive library grids and a compact list view.
- [x] Generate the reference-inspired open-book splash illustration.
- [x] Copy the illustration into the app and finish the splash screen replacement.
- [ ] Finish reader layout/toolbar polish and verify gesture behavior.
- [ ] Compare phone screenshots against the supplied designs and fix layout differences.
- [ ] Check tablet and landscape layouts.

## Phase 4 — Text selection and highlighting

- [x] Long-press selectable PDF text to trigger contextual toolbar.
- [x] Contextual toolbar actions: Highlight, Underline, Strikethrough, Add Note, Copy.
- [x] Highlight colors: Yellow, Green, Blue, Pink, Purple.
- [x] Persist annotations with book ID, page number, selected text, type, color, PDF coordinates, and timestamps.
- [x] Highlights remain correctly positioned after zoom, rotation, different screen sizes, and reopening.
- [x] Tap highlighted text to show contextual menu: Edit note, Change color (5 swatches), Delete highlight.
- [x] Contextual selection toolbar anchors beside actual selected text.
- [ ] Focused long-press/zoom UI check pending on device.

## Phase 5 — Notes, bookmarks and annotation hub

- [x] BookDetails screen with 4 tabs: Overview, Bookmarks, Highlights, Notes.
- [x] Overview tab: study-hub summary grid (highlights, notes, underlines, bookmarks counts) and document properties (title, author, filename, file size, pages, dates, progress, storage source).
- [x] Bookmarks tab: list all bookmarks with page badges, click to jump, delete.
- [x] Highlights tab: search highlighted text/notes, filter by annotation type (All/Highlight/Underline/Strikethrough), filter by highlight color (5 swatches), sort by page/date.
- [x] Notes tab: search notes, click to jump to page, edit/delete.
- [x] Click any annotation → opens reader at the corresponding page.
- [x] Edit note dialog with inline text editor.
- [ ] Verify all annotation hub filters, search, and navigation work correctly on device.

## Phase 6 — Pen and freehand annotation

- [x] Drawing toolbar with Pen, Highlighter, and Eraser tools.
- [x] Color selector: 6 pen colors, 5 highlighter colors.
- [x] Stroke width presets: Fine, Med, Bold, Max.
- [x] Undo and Redo support.
- [x] Clear page drawings button.
- [x] Stylus input differentiation (`PointerType.Stylus`) with "Stylus Only" / "Touch+Pen" toggle.
- [x] Multi-touch rejection (prevents drawing on multi-finger gestures).
- [x] Persist strokes using PDF-relative normalized coordinates via Room (`StrokeSerializer`).
- [x] Quadratic Bezier curve midpoint interpolation for smooth rendering.
- [x] Live in-progress stroke rendering for low-latency feedback.
- [x] Eraser with visual cursor and segment-intersection detection.
- [x] Export annotated PDF copy with share dialog (`PdfAnnotatedExporter`).
- [ ] Verify pinch zoom, double-tap, pan, finger scrolling, and stylus drawing coexist (shared with Phase 8).

## Phase 7 — Search and navigation (in progress)

- [x] Replace canned PDF text with an on-device PDFBox text/outline companion behind `PdfEngine`.
- [x] Read seekable SAF documents in place; bound the extracted text-page cache.
- [x] Implement real text matches, snippets, page numbers, and normalized match bounds.
- [x] Add cancellable, debounced reader search with loading/error states.
- [x] Connect search result navigation and temporary page highlights.
- [x] Keep page thumbnails, page entry, bookmarks, and annotation navigation connected.
- [x] Add a reading-history tab to reader navigation.
- [x] Remove fabricated table-of-contents entries.
- [x] Validate searches against generated and imported PDFs, including no-text documents.
- [ ] Verify page jumps, current-page tracking, reopening, and search-highlight alignment.
- [x] Review internal PDF links and resource/gesture handling inherited from phase 3.

## Phase 8 — Reading experience (in progress)

- [x] Add vertical, horizontal, and single-page preference options.
- [x] Persist page spacing, brightness, keep-awake, orientation, volume navigation, immersive reading, and optional page dimming.
- [x] Add global defaults and optional per-book overrides/reset.
- [x] Connect reader window brightness, orientation, keep-awake, immersive system bars, and volume-key handling.
- [x] Apply rendering-quality preference and optional non-inverting PDF dimming.
- [x] Persist default highlight/pen settings and library view/sort preferences.
- [ ] Verify every preference survives restart and applies to the intended scope.
- [x] Verify exiting the reader restores window/system behavior.
- [ ] Verify pinch zoom, double-tap, pan, finger scrolling, and stylus drawing coexist.

## Phase 9 — Library experience (in progress)

- [x] Connect live home shelves and collection counts.
- [x] Fix many-to-many collection membership reads.
- [x] Add long-press actions: Open, Favorite, Add to Collection, Book Information, Share, Remove from Library.
- [x] Add adaptive grid/list library views, search, sort, Recent, Reading, and Favorites filters.
- [x] Fix import feedback observation and duplicate classification.
- [x] Remove associated bookmarks when removing a library book; preserve originals on library removal.
- [x] Finish collection-saving integration in the rewritten library screen.
- [x] Complete book information: filename, dates, metadata, and annotation counts.
- [x] Correct misleading built-in sample metadata/data without deleting user documents.
- [ ] Validate import, duplicate detection, collections, favorites, sharing, and removal on emulator.

## Phase 16 — Large PDFs (complete, 2026-10-15)

Any PDF, regardless of page count, opens in BookFlow, read in place from device storage (no hand-off to another viewer).

- [x] Reproduce with generated 150/500/1,500-page PDFs and a 300-page, 436 MB scanned PDF. Root cause: import called `documentMetadata()`, and PDFBox 2 `initialParse` loads every stream reachable from the catalog into an unbounded in-heap scratch buffer → `OutOfMemoryError` (192 MB heap) crashed the app. Second cause: pdfium inside `PdfRenderer` retains parsed objects for every visited page, so native memory grew ~200 MB+ while reading a large scan.
- [x] Import uses `PdfRenderer` only (page count + cover); title/author applied afterwards in the background. Import: 1,500 pages 5,244 → 45 ms; 300-page scan 3,869 → 584 ms; 415 MB scan 177 ms.
- [x] PDFBox buffers stream data in a temp file (`cache/pdfbox`, 4 MB heap cap) instead of the heap.
- [x] Build the outline in one pass over the page tree instead of `pages.indexOf()` per entry.
- [x] PDFBox paths convert `OutOfMemoryError` to `IOException`; text failures degrade to render-only with a one-time notice.
- [x] "Open with" imports try a persistable grant first; a private copy is made only when the grant is temporary (otherwise the book could not be reopened after restart).
- [x] Reader memory bounded: `PdfRenderer` is reopened every 24 rendered pages to release pdfium's document cache. Native heap over a full 300-page scroll stays 80–120 MB (mostly collectable bitmaps) instead of climbing past 260 MB.
- [x] Duplicate detection matches by file size + leading 64 KB of content (titles change when metadata is applied; copies have no source URI).
- [x] Regression tests: `LargePdfRegressionTest` (150/500/1,500 pages, scanned, and a larger-than-heap stress file when present). Full suite 19/19 (stress skipped unless pushed; passed 415 MB run separately). Lint passes.
- Known limitation: full-text search is linear (~50 ms/page; ~74 s for 1,500 pages) and outline on very large files takes several seconds because PDFBox parses the whole file; both run in the background and are cancellable.

## Phase 17 — Notes (complete, 2026-10-15)

Fixed A4-ratio pages; free-positioned text/image/clip elements; ink on top. Engine: `androidx.ink` 1.0.0 (low-latency `InProgressStrokes`) + `compose-rich-editor` 1.0.0-rc11.

- [x] Dependencies added without toolchain bumps. Rich editor pinned to 1.0.0-rc11 (newest built for Kotlin 2.1/Compose 1.7; 1.0.0+ needs Kotlin 2.3+).
- [x] Room v4 (auto-migration 3→4): `note_folders`, `notes`, `note_pages`, `note_elements`, FTS4 `notes_fts`. Parent rows use `@Upsert` — `REPLACE` deleted pages' elements via `ON DELETE CASCADE` (caught on device, regression-tested).
- [x] Ink: pen, fountain pen, pencil, highlighter; pressure/tilt from stylus input; whole-stroke and partial eraser; one compact ink file per page (atomic writes).
- [x] Editor: custom pan/zoom viewport (50–400%, fling), lasso select → move/resize/recolor/duplicate/delete/bring to front, rich text boxes (bold, italic, underline, strike, heading, bullets, numbers, checklist, color) on ruled-line rhythm, photos from Photo Picker/camera (downscaled WebP, EXIF-rotated), page backgrounds, add/move/delete pages, 100-step note-wide undo/redo, debounced autosave plus save on background/close.
- [x] Input rules: stylus draws, fingers navigate once a stylus is seen; fingers draw on stylus-less devices ("Draw with finger" toggle); two fingers always pan/zoom and cancel a finger stroke; S Pen side button = lasso; stylus eraser end = eraser.
- [x] Notes home: nested folders with breadcrumbs, pinned section, grid/list, sort, full-text search (titles, text, tags, clip quotes), tag filter, rename/tag/move/delete.
- [x] Export: whole note as PDF (A4, vector text/ink) and page as PNG via the share sheet.
- [x] Navigation (interim until Phase 20): bottom bar Home · Library · Notes · Collections · Settings; Search moved to a Home top-bar action.
- [x] Tests: `NotesLogicTest` (11 JVM: placement, HTML→text, FTS query, eraser/lasso geometry, undo history) and `NotesRegressionTest` (8 instrumented: page add/reorder keeps elements, capture placement, FTS + cascade delete, folder delete, ink file round-trip, PDF export with ink at page scale, photo import, 3→4 migration). Full suite 27 instrumented (1 stress skipped) + 23 unit; lint passes.
- Backup: device-to-device transfer includes `files/notes/`; cloud backup stays database-only (25 MB quota), so restored notes keep text but show missing ink/images as unavailable.
- Deviations: Pencil is a soft graphite marker (Ink's textured pencil and forced path rendering are restricted APIs in 1.0). Strokes use stock brushes only.

### Follow-up: templates and tools (2026-10-15, from user review)

User feedback: only one ruled sheet was visible and tools felt limited. Templates existed but were hidden in the overflow menu.

- [x] Nine A4 templates in a visual picker (real miniature pages): Drawing canvas, Ruled, Narrow ruled, Square grid, Math graph (1 mm/5 mm/1 cm), Dot grid, Cornell notes, Music staff, Isometric. Spacing uses real millimetres.
- [x] Picker appears for New note (remembers the last choice), Add page, and Change page template (optionally all pages); mixed templates per note.
- [x] "+ Add page" tile under the last page (never inks in Pen mode).
- [x] Shapes tool: line, arrow, rectangle, ellipse, triangle with live preview, using the active pen color/width.
- [x] New pens: Marker and Dashed (now Pen, Fountain pen, Pencil, Marker, Dashed, Highlighter).
- [x] Sticky notes with six colors (Room v5 auto-migration adds `note_elements.fillColor`).
- [x] Page overview with thumbnails: tap to jump, arrows to reorder; Pages button in the top bar.
- [x] Labeled toolbar that fits a phone: Pen, Shapes, Eraser, Lasso, Text, Select, Insert (Sticky note, Photo, Camera, Page).
- [x] Tests: `NoteShapesTest` (5), 4→5 migration and sticky/template persistence in `NotesRegressionTest`. Suite: 28 instrumented (1 stress skipped) + 28 unit; lint passes.

### Manual stylus checklist (real hardware; the emulator cannot verify these)

- [ ] Ink appears under the nib with no visible lag on a Samsung S Pen and a USI stylus.
- [ ] Line width follows pressure with the fountain pen; tilt shading is visible.
- [ ] Resting the palm while writing leaves no marks; a finger swipe scrolls once the stylus has been used.
- [ ] Holding the S Pen side button draws a lasso; the stylus eraser end erases.
- [ ] On a phone with no stylus, a finger draws; two fingers pan/zoom and cancel the half-drawn stroke.

## Phase 18 — Reader capture into Notes (complete, 2026-10-15)

- [x] Finger: long-press selects a word; keep dragging to extend word by word (works with a stylus too). Rail tool **Select text** drags out a selection directly.
- [x] Selection bar leads with **To note** (plus a drag handle while the notes panel is open); highlight/underline/strike/note/copy unchanged.
- [x] Rail tool **Clip to note**: drag a rectangle on any page (including scans); only that region is rendered by `PdfRenderer` at ~2× display size (≤ 4 MP) and saved as WebP.
- [x] Destination sheet: Last used note, New note (named after the book), or any note. Quotes keep “…” plus “— Book, p. N”; every capture links back to its page. Snackbar offers Undo and Open.
- [x] Notes panel (header button): reader and note side by side on screens ≥ 840 dp, stacked on phones, with a draggable divider. Captures go straight into the open panel and scroll into view; text can be dragged from the selection handle onto the panel; tapping a clip's page chip jumps the reader beside it.
- [x] Tests: `ReaderCaptureTest` (quote escaping/attribution, clip aspect + file, region render size/content, capture → note → undo). Suite: 32 instrumented (1 stress skipped) + unit; lint passes.
- Flashcard and cloze actions from the selection bar arrive with Phase 19.
- Known limitations: range selection follows the PDF's reading order, so it can pick up a neighbouring column or side box; drag-and-drop into the panel was verified to compile and wire up but needs a manual check on a real device (adb cannot drive a cross-window drag).

## Phase 19 — Flashcards (complete, 2026-10-15)

- [x] Room v6 (auto-migration): `decks`, `cards` (faces as JSON: rich text + optional picture), `card_schedule` (one row per reviewable side), `review_log`.
- [x] SM-2 scheduler, pure Kotlin: learning steps 1 m → 10 m → 1 d (Easy 4 d), Hard ×1.2 / Good × ease / Easy × ease × 1.3, each button strictly longer than the last, lapse → 10 m relearning with interval halved, ease floor 1.3, ±5% fuzz from 3 days, day rollover at 04:00 local (DST-safe). Button labels show the next interval.
- [x] Study queue: due learning steps (with 20-minute learn-ahead), reviews due today, then new cards in creation order; daily new/review limits per deck count what was already studied today; counts include sub-decks.
- [x] Card types: Basic, Basic + reversed, Cloze (`{{c1::answer::hint}}`, one card per number). Editor: deck picker/new deck, rich text with the Notes format bar, “[…] Make blank”, pictures from the photo picker, and handwriting via a low-latency sketch pad; “Save & add another”. Editing a card keeps progress on surviving sides.
- [x] Screens: Cards tab (deck tree with New/Learning/Due, long-press deck settings: rename, limits, sub-deck, delete), deck (Study now, card list, delete), review (tap to reveal, Again/Hard/Good/Easy, undo, show source page), statistics (retention, 30-day reviews, 7-day forecast).
- [x] From the reader: selection bar row 1 = To note · Card · Cloze (row 2 = highlight tools). Card puts the selection on the back; Cloze opens the editor with the sentence; both go to the book's deck (created on first use) and link to the page.
- [x] Fixed on device: Android's ICU regex rejects a bare `}` that the JVM accepts (crash in the deck list); the rich editor writes punctuation as HTML5 entities (`&comma;`, `&colon;`, `&lcub;`), which broke cloze parsing and would show literally in note PDF export/search — editor HTML is now normalized (`HtmlText.normalize`) for notes and cards, and export/search decode older data.
- [x] Tests: `SchedulerTest` (9, incl. cloze), `FlashcardTest` (3, on-device: ICU cloze parsing, sides/queue/undo/edit, daily limit + stats + deck deletion), entity normalization unit test. Suite: 35 instrumented (1 stress skipped) + 38 unit; lint passes.
- Navigation: bottom bar is now Home · Library · Notes · Cards · Settings (Collections tab replaced; Collections remain reachable from Home until Phase 20 adds them to Library).

## Phase 20 — Navigation, global search, Home (complete, 2026-10-15)

- [x] Bottom bar: Home · Library · Notes · Cards · Settings.
- [x] Library has a Books | Collections switch; Home's collection links open the Collections side.
- [x] Search (Home top bar) covers books, highlights, book notes, notebooks (full-text index), and flashcards (front/back text), with scope chips.
- [x] Home: “N flashcards to study” banner (opens Cards) and a Recent Notes shelf with cover thumbnails.
- [x] Suite: 35 instrumented (1 stress skipped) + unit; lint passes.
- [x] Later: Home quick actions (compact 4-up tiles), Settings gear in the Home header, Settings trimmed to 7 rows and removed from the bottom bar.

## Phase 21 — eSign: Fill & Sign (complete, 2026-10-15)

- [x] Bottom bar: Home · Library · Notes · Cards · eSign. Fully offline.
- [x] Saved signature and initials (`esign/data/SignatureStore`, `filesDir/signatures/`): draw with finger or stylus (Jetpack Ink), type in a script/elegant/plain style, or photograph ink on paper (paper removed by a luminance threshold). Black or blue ink; stored as transparent PNGs trimmed to the ink, and saving replaces the previous one of that kind.
- [x] Sign a PDF from the library or from files (`SignEditorScreen`): Signature, Initials, Text, Date, ✓, ✗; tap to place, drag to move, use the corner handle to resize, double-tap text to edit; undo; discard warning.
- [x] Save (`esign/data/PdfSigner`) flattens the fields into each page's content stream (PDFBox, temp-file scratch for big files) and writes `filesDir/books/signed/<id>/<Title> (signed).pdf`, imported into the library; the original is only read. After saving: Open signed copy or Share. The eSign tab lists signed documents with Share.
- [x] Placement is stored as fractions of the displayed page and mapped through `/Rotate` and the crop box (`PdfPlacement`, unit-tested).
- [x] Fixed on device: text and date were drawn at size² (the font size and the text matrix both scaled them); the test now requires text to stay inside its field.
- [x] Tests: `PdfPlacementTest` (4 unit tests), `ESignTest` (4 on-device: placement on 0°/90°/180° pages, text bounds, signature trim/replace, ink extraction). Suite: 40 instrumented + unit; lint passes.

## Phase 22 — More Notes tools (complete, 2026-10-15)

- [x] Floating toolbar: a pencil button at the bottom right (draggable, settles against the nearer edge, position remembered) opens a palette of pens, highlighter, every tool, undo/redo, and paste; picking a pen or tool closes it. On/off in the editor's ⋮ menu. The bottom toolbar is unchanged apart from the new tools (it scrolls on narrow phones; Insert stays pinned).
- [x] Auto shape (default in Shapes): rough lines, triangles, rectangles, and ellipses snap to clean shapes (`ShapeRecognizer`); anything else stays freehand. New shapes: diamond, pentagon, hexagon, star.
- [x] Study tape (`ElementKind.TAPE`): drag to cover; tap to peel back or cover again in any tool; Show all / Hide all; drawn above ink in the editor and in exports; reopened notes start covered. Tape can't be written on.
- [x] Laser pointer: fading trail, never saved.
- [x] Lasso copy / cut / paste, including between notes (`NoteClipboard`); pasted items land in view and stay selected. A tap with the lasso selects or clears.
- [x] Eraser: Highlighter-only mode and Clear page (one undo step).
- [x] Pen editor: custom color picker (saturation/brightness square and hue strip) and recent custom colors.
- [x] Fixed: a new edit could cancel a save already under way ("Couldn't save changes: StandaloneCoroutine was cancelled"), leaving those pages unsaved until edited again; writes are now non-cancellable.
- [x] Tests: `ShapeRecognizerTest` (7 unit tests), `NoteToolsTest` (5 on-device: tape size/toggle/save, tape over ink in export, cross-note paste, highlighter eraser + undo, auto shape vs scribble). Suite: 45 instrumented + unit; lint passes.

## Verification

- [x] Baseline build attempted; found JVM target mismatch.
- [x] Intermediate `:app:assembleDebug` passed after the first implementation batch.
- [x] Start the installed Android API 36 emulator for validation.
- [x] Build the latest changes (library changes landed after the intermediate passing build).
- [x] Add/run focused regression tests for real PDF search/coordinates and persisted data/preferences.
- [x] Run Android lint and resolve relevant failures.
- [ ] Capture phone and tablet screenshots and inspect them.
- [ ] Record final commands, results, screenshot locations, and remaining limitations here.

## Future — intentionally excluded

- [ ] Phase 10: EPUB.
- [ ] Phase 11: Backup and restore.
- [ ] Phase 12: AI reading assistant.
- [ ] Phase 13: Full performance qualification matrix.
- [ ] Phase 14: Full security/privacy release hardening.
- [ ] Phase 15: Play Store release preparation.

Future-only controls retained from the visual references must be clearly labeled as future features.

## Next work order

All feature code for phases 1–9 is implemented. Remaining work is verification and testing only:

1. Build and deploy to emulator; verify gesture behavior (pinch/zoom/pan/stylus coexistence).
2. Verify all preferences survive restart and apply to the intended scope.
3. Verify page jumps, current-page tracking, reopening, and search-highlight alignment.
4. Validate import, duplicate detection, collections, favorites, sharing, and removal on emulator.
5. Verify annotation hub filters, search, and navigation on device.
6. Verify long-press/zoom UI on device.
7. Compare phone/tablet screenshots with the five reference designs and address visual issues.
8. Check tablet and landscape layouts.
9. Record final commands, results, screenshot locations, and remaining limitations here.

### Verification log

- Latest `:app:assembleDebug`: passed after splash/library/reader integration.
- Added six Android regression tests covering real search, rotated bounds, image-only/corrupt PDFs, global/per-book preferences, and collection/removal persistence. Execution pending.
- Splash asset: `app/src/main/res/drawable-nodpi/splash_book.png` (built-in imagegen; reference-inspired illustration).

- First isolated-emulator run: 7/8 tests passed; rotated-page bounds failed. Fixed coordinate transformation; rerun pending.
- Lint first run: 5 errors (restricted key dispatch override and unused Compose layout/padding scopes). Replaced with Activity key callbacks and corrected layout scopes; rerun pending.
- Original emulator was full; tests now use an isolated AVD under ignored `.local-review/`, preserving the existing emulator data.

- Continuation: replaced the selection toolbar’s stale local color reference with the persisted reader color state. Latest build/test/lint rerun is in progress.

- Regression rerun: **8/8 tests passed**, including all four PDF page rotations and both Compose interaction tests.
- `:app:lintDebug`: **passed**, with dependency/deprecation and existing advisory warnings.
- Added content-URI import/duplicate coverage and transactional collection updates; final rerun pending.

- Expanded regression suite: **10/10 passed**. Added content-URI import/duplicate checks and reader brightness/orientation/volume-key restoration on exit. Lint still passes.
- Phone visual review found excessive row/card spacing and a gray navigation-bar surround; tightened typography/spacing and fixed the navigation background.
- Contextual selection toolbar now anchors beside the actual selected text. Focused long-press/zoom UI check pending.
