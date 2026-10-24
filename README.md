# AHT Books

An offline PDF library, annotation reader, notes and flashcards app by Al Hadaf Tech (formerly BookFlow). The Android app is built with Kotlin, Jetpack Compose, Room, DataStore, Coroutines, and Navigation Compose; an iOS app is planned.

Implementation status and verification evidence are maintained in [progress.md](progress.md).

## Repository layout

| Folder | What it holds |
|---|---|
| `android/` | The Android app: Gradle project (`app/`, `baselineprofile/`, wrapper). Open this folder in Android Studio. Signing files (`keystore/`, `keystore.properties`) live here, git-ignored. |
| `ios/` | The iOS app (planned). |
| `store/` | Store listings and artwork: `play-store/` (release checklist, icon, feature graphic, screenshots); an `app-store/` folder will sit beside it. |
| `docs/` | The public website, published by GitHub Pages from `main` → `/docs` at https://tauqeer01.github.io/t-books/. It hosts the privacy policy and terms the store listings link to, so keep `docs/legal/` paths stable. |
| `scripts/` | Repo tools, e.g. `export_legal_docs.py` (writes `docs/legal/` from the in-app legal text). |

## Implemented experience

- Home shelves: Continue Reading, Recently Opened, My Library, and Collections, using real document data and cover thumbnails.
- PDF import through Android's document picker, duplicate detection, favorites, adaptive grid/list views, sorting, sharing, and many-to-many collections.
- Book information, bookmarks, highlights, notes, and annotation navigation.
- Lazy PDF rendering, bounded bitmap/text caches, page thumbnails, real document outlines and internal links, and full-document text search with temporary match highlights.
- Vertical continuous, horizontal paging, and single-page reading; zoom/pan; global and per-book preferences for spacing, brightness, orientation, keep-awake, volume navigation, and immersive reading.
- Optional PDF dimming preserves image colors rather than inverting diagrams.
- Pen/highlighter/eraser, saved page-relative strokes, undo/redo, stylus-only mode, and annotated-copy export.
- Large PDFs open at any page count, read in place; text features degrade gracefully instead of crashing on huge scans.
- Notes: A4 notebooks with nine page templates (drawing canvas, ruled, math graph, Cornell, music, isometric…), low-latency stylus ink (Jetpack Ink), pens, shapes, lasso, eraser, rich text, sticky notes, photos, folders, tags, full-text search, and PDF/PNG export.
- Reader capture: select text (long-press drag or Select tool) or clip any region, send it to a note, or study it beside the book in a notes panel (side by side on tablets).
- Flashcards: decks, basic/reversed/cloze cards with pictures and handwriting, SM-2 spaced repetition with daily limits, review statistics, and cards created straight from a book selection.

The first-run sample is an actual two-page Aircraft Systems PDF. Imported documents retain their own artwork, metadata, page counts, and annotations. Sample book counts and unrelated document titles are not fabricated to populate the reference layout.

## Build and verify

Use Java 17 and an Android SDK with platform 36 installed. Set `ANDROID_HOME` or provide `sdk.dir` in `android/local.properties`. Run Gradle from `android/`:

```sh
cd android
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation suite requires a running Android device/emulator. It creates isolated document fixtures and an in-memory database for repository checks, and restores preferences after testing.

## Structure

- `domain/`: models, repositories, and use cases.
- `data/`: Room, DataStore, and repository implementations.
- `presentation/`: Compose screens, navigation, and view models.
- `core/`: visual theme and utilities.
- `pdf/`: engine abstraction, rendering, text/navigation, drawing, and export.

`PdfEngine` isolates consumers from PDF implementations. Android `PdfRenderer` handles raster pages; [PDFBox Android](https://github.com/TomRoush/PdfBox-Android) supplies text positions, search, metadata, outlines, and internal destinations across the supported Android versions. Seekable document URIs are read in place. A scanned PDF without a text layer remains readable but is not searchable; OCR is not included.

EPUB, backup/restore, AI, and the broader production-release phases 10–15 are outside this implementation. Release/performance qualification and hardware stylus testing should be tracked independently of emulator regression checks.
