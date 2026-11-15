import Foundation
import CoreGraphics

/// Turns a hand-drawn line, triangle, rectangle, quadrilateral or ellipse into a clean outline
/// (the Notes "auto shape" tool, ported from the Android app). Returns nil when the drawing is not
/// close enough to any of them, so it stays freehand ink.
public enum ShapeRecognizer {
    public enum Kind: Equatable { case line, triangle, rectangle, quadrilateral, ellipse }

    public struct Result: Equatable {
        public let kind: Kind
        public let points: [CGPoint]
    }

    /// Explicit shapes the Shape tool can draw by dragging.
    public enum ShapeKind: String, CaseIterable, Identifiable {
        case line, arrow, rectangle, ellipse, triangle
        public var id: String { rawValue }
        public var title: String {
            switch self {
            case .line: return "Line"
            case .arrow: return "Arrow"
            case .rectangle: return "Rectangle"
            case .ellipse: return "Ellipse"
            case .triangle: return "Triangle"
            }
        }
        public var icon: String {
            switch self {
            case .line: return "line.diagonal"
            case .arrow: return "arrow.up.right"
            case .rectangle: return "rectangle"
            case .ellipse: return "oval"
            case .triangle: return "triangle"
            }
        }
    }

    // MARK: Explicit shapes

    /// Outline points of a shape dragged from `a` to `b`, densified so pen rendering keeps corners sharp.
    public static func outline(_ kind: ShapeKind, from a: CGPoint, to b: CGPoint) -> [CGPoint] {
        switch kind {
        case .line:
            return segment(a, b)
        case .arrow:
            let angle = atan2(b.y - a.y, b.x - a.x)
            let head = min(max(hypot(b.x - a.x, b.y - a.y) * 0.28, 10), 40)
            let spread: CGFloat = .pi / 7
            let left = CGPoint(x: b.x - head * cos(angle - spread), y: b.y - head * sin(angle - spread))
            let right = CGPoint(x: b.x - head * cos(angle + spread), y: b.y - head * sin(angle + spread))
            return segment(a, b) + segment(b, left).dropFirst() + segment(left, b).dropFirst() + segment(b, right).dropFirst()
        case .rectangle:
            return polygon([a, CGPoint(x: b.x, y: a.y), b, CGPoint(x: a.x, y: b.y), a])
        case .triangle:
            return polygon([CGPoint(x: (a.x + b.x) / 2, y: a.y), b, CGPoint(x: a.x, y: b.y), CGPoint(x: (a.x + b.x) / 2, y: a.y)])
        case .ellipse:
            let c = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
            let rx = abs(b.x - a.x) / 2, ry = abs(b.y - a.y) / 2
            return (0...72).map { i in
                let t = CGFloat(i) / 72 * 2 * .pi
                return CGPoint(x: c.x + rx * cos(t), y: c.y + ry * sin(t))
            }
        }
    }

    public static func polygon(_ corners: [CGPoint]) -> [CGPoint] {
        guard corners.count > 1 else { return corners }
        var out: [CGPoint] = [corners[0]]
        for i in 1..<corners.count { out += segment(corners[i - 1], corners[i]).dropFirst() }
        return out
    }

    /// Points every few units along a segment.
    public static func segment(_ a: CGPoint, _ b: CGPoint, step: CGFloat = 6) -> [CGPoint] {
        let n = max(1, Int(ceil(hypot(b.x - a.x, b.y - a.y) / step)))
        return (0...n).map { i in
            let t = CGFloat(i) / CGFloat(n)
            return CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
        }
    }

    // MARK: Recognition

    public static func recognize(_ raw: [CGPoint]) -> Result? {
        let points = raw.enumerated().filter { $0.offset == 0 || $0.element != raw[$0.offset - 1] }.map(\.element)
        guard points.count >= 3 else { return nil }
        let left = points.map(\.x).min()!, right = points.map(\.x).max()!
        let top = points.map(\.y).min()!, bottom = points.map(\.y).max()!
        let diagonal = hypot(right - left, bottom - top)
        guard diagonal >= 20 else { return nil }
        let length = zip(points, points.dropFirst()).reduce(0) { $0 + hypot($1.1.x - $1.0.x, $1.1.y - $1.0.y) }
        let first = points.first!, last = points.last!
        let closed = hypot(last.x - first.x, last.y - first.y) < max(0.2 * diagonal, 0.08 * length)

        if !closed {
            // A line: every point close to the chord and the path barely longer than it
            let chord = hypot(last.x - first.x, last.y - first.y)
            let straight = points.allSatisfy { distance($0, toSegment: first, last) < max(0.06 * chord, 6) }
            return straight && length < chord * 1.2 ? Result(kind: .line, points: segment(first, last)) : nil
        }

        let loop = points + [first]
        var corners = Array(simplify(loop, epsilon: 0.08 * diagonal).dropLast())
        corners = mergeCollinear(mergeNearby(corners), diagonal: diagonal)
        switch corners.count {
        case 3:
            return Result(kind: .triangle, points: polygon(corners + [corners[0]]))
        case 4:
            let box = (right - left) * (bottom - top)
            if box > 0 && polygonArea(corners) / box > 0.82 {
                return Result(kind: .rectangle, points: outline(.rectangle, from: CGPoint(x: left, y: top), to: CGPoint(x: right, y: bottom)))
            }
            return Result(kind: .quadrilateral, points: polygon(corners + [corners[0]]))
        default:
            break
        }

        // Ellipse: points stay near the ellipse fitted to the bounding box
        let cx = (left + right) / 2, cy = (top + bottom) / 2
        let rx = (right - left) / 2, ry = (bottom - top) / 2
        guard min(rx, ry) >= 0.15 * max(rx, ry) else { return nil }
        let deviation = points.map { p -> CGFloat in
            let nx = (p.x - cx) / rx, ny = (p.y - cy) / ry
            return abs(sqrt(nx * nx + ny * ny) - 1)
        }
        let average = deviation.reduce(0, +) / CGFloat(deviation.count)
        if average < 0.12 && deviation.max()! < 0.35 {
            return Result(kind: .ellipse, points: outline(.ellipse, from: CGPoint(x: left, y: top), to: CGPoint(x: right, y: bottom)))
        }
        return nil
    }

    static func distance(_ p: CGPoint, toSegment a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = b.x - a.x, dy = b.y - a.y
        let len2 = dx * dx + dy * dy
        guard len2 > 0 else { return hypot(p.x - a.x, p.y - a.y) }
        let t = max(0, min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2))
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /// Ramer–Douglas–Peucker.
    private static func simplify(_ points: [CGPoint], epsilon: CGFloat) -> [CGPoint] {
        guard points.count >= 3 else { return points }
        // A closed loop starts and ends at the same point: split at the point farthest from it instead
        if points.first == points.last {
            let index = points.indices.max { hypot(points[$0].x - points[0].x, points[$0].y - points[0].y)
                < hypot(points[$1].x - points[0].x, points[$1].y - points[0].y) }!
            if index == 0 { return points }
            return Array(simplify(Array(points[0...index]), epsilon: epsilon).dropLast()) + simplify(Array(points[index...]), epsilon: epsilon)
        }
        var index = 0
        var maxDistance: CGFloat = 0
        for i in 1..<(points.count - 1) {
            let d = distance(points[i], toSegment: points.first!, points.last!)
            if d > maxDistance { maxDistance = d; index = i }
        }
        if maxDistance > epsilon {
            return Array(simplify(Array(points[0...index]), epsilon: epsilon).dropLast()) + simplify(Array(points[index...]), epsilon: epsilon)
        }
        return [points.first!, points.last!]
    }

    /// The start of a closed drawing usually adds a corner next to a real one.
    private static func mergeNearby(_ corners: [CGPoint]) -> [CGPoint] {
        guard corners.count >= 2 else { return corners }
        let ring = corners + [corners[0]]
        let perimeter = zip(ring, ring.dropFirst()).reduce(0) { $0 + hypot($1.1.x - $1.0.x, $1.1.y - $1.0.y) }
        var out = corners
        var i = 0
        while out.count > 2 && i < out.count {
            let next = out[(i + 1) % out.count]
            if hypot(next.x - out[i].x, next.y - out[i].y) < 0.08 * perimeter { out.remove(at: (i + 1) % out.count) } else { i += 1 }
        }
        return out
    }

    /// Drops corners where the outline barely turns.
    private static func mergeCollinear(_ corners: [CGPoint], diagonal: CGFloat) -> [CGPoint] {
        var out = corners
        var changed = true
        while changed && out.count > 3 {
            changed = false
            for i in out.indices {
                let prev = out[(i - 1 + out.count) % out.count], next = out[(i + 1) % out.count]
                if distance(out[i], toSegment: prev, next) < 0.07 * diagonal {
                    out.remove(at: i)
                    changed = true
                    break
                }
            }
        }
        return out
    }

    private static func polygonArea(_ corners: [CGPoint]) -> CGFloat {
        let ring = corners + [corners[0]]
        return abs(zip(ring, ring.dropFirst()).reduce(0) { $0 + ($1.0.x * $1.1.y - $1.1.x * $1.0.y) }) / 2
    }
}
