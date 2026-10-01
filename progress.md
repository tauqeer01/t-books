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
- [ ] Finish regression review of phases 1–6; existing implementation is not yet independently certified complete.

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

1. Finish outstanding library wiring, splash integration, and truthful sample/book information.
2. Build, fix compilation errors, and review reader synchronization/gesture/resource behavior.
3. Run focused automated tests and emulator interaction checks.
4. Compare phone/tablet screenshots with references and address visual issues.
5. Update this file with verified results and any remaining gaps before reporting completion.

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
