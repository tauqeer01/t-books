import SwiftUI

public struct HomeScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @Binding var selectedTab: Int
    @Binding var activeBook: Book?
    @State private var showingDocPicker = false
    @State private var showingSettings = false
    @State private var showingCollections = false
    @State private var showingReadingGoal = false

    public init(selectedTab: Binding<Int>, activeBook: Binding<Book?>) {
        self._selectedTab = selectedTab
        self._activeBook = activeBook
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    // Header Bar
                    HStack(alignment: .center) {
                        HStack(spacing: 10) {
                            BookFlowEmblem(size: 38)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("AHT Books")
                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                    .foregroundColor(Theme.appTextPrimary)
                                Text("PDF Reader & Study")
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.appTextSecondary)
                            }
                        }
                        
                        Spacer()
                        
                        // Reading Goal Ring & Actions
                        HStack(spacing: 10) {
                            Button(action: { showingReadingGoal = true }) {
                                ReadingGoalRing(
                                    progress: storage.readingStats.goalProgress,
                                    minutes: storage.readingStats.minutesReadToday,
                                    goalMinutes: storage.readingStats.dailyGoalMinutes,
                                    size: 40,
                                    lineWidth: 4
                                )
                                .contentShape(Circle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("home.readingGoal")
                            .accessibilityHint("Shows today's reading and your daily goal")
                            
                            Button(action: { showingDocPicker = true }) {
                                Image(systemName: "plus.circle.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(Theme.brandPurple)
                            }
                            
                            Button(action: { showingSettings = true }) {
                                Image(systemName: "gearshape")
                                    .font(.system(size: 20, weight: .medium))
                                    .foregroundColor(Theme.appTextPrimary)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
                    
                    // Continue Reading Hero Card
                    if let recent = storage.books.first {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("CONTINUE READING")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Theme.appTextSecondary)
                                .tracking(1)
                            
                            Button(action: { activeBook = recent }) {
                                HStack(spacing: 16) {
                                    if let thumb = recent.thumbnailPath, let img = UIImage(contentsOfFile: thumb) {
                                        Image(uiImage: img)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 68, height: 96)
                                            .cornerRadius(8)
                                    } else {
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(Color(hex: recent.coverColorHex))
                                            .frame(width: 68, height: 96)
                                            .overlay(
                                                Image(systemName: "doc.text.fill")
                                                    .foregroundColor(.white.opacity(0.8))
                                            )
                                    }
                                    
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(recent.title)
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(Theme.appTextPrimary)
                                            .lineLimit(2)
                                        
                                        Text(recent.author)
                                            .font(.system(size: 13))
                                            .foregroundColor(Theme.appTextSecondary)
                                        
                                        Spacer()
                                        
                                        HStack {
                                            Text("Page \(recent.currentPage + 1) of \(max(1, recent.pageCount))")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundColor(Theme.brandPurple)
                                            
                                            Spacer()
                                            
                                            Text("\(Int(recent.readingProgress * 100))%")
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(Theme.appTextSecondary)
                                        }
                                        
                                        // Progress bar
                                        GeometryReader { geo in
                                            ZStack(alignment: .leading) {
                                                Capsule().fill(Theme.brandPurpleSoft).frame(height: 5)
                                                Capsule().fill(Theme.brandPurple)
                                                    .frame(width: max(4, geo.size.width * CGFloat(recent.readingProgress)), height: 5)
                                            }
                                        }
                                        .frame(height: 5)
                                    }
                                }
                                .padding(16)
                                .background(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(Theme.appSurface)
                                        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 3)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                            .accessibilityIdentifier("home.continueReading")
                        }
                        .padding(.horizontal, 20)
                    }
                    
                    // Collections Section
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("Collections")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Button("See All") {
                                showingCollections = true
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                        }
                        .padding(.horizontal, 20)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(storage.collections) { col in
                                    PastelCollectionCard(collection: col) {
                                        showingCollections = true
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    
                    // Library Shelf
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("My Library")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Button("View All (\(storage.books.count))") {
                                selectedTab = 1 // Switch to Library tab
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                        }
                        .padding(.horizontal, 20)
                        
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 16) {
                            ForEach(storage.books.prefix(4)) { book in
                                BookCoverCard(book: book, onOpen: {
                                    activeBook = book
                                })
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    
                    Spacer(minLength: 30)
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
            .sheet(isPresented: $showingDocPicker) {
                DocumentPickerView { url in
                    if let newBook = storage.importPDF(from: url) {
                        activeBook = newBook
                    }
                }
            }
            .sheet(isPresented: $showingCollections) {
                NavigationStack {
                    CollectionsScreen(activeBook: $activeBook)
                }
            }
            .sheet(isPresented: $showingSettings) {
                SettingsScreen()
            }
            .sheet(isPresented: $showingReadingGoal) {
                ReadingGoalSheet(stats: storage.readingStats) { minutes in
                    storage.setDailyGoal(minutes: minutes)
                }
                .presentationDetents([.height(570), .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
            }
        }
    }
}
