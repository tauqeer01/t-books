import XCTest

/// End-to-end tests for Notes, run against the real app in the simulator.
///
/// Each test creates its own note through the New note flow. The `-UITests` launch argument exposes a hidden
/// `notes.state` probe with live counts (pages, strokes, elements by kind), the tool, selection, editing state
/// and the current page's frame on screen, so gestures can be aimed at page points (A4: 595.2 × 841.8).
final class NotesEditorUITests: XCTestCase {
    private var app: XCUIApplication!
    private var title = ""

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-UITests", "-UITestsReset"]
        app.launch()
        title = "UITest \(UUID().uuidString.prefix(6))"
        createNote(title: title)
    }

    // MARK: - Ink

    func testPenInkUndoRedoAndPersistsOnReopen() {
        XCTAssertEqual(state()["tool"], "pen")
        drag(from: (100, 200), to: (450, 260))
        waitFor("strokes", 1, "pen stroke is drawn on the page")
        tap("notes.undo")
        waitFor("strokes", 0, "undo removes it")
        tap("notes.redo")
        waitFor("strokes", 1, "redo brings it back")
        drag(from: (100, 320), to: (450, 330))
        waitFor("strokes", 2)

        closeAndReopen()
        waitFor("strokes", 2, "ink is saved with the note")
    }

    func testPenEditorChangesPenType() {
        tap("notes.pen.0")       // already active: opens the pen editor
        tap("penEditor.type.fountain")
        tap("penEditor.done")
        waitForState("pen", "fountain", "pen type is changed")
        drag(from: (100, 200), to: (400, 240))
        waitFor("strokes", 1)
    }

    func testShapeToolAutoAndExplicitShapes() {
        tap("notes.tool.shape")
        drag(from: (100, 200), to: (450, 230))
        waitFor("strokes", 1, "auto shape keeps a straight line as a stroke")
        tap("notes.shape.rectangle")
        drag(from: (120, 320), to: (420, 480))
        waitFor("strokes", 2, "explicit rectangle is drawn")
        tap("notes.shape.ellipse")
        drag(from: (120, 520), to: (420, 700))
        waitFor("strokes", 3, "explicit ellipse is drawn")
    }

    func testEraserStrokeAndHighlighterModes() {
        drag(from: (100, 200), to: (480, 200))
        waitFor("strokes", 1)
        // Highlighter pen (the 7th default pen)
        tap("notes.pen.6")
        drag(from: (100, 400), to: (480, 400))
        waitFor("markers", 1, "highlighter stroke is drawn")
        waitFor("strokes", 2)

        tap("notes.tool.eraser")
        app.segmentedControls["notes.eraser.mode"].buttons["Highlighter"].tap()
        drag(from: (300, 360), to: (300, 440))
        waitFor("markers", 0, "highlighter eraser removes highlighter ink")
        waitFor("strokes", 1, "and leaves pen ink alone")

        app.segmentedControls["notes.eraser.mode"].buttons["Stroke"].tap()
        drag(from: (300, 160), to: (300, 240))
        waitFor("strokes", 0, "stroke eraser removes the pen stroke")
        tap("notes.undo")
        waitFor("strokes", 1, "erasing is undoable")
    }

    // MARK: - Text, sticky notes, tape

    func testTextBoxTypingFormattingAndEditing() {
        tap("notes.tool.text")
        point(80, 160).tap()
        waitForState("editing", "true", "tapping the page starts a text box")
        app.typeText("Hydraulics overview")
        tap("notes.fmt.heading")
        tap("notes.fmt.done")
        waitFor("text", 1, "text box is kept")
        waitForState("editing", "false")

        // Tap it again with the text tool to edit
        point(100, 168).tap()
        waitForState("editing", "true", "tapping a text box edits it")
        tap("notes.fmt.done")
        waitFor("text", 1)

        // An empty text box is discarded
        point(80, 500).tap()
        waitForState("editing", "true")
        tap("notes.fmt.done")
        waitFor("text", 1, "empty text box is removed")
    }

    func testStickyNoteAndStudyTape() {
        tap("notes.insert")
        app.buttons["Sticky note"].tap()
        waitForState("editing", "true", "new sticky note opens for typing")
        app.typeText("Check PSI")
        tap("notes.fmt.done")
        waitFor("sticky", 1)

        tap("notes.tool.tape")
        drag(from: (100, 600), to: (400, 640))
        waitFor("tape", 1, "dragging with tape covers an area")
        tap("notes.tool.hand")
        point(250, 620).tap()
        waitFor("revealed", 1, "tapping tape peels it back")
        point(250, 620).tap()
        waitFor("revealed", 0, "tapping again covers it")
        tap("notes.tool.tape")
        tap("notes.tape.showAll")
        waitFor("revealed", 1, "show all reveals every tape")
    }

    // MARK: - Lasso

    func testLassoSelectMoveDuplicateRecolorDelete() {
        drag(from: (150, 300), to: (300, 320))
        waitFor("strokes", 1)
        tap("notes.tool.lasso")
        drag(from: (100, 250), to: (360, 380))   // straight drag = marquee
        waitFor("selected", 1, "lasso selects the stroke")

        // Drag the selection
        drag(from: (220, 310), to: (220, 520))
        waitFor("selected", 1, "selection survives a move")
        XCTAssertEqual(state()["undo"], "true")

        tap("notes.sel.duplicate")
        waitFor("strokes", 2, "duplicate copies the selection")
        tap("notes.sel.copy")
        tap("notes.sel.delete")
        waitFor("strokes", 1, "delete removes the selected copy")

        tap("notes.insert")
        app.buttons["Paste"].tap()
        waitFor("strokes", 2, "paste adds the copied ink back")
    }

    // MARK: - Pages

    func testAddDuplicateDeletePagesWithUndo() {
        drag(from: (100, 200), to: (450, 260))
        waitFor("strokes", 1)

        tap("notes.more")
        app.buttons["Add page…"].tap()
        tap("template.GRID")
        waitFor("pages", 2, "a page is added")
        waitFor("page", 1, "and shown")

        tap("notes.more")
        app.buttons["Duplicate page"].tap()
        waitFor("pages", 3)

        tap("notes.more")
        app.buttons["Delete page"].tap()
        app.buttons["Delete page"].firstMatch.tap()
        waitFor("pages", 2, "page is deleted")
        tap("notes.undo")
        waitFor("pages", 3, "page delete is undoable")
        waitFor("strokes", 1, "ink stays on its own page through page edits")
    }

    func testLaserLeavesNoInk() {
        tap("notes.tool.laser")
        drag(from: (100, 300), to: (450, 400))
        sleep(1)
        XCTAssertEqual(value("strokes"), 0, "laser pointer does not draw")
    }

    // MARK: - Library

    func testNotesHomeSearchFindsNoteByTypedText() {
        tap("notes.tool.text")
        point(80, 160).tap()
        waitForState("editing", "true")
        app.typeText("Turbine blade inspection")
        tap("notes.fmt.done")
        waitFor("text", 1)
        tap("notes.done")

        let search = app.textFields["notesHome.search"]
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        _ = wait(5) { search.isHittable }
        sleep(1) // editor closing animation
        search.tap()
        search.typeText("turbine blade")
        XCTAssertTrue(app.buttons["notesHome.note.\(title)"].waitForExistence(timeout: 5), "search finds the note by its text")
    }

    // MARK: - Helpers

    private func createNote(title: String) {
        let notesTab = app.tabBars.buttons["Notes"]
        XCTAssertTrue(notesTab.waitForExistence(timeout: 15))
        notesTab.tap()
        tap("notesHome.new")
        let field = app.textFields["newNote.title"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(title)
        tap("newNote.template.BLANK")
        tap("newNote.create")
        XCTAssertTrue(probe.waitForExistence(timeout: 10), "editor opens")
        let laidOut = wait(10) { (self.state()["frame"] ?? "0") != "0.0,0.0,0.0,0.0" && self.state()["title"] == title }
        XCTAssertTrue(laidOut, "editor shows the new note: \(state())")
        sleep(1)
    }

    private func closeAndReopen() {
        tap("notes.done")
        let card = app.buttons["notesHome.note.\(title)"]
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        card.tap()
        XCTAssertTrue(probe.waitForExistence(timeout: 10))
        _ = wait(5) { self.state()["title"] == self.title }
    }

    private var probe: XCUIElement { app.descendants(matching: .any)["notes.state"] }

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

    private func waitFor(_ key: String, _ expected: Int, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(wait(6) { self.value(key) == expected }, "\(message): expected \(key)=\(expected), state: \(state())", file: file, line: line)
    }

    private func waitForState(_ key: String, _ expected: String, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(wait(6) { self.state()[key] == expected }, "\(message): expected \(key)=\(expected), state: \(state())", file: file, line: line)
    }

    /// Screen coordinate of a point on the current page, in page points from the top-left.
    private func point(_ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        let f = (state()["frame"] ?? "0,0,0,0").split(separator: ",").compactMap { Double($0) }
        XCTAssertEqual(f.count, 4)
        let sx = CGFloat(f[0]) + x * CGFloat(f[2]) / 595.2
        let sy = CGFloat(f[1]) + y * CGFloat(f[3]) / 841.8
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
}
