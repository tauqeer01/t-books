# AHT Books for iOS

Native iOS and iPadOS companion to the published Android app by Al Hadaf Tech (AHT).
Built with pure Swift, SwiftUI, PDFKit, and PencilKit. 100% offline, privacy-first, zero ads, zero external analytics.

## Features Implemented

- **Home & Shelves**: Continue Reading hero card with page thumbnail and progress tracking, Daily Reading Goal ring widget with live minutes tracking, Collections quick cards, and My Library shelf.
- **Library & Document Management**: PDF document picker (`UIDocumentPicker`) supporting import from Files and iCloud Drive, automatic high-res cover thumbnail generation via `PDFKit`, Grid and List views, sorting (Recently Opened, Title, Author, Progress, Date Added), and category filters.
- **Advanced PDF Reader**:
  - Native `PDFKit` engine with continuous vertical scrolling, horizontal page turning, single-page, and realistic book page-curl modes.
  - Full-text search with match highlighting, live count indicators, and step navigation.
  - Table of Contents / Outline navigation sheet (`PDFOutline`) and Bookmarks manager.
  - Reader Themes: Light, Dark (Night Mode with preserved diagram colors), Sepia (warm paper), and Mint.
  - Tool Dial: radial floating selector for rapid tool switching at the edge of the screen.
  - Contextual text selection menu: Highlight (5 swatches: Yellow, Green, Blue, Pink, Purple), Underline, Strikethrough, Add Note, Copy, Send to Notebook, and Create Flashcard.
  - `PencilKit` overlay canvas allowing low-latency stylus and finger annotations persisted per PDF page.
  - Export annotated PDF copy via standard iOS Share Sheet.
- **Notebooks & Handwritten Notes**:
  - A4 notebooks with 9 page background templates: Blank, Ruled, Dotted, Math Graph, Cornell, Music Staff, Isometric, Checklist, and Two Column.
  - Full `PencilKit` (`PKCanvasView`, `PKToolPicker`) for Apple Pencil pressure and tilt sensitivity.
  - Insert rich text blocks, sticky notes, and book quote excerpts with attribution.
  - Export notebook pages to PDF or PNG.
- **Flashcards & Spaced Repetition**:
  - Decks management with due today counters.
  - Card creator with Basic, Reversed, and Cloze (`{{c1::answer::hint}}`) deletions.
  - SM-2 spaced repetition study loop (Again, Hard, Good, Easy) with 04:00 AM local time rollover and review preview intervals.
  - Study scratchpad for handwriting answers with Apple Pencil before flipping.
- **Fill & Sign (eSign)**:
  - Signature and Initials drawing canvas with transparent PNG cropping.
  - Interactive PDF field placement: Signature, Initials, Date, Text, Checkmarks.
  - Non-destructive export: stamps placed fields onto a new PDF copy without altering the original.
- **Settings & Legal**:
  - Reading preferences, goal adjusters, offline privacy overview, open-source licenses, and links to public legal policies.

## Project Structure

```
ios/
├── AHTBooks.xcodeproj/       # Xcode project file with shared schemes
├── AHTBooks/
│   ├── App/                  # App entrypoint (AHTBooksApp.swift, MainTabView.swift)
│   ├── Core/
│   │   ├── Theme/            # Theme.swift (brand colors, pastels, highlight swatches)
│   │   └── Components/       # Reusable UI (BookFlowEmblem, ReadingGoalRing, ToolDial, etc.)
│   ├── Data/
│   │   ├── Models/           # Models.swift (Book, Collection, Annotation, Note, Card, etc.)
│   │   └── Storage/          # StorageManager.swift (offline JSON & binary drawings storage)
│   ├── Features/
│   │   ├── Home/             # HomeScreen.swift
│   │   ├── Library/          # LibraryScreen.swift, BookDetailsScreen.swift
│   │   ├── Collections/      # CollectionsScreen.swift
│   │   ├── Reader/           # PDFReaderView.swift, PDFKitContainerView.swift
│   │   ├── Notes/            # NotesHomeScreen.swift, NoteEditorView.swift, PageTemplateRenderer.swift
│   │   ├── Flashcards/       # CardsScreen.swift, ReviewView.swift, CardEditorScreen.swift, Scheduler.swift, Cloze.swift
│   │   ├── ESign/            # ESignHomeScreen.swift, SignEditorView.swift, SignaturePadView.swift
│   │   └── Settings/         # SettingsScreen.swift
│   ├── Resources/            # Assets.xcassets (AppIcon 1024, AccentColor)
│   └── Info.plist            # PDF document associations and permissions
```

## Build and Run

Requirements: macOS with Xcode 15+ (tested on Xcode 27) and iOS 17+ deployment target.

### Building from Command Line

```bash
# Build for iOS Simulator
xcodebuild -project ios/AHTBooks.xcodeproj \
           -scheme AHTBooks \
           -destination "generic/platform=iOS Simulator" \
           build CODE_SIGNING_ALLOWED=NO

# Install and launch on a booted simulator
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData/AHTBooks-*/Build/Products/Debug-iphonesimulator -name "AHTBooks.app" | head -n 1)
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted com.alhadaftech.bookflow
```

### Opening in Xcode

Open `ios/AHTBooks.xcodeproj` directly in Xcode. Select any iPhone or iPad simulator or connected device and press **Cmd + R** to run.
