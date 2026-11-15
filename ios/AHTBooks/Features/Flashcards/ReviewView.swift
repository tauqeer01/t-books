import SwiftUI
import PencilKit

public struct ReviewView: View {
    public let deck: Deck
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var studyCards: [Card] = []
    @State private var currentCardIndex: Int = 0
    @State private var isFlipped: Bool = false
    @State private var scratchDrawing = PKDrawing()
    @State private var showScratchpad: Bool = false
    
    public init(deck: Deck) {
        self.deck = deck
    }
    
    private var currentCard: Card? {
        guard currentCardIndex < studyCards.count else { return nil }
        return studyCards[currentCardIndex]
    }
    
    private var currentSchedule: CardSchedule {
        guard let card = currentCard else { return CardSchedule(cardId: "") }
        return storage.schedules.first(where: { $0.cardId == card.id }) ?? CardSchedule(cardId: card.id)
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let card = currentCard {
                    // Study Progress Bar
                    VStack(spacing: 6) {
                        HStack {
                            Text("Card \(currentCardIndex + 1) of \(studyCards.count)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                            Spacer()
                            Button(action: { showScratchpad.toggle() }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "pencil.tip")
                                    Text(showScratchpad ? "Hide Scratchpad" : "Scratchpad")
                                }
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                            }
                        }
                        
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Theme.brandPurpleSoft).frame(height: 4)
                                Capsule().fill(Theme.brandPurple)
                                    .frame(width: geo.size.width * CGFloat(currentCardIndex + 1) / CGFloat(max(1, studyCards.count)), height: 4)
                            }
                        }
                        .frame(height: 4)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    
                    // Main Flip Card View
                    ZStack {
                        // Card Container
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(Theme.appSurface)
                            .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)
                        
                        VStack(spacing: 16) {
                            Text(isFlipped ? "ANSWER" : "QUESTION")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(isFlipped ? Theme.highlightGreen : Theme.brandPurple)
                                .tracking(1.5)
                            
                            Spacer()
                            
                            // Question or Answer Text
                            if !isFlipped {
                                if card.type == .CLOZE {
                                    Text(Cloze.render(text: card.front.text, ordinal: 1, revealed: false))
                                        .font(.system(size: 20, weight: .medium))
                                        .multilineTextAlignment(.center)
                                        .foregroundColor(Theme.appTextPrimary)
                                } else {
                                    Text(card.front.text)
                                        .font(.system(size: 20, weight: .semibold))
                                        .multilineTextAlignment(.center)
                                        .foregroundColor(Theme.appTextPrimary)
                                }
                            } else {
                                if card.type == .CLOZE {
                                    Text(Cloze.render(text: card.front.text, ordinal: 1, revealed: true))
                                        .font(.system(size: 20, weight: .medium))
                                        .multilineTextAlignment(.center)
                                        .foregroundColor(Theme.appTextPrimary)
                                } else {
                                    VStack(spacing: 12) {
                                        Text(card.front.text)
                                            .font(.system(size: 15))
                                            .foregroundColor(Theme.appTextSecondary)
                                            .multilineTextAlignment(.center)
                                        Divider().padding(.horizontal, 40)
                                        Text(card.back.text)
                                            .font(.system(size: 20, weight: .bold))
                                            .foregroundColor(Theme.appTextPrimary)
                                            .multilineTextAlignment(.center)
                                    }
                                }
                            }
                            
                            Spacer()
                            
                            if !isFlipped {
                                Text("Tap anywhere to reveal answer")
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.appTextSecondary)
                            }
                        }
                        .padding(28)
                        
                        // Practice Scratchpad Overlay (Apple Pencil)
                        if showScratchpad {
                            ScratchpadCanvas(drawing: $scratchDrawing)
                                .cornerRadius(20)
                        }
                    }
                    .padding(20)
                    .onTapGesture {
                        if !showScratchpad {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                                isFlipped.toggle()
                            }
                        }
                    }
                    
                    // SM-2 Review Buttons Action Bar (Revealed when flipped)
                    if isFlipped {
                        let previews = Scheduler.shared.preview(schedule: currentSchedule)
                        HStack(spacing: 10) {
                            reviewButton(title: "Again", subtitle: previews[.AGAIN] ?? "<1m", color: Theme.annotationRed, rating: .AGAIN)
                            reviewButton(title: "Hard", subtitle: previews[.HARD] ?? "10m", color: Theme.pastelOrange, rating: .HARD)
                            reviewButton(title: "Good", subtitle: previews[.GOOD] ?? "1d", color: Theme.brandPurple, rating: .GOOD)
                            reviewButton(title: "Easy", subtitle: previews[.EASY] ?? "4d", color: Theme.highlightGreen, rating: .EASY)
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                    } else {
                        Button(action: {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                                isFlipped = true
                            }
                        }) {
                            Text("Show Answer")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Capsule().fill(Theme.brandPurple))
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                    }
                } else {
                    // Completed Deck View
                    VStack(spacing: 16) {
                        Spacer()
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 64))
                            .foregroundColor(Theme.brandPurple)
                        Text("Session Complete!")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                        Text("You've reviewed all cards due in this deck for today.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.appTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                        
                        Button(action: { dismiss() }) {
                            Text("Back to Decks")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 28)
                                .padding(.vertical, 12)
                                .background(Capsule().fill(Theme.brandPurple))
                        }
                        .padding(.top, 12)
                        Spacer()
                    }
                }
            }
            .navigationTitle(deck.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") { dismiss() }
                        .foregroundColor(Theme.brandPurple)
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
            .onAppear {
                loadStudyCards()
            }
        }
    }
    
    private func reviewButton(title: String, subtitle: String, color: Color, rating: Rating) -> some View {
        Button(action: {
            processAnswer(rating: rating)
        }) {
            VStack(spacing: 3) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white.opacity(0.85))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(color))
        }
    }
    
    private func loadStudyCards() {
        let deckCards = storage.cards.filter { $0.deckId == deck.id }
        self.studyCards = deckCards.shuffled()
        self.currentCardIndex = 0
        self.isFlipped = false
        self.scratchDrawing = PKDrawing()
    }
    
    private func processAnswer(rating: Rating) {
        guard let card = currentCard else { return }
        let currentSched = currentSchedule
        let newSched = Scheduler.shared.answer(schedule: currentSched, rating: rating)
        
        if let idx = storage.schedules.firstIndex(where: { $0.cardId == card.id }) {
            storage.schedules[idx] = newSched
        } else {
            storage.schedules.append(newSched)
        }
        
        let log = ReviewLog(
            cardId: card.id,
            deckId: deck.id,
            ordinal: 0,
            rating: rating,
            previousState: currentSched.state
        )
        storage.reviewLogs.append(log)
        storage.saveAll()
        
        scratchDrawing = PKDrawing()
        withAnimation(.easeOut(duration: 0.25)) {
            isFlipped = false
            currentCardIndex += 1
        }
    }
}

// Scratchpad Canvas for handwriting answers
private struct ScratchpadCanvas: UIViewRepresentable {
    @Binding var drawing: PKDrawing
    
    func makeUIView(context: Context) -> PKCanvasView {
        let canvas = PKCanvasView()
        canvas.backgroundColor = UIColor.white.withAlphaComponent(0.85)
        canvas.drawing = drawing
        canvas.tool = PKInkingTool(.pen, color: .black, width: 2.0)
        return canvas
    }
    
    func updateUIView(_ canvas: PKCanvasView, context: Context) {
        canvas.drawing = drawing
    }
}
