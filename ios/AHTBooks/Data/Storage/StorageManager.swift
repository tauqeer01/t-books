import Foundation
import SwiftUI
import PDFKit
import PencilKit
import Combine

public final class StorageManager: ObservableObject {
    public static let shared = StorageManager()
    
    // Published data collections
    @Published public var books: [Book] = []
    @Published public var collections: [BookCollection] = []
    @Published public var annotations: [BookAnnotation] = []
    @Published public var bookmarks: [Bookmark] = []
    @Published public var notes: [Note] = []
    @Published public var noteFolders: [NoteFolder] = []
    @Published public var decks: [Deck] = []
    @Published public var cards: [Card] = []
    @Published public var schedules: [CardSchedule] = []
    @Published public var reviewLogs: [ReviewLog] = []
    @Published public var savedSignatures: [SavedSignature] = []
    @Published public var preferences: UserReadingPreferences = UserReadingPreferences()
    @Published public var readingStats: ReadingStats = ReadingStats()
    
    private let fileManager = FileManager.default
    private let appSupportURL: URL
    private let documentsURL: URL
    public var documentsDirectory: URL { documentsURL }
    /// Signatures live outside Documents so they never appear in the Files app
    /// or Finder file sharing (UIFileSharingEnabled exposes Documents).
    private var signaturesURL: URL { appSupportURL.appendingPathComponent("Signatures", isDirectory: true) }
    private var legacySignaturesURL: URL { documentsURL.appendingPathComponent("Signatures", isDirectory: true) }

    public init() {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let support = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.documentsURL = docs
        self.appSupportURL = support
        
        createSubdirectoriesIfNeeded()
        migrateLegacySignatures()
        loadAll()

        // Starter content is added once, on first launch only, so anything the user deletes stays deleted.
        if !UserDefaults.standard.bool(forKey: Self.didSeedStarterContentKey) {
            if books.isEmpty { createSampleBook() }
            if collections.isEmpty { createDefaultCollections() }
            if decks.isEmpty { createSampleDeck() }
            if notes.isEmpty { createSampleNoteIfNeeded() }
            UserDefaults.standard.set(true, forKey: Self.didSeedStarterContentKey)
        }
        restoreSampleBookFileIfNeeded()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-UITestsReset") {
            resetSampleBookForUITests()
        }
        #endif
    }

    #if DEBUG
    /// UI tests start from a clean sample book: no annotations or bookmarks, page 1, horizontal paging.
    private func resetSampleBookForUITests() {
        let sampleId = Self.sampleBookId
        if !books.contains(where: { $0.id == sampleId }) { createSampleBook() }
        annotations.removeAll { $0.bookId == sampleId }
        bookmarks.removeAll { $0.bookId == sampleId }
        if let i = books.firstIndex(where: { $0.id == sampleId }) {
            books[i].currentPage = 0
        }
        preferences.scrollMode = .HORIZONTAL_PAGING
        saveAll()
        // Notes: default pens, finger drawing on
        for key in ["notes.pens.v1", "notes.pens.active", "notes.fingerDrawing"] {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }
    #endif

    private func createSubdirectoriesIfNeeded() {
        let paths = [
            appSupportURL,
            documentsURL.appendingPathComponent("PDFs", isDirectory: true),
            documentsURL.appendingPathComponent("Thumbnails", isDirectory: true),
            documentsURL.appendingPathComponent("Drawings", isDirectory: true),
            documentsURL.appendingPathComponent("Photos", isDirectory: true)
        ]
        for url in paths {
            try? fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        }
        try? fileManager.createDirectory(
            at: signaturesURL,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete]
        )
    }

    /// Moves signature images saved by earlier builds out of the user-visible
    /// Documents folder into protected Application Support storage.
    private func migrateLegacySignatures() {
        guard let files = try? fileManager.contentsOfDirectory(at: legacySignaturesURL, includingPropertiesForKeys: nil) else { return }
        for src in files {
            let dest = signaturesURL.appendingPathComponent(src.lastPathComponent)
            if fileManager.fileExists(atPath: dest.path) {
                try? fileManager.removeItem(at: src)
            } else if (try? fileManager.moveItem(at: src, to: dest)) != nil {
                try? fileManager.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: dest.path)
            }
        }
        if (try? fileManager.contentsOfDirectory(atPath: legacySignaturesURL.path))?.isEmpty == true {
            try? fileManager.removeItem(at: legacySignaturesURL)
        }
    }
    
    // MARK: - Persistence IO
    
    private func saveJSON<T: Encodable>(_ object: T, filename: String) {
        let url = appSupportURL.appendingPathComponent(filename)
        do {
            let data = try JSONEncoder().encode(object)
            try data.write(to: url, options: .atomic)
        } catch {
            print("Failed to save \(filename): \(error)")
        }
    }
    
    private func loadJSON<T: Decodable>(filename: String, fallback: T) -> T {
        let url = appSupportURL.appendingPathComponent(filename)
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode(T.self, from: data) else {
            return fallback
        }
        return decoded
    }
    
    public func saveAll() {
        saveJSON(books, filename: "books.json")
        saveJSON(collections, filename: "collections.json")
        saveJSON(annotations, filename: "annotations.json")
        saveJSON(bookmarks, filename: "bookmarks.json")
        saveJSON(notes, filename: "notes.json")
        saveJSON(noteFolders, filename: "note_folders.json")
        saveJSON(decks, filename: "decks.json")
        saveJSON(cards, filename: "cards.json")
        saveJSON(schedules, filename: "schedules.json")
        saveJSON(reviewLogs, filename: "review_logs.json")
        saveJSON(savedSignatures, filename: "signatures.json")
        saveJSON(preferences, filename: "preferences.json")
        saveJSON(readingStats, filename: "reading_stats.json")
    }
    
    /// Writes only the annotations file; the reader calls this after every stroke.
    public func saveAnnotations() {
        saveJSON(annotations, filename: "annotations.json")
    }

    /// Writes only the notes and folders; the note editor saves this way while you write.
    public func saveNotes() {
        saveJSON(notes, filename: "notes.json")
        saveJSON(noteFolders, filename: "note_folders.json")
    }

    public func loadAll() {
        self.books = loadJSON(filename: "books.json", fallback: [])
        self.collections = loadJSON(filename: "collections.json", fallback: [])
        self.annotations = loadJSON(filename: "annotations.json", fallback: [])
        self.bookmarks = loadJSON(filename: "bookmarks.json", fallback: [])
        self.notes = loadJSON(filename: "notes.json", fallback: [])
        self.noteFolders = loadJSON(filename: "note_folders.json", fallback: [])
        self.decks = loadJSON(filename: "decks.json", fallback: [])
        self.cards = loadJSON(filename: "cards.json", fallback: [])
        self.schedules = loadJSON(filename: "schedules.json", fallback: [])
        self.reviewLogs = loadJSON(filename: "review_logs.json", fallback: [])
        self.savedSignatures = loadJSON(filename: "signatures.json", fallback: [])
        self.preferences = loadJSON(filename: "preferences.json", fallback: UserReadingPreferences())
        self.readingStats = loadJSON(filename: "reading_stats.json", fallback: ReadingStats())

        // Page curl was never a separate mode on iOS; it behaved as horizontal paging.
        if preferences.scrollMode == .BOOK_CURL { preferences.scrollMode = .HORIZONTAL_PAGING }

        // iOS can move the app's data container between launches (app updates, device restores),
        // so absolute paths saved earlier are re-anchored to the current Documents folder.
        var pathsChanged = false
        for i in books.indices {
            let file = relocated(books[i].filePath, in: "PDFs")
            if file != books[i].filePath { books[i].filePath = file; pathsChanged = true }
            if let thumb = books[i].thumbnailPath {
                let moved = relocated(thumb, in: "Thumbnails")
                if moved != thumb { books[i].thumbnailPath = moved; pathsChanged = true }
            }
        }
        if pathsChanged { saveJSON(books, filename: "books.json") }
    }

    /// Returns the file's path in the current container when the saved absolute path no longer exists.
    private func relocated(_ path: String, in folder: String) -> String {
        guard !path.isEmpty, !fileManager.fileExists(atPath: path) else { return path }
        let candidate = documentsURL.appendingPathComponent(folder)
            .appendingPathComponent((path as NSString).lastPathComponent).path
        return fileManager.fileExists(atPath: candidate) ? candidate : path
    }

    public func savePreferences() {
        saveJSON(preferences, filename: "preferences.json")
    }

    // MARK: - Collections

    public func bookCount(in collection: BookCollection) -> Int {
        books.reduce(0) { $0 + ($1.collectionIds.contains(collection.id) ? 1 : 0) }
    }

    public func setCollections(bookId: String, collectionIds: [String]) {
        guard let index = books.firstIndex(where: { $0.id == bookId }) else { return }
        books[index].collectionIds = collectionIds
        saveJSON(books, filename: "books.json")
    }

    public func addCollection(name: String, description: String) {
        let pastels = ["#EDE9FE", "#DCFCE7", "#FFE4E6", "#FEF3C7", "#D1FAE5", "#FFEDD5", "#DBEAFE"]
        let color = pastels[collections.count % pastels.count]
        collections.append(BookCollection(name: name, description: description, pastelColorHex: color, iconName: "folder.fill"))
        saveJSON(collections, filename: "collections.json")
    }

    /// Deletes the collection only; its books stay in the library.
    public func deleteCollection(id: String) {
        collections.removeAll { $0.id == id }
        for i in books.indices where books[i].collectionIds.contains(id) {
            books[i].collectionIds.removeAll { $0 == id }
        }
        saveJSON(collections, filename: "collections.json")
        saveJSON(books, filename: "books.json")
    }

    // MARK: - Decks

    public func addDeck(name: String) {
        decks.append(Deck(name: name))
        saveJSON(decks, filename: "decks.json")
    }

    /// Deletes a deck with its cards, their review schedules and review history.
    public func deleteDeck(id: String) {
        let cardIds = Set(cards.filter { $0.deckId == id }.map(\.id))
        decks.removeAll { $0.id == id }
        cards.removeAll { $0.deckId == id }
        schedules.removeAll { cardIds.contains($0.cardId) }
        reviewLogs.removeAll { $0.deckId == id || cardIds.contains($0.cardId) }
        saveJSON(decks, filename: "decks.json")
        saveJSON(cards, filename: "cards.json")
        saveJSON(schedules, filename: "schedules.json")
        saveJSON(reviewLogs, filename: "review_logs.json")
    }
    
    // MARK: - Book & Library Operations
    
    @discardableResult
    public func importPDF(from sourceURL: URL) -> Book? {
        let shouldStopAccessing = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if shouldStopAccessing { sourceURL.stopAccessingSecurityScopedResource() }
        }
        
        let fileId = UUID().uuidString
        let destFileName = "\(fileId).pdf"
        let destURL = documentsURL.appendingPathComponent("PDFs").appendingPathComponent(destFileName)
        
        do {
            if fileManager.fileExists(atPath: destURL.path) {
                try fileManager.removeItem(at: destURL)
            }
            try fileManager.copyItem(at: sourceURL, to: destURL)
        } catch {
            print("Failed to copy PDF: \(error)")
            return nil
        }
        
        guard let pdfDoc = PDFDocument(url: destURL) else { return nil }
        let pageCount = pdfDoc.pageCount
        let attr = (try? fileManager.attributesOfItem(atPath: destURL.path)) ?? [:]
        let fileSize = (attr[.size] as? NSNumber)?.int64Value ?? 0
        
        func attribute(_ key: PDFDocumentAttribute) -> String? {
            let value = (pdfDoc.documentAttributes?[key] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            return value?.isEmpty == false ? value : nil
        }
        let title = attribute(.titleAttribute) ?? sourceURL.deletingPathExtension().lastPathComponent
        let author = attribute(.authorAttribute) ?? "Unknown Author"
        
        let thumbPath = generateThumbnail(for: pdfDoc, id: fileId)
        
        let newBook = Book(
            id: fileId,
            title: title,
            author: author,
            filePath: destURL.path,
            fileSizeBytes: fileSize,
            pageCount: pageCount,
            currentPage: 0,
            readingProgress: 0.0,
            isFavorite: false,
            category: "Imported",
            collectionIds: [],
            thumbnailPath: thumbPath,
            coverColorHex: Theme.brandPurple.toHex(),
            lastReadTimestamp: Int64(Date().timeIntervalSince1970 * 1000),
            addedTimestamp: Int64(Date().timeIntervalSince1970 * 1000)
        )
        
        DispatchQueue.main.async {
            self.books.insert(newBook, at: 0)
            self.saveJSON(self.books, filename: "books.json")
        }
        return newBook
    }
    
    private func generateThumbnail(for pdfDoc: PDFDocument, id: String) -> String? {
        guard let page = pdfDoc.page(at: 0) else { return nil }
        let pageBounds = page.bounds(for: .mediaBox)
        let targetSize = CGSize(width: 300, height: 300 * (pageBounds.height / max(1, pageBounds.width)))
        let image = page.thumbnail(of: targetSize, for: .mediaBox)
        
        let thumbURL = documentsURL.appendingPathComponent("Thumbnails").appendingPathComponent("\(id).png")
        if let pngData = image.pngData() {
            try? pngData.write(to: thumbURL)
            return thumbURL.path
        }
        return nil
    }
    
    public func updateBookProgress(bookId: String, page: Int, totalPages: Int) {
        guard let index = books.firstIndex(where: { $0.id == bookId }) else { return }
        books[index].currentPage = page
        books[index].readingProgress = Float(page + 1) / Float(max(1, totalPages))
        books[index].lastReadTimestamp = Int64(Date().timeIntervalSince1970 * 1000)
        saveJSON(books, filename: "books.json")
    }
    
    public func toggleFavorite(bookId: String) {
        guard let index = books.firstIndex(where: { $0.id == bookId }) else { return }
        books[index].isFavorite.toggle()
        saveJSON(books, filename: "books.json")
    }
    
    public func deleteBook(bookId: String) {
        if let book = books.first(where: { $0.id == bookId }) {
            try? fileManager.removeItem(atPath: book.filePath)
            if let thumb = book.thumbnailPath {
                try? fileManager.removeItem(atPath: thumb)
            }
        }
        books.removeAll { $0.id == bookId }
        annotations.removeAll { $0.bookId == bookId }
        bookmarks.removeAll { $0.bookId == bookId }
        saveJSON(books, filename: "books.json")
        saveJSON(annotations, filename: "annotations.json")
        saveJSON(bookmarks, filename: "bookmarks.json")
    }
    
    public func addReadingTime(seconds: Int64) {
        guard seconds > 0 else { return }
        readingStats.addReadingTime(seconds)
        saveJSON(readingStats, filename: "reading_stats.json")
    }

    public func setDailyGoal(minutes: Int) {
        let range = ReadingStats.goalRangeMinutes
        readingStats.dailyGoalMinutes = min(max(minutes, range.lowerBound), range.upperBound)
        saveJSON(readingStats, filename: "reading_stats.json")
    }
    
    // MARK: - PencilKit Drawings Persistence
    
    public func saveDrawing(_ drawing: PKDrawing, fileName: String) {
        let url = documentsURL.appendingPathComponent("Drawings").appendingPathComponent(fileName)
        let data = drawing.dataRepresentation()
        try? data.write(to: url, options: .atomic)
    }
    
    public func loadDrawing(fileName: String) -> PKDrawing {
        let url = documentsURL.appendingPathComponent("Drawings").appendingPathComponent(fileName)
        guard let data = try? Data(contentsOf: url),
              let drawing = try? PKDrawing(data: data) else {
            return PKDrawing()
        }
        return drawing
    }
    
    // MARK: - Signatures Persistence
    
    public func saveSignatureImage(_ image: UIImage, kind: SignatureKind) -> SavedSignature {
        let id = UUID().uuidString
        let fileName = "\(id).png"
        let url = signaturesURL.appendingPathComponent(fileName)
        if let data = image.pngData() {
            try? data.write(to: url, options: [.atomic, .completeFileProtection])
        }
        let sig = SavedSignature(id: id, kind: kind, imageFileName: fileName)
        savedSignatures.append(sig)
        saveJSON(savedSignatures, filename: "signatures.json")
        return sig
    }

    public func getSignatureImage(fileName: String) -> UIImage? {
        let url = signaturesURL.appendingPathComponent(fileName)
        return UIImage(contentsOfFile: url.path)
    }

    /// Removes the signature from the list and deletes its image file from disk.
    public func deleteSignature(_ signature: SavedSignature) {
        savedSignatures.removeAll { $0.id == signature.id }
        // Only delete a plain file name inside the signatures folder.
        let fileName = (signature.imageFileName as NSString).lastPathComponent
        if !fileName.isEmpty {
            try? fileManager.removeItem(at: signaturesURL.appendingPathComponent(fileName))
        }
        saveJSON(savedSignatures, filename: "signatures.json")
    }
    
    // MARK: - Sample Data Generation
    
    private func createDefaultCollections() {
        self.collections = [
            BookCollection(name: "Favorites", description: "Your starred and beloved documents", pastelColorHex: "#FFE4E6", iconName: "star.fill"),
            BookCollection(name: "Study Material", description: "Textbooks, research and class notes", pastelColorHex: "#EDE9FE", iconName: "book.fill"),
            BookCollection(name: "Aviation & Systems", description: "Flight handbooks and technical manuals", pastelColorHex: "#DCFCE7", iconName: "airplane"),
            BookCollection(name: "Quick Reference", description: "Cheatsheets and reference guides", pastelColorHex: "#FEF3C7", iconName: "bookmark.fill")
        ]
        saveJSON(collections, filename: "collections.json")
    }
    
    private func createSampleDeck() {
        let deck = Deck(name: "General Knowledge & Study", colorHex: "#5935FF")
        decks.append(deck)
        
        let card1 = Card(
            deckId: deck.id,
            type: .BASIC,
            front: CardFace(text: "What are the primary colors in standard additive RGB color models?"),
            back: CardFace(text: "Red, Green, and Blue.")
        )
        let card2 = Card(
            deckId: deck.id,
            type: .CLOZE,
            front: CardFace(text: "The aircraft hydraulic system operates at {{c1::3000::pressure in PSI}} PSI."),
            back: CardFace(text: "")
        )
        cards.append(contentsOf: [card1, card2])
        
        let sched1 = CardSchedule(cardId: card1.id)
        let sched2 = CardSchedule(cardId: card2.id)
        schedules.append(contentsOf: [sched1, sched2])
        
        saveJSON(decks, filename: "decks.json")
        saveJSON(cards, filename: "cards.json")
        saveJSON(schedules, filename: "schedules.json")
    }
    
    private func createSampleNoteIfNeeded() {
        let noteId = "sample_study_note"
        let p1 = NotePage(
            noteId: noteId,
            pageIndex: 0,
            background: .CORNELL,
            paperTheme: .ivory,
            elements: [
                NoteElement(
                    kind: .STICKY,
                    x: 60,
                    y: 110,
                    width: 200,
                    height: 100,
                    content: "Review key concepts for Chapter 3 exam: Cellular respiration and ATP synthesis.",
                    fillColor: "#FEF08A"
                ),
                NoteElement(
                    kind: .QUOTE,
                    x: 60,
                    y: 240,
                    width: 250,
                    height: 80,
                    content: "“Mitochondria generate most of the cell's chemical energy stored in adenosine triphosphate.”",
                    fillColor: nil
                ),
                NoteElement(
                    kind: .TAPE,
                    x: 60,
                    y: 350,
                    width: 220,
                    height: 36,
                    content: "Study Tape",
                    fillColor: "#F59E0B"
                )
            ]
        )
        let p2 = NotePage(
            noteId: noteId,
            pageIndex: 1,
            background: .RULED,
            paperTheme: .white,
            elements: [
                NoteElement(
                    kind: .STICKY,
                    x: 70,
                    y: 120,
                    width: 190,
                    height: 90,
                    content: "Key formulas:\nΔG = ΔH - TΔS\nATP -> ADP + Pi",
                    fillColor: "#BBF7D0"
                )
            ]
        )
        let p3 = NotePage(
            noteId: noteId,
            pageIndex: 2,
            background: .MATH_GRAPH,
            paperTheme: .blueprint,
            elements: []
        )
        let sampleNote = Note(
            id: noteId,
            title: "Biology & Cellular Notes",
            pinned: true,
            tags: ["Biology", "Exam", "Study"],
            pages: [p1, p2, p3]
        )
        notes.append(sampleNote)
        saveAll()
    }
    
    private static let didSeedStarterContentKey = "starterContent.seeded.v1"
    private static let sampleBookId = "sample_aircraft"
    private var sampleBookURL: URL {
        documentsURL.appendingPathComponent("PDFs").appendingPathComponent("aircraft_systems_sample.pdf")
    }

    /// Adds the bundled sample document to an empty library (first launch only).
    private func createSampleBook() {
        guard let data = renderSamplePDF(to: sampleBookURL),
              let pdfDoc = PDFDocument(url: sampleBookURL) else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        books.insert(Book(
            id: Self.sampleBookId,
            title: "Aircraft Systems & Operations",
            author: "AHT Books sample document",
            filePath: sampleBookURL.path,
            fileSizeBytes: Int64(data.count),
            pageCount: pdfDoc.pageCount,
            category: "Sample",
            thumbnailPath: generateThumbnail(for: pdfDoc, id: Self.sampleBookId),
            coverColorHex: "#5935FF",
            lastReadTimestamp: now,
            addedTimestamp: now
        ), at: 0)
        saveJSON(books, filename: "books.json")
    }

    /// Re-creates the sample PDF if the user still has the sample book but its file is gone.
    private func restoreSampleBookFileIfNeeded() {
        guard let index = books.firstIndex(where: { $0.id == Self.sampleBookId }),
              !fileManager.fileExists(atPath: books[index].filePath) else { return }
        guard renderSamplePDF(to: sampleBookURL) != nil, let pdfDoc = PDFDocument(url: sampleBookURL) else { return }
        books[index].filePath = sampleBookURL.path
        if books[index].thumbnailPath.map({ fileManager.fileExists(atPath: $0) }) != true {
            books[index].thumbnailPath = generateThumbnail(for: pdfDoc, id: Self.sampleBookId)
        }
        saveJSON(books, filename: "books.json")
    }

    @discardableResult
    private func renderSamplePDF(to destURL: URL) -> Data? {
        // Render 2-page sample PDF
        let format = UIGraphicsPDFRendererFormat()
        let pageRect = CGRect(x: 0, y: 0, width: 612, height: 792) // Standard Letter
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)
        
        let data = renderer.pdfData { context in
            // Page 1
            context.beginPage()
            let titleAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 26, weight: .bold),
                .foregroundColor: UIColor(red: 0x59/255, green: 0x35/255, blue: 1, alpha: 1)
            ]
            let headingAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 18, weight: .semibold),
                .foregroundColor: UIColor(red: 0x10/255, green: 0x13/255, blue: 0x26/255, alpha: 1)
            ]
            let bodyAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .regular),
                .foregroundColor: UIColor(red: 0x10/255, green: 0x13/255, blue: 0x26/255, alpha: 1)
            ]
            
            "AHT Books Reference Manual".draw(at: CGPoint(x: 54, y: 60), withAttributes: titleAttr)
            "Chapter 1: Aircraft Systems & Hydraulics Overview".draw(at: CGPoint(x: 54, y: 105), withAttributes: headingAttr)
            
            let textPage1 = """
            Welcome to AHT Books! This sample document lets you explore text selection, highlighting, freehand pen notes, and search right away.

            Aircraft hydraulic systems provide reliable power to flight control surfaces, landing gear, wheel brakes, and thrust reversers. A typical transport-category aircraft operates dual or triple independent hydraulic circuits at nominal pressures of 3,000 PSI (20.7 MPa).

            Each circuit incorporates an engine-driven pump (EDP), an electric motor-driven auxiliary pump (ACMP), and a ram air turbine (RAT) for emergency hydraulic pressure in the event of all-engine power loss. Fluid reservoirs are pressurized by bleed air to prevent pump cavitation at high altitudes.

            Try long-pressing on any text above to highlight it in yellow, green, or purple, or tap the spinning Tool Dial at the right edge of your screen to draw with low-latency stylus ink!
            """
            textPage1.draw(in: CGRect(x: 54, y: 140, width: 504, height: 580), withAttributes: bodyAttr)
            
            // Page 2
            context.beginPage()
            "Chapter 2: Electrical Power Distribution".draw(at: CGPoint(x: 54, y: 60), withAttributes: headingAttr)
            let textPage2 = """
            Modern transport aircraft utilize 115V AC at 400 Hz as the primary electrical supply, generated by integrated drive generators (IDGs) mounted directly to the engine accessory gearboxes.

            Constant-speed drives ensure constant alternator frequency regardless of engine RPM fluctuations. Secondary DC power is provided at 28V via transformer rectifier units (TRUs) to energize flight computers, avionics displays, and emergency lighting.

            Main aircraft storage batteries maintain standby instrument power and facilitate auxiliary power unit (APU) starting on the ground or in flight.
            
            Annotation Navigation:
            You can bookmark this page by tapping the bookmark icon in the top toolbar, or create flashcards directly from any highlighted term!
            """
            textPage2.draw(in: CGRect(x: 54, y: 95, width: 504, height: 600), withAttributes: bodyAttr)
        }
        
        do {
            try data.write(to: destURL, options: .atomic)
            return data
        } catch {
            return nil
        }
    }
}
