# BookFlow

An offline Android PDF library and annotation reader built with Kotlin, Jetpack Compose, Room, DataStore, Coroutines, and Navigation Compose.

Implementation status and verification evidence are maintained in [progress.md](progress.md). Product scope is in [docs/prd.txt](docs/prd.txt); the PNG files beside it are the visual references.

## Implemented experience

- Home shelves: Continue Reading, Recently Opened, My Library, and Collections, using real document data and cover thumbnails.
- PDF import through Android's document picker, duplicate detection, favorites, adaptive grid/list views, sorting, sharing, and many-to-many collections.
- Book information, bookmarks, highlights, notes, and annotation navigation.
- Lazy PDF rendering, bounded bitmap/text caches, page thumbnails, real document outlines and internal links, and full-document text search with temporary match highlights.
- Vertical continuous, horizontal paging, and single-page reading; zoom/pan; global and per-book preferences for spacing, brightness, orientation, keep-awake, volume navigation, and immersive reading.
- Optional PDF dimming preserves image colors rather than inverting diagrams.
- Pen/highlighter/eraser, saved page-relative strokes, undo/redo, stylus-only mode, and annotated-copy export.

The first-run sample is an actual two-page Aircraft Systems PDF. Imported documents retain their own artwork, metadata, page counts, and annotations. Sample book counts and unrelated document titles are not fabricated to populate the reference layout.

## Build and verify

Use Java 17 and an Android SDK with platform 36 installed. Set `ANDROID_HOME` or provide `sdk.dir` in your local `local.properties`.

```sh
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
