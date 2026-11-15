import XCTest

/// End-to-end tests for the PDF reader's annotation tools, run against the real app in the simulator.
///
/// The app is launched with `-UITests` (exposes a hidden `reader.state` probe with live annotation
/// counts, tool, page and the page frame on screen) and `-UITestsReset` (clean sample book on page 1).
/// Gestures are aimed at known text on the sample PDF ("Aircraft Systems & Operations", US Letter).
final class ReaderAnnotationUITests: XCTestCase {
    private var app: XCUIApplication!

    /// Dial order in the reader (same as Android).
    private let dialOrder = ["Scroll", "Select Text", "Clip to Note", "Fine Pen", "Medium Pen", "Marker",
                             "Highlight", "Underline", "Strikethrough", "Line", "Arrow", "Rectangle",
                             "Ellipse", "Sticky Note", "Eraser"]

    // Page points on the sample PDF's first page (612 x 792, origin top-left)
    private let firstTextLine: CGFloat = 148      // "Welcome to AHT Books! This sample document..."
    private let emptyAreaY: CGFloat = 640         // below the text

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-UITests", "-UITestsReset"]
        app.launch()
        openSampleBook()
    }

    // MARK: - Pen, undo, redo, clear

    func testPenStrokeUndoRedoAndClearPage() {
        selectTool("Fine Pen", expect: "pen")
        let before = value("drawings")
        drag(from: (120, 520), to: (420, 560))
        waitForValue("drawings", before + 1, "pen stroke is saved")
        XCTAssertEqual(state()["undo"], "true")

        tap("rail.undo")
        waitForValue("drawings", before, "undo removes the stroke")
        tap("rail.redo")
        waitForValue("drawings", before + 1, "redo restores the stroke")

        selectTool("Medium Pen", expect: "pen")
        drag(from: (120, 600), to: (420, 610))
        waitForValue("drawings", before + 2, "medium pen stroke is saved")

        tap("rail.more")
        app.buttons["Clear Page Drawings"].tap()
        waitForValue("drawings", 0, "clear page removes this page's drawings")
        tap("rail.undo")
        waitForValue("drawings", before + 2, "undo brings the cleared drawings back")
    }

    // MARK: - Highlighter and shapes

    func testHighlighterAndAllShapes() {
        selectTool("Marker", expect: "highlighter")
        var count = value("drawings")
        drag(from: (80, 520), to: (400, 520))
        count += 1
        waitForValue("drawings", count, "highlighter stroke is saved")

        for (name, raw) in [("Line", "line"), ("Arrow", "arrow"), ("Rectangle", "rectangle"), ("Ellipse", "ellipse")] {
            selectTool(name, expect: raw)
            drag(from: (140, 560), to: (360, 700))
            count += 1
            waitForValue("drawings", count, "\(name) is saved")
        }

        // A tap with a shape tool draws nothing
        point(300, 700).tap()
        sleep(1)
        XCTAssertEqual(value("drawings"), count, "a tap does not create a shape")
    }

    // MARK: - Text markup tools

    func testHighlightUnderlineStrikethroughSnapToText() {
        selectTool("Highlight", expect: "textHighlight")
        drag(from: (58, firstTextLine), to: (320, firstTextLine))
        waitForValue("highlights", 1, "drag across text highlights it")

        selectTool("Underline", expect: "textUnderline")
        drag(from: (58, firstTextLine), to: (240, firstTextLine))
        waitForValue("underlines", 1, "drag across text underlines it")

        selectTool("Strikethrough", expect: "textStrikethrough")
        point(120, firstTextLine).tap()
        waitForValue("strikes", 1, "a tap strikes through the word under it")

        // No text: nothing is marked and the reader says so
        selectTool("Highlight", expect: "textHighlight")
        drag(from: (100, emptyAreaY), to: (400, emptyAreaY))
        XCTAssertTrue(app.staticTexts["No text there to mark"].waitForExistence(timeout: 3))
        XCTAssertEqual(value("highlights"), 1)

        tap("rail.undo")
        waitForValue("strikes", 0, "undo removes the last text mark")
    }

    // MARK: - Eraser

    func testEraserRemovesStrokesAndMarksAndIsUndoable() {
        selectTool("Fine Pen", expect: "pen")
        drag(from: (100, 600), to: (450, 600))
        waitForValue("drawings", 1)
        selectTool("Highlight", expect: "textHighlight")
        drag(from: (58, firstTextLine), to: (320, firstTextLine))
        waitForValue("highlights", 1)

        selectTool("Eraser", expect: "eraser")
        drag(from: (270, 560), to: (270, 650))
        waitForValue("drawings", 0, "eraser removes the stroke it crosses")
        drag(from: (150, firstTextLine - 20), to: (150, firstTextLine + 20))
        waitForValue("highlights", 0, "eraser removes the highlight it crosses")

        tap("rail.undo")
        waitForValue("highlights", 1, "undo restores the erased highlight")
        tap("rail.undo")
        waitForValue("drawings", 1, "undo restores the erased stroke")
    }

    // MARK: - Sticky notes

    func testStickyNoteSavesTextAndEmptyNoteIsDiscarded() {
        selectTool("Sticky Note", expect: "stickyNote")
        point(500, 660).tap()
        let editor = app.textViews["noteEditor.text"]
        XCTAssertTrue(editor.waitForExistence(timeout: 5), "placing a sticky note opens its editor")
        editor.typeText("Check hydraulic pressure")
        tap("noteEditor.save")
        waitForValue("notes", 1, "sticky note is saved")

        point(450, 720).tap()
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        tap("noteEditor.cancel")
        sleep(1)
        XCTAssertEqual(value("notes"), 1, "cancelling an empty sticky note discards it")

        // Tapping the saved note while reading opens its menu
        tap("rail.done")
        point(500, 660).tap()
        XCTAssertTrue(app.buttons["annotationMenu.delete"].waitForExistence(timeout: 5))
        tap("annotationMenu.delete")
        waitForValue("notes", 0, "delete from the annotation menu removes the note")
    }

    // MARK: - Select text and its actions

    func testSelectTextToolActions() {
        selectTool("Select Text", expect: "selectText")

        selectFirstLine()
        tap("selection.highlight.#86EFAC")
        waitForValue("highlights", 1, "highlight from the selection bar")

        selectFirstLine()
        tap("selection.copy")
        XCTAssertTrue(app.staticTexts["Copied"].waitForExistence(timeout: 3))

        selectFirstLine()
        tap("selection.card")
        XCTAssertTrue(app.navigationBars["New Flashcard"].waitForExistence(timeout: 5), "card editor opens prefilled")
        app.navigationBars["New Flashcard"].buttons["Cancel"].tap()

        selectFirstLine()
        tap("selection.toNotebook")
        XCTAssertTrue(app.buttons["notebookPicker.new"].waitForExistence(timeout: 5))
        tap("notebookPicker.new")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Added to'")).firstMatch
            .waitForExistence(timeout: 5), "quote is added to a notebook")

        selectFirstLine()
        tap("selection.note")
        let noteText = app.textViews["selectionNote.text"]
        XCTAssertTrue(noteText.waitForExistence(timeout: 5))
        noteText.typeText("Key fact")
        tap("selectionNote.save")
        waitForValue("highlights", 2, "note on a selection saves a highlight with the note")
    }

    // MARK: - Clip to note

    func testClipToNoteAddsImageToNotebook() {
        selectTool("Clip to Note", expect: "clip")
        drag(from: (60, 120), to: (420, 260))
        XCTAssertTrue(app.buttons["notebookPicker.new"].waitForExistence(timeout: 5), "dragging a clip opens the notebook picker")
        tap("notebookPicker.new")
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Added to'")).firstMatch
            .waitForExistence(timeout: 5), "clip is added to a notebook")
    }

    // MARK: - Reading mode

    func testReadingModeLongPressSelectionAndAnnotationMenu() {
        // Make a highlight, then leave annotation mode
        selectTool("Highlight", expect: "textHighlight")
        drag(from: (58, firstTextLine), to: (320, firstTextLine))
        waitForValue("highlights", 1)
        tap("rail.done")
        waitForState("tool", "hand")

        // Tap the highlight: menu opens; recolor; delete
        point(150, firstTextLine).tap()
        XCTAssertTrue(app.buttons["annotationMenu.delete"].waitForExistence(timeout: 5), "tapping a highlight opens its menu")
        app.buttons["annotationMenu.color.#86EFAC"].tap()
        tap("annotationMenu.delete")
        waitForValue("highlights", 0, "delete removes the highlight")

        // Long press selects a word natively and shows the selection actions
        point(300, 200).press(forDuration: 1.3)
        waitForState("selection", "true", "long press selects text in reading mode")
        XCTAssertTrue(app.buttons["selection.underline"].waitForExistence(timeout: 3))
        tap("selection.underline")
        waitForValue("underlines", 1, "underline from a long-press selection")
    }

    // MARK: - Multi-touch and stylus-only

    func testPinchDoesNotDrawAndStylusOnlyIgnoresFinger() {
        selectTool("Fine Pen", expect: "pen")
        app.otherElements["reader.pdf"].pinch(withScale: 1.4, velocity: 1)
        sleep(1)
        XCTAssertEqual(value("drawings"), 0, "two-finger pinch zooms instead of drawing")

        tap("rail.more")
        app.buttons["Stylus Only: Off"].tap()
        drag(from: (120, 500), to: (420, 520))
        sleep(1)
        XCTAssertEqual(value("drawings"), 0, "with Apple Pencil only, a finger does not draw")
    }

    // MARK: - Navigation, search, bookmarks, export

    func testSearchJumpsToMatchAndBookmarkToggles() {
        tap("reader.search")
        let field = app.textFields["search.field"]
        XCTAssertTrue(field.waitForExistence(timeout: 3))
        field.tap()
        field.typeText("Electrical\n")
        XCTAssertTrue(app.staticTexts["search.count"].waitForExistence(timeout: 5), "search finds matches")
        waitForState("page", "1", "search jumps to the page with the match")

        tap("reader.bookmark")
        XCTAssertTrue(app.buttons["Remove bookmark"].waitForExistence(timeout: 3), "page is bookmarked")
        tap("reader.bookmark")
        XCTAssertTrue(app.buttons["Bookmark page"].waitForExistence(timeout: 3), "bookmark is removed")
    }

    func testExportOpensShareSheet() {
        selectTool("Fine Pen", expect: "pen")
        drag(from: (120, 520), to: (420, 560))
        waitForValue("drawings", 1)
        tap("rail.done")

        tap("reader.more")
        app.buttons["Export Annotated Copy"].tap()
        let shared = app.otherElements["ActivityListView"].waitForExistence(timeout: 8)
            || app.buttons["Save to Files"].waitForExistence(timeout: 2)
            || app.cells["Save to Files"].waitForExistence(timeout: 2)
        XCTAssertTrue(shared, "export opens the share sheet with the annotated PDF")
    }

    // MARK: - Persistence

    func testAnnotationsPersistAcrossRelaunch() {
        selectTool("Fine Pen", expect: "pen")
        drag(from: (120, 520), to: (420, 560))
        waitForValue("drawings", 1)
        selectTool("Highlight", expect: "textHighlight")
        drag(from: (58, firstTextLine), to: (320, firstTextLine))
        waitForValue("highlights", 1)

        app.terminate()
        app.launchArguments = ["-UITests"] // no reset
        app.launch()
        openSampleBook()
        waitForValue("drawings", 1, "stroke is still there after relaunch")
        waitForValue("highlights", 1, "highlight is still there after relaunch")
    }

    // MARK: - Helpers

    private func openSampleBook() {
        let card = app.buttons["home.continueReading"]
        XCTAssertTrue(card.waitForExistence(timeout: 15), "home shows the Continue Reading card")
        card.tap()
        XCTAssertTrue(probe.waitForExistence(timeout: 15), "reader opens")
        waitForState("page", "0")
        let ready = wait(10) { (self.state()["frame"] ?? "0,0,0,0") != "0,0,0,0" }
        XCTAssertTrue(ready, "PDF page is laid out")
        sleep(1) // let paging settle
    }

    private var probe: XCUIElement { app.descendants(matching: .any)["reader.state"] }

    private func state() -> [String: String] {
        let raw = (probe.value as? String) ?? probe.label
        var result: [String: String] = [:]
        for pair in raw.split(separator: ";") {
            let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
            if parts.count == 2 { result[parts[0]] = parts[1] }
        }
        return result
    }

    private func value(_ key: String) -> Int { Int(state()[key] ?? "") ?? -1 }

    @discardableResult
    private func wait(_ timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let end = Date().addingTimeInterval(timeout)
        while Date() < end {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        return condition()
    }

    private func waitForValue(_ key: String, _ expected: Int, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        let ok = wait(6) { self.value(key) == expected }
        XCTAssertTrue(ok, "\(message): expected \(key)=\(expected), state: \(state())", file: file, line: line)
    }

    private func waitForState(_ key: String, _ expected: String, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        let ok = wait(6) { self.state()[key] == expected }
        XCTAssertTrue(ok, "\(message): expected \(key)=\(expected), state: \(state())", file: file, line: line)
    }

    /// Screen coordinate of a point on the current page, in PDF page points from the top-left.
    private func point(_ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        let f = (state()["frame"] ?? "0,0,0,0").split(separator: ",").compactMap { Double($0) }
        XCTAssertEqual(f.count, 4)
        let sx = CGFloat(f[0]) + x * CGFloat(f[2]) / 612
        let sy = CGFloat(f[1]) + y * CGFloat(f[3]) / 792
        return app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: sx, dy: sy))
    }

    private func drag(from a: (CGFloat, CGFloat), to b: (CGFloat, CGFloat)) {
        point(a.0, a.1).press(forDuration: 0.05, thenDragTo: point(b.0, b.1))
    }

    private func tap(_ identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 5), "\(identifier) exists", file: file, line: line)
        element.tap()
    }

    private func selectFirstLine() {
        drag(from: (58, firstTextLine), to: (320, firstTextLine))
        waitForState("selection", "true", "text is selected")
        XCTAssertTrue(app.descendants(matching: .any)["selection.bar"].waitForExistence(timeout: 3))
    }

    /// Picks a tool on the radial dial, turning it as needed (only nearby tools are on screen).
    private func selectTool(_ name: String, expect raw: String, file: StaticString = #filePath, line: UInt = #line) {
        guard let targetIndex = dialOrder.firstIndex(of: name) else { return XCTFail("unknown tool \(name)") }
        openDial()
        for _ in 0..<8 {
            let target = app.buttons["dial.\(name)"]
            if target.waitForExistence(timeout: 1.5) && target.isHittable {
                target.tap()
                break
            }
            // Tap the visible tool closest to the target; the dial turns there and closes, then reopen it
            let visible = dialOrder.enumerated().filter { app.buttons["dial.\($0.element)"].exists }
            guard let nearest = visible.min(by: { abs($0.offset - targetIndex) < abs($1.offset - targetIndex) }) else {
                return XCTFail("dial shows no tools", file: file, line: line)
            }
            app.buttons["dial.\(nearest.element)"].tap()
            openDial()
        }
        waitForState("tool", raw, "selected \(name)", file: file, line: line)
    }

    private func openDial() {
        if app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'dial.'")).firstMatch.exists { return }
        if app.buttons["rail.tool"].exists {
            app.buttons["rail.tool"].tap()
        } else {
            tap("reader.penButton")
        }
        _ = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'dial.'")).firstMatch.waitForExistence(timeout: 3)
        sleep(1) // dial spring animation
    }
}
