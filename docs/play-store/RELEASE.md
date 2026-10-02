# BookFlow — Google Play release checklist

Everything needed to publish **BookFlow 1.0.0 (version code 1)**, package `com.alhadaftech.bookflow`.
Store link (live after publishing): https://play.google.com/store/apps/details?id=com.alhadaftech.bookflow

## 1. Before every upload

- [ ] Bump `versionCode` (and `versionName` if user-visible) in `app/build.gradle.kts`. Play rejects a reused version code.
- [ ] If the database schema changed: bump `version` in `BookFlowDatabase`, add a `Migration` to `MIGRATIONS`, and commit the new file in `app/schemas/`. **Never** rely on destructive migration — it would erase users' highlights and notes.
- [ ] If `LegalDocuments.kt` changed: `python3 scripts/export_legal_docs.py` and re-publish `docs/legal/`.
- [ ] Build the bundle: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- [ ] Optionally smoke-test a release APK on a device: `./gradlew :app:assembleRelease` then `adb install -r app/build/outputs/apk/release/app-release.apk` (open a book, search text, open Settings › Open-source licenses, "Open with" a PDF from Files).

## 2. Signing (one-time setup — keep these safe)

- Upload key: `keystore/bookflow-upload.jks`, alias `bookflow-upload`, password in `keystore.properties`. Both are git-ignored.
- **Back up both files** (e.g. password manager + offline copy). Losing them means requesting an upload-key reset from Google support.
- Upload key SHA-256: `49:23:72:4F:B8:1F:56:40:61:91:8F:DF:59:D5:16:45:67:BF:D6:F9:55:E6:BD:60:B7:E9:8B:8D:4B:6A:46:78`
- On first upload, accept **Play App Signing** (Google holds the app signing key; you keep only the upload key).

## 3. Store listing (Grow › Store presence › Main store listing)

| Field | Value |
|---|---|
| App name (28/30) | BookFlow: PDF Reader & Notes |
| Short description (78/80) | Read, highlight and annotate PDFs. Private, offline, with daily reading goals. |
| App icon | `docs/play-store/icon-512.png` (512×512) |
| Feature graphic | `docs/play-store/feature-graphic-1024x500.png` (1024×500) |
| Phone screenshots | `docs/play-store/screenshots/phone/01…08-*.png` (8 images, 1080×1920, 9:16), upload in numbered order |
| Tablet screenshots | `docs/play-store/screenshots/tablet/01…04-*.png` (4 images, 2560×1600, 16:10), upload to both **7-inch** and **10-inch** tablet slots |
| Category | Books & Reference |
| Contact email | aht.apps@alhadaftech.com |
| Privacy policy URL | Host `docs/legal/privacy-policy.html` publicly (e.g. GitHub Pages) and paste the URL |

### Full description (1700/4000)

```
BookFlow is a fast, private PDF reader built for studying and serious reading. Open any PDF, mark it up, and pick up exactly where you left off — everything stays on your device.

READ YOUR WAY
• Smooth vertical scrolling, horizontal swipe, single page, or Book mode with a realistic page curl
• Tap the page edges to turn pages, or tap the middle for distraction-free full-screen reading
• Pinch and double-tap zoom, page thumbnails, table of contents, and Go to page
• Light, Dark, Sepia and Mint page themes, plus a dark mode for the whole app

HIGHLIGHT AND ANNOTATE
• Highlight, underline or strike through text — drag across a passage and it snaps to whole words
• Pen and highlighter with colors and thickness presets, stylus-only mode for tablets with a pen
• Shapes: lines, arrows, rectangles and ellipses
• Sticky notes anywhere on the page, notes on highlighted text, and an eraser
• Undo and redo while annotating
• Export an annotated copy of your PDF to share

FIND ANYTHING
• Full-text search inside a book with every match listed in context
• Search across your library's titles, highlights and notes
• Bookmarks and reading history

STAY ORGANIZED
• Collections, favorites and a library that remembers your progress in every book
• Open PDFs straight from your file manager, email or downloads with "Open with BookFlow"

BUILD A READING HABIT
• Set a daily reading goal and watch your ring fill as you read
• Track your streak and minutes read over the last week

PRIVATE BY DESIGN
• No account, no ads, no analytics, no tracking
• BookFlow has no internet permission — your documents and notes never leave your device
• Removing a book from BookFlow never deletes your original file
```

### Release notes (What's new) for 1.0.0

```
First release of BookFlow: read, highlight and annotate PDFs privately on your device.
```

## 4. App content (Policy › App content)

| Declaration | Answer |
|---|---|
| Privacy policy | URL of hosted `privacy-policy.html` |
| Ads | **No**, the app does not contain ads |
| App access | **All functionality is available without special access** (no login) |
| Content rating | Category **Reference, News, or Educational**; answer **No** to all violence, sexual content, profanity, drugs, gambling questions; **No** user-to-user communication or sharing of user content online; **No** location sharing. Expected rating: Everyone / PEGI 3 |
| Target audience | **13 and over** (or 18+). Not designed for children, so it stays out of the Families program requirements |
| News app | No |
| Health apps / Financial features / Government | No |
| Data safety | See section 5 |

## 5. Data safety form

BookFlow declares **no internet permission**, has no SDKs that send data, no accounts and no ads, so it cannot transmit data on its own.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) |
| Do you provide a way for users to request that their data be deleted? | Not applicable; all data is on-device and removed by deleting books or uninstalling |

Notes for reviewers / your own records:
- **Crash reports** are written to app-private storage and only leave the device if the user taps *Settings › Report a problem › Share report* and chooses an app (e.g. email) to send it. That is a user-initiated share through another app, not collection by BookFlow. If you prefer the most conservative reading, declare **App info and performance › Crash logs** as *collected, optional, not shared, for App functionality/Analytics*.
- **Android Auto Backup** (library database and settings, not PDFs) is handled by Android under the user's Google account backup settings; it is disclosed in the privacy policy.
- **Play Console Android vitals** crash statistics come from Google's own platform diagnostics, not from an SDK in the app.

## 6. Publishing steps

1. Play Console › Create app: name *BookFlow*, default language English (US), App, Free.
2. Complete sections 3–5 above.
3. Testing › **Internal testing**: create a release, upload `app-release.aab`, add testers, roll out. Install from the opt-in link and run the smoke test in section 1.
4. Review the **Pre-launch report** (automatic device tests, accessibility and security warnings) and fix anything flagged.
5. New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production access is granted.
6. Production › Create release with the same bundle (or a newer version code), staged rollout (e.g. 20%), then 100% after checking Android vitals.

## 7. What's already in the build

- R8 code + resource shrinking (APK ~10 MB vs 80 MB debug); obfuscation mapping is embedded in the bundle so Play de-obfuscates crash reports.
- Release signing from `keystore.properties`; no `INTERNET` permission; narrowed FileProvider paths.
- Auto Backup / device-transfer rules (database + settings; PDFs and thumbnails excluded).
- Room schema exported (`app/schemas/`), destructive migration only on downgrade.
- Branded system splash (Android 12+ API), adaptive icon with Android 13 themed (monochrome) layer.
- Edge-to-edge, predictive back, light/dark/system app theme, navigation rail on screens ≥600dp.
- In-app Privacy policy, Terms of use, generated Open-source licenses, Report a problem (local crash log).
- TalkBack labels and 48dp targets for color pickers, tab semantics on navigation, heading semantics.

## 8. Open items

- [ ] **App baseline profile**: the `:baselineprofile` module is set up, but generation on the Android 16 emulator failed with Macrobenchmark 1.3.4 ("Unable to confirm activity launch completion"). Next step: upgrade `baselineprofile` to 1.4.x in `gradle/libs.versions.toml`, then `./gradlew :app:generateReleaseBaselineProfile` with a device connected (note: the run uninstalls the app afterwards). Library profiles (Compose etc.) are already included in the bundle.
- [ ] Host `docs/legal/privacy-policy.html` and `terms-of-use.html` and add the URL in Play Console.
