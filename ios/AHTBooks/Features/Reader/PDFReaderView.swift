import SwiftUI
import PDFKit
import PencilKit

public struct PDFReaderView: View {
    public let book: Book
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var pdfDocument: PDFDocument?
    @State private var currentPageIndex: Int
    @State private var totalPages: Int = 1
    
    // Annotation & Dial State
    // 0 = Normal reading mode (pen button at bottom-right)
    // 1 = Dial open (spinning radial half-disc at right edge)
    // 2 = Collapsed tool rail on right edge
    @State private var annotationRailMode: Int = 0
    @State private var isDialOpen: Bool = false
    @State private var selectedDialTool: DialToolItem = .pen(name: "Pen", width: 2.5, isPencil: false)
    @State private var activeColorHex: String = "#5935FF"
    @State private var showingColorFlyout: Bool = false
    @State private var isStylusOnly: Bool = false
    
    // Search state
    @State private var isSearching: Bool = false
    @State private var searchQuery: String = ""
    @State private var searchMatches: [PDFSelection] = []
    @State private var currentMatchIndex: Int = 0
    
    // Sheets & Popovers
    @State private var showingOutlineSheet: Bool = false
    @State private var showingBookmarksSheet: Bool = false
    @State private var showingSettingsSheet: Bool = false
    @State private var showingShareSheet: Bool = false
    @State private var shareURL: URL? = nil
    
    // Selection state
    @State private var selectedText: String? = nil
    @State private var selectedTextBounds: CGRect = .zero
    @State private var selectedTextPageIndex: Int = 0
    @State private var showingSelectionMenu: Bool = false
    @State private var noteInputText: String = ""
    @State private var showingAddNoteAlert: Bool = false
    
    // Drawing canvas state
    @State private var currentDrawing: PKDrawing = PKDrawing()
    @State private var canvasUndoManager: UndoManager? = nil
    
    // Session timer
    @State private var sessionStartTimestamp = Date()
    
    public init(book: Book) {
        self.book = book
        self._currentPageIndex = State(initialValue: book.currentPage)
    }
    
    // Tools available on the radial dial
    private var dialItems: [DialToolItem] {
        [
            .hand,
            .selectText,
            .clip,
            .pen(name: "Fine Pen", width: 1.5, isPencil: false),
            .pen(name: "Medium Pen", width: 3.0, isPencil: false),
            .highlighter,
            .textHighlight,
            .textUnderline,
            .textStrikethrough,
            .line,
            .arrow,
            .rectangle,
            .ellipse,
            .stickyNote,
            .eraser
        ]
    }
    
    public var body: some View {
        ZStack {
            // Main PDF View
            if let doc = pdfDocument {
                ZStack {
                    PDFKitContainerView(
                        pdfDocument: doc,
                        initialPageIndex: currentPageIndex,
                        scrollMode: storage.preferences.scrollMode,
                        theme: storage.preferences.readerTheme,
                        onPageChanged: { page, total in
                            self.currentPageIndex = page
                            self.totalPages = total
                            storage.updateBookProgress(bookId: book.id, page: page, totalPages: total)
                            loadDrawingForPage(page: page)
                        },
                        onTextSelected: { text, bounds, page in
                            self.selectedText = text
                            self.selectedTextBounds = bounds
                            self.selectedTextPageIndex = page
                            self.showingSelectionMenu = true
                        }
                    )
                    
                    // Drawing Overlay if in annotation mode and tool draws ink
                    if annotationRailMode > 0 && selectedDialTool.isDrawingInk {
                        PencilKitOverlayView(
                            drawing: $currentDrawing,
                            tool: selectedDialTool,
                            colorHex: activeColorHex,
                            isStylusOnly: isStylusOnly,
                            onUndoManagerReady: { um in
                                self.canvasUndoManager = um
                            },
                            onDrawingChanged: {
                                saveDrawingForPage(page: currentPageIndex)
                            }
                        )
                    }
                }
                .ignoresSafeArea(edges: .bottom)
            } else {
                ProgressView("Loading PDF...")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.appTextSecondary)
            }
            
            // Annotation Tool System (Matching Android Behavior)
            VStack {
                Spacer()
                HStack(alignment: .center) {
                    Spacer()
                    
                    switch annotationRailMode {
                    case 0:
                        // Mode 0: Closed -> Liquid floating pen button at bottom-right
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                annotationRailMode = 1
                                isDialOpen = true
                            }
                        }) {
                            ZStack {
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            colors: [
                                                Theme.brandPurple,
                                                Color(hex: "#7C3AED")
                                            ],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .overlay(
                                        Circle()
                                            .stroke(
                                                LinearGradient(
                                                    colors: [.white.opacity(0.6), .white.opacity(0.1)],
                                                    startPoint: .top,
                                                    endPoint: .bottom
                                                ),
                                                lineWidth: 1.5
                                            )
                                    )
                                    .frame(width: 50, height: 50)
                                    .shadow(color: Theme.brandPurple.opacity(0.35), radius: 10, x: 0, y: 4)
                                
                                Image(systemName: "pencil.tip")
                                    .font(.system(size: 21, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .buttonStyle(LiquidScaleButtonStyle())
                        .padding(.trailing, 18)
                        .padding(.bottom, 24)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                        
                    case 1:
                        // Mode 1: Dial open (Spinning liquid half-disc at right edge, vertically centered)
                        RadialToolDial(
                            items: dialItems,
                            selectedTool: $selectedDialTool,
                            isDialOpen: $isDialOpen,
                            onPick: { tool, tapped in
                                selectedDialTool = tool
                                withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                    annotationRailMode = 2
                                    isDialOpen = false
                                }
                            }
                        )
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                        
                    default:
                        // Mode 2: Liquid collapsed tool bar on the right edge
                        HStack(spacing: 8) {
                            if showingColorFlyout {
                                RailFlyoutColorCard(selectedColorHex: $activeColorHex) { hex in
                                    activeColorHex = hex
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                        showingColorFlyout = false
                                    }
                                }
                                .transition(.scale(scale: 0.85).combined(with: .opacity))
                            }
                            
                            CollapsedToolBar(
                                activeTool: selectedDialTool,
                                activeColorHex: activeColorHex,
                                onOpenDial: {
                                    withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                        annotationRailMode = 1
                                        isDialOpen = true
                                        showingColorFlyout = false
                                    }
                                },
                                onToggleColorPalette: {
                                    withAnimation(.spring(response: 0.32, dampingFraction: 0.75)) {
                                        showingColorFlyout.toggle()
                                    }
                                },
                                onUndo: {
                                    canvasUndoManager?.undo()
                                },
                                onRedo: {
                                    canvasUndoManager?.redo()
                                },
                                onClearPage: {
                                    currentDrawing = PKDrawing()
                                    saveDrawingForPage(page: currentPageIndex)
                                },
                                onToggleStylusOnly: {
                                    isStylusOnly.toggle()
                                },
                                isStylusOnly: isStylusOnly,
                                onDone: {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                        annotationRailMode = 0
                                        isDialOpen = false
                                        showingColorFlyout = false
                                    }
                                }
                            )
                        }
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                    }
                }
                if annotationRailMode != 0 {
                    Spacer()
                }
            }
            
            // Search Bar Overlay
            if isSearching {
                VStack {
                    searchHeaderView
                        .padding(.top, 44)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: {
                    recordReadingTime()
                    dismiss()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Library")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
            
            ToolbarItem(placement: .principal) {
                VStack(spacing: 1) {
                    Text(book.title)
                        .font(.system(size: 14, weight: .bold))
                        .lineLimit(1)
                    Text("Page \(currentPageIndex + 1) of \(max(1, totalPages))")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.appTextSecondary)
                }
            }
            
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 14) {
                    Button(action: { withAnimation { isSearching.toggle() } }) {
                        Image(systemName: "magnifyingglass")
                    }
                    
                    Button(action: toggleBookmark) {
                        Image(systemName: isCurrentPageBookmarked ? "bookmark.fill" : "bookmark")
                            .foregroundColor(isCurrentPageBookmarked ? Theme.brandPurple : Theme.appTextPrimary)
                    }
                    
                    Menu {
                        Button {
                            showingOutlineSheet = true
                        } label: {
                            Label("Table of Contents", systemImage: "list.bullet.indent")
                        }
                        
                        Button {
                            showingBookmarksSheet = true
                        } label: {
                            Label("Bookmarks", systemImage: "bookmark")
                        }
                        
                        Button {
                            showingSettingsSheet = true
                        } label: {
                            Label("Reading Display & Themes", systemImage: "textformat.size")
                        }
                        
                        Divider()
                        
                        Button {
                            exportAnnotatedPDF()
                        } label: {
                            Label("Export Annotated Copy", systemImage: "square.and.arrow.up")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
                .font(.system(size: 16))
                .foregroundColor(Theme.appTextPrimary)
            }
        }
        .onAppear {
            loadDocument()
            loadDrawingForPage(page: currentPageIndex)
        }
        .onDisappear {
            recordReadingTime()
        }
        .sheet(isPresented: $showingOutlineSheet) {
            OutlineSheetView(pdfDocument: pdfDocument) { pageIndex in
                jumpToPage(pageIndex)
            }
        }
        .sheet(isPresented: $showingBookmarksSheet) {
            BookmarksSheetView(bookId: book.id) { pageIndex in
                jumpToPage(pageIndex)
            }
        }
        .sheet(isPresented: $showingSettingsSheet) {
            ReaderSettingsSheet()
        }
        .sheet(isPresented: $showingShareSheet) {
            if let url = shareURL {
                ShareSheet(items: [url])
            }
        }
        .confirmationDialog("Text Selection", isPresented: $showingSelectionMenu, titleVisibility: .visible) {
            Button("Highlight in Yellow") { applyTextMarkup(type: .HIGHLIGHT, colorHex: "#FFE600") }
            Button("Highlight in Green") { applyTextMarkup(type: .HIGHLIGHT, colorHex: "#4ADE80") }
            Button("Highlight in Purple") { applyTextMarkup(type: .HIGHLIGHT, colorHex: "#A78BFA") }
            Button("Underline") { applyTextMarkup(type: .UNDERLINE, colorHex: "#5935FF") }
            Button("Strikethrough") { applyTextMarkup(type: .STRIKETHROUGH, colorHex: "#EF4444") }
            Button("Add Note") { showingAddNoteAlert = true }
            Button("Send to Notebook") { sendSelectionToNote() }
            Button("Create Flashcard") { createFlashcardFromSelection() }
            Button("Copy Text") {
                UIPasteboard.general.string = selectedText
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Add Note to Highlight", isPresented: $showingAddNoteAlert) {
            TextField("Note comment...", text: $noteInputText)
            Button("Cancel", role: .cancel) { noteInputText = "" }
            Button("Save") {
                applyTextMarkup(type: .NOTE, colorHex: activeColorHex, noteContent: noteInputText)
                noteInputText = ""
            }
        }
    }
    
    // MARK: - Search Bar View
    
    private var searchHeaderView: some View {
        HStack(spacing: 8) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(Theme.appTextSecondary)
                TextField("Search in document...", text: $searchQuery, onCommit: executeSearch)
                    .font(.system(size: 14))
                if !searchQuery.isEmpty {
                    Button(action: {
                        searchQuery = ""
                        searchMatches = []
                    }) {
                        Image(systemName: "xmark.circle.fill").foregroundColor(Theme.appTextSecondary)
                    }
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Theme.appSurface))
            
            if !searchMatches.isEmpty {
                Text("\(currentMatchIndex + 1)/\(searchMatches.count)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                
                Button(action: prevSearchMatch) {
                    Image(systemName: "chevron.up")
                        .padding(8)
                        .background(Circle().fill(Theme.appSurface))
                }
                Button(action: nextSearchMatch) {
                    Image(systemName: "chevron.down")
                        .padding(8)
                        .background(Circle().fill(Theme.appSurface))
                }
            }
            
            Button("Done") {
                withAnimation {
                    isSearching = false
                    searchQuery = ""
                    searchMatches = []
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Theme.brandPurple)
        }
        .padding(12)
        .background(Theme.appSurface.shadow(radius: 4))
    }
    
    // MARK: - Actions & Helpers
    
    private func loadDocument() {
        var fileURL = URL(fileURLWithPath: book.filePath)
        if !FileManager.default.fileExists(atPath: fileURL.path) {
            let fileName = fileURL.lastPathComponent
            let fallbackURL = StorageManager.shared.documentsDirectory.appendingPathComponent("PDFs").appendingPathComponent(fileName)
            if FileManager.default.fileExists(atPath: fallbackURL.path) {
                fileURL = fallbackURL
            }
        }
        
        print("AHT_LOG: loadDocument URL=\(fileURL.path), exists=\(FileManager.default.fileExists(atPath: fileURL.path))")
        if let doc = PDFDocument(url: fileURL) {
            self.pdfDocument = doc
            self.totalPages = doc.pageCount
        } else {
            print("AHT_LOG: Failed to load PDFDocument from \(fileURL)")
        }
    }
    
    private var isCurrentPageBookmarked: Bool {
        storage.bookmarks.contains { $0.bookId == book.id && $0.pageIndex == currentPageIndex }
    }
    
    private func toggleBookmark() {
        if let existing = storage.bookmarks.first(where: { $0.bookId == book.id && $0.pageIndex == currentPageIndex }) {
            storage.bookmarks.removeAll { $0.id == existing.id }
        } else {
            let bm = Bookmark(bookId: book.id, pageIndex: currentPageIndex, label: "Page \(currentPageIndex + 1)")
            storage.bookmarks.append(bm)
        }
        storage.saveAll()
    }
    
    private func jumpToPage(_ index: Int) {
        guard let doc = pdfDocument, index >= 0, index < doc.pageCount else { return }
        self.currentPageIndex = index
        storage.updateBookProgress(bookId: book.id, page: index, totalPages: doc.pageCount)
    }
    
    private func executeSearch() {
        guard let doc = pdfDocument, !searchQuery.isEmpty else { return }
        let matches = doc.findString(searchQuery, withOptions: [.caseInsensitive])
        self.searchMatches = matches
        self.currentMatchIndex = 0
        if let first = matches.first, let page = first.pages.first {
            jumpToPage(doc.index(for: page))
        }
    }
    
    private func nextSearchMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.count
        let match = searchMatches[currentMatchIndex]
        if let page = match.pages.first, let doc = pdfDocument {
            jumpToPage(doc.index(for: page))
        }
    }
    
    private func prevSearchMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.count) % searchMatches.count
        let match = searchMatches[currentMatchIndex]
        if let page = match.pages.first, let doc = pdfDocument {
            jumpToPage(doc.index(for: page))
        }
    }
    
    private func applyTextMarkup(type: AnnotationType, colorHex: String, noteContent: String = "") {
        guard let text = selectedText, let doc = pdfDocument,
              let page = doc.page(at: selectedTextPageIndex) else { return }
        
        let annotation = PDFAnnotation(
            bounds: selectedTextBounds,
            forType: type == .UNDERLINE ? .underline : (type == .STRIKETHROUGH ? .strikeOut : .highlight),
            withProperties: nil
        )
        annotation.color = UIColor(Color(hex: colorHex))
        page.addAnnotation(annotation)
        
        let ann = BookAnnotation(
            bookId: book.id,
            pageIndex: selectedTextPageIndex,
            type: type,
            colorHex: colorHex,
            selectedText: text,
            noteContent: noteContent,
            rectLeft: Float(selectedTextBounds.minX),
            rectTop: Float(selectedTextBounds.minY),
            rectRight: Float(selectedTextBounds.maxX),
            rectBottom: Float(selectedTextBounds.maxY)
        )
        storage.annotations.append(ann)
        storage.saveAll()
    }
    
    private func sendSelectionToNote() {
        guard let text = selectedText else { return }
        let newElement = NoteElement(
            kind: .QUOTE,
            content: text,
            quoteText: text,
            sourceBookId: book.id,
            sourcePage: selectedTextPageIndex
        )
        if let firstNote = storage.notes.first {
            if let pageIndex = firstNote.pages.indices.first {
                storage.notes[0].pages[pageIndex].elements.append(newElement)
            }
        } else {
            let note = Note(
                title: "Notes on \(book.title)",
                pages: [NotePage(noteId: UUID().uuidString, elements: [newElement])]
            )
            storage.notes.append(note)
        }
        storage.saveAll()
    }
    
    private func createFlashcardFromSelection() {
        guard let text = selectedText, let deck = storage.decks.first else { return }
        let card = Card(
            deckId: deck.id,
            type: .BASIC,
            front: CardFace(text: text),
            back: CardFace(text: "Explanation for: \(text)"),
            sourceBookId: book.id,
            sourcePage: selectedTextPageIndex
        )
        let sched = CardSchedule(cardId: card.id)
        storage.cards.append(card)
        storage.schedules.append(sched)
        storage.saveAll()
    }
    
    private func exportAnnotatedPDF() {
        guard let doc = pdfDocument else { return }
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("Annotated_\(book.title).pdf")
        doc.write(to: tempURL)
        self.shareURL = tempURL
        self.showingShareSheet = true
    }
    
    private func loadDrawingForPage(page: Int) {
        let fileName = "drawing_\(book.id)_p\(page).drawing"
        self.currentDrawing = storage.loadDrawing(fileName: fileName)
    }
    
    private func saveDrawingForPage(page: Int) {
        let fileName = "drawing_\(book.id)_p\(page).drawing"
        storage.saveDrawing(currentDrawing, fileName: fileName)
    }
    
    private func recordReadingTime() {
        let elapsed = Int64(Date().timeIntervalSince(sessionStartTimestamp))
        storage.addReadingTime(seconds: elapsed)
        sessionStartTimestamp = Date()
    }
}

// MARK: - PencilKit Overlay View (Supports UndoManager and Stylus-only)

public struct PencilKitOverlayView: UIViewRepresentable {
    @Binding public var drawing: PKDrawing
    public var tool: DialToolItem
    public var colorHex: String
    public var isStylusOnly: Bool
    public var onUndoManagerReady: (UndoManager?) -> Void
    public var onDrawingChanged: () -> Void
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public func makeUIView(context: Context) -> PKCanvasView {
        let canvas = PKCanvasView()
        canvas.backgroundColor = .clear
        canvas.isOpaque = false
        canvas.drawing = drawing
        canvas.delegate = context.coordinator
        canvas.drawingPolicy = isStylusOnly ? .pencilOnly : .anyInput
        updateTool(for: canvas)
        
        DispatchQueue.main.async {
            onUndoManagerReady(canvas.undoManager)
        }
        return canvas
    }
    
    public func updateUIView(_ canvas: PKCanvasView, context: Context) {
        if canvas.drawing != drawing {
            canvas.drawing = drawing
        }
        canvas.drawingPolicy = isStylusOnly ? .pencilOnly : .anyInput
        updateTool(for: canvas)
    }
    
    private func updateTool(for canvas: PKCanvasView) {
        let color = UIColor(Color(hex: colorHex))
        switch tool {
        case .pen(_, let width, let isPencil):
            if isPencil {
                canvas.tool = PKInkingTool(.pencil, color: color, width: width)
            } else {
                canvas.tool = PKInkingTool(.pen, color: color, width: width)
            }
        case .highlighter:
            canvas.tool = PKInkingTool(.marker, color: color.withAlphaComponent(0.4), width: 14.0)
        case .eraser:
            canvas.tool = PKEraserTool(.vector)
        default:
            canvas.tool = PKInkingTool(.pen, color: color, width: 2.0)
        }
    }
    
    public class Coordinator: NSObject, PKCanvasViewDelegate {
        var parent: PencilKitOverlayView
        init(_ parent: PencilKitOverlayView) { self.parent = parent }
        
        public func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
            parent.drawing = canvasView.drawing
            parent.onDrawingChanged()
        }
    }
}

// MARK: - Outline Sheet View

public struct OutlineSheetView: View {
    public let pdfDocument: PDFDocument?
    public var onSelectPage: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    
    private var outlineItems: [(String, Int)] {
        guard let doc = pdfDocument, let root = doc.outlineRoot else { return [] }
        var list: [(String, Int)] = []
        func traverse(node: PDFOutline) {
            if let label = node.label, let dest = node.destination, let page = dest.page {
                let idx = doc.index(for: page)
                list.append((label, idx))
            }
            for i in 0..<node.numberOfChildren {
                if let child = node.child(at: i) {
                    traverse(node: child)
                }
            }
        }
        traverse(node: root)
        return list
    }
    
    public var body: some View {
        NavigationStack {
            List {
                if outlineItems.isEmpty {
                    Text("No Table of Contents available in this document.")
                        .foregroundColor(Theme.appTextSecondary)
                } else {
                    ForEach(outlineItems, id: \.0) { item in
                        Button(action: {
                            onSelectPage(item.1)
                            dismiss()
                        }) {
                            HStack {
                                Text(item.0)
                                    .font(.system(size: 15))
                                    .foregroundColor(Theme.appTextPrimary)
                                Spacer()
                                Text("Page \(item.1 + 1)")
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.brandPurple)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Table of Contents")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Bookmarks Sheet View

public struct BookmarksSheetView: View {
    public let bookId: String
    public var onSelectPage: (Int) -> Void
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    private var bookBookmarks: [Bookmark] {
        storage.bookmarks.filter { $0.bookId == bookId }
    }
    
    public var body: some View {
        NavigationStack {
            List {
                if bookBookmarks.isEmpty {
                    Text("No bookmarks added yet.")
                        .foregroundColor(Theme.appTextSecondary)
                } else {
                    ForEach(bookBookmarks) { bm in
                        Button(action: {
                            onSelectPage(bm.pageIndex)
                            dismiss()
                        }) {
                            HStack {
                                Image(systemName: "bookmark.fill")
                                    .foregroundColor(Theme.brandPurple)
                                Text(bm.label.isEmpty ? "Page \(bm.pageIndex + 1)" : bm.label)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(Theme.appTextPrimary)
                                Spacer()
                                Text("Page \(bm.pageIndex + 1)")
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.brandPurple)
                            }
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
            .navigationTitle("Bookmarks")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Reader Settings Sheet

public struct ReaderSettingsSheet: View {
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Scroll Mode")) {
                    Picker("Paging", selection: $storage.preferences.scrollMode) {
                        Text("Continuous Vertical").tag(PageScrollMode.CONTINUOUS_VERTICAL)
                        Text("Horizontal Paging").tag(PageScrollMode.HORIZONTAL_PAGING)
                        Text("Single Page").tag(PageScrollMode.SINGLE_PAGE)
                        Text("Book Page Curl").tag(PageScrollMode.BOOK_CURL)
                    }
                }
                
                Section(header: Text("Theme Color")) {
                    Picker("Reader Theme", selection: $storage.preferences.readerTheme) {
                        Text("Light").tag(ReaderTheme.LIGHT)
                        Text("Dark (Night)").tag(ReaderTheme.DARK)
                        Text("Sepia").tag(ReaderTheme.SEPIA)
                        Text("Mint").tag(ReaderTheme.MINT)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                }
                
                Section(header: Text("Display Settings")) {
                    Toggle("Keep Screen Awake", isOn: $storage.preferences.keepScreenOn)
                    Toggle("High-Resolution Rendering", isOn: $storage.preferences.highResolutionRendering)
                }
            }
            .navigationTitle("Reader Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        storage.saveAll()
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Share Sheet Helper

public struct ShareSheet: UIViewControllerRepresentable {
    public let items: [Any]
    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
