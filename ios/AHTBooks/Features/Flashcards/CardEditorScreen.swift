import SwiftUI

public struct CardEditorScreen: View {
    public let deckId: String
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var cardType: CardType = .BASIC
    @State private var frontText: String = ""
    @State private var backText: String = ""
    @State private var tagsText: String = ""
    
    /// Source of a card started from the reader.
    private let sourceBookId: String?
    private let sourcePage: Int?
    /// Deck to create on save when it does not exist yet (e.g. a new deck named after the book).
    private let deckToCreate: Deck?

    public init(
        deckId: String,
        initialType: CardType = .BASIC,
        initialFront: String = "",
        initialBack: String = "",
        sourceBookId: String? = nil,
        sourcePage: Int? = nil,
        deckToCreate: Deck? = nil
    ) {
        self.deckId = deckId
        self.sourceBookId = sourceBookId
        self.sourcePage = sourcePage
        self.deckToCreate = deckToCreate
        self._cardType = State(initialValue: initialType)
        self._frontText = State(initialValue: initialFront)
        self._backText = State(initialValue: initialBack)
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Card Type")) {
                    Picker("Type", selection: $cardType) {
                        Text("Basic").tag(CardType.BASIC)
                        Text("Reversed").tag(CardType.REVERSED)
                        Text("Cloze (Fill-in-blank)").tag(CardType.CLOZE)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                }
                
                Section(header: Text(cardType == .CLOZE ? "Text with Cloze Deletion" : "Front (Question)")) {
                    TextEditor(text: $frontText)
                        .frame(height: 100)
                    
                    if cardType == .CLOZE {
                        Button("Wrap Selection as Cloze {{c1::...}}") {
                            // Example auto cloze helper
                            if !frontText.isEmpty {
                                frontText = "{{c1::\(frontText)}}"
                            }
                        }
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Theme.brandPurple)
                    }
                }
                
                if cardType != .CLOZE {
                    Section(header: Text("Back (Answer)")) {
                        TextEditor(text: $backText)
                            .frame(height: 100)
                    }
                }
                
                Section(header: Text("Tags (comma separated)")) {
                    TextField("e.g. aviation, hydraulics, chapter1", text: $tagsText)
                }
            }
            .navigationTitle("New Flashcard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        saveCard()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
        }
    }
    
    private func saveCard() {
        guard !frontText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let tags = tagsText.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        
        let card = Card(
            deckId: deckId,
            type: cardType,
            front: CardFace(text: frontText),
            back: CardFace(text: backText),
            sourceBookId: sourceBookId,
            sourcePage: sourcePage,
            tags: tags
        )
        let schedule = CardSchedule(cardId: card.id)
        if let deck = deckToCreate, !storage.decks.contains(where: { $0.id == deck.id }) {
            storage.decks.append(deck)
        }

        storage.cards.append(card)
        storage.schedules.append(schedule)
        storage.saveAll()
        dismiss()
    }
}
