import SwiftUI
import PencilKit

/// Notes library: folders, tags, search across titles, text and quotes, sorting, pinning,
/// grid or list, and live page thumbnails (parity with the Android notes home).
public struct NotesHomeScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var openNote: Note?
    @State private var showingNewNote = false
    @State private var newNoteTitle = ""
    @State private var newNoteTemplate: PageBackground = .RULED
    @State private var newNoteTheme: PaperTheme = .white
    @State private var searchQuery = ""
    @State private var folderId: String?
    @State private var selectedTag: String?
    @AppStorage("notes.sort") private var sortRaw = NoteSort.updated.rawValue
    @AppStorage("notes.grid") private var grid = true

    // Note and folder actions
    @State private var renaming: Note?
    @State private var renameText = ""
    @State private var tagging: Note?
    @State private var tagsText = ""
    @State private var deleting: Note?
    @State private var showingNewFolder = false
    @State private var folderName = ""
    @State private var renamingFolder: NoteFolder?
    @State private var deletingFolder: NoteFolder?

    public init() {}

    enum NoteSort: String, CaseIterable, Identifiable {
        case updated, created, title
        var id: String { rawValue }
        var title: String {
            switch self {
            case .updated: return "Last edited"
            case .created: return "Date created"
            case .title: return "Title"
            }
        }
    }

    private var sort: NoteSort { NoteSort(rawValue: sortRaw) ?? .updated }

    private var subfolders: [NoteFolder] {
        storage.noteFolders.filter { $0.parentId == folderId }.sorted { ($0.sortOrder, $0.name) < ($1.sortOrder, $1.name) }
    }

    private var breadcrumb: [NoteFolder] {
        var chain: [NoteFolder] = []
        var current = folderId
        while let id = current, let folder = storage.noteFolders.first(where: { $0.id == id }) {
            chain.insert(folder, at: 0)
            current = folder.parentId
        }
        return chain
    }

    private var allTags: [String] {
        Array(Set(storage.notes.flatMap(\.tags))).sorted()
    }

    private var visibleNotes: [Note] {
        let q = searchQuery.trimmingCharacters(in: .whitespaces).lowercased()
        var list = storage.notes
        // Searching looks in every folder; browsing shows the open folder
        if q.isEmpty { list = list.filter { $0.folderId == folderId } }
        if let tag = selectedTag { list = list.filter { $0.tags.contains(tag) } }
        if !q.isEmpty {
            list = list.filter { note in
                note.title.lowercased().contains(q)
                    || note.tags.contains { $0.lowercased().contains(q) }
                    || note.pages.contains { page in
                        page.elements.contains { ($0.content + " " + ($0.quoteText ?? "")).lowercased().contains(q) }
                    }
            }
        }
        return list.sorted { a, b in
            if a.pinned != b.pinned { return a.pinned }
            switch sort {
            case .updated: return a.updatedAt > b.updatedAt
            case .created: return a.createdAt > b.createdAt
            case .title: return a.title.localizedCaseInsensitiveCompare(b.title) == .orderedAscending
            }
        }
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    searchField
                    if !breadcrumb.isEmpty { breadcrumbBar }
                    folderChips
                    if !allTags.isEmpty { tagChips }
                    if visibleNotes.isEmpty {
                        emptyState
                    } else if grid {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150, maximum: 220), spacing: 18)], spacing: 20) {
                            ForEach(visibleNotes) { note in noteButton(note) }
                        }
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(visibleNotes) { note in noteButton(note) }
                        }
                    }
                }
                .padding(20)
            }
            .background(Theme.appBackground.ignoresSafeArea())
            .navigationTitle("Notes & Study")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Menu {
                        Picker("Sort", selection: $sortRaw) {
                            ForEach(NoteSort.allCases) { Text($0.title).tag($0.rawValue) }
                        }
                        Button { grid.toggle() } label: {
                            Label(grid ? "Show as list" : "Show as grid", systemImage: grid ? "list.bullet" : "square.grid.2x2")
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down.circle")
                    }
                    .accessibilityIdentifier("notesHome.sort")
                    Button { showingNewNote = true } label: {
                        Image(systemName: "plus").font(.system(size: 16, weight: .bold))
                    }
                    .accessibilityLabel("New note")
                    .accessibilityIdentifier("notesHome.new")
                }
            }
            .tint(Theme.brandPurple)
            .fullScreenCover(item: $openNote) { note in
                NoteEditorView(note: Binding(
                    get: { storage.notes.first { $0.id == note.id } ?? note },
                    set: { updated in
                        if let i = storage.notes.firstIndex(where: { $0.id == updated.id }) { storage.notes[i] = updated }
                    }
                ))
            }
            .sheet(isPresented: $showingNewNote) { newNoteSheet }
            .alert("Rename note", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("Title", text: $renameText)
                Button("Cancel", role: .cancel) {}
                Button("Rename") { updateNote(renaming) { $0.title = renameText.isEmpty ? "Untitled Note" : renameText } }
            }
            .alert("Tags", isPresented: Binding(get: { tagging != nil }, set: { if !$0 { tagging = nil } })) {
                TextField("exam, chapter 1", text: $tagsText)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    let tags = tagsText.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "") }
                    updateNote(tagging) { $0.tags = Array(Set(tags.filter { !$0.isEmpty })).sorted() }
                }
            } message: { Text("Separate tags with commas") }
            .alert("Delete note?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) { if let n = deleting { deleteNote(n) } }
            } message: { Text("“\(deleting?.title ?? "")” and its pages will be deleted.") }
            .alert("New folder", isPresented: $showingNewFolder) {
                TextField("Folder name", text: $folderName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    let name = folderName.trimmingCharacters(in: .whitespaces)
                    if !name.isEmpty {
                        storage.noteFolders.append(NoteFolder(name: name, colorHex: noteFolderColors[storage.noteFolders.count % noteFolderColors.count], parentId: folderId))
                        storage.saveNotes()
                    }
                    folderName = ""
                }
            }
            .alert("Rename folder", isPresented: Binding(get: { renamingFolder != nil }, set: { if !$0 { renamingFolder = nil } })) {
                TextField("Folder name", text: $folderName)
                Button("Cancel", role: .cancel) {}
                Button("Rename") {
                    if let f = renamingFolder, let i = storage.noteFolders.firstIndex(where: { $0.id == f.id }), !folderName.isEmpty {
                        storage.noteFolders[i].name = folderName
                        storage.saveNotes()
                    }
                }
            }
            .alert("Delete folder?", isPresented: Binding(get: { deletingFolder != nil }, set: { if !$0 { deletingFolder = nil } })) {
                Button("Cancel", role: .cancel) {}
                Button("Delete folder", role: .destructive) { if let f = deletingFolder { deleteFolder(f) } }
            } message: { Text("Its notes and folders move up one level.") }
        }
    }

    // MARK: - Header pieces

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundColor(Theme.appTextSecondary)
            TextField("Search titles, text, tags and quotes", text: $searchQuery)
                .font(.system(size: 14))
                .accessibilityIdentifier("notesHome.search")
            if !searchQuery.isEmpty {
                Button { searchQuery = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(Theme.appTextSecondary) }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.appSurface))
    }

    private var breadcrumbBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                Button("All notes") { folderId = nil }
                ForEach(breadcrumb) { folder in
                    Image(systemName: "chevron.right").font(.system(size: 10)).foregroundColor(Theme.appTextSecondary)
                    Button(folder.name) { folderId = folder.id }
                }
            }
            .font(.system(size: 13, weight: .semibold))
        }
    }

    private var folderChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(subfolders) { folder in
                    Button { folderId = folder.id } label: {
                        Label(folder.name, systemImage: "folder.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Color(hex: folder.colorHex))
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Capsule().fill(Color(hex: folder.colorHex).opacity(0.12)))
                    }
                    .contextMenu {
                        Button { folderName = folder.name; renamingFolder = folder } label: { Label("Rename", systemImage: "pencil") }
                        Button(role: .destructive) { deletingFolder = folder } label: { Label("Delete folder", systemImage: "trash") }
                    }
                    .accessibilityIdentifier("notesHome.folder.\(folder.name)")
                }
                Button { showingNewFolder = true } label: {
                    Label("Folder", systemImage: "folder.badge.plus")
                        .font(.system(size: 13, weight: .semibold))
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Capsule().stroke(Theme.brandPurple.opacity(0.4), style: StrokeStyle(lineWidth: 1, dash: [4])))
                }
                .accessibilityIdentifier("notesHome.newFolder")
            }
        }
    }

    private var tagChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(allTags, id: \.self) { tag in
                    let on = selectedTag == tag
                    Button { selectedTag = on ? nil : tag } label: {
                        Text("#\(tag)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(on ? .white : Theme.brandPurple)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Capsule().fill(on ? Theme.brandPurple : Theme.brandPurpleSoft))
                    }
                    .accessibilityIdentifier("notesHome.tag.\(tag)")
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "note.text").font(.system(size: 44)).foregroundColor(Theme.brandPurple.opacity(0.6))
            Text(searchQuery.isEmpty && selectedTag == nil ? "No notes yet" : "No notes match")
                .font(.system(size: 17, weight: .bold))
            Text("Write by hand, type, add photos, or send text and clips from any book.")
                .font(.system(size: 13)).foregroundColor(Theme.appTextSecondary).multilineTextAlignment(.center)
            if searchQuery.isEmpty {
                Button { showingNewNote = true } label: {
                    Label("New note", systemImage: "plus.circle.fill")
                        .font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 22).padding(.vertical, 12)
                        .background(Capsule().fill(Theme.brandPurple))
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    // MARK: - Note cards

    private func noteButton(_ note: Note) -> some View {
        Button { openNote = note } label: {
            if grid { gridCard(note) } else { listRow(note) }
        }
        .buttonStyle(.plain)
        .contextMenu { noteMenu(note) }
        .accessibilityIdentifier("notesHome.note.\(note.title)")
    }

    private func gridCard(_ note: Note) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .topTrailing) {
                NoteThumbnail(note: note)
                    .aspectRatio(NoteLayout.pageSize.width / NoteLayout.pageSize.height, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.black.opacity(0.08), lineWidth: 1))
                    .shadow(color: .black.opacity(0.08), radius: 6, x: 0, y: 3)
                if note.pinned { pinBadge }
            }
            meta(note)
        }
    }

    private func listRow(_ note: Note) -> some View {
        HStack(spacing: 14) {
            NoteThumbnail(note: note)
                .frame(width: 54, height: 76)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.black.opacity(0.08)))
            meta(note)
            Spacer()
            if note.pinned { Image(systemName: "pin.fill").foregroundColor(Theme.brandPurple).font(.system(size: 12)) }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 12).fill(Theme.appSurface))
    }

    private var pinBadge: some View {
        Image(systemName: "pin.fill")
            .foregroundColor(Theme.brandPurple)
            .font(.system(size: 12))
            .padding(7)
            .background(Circle().fill(Color.white.opacity(0.9)))
            .padding(6)
    }

    private func meta(_ note: Note) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(note.title).font(.system(size: 14, weight: .bold)).foregroundColor(Theme.appTextPrimary).lineLimit(1)
            Text("\(note.pages.count) page\(note.pages.count == 1 ? "" : "s") · \(formatDate(note.updatedAt))")
                .font(.system(size: 11)).foregroundColor(Theme.appTextSecondary)
            if !note.tags.isEmpty {
                Text(note.tags.map { "#\($0)" }.joined(separator: " "))
                    .font(.system(size: 11, weight: .medium)).foregroundColor(Theme.brandPurple).lineLimit(1)
            }
        }
    }

    @ViewBuilder
    private func noteMenu(_ note: Note) -> some View {
        Button { openNote = note } label: { Label("Open", systemImage: "pencil.tip") }
        Button { updateNote(note) { $0.pinned.toggle() } } label: {
            Label(note.pinned ? "Unpin" : "Pin", systemImage: note.pinned ? "pin.slash" : "pin")
        }
        Button { renameText = note.title; renaming = note } label: { Label("Rename", systemImage: "pencil") }
        Button { tagsText = note.tags.joined(separator: ", "); tagging = note } label: { Label("Edit tags", systemImage: "tag") }
        Menu {
            Button("All notes (no folder)") { updateNote(note) { $0.folderId = nil } }
            ForEach(storage.noteFolders) { folder in
                Button(folder.name) { updateNote(note) { $0.folderId = folder.id } }
            }
        } label: { Label("Move to folder", systemImage: "folder") }
        Button { duplicate(note) } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
        Divider()
        Button(role: .destructive) { deleting = note } label: { Label("Delete", systemImage: "trash") }
    }

    // MARK: - New note

    private var newNoteSheet: some View {
        NavigationStack {
            Form {
                Section("Title") {
                    TextField("Untitled Note", text: $newNoteTitle).accessibilityIdentifier("newNote.title")
                }
                Section("Paper") {
                    Picker("Color", selection: $newNoteTheme) {
                        ForEach(PaperTheme.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 12)], spacing: 14) {
                        ForEach(PageBackground.allCases, id: \.self) { bg in
                            Button { newNoteTemplate = bg } label: {
                                VStack(spacing: 6) {
                                    PageTemplateBackground(template: bg, paperTheme: newNoteTheme)
                                        .aspectRatio(0.707, contentMode: .fit)
                                        .clipShape(RoundedRectangle(cornerRadius: 6))
                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(newNoteTemplate == bg ? Theme.brandPurple : Color.black.opacity(0.1),
                                                                                         lineWidth: newNoteTemplate == bg ? 3 : 1))
                                    Text(bg.title).font(.system(size: 11, weight: .medium)).lineLimit(1)
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("newNote.template.\(bg.rawValue)")
                        }
                    }
                    .padding(.vertical, 6)
                } header: {
                    Text("Page")
                } footer: {
                    Text("You can change any page's template later, or mix templates in one note.")
                }
            }
            .navigationTitle("New note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showingNewNote = false } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create", action: createNote).fontWeight(.bold).accessibilityIdentifier("newNote.create")
                }
            }
        }
    }

    // MARK: - Actions

    private func createNote() {
        let title = newNoteTitle.trimmingCharacters(in: .whitespaces)
        var note = Note(folderId: folderId, title: title.isEmpty ? "Untitled Note" : title)
        note.pages = [NotePage(noteId: note.id, pageIndex: 0, background: newNoteTemplate, paperTheme: newNoteTheme)]
        storage.notes.append(note)
        storage.saveNotes()
        newNoteTitle = ""
        showingNewNote = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { openNote = note }
    }

    private func updateNote(_ note: Note?, _ change: (inout Note) -> Void) {
        guard let note, let i = storage.notes.firstIndex(where: { $0.id == note.id }) else { return }
        change(&storage.notes[i])
        storage.notes[i].updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        storage.saveNotes()
    }

    private func duplicate(_ note: Note) {
        var copy = Note(folderId: note.folderId, title: "\(note.title) copy", tags: note.tags)
        copy.pages = note.pages.map { page in
            var p = NotePage(noteId: copy.id, pageIndex: page.pageIndex, background: page.background, paperTheme: page.paperTheme, elements: page.elements)
            p.drawingFileName = NoteInkStore.save(NoteInkStore.load(note: note, page: page), note: copy, page: p)
            return p
        }
        storage.notes.append(copy)
        storage.saveNotes()
    }

    private func deleteNote(_ note: Note) {
        storage.notes.removeAll { $0.id == note.id }
        storage.saveNotes()
    }

    private func deleteFolder(_ folder: NoteFolder) {
        for i in storage.notes.indices where storage.notes[i].folderId == folder.id { storage.notes[i].folderId = folder.parentId }
        for i in storage.noteFolders.indices where storage.noteFolders[i].parentId == folder.id { storage.noteFolders[i].parentId = folder.parentId }
        storage.noteFolders.removeAll { $0.id == folder.id }
        if folderId == folder.id { folderId = folder.parentId }
        storage.saveNotes()
    }

    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
        let df = DateFormatter()
        df.dateStyle = .medium
        return df.string(from: date)
    }
}

private let noteFolderColors = ["#5935FF", "#16A34A", "#EA580C", "#0891B2", "#DB2777", "#CA8A04"]

/// First page of a note with its ink and items, rendered off the main thread and cached.
struct NoteThumbnail: View {
    let note: Note
    @State private var image: UIImage?

    private static let cache = NSCache<NSString, UIImage>()
    private var key: NSString { "\(note.id)-\(note.updatedAt)" as NSString }

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                PageTemplateBackground(template: note.pages.first?.background ?? .BLANK,
                                       paperTheme: note.pages.first?.paperTheme ?? .white)
            }
        }
        .task(id: key) {
            if let cached = Self.cache.object(forKey: key) {
                image = cached
                return
            }
            guard let page = note.pages.first else { return }
            let ink = NoteInkStore.load(note: note, page: page)
            let rendered = NoteExporter.renderPageImage(page: page, drawing: ink,
                                                        size: CGSize(width: NoteLayout.pageSize.width / 2, height: NoteLayout.pageSize.height / 2),
                                                        scale: UIScreen.main.scale)
            Self.cache.setObject(rendered, forKey: key)
            image = rendered
        }
    }
}
