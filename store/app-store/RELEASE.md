# AHT Books — App Store release checklist

Everything needed to publish **AHT Books** for iPhone and iPad, bundle ID `com.alhadaftech.bookflow` (the same ID as the Android package; the two stores are independent).
Version **1.0.0**, build **1**. Both come from `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in the Xcode project, and Settings › About shows them from the bundle.

## 1. Before every upload

- [ ] Bump the build number (`CURRENT_PROJECT_VERSION` in `scripts/generate_ios_project.py`, then `python3 scripts/generate_ios_project.py`). App Store Connect rejects a reused build number for the same version.
- [ ] For a user-visible release, also bump `MARKETING_VERSION` there.
- [ ] Never edit `ios/AHTBooks.xcodeproj/project.pbxproj` by hand. It is generated, and new Swift files are picked up by re-running the script.
- [ ] If stored data models changed (`ios/AHTBooks/Data/Models/Models.swift`): new fields must decode with `decodeIfPresent` (see `ReadingStats`) or users' saved data falls back to defaults.
- [ ] If the iOS privacy policy changed: edit `docs/legal/privacy-policy-ios.md`/`.html` **and** the matching text in `SettingsScreen.swift`, then publish `docs/`.
- [ ] Run the UI tests: `cd ios && xcodebuild -project AHTBooks.xcodeproj -scheme AHTBooks -destination 'platform=iOS Simulator,name=iPhone 18 Pro' test`
- [ ] Smoke-test on a real device: import a PDF from Files, highlight text, draw with the tool dial, write in a note, review a flashcard, save a signature and sign a PDF, switch to dark mode.

## 2. Signing and upload (one-time setup)

1. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list), register the App ID `com.alhadaftech.bookflow` (no extra capabilities are needed).
2. In Xcode › AHTBooks target › Signing & Capabilities, choose your team and keep **Automatically manage signing**. To keep it across project regeneration, add `DEVELOPMENT_TEAM = <TEAM ID>;` to the app target's build settings in `scripts/generate_ios_project.py`.
3. App Store Connect › Apps › **+ New App**: platform iOS, name *AHT Books: PDF Reader & Notes*, language English (U.S.), bundle ID `com.alhadaftech.bookflow`, SKU `aht-books-ios`, full access.
4. Build the upload file (team `8PLDJ8ZVP5`, AL-Hadaf Technologies Private limited, is set in `scripts/generate_ios_project.py`). The team has no registered test devices, so archive unsigned and let the App Store export sign it with the cloud-managed Apple Distribution certificate:
   ```
   cd ios
   xcodebuild -project AHTBooks.xcodeproj -scheme AHTBooks -configuration Release -destination 'generic/platform=iOS' -archivePath build/AHTBooks.xcarchive CODE_SIGNING_ALLOWED=NO clean archive
   xcodebuild -exportArchive -archivePath build/AHTBooks.xcarchive -exportPath build/export -exportOptionsPlist build/ExportOptions.plist -allowProvisioningUpdates
   ```
   `build/ExportOptions.plist`: `method` = `app-store-connect`, `teamID` = `8PLDJ8ZVP5`, `signingStyle` = `automatic`, `uploadSymbols` = true. The result is `ios/build/export/AHTBooks.ipa`.
   Upload it with the **Transporter** app (sign in with an App Store Connect user of the team, drag in the `.ipa`, Deliver). The app record from step 3 must exist first.
5. Export compliance is answered in the build: `ITSAppUsesNonExemptEncryption = NO` (the app uses no encryption beyond Apple's file protection).

## 3. Store listing (App Store Connect › App Information and version page)

| Field | Value |
|---|---|
| Name (29/30) | AHT Books: PDF Reader & Notes |
| Subtitle (26/30) | Read, annotate & sign PDFs |
| Primary category | Productivity |
| Secondary category | Education |
| Support URL | https://tauqeer01.github.io/t-books/ |
| Marketing URL | https://tauqeer01.github.io/t-books/ (optional) |
| Privacy Policy URL | https://tauqeer01.github.io/t-books/legal/privacy-policy-ios.html |
| Copyright | 2026 AL-Hadaf Technologies Private Limited |
| Price | Free |
| App icon | Comes from the build (`ios/AHTBooks/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`, 1024×1024, no alpha). A copy is at `store/app-store/icon-1024.png`. |

`docs/` must be published on GitHub Pages from `main` before submitting, so the support and privacy URLs resolve. The iOS policy page and the updated support page are new in `docs/`.

### Promotional text (168/170), can be changed without a new build

```
Highlight PDFs, write notes by hand with Apple Pencil, study with flashcards, and fill and sign forms. No account, no ads, no tracking. Everything stays on your device.
```

### Keywords (96/100), comma-separated, no spaces

Words already in the name or subtitle (AHT, Books, PDF, Reader, Notes, Read, annotate, sign) are indexed by Apple automatically, so they are not repeated here.

```
highlight,handwriting,notebook,flashcard,study,markup,document,textbook,offline,pencil,signature
```

### Description (2755/4000)

```
AHT Books is a private PDF reader, PDF annotator and handwriting notes app for iPhone and iPad. Read and highlight PDFs, write notes with Apple Pencil, study with flashcards, and fill and sign documents, all offline, with no account and no ads.

Built for students, professionals and anyone who reads textbooks, manuals, papers and forms, AHT Books keeps your documents, notes and study progress in one place.

READ PDFs YOUR WAY
• Smooth PDF viewer with continuous scrolling, page-by-page swiping or single-page reading
• Light, Dark, Sepia and Mint page colors, plus a full dark mode for the whole app
• Jump through long documents with the table of contents and bookmarks
• Search the text of any PDF with selectable text
• Always reopen a book on the page where you stopped

HIGHLIGHT AND ANNOTATE PDFs
• Highlight, underline or strike through text in several colors
• Draw on PDFs with pens and markers from a spinning tool dial at the edge of the page
• Add notes and bookmarks, with undo and redo
• Send a passage to your notebook or turn it into a flashcard in one tap
• Share an annotated copy of any PDF

HANDWRITTEN NOTES WITH APPLE PENCIL
• Digital notebooks with blank, ruled, grid, dotted, Cornell, music, checklist and more paper templates
• Low-latency Apple Pencil and finger writing, shape recognition, lasso, text boxes, photos and sticky notes
• Study tape to hide answers while you revise, and a laser pointer for presenting
• Organize notes with folders, tags and search, and export pages to PDF or image

FLASHCARDS AND SPACED REPETITION
• Basic, reversed and fill-in-the-blank (cloze) flashcards
• Spaced repetition schedules every card for the right day, so you remember more with less study time
• A handwriting scratchpad to work out answers before you reveal them

FILL AND SIGN PDF FORMS
• Draw and save your signature and initials
• Add signatures, initials, text, dates and checkmarks to any PDF
• Save a signed copy while your original file stays unchanged

BUILD A DAILY READING HABIT
• Set a daily reading goal and watch your progress ring fill
• Keep your reading streak going and see your whole week at a glance

ORGANIZE YOUR LIBRARY
• Import PDFs from the Files app, or open them from any app with Open In
• Favorites, collections and recently read books
• Search, sort, and switch between grid and list views

PRIVATE AND OFFLINE BY DESIGN
AHT Books has no account, no ads and no tracking, and it does not connect to the internet. Your PDFs, notes, flashcards and signatures stay on your iPhone and iPad.

Good to know: scanned PDFs without a text layer can be read and annotated, but their text cannot be searched.

AHT Books is made by Al Hadaf Tech (AHT). Questions or feedback? Write to aht.apps@alhadaftech.com.
```

### What's New in This Version (1.0.0)

```
First release of AHT Books for iPhone and iPad.
```

## 4. Screenshots

Listing images in the same style as the Google Play set: brand-purple gradient, a headline and a one-line description, and the iOS app screen with rounded corners. The screens are real captures from the iOS app in the simulator (9:41 status bar, built-in sample content). Upload in numbered order.

| Size slot in App Store Connect | Folder | Resolution |
|---|---|---|
| **iPhone with Dynamic Island (medium display)** (required) | `store/app-store/screenshots/iphone-6.3/` (8 images) | 1206×2622 |
| iPhone with Dynamic Island (large display) (optional; reach it via *View All Sizes in Media Manager*) | `store/app-store/screenshots/iphone-6.9/` (8 images) | 1320×2868 |
| **iPad 13" display** (required because the app runs on iPad) | `store/app-store/screenshots/ipad-13/` (8 images) | 2064×2752 |

Each slot accepts only its own sizes. Dropping the 1320×2868 set into the medium slot fails with "File dimensions are invalid".

Smaller iPhone and iPad sizes are scaled from these automatically. All images are RGB with no alpha channel.

| # | Screen | Headline | Description |
|---|---|---|---|
| 01 | Home | Your library, beautifully organized | Pick up right where you left off |
| 02 | Reader | Smooth, beautiful PDF reading | Search, bookmarks and four page colors |
| 03 | Note editor (iPad: notes list) | Handwritten notes that feel natural (iPad: All your notebooks in one place) | Templates, Apple Pencil ink and study tape (iPad: Folders, tags and search across every note) |
| 04 | Flashcard review | Study smarter with flashcards | Spaced repetition schedules every review |
| 05 | Fill & Sign | Fill and sign any PDF | Save your signature once, sign in seconds |
| 06 | Today's Reading | Build a daily reading habit | Set a goal, keep your streak, see your week |
| 07 | Library | Find any document fast | Search, sort, favorites and collections |
| 08 | Dark mode | Easy on the eyes, day or night | A full dark mode across the whole app |

To update: replace the raw captures in `store/app-store/screenshots/raw/<device>/` (same file names), edit headlines in `CAPTIONS` if needed, then run `python3 scripts/make_app_store_screenshots.py`.

## 5. App Privacy (App Store Connect › App Privacy)

| Question | Answer |
|---|---|
| Do you or your third-party partners collect data from this app? | **No, we do not collect data from this app** |
| Resulting label | **Data Not Collected** |

Why this is accurate: there is no networking code, no third-party SDK, no account and no analytics. Everything is stored on the device. Reading time, notes, flashcards and signatures never leave it, and Share/Export only sends a file where the user chooses. The required-reason APIs (`UserDefaults`, file size via `attributesOfItem`) are declared in `ios/AHTBooks/Resources/PrivacyInfo.xcprivacy` with `NSPrivacyTracking = false`.

## 6. Age rating

Answer **None / No** to every question (no violence, mature content, gambling, contests, unrestricted web access, or user-generated content shared with others). Expected rating: **4+**.

## 7. App Review information

- Sign-in required: **No**
- Contact: Al Hadaf Tech, aht.apps@alhadaftech.com (add a phone number in App Store Connect)
- Notes for the reviewer:

```
AHT Books works fully offline and has no account. A sample PDF ("Aircraft Systems & Operations"), a sample notebook and a sample flashcard deck are added on first launch so every feature can be tried right away:
• Home › Continue Reading opens the PDF reader. Long-press text to highlight it, or tap the round tool dial at the right edge to draw.
• Notes tab › "Biology & Cellular Notes" opens the handwriting editor (finger drawing is on).
• Flashcards tab › Study Now starts a review session.
• Fill & Sign tab › + Add draws a signature; then choose a document to sign.
• The ring at the top of Home shows today's reading time against the daily goal.
Camera access is only requested when the user chooses to take a photo for a note. The photo picker only receives photos the user selects.
```

## 8. What's already in the build

- Privacy manifest (`PrivacyInfo.xcprivacy`), export-compliance key, accurate camera and photo usage strings.
- App icon without alpha channel. Launch screen color follows light and dark mode.
- Light, dark and system app theme (Settings › Appearance, or the moon button on Home), with colors from the Android dark scheme.
- Sample content is added once, on first launch. Anything the user deletes stays deleted.
- Saved file paths are re-anchored on launch, so books and covers survive app updates and device restores (iOS can move the app's data folder).
- Reading time only counts while the reader is on screen and the app is active.
- Signatures are kept in protected storage outside the Files-visible Documents folder.
- iPhone and iPad: portrait and landscape; book grids adapt to iPad widths.

## 9. Open items

- [ ] **Publish `docs/`** (support page and `legal/privacy-policy-ios.html`) before submitting; the URLs above must load.
- [ ] Set the development team (section 2) and register the bundle ID.
- [ ] **iPad note editor:** a note opens scrolled about one header's height too far, so the top of the first page sits under the toolbar. Phones are fine. Worth fixing before or soon after launch, since reviewers also test on iPad.
- [ ] Three Notes editor UI tests fail on the current code, and also on the commit before the audit changes: `testEraserStrokeAndHighlighterModes`, `testLassoSelectMoveDuplicateRecolorDelete`, `testStickyNoteAndStudyTape`. Check whether the features or the tests are wrong before release.
- [ ] Optional: add App Store screenshots for dark mode or localized listings later; App Store Connect allows updating screenshots only with a new version.
