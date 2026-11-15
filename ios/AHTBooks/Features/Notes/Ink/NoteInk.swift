import SwiftUI
import PencilKit

/// Notes use fixed A4 pages. Ink and elements are stored in page points (595.2 × 841.8), so a
/// note looks identical on iPhone, iPad, in any orientation and in exports. In the editor the
/// pages are stacked vertically in "document" coordinates with a gap between them.
public enum NoteLayout {
    public static let pageSize = CGSize(width: NoteExporter.a4Width, height: NoteExporter.a4Height)
    public static let gap: CGFloat = 28

    public static func origin(ofPage index: Int) -> CGPoint {
        CGPoint(x: gap, y: gap + CGFloat(index) * (pageSize.height + gap))
    }

    public static func frame(ofPage index: Int) -> CGRect {
        CGRect(origin: origin(ofPage: index), size: pageSize)
    }

    public static func contentSize(pageCount: Int) -> CGSize {
        CGSize(width: pageSize.width + gap * 2, height: gap + CGFloat(max(pageCount, 1)) * (pageSize.height + gap))
    }

    /// The page under a document y coordinate (clamped to existing pages).
    public static func pageIndex(atY y: CGFloat, pageCount: Int) -> Int {
        let raw = Int(floor((y - gap / 2) / (pageSize.height + gap)))
        return min(max(raw, 0), max(pageCount - 1, 0))
    }
}

// MARK: - Pens

public enum NotePenType: String, Codable, CaseIterable, Identifiable {
    case pen, fountain, pencil, marker, monoline, crayon, watercolor, highlighter

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .pen: return "Pen"
        case .fountain: return "Fountain pen"
        case .pencil: return "Pencil"
        case .marker: return "Marker"
        case .monoline: return "Fineliner"
        case .crayon: return "Crayon"
        case .watercolor: return "Watercolor"
        case .highlighter: return "Highlighter"
        }
    }

    public var icon: String {
        switch self {
        case .pen: return "pencil.tip"
        case .fountain: return "pencil.and.scribble"
        case .pencil: return "pencil"
        case .marker: return "paintbrush.pointed"
        case .monoline: return "scribble"
        case .crayon: return "pencil.line"
        case .watercolor: return "paintbrush"
        case .highlighter: return "highlighter"
        }
    }

    public var inkType: PKInk.InkType {
        switch self {
        case .pen: return .pen
        case .fountain: return .fountainPen
        case .pencil: return .pencil
        case .marker, .highlighter: return .marker
        case .monoline: return .monoline
        case .crayon: return .crayon
        case .watercolor: return .watercolor
        }
    }

    public var widthRange: ClosedRange<CGFloat> {
        switch self {
        case .highlighter: return 8...40
        case .marker: return 2...30
        case .watercolor, .crayon: return 4...40
        default: return 0.5...16
        }
    }

    public var isTranslucent: Bool { self == .highlighter }
}

public struct NotePen: Codable, Equatable, Identifiable {
    public var id: String = UUID().uuidString
    public var type: NotePenType
    public var colorHex: String
    public var width: CGFloat

    public var color: UIColor {
        let base = UIColor(Color(hex: colorHex))
        return type.isTranslucent ? base.withAlphaComponent(0.45) : base
    }

    public var tool: PKInkingTool { PKInkingTool(type.inkType, color: color, width: width) }

    public static let defaults: [NotePen] = [
        NotePen(type: .pen, colorHex: "#1F2937", width: 2),
        NotePen(type: .pen, colorHex: "#2563EB", width: 2),
        NotePen(type: .fountain, colorHex: "#111827", width: 3),
        NotePen(type: .fountain, colorHex: "#DC2626", width: 3),
        NotePen(type: .pencil, colorHex: "#4B5563", width: 2.5),
        NotePen(type: .marker, colorHex: "#16A34A", width: 8),
        NotePen(type: .highlighter, colorHex: "#FDE047", width: 20),
        NotePen(type: .highlighter, colorHex: "#86EFAC", width: 20)
    ]

    public static let palette = ["#111827", "#4B5563", "#DC2626", "#EA580C", "#CA8A04", "#16A34A",
                                 "#0891B2", "#2563EB", "#7C3AED", "#DB2777", "#FDE047", "#86EFAC", "#93C5FD", "#F9A8D4"]
    public static let highlighterPalette = ["#FDE047", "#86EFAC", "#7DD3FC", "#F472B6", "#C084FC", "#FDBA74",
                                            "#4ADE80", "#60A5FA", "#FEF9C3", "#DCFCE7", "#E0F2FE", "#FCE7F3"]
}

/// Saved pens, remembered across notes.
public enum NotePenStore {
    private static let pensKey = "notes.pens.v1"
    private static let activeKey = "notes.pens.active"
    private static let fingerKey = "notes.fingerDrawing"

    public static func load() -> [NotePen] {
        guard let data = UserDefaults.standard.data(forKey: pensKey),
              let pens = try? JSONDecoder().decode([NotePen].self, from: data), !pens.isEmpty else { return NotePen.defaults }
        return pens
    }

    public static func save(_ pens: [NotePen]) {
        if let data = try? JSONEncoder().encode(pens) { UserDefaults.standard.set(data, forKey: pensKey) }
    }

    public static var activeIndex: Int {
        get { UserDefaults.standard.integer(forKey: activeKey) }
        set { UserDefaults.standard.set(newValue, forKey: activeKey) }
    }

    public static var fingerDrawing: Bool {
        get { UserDefaults.standard.object(forKey: fingerKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: fingerKey) }
    }
}

// MARK: - Tools

public enum NoteTool: String, CaseIterable, Identifiable {
    case pen, shape, eraser, lasso, text, hand, laser, tape

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .pen: return "Pen"
        case .shape: return "Shape"
        case .eraser: return "Eraser"
        case .lasso: return "Lasso"
        case .text: return "Text"
        case .hand: return "Hand"
        case .laser: return "Laser"
        case .tape: return "Tape"
        }
    }

    public var icon: String {
        switch self {
        case .pen: return "pencil.tip"
        case .shape: return "square.on.circle"
        case .eraser: return "eraser"
        case .lasso: return "lasso"
        case .text: return "character.textbox"
        case .hand: return "hand.raised"
        case .laser: return "light.max"
        case .tape: return "bandage"
        }
    }

    /// Tools that let PencilKit ink (pen and shapes, plus the stroke and partial erasers).
    public var usesPencilKit: Bool { self == .pen || self == .shape || self == .eraser }
}

public enum NoteEraserMode: String, CaseIterable, Identifiable {
    case stroke, partial, highlighter
    public var id: String { rawValue }
    public var title: String {
        switch self {
        case .stroke: return "Stroke"
        case .partial: return "Partial"
        case .highlighter: return "Highlighter"
        }
    }
}

public let noteTapeColors = ["#F59E0B", "#60A5FA", "#F472B6", "#34D399", "#9CA3AF"]
public let noteStickyColors = ["#FEF08A", "#FBCFE8", "#BAE6FD", "#BBF7D0", "#FED7AA", "#E9D5FF"]

// MARK: - Ink storage

/// Loads and saves each page's ink by page id, in page points.
public enum NoteInkStore {
    public static func fileName(noteId: String, pageId: String) -> String { "note_\(noteId)_\(pageId).ink" }

    /// Page ink in page points. Older notes stored ink per page number in on-screen points; those are
    /// read from the page's previous file and scaled once to page points.
    public static func load(note: Note, page: NotePage, storage: StorageManager = .shared) -> PKDrawing {
        let current = fileName(noteId: note.id, pageId: page.id)
        if page.drawingFileName == current {
            return storage.loadDrawing(fileName: current)
        }
        guard let legacy = page.drawingFileName else { return PKDrawing() }
        let drawing = storage.loadDrawing(fileName: legacy)
        guard !drawing.strokes.isEmpty else { return drawing }
        // The old editor drew on a paper as wide as the screen minus 24 pt
        let legacyWidth = max(UIScreen.main.bounds.width - 24, 100)
        let scale = NoteLayout.pageSize.width / legacyWidth
        return drawing.transformed(using: CGAffineTransform(scaleX: scale, y: scale))
    }

    public static func save(_ drawing: PKDrawing, note: Note, page: NotePage, storage: StorageManager = .shared) -> String {
        let name = fileName(noteId: note.id, pageId: page.id)
        storage.saveDrawing(drawing, fileName: name)
        return name
    }

    /// Splits document ink into pages (by each stroke's center) in page points.
    public static func split(_ drawing: PKDrawing, pageCount: Int) -> [PKDrawing] {
        var pages = Array(repeating: [PKStroke](), count: max(pageCount, 1))
        for stroke in drawing.strokes {
            let index = NoteLayout.pageIndex(atY: stroke.renderBounds.midY, pageCount: pageCount)
            let origin = NoteLayout.origin(ofPage: index)
            var moved = stroke
            moved.transform = stroke.transform.concatenating(CGAffineTransform(translationX: -origin.x, y: -origin.y))
            pages[index].append(moved)
        }
        return pages.map { PKDrawing(strokes: $0) }
    }

    /// Joins page ink into one document drawing.
    public static func join(_ pages: [PKDrawing]) -> PKDrawing {
        var strokes: [PKStroke] = []
        for (index, page) in pages.enumerated() {
            let origin = NoteLayout.origin(ofPage: index)
            for stroke in page.strokes {
                var moved = stroke
                moved.transform = stroke.transform.concatenating(CGAffineTransform(translationX: origin.x, y: origin.y))
                strokes.append(moved)
            }
        }
        return PKDrawing(strokes: strokes)
    }
}

extension PKStroke {
    /// Path points in drawing coordinates.
    var documentPoints: [CGPoint] {
        path.map { $0.location.applying(transform) }
    }
}
