import SwiftUI
import PencilKit

/// SwiftUI wrapper around the note canvas.
struct NoteCanvasRepresentable: UIViewRepresentable {
    @ObservedObject var model: NoteEditorModel

    func makeUIView(context: Context) -> NoteCanvasView {
        let canvas = NoteCanvasView(model: model)
        canvas.accessibilityIdentifier = "notes.canvas"
        return canvas
    }

    func updateUIView(_ canvas: NoteCanvasView, context: Context) {
        canvas.sync()
    }
}

/// The whole note as one zoomable PencilKit canvas: pages stacked vertically in document coordinates
/// (see `NoteLayout`). Paper and elements sit under the ink; study tape, the selection, lasso and laser above it.
final class NoteCanvasView: PKCanvasView, UIGestureRecognizerDelegate, UITextViewDelegate {
    private weak var model: NoteEditorModel?
    private let underlay = UIView()
    private let overlay = NoteOverlayView()
    private var pageViews: [NotePageView] = []
    private let textEditor = UITextView()

    // State mirrored from the model
    private var lastRevision = -1
    private var lastPages: [NotePage] = []
    private var lastRevealed: Set<String> = []
    private var lastSelection: NoteSelection?
    private var lastEditing: String?
    private var lastScrollRequest: UUID?
    private var fitted = false
    private var lastFitWidth: CGFloat = 0

    // Ink commits
    private var committedInk = PKDrawing()
    private var usingTool = false
    private var applying = false

    // Gestures
    private var tapRecognizer: UITapGestureRecognizer!
    private var panRecognizer: UIPanGestureRecognizer!
    private enum PanMode { case none, lasso, tape, laser, eraseHighlighter, move, resize }
    private var panMode: PanMode = .none
    private var panStart: CGPoint = .zero
    private var lassoPoints: [CGPoint] = []
    private var resizeAnchor: CGPoint = .zero
    private var lastPanPoint: CGPoint = .zero

    /// PencilKit registers its own undo here; the note keeps one history in `NoteEditorModel` instead.
    private let pencilKitUndo = UndoManager()
    override var undoManager: UndoManager? { pencilKitUndo }

    /// PencilKit forwards private delegate calls to any delegate that answers them, so a canvas that is its own
    /// delegate recurses forever when drawing starts. A separate object passes the events back instead.
    private let events = CanvasEvents()

    init(model: NoteEditorModel) {
        self.model = model
        super.init(frame: .zero)
        backgroundColor = UIColor(Color(hex: "#E5E9F0"))
        isOpaque = true
        events.canvas = self
        delegate = events
        alwaysBounceVertical = true
        showsHorizontalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        bouncesZoom = true

        underlay.isUserInteractionEnabled = false
        insertSubview(underlay, at: 0)
        addSubview(overlay)

        textEditor.delegate = self
        textEditor.isScrollEnabled = false
        textEditor.backgroundColor = .clear
        textEditor.textContainerInset = UIEdgeInsets(top: 6, left: 4, bottom: 6, right: 4)
        textEditor.accessibilityIdentifier = "notes.textEditor"
        textEditor.isHidden = true
        overlay.addSubview(textEditor)

        tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tapRecognizer.delegate = self
        addGestureRecognizer(tapRecognizer)

        panRecognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        panRecognizer.maximumNumberOfTouches = 1
        panRecognizer.delegate = self
        addGestureRecognizer(panRecognizer)
        panGestureRecognizer.require(toFail: panRecognizer)

        let pencil = PencilDetector(target: nil, action: nil)
        pencil.onPencil = { [weak self] in
            // Once Apple Pencil is used, fingers scroll (turn "Draw with finger" back on to ink with fingers)
            guard let model = self?.model, !model.stylusSeen else { return }
            model.stylusSeen = true
        }
        pencil.delegate = self
        addGestureRecognizer(pencil)

        drawing = model.drawing
        committedInk = model.drawing
        lastRevision = model.drawingRevision

        model.formatHandler = { [weak self] in self?.applyFormat($0) }
        model.pageFrameProvider = { [weak self] index in
            guard let self else { return .zero }
            let f = NoteLayout.frame(ofPage: index)
            let inView = CGRect(x: f.minX * self.zoomScale, y: f.minY * self.zoomScale, width: f.width * self.zoomScale, height: f.height * self.zoomScale)
            return self.convert(inView, to: nil)
        }
        model.zoomProvider = { [weak self] in
            guard let self, self.minimumZoomScale > 0 else { return 1 }
            return self.zoomScale / self.minimumZoomScale
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    // MARK: - Layout & zoom

    private var documentSize: CGSize { NoteLayout.contentSize(pageCount: model?.pageCount ?? 1) }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        if abs(bounds.width - lastFitWidth) > 0.5 {
            let fit = bounds.width / documentSize.width
            let wasFit = !fitted || abs(zoomScale - minimumZoomScale) < 0.001
            lastFitWidth = bounds.width
            minimumZoomScale = fit
            maximumZoomScale = fit * 5
            if wasFit { zoomScale = fit }
            fitted = true
        }
        syncLayers()
        bringSubviewToFront(overlay)
    }

    private func syncLayers() {
        let z = zoomScale
        let doc = documentSize
        for layer in [underlay, overlay] as [UIView] {
            layer.transform = .identity
            layer.bounds = CGRect(origin: .zero, size: doc)
            layer.transform = CGAffineTransform(scaleX: z, y: z)
            layer.center = CGPoint(x: doc.width * z / 2, y: doc.height * z / 2)
        }
        let size = CGSize(width: doc.width * z, height: doc.height * z)
        if contentSize != size { contentSize = size }
        overlay.zoom = z
    }

    fileprivate func didZoom() {
        syncLayers()
    }

    fileprivate func didEndZooming(atScale scale: CGFloat) {
        // Re-render paper and text at the new zoom so they stay sharp
        let factor = min(UIScreen.main.scale * scale, 9)
        for page in pageViews { page.setRenderScale(factor) }
        overlay.setRenderScale(factor)
    }

    fileprivate func didScroll() {
        guard let model, zoomScale > 0 else { return }
        let centerY = (contentOffset.y + bounds.height / 2) / zoomScale
        let page = NoteLayout.pageIndex(atY: centerY, pageCount: model.pageCount)
        if page != model.currentPage { model.currentPage = page }
    }

    // MARK: - Sync from the model

    func sync() {
        guard let model else { return }

        if model.drawingRevision != lastRevision {
            lastRevision = model.drawingRevision
            applying = true
            drawing = model.drawing
            committedInk = model.drawing
            applying = false
        }
        if model.note.pages != lastPages {
            let countChanged = model.note.pages.count != lastPages.count
            lastPages = model.note.pages
            rebuildPages()
            if countChanged { syncLayers() }
        }
        if model.revealedTapes != lastRevealed || model.selection != lastSelection {
            lastRevealed = model.revealedTapes
            lastSelection = model.selection
            rebuildOverlay()
        }
        if model.editingElementId != lastEditing {
            lastEditing = model.editingElementId
            updateTextEditor()
        }
        if let request = model.scrollRequest, request.id != lastScrollRequest {
            lastScrollRequest = request.id
            scroll(toPage: request.page)
        }
        applyTool()
    }

    private func applyTool() {
        guard let model else { return }
        let tool = model.tool
        let fingerInks = model.fingerDrawing && !model.stylusSeen
        let policy: PKCanvasViewDrawingPolicy = fingerInks ? .anyInput : .pencilOnly
        if drawingPolicy != policy { drawingPolicy = policy }

        switch tool {
        case .pen, .shape:
            self.tool = model.pen.tool
        case .eraser:
            switch model.eraserMode {
            case .stroke: self.tool = PKEraserTool(.vector)
            case .partial: self.tool = PKEraserTool(.bitmap, width: model.eraserSize)
            case .highlighter: break
            }
        default:
            break
        }
        // No inking while a text box is open, so taps in the text only move the cursor
        let inks = tool.usesPencilKit && !(tool == .eraser && model.eraserMode == .highlighter) && model.editingElementId == nil
        drawingGestureRecognizer.isEnabled = inks
        tapRecognizer.isEnabled = !inks || model.editingElementId != nil
        if !inks {
            // Fingers scroll unless a one-finger tool (lasso, tape, laser, eraser) claims the touch
            if pencilKitPanTouches == nil { pencilKitPanTouches = panGestureRecognizer.minimumNumberOfTouches }
            panGestureRecognizer.minimumNumberOfTouches = tool == .hand || tool == .text ? 1 : 2
        } else if let saved = pencilKitPanTouches {
            // Hand scrolling back to PencilKit's own setting
            panGestureRecognizer.minimumNumberOfTouches = saved
            pencilKitPanTouches = nil
        }
    }

    private var pencilKitPanTouches: Int?

    /// PencilKit's canvas hides views added to it from accessibility; expose the open text box and the tapes.
    override var accessibilityElements: [Any]? {
        get {
            if !textEditor.isHidden { return [textEditor] }
            return (super.accessibilityElements ?? []) + overlay.subviews.filter { $0 is TapeView }
        }
        set { super.accessibilityElements = newValue }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // Touches inside the open text box belong to it
        if (gestureRecognizer === tapRecognizer || gestureRecognizer === panRecognizer),
           let view = touch.view, view === textEditor || view.isDescendant(of: textEditor) {
            return false
        }
        return true
    }

    private func scroll(toPage index: Int) {
        let frame = NoteLayout.frame(ofPage: index)
        let y = max(0, min(frame.minY * zoomScale - 12, contentSize.height - bounds.height))
        setContentOffset(CGPoint(x: contentOffset.x, y: y), animated: true)
    }

    // MARK: - Pages and elements

    private func rebuildPages() {
        guard let model else { return }
        while pageViews.count < model.pageCount {
            let view = NotePageView()
            underlay.addSubview(view)
            pageViews.append(view)
        }
        while pageViews.count > model.pageCount { pageViews.removeLast().removeFromSuperview() }
        let isDarkFor: (Int) -> Bool = { model.note.pages[$0].paperTheme.isDark }
        for (i, view) in pageViews.enumerated() {
            view.frame = NoteLayout.frame(ofPage: i)
            view.configure(page: model.note.pages[i], dark: isDarkFor(i), editingId: model.editingElementId)
        }
        rebuildOverlay()
        updateTextEditor()
    }

    private func rebuildOverlay() {
        guard let model else { return }
        var tapes: [(CGRect, NoteElement, Bool)] = []
        for (p, page) in model.note.pages.enumerated() {
            let origin = NoteLayout.origin(ofPage: p)
            for el in page.elements where el.kind == .TAPE {
                let r = CGRect(x: origin.x + CGFloat(el.x), y: origin.y + CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height))
                tapes.append((r, el, model.revealedTapes.contains(el.id)))
            }
        }
        overlay.setTapes(tapes)
        overlay.selectionRect = model.selectionBounds()
    }

    // MARK: - Inline text editing

    private func updateTextEditor() {
        guard let model, let id = model.editingElementId, let (p, el) = model.element(id) else {
            if !textEditor.isHidden {
                textEditor.resignFirstResponder()
                textEditor.isHidden = true
            }
            pageViews.forEach { $0.hiddenElementId = nil }
            return
        }
        let origin = NoteLayout.origin(ofPage: p)
        let dark = model.note.pages[p].paperTheme.isDark
        textEditor.frame = CGRect(x: origin.x + CGFloat(el.x), y: origin.y + CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height))
        textEditor.font = NoteElementStyle.font(for: el)
        textEditor.textColor = NoteElementStyle.textColor(for: el, darkPaper: dark)
        textEditor.backgroundColor = el.kind == .STICKY ? UIColor(Color(hex: el.fillColor ?? noteStickyColors[0])) : UIColor.systemBackground.withAlphaComponent(dark ? 0.1 : 0.85)
        textEditor.layer.cornerRadius = 6
        textEditor.layer.borderWidth = 1.5 / max(zoomScale, 0.1)
        textEditor.layer.borderColor = UIColor(Theme.brandPurple).cgColor
        if textEditor.text != el.content { textEditor.text = el.content }
        textEditor.isHidden = false
        pageViews.forEach { $0.hiddenElementId = id }
        // Focus after the current SwiftUI update; UIKit ignores first-responder changes made inside it
        DispatchQueue.main.async { [weak self] in
            guard let self, self.model?.editingElementId == id, !self.textEditor.isFirstResponder else { return }
            self.textEditor.becomeFirstResponder()
        }
    }

    func textViewDidChange(_ textView: UITextView) {
        guard let model, let id = model.editingElementId, let (_, el) = model.element(id) else { return }
        let fit = textView.sizeThatFits(CGSize(width: CGFloat(el.width), height: .greatestFiniteMagnitude))
        let height = max(CGFloat(el.kind == .STICKY ? 100 : 36), fit.height)
        textView.frame.size.height = height
        model.updateElement(id, commit: false) {
            $0.content = textView.text
            $0.height = Float(height)
        }
        lastPages = model.note.pages
    }

    func textViewDidEndEditing(_ textView: UITextView) {
        if model?.editingElementId != nil { model?.finishEditing() }
    }

    /// Inserts Markdown-style formatting around the selection (bold, italic) or at the line start (list, checklist, heading).
    func applyFormat(_ format: NoteTextFormat) {
        guard !textEditor.isHidden else { return }
        let range = textEditor.selectedRange
        let text = textEditor.text as NSString
        switch format {
        case .bold, .italic:
            let mark = format == .bold ? "**" : "_"
            let selected = text.substring(with: range)
            textEditor.textStorage.replaceCharacters(in: range, with: "\(mark)\(selected)\(mark)")
            textEditor.selectedRange = NSRange(location: range.location + mark.count, length: range.length)
        case .checklist, .bullet, .heading:
            let prefix = format == .checklist ? "- [ ] " : (format == .bullet ? "• " : "# ")
            let lineStart = text.lineRange(for: NSRange(location: range.location, length: 0)).location
            textEditor.textStorage.replaceCharacters(in: NSRange(location: lineStart, length: 0), with: prefix)
            textEditor.selectedRange = NSRange(location: range.location + prefix.count, length: range.length)
        }
        textViewDidChange(textEditor)
    }

    // MARK: - Ink commits from PencilKit

    fileprivate func didBeginUsingTool() {
        usingTool = true
    }

    fileprivate func didEndUsingTool() {
        usingTool = false
        DispatchQueue.main.async { [weak self] in self?.commitInk() }
    }

    fileprivate func drawingDidChange() {
        guard !applying, drawing != committedInk else { return }
        if !usingTool { commitInk() }
    }

    private func commitInk() {
        guard let model, !applying, drawing != committedInk else { return }
        var after = drawing
        // Shape tool: snap the new stroke to a clean shape
        if model.tool == .shape, after.strokes.count == committedInk.strokes.count + 1, let stroke = after.strokes.last {
            let points = stroke.documentPoints
            let snapped: [CGPoint]?
            if let kind = model.shapeKind, let first = points.first, let last = points.last, hypot(last.x - first.x, last.y - first.y) > 6 {
                snapped = ShapeRecognizer.outline(kind, from: first, to: last)
            } else {
                snapped = ShapeRecognizer.recognize(points)?.points
            }
            if let snapped, snapped.count > 1 {
                let size = stroke.path.first?.size ?? CGSize(width: model.pen.width, height: model.pen.width)
                let controlPoints = snapped.enumerated().map { i, p in
                    PKStrokePoint(location: p, timeOffset: TimeInterval(i) * 0.005, size: size, opacity: 1,
                                  force: 1, azimuth: 0, altitude: .pi / 2)
                }
                var strokes = after.strokes
                strokes[strokes.count - 1] = PKStroke(ink: stroke.ink, path: PKStrokePath(controlPoints: controlPoints, creationDate: Date()))
                after = PKDrawing(strokes: strokes)
                applying = true
                drawing = after
                applying = false
            }
        }
        model.inkChanged(from: committedInk, to: after)
        committedInk = after
    }

    // MARK: - Taps

    private func documentPoint(_ recognizer: UIGestureRecognizer) -> CGPoint {
        let p = recognizer.location(in: self)
        return CGPoint(x: p.x / zoomScale, y: p.y / zoomScale)
    }

    /// Topmost element under a document point (tapes first, they cover everything).
    private func elementHit(at point: CGPoint) -> (page: Int, element: NoteElement)? {
        guard let model else { return nil }
        let page = NoteLayout.pageIndex(atY: point.y, pageCount: model.pageCount)
        let origin = NoteLayout.origin(ofPage: page)
        let local = CGPoint(x: point.x - origin.x, y: point.y - origin.y)
        let candidates = model.note.pages[page].elements.sorted { ($0.kind == .TAPE ? 1 : 0, $0.z) > ($1.kind == .TAPE ? 1 : 0, $1.z) }
        for el in candidates {
            let r = CGRect(x: CGFloat(el.x), y: CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height)).insetBy(dx: -6, dy: -6)
            if r.contains(local) { return (page, el) }
        }
        return nil
    }

    @objc private func handleTap(_ recognizer: UITapGestureRecognizer) {
        guard let model, recognizer.state == .ended else { return }
        let point = documentPoint(recognizer)
        if model.editingElementId != nil {
            textEditor.resignFirstResponder()
            model.finishEditing()
            return
        }
        let page = NoteLayout.pageIndex(atY: point.y, pageCount: model.pageCount)
        let origin = NoteLayout.origin(ofPage: page)
        let local = CGPoint(x: point.x - origin.x, y: point.y - origin.y)

        if let (_, el) = elementHit(at: point) {
            if el.kind == .TAPE {
                model.toggleTape(el.id)
                return
            }
            let editable = el.kind == .TEXT || el.kind == .STICKY
            let alreadySelected = model.selection?.elementIds == [el.id]
            if editable && (model.tool == .text || alreadySelected) {
                model.selection = nil
                model.checkpoint()
                model.editingElementId = el.id
            } else {
                model.select(element: el.id)
            }
            return
        }
        if model.selection != nil {
            model.selection = nil
            return
        }
        if model.tool == .text, NoteLayout.frame(ofPage: page).contains(point) {
            model.addTextBox(page: page, at: local)
        }
    }

    // MARK: - One-finger tools

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === panRecognizer, let model else { return super.gestureRecognizerShouldBegin(gestureRecognizer) }
        // While inking, PencilKit owns one-finger touches and this recognizer steps aside at once
        if drawingGestureRecognizer.isEnabled && model.selection == nil { return false }
        let point = documentPoint(gestureRecognizer)
        if let rect = model.selectionBounds() {
            if overlay.handleRect.insetBy(dx: -18 / zoomScale, dy: -18 / zoomScale).contains(point) { return true }
            if rect.insetBy(dx: -12 / zoomScale, dy: -12 / zoomScale).contains(point) { return true }
        }
        switch model.tool {
        case .lasso, .tape, .laser: return true
        case .eraser: return model.eraserMode == .highlighter
        default: return false
        }
    }

    @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
        guard let model else { return }
        let point = documentPoint(recognizer)
        switch recognizer.state {
        case .began:
            // The pan starts after the finger has moved a little; begin where it first touched down
            let moved = recognizer.translation(in: self)
            let start = CGPoint(x: point.x - moved.x / zoomScale, y: point.y - moved.y / zoomScale)
            panStart = start
            lastPanPoint = point
            if let rect = model.selectionBounds() {
                if overlay.handleRect.insetBy(dx: -18 / zoomScale, dy: -18 / zoomScale).contains(start) {
                    panMode = .resize
                    resizeAnchor = CGPoint(x: rect.minX, y: rect.minY)
                    model.beginTransform()
                    return
                }
                if rect.insetBy(dx: -12 / zoomScale, dy: -12 / zoomScale).contains(start) {
                    panMode = .move
                    model.beginTransform()
                    model.transformSelection(translation: CGVector(dx: point.x - start.x, dy: point.y - start.y), scale: 1, anchor: .zero)
                    return
                }
            }
            switch model.tool {
            case .lasso:
                panMode = .lasso
                model.selection = nil
                lassoPoints = [start, point]
            case .tape:
                panMode = .tape
            case .laser:
                panMode = .laser
                overlay.beginLaser(at: start)
                overlay.extendLaser(to: point)
            case .eraser where model.eraserMode == .highlighter:
                panMode = .eraseHighlighter
                model.beginHighlighterErase()
                model.eraseHighlighter(from: start, to: point, radius: model.eraserSize / 2)
            default:
                panMode = .none
            }

        case .changed:
            panMoved(to: point)

        case .ended, .cancelled, .failed:
            // The last movement is only reported with the end of the gesture
            if recognizer.state == .ended { panMoved(to: point) }
            finishPan(ended: recognizer.state == .ended)
            panMode = .none
        default:
            break
        }
    }

    private func panMoved(to point: CGPoint) {
        guard let model else { return }
        switch panMode {
            case .move:
                model.transformSelection(translation: CGVector(dx: point.x - panStart.x, dy: point.y - panStart.y), scale: 1, anchor: .zero)
            case .resize:
                let startDistance = max(hypot(panStart.x - resizeAnchor.x, panStart.y - resizeAnchor.y), 1)
                let scale = max(0.15, hypot(point.x - resizeAnchor.x, point.y - resizeAnchor.y) / startDistance)
                model.transformSelection(translation: .zero, scale: scale, anchor: resizeAnchor)
            case .lasso:
                lassoPoints.append(point)
                overlay.lassoPath = lassoPoints
            case .tape:
                overlay.tapePreview = CGRect(x: min(panStart.x, point.x), y: min(panStart.y, point.y),
                                             width: abs(point.x - panStart.x), height: abs(point.y - panStart.y))
                overlay.tapeColor = UIColor(Color(hex: model.tapeColor))
            case .laser:
                overlay.extendLaser(to: point)
            case .eraseHighlighter:
                model.eraseHighlighter(from: lastPanPoint, to: point, radius: model.eraserSize / 2)
            case .none:
                break
        }
        lastPanPoint = point
    }

    private func finishPan(ended: Bool) {
        guard let model else { return }
        switch panMode {
            case .move, .resize:
                model.endTransform()
            case .lasso:
                overlay.lassoPath = []
                if ended { model.lassoSelect(polygon: lassoPoints) }
                lassoPoints = []
            case .tape:
                let rect = overlay.tapePreview ?? .zero
                overlay.tapePreview = nil
                if ended, rect.width > 12 || rect.height > 12 {
                    let page = NoteLayout.pageIndex(atY: rect.midY, pageCount: model.pageCount)
                    let origin = NoteLayout.origin(ofPage: page)
                    model.addTape(page: page, rect: rect.offsetBy(dx: -origin.x, dy: -origin.y))
                }
            case .laser:
                overlay.endLaser()
            case .eraseHighlighter:
                model.endHighlighterErase()
            case .none:
                break
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        gestureRecognizer is PencilDetector || other is PencilDetector
    }
}

/// Receives PencilKit and scroll events for a `NoteCanvasView`.
private final class CanvasEvents: NSObject, PKCanvasViewDelegate {
    weak var canvas: NoteCanvasView?

    func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) { canvas?.drawingDidChange() }
    func canvasViewDidBeginUsingTool(_ canvasView: PKCanvasView) { canvas?.didBeginUsingTool() }
    func canvasViewDidEndUsingTool(_ canvasView: PKCanvasView) { canvas?.didEndUsingTool() }
    func scrollViewDidZoom(_ scrollView: UIScrollView) { canvas?.didZoom() }
    func scrollViewDidScroll(_ scrollView: UIScrollView) { canvas?.didScroll() }
    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        canvas?.didEndZooming(atScale: scale)
    }
}

// MARK: - Pencil detection

/// Notices Apple Pencil touches without taking part in any gesture.
final class PencilDetector: UIGestureRecognizer {
    var onPencil: () -> Void = {}

    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        allowedTouchTypes = [NSNumber(value: UITouch.TouchType.pencil.rawValue)]
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if touches.contains(where: { $0.type == .pencil }) { onPencil() }
        state = .failed
    }
}

// MARK: - Text formats

enum NoteTextFormat { case bold, italic, bullet, checklist, heading }

// MARK: - Element styling and rich text

enum NoteElementStyle {
    static func font(for el: NoteElement) -> UIFont {
        switch el.kind {
        case .STICKY: return .systemFont(ofSize: 15, weight: .medium)
        case .QUOTE: return UIFont(descriptor: UIFont.systemFont(ofSize: 14).fontDescriptor.withDesign(.serif)?.withSymbolicTraits(.traitItalic) ?? UIFont.systemFont(ofSize: 14).fontDescriptor, size: 14)
        default: return .systemFont(ofSize: 16)
        }
    }

    static func textColor(for el: NoteElement, darkPaper: Bool) -> UIColor {
        if el.kind == .TEXT, let hex = el.fillColor { return UIColor(Color(hex: hex)) }
        if el.kind == .STICKY { return UIColor(Color(hex: "#1E293B")) }
        return darkPaper ? UIColor(white: 0.95, alpha: 1) : UIColor(Color(hex: "#111827"))
    }

    /// Light Markdown: **bold**, _italic_, "# " headings, "- [ ]" / "- [x]" checklists and "• " bullets.
    static func attributed(_ text: String, font: UIFont, color: UIColor) -> NSAttributedString {
        let out = NSMutableAttributedString()
        let lines = text.components(separatedBy: "\n")
        for (i, rawLine) in lines.enumerated() {
            var line = rawLine
            var lineFont = font
            if line.hasPrefix("# ") {
                line.removeFirst(2)
                lineFont = .systemFont(ofSize: font.pointSize * 1.35, weight: .bold)
            } else if line.hasPrefix("- [ ] ") {
                line = "☐ " + line.dropFirst(6)
            } else if line.lowercased().hasPrefix("- [x] ") {
                line = "☑︎ " + line.dropFirst(6)
            }
            let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
            let parsed = (try? NSMutableAttributedString(AttributedString(markdown: line, options: options)))
                ?? NSMutableAttributedString(string: line)
            let full = NSRange(location: 0, length: parsed.length)
            parsed.addAttribute(.foregroundColor, value: color, range: full)
            parsed.enumerateAttribute(.inlinePresentationIntent, in: full) { value, range, _ in
                var traits: UIFontDescriptor.SymbolicTraits = []
                if let raw = value as? UInt {
                    let intent = InlinePresentationIntent(rawValue: raw)
                    if intent.contains(.stronglyEmphasized) { traits.insert(.traitBold) }
                    if intent.contains(.emphasized) { traits.insert(.traitItalic) }
                }
                let base = lineFont.fontDescriptor
                let descriptor = base.withSymbolicTraits(base.symbolicTraits.union(traits)) ?? base
                parsed.addAttribute(.font, value: UIFont(descriptor: descriptor, size: lineFont.pointSize), range: range)
            }
            out.append(parsed)
            if i < lines.count - 1 { out.append(NSAttributedString(string: "\n", attributes: [.font: font])) }
        }
        return out
    }

    /// Images were saved with absolute paths; the app container path changes between installs.
    static func image(at path: String?) -> UIImage? {
        guard let path else { return nil }
        if let img = UIImage(contentsOfFile: path) { return img }
        let fallback = StorageManager.shared.documentsDirectory.appendingPathComponent("Photos")
            .appendingPathComponent((path as NSString).lastPathComponent)
        return UIImage(contentsOfFile: fallback.path)
    }
}

// MARK: - Page view (paper + elements under the ink)

final class NotePageView: UIView {
    private var page: NotePage?
    private var dark = false
    private var elementViews: [String: UIView] = [:]
    var hiddenElementId: String? { didSet { elementViews.forEach { $0.value.isHidden = $0.key == hiddenElementId } } }

    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.12
        layer.shadowRadius = 8
        layer.shadowOffset = CGSize(width: 0, height: 3)
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func configure(page: NotePage, dark: Bool, editingId: String?) {
        let backgroundChanged = self.page?.background != page.background || self.page?.paperTheme != page.paperTheme
        let elementsChanged = self.page?.elements != page.elements
        self.page = page
        self.dark = dark
        if backgroundChanged { setNeedsDisplay() }
        if elementsChanged || elementViews.isEmpty { rebuildElements() }
        hiddenElementId = editingId
    }

    func setRenderScale(_ factor: CGFloat) {
        contentScaleFactor = factor
        setNeedsDisplay()
        func apply(_ v: UIView) {
            v.contentScaleFactor = factor
            v.layer.contentsScale = factor
            if v is UILabel { v.setNeedsDisplay() }
            v.subviews.forEach(apply)
        }
        subviews.forEach(apply)
    }

    override func draw(_ rect: CGRect) {
        guard let page, let ctx = UIGraphicsGetCurrentContext() else { return }
        PageTemplateRenderer.draw(context: ctx, background: page.background, paperTheme: page.paperTheme, rect: bounds)
    }

    private func rebuildElements() {
        subviews.forEach { $0.removeFromSuperview() }
        elementViews.removeAll()
        guard let page else { return }
        for el in page.elements.sorted(by: { $0.z < $1.z }) where el.kind != .TAPE {
            let view = Self.makeView(for: el, darkPaper: dark)
            view.frame = CGRect(x: CGFloat(el.x), y: CGFloat(el.y), width: CGFloat(el.width), height: CGFloat(el.height))
            view.accessibilityIdentifier = "notes.element.\(el.kind.rawValue)"
            addSubview(view)
            elementViews[el.id] = view
        }
    }

    static func makeView(for el: NoteElement, darkPaper: Bool) -> UIView {
        let container = UIView()
        container.isUserInteractionEnabled = false
        let font = NoteElementStyle.font(for: el)
        let color = NoteElementStyle.textColor(for: el, darkPaper: darkPaper)
        func label(_ text: String, inset: UIEdgeInsets) -> UILabel {
            let l = UILabel()
            l.numberOfLines = 0
            l.attributedText = NoteElementStyle.attributed(text, font: font, color: color)
            l.frame = CGRect(x: inset.left, y: inset.top, width: CGFloat(el.width) - inset.left - inset.right,
                             height: CGFloat(el.height) - inset.top - inset.bottom)
            l.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            return l
        }

        switch el.kind {
        case .STICKY:
            container.backgroundColor = UIColor(Color(hex: el.fillColor ?? noteStickyColors[0]))
            container.layer.cornerRadius = 4
            container.layer.shadowColor = UIColor.black.cgColor
            container.layer.shadowOpacity = 0.18
            container.layer.shadowRadius = 4
            container.layer.shadowOffset = CGSize(width: 0, height: 2)
            let l = label(el.content.isEmpty ? "Tap to write" : el.content, inset: UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10))
            if el.content.isEmpty { l.textColor = UIColor(Color(hex: "#1E293B")).withAlphaComponent(0.4) }
            l.lineBreakMode = .byTruncatingTail
            container.addSubview(l)
        case .TEXT:
            let l = label(el.content, inset: UIEdgeInsets(top: 6, left: 9, bottom: 6, right: 9))
            l.frame.size.height = max(l.frame.height, l.sizeThatFits(CGSize(width: l.frame.width, height: .greatestFiniteMagnitude)).height)
            container.addSubview(l)
        case .QUOTE:
            container.backgroundColor = UIColor(Theme.brandPurpleSoft).withAlphaComponent(darkPaper ? 0.2 : 0.8)
            container.layer.cornerRadius = 6
            let bar = UIView(frame: CGRect(x: 0, y: 0, width: 4, height: CGFloat(el.height)))
            bar.backgroundColor = UIColor(Color(hex: el.fillColor ?? "#5935FF"))
            bar.autoresizingMask = [.flexibleHeight]
            container.addSubview(bar)
            let caption = UILabel(frame: CGRect(x: 12, y: 6, width: CGFloat(el.width) - 18, height: 14))
            caption.font = .systemFont(ofSize: 10, weight: .bold)
            caption.textColor = UIColor(Theme.brandPurple)
            let title = el.sourceBookId.flatMap { id in StorageManager.shared.books.first { $0.id == id }?.title } ?? "Quote"
            caption.text = el.sourcePage.map { "\(title.uppercased()) · P. \($0 + 1)" } ?? title.uppercased()
            container.addSubview(caption)
            let body = label(el.quoteText ?? el.content, inset: UIEdgeInsets(top: 22, left: 12, bottom: 6, right: 8))
            container.addSubview(body)
        case .IMAGE:
            let iv = UIImageView(image: NoteElementStyle.image(at: el.imagePath))
            iv.contentMode = .scaleAspectFill
            iv.clipsToBounds = true
            iv.layer.cornerRadius = 4
            iv.backgroundColor = UIColor.systemGray5
            iv.frame = CGRect(x: 0, y: 0, width: CGFloat(el.width), height: CGFloat(el.height))
            iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            container.addSubview(iv)
            if el.sourceBookId != nil {
                container.layer.borderWidth = 1
                container.layer.borderColor = UIColor(Theme.brandPurple).withAlphaComponent(0.4).cgColor
            }
        case .TAPE:
            break
        }
        return container
    }
}

// MARK: - Overlay (above the ink)

final class NoteOverlayView: UIView {
    var zoom: CGFloat = 1 { didSet { if zoom != oldValue { updateChrome() } } }
    var selectionRect: CGRect? { didSet { updateChrome() } }
    var lassoPath: [CGPoint] = [] { didSet { updateLasso() } }
    var tapePreview: CGRect? { didSet { updateTapePreview() } }
    var tapeColor: UIColor = .orange
    private(set) var handleRect: CGRect = .null

    private var tapeViews: [UIView] = []
    private let selectionLayer = CAShapeLayer()
    private let handleLayer = CAShapeLayer()
    private let lassoLayer = CAShapeLayer()
    private let tapePreviewLayer = CAShapeLayer()
    private let laserLayer = CAShapeLayer()
    private var laserPoints: [CGPoint] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        for l in [selectionLayer, lassoLayer, tapePreviewLayer] {
            l.fillColor = UIColor.clear.cgColor
            l.strokeColor = UIColor(Theme.brandPurple).cgColor
            layer.addSublayer(l)
        }
        lassoLayer.fillColor = UIColor(Theme.brandPurple).withAlphaComponent(0.06).cgColor
        handleLayer.fillColor = UIColor.white.cgColor
        handleLayer.strokeColor = UIColor(Theme.brandPurple).cgColor
        layer.addSublayer(handleLayer)
        laserLayer.fillColor = UIColor.clear.cgColor
        laserLayer.strokeColor = UIColor.systemRed.withAlphaComponent(0.85).cgColor
        laserLayer.lineCap = .round
        laserLayer.lineJoin = .round
        laserLayer.shadowColor = UIColor.systemRed.cgColor
        laserLayer.shadowOpacity = 0.9
        laserLayer.shadowRadius = 6
        laserLayer.shadowOffset = .zero
        layer.addSublayer(laserLayer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    /// Only the inline text editor takes touches; everything else falls through to the canvas.
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        for sub in subviews.reversed() where sub is UITextView && !sub.isHidden {
            if sub.frame.contains(point) { return sub.hitTest(convert(point, to: sub), with: event) }
        }
        return nil
    }

    func setRenderScale(_ factor: CGFloat) {
        for v in tapeViews { v.contentScaleFactor = factor; v.setNeedsDisplay() }
        for l in [selectionLayer, handleLayer, lassoLayer, tapePreviewLayer, laserLayer] { l.contentsScale = factor }
        subviews.compactMap { $0 as? UITextView }.forEach { $0.contentScaleFactor = factor }
    }

    func setTapes(_ tapes: [(CGRect, NoteElement, Bool)]) {
        tapeViews.forEach { $0.removeFromSuperview() }
        tapeViews = tapes.map { rect, el, revealed in
            let v = TapeView(frame: rect)
            v.color = UIColor(Color(hex: el.fillColor ?? noteTapeColors[0]))
            v.revealed = revealed
            v.accessibilityIdentifier = revealed ? "notes.tape.revealed" : "notes.tape"
            v.isAccessibilityElement = true
            insertSubview(v, at: 0)
            return v
        }
    }

    private func updateChrome() {
        let line = 1.5 / max(zoom, 0.05)
        selectionLayer.lineWidth = line
        selectionLayer.lineDashPattern = [NSNumber(value: Double(6 / zoom)), NSNumber(value: Double(4 / zoom))]
        lassoLayer.lineWidth = line
        lassoLayer.lineDashPattern = selectionLayer.lineDashPattern
        tapePreviewLayer.lineWidth = line
        laserLayer.lineWidth = 4 / max(zoom, 0.05)
        handleLayer.lineWidth = 2 / max(zoom, 0.05)
        if let r = selectionRect?.insetBy(dx: -8 / zoom, dy: -8 / zoom) {
            selectionLayer.path = UIBezierPath(roundedRect: r, cornerRadius: 4 / zoom).cgPath
            let handle = 9 / zoom
            handleRect = CGRect(x: r.maxX - handle, y: r.maxY - handle, width: handle * 2, height: handle * 2)
            handleLayer.path = UIBezierPath(ovalIn: handleRect).cgPath
        } else {
            selectionLayer.path = nil
            handleLayer.path = nil
            handleRect = .null
        }
    }

    private func updateLasso() {
        guard let first = lassoPath.first else {
            lassoLayer.path = nil
            return
        }
        let path = UIBezierPath()
        path.move(to: first)
        lassoPath.dropFirst().forEach { path.addLine(to: $0) }
        lassoLayer.path = path.cgPath
    }

    private func updateTapePreview() {
        if let r = tapePreview {
            tapePreviewLayer.path = UIBezierPath(rect: r).cgPath
            tapePreviewLayer.fillColor = tapeColor.withAlphaComponent(0.6).cgColor
        } else {
            tapePreviewLayer.path = nil
        }
    }

    // Laser pointer: a glowing trail that fades away
    func beginLaser(at point: CGPoint) {
        laserLayer.removeAllAnimations()
        laserLayer.opacity = 1
        laserPoints = [point]
        drawLaser()
    }

    func extendLaser(to point: CGPoint) {
        laserPoints.append(point)
        if laserPoints.count > 90 { laserPoints.removeFirst(laserPoints.count - 90) }
        drawLaser()
    }

    func endLaser() {
        let fade = CABasicAnimation(keyPath: "opacity")
        fade.fromValue = 1
        fade.toValue = 0
        fade.duration = 0.7
        laserLayer.opacity = 0
        laserLayer.add(fade, forKey: "fade")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) { [weak self] in
            guard let self, self.laserLayer.opacity == 0 else { return }
            self.laserPoints = []
            self.laserLayer.path = nil
        }
    }

    private func drawLaser() {
        guard let first = laserPoints.first else { return }
        let path = UIBezierPath()
        path.move(to: first)
        laserPoints.dropFirst().forEach { path.addLine(to: $0) }
        laserLayer.path = path.cgPath
    }
}

/// Study tape: opaque until tapped, then a faint outline so the content underneath shows.
final class TapeView: UIView {
    var color: UIColor = .orange { didSet { setNeedsDisplay() } }
    var revealed = false { didSet { setNeedsDisplay() } }

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func draw(_ rect: CGRect) {
        let shape = UIBezierPath(roundedRect: bounds.insetBy(dx: 1, dy: 1), cornerRadius: 3)
        if revealed {
            color.withAlphaComponent(0.12).setFill()
            shape.fill()
            shape.setLineDash([5, 3], count: 2, phase: 0)
            shape.lineWidth = 1.5
            color.setStroke()
            shape.stroke()
            return
        }
        color.setFill()
        shape.fill()
        UIColor.white.withAlphaComponent(0.22).setStroke()
        let stripes = UIBezierPath()
        var x = -bounds.height
        while x < bounds.width {
            stripes.move(to: CGPoint(x: x, y: bounds.height))
            stripes.addLine(to: CGPoint(x: x + bounds.height, y: 0))
            x += 12
        }
        stripes.lineWidth = 3
        shape.addClip()
        stripes.stroke()
    }
}
