import SwiftUI

public struct CardsScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var showingCreateDeckAlert = false
    @State private var newDeckName = ""
    @State private var reviewingDeck: Deck? = nil
    @State private var addingCardToDeckId: String? = nil
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if storage.decks.isEmpty {
                        VStack(spacing: 12) {
                            Spacer(minLength: 60)
                            Image(systemName: "rectangle.stack")
                                .font(.system(size: 48))
                                .foregroundColor(Theme.appTextSecondary.opacity(0.6))
                            Text("No Decks Yet")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Text("Create a deck to study key concepts with spaced repetition")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.appTextSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 40)
                            Button(action: { showingCreateDeckAlert = true }) {
                                Text("Create First Deck")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 10)
                                    .background(Capsule().fill(Theme.brandPurple))
                            }
                            .padding(.top, 8)
                        }
                    } else {
                        ForEach(storage.decks) { deck in
                            let deckCards = storage.cards.filter { $0.deckId == deck.id }
                            let dueCount = storage.schedules.filter { sched in
                                deckCards.contains { $0.id == sched.cardId } && Scheduler.shared.isDue(schedule: sched)
                            }.count
                            
                            VStack(alignment: .leading, spacing: 14) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(deck.name)
                                            .font(.system(size: 18, weight: .bold))
                                            .foregroundColor(Theme.appTextPrimary)
                                        Text("\(deckCards.count) cards total • \(dueCount) due today")
                                            .font(.system(size: 13))
                                            .foregroundColor(Theme.appTextSecondary)
                                    }
                                    Spacer()
                                    
                                    Button(action: { addingCardToDeckId = deck.id }) {
                                        Image(systemName: "plus.circle")
                                            .font(.system(size: 22))
                                            .foregroundColor(Theme.brandPurple)
                                    }
                                }
                                
                                HStack(spacing: 12) {
                                    Button(action: { reviewingDeck = deck }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "play.fill")
                                                .font(.system(size: 12))
                                            Text(dueCount > 0 ? "Study Now (\(dueCount))" : "Review All (\(deckCards.count))")
                                                .font(.system(size: 14, weight: .bold))
                                        }
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 10)
                                                .fill(dueCount > 0 ? Theme.brandPurple : Theme.brandPurpleLight)
                                        )
                                    }
                                    .disabled(deckCards.isEmpty)
                                }
                            }
                            .padding(18)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(Theme.appSurface)
                                    .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
                            )
                            .contextMenu {
                                Button {
                                    addingCardToDeckId = deck.id
                                } label: {
                                    Label("Add Card", systemImage: "plus")
                                }
                                Button(role: .destructive) {
                                    storage.decks.removeAll { $0.id == deck.id }
                                    storage.cards.removeAll { $0.deckId == deck.id }
                                    storage.saveAll()
                                } label: {
                                    Label("Delete Deck", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .navigationTitle("Flashcards")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingCreateDeckAlert = true }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.brandPurple)
                    }
                }
            }
            .alert("New Deck", isPresented: $showingCreateDeckAlert) {
                TextField("Deck Name", text: $newDeckName)
                Button("Cancel", role: .cancel) { newDeckName = "" }
                Button("Create") {
                    guard !newDeckName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    let deck = Deck(name: newDeckName.trimmingCharacters(in: .whitespaces))
                    storage.decks.append(deck)
                    storage.saveAll()
                    newDeckName = ""
                }
            }
            .sheet(item: $reviewingDeck) { deck in
                ReviewView(deck: deck)
            }
            .sheet(isPresented: Binding(
                get: { addingCardToDeckId != nil },
                set: { if !$0 { addingCardToDeckId = nil } }
            )) {
                if let deckId = addingCardToDeckId {
                    CardEditorScreen(deckId: deckId)
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
        }
    }
}
