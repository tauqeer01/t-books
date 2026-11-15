import SwiftUI
import PDFKit

/// Text picked with the Select text tool or a long press, ready for the selection actions.
public struct ReaderTextSelection: Equatable {
    public var pageIndex: Int
    public var text: String
    /// One rectangle per text line, in page points (top-left origin).
    public var lines: [CGRect]
}

/// Owns the reader's annotations: creating, erasing, undo/redo, persistence, and mirroring
/// every `BookAnnotation` onto the open `PDFDocument` as native PDF annotations so they
/// zoom and scroll with the page and are included when the document is exported.
@MainActor
public final class ReaderAnnotationController: ObservableObject {
    public let bookId: String
    private let storage: StorageManager
    private weak var document: PDFDocument?

    // Tool state
    @Published public var isDrawingMode = false
    @Published public private(set) var tool: ReaderTool = .pen
    @Published public var colorHex: String = "#4F46E5"
    @Published public var strokeWidth: Double = 2.5
    @Published public var isStylusOnly = false

    // History
    @Published public private(set) var canUndo = false
    @Published public private(set) var canRedo = false

    // UI requests
    @Published public var toast: String?
    /// Sticky note (or annotation note) being edited.
    @Published public var editingNote: BookAnnotation?
    /// Annotation tapped in reading mode; shows its menu.
    @Published public var tappedAnnotation: BookAnnotation?
    /// Current text selection; shows the selection actions.
    @Published public var textSelection: ReaderTextSelection?

    private var history = AnnotationHistory()
    private var rendered: [String: [PDFAnnotation]] = [:]
    private var newStickyId: String?
    private var erasedInGesture: [BookAnnotation] = []
    // Last style picked for ink tools and for translucent tools, kept across tool switches (as on Android)
    private var inkStyle: (String, Double) = ("#4F46E5", 2.5)
    private var highlighterStyle: (String, Double) = ("#FDE047", 6)

    public static let inkPalette = ["#111827", "#4B5563", "#DC2626", "#EA580C", "#CA8A04", "#16A34A",
                                    "#0891B2", "#2563EB", "#7C3AED", "#DB2777", "#4F46E5", "#5935FF"]
    public static let highlighterPalette = ["#FDE047", "#86EFAC", "#7DD3FC", "#F472B6", "#C084FC",
                                            "#FDBA74", "#4ADE80", "#60A5FA", "#FEF9C3", "#DCFCE7"]

    public init(bookId: String, storage: StorageManager = .shared) {
        self.bookId = bookId
        self.storage = storage
    }

    /// The tool that receives touches: scrolling unless the annotation rail is open.
    public var activeTool: ReaderTool { isDrawingMode ? tool : .hand }
    public var palette: [String] { tool.usesHighlighterPalette ? Self.highlighterPalette : Self.inkPalette }
    public var widthRange: ClosedRange<Double> { tool == .highlighter ? 2...14 : 0.5...12 }

    public var bookAnnotations: [BookAnnotation] {
        storage.annotations.filter { $0.bookId == bookId }
    }

    public func annotations(onPage pageIndex: Int) -> [BookAnnotation] {
        storage.annotations.filter { $0.bookId == bookId && $0.pageIndex == pageIndex }
    }

    // MARK: - Document

    public func attach(_ document: PDFDocument) {
        guard self.document !== document else { return }
        self.document = document
        rendered.removeAll()
        bookAnnotations.forEach(render)
    }

    public static func pageSpace(of page: PDFPage) -> PageSpace {
        PageSpace(size: page.bounds(for: .cropBox).size)
    }

    // MARK: - Tool selection

    public func select(_ newTool: ReaderTool, width: Double? = nil) {
        tool = newTool
        if newTool.hasColor {
            let style = newTool.usesHighlighterPalette ? highlighterStyle : inkStyle
            colorHex = style.0
            strokeWidth = width ?? style.1
            if width != nil { rememberStyle() }
        }
    }

    public func setColor(_ hex: String) {
        colorHex = hex
        rememberStyle()
    }

    public func setWidth(_ width: Double) {
        strokeWidth = width
        rememberStyle()
    }

    private func rememberStyle() {
        guard tool.hasColor else { return }
        if tool.usesHighlighterPalette { highlighterStyle = (colorHex, strokeWidth) } else { inkStyle = (colorHex, strokeWidth) }
    }

    // MARK: - Creating annotations

    /// Finishes a pen or highlighter stroke. `points` are page points.
    public func commitStroke(pageIndex: Int, points: [CGPoint], space: PageSpace) {
        guard !points.isEmpty else { return }
        let payload = StrokePayload(points: points.map(space.normalize), width: strokeWidth,
                                    isHighlighter: tool == .highlighter)
        add([strokeAnnotation(pageIndex: pageIndex, payload: payload, space: space, points: points)])
    }

    /// Finishes a line, arrow, rectangle or ellipse dragged from `start` to `end` (page points).
    public func commitShape(pageIndex: Int, start: CGPoint, end: CGPoint, space: PageSpace, arrowHead: ClosedRange<CGFloat>) {
        let outline = ShapeGeometry.points(tool, from: start, to: end, arrowHead: arrowHead)
        guard outline.count >= 2 else { return }
        let payload = StrokePayload(points: outline.map(space.normalize), width: strokeWidth, isHighlighter: false)
        add([strokeAnnotation(pageIndex: pageIndex, payload: payload, space: space, points: outline)])
    }

    private func strokeAnnotation(pageIndex: Int, payload: StrokePayload, space: PageSpace, points: [CGPoint]) -> BookAnnotation {
        let xs = points.map(\.x), ys = points.map(\.y)
        let bounds = space.normalize(CGRect(x: xs.min()!, y: ys.min()!, width: xs.max()! - xs.min()!, height: ys.max()! - ys.min()!))
        return BookAnnotation(bookId: bookId, pageIndex: pageIndex, type: .DRAWING, colorHex: colorHex,
                              rectLeft: Float(bounds.l), rectTop: Float(bounds.t),
                              rectRight: Float(bounds.r), rectBottom: Float(bounds.b),
                              strokePathData: AnnotationCodec.encode(payload))
    }

    /// Highlights, underlines or strikes the selected lines. Returns false when there was no text.
    @discardableResult
    public func commitMarkup(_ selection: ReaderTextSelection?, type: AnnotationType, colorHex: String? = nil,
                             note: String = "", space: PageSpace) -> Bool {
        guard let selection, !selection.lines.isEmpty else {
            toast = "No text there to mark"
            return false
        }
        let union = selection.lines.dropFirst().reduce(selection.lines[0]) { $0.union($1) }
        let u = space.normalize(union)
        add([BookAnnotation(bookId: bookId, pageIndex: selection.pageIndex, type: type,
                            colorHex: colorHex ?? self.colorHex, selectedText: selection.text, noteContent: note,
                            rectLeft: Float(u.l), rectTop: Float(u.t), rectRight: Float(u.r), rectBottom: Float(u.b),
                            strokePathData: AnnotationCodec.encode(selection.lines.map(space.normalize)))])
        return true
    }

    /// Drops a sticky note at a page point and opens the note editor for it.
    public func placeStickyNote(pageIndex: Int, at point: CGPoint, space: PageSpace) {
        let half = CGSize(width: space.size.width * 0.025, height: space.size.height * 0.018)
        let r = space.normalize(CGRect(x: point.x - half.width, y: point.y - half.height,
                                       width: half.width * 2, height: half.height * 2))
        let note = BookAnnotation(bookId: bookId, pageIndex: pageIndex, type: .NOTE, colorHex: colorHex,
                                  selectedText: "Sticky note", rectLeft: Float(r.l), rectTop: Float(r.t),
                                  rectRight: Float(r.r), rectBottom: Float(r.b))
        newStickyId = note.id
        add([note])
        editingNote = note
    }

    // MARK: - Eraser

    public func beginErase() { erasedInGesture.removeAll() }

    /// Removes every stroke or markup on the page within `radius` page points of `point`.
    public func erase(pageIndex: Int, at point: CGPoint, radius: CGFloat, space: PageSpace) {
        let hits = annotations(onPage: pageIndex).filter { AnnotationHitTest.hits($0, at: point, space: space, radius: radius) }
        guard !hits.isEmpty else { return }
        erasedInGesture += hits
        apply(.remove(hits))
    }

    /// Records the whole eraser drag as one undoable step.
    public func endErase() {
        guard !erasedInGesture.isEmpty else { return }
        history.record(.remove(erasedInGesture))
        erasedInGesture.removeAll()
        refreshHistory()
    }

    // MARK: - Editing

    public func delete(_ annotation: BookAnnotation) {
        guard let current = storage.annotations.first(where: { $0.id == annotation.id }) else { return }
        apply(.remove([current]))
        history.record(.remove([current]))
        refreshHistory()
        if tappedAnnotation?.id == annotation.id { tappedAnnotation = nil }
    }

    public func updateColor(_ annotation: BookAnnotation, to hex: String) {
        update(annotation.id) { $0.colorHex = hex }
    }

    public func saveNote(_ annotation: BookAnnotation, text: String) {
        update(annotation.id) { $0.noteContent = text }
        if annotation.id == newStickyId { newStickyId = nil }
        editingNote = nil
        toast = "Note saved"
    }

    /// Closing the editor on a new, empty sticky note discards it (as on Android).
    public func cancelNoteEditing() {
        if let editing = editingNote, editing.id == newStickyId,
           (storage.annotations.first { $0.id == editing.id }?.noteContent ?? "").isEmpty {
            apply(.remove([editing]))
            history.forget(id: editing.id)
            refreshHistory()
        }
        newStickyId = nil
        editingNote = nil
    }

    private func update(_ id: String, _ change: (inout BookAnnotation) -> Void) {
        guard let i = storage.annotations.firstIndex(where: { $0.id == id }) else { return }
        unrender(id)
        change(&storage.annotations[i])
        storage.annotations[i].updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        render(storage.annotations[i])
        if tappedAnnotation?.id == id { tappedAnnotation = storage.annotations[i] }
        storage.saveAnnotations()
        objectWillChange.send()
    }

    // MARK: - Undo / redo / clear

    public func undo() {
        guard let change = history.undo() else { return }
        apply(change)
        refreshHistory()
    }

    public func redo() {
        guard let change = history.redo() else { return }
        apply(change)
        refreshHistory()
    }

    /// Removes the pen, highlighter and shape drawings on one page.
    public func clearDrawings(onPage pageIndex: Int) {
        let strokes = annotations(onPage: pageIndex).filter { $0.type == .DRAWING }
        guard !strokes.isEmpty else {
            toast = "No drawings on this page"
            return
        }
        apply(.remove(strokes))
        history.record(.remove(strokes))
        refreshHistory()
        toast = "Page drawings cleared"
    }

    // MARK: - Hit testing

    public func annotation(at point: CGPoint, pageIndex: Int, space: PageSpace, radius: CGFloat) -> BookAnnotation? {
        AnnotationHitTest.annotation(at: point, in: annotations(onPage: pageIndex), space: space, radius: radius)
    }

    // MARK: - UI test support

    /// The reader's PDF view, for the UI-test state probe.
    weak var pdfView: PDFView?

    /// Annotation counts, tool, page and the current page's frame on screen, for UI tests.
    func testStateDescription() -> String {
        let mine = bookAnnotations
        func count(_ types: [AnnotationType]) -> Int { mine.filter { types.contains($0.type) }.count }
        var frame = "0,0,0,0"
        var page = -1
        if let pdfView, let current = pdfView.currentPage, let doc = pdfView.document {
            page = doc.index(for: current)
            let r = pdfView.convert(pdfView.convert(current.bounds(for: .cropBox), from: current), to: nil)
            frame = String(format: "%.1f,%.1f,%.1f,%.1f", r.minX, r.minY, r.width, r.height)
        }
        return "annotations=\(mine.count);drawings=\(count([.DRAWING]));marks=\(count([.HIGHLIGHT, .UNDERLINE, .STRIKETHROUGH]));"
            + "highlights=\(count([.HIGHLIGHT]));underlines=\(count([.UNDERLINE]));strikes=\(count([.STRIKETHROUGH]));"
            + "notes=\(count([.NOTE]));page=\(page);tool=\(activeTool.rawValue);selection=\(textSelection != nil);"
            + "undo=\(canUndo);redo=\(canRedo);frame=\(frame)"
    }

    // MARK: - Internals

    private func add(_ annotations: [BookAnnotation]) {
        apply(.add(annotations))
        history.record(.add(annotations))
        refreshHistory()
    }

    private func apply(_ change: AnnotationChange) {
        switch change {
        case .add(let list):
            for ann in list where !storage.annotations.contains(where: { $0.id == ann.id }) {
                storage.annotations.append(ann)
                render(ann)
            }
        case .remove(let list):
            let ids = Set(list.map(\.id))
            storage.annotations.removeAll { ids.contains($0.id) }
            ids.forEach(unrender)
        }
        storage.saveAnnotations()
    }

    private func refreshHistory() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    private func render(_ ann: BookAnnotation) {
        guard let page = document?.page(at: ann.pageIndex) else { return }
        let made = PDFAnnotationFactory.make(ann, box: page.bounds(for: .cropBox))
        made.forEach(page.addAnnotation)
        rendered[ann.id] = made
    }

    private func unrender(_ id: String) {
        for pdfAnnotation in rendered.removeValue(forKey: id) ?? [] {
            pdfAnnotation.page?.removeAnnotation(pdfAnnotation)
        }
    }
}

/// Builds the native PDF annotations that draw a `BookAnnotation`.
public enum PDFAnnotationFactory {
    public static func make(_ ann: BookAnnotation, box: CGRect) -> [PDFAnnotation] {
        let space = PageSpace(size: box.size)
        // Page points (top-left origin) to PDF page space (bottom-left origin, offset by the crop box)
        func pdf(_ p: CGPoint) -> CGPoint { CGPoint(x: box.minX + p.x, y: box.maxY - p.y) }
        func pdf(_ r: CGRect) -> CGRect { CGRect(x: box.minX + r.minX, y: box.maxY - r.maxY, width: r.width, height: r.height) }
        let color = UIColor(Color(hex: ann.colorHex))

        switch ann.type {
        case .DRAWING:
            guard let stroke = AnnotationCodec.decodeStroke(ann.strokePathData), !stroke.points.isEmpty else { return [] }
            let width = space.lineWidth(stroke.width, highlighter: stroke.isHighlighter)
            let pts = stroke.points.map { pdf(space.denormalize($0)) }
            let xs = pts.map(\.x), ys = pts.map(\.y)
            let bounds = CGRect(x: xs.min()!, y: ys.min()!, width: xs.max()! - xs.min()!, height: ys.max()! - ys.min()!)
                .insetBy(dx: -width, dy: -width)
            let path = UIBezierPath()
            path.move(to: CGPoint(x: pts[0].x - bounds.minX, y: pts[0].y - bounds.minY))
            if pts.count == 1 {
                path.addLine(to: CGPoint(x: pts[0].x - bounds.minX + 0.01, y: pts[0].y - bounds.minY))
            }
            for p in pts.dropFirst() { path.addLine(to: CGPoint(x: p.x - bounds.minX, y: p.y - bounds.minY)) }
            let ink = PDFAnnotation(bounds: bounds, forType: .ink, withProperties: nil)
            ink.add(path)
            let border = PDFBorder()
            border.lineWidth = width
            ink.border = border
            ink.color = stroke.isHighlighter ? color.withAlphaComponent(0.42) : color
            return [ink]

        case .HIGHLIGHT, .UNDERLINE, .STRIKETHROUGH:
            let subtype: PDFAnnotationSubtype = ann.type == .HIGHLIGHT ? .highlight : (ann.type == .UNDERLINE ? .underline : .strikeOut)
            return ann.normRects.map { rect in
                let mark = PDFAnnotation(bounds: pdf(space.denormalize(rect)), forType: subtype, withProperties: nil)
                mark.color = ann.type == .HIGHLIGHT ? color.withAlphaComponent(0.5) : color
                if !ann.noteContent.isEmpty { mark.contents = ann.noteContent }
                return mark
            }

        case .NOTE:
            let r = space.denormalize(ann.normRects[0])
            let side = max(min(box.width * 0.05, 26), 14)
            let center = pdf(CGPoint(x: r.midX, y: r.midY))
            let note = PDFAnnotation(bounds: CGRect(x: center.x - side / 2, y: center.y - side / 2, width: side, height: side),
                                     forType: .text, withProperties: nil)
            note.iconType = .note
            note.color = color
            note.contents = ann.noteContent
            return [note]
        }
    }
}
