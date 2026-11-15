import SwiftUI
import PencilKit

/// Lasso or tap selection: strokes (by index in the document drawing) and elements, on one page.
public struct NoteSelection: Equatable {
    public var pageIndex: Int
    public var strokeIndices: Set<Int>
    public var elementIds: Set<String>
    public var isEmpty: Bool { strokeIndices.isEmpty && elementIds.isEmpty }
}

/// Copied strokes (page points) and elements.
struct NoteClipboard {
    var strokes: [PKStroke]
    var elements: [NoteElement]
}

/// State and editing actions for one open note (the iOS counterpart of Android's NoteEditorViewModel).
@MainActor
public final class NoteEditorModel: ObservableObject {
    @Published public private(set) var note: Note
    /// Bumped whenever ink changes outside the canvas (undo, page edits, lasso), so the canvas reloads it.
    @Published public private(set) var drawingRevision = 0
    public private(set) var drawing = PKDrawing()

    // Tools
    @Published public var tool: NoteTool = .pen { didSet { if tool != .lasso && tool != .hand && tool != .text { selection = nil } } }
    @Published public var pens: [NotePen] = NotePenStore.load()
    @Published public var activePen: Int = min(NotePenStore.activeIndex, max(NotePenStore.load().count - 1, 0))
    /// nil = auto shape (draw freehand, snap what is recognized).
    @Published public var shapeKind: ShapeRecognizer.ShapeKind?
    @Published public var eraserMode: NoteEraserMode = .stroke
    @Published public var eraserSize: CGFloat = 20
    @Published public var tapeColor: String = noteTapeColors[0]
    @Published public var stickyColor: String = noteStickyColors[0]
    @Published public var revealedTapes: Set<String> = []
    @Published public var fingerDrawing: Bool = NotePenStore.fingerDrawing
    /// Once Apple Pencil is used, fingers scroll and only the pencil draws (unless finger drawing is forced on).
    @Published public var stylusSeen = false

    // Selection & editing
    @Published public var selection: NoteSelection?
    @Published public var editingElementId: String?
    @Published public var currentPage = 0

    /// Asks the canvas to scroll a page into view.
    public struct ScrollRequest: Equatable {
        public let page: Int
        public let id = UUID()
    }
    @Published public private(set) var scrollRequest: ScrollRequest?

    public func goToPage(_ index: Int) {
        guard note.pages.indices.contains(index) else { return }
        currentPage = index
        scrollRequest = ScrollRequest(page: index)
    }

    // History & messages
    @Published public private(set) var canUndo = false
    @Published public private(set) var canRedo = false
    @Published public var message: String?
    @Published public private(set) var hasClipboard = false

    private struct Snapshot { var drawing: PKDrawing; var pages: [NotePage] }
    private var undoStack: [Snapshot] = []
    private var redoStack: [Snapshot] = []
    private var clipboard: NoteClipboard?
    private var saveWork: DispatchWorkItem?
    private let storage: StorageManager
    private let onChange: (Note) -> Void

    public init(note: Note, storage: StorageManager = .shared, onChange: @escaping (Note) -> Void) {
        var note = note
        if note.pages.isEmpty { note.pages = [NotePage(noteId: note.id)] }
        self.note = note
        self.storage = storage
        self.onChange = onChange
        drawing = NoteInkStore.join(note.pages.map { NoteInkStore.load(note: note, page: $0, storage: storage) })
    }

    public var pen: NotePen { pens[min(activePen, pens.count - 1)] }
    public var pageCount: Int { note.pages.count }

    // MARK: - Ink from the canvas

    /// The canvas reports finished ink changes (strokes, erasing, shape snaps).
    func inkChanged(from before: PKDrawing, to after: PKDrawing) {
        guard before != after else { return }
        push(Snapshot(drawing: before, pages: note.pages))
        drawing = after
        scheduleSave()
    }

    /// Ink changed by the canvas itself without an undo step (keeps the model in sync).
    func syncInk(_ after: PKDrawing) { drawing = after }

    // MARK: - Pens

    public func selectPen(_ index: Int) {
        activePen = index
        NotePenStore.activeIndex = index
        tool = .pen
    }

    public func updateActivePen(_ change: (inout NotePen) -> Void) {
        change(&pens[activePen])
        NotePenStore.save(pens)
        objectWillChange.send()
    }

    public func addPen() {
        pens.append(pen)
        selectPen(pens.count - 1)
        NotePenStore.save(pens)
    }

    public func removeActivePen() {
        guard pens.count > 1 else { return }
        pens.remove(at: activePen)
        selectPen(max(0, activePen - 1))
        NotePenStore.save(pens)
    }

    public func setFingerDrawing(_ on: Bool) {
        fingerDrawing = on
        NotePenStore.fingerDrawing = on
        if on { stylusSeen = false }
        message = on ? "Fingers draw" : "Only Apple Pencil draws; fingers scroll"
    }

    // MARK: - Elements

    public func element(_ id: String) -> (page: Int, element: NoteElement)? {
        for (p, page) in note.pages.enumerated() {
            if let el = page.elements.first(where: { $0.id == id }) { return (p, el) }
        }
        return nil
    }

    @discardableResult
    public func addElement(_ element: NoteElement, page: Int, edit: Bool = false) -> String {
        checkpoint()
        var el = element
        el.z = (note.pages[page].elements.map(\.z).max() ?? 0) + 1
        note.pages[page].elements.append(el)
        commitPages()
        if edit { editingElementId = el.id }
        return el.id
    }

    public func addTextBox(page: Int, at point: CGPoint) {
        let el = NoteElement(kind: .TEXT, x: Float(min(point.x, NoteLayout.pageSize.width - 220)), y: Float(point.y - 12),
                             width: 220, height: 44, content: "")
        addElement(el, page: page, edit: true)
    }

    public func addStickyNote(page: Int, at point: CGPoint? = nil) {
        let p = point ?? CGPoint(x: NoteLayout.pageSize.width / 2 - 90, y: 120)
        let el = NoteElement(kind: .STICKY, x: Float(p.x), y: Float(p.y), width: 180, height: 150, content: "", fillColor: stickyColor)
        addElement(el, page: page, edit: true)
    }

    public func addTape(page: Int, rect: CGRect) {
        let el = NoteElement(kind: .TAPE, x: Float(rect.minX), y: Float(rect.minY), width: Float(max(rect.width, 24)),
                             height: Float(max(rect.height, 14)), content: "", fillColor: tapeColor)
        addElement(el, page: page)
    }

    public func addImage(_ image: UIImage, page: Int) {
        let url = storage.documentsDirectory.appendingPathComponent("Photos").appendingPathComponent("\(UUID().uuidString).jpg")
        guard let data = image.jpegData(compressionQuality: 0.88), (try? data.write(to: url)) != nil else {
            message = "Couldn't add the image"
            return
        }
        let maxW: CGFloat = 320
        let w = min(maxW, image.size.width)
        let h = w * image.size.height / max(image.size.width, 1)
        let el = NoteElement(kind: .IMAGE, x: Float((NoteLayout.pageSize.width - w) / 2), y: 100, width: Float(w), height: Float(h), imagePath: url.path)
        let id = addElement(el, page: page)
        selection = NoteSelection(pageIndex: page, strokeIndices: [], elementIds: [id])
        tool = .lasso
    }

    /// Live update while dragging or typing; `checkpoint()` once before a gesture starts.
    public func updateElement(_ id: String, commit: Bool = true, _ change: (inout NoteElement) -> Void) {
        guard let (p, _) = element(id), let i = note.pages[p].elements.firstIndex(where: { $0.id == id }) else { return }
        change(&note.pages[p].elements[i])
        if commit { commitPages() }
    }

    public func finishEditing() {
        guard let id = editingElementId else { return }
        editingElementId = nil
        // Empty text boxes disappear; empty sticky notes stay so they can be written on later
        if let (_, el) = element(id), el.kind == .TEXT, el.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            removeElements([id], record: false)
        }
        commitPages()
    }

    public func toggleTape(_ id: String) {
        if revealedTapes.contains(id) { revealedTapes.remove(id) } else { revealedTapes.insert(id) }
    }

    public func revealAllTapes(_ reveal: Bool) {
        revealedTapes = reveal ? Set(note.pages.flatMap { $0.elements.filter { $0.kind == .TAPE }.map(\.id) }) : []
    }

    private func removeElements(_ ids: Set<String>, record: Bool = true) {
        if record { checkpoint() }
        for p in note.pages.indices { note.pages[p].elements.removeAll { ids.contains($0.id) } }
        commitPages()
    }

    // MARK: - Selection

    public func select(element id: String) {
        guard let (p, _) = element(id) else { return }
        selection = NoteSelection(pageIndex: p, strokeIndices: [], elementIds: [id])
    }

    /// Lasso: strokes with most points inside the polygon and elements whose center is inside (document coordinates).
    public func lassoSelect(polygon raw: [CGPoint]) {
        guard raw.count > 1, let first = raw.first, let last = raw.last else { return }
        // A straight drag works as a marquee: select the rectangle it spans
        var polygon = raw
        let ring = raw + [first]
        let area = abs(zip(ring, ring.dropFirst()).reduce(0) { $0 + ($1.0.x * $1.1.y - $1.1.x * $1.0.y) }) / 2
        let box = CGRect(x: min(first.x, last.x), y: min(first.y, last.y), width: abs(last.x - first.x), height: abs(last.y - first.y))
        if raw.count < 3 || area < 0.1 * box.width * box.height {
            polygon = [CGPoint(x: box.minX, y: box.minY), CGPoint(x: box.maxX, y: box.minY),
                       CGPoint(x: box.maxX, y: box.maxY), CGPoint(x: box.minX, y: box.maxY)]
        }
        let path = UIBezierPath()
        path.move(to: polygon[0])
        polygon.dropFirst().forEach { path.addLine(to: $0) }
        path.close()
        let page = NoteLayout.pageIndex(atY: polygon.map(\.y).reduce(0, +) / CGFloat(polygon.count), pageCount: pageCount)
        var strokes = Set<Int>()
        for (i, stroke) in drawing.strokes.enumerated() {
            let pts = stroke.documentPoints
            guard !pts.isEmpty else { continue }
            let inside = pts.filter { path.contains($0) }.count
            if Double(inside) / Double(pts.count) > 0.6 { strokes.insert(i) }
        }
        let origin = NoteLayout.origin(ofPage: page)
        let elements = note.pages[page].elements.filter { el in
            path.contains(CGPoint(x: origin.x + CGFloat(el.x + el.width / 2), y: origin.y + CGFloat(el.y + el.height / 2)))
        }.map(\.id)
        let sel = NoteSelection(pageIndex: page, strokeIndices: strokes, elementIds: Set(elements))
        selection = sel.isEmpty ? nil : sel
        if sel.isEmpty { message = "Nothing selected" }
    }

    /// Selection bounds in document coordinates.
    public func selectionBounds(_ sel: NoteSelection? = nil) -> CGRect? {
        guard let sel = sel ?? selection else { return nil }
        var rects: [CGRect] = sel.strokeIndices.compactMap { drawing.strokes.indices.contains($0) ? drawing.strokes[$0].renderBounds : nil }
        let origin = NoteLayout.origin(ofPage: sel.pageIndex)
        for id in sel.elementIds {
            if let (_, el) = element(id) {
                rects.append(CGRect(x: origin.x + CGFloat(el.x), y: origin.y + CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height)))
            }
        }
        return rects.dropFirst().reduce(rects.first) { $0?.union($1) }
    }

    private var transformBase: Snapshot?

    public func beginTransform() {
        transformBase = Snapshot(drawing: drawing, pages: note.pages)
    }

    /// Moves and scales the selection from the state at `beginTransform()`. `anchor` is in document coordinates.
    public func transformSelection(translation: CGVector, scale: CGFloat, anchor: CGPoint) {
        guard let sel = selection, let base = transformBase else { return }
        let t = CGAffineTransform(translationX: -anchor.x, y: -anchor.y)
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: anchor.x + translation.dx, y: anchor.y + translation.dy))
        var strokes = base.drawing.strokes
        for i in sel.strokeIndices where strokes.indices.contains(i) {
            strokes[i].transform = strokes[i].transform.concatenating(t)
        }
        drawing = PKDrawing(strokes: strokes)
        var pages = base.pages
        let origin = NoteLayout.origin(ofPage: sel.pageIndex)
        for (i, el) in pages[sel.pageIndex].elements.enumerated() where sel.elementIds.contains(el.id) {
            let r = CGRect(x: origin.x + CGFloat(el.x), y: origin.y + CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height)).applying(t)
            pages[sel.pageIndex].elements[i].x = Float(r.minX - origin.x)
            pages[sel.pageIndex].elements[i].y = Float(r.minY - origin.y)
            pages[sel.pageIndex].elements[i].width = Float(max(r.width, 16))
            pages[sel.pageIndex].elements[i].height = Float(max(r.height, 12))
        }
        note.pages = pages
        drawingRevision += 1
    }

    public func endTransform() {
        guard let base = transformBase else { return }
        transformBase = nil
        if base.drawing != drawing || base.pages != note.pages {
            push(base)
            commitPages()
        }
    }

    public func deleteSelection() {
        guard let sel = selection else { return }
        checkpoint()
        drawing = PKDrawing(strokes: drawing.strokes.enumerated().filter { !sel.strokeIndices.contains($0.offset) }.map(\.element))
        for p in note.pages.indices { note.pages[p].elements.removeAll { sel.elementIds.contains($0.id) } }
        selection = nil
        drawingRevision += 1
        commitPages()
    }

    public func recolorSelection(_ hex: String) {
        guard let sel = selection else { return }
        checkpoint()
        let color = UIColor(Color(hex: hex))
        var strokes = drawing.strokes
        for i in sel.strokeIndices where strokes.indices.contains(i) {
            let ink = strokes[i].ink
            strokes[i].ink = PKInk(ink.inkType, color: ink.inkType == .marker ? color.withAlphaComponent(ink.color.cgColor.alpha) : color)
        }
        drawing = PKDrawing(strokes: strokes)
        for p in note.pages.indices {
            for i in note.pages[p].elements.indices where sel.elementIds.contains(note.pages[p].elements[i].id) {
                if note.pages[p].elements[i].kind != .IMAGE { note.pages[p].elements[i].fillColor = hex }
            }
        }
        drawingRevision += 1
        commitPages()
    }

    public func duplicateSelection() {
        guard let sel = selection else { return }
        checkpoint()
        let offset = CGAffineTransform(translationX: 24, y: 24)
        var strokes = drawing.strokes
        var newIndices = Set<Int>()
        for i in sel.strokeIndices.sorted() where strokes.indices.contains(i) {
            var copy = strokes[i]
            copy.transform = copy.transform.concatenating(offset)
            strokes.append(copy)
            newIndices.insert(strokes.count - 1)
        }
        drawing = PKDrawing(strokes: strokes)
        var newIds = Set<String>()
        let maxZ = note.pages[sel.pageIndex].elements.map(\.z).max() ?? 0
        for (n, el) in note.pages[sel.pageIndex].elements.filter({ sel.elementIds.contains($0.id) }).enumerated() {
            var copy = NoteElement(kind: el.kind, x: el.x + 24, y: el.y + 24, width: el.width, height: el.height, rotation: el.rotation,
                                   z: maxZ + 1 + n, content: el.content, imagePath: el.imagePath, quoteText: el.quoteText,
                                   sourceBookId: el.sourceBookId, sourcePage: el.sourcePage, fillColor: el.fillColor)
            copy.z = maxZ + 1 + n
            note.pages[sel.pageIndex].elements.append(copy)
            newIds.insert(copy.id)
        }
        selection = NoteSelection(pageIndex: sel.pageIndex, strokeIndices: newIndices, elementIds: newIds)
        drawingRevision += 1
        commitPages()
    }

    public func bringSelectionToFront() {
        guard let sel = selection else { return }
        checkpoint()
        var strokes = drawing.strokes
        let moving = sel.strokeIndices.sorted().compactMap { strokes.indices.contains($0) ? strokes[$0] : nil }
        strokes = strokes.enumerated().filter { !sel.strokeIndices.contains($0.offset) }.map(\.element) + moving
        drawing = PKDrawing(strokes: strokes)
        let maxZ = note.pages[sel.pageIndex].elements.map(\.z).max() ?? 0
        for i in note.pages[sel.pageIndex].elements.indices where sel.elementIds.contains(note.pages[sel.pageIndex].elements[i].id) {
            note.pages[sel.pageIndex].elements[i].z = maxZ + 1
        }
        let start = strokes.count - moving.count
        selection = NoteSelection(pageIndex: sel.pageIndex, strokeIndices: Set(start..<strokes.count), elementIds: sel.elementIds)
        drawingRevision += 1
        commitPages()
    }

    public func copySelection() {
        guard let sel = selection else { return }
        let origin = NoteLayout.origin(ofPage: sel.pageIndex)
        let back = CGAffineTransform(translationX: -origin.x, y: -origin.y)
        let strokes = sel.strokeIndices.sorted().compactMap { i -> PKStroke? in
            guard drawing.strokes.indices.contains(i) else { return nil }
            var s = drawing.strokes[i]
            s.transform = s.transform.concatenating(back)
            return s
        }
        let elements = note.pages[sel.pageIndex].elements.filter { sel.elementIds.contains($0.id) }
        clipboard = NoteClipboard(strokes: strokes, elements: elements)
        hasClipboard = true
        message = "Copied"
    }

    public func cutSelection() {
        copySelection()
        deleteSelection()
        message = "Cut"
    }

    public func paste(page: Int) {
        guard let clip = clipboard else { return }
        checkpoint()
        let origin = NoteLayout.origin(ofPage: page)
        let shift = CGAffineTransform(translationX: origin.x + 16, y: origin.y + 16)
        var strokes = drawing.strokes
        var newIndices = Set<Int>()
        for s in clip.strokes {
            var copy = s
            copy.transform = copy.transform.concatenating(shift)
            strokes.append(copy)
            newIndices.insert(strokes.count - 1)
        }
        drawing = PKDrawing(strokes: strokes)
        var ids = Set<String>()
        let maxZ = note.pages[page].elements.map(\.z).max() ?? 0
        for (n, el) in clip.elements.enumerated() {
            let copy = NoteElement(kind: el.kind, x: el.x + 16, y: el.y + 16, width: el.width, height: el.height, rotation: el.rotation,
                                   z: maxZ + 1 + n, content: el.content, imagePath: el.imagePath, quoteText: el.quoteText,
                                   sourceBookId: el.sourceBookId, sourcePage: el.sourcePage, fillColor: el.fillColor)
            note.pages[page].elements.append(copy)
            ids.insert(copy.id)
        }
        selection = NoteSelection(pageIndex: page, strokeIndices: newIndices, elementIds: ids)
        tool = .lasso
        drawingRevision += 1
        commitPages()
        message = "Pasted"
    }

    // MARK: - Erasing highlighter ink only

    private var highlighterEraseBase: PKDrawing?

    public func beginHighlighterErase() { highlighterEraseBase = drawing }

    /// Erases highlighter ink along the finger's path, so fast swipes do not skip strokes.
    public func eraseHighlighter(from a: CGPoint, to b: CGPoint, radius: CGFloat) {
        let steps = max(1, Int(ceil(hypot(b.x - a.x, b.y - a.y) / max(radius / 2, 1))))
        for i in 0...steps {
            let t = CGFloat(i) / CGFloat(steps)
            eraseHighlighter(at: CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t), radius: radius)
        }
    }

    /// Removes highlighter (marker) strokes within `radius` of `point` (document coordinates).
    public func eraseHighlighter(at point: CGPoint, radius: CGFloat) {
        let kept = drawing.strokes.filter { stroke in
            guard stroke.ink.inkType == .marker, stroke.renderBounds.insetBy(dx: -radius, dy: -radius).contains(point) else { return true }
            let pts = stroke.documentPoints
            let reach = radius + (stroke.path.first?.size.width ?? 0) / 2
            if pts.count == 1 { return hypot(pts[0].x - point.x, pts[0].y - point.y) > reach }
            for i in 1..<pts.count where ShapeRecognizer.distance(point, toSegment: pts[i - 1], pts[i]) <= reach { return false }
            return true
        }
        if kept.count != drawing.strokes.count {
            drawing = PKDrawing(strokes: kept)
            drawingRevision += 1
        }
    }

    public func endHighlighterErase() {
        guard let base = highlighterEraseBase else { return }
        highlighterEraseBase = nil
        if base != drawing {
            push(Snapshot(drawing: base, pages: note.pages))
            scheduleSave()
        }
    }

    public func clearPageInk(_ page: Int) {
        let frame = NoteLayout.frame(ofPage: page).insetBy(dx: -NoteLayout.gap / 2, dy: -NoteLayout.gap / 2)
        let kept = drawing.strokes.filter { NoteLayout.pageIndex(atY: $0.renderBounds.midY, pageCount: pageCount) != page || !frame.intersects($0.renderBounds) }
        guard kept.count != drawing.strokes.count else {
            message = "No ink on this page"
            return
        }
        checkpoint()
        drawing = PKDrawing(strokes: kept)
        drawingRevision += 1
        commitPages()
    }

    // MARK: - Pages

    public func addPage(after index: Int, background: PageBackground? = nil, theme: PaperTheme? = nil) {
        checkpoint()
        var inks = NoteInkStore.split(drawing, pageCount: pageCount)
        let source = note.pages[min(max(index, 0), pageCount - 1)]
        let page = NotePage(noteId: note.id, background: background ?? source.background, paperTheme: theme ?? source.paperTheme)
        note.pages.insert(page, at: index + 1)
        inks.insert(PKDrawing(), at: index + 1)
        relayout(inks)
        goToPage(index + 1)
    }

    public func duplicatePage(_ index: Int) {
        checkpoint()
        var inks = NoteInkStore.split(drawing, pageCount: pageCount)
        let src = note.pages[index]
        var copy = NotePage(noteId: note.id, background: src.background, paperTheme: src.paperTheme)
        copy.elements = src.elements.map {
            NoteElement(kind: $0.kind, x: $0.x, y: $0.y, width: $0.width, height: $0.height, rotation: $0.rotation, z: $0.z,
                        content: $0.content, imagePath: $0.imagePath, quoteText: $0.quoteText, sourceBookId: $0.sourceBookId,
                        sourcePage: $0.sourcePage, fillColor: $0.fillColor)
        }
        note.pages.insert(copy, at: index + 1)
        inks.insert(inks[index], at: index + 1)
        relayout(inks)
        goToPage(index + 1)
    }

    public func deletePage(_ index: Int) {
        guard pageCount > 1 else {
            message = "A note needs at least one page"
            return
        }
        checkpoint()
        var inks = NoteInkStore.split(drawing, pageCount: pageCount)
        note.pages.remove(at: index)
        inks.remove(at: index)
        relayout(inks)
        currentPage = min(currentPage, pageCount - 1)
    }

    public func movePage(from: Int, to: Int) {
        guard from != to, note.pages.indices.contains(from), note.pages.indices.contains(to) else { return }
        checkpoint()
        var inks = NoteInkStore.split(drawing, pageCount: pageCount)
        note.pages.insert(note.pages.remove(at: from), at: to)
        inks.insert(inks.remove(at: from), at: to)
        relayout(inks)
        goToPage(to)
    }

    public func setBackground(_ background: PageBackground, theme: PaperTheme, page: Int, allPages: Bool) {
        checkpoint()
        for i in note.pages.indices where allPages || i == page {
            note.pages[i].background = background
            note.pages[i].paperTheme = theme
        }
        commitPages()
    }

    public func rename(_ title: String) {
        note.title = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Untitled Note" : title
        scheduleSave()
    }

    /// Ink of one page, in page points (for thumbnails and export).
    public func pageInk(_ index: Int) -> PKDrawing {
        let inks = NoteInkStore.split(drawing, pageCount: pageCount)
        return inks.indices.contains(index) ? inks[index] : PKDrawing()
    }

    private func relayout(_ inks: [PKDrawing]) {
        for i in note.pages.indices { note.pages[i].pageIndex = i }
        drawing = NoteInkStore.join(inks)
        selection = nil
        drawingRevision += 1
        commitPages()
    }

    // MARK: - Undo / redo

    public func checkpoint() { push(Snapshot(drawing: drawing, pages: note.pages)) }

    private func push(_ snapshot: Snapshot) {
        undoStack.append(snapshot)
        if undoStack.count > 100 { undoStack.removeFirst() }
        redoStack.removeAll()
        refreshHistory()
    }

    public func undo() {
        guard let s = undoStack.popLast() else { return }
        redoStack.append(Snapshot(drawing: drawing, pages: note.pages))
        restore(s)
    }

    public func redo() {
        guard let s = redoStack.popLast() else { return }
        undoStack.append(Snapshot(drawing: drawing, pages: note.pages))
        restore(s)
    }

    private func restore(_ s: Snapshot) {
        drawing = s.drawing
        note.pages = s.pages
        currentPage = min(currentPage, pageCount - 1)
        selection = nil
        editingElementId = nil
        drawingRevision += 1
        refreshHistory()
        commitPages()
    }

    private func refreshHistory() {
        canUndo = !undoStack.isEmpty
        canRedo = !redoStack.isEmpty
    }

    // MARK: - Canvas hooks

    /// Applies a text format to the text being edited (set by the canvas).
    var formatHandler: ((NoteTextFormat) -> Void)?
    /// A page's frame in window coordinates (set by the canvas; used by UI tests).
    var pageFrameProvider: ((Int) -> CGRect)?
    /// Current canvas zoom relative to fit-to-width (set by the canvas; used by UI tests).
    var zoomProvider: (() -> CGFloat)?

    /// Counts, tool and geometry for UI tests.
    func testStateDescription() -> String {
        let elements = note.pages.flatMap(\.elements)
        func count(_ kind: ElementKind) -> Int { elements.filter { $0.kind == kind }.count }
        let markers = drawing.strokes.filter { $0.ink.inkType == .marker }.count
        let frame = pageFrameProvider?(currentPage) ?? .zero
        let editingText = editingElementId.flatMap { element($0)?.element.content } ?? ""
        return "pages=\(pageCount);strokes=\(drawing.strokes.count);markers=\(markers);text=\(count(.TEXT));sticky=\(count(.STICKY));"
            + "image=\(count(.IMAGE));quote=\(count(.QUOTE));tape=\(count(.TAPE));revealed=\(revealedTapes.count);"
            + "tool=\(tool.rawValue);selected=\((selection?.strokeIndices.count ?? 0) + (selection?.elementIds.count ?? 0));"
            + "editing=\(editingElementId != nil);undo=\(canUndo);redo=\(canRedo);page=\(currentPage);"
            + "pen=\(pen.type.rawValue);title=\(note.title);editingText=\(editingText.replacingOccurrences(of: ";", with: ","));"
            + String(format: "zoom=%.2f;frame=%.1f,%.1f,%.1f,%.1f", zoomProvider?() ?? 1, frame.minX, frame.minY, frame.width, frame.height)
    }

    // MARK: - Saving

    private func commitPages() {
        objectWillChange.send()
        scheduleSave()
    }

    private func scheduleSave() {
        saveWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.saveNow() }
        saveWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8, execute: work)
    }

    /// Writes ink per page and the note itself.
    public func saveNow() {
        saveWork?.cancel()
        saveWork = nil
        let inks = NoteInkStore.split(drawing, pageCount: pageCount)
        for i in note.pages.indices {
            note.pages[i].drawingFileName = NoteInkStore.save(inks[i], note: note, page: note.pages[i], storage: storage)
        }
        note.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        if let idx = storage.notes.firstIndex(where: { $0.id == note.id }) {
            storage.notes[idx] = note
        } else {
            storage.notes.append(note)
        }
        storage.saveNotes()
        onChange(note)
    }
}
