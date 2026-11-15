import SwiftUI

public struct SettingsScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var showingPrivacyPolicy: Bool = false
    @State private var showingTerms: Bool = false
    @State private var showingLicenses: Bool = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            Form {
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
                        Text("Book Page Curl").tag(PageScrollMode.BOOK_CURL)
                    }
                    
                    Toggle("Keep Screen Awake While Reading", isOn: $storage.preferences.keepScreenOn)
                    Toggle("High-Resolution Vector Rendering", isOn: $storage.preferences.highResolutionRendering)
                }
                
                // Study & Habits
                Section(header: Text("Study Goals")) {
                    Picker("Daily Reading Goal", selection: $storage.readingStats.dailyGoalMinutes) {
                        Text("15 minutes").tag(15)
                        Text("30 minutes").tag(30)
                        Text("45 minutes").tag(45)
                        Text("60 minutes").tag(60)
                        Text("90 minutes").tag(90)
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
                        Text("\(storage.books.count) books • \(storage.notes.count) notes")
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
                        Text("Version 1.0.0 (Build 1)")
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
            .sheet(isPresented: $showingPrivacyPolicy) {
                LegalDocumentModal(
                    title: "Privacy Policy",
                    url: "https://tauqeer01.github.io/t-books/legal/privacy-policy.html",
                    content: """
                    AHT Books is committed to your privacy.
                    
                    1. Offline and on-device
                    All your library PDFs, annotations, handwritten notebook entries, flashcards, and reading statistics remain strictly on your local device.
                    
                    2. No tracking and no ads
                    The app does not collect personal data, does not include advertisements, and does not sell or share data with third parties.
                    
                    3. File storage
                    Files imported into AHT Books are stored in your device's sandboxed document directory and accessible via the Files app.
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
            .onChange(of: storage.preferences) { _ in
                storage.saveAll()
            }
            .onChange(of: storage.readingStats.dailyGoalMinutes) { _ in
                storage.saveAll()
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
                        Link("View Online Policy at alhadaftech.com", destination: webURL)
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
