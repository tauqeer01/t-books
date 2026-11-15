import SwiftUI
import PDFKit

/// A request to show a page; a new `id` makes the reader jump even to the same page again.
public struct PageRequest: Equatable {
    public let id = UUID()
    public let pageIndex: Int
    public init(_ pageIndex: Int) { self.pageIndex = pageIndex }
}

public struct PDFKitContainerView: UIViewRepresentable {
    public let pdfDocument: PDFDocument
    public let initialPageIndex: Int
    public let scrollMode: PageScrollMode
    public let theme: ReaderTheme
    public let controller: ReaderAnnotationController
    /// Tool state is passed by value so SwiftUI updates the view when it changes.
    public let activeTool: ReaderTool
    public let isStylusOnly: Bool
    public let colorHex: String
    public let strokeWidth: Double
    public let pageRequest: PageRequest?
    public let searchMatches: [PDFSelection]
    public let currentSearchMatch: PDFSelection?
    public var onPageChanged: (Int, Int) -> Void
    /// Taps on the page that hit no annotation, in reading mode.
    public var onTapEmpty: () -> Void
    /// A dragged clip region: page index and rectangle in page points (top-left origin).
    public var onClip: (Int, CGRect) -> Void

    public func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    public func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.document = pdfDocument
        pdfView.autoScales = true
        pdfView.delegate = context.coordinator
        pdfView.accessibilityIdentifier = "reader.pdf"
        controller.attach(pdfDocument)
        controller.pdfView = pdfView

        let coordinator = context.coordinator
        coordinator.pdfView = pdfView
        coordinator.applyDisplayMode(scrollMode)
        applyTheme(to: pdfView)

        let preview = AnnotationPreviewView(frame: pdfView.bounds)
        preview.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        pdfView.addSubview(preview)
        coordinator.preview = preview

        let draw = AnnotationTouchRecognizer(target: coordinator, action: #selector(Coordinator.handleDraw(_:)))
        draw.delegate = coordinator
        pdfView.addGestureRecognizer(draw)
        coordinator.drawRecognizer = draw

        let tap = UITapGestureRecognizer(target: coordinator, action: #selector(Coordinator.handleTap(_:)))
        tap.delegate = coordinator
        tap.cancelsTouchesInView = false
        pdfView.addGestureRecognizer(tap)
        coordinator.tapRecognizer = tap

        if initialPageIndex > 0, let page = pdfDocument.page(at: initialPageIndex) {
            DispatchQueue.main.async { pdfView.go(to: page) }
        }

        NotificationCenter.default.addObserver(coordinator, selector: #selector(Coordinator.pageChanged(_:)),
                                               name: .PDFViewPageChanged, object: pdfView)
        NotificationCenter.default.addObserver(coordinator, selector: #selector(Coordinator.selectionChanged(_:)),
                                               name: .PDFViewSelectionChanged, object: pdfView)
        NotificationCenter.default.addObserver(coordinator, selector: #selector(Coordinator.clearSelectionRequested),
                                               name: .readerClearSelection, object: nil)
        coordinator.applyTool()
        return pdfView
    }

    public func updateUIView(_ pdfView: PDFView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        if pdfView.document !== pdfDocument {
            pdfView.document = pdfDocument
            controller.attach(pdfDocument)
        }
        coordinator.applyDisplayMode(scrollMode)
        applyTheme(to: pdfView)
        coordinator.applyTool()

        if let request = pageRequest, request.id != coordinator.lastPageRequestId {
            coordinator.lastPageRequestId = request.id
            if let page = pdfDocument.page(at: request.pageIndex) { pdfView.go(to: page) }
        }
        if coordinator.lastSearchMatches != searchMatches {
            coordinator.lastSearchMatches = searchMatches
            searchMatches.forEach { $0.color = UIColor.systemYellow.withAlphaComponent(0.55) }
            pdfView.highlightedSelections = searchMatches.isEmpty ? nil : searchMatches
        }
        if coordinator.lastSearchMatch !== currentSearchMatch {
            coordinator.lastSearchMatch = currentSearchMatch
            if let match = currentSearchMatch {
                searchMatches.forEach { $0.color = UIColor.systemYellow.withAlphaComponent(0.55) }
                match.color = UIColor.systemOrange.withAlphaComponent(0.7)
                pdfView.highlightedSelections = searchMatches
                pdfView.go(to: match)
            }
        }
    }

    public static func dismantleUIView(_ pdfView: PDFView, coordinator: Coordinator) {
        NotificationCenter.default.removeObserver(coordinator)
    }

    private func applyTheme(to pdfView: PDFView) {
        let hex: String
        switch theme {
        case .LIGHT: hex = "#F8F8FF"
        case .DARK: hex = "#0B1120"
        case .SEPIA: hex = "#F8F1E5"
        case .MINT: hex = "#E8F5E9"
        }
        pdfView.backgroundColor = UIColor(Color(hex: hex))
    }

    // MARK: - Coordinator

    @MainActor
    public final class Coordinator: NSObject, PDFViewDelegate, UIGestureRecognizerDelegate {
        var parent: PDFKitContainerView
        weak var pdfView: PDFView?
        weak var preview: AnnotationPreviewView?
        weak var drawRecognizer: AnnotationTouchRecognizer?
        weak var tapRecognizer: UITapGestureRecognizer?
        var lastPageRequestId: UUID?
        var lastSearchMatches: [PDFSelection] = []
        weak var lastSearchMatch: PDFSelection?
        private var lastScrollMode: PageScrollMode?
        private var lastToolConfig: (ReaderTool, Bool)?
        private var savedPanTouchTypes: [ObjectIdentifier: [NSNumber]] = [:]

        // Gesture state
        private var gesturePage: PDFPage?
        private var gesturePageIndex = 0
        private var startPagePoint: CGPoint = .zero
        private var startViewPoint: CGPoint = .zero
        private var strokePoints: [CGPoint] = []
        private var markupSelection: ReaderTextSelection?
        private var ignoreSelectionNotifications = false

        init(parent: PDFKitContainerView) { self.parent = parent }

        private var controller: ReaderAnnotationController { parent.controller }

        // MARK: Display mode

        func applyDisplayMode(_ mode: PageScrollMode) {
            guard let pdfView, mode != lastScrollMode else { return }
            lastScrollMode = mode
            let current = pdfView.currentPage
            switch mode {
            case .CONTINUOUS_VERTICAL:
                pdfView.usePageViewController(false)
                pdfView.displayMode = .singlePageContinuous
                pdfView.displayDirection = .vertical
            case .HORIZONTAL_PAGING, .BOOK_CURL:
                // Swipe left/right page by page
                pdfView.displayMode = .singlePage
                pdfView.displayDirection = .horizontal
                pdfView.usePageViewController(true, withViewOptions: [UIPageViewController.OptionsKey.interPageSpacing: 16])
            case .SINGLE_PAGE:
                pdfView.displayMode = .singlePage
                pdfView.displayDirection = .vertical
                pdfView.usePageViewController(true, withViewOptions: nil)
            }
            pdfView.autoScales = true
            if let current { pdfView.go(to: current) }
            lastToolConfig = nil // page view controller swaps scroll views; reconfigure them
            applyTool()
        }

        // MARK: Tool configuration

        func applyTool() {
            guard let pdfView else { return }
            let tool = parent.activeTool
            let stylusOnly = parent.isStylusOnly
            let drawing = tool != .hand
            drawRecognizer?.isEnabled = drawing
            drawRecognizer?.stylusOnly = stylusOnly
            tapRecognizer?.isEnabled = !drawing
            if let last = lastToolConfig, last == (tool, stylusOnly) { return }
            lastToolConfig = (tool, stylusOnly)

            // Markup mode stops PDFView's own text-selection and page gestures while annotating
            pdfView.isInMarkupMode = drawing
            if drawing { pdfView.clearSelection() }
            // One finger draws; two fingers scroll and zoom. With stylus-only, fingers scroll as usual.
            for scrollView in Self.scrollViews(in: pdfView) {
                let pan = scrollView.panGestureRecognizer
                let key = ObjectIdentifier(pan)
                if savedPanTouchTypes[key] == nil { savedPanTouchTypes[key] = pan.allowedTouchTypes }
                if drawing && !stylusOnly {
                    pan.minimumNumberOfTouches = 2
                    pan.allowedTouchTypes = savedPanTouchTypes[key] ?? pan.allowedTouchTypes
                } else if drawing && stylusOnly {
                    pan.minimumNumberOfTouches = 1
                    pan.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.direct.rawValue)]
                } else {
                    pan.minimumNumberOfTouches = 1
                    pan.allowedTouchTypes = savedPanTouchTypes[key] ?? pan.allowedTouchTypes
                }
            }
            if !drawing { preview?.clear() }
        }

        private static func scrollViews(in view: UIView) -> [UIScrollView] {
            var found: [UIScrollView] = []
            for sub in view.subviews {
                if let scroll = sub as? UIScrollView { found.append(scroll) }
                found += scrollViews(in: sub)
            }
            return found
        }

        // MARK: Geometry

        private func pagePoint(_ viewPoint: CGPoint, on page: PDFPage) -> CGPoint {
            guard let pdfView else { return .zero }
            let p = pdfView.convert(viewPoint, to: page)
            let box = page.bounds(for: .cropBox)
            return CGPoint(x: min(max(p.x - box.minX, 0), box.width), y: min(max(box.maxY - p.y, 0), box.height))
        }

        private func viewPoint(_ pagePoint: CGPoint, on page: PDFPage) -> CGPoint {
            guard let pdfView else { return .zero }
            let box = page.bounds(for: .cropBox)
            return pdfView.convert(CGPoint(x: box.minX + pagePoint.x, y: box.maxY - pagePoint.y), from: page)
        }

        private func viewRect(_ pageRect: CGRect, on page: PDFPage) -> CGRect {
            let a = viewPoint(CGPoint(x: pageRect.minX, y: pageRect.minY), on: page)
            let b = viewPoint(CGPoint(x: pageRect.maxX, y: pageRect.maxY), on: page)
            return CGRect(x: min(a.x, b.x), y: min(a.y, b.y), width: abs(b.x - a.x), height: abs(b.y - a.y))
        }

        private func pdfPoint(_ pagePoint: CGPoint, on page: PDFPage) -> CGPoint {
            let box = page.bounds(for: .cropBox)
            return CGPoint(x: box.minX + pagePoint.x, y: box.maxY - pagePoint.y)
        }

        /// View points per page point at the current zoom.
        private var scale: CGFloat { max(pdfView?.scaleFactor ?? 1, 0.01) }

        /// Whole-word text selection between two page points; a tap selects the word under it.
        private func pdfSelection(on page: PDFPage, from a: CGPoint, to b: CGPoint) -> PDFSelection? {
            let pa = pdfPoint(a, on: page), pb = pdfPoint(b, on: page)
            if hypot(a.x - b.x, a.y - b.y) * scale < 8 {
                return word(on: page, at: pa)
            }
            guard let selection = page.selection(from: pa, to: pb) else { return nil }
            if let first = word(on: page, at: pa) { selection.add(first) }
            if let last = word(on: page, at: pb) { selection.add(last) }
            // PDFKit snaps drags in blank space to the nearest characters; keep only lines the drag passes over
            let slop = 6 / scale
            let band = CGRect(x: min(pa.x, pb.x), y: min(pa.y, pb.y), width: abs(pb.x - pa.x), height: abs(pb.y - pa.y))
                .insetBy(dx: -slop, dy: -slop)
            let lines = selection.selectionsByLine().filter { $0.bounds(for: page).intersects(band) }
            guard let firstLine = lines.first else { return nil }
            let kept = PDFSelection(document: page.document ?? PDFDocument())
            kept.add(firstLine)
            lines.dropFirst().forEach { kept.add($0) }
            return kept
        }

        /// The word actually under a point. PDFKit returns the nearest word even for blank space, which
        /// would mark text the finger never touched.
        private func word(on page: PDFPage, at point: CGPoint) -> PDFSelection? {
            guard let word = page.selectionForWord(at: point) else { return nil }
            let slop = 4 / scale
            return word.bounds(for: page).insetBy(dx: -slop, dy: -slop).contains(point) ? word : nil
        }

        private func textSelection(on page: PDFPage, pageIndex: Int, from a: CGPoint, to b: CGPoint) -> ReaderTextSelection? {
            Self.readerSelection(pdfSelection(on: page, from: a, to: b), page: page, pageIndex: pageIndex)
        }

        @objc func clearSelectionRequested() {
            pdfView?.clearSelection()
        }

        static func readerSelection(_ selection: PDFSelection?, page: PDFPage, pageIndex: Int) -> ReaderTextSelection? {
            guard let selection, let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !text.isEmpty else { return nil }
            let box = page.bounds(for: .cropBox)
            let lines = selection.selectionsByLine().compactMap { line -> CGRect? in
                let r = line.bounds(for: page)
                guard r.width > 0.5, r.height > 0.5 else { return nil }
                return CGRect(x: r.minX - box.minX, y: box.maxY - r.maxY, width: r.width, height: r.height)
            }
            guard !lines.isEmpty else { return nil }
            return ReaderTextSelection(pageIndex: pageIndex, text: text, lines: lines)
        }

        // MARK: Drawing gesture

        @objc func handleDraw(_ recognizer: AnnotationTouchRecognizer) {
            guard let pdfView, let preview else { return }
            let location = recognizer.location(in: pdfView)
            let tool = parent.activeTool

            switch recognizer.state {
            case .began:
                guard let page = pdfView.page(for: location, nearest: true), let doc = pdfView.document else {
                    recognizer.state = .cancelled
                    return
                }
                gesturePage = page
                gesturePageIndex = doc.index(for: page)
                startViewPoint = location
                startPagePoint = pagePoint(location, on: page)
                strokePoints = [startPagePoint]
                markupSelection = nil
                if tool == .eraser {
                    controller.beginErase()
                    erase(at: startPagePoint, viewPoint: location, page: page)
                } else if tool == .selectText {
                    // Start a fresh selection; the action bar shows when the drag ends
                    controller.textSelection = nil
                    pdfView.clearSelection()
                }
                updatePreview(tool: tool, current: startPagePoint, viewPoint: location, page: page)

            case .changed:
                guard let page = gesturePage else { return }
                let current = pagePoint(location, on: page)
                if tool == .pen || tool == .highlighter {
                    if let last = strokePoints.last, hypot(current.x - last.x, current.y - last.y) * scale >= 1 {
                        strokePoints.append(current)
                    }
                } else if tool == .eraser {
                    erase(at: current, viewPoint: location, page: page)
                }
                updatePreview(tool: tool, current: current, viewPoint: location, page: page)

            case .ended:
                guard let page = gesturePage else { return }
                let end = pagePoint(location, on: page)
                commit(tool: tool, end: end, endViewPoint: location, page: page)
                finishGesture()

            case .cancelled, .failed:
                if tool == .eraser { controller.endErase() }
                finishGesture()

            default:
                break
            }
        }

        private func erase(at point: CGPoint, viewPoint: CGPoint, page: PDFPage) {
            // Erase what the 18 pt eraser circle covers on screen
            let space = ReaderAnnotationController.pageSpace(of: page)
            controller.erase(pageIndex: gesturePageIndex, at: point, radius: 18 / scale, space: space)
        }

        private func updatePreview(tool: ReaderTool, current: CGPoint, viewPoint: CGPoint, page: PDFPage) {
            guard let preview else { return }
            let space = ReaderAnnotationController.pageSpace(of: page)
            let color = UIColor(Color(hex: parent.colorHex))
            switch tool {
            case .pen, .highlighter:
                let isHighlighter = tool == .highlighter
                preview.stroke = strokePoints.map { self.viewPoint($0, on: page) }
                preview.strokeColor = isHighlighter ? color.withAlphaComponent(0.42) : color
                preview.strokeWidth = space.lineWidth(parent.strokeWidth, highlighter: isHighlighter) * scale
            case .line, .arrow, .rectangle, .ellipse:
                let head = (8 / scale)...(22 / scale)
                preview.stroke = ShapeGeometry.points(tool, from: startPagePoint, to: current, arrowHead: head)
                    .map { self.viewPoint($0, on: page) }
                preview.strokeColor = color
                preview.strokeWidth = space.lineWidth(parent.strokeWidth, highlighter: false) * scale
            case .textHighlight, .textUnderline, .textStrikethrough, .selectText:
                markupSelection = textSelection(on: page, pageIndex: gesturePageIndex, from: startPagePoint, to: current)
                preview.markupRects = (markupSelection?.lines ?? []).map { viewRect($0, on: page) }
                preview.markupStyle = tool == .selectText ? .select : (tool == .textHighlight ? .highlight : (tool == .textUnderline ? .underline : .strike))
                preview.markupColor = color
            case .clip:
                preview.clipRect = CGRect(x: min(startViewPoint.x, viewPoint.x), y: min(startViewPoint.y, viewPoint.y),
                                          width: abs(viewPoint.x - startViewPoint.x), height: abs(viewPoint.y - startViewPoint.y))
            case .eraser:
                preview.eraserCenter = viewPoint
            case .stickyNote, .hand:
                break
            }
            preview.setNeedsDisplay()
        }

        private func commit(tool: ReaderTool, end: CGPoint, endViewPoint: CGPoint, page: PDFPage) {
            let space = ReaderAnnotationController.pageSpace(of: page)
            let movedOnScreen = hypot(endViewPoint.x - startViewPoint.x, endViewPoint.y - startViewPoint.y)
            switch tool {
            case .pen, .highlighter:
                controller.commitStroke(pageIndex: gesturePageIndex, points: strokePoints, space: space)
            case .line, .arrow, .rectangle, .ellipse:
                // Ignore taps: only keep shapes dragged out to a visible size
                if movedOnScreen > 6 {
                    controller.commitShape(pageIndex: gesturePageIndex, start: startPagePoint, end: end, space: space,
                                           arrowHead: (8 / scale)...(22 / scale))
                }
            case .textHighlight, .textUnderline, .textStrikethrough:
                let selection = textSelection(on: page, pageIndex: gesturePageIndex, from: startPagePoint, to: end)
                controller.commitMarkup(selection, type: tool.markupType ?? .HIGHLIGHT, space: space)
            case .selectText:
                let pdfSel = pdfSelection(on: page, from: startPagePoint, to: end)
                let selection = Self.readerSelection(pdfSel, page: page, pageIndex: gesturePageIndex)
                // Keep the selected text visibly highlighted while its actions are shown
                pdfView?.currentSelection = selection == nil ? nil : pdfSel
                controller.textSelection = selection
                if selection == nil { controller.toast = "No text there to select" }
            case .clip:
                let rect = CGRect(x: min(startPagePoint.x, end.x), y: min(startPagePoint.y, end.y),
                                  width: abs(end.x - startPagePoint.x), height: abs(end.y - startPagePoint.y))
                // A clip needs a visible area; ignore taps
                if rect.width * scale > 24 && rect.height * scale > 24 {
                    parent.onClip(gesturePageIndex, rect)
                }
            case .stickyNote:
                if movedOnScreen < 10 {
                    controller.placeStickyNote(pageIndex: gesturePageIndex, at: end, space: space)
                }
            case .eraser:
                controller.endErase()
            case .hand:
                break
            }
        }

        private func finishGesture() {
            gesturePage = nil
            strokePoints = []
            markupSelection = nil
            preview?.clear()
        }

        // MARK: Reading-mode taps and selection

        @objc func handleTap(_ recognizer: UITapGestureRecognizer) {
            guard recognizer.state == .ended, let pdfView, let doc = pdfView.document else { return }
            let location = recognizer.location(in: pdfView)
            if let page = pdfView.page(for: location, nearest: false) {
                let pageIndex = doc.index(for: page)
                let space = ReaderAnnotationController.pageSpace(of: page)
                let point = pagePoint(location, on: page)
                if let hit = controller.annotation(at: point, pageIndex: pageIndex, space: space, radius: 10 / scale) {
                    controller.textSelection = nil
                    controller.tappedAnnotation = hit
                    return
                }
            }
            if controller.textSelection != nil {
                controller.textSelection = nil
                pdfView.clearSelection()
                return
            }
            parent.onTapEmpty()
        }

        @objc func pageChanged(_ notification: Notification) {
            guard let pdfView, let page = pdfView.currentPage, let doc = pdfView.document else { return }
            parent.onPageChanged(doc.index(for: page), doc.pageCount)
        }

        /// Long-press selections in reading mode feed the selection actions.
        @objc func selectionChanged(_ notification: Notification) {
            guard let pdfView, parent.activeTool == .hand else { return }
            guard let selection = pdfView.currentSelection, let page = selection.pages.first,
                  let doc = pdfView.document else {
                return
            }
            if let reader = Self.readerSelection(selection, page: page, pageIndex: doc.index(for: page)) {
                controller.textSelection = reader
            }
        }

        // MARK: UIGestureRecognizerDelegate

        public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                                      shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            if gestureRecognizer === tapRecognizer { return true }
            // Let two-finger scrolling and pinch zoom take over from a stroke
            return other is UIPanGestureRecognizer || other is UIPinchGestureRecognizer
        }
    }
}

// MARK: - Touch recognizer

/// Tracks a single touch for drawing. A second finger cancels it so scrolling and zooming take over.
public final class AnnotationTouchRecognizer: UIGestureRecognizer {
    private var tracked: UITouch?

    var stylusOnly = false {
        didSet {
            allowedTouchTypes = stylusOnly
                ? [NSNumber(value: UITouch.TouchType.pencil.rawValue)]
                : [NSNumber(value: UITouch.TouchType.direct.rawValue), NSNumber(value: UITouch.TouchType.pencil.rawValue)]
        }
    }

    public override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if tracked != nil || touches.count > 1 {
            state = state == .possible ? .failed : .cancelled
            return
        }
        tracked = touches.first
        state = .began
    }

    public override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let tracked, touches.contains(tracked) else { return }
        state = .changed
    }

    public override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let tracked, touches.contains(tracked) else { return }
        state = .ended
    }

    public override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        state = .cancelled
    }

    public override func reset() {
        super.reset()
        tracked = nil
    }

    public override func location(in view: UIView?) -> CGPoint {
        tracked?.location(in: view) ?? super.location(in: view)
    }
}

// MARK: - Live preview

/// Draws the stroke, shape, text marks, clip box or eraser while a gesture is in progress.
public final class AnnotationPreviewView: UIView {
    enum MarkupStyle { case highlight, underline, strike, select }

    var stroke: [CGPoint] = []
    var strokeColor: UIColor = .black
    var strokeWidth: CGFloat = 2
    var markupRects: [CGRect] = []
    var markupStyle: MarkupStyle = .highlight
    var markupColor: UIColor = .yellow
    var clipRect: CGRect?
    var eraserCenter: CGPoint?

    public override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func clear() {
        stroke = []
        markupRects = []
        clipRect = nil
        eraserCenter = nil
        setNeedsDisplay()
    }

    public override func draw(_ rect: CGRect) {
        for r in markupRects {
            switch markupStyle {
            case .highlight:
                markupColor.withAlphaComponent(0.45).setFill()
                UIBezierPath(rect: r).fill()
            case .select:
                UIColor.systemBlue.withAlphaComponent(0.3).setFill()
                UIBezierPath(rect: r).fill()
            case .underline, .strike:
                let y = markupStyle == .underline ? r.maxY - 1 : r.midY
                let line = UIBezierPath()
                line.move(to: CGPoint(x: r.minX, y: y))
                line.addLine(to: CGPoint(x: r.maxX, y: y))
                line.lineWidth = max(1.5, r.height * 0.08)
                markupColor.setStroke()
                line.stroke()
            }
        }
        if let first = stroke.first {
            let path = UIBezierPath()
            path.move(to: first)
            if stroke.count == 1 { path.addLine(to: CGPoint(x: first.x + 0.01, y: first.y)) }
            stroke.dropFirst().forEach { path.addLine(to: $0) }
            path.lineWidth = strokeWidth
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            strokeColor.setStroke()
            path.stroke()
        }
        if let clip = clipRect {
            UIColor(red: 0.43, green: 0.16, blue: 0.85, alpha: 0.13).setFill()
            UIBezierPath(rect: clip).fill()
            let border = UIBezierPath(rect: clip)
            border.lineWidth = 2
            border.setLineDash([8, 5], count: 2, phase: 0)
            UIColor(red: 0.43, green: 0.16, blue: 0.85, alpha: 1).setStroke()
            border.stroke()
        }
        if let center = eraserCenter {
            let circle = UIBezierPath(arcCenter: center, radius: 18, startAngle: 0, endAngle: 2 * .pi, clockwise: true)
            UIColor.black.withAlphaComponent(0.15).setFill()
            circle.fill()
            circle.lineWidth = 2
            UIColor.white.setStroke()
            circle.stroke()
        }
    }
}
