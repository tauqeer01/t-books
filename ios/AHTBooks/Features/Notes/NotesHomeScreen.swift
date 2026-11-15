import SwiftUI

public struct NotesHomeScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var activeEditingNote: Note? = nil
    @State private var showingNewNoteSheet: Bool = false
    @State private var newNoteTitle: String = ""
    @State private var newNoteTemplate: PageBackground = .BLANK
    @State private var newNoteTheme: PaperTheme = .white
    @State private var searchQuery: String = ""
    
    public init() {}
    
    private var filteredNotes: [Note] {
        var list = storage.notes
        if !searchQuery.isEmpty {
            let q = searchQuery.lowercased()
            list = list.filter { $0.title.lowercased().contains(q) || $0.tags.contains { $0.lowercased().contains(q) } }
        }
        return list.sorted {
            if $0.pinned != $1.pinned { return $0.pinned }
            return $0.updatedAt > $1.updatedAt
        }
    }
    
    private let columns = [
        GridItem(.adaptive(minimum: 160, maximum: 220), spacing: 20)
    ]
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Search Bar
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Theme.appTextSecondary)
                    TextField("Search notes and tags...", text: $searchQuery)
                        .font(.system(size: 14))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12).fill(Theme.appSurface))
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                if filteredNotes.isEmpty {
                    VStack(spacing: 14) {
                        Spacer()
                        Image(systemName: "note.text")
                            .font(.system(size: 48))
                            .foregroundColor(Theme.brandPurple.opacity(0.6))
                        Text("No notes yet")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                        Text("Create your first A4 notebook with Apple Pencil support, custom templates, and PDF export")
                            .font(.system(size: 13))
                            .foregroundColor(Theme.appTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                        Button(action: { showingNewNoteSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "plus.circle.fill")
                                Text("Create Notebook")
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 12)
                            .background(Capsule().fill(Theme.brandPurple))
                        }
                        .padding(.top, 8)
                        Spacer()
                    }
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 20) {
                            ForEach(filteredNotes) { note in
                                Button(action: { activeEditingNote = note }) {
                                    VStack(alignment: .leading, spacing: 8) {
                                        // Note Thumbnail Card
                                        ZStack(alignment: .topTrailing) {
                                            let firstPage = note.pages.first
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .fill(Color(hex: firstPage?.paperTheme.hexColor ?? "#FFFFFF"))
                                                .aspectRatio(595.0 / 842.0, contentMode: .fit)
                                                .overlay(
                                                    PageTemplateBackground(
                                                        template: firstPage?.background ?? .BLANK,
                                                        paperTheme: firstPage?.paperTheme ?? .white
                                                    )
                                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                                )
                                                .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 3)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 10)
                                                        .stroke(Color.black.opacity(0.08), lineWidth: 1)
                                                )
                                            
                                            if note.pinned {
                                                Image(systemName: "pin.fill")
                                                    .foregroundColor(Theme.brandPurple)
                                                    .font(.system(size: 12))
                                                    .padding(8)
                                                    .background(Circle().fill(Color.white.opacity(0.85)))
                                                    .padding(6)
                                            }
                                        }
                                        
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(note.title)
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(Theme.appTextPrimary)
                                                .lineLimit(1)
                                            
                                            Text("\(note.pages.count) Pages • \(formatDate(note.updatedAt))")
                                                .font(.system(size: 11))
                                                .foregroundColor(Theme.appTextSecondary)
                                        }
                                    }
                                }
                                .buttonStyle(PlainButtonStyle())
                                .contextMenu {
                                    Button {
                                        activeEditingNote = note
                                    } label: {
                                        Label("Open", systemImage: "pencil.tip")
                                    }
                                    Button {
                                        if let idx = storage.notes.firstIndex(where: { $0.id == note.id }) {
                                            storage.notes[idx].pinned.toggle()
                                            storage.saveAll()
                                        }
                                    } label: {
                                        Label(note.pinned ? "Unpin" : "Pin Note", systemImage: "pin")
                                    }
                                    Divider()
                                    Button(role: .destructive) {
                                        storage.notes.removeAll { $0.id == note.id }
                                        storage.saveAll()
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .padding(20)
                    }
                }
            }
            .navigationTitle("Notes & Study")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingNewNoteSheet = true }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.brandPurple)
                    }
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
            .sheet(item: $activeEditingNote) { note in
                let binding = Binding<Note>(
                    get: { note },
                    set: { updated in
                        if let idx = storage.notes.firstIndex(where: { $0.id == updated.id }) {
                            storage.notes[idx] = updated
                            storage.saveAll()
                        }
                    }
                )
                NoteEditorView(note: binding)
            }
            .sheet(isPresented: $showingNewNoteSheet) {
                NavigationStack {
                    Form {
                        Section("Notebook Details") {
                            TextField("Notebook Title", text: $newNoteTitle)
                        }
                        Section("Paper Theme") {
                            Picker("Theme", selection: $newNoteTheme) {
                                ForEach(PaperTheme.allCases, id: \.self) { theme in
                                    Text(theme.rawValue).tag(theme)
                                }
                            }
                            .pickerStyle(.segmented)
                        }
                        Section("Page Template") {
                            Picker("Template", selection: $newNoteTemplate) {
                                ForEach(PageBackground.allCases, id: \.self) { bg in
                                    Text(bg.title).tag(bg)
                                }
                            }
                        }
                    }
                    .navigationTitle("New Notebook")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Cancel") { showingNewNoteSheet = false }
                        }
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Create") {
                                let title = newNoteTitle.trimmingCharacters(in: .whitespaces).isEmpty ? "Untitled Note" : newNoteTitle
                                let page = NotePage(
                                    noteId: UUID().uuidString,
                                    pageIndex: 0,
                                    background: newNoteTemplate,
                                    paperTheme: newNoteTheme
                                )
                                let newNote = Note(title: title, pages: [page])
                                storage.notes.append(newNote)
                                storage.saveAll()
                                newNoteTitle = ""
                                showingNewNoteSheet = false
                                activeEditingNote = newNote
                            }
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Theme.brandPurple)
                        }
                    }
                }
            }
        }
    }
    
    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
        let df = DateFormatter()
        df.dateStyle = .short
        return df.string(from: date)
    }
}
