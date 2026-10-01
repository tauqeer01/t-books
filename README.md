# BookFlow — Modern Android PDF Reader & Annotation App

Inspired by the elegance and simplicity of Apple Books, designed natively for Android phones and tablets using Kotlin and Jetpack Compose.

## 🏗 Architecture & Tech Stack

- **UI & Design:** Jetpack Compose + Material Design 3 (M3)
- **Architecture:** Clean Architecture + MVVM
- **Database:** Room with Kotlin Symbol Processing (KSP)
- **Preferences:** Jetpack DataStore
- **Asynchronous Flow:** Kotlin Coroutines + StateFlow
- **Navigation:** Navigation Compose
- **Storage:** Android Storage Access Framework (SAF)
- **Image Loading:** Coil
- **PDF Engine Abstraction:** `PdfEngine` decoupled interface with Android native `AndroidPdfRendererEngine` (hardware accelerated, high-DPI supersampling, thread-safe LruCache) and `PdfiumEngineAdapter` modular integration.

## 📱 Modules & Layers

- `core/`: Centralized Apple Books inspired color palettes (Classic Light, Warm Sepia, Night OLED, Pastel Mint), typography, date and file utilities.
- `domain/`: Pure domain entities (`Book`, `BookCollection`, `BookAnnotation`, `Bookmark`, `UserReadingPreferences`), repository interfaces, and clean use cases.
- `pdf/`:
  - `PdfEngine`: Document lifecycle, multi-scale page rendering, normalized coordinate rectangle mapping, search, and outline tree extraction.
  - `AndroidPdfRendererEngine`: High-performance hardware-accelerated rendering.
  - `SamplePdfGenerator`: Realistic multi-page technical textbook generation ("Aircraft Systems & Operations Reference Manual" with aerodynamics, hydraulics, glass cockpit EFIS schematics, and turbine diagrams).
- `data/`: Room entities, DAOs, `BookFlowDatabase`, `DataStorePreferencesRepository`, repository implementations, and `AppContainer` dependency injection.
- `presentation/`:
  - `SplashScreen`: Minimalist book emblem with smooth entrance transition.
  - `LibraryScreen`: Continue Reading hero card, category chips ("All", "Reading Now", "Textbooks", "Favorites", "Design"), grid/list view toggle, live search, and SAF PDF import.
  - `CollectionsScreen`: Pastel colored shelves, category folder tiles, book counters, and custom collection creator with pastel color picker.
  - `ReaderScreen`: Full PDF reader with multi-touch zoom/pan, chapter outline (TOC), bookmarks, annotations overlay (highlights, underlines, notes with color selector), and page scrubber.
  - `AnnotationsScreen`: Universal quote and highlight repository filtered by book, type, and color swatches.
  - `SettingsScreen`: Theme selector, continuous vertical scroll vs horizontal page flip, high-DPI supersampling, cache management, and sample library reloader.
