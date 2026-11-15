import SwiftUI

public struct BookDetailsScreen: View {
    public let book: Book
    public var onOpenReader: () -> Void
    @ObservedObject var storage = StorageManager.shared
    @State private var selectedTab: Int = 0
    @Environment(\.dismiss) private var dismiss
    
    public init(book: Book, onOpenReader: @escaping () -> Void) {
        self.book = book
        self.onOpenReader = onOpenReader
    }
    
    private var bookAnnotations: [BookAnnotation] {
        storage.annotations.filter { $0.bookId == book.id }
    }
    
    private var bookBookmarks: [Bookmark] {
        storage.bookmarks.filter { $0.bookId == book.id }
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Segmented Picker
                Picker("Tabs", selection: $selectedTab) {
                    Text("Overview").tag(0)
                    Text("Bookmarks (\(bookBookmarks.count))").tag(1)
                    Text("Highlights (\(bookAnnotations.filter { $0.type == .HIGHLIGHT }.count))").tag(2)
                    Text("Notes (\(bookAnnotations.filter { !$0.noteContent.isEmpty }.count))").tag(3)
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                
                // Tab Content
                TabView(selection: $selectedTab) {
                    overviewView.tag(0)
                    bookmarksView.tag(1)
                    highlightsView.tag(2)
                    notesView.tag(3)
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            }
            .navigationTitle("Document Hub")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
        }
    }
    
    private var overviewView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Book Banner
                HStack(spacing: 16) {
                    if let thumb = book.thumbnailPath, let img = UIImage(contentsOfFile: thumb) {
                        Image(uiImage: img)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 80, height: 114)
                            .cornerRadius(10)
                    } else {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color(hex: book.coverColorHex))
                            .frame(width: 80, height: 114)
                    }
                    
                    VStack(alignment: .leading, spacing: 6) {
                        Text(book.title)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                            .lineLimit(2)
                        
                        Text(book.author)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.appTextSecondary)
                        
                        Text("\(book.pageCount) Pages • \(formatBytes(book.fileSizeBytes))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Theme.brandPurple)
                        
                        Button(action: onOpenReader) {
                            Text("Open in Reader")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Capsule().fill(Theme.brandPurple))
                        }
                        .padding(.top, 4)
                    }
                    Spacer()
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(Theme.appSurface))
                .padding(.horizontal, 16)
                
                // Stats Grid
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    StatTile(title: "Highlights", value: "\(bookAnnotations.filter { $0.type == .HIGHLIGHT }.count)", icon: "highlighter", color: Theme.brandPurple)
                    StatTile(title: "Bookmarks", value: "\(bookBookmarks.count)", icon: "bookmark.fill", color: Theme.highlightBlue)
                    StatTile(title: "Notes Added", value: "\(bookAnnotations.filter { !$0.noteContent.isEmpty }.count)", icon: "note.text", color: Theme.highlightGreen)
                    StatTile(title: "Progress", value: "\(Int(book.readingProgress * 100))%", icon: "chart.bar.fill", color: Theme.highlightPink)
                }
                .padding(.horizontal, 16)
                
                // Document Metadata Table
                VStack(spacing: 12) {
                    metaRow(label: "Current Page", value: "\(book.currentPage + 1) of \(max(1, book.pageCount))")
                    metaRow(label: "File Size", value: formatBytes(book.fileSizeBytes))
                    metaRow(label: "Date Added", value: formatDate(book.addedTimestamp))
                    metaRow(label: "Last Read", value: formatDate(book.lastReadTimestamp))
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(Theme.appSurface))
                .padding(.horizontal, 16)
            }
            .padding(.vertical, 16)
        }
    }
    
    private var bookmarksView: some View {
        List {
            if bookBookmarks.isEmpty {
                Text("No bookmarks added yet. Tap the bookmark icon while reading.")
                    .foregroundColor(Theme.appTextSecondary)
            } else {
                ForEach(bookBookmarks) { bm in
                    HStack {
                        Image(systemName: "bookmark.fill")
                            .foregroundColor(Theme.brandPurple)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(bm.label.isEmpty ? "Page \(bm.pageIndex + 1)" : bm.label)
                                .font(.system(size: 15, weight: .semibold))
                            Text("Page \(bm.pageIndex + 1)")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                        Spacer()
                    }
                }
                .onDelete { indexSet in
                    for idx in indexSet {
                        let id = bookBookmarks[idx].id
                        storage.bookmarks.removeAll { $0.id == id }
                    }
                    storage.saveAll()
                }
            }
        }
        .listStyle(PlainListStyle())
    }
    
    private var highlightsView: some View {
        List {
            let list = bookAnnotations.filter { $0.type == .HIGHLIGHT }
            if list.isEmpty {
                Text("No highlights found for this document.")
                    .foregroundColor(Theme.appTextSecondary)
            } else {
                ForEach(list) { ann in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Circle()
                                .fill(Color(hex: ann.colorHex))
                                .frame(width: 10, height: 10)
                            Text("Page \(ann.pageIndex + 1)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                            Spacer()
                        }
                        Text("\"\(ann.selectedText)\"")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.appTextPrimary)
                    }
                    .padding(.vertical, 4)
                }
                .onDelete { indexSet in
                    for idx in indexSet {
                        let id = list[idx].id
                        storage.annotations.removeAll { $0.id == id }
                    }
                    storage.saveAll()
                }
            }
        }
        .listStyle(PlainListStyle())
    }
    
    private var notesView: some View {
        List {
            let list = bookAnnotations.filter { !$0.noteContent.isEmpty }
            if list.isEmpty {
                Text("No notes added yet.")
                    .foregroundColor(Theme.appTextSecondary)
            } else {
                ForEach(list) { ann in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: "note.text")
                                .foregroundColor(Theme.brandPurple)
                            Text("Page \(ann.pageIndex + 1)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                            Spacer()
                        }
                        if !ann.selectedText.isEmpty {
                            Text("Quote: \"\(ann.selectedText)\"")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                        Text(ann.noteContent)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.appTextPrimary)
                    }
                    .padding(.vertical, 4)
                }
                .onDelete { indexSet in
                    for idx in indexSet {
                        let id = list[idx].id
                        storage.annotations.removeAll { $0.id == id }
                    }
                    storage.saveAll()
                }
            }
        }
        .listStyle(PlainListStyle())
    }
    
    private func metaRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(Theme.appTextSecondary)
            Spacer()
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.appTextPrimary)
        }
    }
    
    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useMB, .useKB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
    
    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
        let df = DateFormatter()
        df.dateStyle = .medium
        return df.string(from: date)
    }
}

private struct StatTile: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(color.opacity(0.15)).frame(width: 36, height: 36)
                Image(systemName: icon).foregroundColor(color).font(.system(size: 16, weight: .bold))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Theme.appTextPrimary)
                Text(title)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.appTextSecondary)
            }
            Spacer()
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.appSurface))
    }
}
