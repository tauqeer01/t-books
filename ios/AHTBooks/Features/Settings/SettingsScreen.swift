import SwiftUI

public struct SettingsScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var showingPrivacyPolicy: Bool = false
    @State private var showingTerms: Bool = false
    @State private var showingLicenses: Bool = false
    @AppStorage(AppThemeMode.storageKey) private var themeMode: AppThemeMode = .system
    @Environment(\.dismiss) private var dismiss

    public init() {}

    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "Version \(version) (Build \(build))"
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Appearance")) {
                    Picker("App Theme", selection: $themeMode) {
                        ForEach(AppThemeMode.allCases) { Text($0.displayName).tag($0) }
                    }
                }

                // Reading Experience
                Section(header: Text("Reading Experience")) {
                    Picker("Default Theme", selection: $storage.preferences.readerTheme) {
                        Text("Light").tag(ReaderTheme.LIGHT)
                        Text("Dark (Night)").tag(ReaderTheme.DARK)
                        Text("Sepia").tag(ReaderTheme.SEPIA)
                        Text("Mint").tag(ReaderTheme.MINT)
                    }
                    
                    Picker("Default Scroll Mode", selection: $storage.preferences.scrollMode) {
                        Text("Continuous Vertical").tag(PageScrollMode.CONTINUOUS_VERTICAL)
                        Text("Horizontal Paging").tag(PageScrollMode.HORIZONTAL_PAGING)
                        Text("Single Page").tag(PageScrollMode.SINGLE_PAGE)
                    }
                    
                    Toggle("Keep Screen Awake While Reading", isOn: $storage.preferences.keepScreenOn)
                }
                
                // Study & Habits
                Section(header: Text("Study Goals")) {
                    Stepper(value: $storage.readingStats.dailyGoalMinutes, in: ReadingStats.goalRangeMinutes, step: 5) {
                        HStack {
                            Text("Daily Reading Goal")
                            Spacer()
                            Text("\(storage.readingStats.dailyGoalMinutes) min")
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                    
                    HStack {
                        Text("Today's Reading")
                        Spacer()
                        Text("\(storage.readingStats.minutesReadToday) min / \(storage.readingStats.dailyGoalMinutes) min")
                            .foregroundColor(Theme.brandPurple)
                            .font(.system(size: 14, weight: .bold))
                    }
                    
                    HStack {
                        Text("Current Streak")
                        Spacer()
                        Text("\(storage.readingStats.currentStreakDays) days")
                            .foregroundColor(Theme.appTextSecondary)
                    }
                }
                
                // Privacy & Storage
                Section(header: Text("Privacy & Storage")) {
                    HStack {
                        Text("Library Data")
                        Spacer()
                        Text("\(storage.books.count) \(storage.books.count == 1 ? "book" : "books") • \(storage.notes.count) \(storage.notes.count == 1 ? "note" : "notes")")
                            .foregroundColor(Theme.appTextSecondary)
                            .font(.system(size: 13))
                    }
                    
                    Text("AHT Books operates 100% offline. All documents, annotations, handwritten notes, and flashcards remain securely stored on your device.")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.appTextSecondary)
                }
                
                // Legal & Support
                Section(header: Text("Legal & Information")) {
                    Button(action: { showingPrivacyPolicy = true }) {
                        HStack {
                            Text("Privacy Policy")
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                    
                    Button(action: { showingTerms = true }) {
                        HStack {
                            Text("Terms of Use")
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                    
                    Button(action: { showingLicenses = true }) {
                        HStack {
                            Text("Open-Source Licenses")
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                    
                    Link(destination: URL(string: "mailto:aht.apps@alhadaftech.com")!) {
                        HStack {
                            Text("Contact Support")
                                .foregroundColor(Theme.brandPurple)
                            Spacer()
                            Image(systemName: "envelope.fill")
                                .foregroundColor(Theme.brandPurple)
                        }
                    }
                }
                
                // About
                Section {
                    VStack(alignment: .center, spacing: 6) {
                        BookFlowEmblem(size: 44)
                        Text("AHT Books for iOS")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                        Text(versionText)
                            .font(.system(size: 12))
                            .foregroundColor(Theme.appTextSecondary)
                        Text("Published by Al Hadaf Tech (AHT)")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.appTextSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .sheet(isPresented: $showingPrivacyPolicy) {
                LegalDocumentModal(
                    title: "Privacy Policy",
                    // Same text as docs/legal/privacy-policy-ios.md (the hosted copy linked in App Store Connect)
                    url: "https://tauqeer01.github.io/t-books/legal/privacy-policy-ios.html",
                    content: """
                    Summary
                    AHT Books works entirely on your device. It has no accounts, no ads, no analytics and no tracking, and it does not send your documents or data anywhere. The app contains no code that connects to the internet.

                    What AHT Books stores on your device
                    • Copies of the PDFs you import. Your original file is never changed.
                    • Your library details: titles, authors, reading position, collections and favorites.
                    • Your highlights, notes, drawings and bookmarks.
                    • Your notebooks, including handwriting and any photos you add to them.
                    • Your flashcards and their review history.
                    • Your saved signatures and initials, kept in protected storage that is not visible in the Files app.
                    • Reading time per day for your reading goal, and your settings.

                    This data never leaves your device through AHT Books.

                    Files, photos and camera
                    AHT Books only opens files you pick in the Files picker or send to it with “Open in”. It does not scan your storage. Imported PDFs, page thumbnails and note photos are kept in the app’s Documents folder, which you can see in the Files app under On My iPhone › AHT Books.

                    Photos: AHT Books only receives the photos you choose in the photo picker to add to a note.
                    Camera: the camera is only used when you choose to take a photo for a note.

                    Backups
                    If iCloud Backup or a computer backup is turned on, iOS may include AHT Books’ data in your device backup. You control this in your device settings, and Apple’s privacy policy applies to those backups.

                    Crash reports
                    AHT Books has no crash reporting of its own. If you have chosen to share analytics with app developers in iOS Settings, Apple may provide anonymous crash reports to the developer.

                    Sharing
                    AHT Books does not sell or share your data. When you use Share or Export (for example a signed PDF, an annotated copy or a notebook page), the file goes only to the app or person you choose.

                    Deleting your data
                    Delete books, notes, decks and signatures inside the app, or delete AHT Books from your device to remove all of its data.

                    Children
                    AHT Books is a general-audience reading app and does not knowingly collect personal information from anyone.

                    Changes and contact
                    If this policy changes, the new version will be published with an updated effective date. Questions: aht.apps@alhadaftech.com.
                    """
                )
            }
            .sheet(isPresented: $showingTerms) {
                LegalDocumentModal(
                    title: "Terms of Use",
                    url: "https://tauqeer01.github.io/t-books/legal/terms-of-use.html",
                    content: """
                    By using AHT Books, you agree to the following terms:
                    
                    1. License
                    Al Hadaf Tech grants you a personal, non-exclusive license to use AHT Books for personal and educational reading, annotation, and study.
                    
                    2. User Content
                    You retain all ownership rights to documents and notes imported or created within the application.
                    """
                )
            }
            .sheet(isPresented: $showingLicenses) {
                LicensesModal()
            }
            .onChange(of: storage.preferences) { _, _ in
                storage.savePreferences()
            }
            .onChange(of: storage.readingStats.dailyGoalMinutes) { _, minutes in
                storage.setDailyGoal(minutes: minutes)
            }
        }
    }
}

private struct LegalDocumentModal: View {
    let title: String
    let url: String
    let content: String
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(content)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.appTextPrimary)
                    
                    if let webURL = URL(string: url) {
                        Link("Read the full version online", destination: webURL)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                            .padding(.top, 12)
                    }
                }
                .padding(20)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct LicensesModal: View {
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Apple Native Frameworks")) {
                    Text("PDFKit • CoreGraphics • PencilKit • SwiftUI • Foundation • UIKit")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.appTextPrimary)
                }
                Section(header: Text("Application Architecture")) {
                    Text("Designed and built natively in Swift for iOS & iPadOS by Al Hadaf Tech.")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.appTextSecondary)
                }
            }
            .navigationTitle("Licenses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
