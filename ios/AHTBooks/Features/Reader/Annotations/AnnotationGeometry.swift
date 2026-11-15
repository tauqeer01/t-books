import Foundation
import CoreGraphics

// Pure annotation logic shared by the reader: tools, encoding, shape outlines,
// hit testing and undo history. No UIKit or PDFKit so it can be tested in isolation.
//
// Coordinates: "page points" are PDF points measured from the page's top-left corner
// (x right, y down). Stored annotations use normalized page coordinates (0...1), the
// same convention as the Android app.

/// Annotation tools offered by the reader (mirrors Android `DrawingTool`).
public enum ReaderTool: String, CaseIterable, Equatable {
    case hand, selectText, clip
    case pen, highlighter, eraser
    case line, arrow, rectangle, ellipse
    case textHighlight, textUnderline, textStrikethrough
    case stickyNote

    public var isShape: Bool { self == .line || self == .arrow || self == .rectangle || self == .ellipse }
    /// Drag across text to mark whole words (snaps to the PDF text layer).
    public var isTextMarkup: Bool { self == .textHighlight || self == .textUnderline || self == .textStrikethrough }
    /// Translucent tools pick from the pastel palette.
    public var usesHighlighterPalette: Bool { self == .highlighter || self == .textHighlight || self == .stickyNote }
    public var hasColor: Bool { !(self == .eraser || self == .hand || self == .selectText || self == .clip) }
    public var hasStrokeWidth: Bool { self == .pen || self == .highlighter || isShape }

    public var markupType: AnnotationType? {
        switch self {
        case .textHighlight: return .HIGHLIGHT
        case .textUnderline: return .UNDERLINE
        case .textStrikethrough: return .STRIKETHROUGH
        default: return nil
        }
    }
}

public struct NormPoint: Codable, Equatable {
    public var x: Double
    public var y: Double
    public init(x: Double, y: Double) { self.x = x; self.y = y }
}

public struct NormRect: Codable, Equatable {
    public var l: Double
    public var t: Double
    public var r: Double
    public var b: Double
    public init(l: Double, t: Double, r: Double, b: Double) { self.l = l; self.t = t; self.r = r; self.b = b }
}

/// Ink stroke stored in `BookAnnotation.strokePathData` for `.DRAWING` annotations.
/// `width` uses the Android unit: one unit is 1/400 of the page width.
public struct StrokePayload: Codable, Equatable {
    public var points: [NormPoint]
    public var width: Double
    public var isHighlighter: Bool
    public init(points: [NormPoint], width: Double, isHighlighter: Bool) {
        self.points = points; self.width = width; self.isHighlighter = isHighlighter
    }
}

public enum AnnotationCodec {
    public static func encode(_ stroke: StrokePayload) -> String {
        (try? String(decoding: JSONEncoder().encode(stroke), as: UTF8.self)) ?? ""
    }
    public static func decodeStroke(_ data: String?) -> StrokePayload? {
        guard let data, let bytes = data.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(StrokePayload.self, from: bytes)
    }
    public static func encode(_ rects: [NormRect]) -> String {
        (try? String(decoding: JSONEncoder().encode(rects), as: UTF8.self)) ?? ""
    }
    public static func decodeRects(_ data: String?) -> [NormRect]? {
        guard let data, let bytes = data.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode([NormRect].self, from: bytes)
    }
}

/// Converts between page points (top-left origin) and normalized coordinates.
public struct PageSpace: Equatable {
    public var size: CGSize
    public init(size: CGSize) { self.size = size }

    public func normalize(_ p: CGPoint) -> NormPoint {
        NormPoint(x: clamp01(Double(p.x / max(size.width, 1))), y: clamp01(Double(p.y / max(size.height, 1))))
    }
    public func denormalize(_ p: NormPoint) -> CGPoint {
        CGPoint(x: CGFloat(p.x) * size.width, y: CGFloat(p.y) * size.height)
    }
    public func normalize(_ r: CGRect) -> NormRect {
        NormRect(l: clamp01(Double(r.minX / size.width)), t: clamp01(Double(r.minY / size.height)),
                 r: clamp01(Double(r.maxX / size.width)), b: clamp01(Double(r.maxY / size.height)))
    }
    public func denormalize(_ r: NormRect) -> CGRect {
        CGRect(x: CGFloat(r.l) * size.width, y: CGFloat(r.t) * size.height,
               width: CGFloat(r.r - r.l) * size.width, height: CGFloat(r.b - r.t) * size.height)
    }
    /// Line width in page points for a stroke width in Android units.
    public func lineWidth(_ width: Double, highlighter: Bool) -> CGFloat {
        let base = max(CGFloat(width) * size.width / 400, 0.5)
        return highlighter ? base * 2.2 : base
    }

    private func clamp01(_ v: Double) -> Double { min(max(v, 0), 1) }
}

extension BookAnnotation {
    /// Rectangles covering the annotation, normalized. Markups store one per text line.
    public var normRects: [NormRect] {
        if type != .DRAWING, let rects = AnnotationCodec.decodeRects(strokePathData), !rects.isEmpty { return rects }
        return [NormRect(l: Double(rectLeft), t: Double(rectTop), r: Double(rectRight), b: Double(rectBottom))]
    }
}

/// Shape outlines as stroke points, so shapes reuse stroke storage, erasing and export (as on Android).
public enum ShapeGeometry {
    public static func points(_ tool: ReaderTool, from start: CGPoint, to end: CGPoint,
                              arrowHead: ClosedRange<CGFloat> = 8...22) -> [CGPoint] {
        switch tool {
        case .line:
            return [start, end]
        case .arrow:
            let angle = atan2(end.y - start.y, end.x - start.x)
            let length = hypot(end.x - start.x, end.y - start.y)
            let head = min(max(length * 0.28, arrowHead.lowerBound), arrowHead.upperBound)
            let spread: CGFloat = .pi / 7
            let left = CGPoint(x: end.x - head * cos(angle - spread), y: end.y - head * sin(angle - spread))
            let right = CGPoint(x: end.x - head * cos(angle + spread), y: end.y - head * sin(angle + spread))
            return [start, end, left, end, right]
        case .rectangle:
            return [start, CGPoint(x: end.x, y: start.y), end, CGPoint(x: start.x, y: end.y), start]
        case .ellipse:
            let c = CGPoint(x: (start.x + end.x) / 2, y: (start.y + end.y) / 2)
            let rx = abs(end.x - start.x) / 2, ry = abs(end.y - start.y) / 2
            return (0...64).map { i in
                let t = CGFloat(i) / 64 * 2 * .pi
                return CGPoint(x: c.x + rx * cos(t), y: c.y + ry * sin(t))
            }
        default:
            return []
        }
    }
}

public enum AnnotationHitTest {
    /// Distance from `p` to the segment `a`-`b`.
    public static func distance(_ p: CGPoint, toSegment a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = b.x - a.x, dy = b.y - a.y
        let len2 = dx * dx + dy * dy
        guard len2 > 0 else { return hypot(p.x - a.x, p.y - a.y) }
        let t = max(0, min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2))
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /// True when a polyline drawn `lineWidth` wide passes within `radius` of `p`.
    public static func stroke(_ points: [CGPoint], lineWidth: CGFloat, hits p: CGPoint, radius: CGFloat) -> Bool {
        let reach = radius + lineWidth / 2
        guard let first = points.first else { return false }
        if points.count == 1 { return hypot(p.x - first.x, p.y - first.y) <= reach }
        for i in 1..<points.count where distance(p, toSegment: points[i - 1], points[i]) <= reach {
            return true
        }
        return false
    }

    /// The annotation under `p` (page points) on one page, topmost first. Strokes count too.
    public static func annotation(at p: CGPoint, in annotations: [BookAnnotation], space: PageSpace,
                                  radius: CGFloat) -> BookAnnotation? {
        for ann in annotations.reversed() {
            if hits(ann, at: p, space: space, radius: radius) { return ann }
        }
        return nil
    }

    public static func hits(_ ann: BookAnnotation, at p: CGPoint, space: PageSpace, radius: CGFloat) -> Bool {
        if ann.type == .DRAWING {
            guard let stroke = AnnotationCodec.decodeStroke(ann.strokePathData) else { return false }
            let pts = stroke.points.map(space.denormalize)
            return Self.stroke(pts, lineWidth: space.lineWidth(stroke.width, highlighter: stroke.isHighlighter),
                               hits: p, radius: radius)
        }
        return ann.normRects.contains { space.denormalize($0).insetBy(dx: -radius, dy: -radius).contains(p) }
    }
}

/// One undoable change: annotations added or removed together.
public enum AnnotationChange: Equatable {
    case add([BookAnnotation])
    case remove([BookAnnotation])

    public var inverse: AnnotationChange {
        switch self {
        case .add(let a): return .remove(a)
        case .remove(let a): return .add(a)
        }
    }
}

public struct AnnotationHistory {
    public private(set) var undoStack: [AnnotationChange] = []
    public private(set) var redoStack: [AnnotationChange] = []
    public init() {}

    public var canUndo: Bool { !undoStack.isEmpty }
    public var canRedo: Bool { !redoStack.isEmpty }

    public mutating func record(_ change: AnnotationChange) {
        undoStack.append(change)
        redoStack.removeAll()
    }
    /// Returns the change to apply to undo the last action.
    public mutating func undo() -> AnnotationChange? {
        guard let last = undoStack.popLast() else { return nil }
        redoStack.append(last)
        return last.inverse
    }
    /// Returns the change to apply to redo the last undone action.
    public mutating func redo() -> AnnotationChange? {
        guard let last = redoStack.popLast() else { return nil }
        undoStack.append(last)
        return last
    }
    /// Forgets an annotation entirely (e.g. an empty sticky note that was discarded).
    public mutating func forget(id: String) {
        func strip(_ c: AnnotationChange) -> AnnotationChange? {
            switch c {
            case .add(let a): let f = a.filter { $0.id != id }; return f.isEmpty ? nil : .add(f)
            case .remove(let a): let f = a.filter { $0.id != id }; return f.isEmpty ? nil : .remove(f)
            }
        }
        undoStack = undoStack.compactMap(strip)
        redoStack = redoStack.compactMap(strip)
    }
}
