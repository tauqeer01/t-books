import Foundation
import SwiftUI

// MARK: - Book & Library Models

public struct Book: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var title: String
    public var author: String
    public var filePath: String
    public var fileSizeBytes: Int64
    public var pageCount: Int
    public var currentPage: Int
    public var readingProgress: Float
    public var isFavorite: BooleanLiteralType
    public var category: String
    public var collectionIds: [String]
    public var thumbnailPath: String?
    public var coverColorHex: String
    public var lastReadTimestamp: Int64
    public var addedTimestamp: Int64
    
    public init(
        id: String = UUID().uuidString,
        title: String,
        author: String = "Unknown Author",
        filePath: String,
        fileSizeBytes: Int64 = 0,
        pageCount: Int = 1,
        currentPage: Int = 0,
        readingProgress: Float = 0.0,
        isFavorite: Bool = false,
        category: String = "General",
        collectionIds: [String] = [],
        thumbnailPath: String? = nil,
        coverColorHex: String = "#5935FF",
        lastReadTimestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        addedTimestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.filePath = filePath
        self.fileSizeBytes = fileSizeBytes
        self.pageCount = pageCount
        self.currentPage = currentPage
        self.readingProgress = readingProgress
        self.isFavorite = isFavorite
        self.category = category
        self.collectionIds = collectionIds
        self.thumbnailPath = thumbnailPath
        self.coverColorHex = coverColorHex
        self.lastReadTimestamp = lastReadTimestamp
        self.addedTimestamp = addedTimestamp
    }
}

public struct BookCollection: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var description: String
    public var pastelColorHex: String
    public var iconName: String
    public var bookCount: Int
    public var createdAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        name: String,
        description: String = "",
        pastelColorHex: String = "#EDE9FE",
        iconName: String = "folder.fill",
        bookCount: Int = 0,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.pastelColorHex = pastelColorHex
        self.iconName = iconName
        self.bookCount = bookCount
        self.createdAt = createdAt
    }
}

public enum AnnotationType: String, Codable, CaseIterable {
    case HIGHLIGHT
    case UNDERLINE
    case STRIKETHROUGH
    case NOTE
    case DRAWING
}

public struct BookAnnotation: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let bookId: String
    public var pageIndex: Int
    public var type: AnnotationType
    public var colorHex: String
    public var selectedText: String
    public var noteContent: String
    public var rectLeft: Float
    public var rectTop: Float
    public var rectRight: Float
    public var rectBottom: Float
    public var strokePathData: String?
    public var createdAt: Int64
    public var updatedAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        bookId: String,
        pageIndex: Int,
        type: AnnotationType = .HIGHLIGHT,
        colorHex: String = "#FFE600",
        selectedText: String = "",
        noteContent: String = "",
        rectLeft: Float = 0,
        rectTop: Float = 0,
        rectRight: Float = 0,
        rectBottom: Float = 0,
        strokePathData: String? = nil,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.bookId = bookId
        self.pageIndex = pageIndex
        self.type = type
        self.colorHex = colorHex
        self.selectedText = selectedText
        self.noteContent = noteContent
        self.rectLeft = rectLeft
        self.rectTop = rectTop
        self.rectRight = rectRight
        self.rectBottom = rectBottom
        self.strokePathData = strokePathData
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public struct Bookmark: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let bookId: String
    public var pageIndex: Int
    public var label: String
    public var createdAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        bookId: String,
        pageIndex: Int,
        label: String = "",
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.bookId = bookId
        self.pageIndex = pageIndex
        self.label = label
        self.createdAt = createdAt
    }
}

// MARK: - Preferences & Settings

public enum ReaderTheme: String, Codable, CaseIterable {
    case LIGHT
    case DARK
    case SEPIA
    case MINT
}

public enum PageScrollMode: String, Codable, CaseIterable {
    case CONTINUOUS_VERTICAL
    case HORIZONTAL_PAGING
    case SINGLE_PAGE
    case BOOK_CURL
}

public enum ReadingOrientation: String, Codable, CaseIterable {
    case AUTO
    case PORTRAIT
    case LANDSCAPE
}

public enum BookSortOrder: String, Codable, CaseIterable {
    case RECENTLY_OPENED
    case TITLE
    case AUTHOR
    case PROGRESS
    case DATE_ADDED
}

public struct UserReadingPreferences: Codable, Equatable {
    public var readerTheme: ReaderTheme = .LIGHT
    public var scrollMode: PageScrollMode = .CONTINUOUS_VERTICAL
    public var highResolutionRendering: Bool = true
    public var keepScreenOn: Bool = true
    public var pageSpacing: Int = 12
    public var brightness: Float = -1.0
    public var orientation: ReadingOrientation = .AUTO
    public var volumeButtonNavigation: Bool = false
    public var immersiveReading: Bool = false
    public var nightTreatment: Bool = false
    public var defaultHighlightColor: String = "#FFE600"
    public var penColor: String = "#5935FF"
    public var penWidth: Float = 3.5
    public var stylusOnly: Bool = false
    public var libraryGrid: Bool = true
    public var librarySort: BookSortOrder = .RECENTLY_OPENED
    
    public init() {}
}

public struct ReadingStats: Codable, Equatable {
    public var dailyGoalMinutes: Int = 30
    public var secondsReadToday: Int64 = 0
    public var currentStreakDays: Int = 1
    public var totalMinutesRead: Int = 0
    
    public var minutesReadToday: Int {
        Int(secondsReadToday / 60)
    }
    
    public var goalProgress: Double {
        guard dailyGoalMinutes > 0 else { return 0 }
        return min(1.0, Double(minutesReadToday) / Double(dailyGoalMinutes))
    }
    
    public init(
        dailyGoalMinutes: Int = 30,
        secondsReadToday: Int64 = 0,
        currentStreakDays: Int = 1,
        totalMinutesRead: Int = 0
    ) {
        self.dailyGoalMinutes = dailyGoalMinutes
        self.secondsReadToday = secondsReadToday
        self.currentStreakDays = currentStreakDays
        self.totalMinutesRead = totalMinutesRead
    }
}

// MARK: - Notes Models

public struct NoteFolder: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var colorHex: String
    public var parentId: String?
    public var sortOrder: Int
    public var createdAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        name: String,
        colorHex: String = "#5935FF",
        parentId: String? = nil,
        sortOrder: Int = 0,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.parentId = parentId
        self.sortOrder = sortOrder
        self.createdAt = createdAt
    }
}

public enum PageBackground: String, Codable, CaseIterable {
    case BLANK
    case RULED
    case NARROW
    case GRID
    case MATH_GRAPH
    case DOTTED
    case CORNELL
    case MUSIC
    case ISOMETRIC
    case CHECKLIST
    case TWO_COLUMN
    
    public var title: String {
        switch self {
        case .BLANK: return "Blank Canvas"
        case .RULED: return "Ruled"
        case .NARROW: return "Narrow Ruled"
        case .GRID: return "Square Grid"
        case .MATH_GRAPH: return "Math Graph"
        case .DOTTED: return "Dot Grid"
        case .CORNELL: return "Cornell Notes"
        case .MUSIC: return "Music Staff"
        case .ISOMETRIC: return "Isometric"
        case .CHECKLIST: return "Checklist"
        case .TWO_COLUMN: return "Two Column"
        }
    }
    
    public var subtitle: String {
        switch self {
        case .BLANK: return "Plain page for sketches and diagrams"
        case .RULED: return "Wide lines for handwriting and typing"
        case .NARROW: return "College-ruled lines, more per page"
        case .GRID: return "5 mm squares for layout and sketches"
        case .MATH_GRAPH: return "Fine multi-scale graph paper"
        case .DOTTED: return "5 mm dots for bullet journaling"
        case .CORNELL: return "Cues, notes, and summary areas"
        case .MUSIC: return "Staves for musical notation"
        case .ISOMETRIC: return "30° grid for 3D drawing"
        case .CHECKLIST: return "Tasks with checkboxes"
        case .TWO_COLUMN: return "Parallel side-by-side columns"
        }
    }
}

public enum PaperTheme: String, Codable, CaseIterable {
    case white = "White"
    case ivory = "Ivory"
    case sepia = "Sepia"
    case dark = "Dark"
    case blueprint = "Blueprint"
    
    public var hexColor: String {
        switch self {
        case .white: return "#FFFFFF"
        case .ivory: return "#FDFBF7"
        case .sepia: return "#FBF0D9"
        case .dark: return "#1E1E2E"
        case .blueprint: return "#1E3A8A"
        }
    }
    
    public var isDark: Bool {
        return self == .dark || self == .blueprint
    }
}

public enum ElementKind: String, Codable, CaseIterable {
    case TEXT
    case IMAGE
    case QUOTE
    case STICKY
    case TAPE
}

public struct NoteElement: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var kind: ElementKind
    public var x: Float
    public var y: Float
    public var width: Float
    public var height: Float
    public var rotation: Float
    public var z: Int
    public var content: String
    public var imagePath: String?
    public var quoteText: String?
    public var sourceBookId: String?
    public var sourcePage: Int?
    public var fillColor: String?
    
    public init(
        id: String = UUID().uuidString,
        kind: ElementKind = .TEXT,
        x: Float = 40,
        y: Float = 40,
        width: Float = 200,
        height: Float = 100,
        rotation: Float = 0,
        z: Int = 0,
        content: String = "",
        imagePath: String? = nil,
        quoteText: String? = nil,
        sourceBookId: String? = nil,
        sourcePage: Int? = nil,
        fillColor: String? = nil
    ) {
        self.id = id
        self.kind = kind
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.rotation = rotation
        self.z = z
        self.content = content
        self.imagePath = imagePath
        self.quoteText = quoteText
        self.sourceBookId = sourceBookId
        self.sourcePage = sourcePage
        self.fillColor = fillColor
    }
}

public struct NotePage: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let noteId: String
    public var pageIndex: Int
    public var background: PageBackground
    public var paperTheme: PaperTheme
    public var elements: [NoteElement]
    public var drawingFileName: String?
    
    public init(
        id: String = UUID().uuidString,
        noteId: String,
        pageIndex: Int = 0,
        background: PageBackground = .BLANK,
        paperTheme: PaperTheme = .white,
        elements: [NoteElement] = [],
        drawingFileName: String? = nil
    ) {
        self.id = id
        self.noteId = noteId
        self.pageIndex = pageIndex
        self.background = background
        self.paperTheme = paperTheme
        self.elements = elements
        self.drawingFileName = drawingFileName
    }
    
    // Custom decoding to support legacy records where paperTheme wasn't stored
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        noteId = try container.decode(String.self, forKey: .noteId)
        pageIndex = try container.decode(Int.self, forKey: .pageIndex)
        background = try container.decode(PageBackground.self, forKey: .background)
        paperTheme = try container.decodeIfPresent(PaperTheme.self, forKey: .paperTheme) ?? .white
        elements = try container.decode([NoteElement].self, forKey: .elements)
        drawingFileName = try container.decodeIfPresent(String.self, forKey: .drawingFileName)
    }
}

public struct Note: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var folderId: String?
    public var title: String
    public var pinned: Bool
    public var tags: [String]
    public var createdAt: Int64
    public var updatedAt: Int64
    public var thumbnailPath: String?
    public var pages: [NotePage]
    
    public init(
        id: String = UUID().uuidString,
        folderId: String? = nil,
        title: String = "Untitled Note",
        pinned: Bool = false,
        tags: [String] = [],
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        thumbnailPath: String? = nil,
        pages: [NotePage] = []
    ) {
        self.id = id
        self.folderId = folderId
        self.title = title
        self.pinned = pinned
        self.tags = tags
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.thumbnailPath = thumbnailPath
        self.pages = pages
    }
}

// MARK: - Flashcards Models

public struct Deck: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var colorHex: String
    public var parentId: String?
    public var bookId: String?
    public var newPerDay: Int
    public var reviewsPerDay: Int
    public var createdAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        name: String,
        colorHex: String = "#5935FF",
        parentId: String? = nil,
        bookId: String? = nil,
        newPerDay: Int = 20,
        reviewsPerDay: Int = 50,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.parentId = parentId
        self.bookId = bookId
        self.newPerDay = newPerDay
        self.reviewsPerDay = reviewsPerDay
        self.createdAt = createdAt
    }
}

public enum CardType: String, Codable, CaseIterable {
    case BASIC
    case REVERSED
    case CLOZE
}

public struct CardFace: Codable, Equatable, Hashable {
    public var text: String
    public var imagePath: String?
    
    public init(text: String = "", imagePath: String? = nil) {
        self.text = text
        self.imagePath = imagePath
    }
}

public struct Card: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var deckId: String
    public var type: CardType
    public var front: CardFace
    public var back: CardFace
    public var sourceBookId: String?
    public var sourcePage: Int?
    public var tags: [String]
    public var createdAt: Int64
    public var updatedAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        deckId: String,
        type: CardType = .BASIC,
        front: CardFace = CardFace(),
        back: CardFace = CardFace(),
        sourceBookId: String? = nil,
        sourcePage: Int? = nil,
        tags: [String] = [],
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.deckId = deckId
        self.type = type
        self.front = front
        self.back = back
        self.sourceBookId = sourceBookId
        self.sourcePage = sourcePage
        self.tags = tags
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public enum CardState: String, Codable, CaseIterable {
    case NEW
    case LEARNING
    case RELEARNING
    case REVIEW
}

public enum Rating: String, Codable, CaseIterable {
    case AGAIN
    case HARD
    case GOOD
    case EASY
}

public struct CardSchedule: Identifiable, Codable, Equatable, Hashable {
    public var id: String { "\(cardId)_\(ordinal)" }
    public let cardId: String
    public let ordinal: Int
    public var state: CardState
    public var ease: Float
    public var intervalDays: Float
    public var dueAt: Int64
    public var step: Int
    public var reps: Int
    public var lapses: Int
    
    public init(
        cardId: String,
        ordinal: Int = 0,
        state: CardState = .NEW,
        ease: Float = 2.5,
        intervalDays: Float = 0.0,
        dueAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        step: Int = 0,
        reps: Int = 0,
        lapses: Int = 0
    ) {
        self.cardId = cardId
        self.ordinal = ordinal
        self.state = state
        self.ease = ease
        self.intervalDays = intervalDays
        self.dueAt = dueAt
        self.step = step
        self.reps = reps
        self.lapses = lapses
    }
}

public struct ReviewLog: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let cardId: String
    public let deckId: String
    public let ordinal: Int
    public let rating: Rating
    public let previousState: CardState
    public let elapsedMs: Int64
    public let reviewedAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        cardId: String,
        deckId: String,
        ordinal: Int = 0,
        rating: Rating,
        previousState: CardState,
        elapsedMs: Int64 = 0,
        reviewedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.cardId = cardId
        self.deckId = deckId
        self.ordinal = ordinal
        self.rating = rating
        self.previousState = previousState
        self.elapsedMs = elapsedMs
        self.reviewedAt = reviewedAt
    }
}

// MARK: - ESign Models

public enum SignatureKind: String, Codable, CaseIterable {
    case SIGNATURE
    case INITIALS
    
    public var title: String {
        switch self {
        case .SIGNATURE: return "Signature"
        case .INITIALS: return "Initials"
        }
    }
}

public struct SavedSignature: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var kind: SignatureKind
    public var imageFileName: String
    public var createdAt: Int64
    
    public init(
        id: String = UUID().uuidString,
        kind: SignatureKind = .SIGNATURE,
        imageFileName: String,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.kind = kind
        self.imageFileName = imageFileName
        self.createdAt = createdAt
    }
}

public enum FieldKind: String, Codable, CaseIterable {
    case SIGNATURE
    case INITIALS
    case TEXT
    case DATE
    case CHECK
    case CROSS
    
    public var isImage: Bool { self == .SIGNATURE || self == .INITIALS }
    public var isText: Bool { self == .TEXT || self == .DATE }
}

public struct SignField: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var page: Int
    public var kind: FieldKind
    public var x: Float
    public var y: Float
    public var width: Float
    public var height: Float
    public var text: String
    public var signatureId: String?
    public var colorHex: String
    
    public init(
        id: String = UUID().uuidString,
        page: Int = 0,
        kind: FieldKind = .SIGNATURE,
        x: Float = 0.3,
        y: Float = 0.5,
        width: Float = 0.25,
        height: Float = 0.08,
        text: String = "",
        signatureId: String? = nil,
        colorHex: String = "#101326"
    ) {
        self.id = id
        self.page = page
        self.kind = kind
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.text = text
        self.signatureId = signatureId
        self.colorHex = colorHex
    }
}
