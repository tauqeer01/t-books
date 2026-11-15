import SwiftUI
import PDFKit

public struct PDFReaderView: View {
    public let book: Book
    @ObservedObject var storage = StorageManager.shared
    @StateObject private var annotations: ReaderAnnotationController
    @Environment(\.dismiss) private var dismiss

    @State private var pdfDocument: PDFDocument?
    @State private var currentPageIndex: Int
    @State private var totalPages: Int = 1
    @State private var pageRequest: PageRequest?
    @State private var isChromeVisible = true

    // Annotation rail: 0 = pen button, 1 = dial open, 2 = collapsed tool rail
    @State private var annotationRailMode: Int = 0
    @State private var isDialOpen: Bool = false
    @State private var selectedDialTool: DialToolItem = .pen(name: "Fine Pen", width: 1.5, isPencil: false)
    @State private var showingColorFlyout = false
    @State private var showingWidthFlyout = false

    // Search
    @State private var isSearching = false
    @State private var searchQuery = ""
    @State private var searchMatches: [PDFSelection] = []
    @State private var currentMatchIndex = 0

    // Sheets
    @State private var showingOutlineSheet = false
    @State private var showingBookmarksSheet = false
    @State private var showingAnnotationsSheet = false
    @State private var showingSettingsSheet = false
    @State private var shareItem: ShareItem?
    @State private var notebookTarget: NotebookCapture?
    @State private var cardDraft: CardDraft?
    @State private var selectionNoteDraft: ReaderTextSelection?

    @State private var sessionStartTimestamp = Date()

    private let isUITesting = ProcessInfo.processInfo.arguments.contains("-UITests")

    public init(book: Book) {
        self.book = book
        self._currentPageIndex = State(initialValue: book.currentPage)
        self._annotations = StateObject(wrappedValue: ReaderAnnotationController(bookId: book.id))
    }

    // Tools on the radial dial (same set and order as Android)
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
            if let doc = pdfDocument {
                PDFKitContainerView(
                    pdfDocument: doc,
                    initialPageIndex: currentPageIndex,
                    scrollMode: storage.preferences.scrollMode,
                    theme: storage.preferences.readerTheme,
                    controller: annotations,
                    activeTool: annotations.activeTool,
                    isStylusOnly: annotations.isStylusOnly,
                    colorHex: annotations.colorHex,
                    strokeWidth: annotations.strokeWidth,
                    pageRequest: pageRequest,
                    searchMatches: searchMatches,
                    currentSearchMatch: searchMatches.indices.contains(currentMatchIndex) ? searchMatches[currentMatchIndex] : nil,
                    onPageChanged: { page, total in
                        currentPageIndex = page
                        totalPages = total
                        storage.updateBookProgress(bookId: book.id, page: page, totalPages: total)
                    },
                    onTapEmpty: {
                        guard annotationRailMode == 0 else { return }
                        withAnimation(.easeInOut(duration: 0.2)) { isChromeVisible.toggle() }
                    },
                    onClip: { pageIndex, rect in
                        guard let image = renderClip(pageIndex: pageIndex, rect: rect) else {
                            annotations.toast = "Couldn't capture that area"
                            return
                        }
                        notebookTarget = NotebookCapture(kind: .clip(image), pageIndex: pageIndex)
                    }
                )
                .ignoresSafeArea(edges: .bottom)
            } else {
                ProgressView("Loading PDF...")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.appTextSecondary)
            }

            annotationRail

            // Selection actions
            if let selection = annotations.textSelection,
               annotationRailMode == 0 || annotations.tool == .selectText {
                VStack {
                    Spacer()
                    SelectionActionBar(
                        selection: selection,
                        onHighlight: { hex in markSelection(.HIGHLIGHT, color: hex) },
                        onUnderline: { markSelection(.UNDERLINE, color: "#2563EB") },
                        onStrikethrough: { markSelection(.STRIKETHROUGH, color: "#DC2626") },
                        onAddNote: { selectionNoteDraft = selection },
                        onCopy: {
                            UIPasteboard.general.string = selection.text
                            annotations.toast = "Copied"
                            clearSelection()
                        },
                        onSendToNotebook: {
                            notebookTarget = NotebookCapture(kind: .quote(selection.text), pageIndex: selection.pageIndex)
                        },
                        onFlashcard: { startCard(from: selection, cloze: false) },
                        onClozeCard: { startCard(from: selection, cloze: true) },
                        onClose: clearSelection
                    )
                    .padding(.bottom, annotationRailMode == 0 ? 90 : 24)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            if isSearching {
                VStack {
                    searchHeaderView
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            if let message = annotations.toast {
                VStack {
                    Spacer()
                    ReaderToast(message: message).padding(.bottom, 110)
                }
                .transition(.opacity)
                .task(id: message) {
                    try? await Task.sleep(nanoseconds: 1_800_000_000)
                    if annotations.toast == message { withAnimation { annotations.toast = nil } }
                }
            }

            if isUITesting { testStateProbe }
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar(isChromeVisible || annotationRailMode != 0 ? .visible : .hidden, for: .navigationBar)
        .toolbar { readerToolbar }
        .onAppear {
            loadDocument()
            applyDialTool(selectedDialTool)
        }
        .onDisappear { recordReadingTime() }
        .sheet(isPresented: $showingOutlineSheet) {
            OutlineSheetView(pdfDocument: pdfDocument) { jumpToPage($0) }
        }
        .sheet(isPresented: $showingBookmarksSheet) {
            BookmarksSheetView(bookId: book.id) { jumpToPage($0) }
        }
        .sheet(isPresented: $showingAnnotationsSheet) {
            AnnotationsListSheet(controller: annotations) { jumpToPage($0) }
        }
        .sheet(isPresented: $showingSettingsSheet) { ReaderSettingsSheet() }
        .sheet(item: $shareItem) { ShareSheet(items: [$0.url]) }
        .sheet(item: $notebookTarget) { capture in
            NotebookPickerSheet(title: capture.isClip ? "Add clip to…" : "Add quote to…") { noteId in
                addToNotebook(capture, noteId: noteId)
            }
        }
        .sheet(item: $cardDraft) { draft in
            CardEditorScreen(deckId: draft.deck.id, initialType: draft.type, initialFront: draft.front,
                             initialBack: draft.back, sourceBookId: book.id, sourcePage: draft.pageIndex,
                             deckToCreate: draft.isNewDeck ? draft.deck : nil)
        }
        .sheet(item: Binding(get: { annotations.tappedAnnotation }, set: { annotations.tappedAnnotation = $0 })) { ann in
            AnnotationMenuSheet(
                annotation: ann,
                onColor: { annotations.updateColor(ann, to: $0) },
                onEditNote: {
                    annotations.tappedAnnotation = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { annotations.editingNote = ann }
                },
                onCopy: {
                    UIPasteboard.general.string = ann.selectedText
                    annotations.tappedAnnotation = nil
                    annotations.toast = "Copied"
                },
                onDelete: { annotations.delete(ann) }
            )
        }
        .sheet(item: Binding(get: { annotations.editingNote }, set: { if $0 == nil { annotations.cancelNoteEditing() } })) { ann in
            AnnotationNoteEditor(
                annotation: ann,
                onSave: { text in
                    if text.isEmpty && ann.type == .NOTE { annotations.cancelNoteEditing() }
                    else { annotations.saveNote(ann, text: text) }
                },
                onCancel: { annotations.cancelNoteEditing() }
            )
        }
        .sheet(item: $selectionNoteDraft) { selection in
            SelectionNoteSheet(selection: selection) { text in
                markSelection(.HIGHLIGHT, color: "#FDE047", note: text)
                selectionNoteDraft = nil
            } onCancel: { selectionNoteDraft = nil }
        }
        .animation(.spring(response: 0.32, dampingFraction: 0.8), value: annotations.textSelection)
    }

    // MARK: - Annotation rail

    private var annotationRail: some View {
        VStack {
            Spacer()
            HStack(alignment: .center) {
                Spacer()
                switch annotationRailMode {
                case 0:
                    if isChromeVisible {
                        Button(action: openDial) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Theme.brandPurple, Color(hex: "#7C3AED")],
                                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .overlay(Circle().stroke(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0.1)],
                                                                            startPoint: .top, endPoint: .bottom), lineWidth: 1.5))
                                    .frame(width: 50, height: 50)
                                    .shadow(color: Theme.brandPurple.opacity(0.35), radius: 10, x: 0, y: 4)
                                Image(systemName: "pencil.tip")
                                    .font(.system(size: 21, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .buttonStyle(LiquidScaleButtonStyle())
                        .accessibilityLabel("Annotate")
                        .accessibilityIdentifier("reader.penButton")
                        .padding(.trailing, 18)
                        .padding(.bottom, 24)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                    }
                case 1:
                    RadialToolDial(items: dialItems, selectedTool: $selectedDialTool, isDialOpen: $isDialOpen) { tool, _ in
                        selectedDialTool = tool
                        applyDialTool(tool)
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                            annotationRailMode = 2
                            isDialOpen = false
                        }
                    }
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                default:
                    HStack(alignment: .center, spacing: 8) {
                        if showingColorFlyout {
                            RailFlyoutColorCard(
                                selectedColorHex: Binding(get: { annotations.colorHex }, set: { annotations.setColor($0) }),
                                palette: annotations.palette
                            ) { _ in
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) { showingColorFlyout = false }
                            }
                            .transition(.scale(scale: 0.85).combined(with: .opacity))
                        }
                        if showingWidthFlyout {
                            RailFlyoutWidthCard(
                                width: Binding(get: { annotations.strokeWidth }, set: { annotations.setWidth($0) }),
                                range: annotations.widthRange,
                                colorHex: annotations.colorHex
                            )
                            .transition(.scale(scale: 0.85).combined(with: .opacity))
                        }
                        CollapsedToolBar(
                            activeTool: selectedDialTool,
                            activeColorHex: annotations.colorHex,
                            onOpenDial: openDial,
                            onToggleColorPalette: {
                                withAnimation(.spring(response: 0.32, dampingFraction: 0.75)) {
                                    showingWidthFlyout = false
                                    showingColorFlyout.toggle()
                                }
                            },
                            onUndo: annotations.undo,
                            onRedo: annotations.redo,
                            onClearPage: { annotations.clearDrawings(onPage: currentPageIndex) },
                            onToggleStylusOnly: {
                                annotations.isStylusOnly.toggle()
                                annotations.toast = annotations.isStylusOnly ? "Apple Pencil only: fingers scroll" : "Draw with finger or Apple Pencil"
                            },
                            isStylusOnly: annotations.isStylusOnly,
                            onDone: closeAnnotating,
                            strokeWidth: annotations.tool.hasStrokeWidth ? annotations.strokeWidth : nil,
                            onToggleWidth: {
                                withAnimation(.spring(response: 0.32, dampingFraction: 0.75)) {
                                    showingColorFlyout = false
                                    showingWidthFlyout.toggle()
                                }
                            },
                            canUndo: annotations.canUndo,
                            canRedo: annotations.canRedo
                        )
                    }
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
            if annotationRailMode != 0 { Spacer() }
        }
    }

    private func openDial() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
            annotationRailMode = 1
            isDialOpen = true
            showingColorFlyout = false
            showingWidthFlyout = false
            isChromeVisible = true
        }
        annotations.isDrawingMode = true
        annotations.textSelection = nil
    }

    private func closeAnnotating() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
            annotationRailMode = 0
            isDialOpen = false
            showingColorFlyout = false
            showingWidthFlyout = false
        }
        annotations.isDrawingMode = false
        annotations.textSelection = nil
    }

    private func applyDialTool(_ item: DialToolItem) {
        showingColorFlyout = false
        showingWidthFlyout = false
        switch item {
        case .hand: annotations.select(.hand)
        case .selectText: annotations.select(.selectText)
        case .clip: annotations.select(.clip)
        case .pen(_, let width, _): annotations.select(.pen, width: Double(width))
        case .highlighter: annotations.select(.highlighter)
        case .textHighlight: annotations.select(.textHighlight)
        case .textUnderline: annotations.select(.textUnderline)
        case .textStrikethrough: annotations.select(.textStrikethrough)
        case .line: annotations.select(.line)
        case .arrow: annotations.select(.arrow)
        case .rectangle: annotations.select(.rectangle)
        case .ellipse: annotations.select(.ellipse)
        case .stickyNote: annotations.select(.stickyNote)
        case .eraser: annotations.select(.eraser)
        case .tape, .textBox, .lasso, .ruler: annotations.select(.hand)
        }
        if item != .selectText { annotations.textSelection = nil }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var readerToolbar: some ToolbarContent {
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
            .accessibilityIdentifier("reader.back")
        }
        ToolbarItem(placement: .principal) {
            VStack(spacing: 1) {
                Text(book.title)
                    .font(.system(size: 14, weight: .bold))
                    .lineLimit(1)
                Text("Page \(currentPageIndex + 1) of \(max(1, totalPages))")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.appTextSecondary)
                    .accessibilityIdentifier("reader.pageLabel")
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            HStack(spacing: 14) {
                Button(action: { withAnimation { isSearching.toggle() } }) {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("Search")
                .accessibilityIdentifier("reader.search")
                Button(action: toggleBookmark) {
                    Image(systemName: isCurrentPageBookmarked ? "bookmark.fill" : "bookmark")
                        .foregroundColor(isCurrentPageBookmarked ? Theme.brandPurple : Theme.appTextPrimary)
                }
                .accessibilityLabel(isCurrentPageBookmarked ? "Remove bookmark" : "Bookmark page")
                .accessibilityIdentifier("reader.bookmark")
                Menu {
                    Button { showingOutlineSheet = true } label: {
                        Label("Table of Contents", systemImage: "list.bullet.indent")
                    }
                    Button { showingBookmarksSheet = true } label: {
                        Label("Bookmarks", systemImage: "bookmark")
                    }
                    Button { showingAnnotationsSheet = true } label: {
                        Label("Annotations", systemImage: "highlighter")
                    }
                    Button { showingSettingsSheet = true } label: {
                        Label("Reading Display & Themes", systemImage: "textformat.size")
                    }
                    Divider()
                    Button { exportAnnotatedPDF() } label: {
                        Label("Export Annotated Copy", systemImage: "square.and.arrow.up")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("More")
                .accessibilityIdentifier("reader.more")
            }
            .font(.system(size: 16))
            .foregroundColor(Theme.appTextPrimary)
        }
    }

    // MARK: - Search

    private var searchHeaderView: some View {
        HStack(spacing: 8) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(Theme.appTextSecondary)
                TextField("Search in document...", text: $searchQuery, onCommit: executeSearch)
                    .font(.system(size: 14))
                    .submitLabel(.search)
                    .accessibilityIdentifier("search.field")
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
                    .accessibilityIdentifier("search.count")
                Button(action: prevSearchMatch) {
                    Image(systemName: "chevron.up").padding(8).background(Circle().fill(Theme.appSurface))
                }
                .accessibilityIdentifier("search.prev")
                Button(action: nextSearchMatch) {
                    Image(systemName: "chevron.down").padding(8).background(Circle().fill(Theme.appSurface))
                }
                .accessibilityIdentifier("search.next")
            } else if !searchQuery.isEmpty && searchedOnce {
                Text("No matches").font(.system(size: 12)).foregroundColor(Theme.appTextSecondary)
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

    @State private var searchedOnce = false

    private func executeSearch() {
        guard let doc = pdfDocument, !searchQuery.isEmpty else { return }
        searchedOnce = true
        searchMatches = doc.findString(searchQuery, withOptions: [.caseInsensitive])
        currentMatchIndex = 0
    }

    private func nextSearchMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.count
    }

    private func prevSearchMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.count) % searchMatches.count
    }

    // MARK: - Selection actions

    private func markSelection(_ type: AnnotationType, color: String, note: String = "") {
        guard let selection = annotations.textSelection ?? selectionNoteDraft,
              let page = pdfDocument?.page(at: selection.pageIndex) else { return }
        annotations.commitMarkup(selection, type: type, colorHex: color, note: note,
                                 space: ReaderAnnotationController.pageSpace(of: page))
        clearSelection()
    }

    private func clearSelection() {
        annotations.textSelection = nil
        NotificationCenter.default.post(name: .readerClearSelection, object: nil)
    }

    private func startCard(from selection: ReaderTextSelection, cloze: Bool) {
        let text = selection.text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let existing = storage.decks.first { $0.bookId == book.id } ?? storage.decks.first { $0.name == book.title }
        let deck = existing ?? Deck(name: book.title, bookId: book.id)
        // A basic card starts with the selection as the answer; a cloze card starts with the sentence
        cardDraft = CardDraft(deck: deck, isNewDeck: existing == nil, type: cloze ? .CLOZE : .BASIC,
                              front: cloze ? text : "", back: cloze ? "" : text, pageIndex: selection.pageIndex)
        clearSelection()
    }

    private func addToNotebook(_ capture: NotebookCapture, noteId: String?) {
        var element: NoteElement
        switch capture.kind {
        case .quote(let text):
            element = NoteElement(kind: .QUOTE, width: 320, height: 120, content: text, quoteText: text,
                                  sourceBookId: book.id, sourcePage: capture.pageIndex)
        case .clip(let image):
            let url = storage.documentsDirectory.appendingPathComponent("Photos").appendingPathComponent("\(UUID().uuidString).jpg")
            guard let data = image.jpegData(compressionQuality: 0.9), (try? data.write(to: url)) != nil else {
                annotations.toast = "Couldn't save the clip"
                return
            }
            let width: Float = 320
            let height = Float(image.size.height / max(image.size.width, 1)) * width
            element = NoteElement(kind: .IMAGE, width: width, height: height, imagePath: url.path,
                                  sourceBookId: book.id, sourcePage: capture.pageIndex)
        }

        if let noteId, let i = storage.notes.firstIndex(where: { $0.id == noteId }) {
            if storage.notes[i].pages.isEmpty {
                storage.notes[i].pages = [NotePage(noteId: noteId)]
            }
            let last = storage.notes[i].pages.count - 1
            let bottom = storage.notes[i].pages[last].elements.map { $0.y + $0.height }.max() ?? 24
            element.x = 40
            element.y = bottom + 16
            element.z = (storage.notes[i].pages[last].elements.map(\.z).max() ?? 0) + 1
            storage.notes[i].pages[last].elements.append(element)
            storage.notes[i].updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
            annotations.toast = "Added to \(storage.notes[i].title)"
        } else {
            var note = Note(title: "Notes on \(book.title)")
            element.x = 40
            element.y = 40
            note.pages = [NotePage(noteId: note.id, elements: [element])]
            storage.notes.insert(note, at: 0)
            annotations.toast = "Added to \(note.title)"
        }
        storage.saveAll()
        clearSelection()
    }

    /// A sharp image of part of a page (page points, top-left origin), with its annotations.
    private func renderClip(pageIndex: Int, rect: CGRect) -> UIImage? {
        guard let page = pdfDocument?.page(at: pageIndex) else { return nil }
        let box = page.bounds(for: .cropBox)
        let scale = min(2400 / max(box.width, box.height), 4)
        let full = page.thumbnail(of: CGSize(width: box.width * scale, height: box.height * scale), for: .cropBox)
        guard let cg = full.cgImage else { return nil }
        let px = CGFloat(cg.width) / box.width
        let crop = CGRect(x: rect.minX * px, y: rect.minY * px, width: rect.width * px, height: rect.height * px).integral
        guard let cropped = cg.cropping(to: crop) else { return nil }
        return UIImage(cgImage: cropped)
    }

    // MARK: - Document & navigation

    private func loadDocument() {
        guard pdfDocument == nil else { return }
        var fileURL = URL(fileURLWithPath: book.filePath)
        if !FileManager.default.fileExists(atPath: fileURL.path) {
            let fallbackURL = storage.documentsDirectory.appendingPathComponent("PDFs").appendingPathComponent(fileURL.lastPathComponent)
            if FileManager.default.fileExists(atPath: fallbackURL.path) { fileURL = fallbackURL }
        }
        if let doc = PDFDocument(url: fileURL) {
            pdfDocument = doc
            totalPages = doc.pageCount
            if currentPageIndex >= doc.pageCount { currentPageIndex = max(0, doc.pageCount - 1) }
        } else {
            annotations.toast = "Couldn't open this PDF"
        }
    }

    private var isCurrentPageBookmarked: Bool {
        storage.bookmarks.contains { $0.bookId == book.id && $0.pageIndex == currentPageIndex }
    }

    private func toggleBookmark() {
        if let existing = storage.bookmarks.first(where: { $0.bookId == book.id && $0.pageIndex == currentPageIndex }) {
            storage.bookmarks.removeAll { $0.id == existing.id }
        } else {
            storage.bookmarks.append(Bookmark(bookId: book.id, pageIndex: currentPageIndex, label: "Page \(currentPageIndex + 1)"))
        }
        storage.saveAll()
    }

    private func jumpToPage(_ index: Int) {
        guard let doc = pdfDocument, index >= 0, index < doc.pageCount else { return }
        pageRequest = PageRequest(index)
    }

    private func exportAnnotatedPDF() {
        guard let doc = pdfDocument else { return }
        let safeTitle = book.title.components(separatedBy: CharacterSet(charactersIn: "/\\:?%*|\"<>")).joined(separator: "-")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(safeTitle) (annotated).pdf")
        try? FileManager.default.removeItem(at: url)
        // Annotations live on the document as native PDF annotations, so they are written with it
        if doc.write(to: url) {
            shareItem = ShareItem(url: url)
        } else {
            annotations.toast = "Export failed"
        }
    }

    private func recordReadingTime() {
        let elapsed = Int64(Date().timeIntervalSince(sessionStartTimestamp))
        storage.addReadingTime(seconds: elapsed)
        sessionStartTimestamp = Date()
    }

    /// Reader state for UI tests (only with the -UITests launch argument). Read live when queried.
    private var testStateProbe: some View {
        let controller = annotations
        return VStack {
            HStack {
                ReaderTestProbe { controller.testStateDescription() }
                    .frame(width: 2, height: 2)
                Spacer()
            }
            Spacer()
        }
        .allowsHitTesting(false)
    }
}

/// Exposes reader state to UI tests through an accessibility value computed at query time.
private struct ReaderTestProbe: UIViewRepresentable {
    let describe: () -> String

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.describe = describe
        return view
    }

    func updateUIView(_ view: ProbeView, context: Context) { view.describe = describe }

    final class ProbeView: UIView {
        var describe: () -> String = { "" }
        override var isAccessibilityElement: Bool { get { true } set {} }
        override var accessibilityIdentifier: String? { get { "reader.state" } set {} }
        override var accessibilityValue: String? { get { describe() } set {} }
        override var accessibilityLabel: String? { get { describe() } set {} }
    }
}

// MARK: - Reader helper types

extension Notification.Name {
    static let readerClearSelection = Notification.Name("readerClearSelection")
}

struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

struct NotebookCapture: Identifiable {
    enum Kind {
        case quote(String)
        case clip(UIImage)
    }
    let id = UUID()
    let kind: Kind
    let pageIndex: Int
    var isClip: Bool { if case .clip = kind { return true } else { return false } }
}

struct CardDraft: Identifiable {
    let id = UUID()
    let deck: Deck
    let isNewDeck: Bool
    let type: CardType
    let front: String
    let back: String
    let pageIndex: Int
}

extension ReaderTextSelection: Identifiable {
    public var id: String { "\(pageIndex)-\(text.hashValue)-\(lines.count)" }
}

/// Highlights the selection with a note.
struct SelectionNoteSheet: View {
    let selection: ReaderTextSelection
    var onSave: (String) -> Void
    var onCancel: () -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("“\(selection.text)”").font(.system(size: 14)).foregroundColor(Theme.appTextSecondary).lineLimit(3)
                TextEditor(text: $text)
                    .focused($focused)
                    .onAppear { focused = true }
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Theme.brandPurpleSoft.opacity(0.6)))
                    .accessibilityIdentifier("selectionNote.text")
            }
            .padding(16)
            .navigationTitle("Add note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(text.trimmingCharacters(in: .whitespacesAndNewlines)) }
                        .fontWeight(.bold)
                        .accessibilityIdentifier("selectionNote.save")
                }
            }
        }
        .presentationDetents([.medium])
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
