import SwiftUI

public struct LibraryScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @Binding var activeBook: Book?
    @State private var searchQuery: String = ""
    private var isGridView: Bool { storage.preferences.libraryGrid }
    @State private var selectedFilter: String = "All"
    @State private var showingDocPicker: Bool = false
    @State private var bookForDetails: Book? = nil
    @State private var bookForCollections: Book? = nil
    @State private var bookToDelete: Book? = nil
    
    private let filters = ["All", "Favorites", "Recent", "Unread", "Completed"]
    
    public init(activeBook: Binding<Book?>) {
        self._activeBook = activeBook
    }
    
    private var filteredBooks: [Book] {
        var list = storage.books
        if selectedFilter == "Favorites" {
            list = list.filter { $0.isFavorite }
        } else if selectedFilter == "Recent" {
            // Opened in the last 14 days
            let cutoff = Int64(Date().addingTimeInterval(-14 * 86_400).timeIntervalSince1970 * 1000)
            list = list.filter { $0.lastReadTimestamp >= cutoff }
        } else if selectedFilter == "Unread" {
            list = list.filter { $0.readingProgress < 0.05 }
        } else if selectedFilter == "Completed" {
            list = list.filter { $0.readingProgress >= 0.95 }
        }
        
        if !searchQuery.isEmpty {
            let q = searchQuery.lowercased()
            list = list.filter { $0.title.lowercased().contains(q) || $0.author.lowercased().contains(q) }
        }
        
        switch storage.preferences.librarySort {
        case .RECENTLY_OPENED:
            return list.sorted { $0.lastReadTimestamp > $1.lastReadTimestamp }
        case .TITLE:
            return list.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .AUTHOR:
            return list.sorted { $0.author.localizedCaseInsensitiveCompare($1.author) == .orderedAscending }
        case .PROGRESS:
            return list.sorted { $0.readingProgress > $1.readingProgress }
        case .DATE_ADDED:
            return list.sorted { $0.addedTimestamp > $1.addedTimestamp }
        }
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Search Bar
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Theme.appTextSecondary)
                    TextField("Search library by title or author...", text: $searchQuery)
                        .font(.system(size: 14))
                    if !searchQuery.isEmpty {
                        Button(action: { searchQuery = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Theme.appSurface)
                        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 1)
                )
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                // Filter Chips & View Mode
                HStack {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(filters, id: \.self) { f in
                                Button(action: { selectedFilter = f }) {
                                    Text(f)
                                        .font(.system(size: 13, weight: selectedFilter == f ? .bold : .medium))
                                        .foregroundColor(selectedFilter == f ? .white : Theme.appTextPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
                                        .background(
                                            Capsule()
                                                .fill(selectedFilter == f ? Theme.brandPurple : Theme.appSurface)
                                        )
                                }
                            }
                        }
                    }
                    
                    Spacer()
                    
                    // Toggle Grid/List
                    Button(action: {
                        storage.preferences.libraryGrid.toggle()
                        storage.savePreferences()
                    }) {
                        Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                            .padding(8)
                            .background(Circle().fill(Theme.appSurface))
                    }
                    .accessibilityLabel(isGridView ? "Show as list" : "Show as grid")
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                
                // Books Content
                if filteredBooks.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "books.vertical")
                            .font(.system(size: 48))
                            .foregroundColor(Theme.appTextSecondary.opacity(0.6))
                        Text(searchQuery.isEmpty ? "No books found" : "No matches for '\(searchQuery)'")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                        Text("Tap + to import PDF documents from Files")
                            .font(.system(size: 13))
                            .foregroundColor(Theme.appTextSecondary)
                        Spacer()
                    }
                } else if isGridView {
                    ScrollView(.vertical, showsIndicators: false) {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 18) {
                            ForEach(filteredBooks) { book in
                                BookCoverCard(book: book, onOpen: {
                                    activeBook = book
                                })
                                .contextMenu {
                                    Button {
                                        activeBook = book
                                    } label: {
                                        Label("Read", systemImage: "book.fill")
                                    }
                                    Button {
                                        bookForDetails = book
                                    } label: {
                                        Label("Book Details", systemImage: "info.circle")
                                    }
                                    Button {
                                        storage.toggleFavorite(bookId: book.id)
                                    } label: {
                                        Label(book.isFavorite ? "Remove from Favorites" : "Add to Favorites", systemImage: "star")
                                    }
                                    Button {
                                        bookForCollections = book
                                    } label: {
                                        Label("Collections…", systemImage: "square.stack.3d.up")
                                    }
                                    Divider()
                                    Button(role: .destructive) {
                                        bookToDelete = book
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 30)
                    }
                } else {
                    List {
                        ForEach(filteredBooks) { book in
                            Button(action: { activeBook = book }) {
                                HStack(spacing: 14) {
                                    if let thumb = book.thumbnailPath, let img = UIImage(contentsOfFile: thumb) {
                                        Image(uiImage: img)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 44, height: 62)
                                            .cornerRadius(6)
                                    } else {
                                        RoundedRectangle(cornerRadius: 6)
                                            .fill(Color(hex: book.coverColorHex))
                                            .frame(width: 44, height: 62)
                                    }
                                    
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(book.title)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(Theme.appTextPrimary)
                                            .lineLimit(1)
                                        Text(book.author)
                                            .font(.system(size: 12))
                                            .foregroundColor(Theme.appTextSecondary)
                                        Text("\(Int(book.readingProgress * 100))% • Page \(book.currentPage + 1) of \(max(1, book.pageCount))")
                                            .font(.system(size: 11, weight: .medium))
                                            .foregroundColor(Theme.brandPurple)
                                    }
                                    
                                    Spacer()
                                    
                                    if book.isFavorite {
                                        Image(systemName: "star.fill")
                                            .foregroundColor(.yellow)
                                            .font(.system(size: 14))
                                    }
                                }
                            }
                        }
                        .onDelete { indexSet in
                            if let idx = indexSet.first { bookToDelete = filteredBooks[idx] }
                        }
                    }
                    .listStyle(PlainListStyle())
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("Library")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Picker("Sort by", selection: Binding(
                            get: { storage.preferences.librarySort },
                            set: { storage.preferences.librarySort = $0; storage.savePreferences() }
                        )) {
                            Text("Recently Opened").tag(BookSortOrder.RECENTLY_OPENED)
                            Text("Title").tag(BookSortOrder.TITLE)
                            Text("Author").tag(BookSortOrder.AUTHOR)
                            Text("Progress").tag(BookSortOrder.PROGRESS)
                            Text("Date Added").tag(BookSortOrder.DATE_ADDED)
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                    }
                    .accessibilityLabel("Sort books")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingDocPicker = true }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.brandPurple)
                    }
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
            .sheet(item: $bookForCollections) { b in
                BookCollectionsSheet(bookId: b.id)
            }
            .alert("Remove from Library?", isPresented: Binding(
                get: { bookToDelete != nil },
                set: { if !$0 { bookToDelete = nil } }
            )) {
                Button("Cancel", role: .cancel) {}
                Button("Remove", role: .destructive) {
                    if let book = bookToDelete { storage.deleteBook(bookId: book.id) }
                }
            } message: {
                Text("“\(bookToDelete?.title ?? "")” and its highlights, notes and bookmarks will be deleted from AHT Books.")
            }
            .sheet(item: $bookForDetails) { b in
                BookDetailsScreen(book: b, onOpenReader: {
                    activeBook = b
                    bookForDetails = nil
                })
            }
        }
    }
}
