import SwiftUI

public struct MainTabView: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var selectedTab: Int = 0
    @State private var activeBook: Book? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                HomeScreen(selectedTab: $selectedTab, activeBook: $activeBook)
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    .tag(0)
                
                LibraryScreen(activeBook: $activeBook)
                    .tabItem {
                        Label("Library", systemImage: "books.vertical.fill")
                    }
                    .tag(1)
                
                NotesHomeScreen()
                    .tabItem {
                        Label("Notes", systemImage: "pencil.and.outline")
                    }
                    .tag(2)
                
                CardsScreen()
                    .tabItem {
                        Label("Flashcards", systemImage: "rectangle.stack.fill")
                    }
                    .tag(3)
                
                ESignHomeScreen()
                    .tabItem {
                        Label("Fill & Sign", systemImage: "signature")
                    }
                    .tag(4)
            }
            .tint(Theme.brandPurple)
        }
        .fullScreenCover(item: $activeBook) { book in
            NavigationStack {
                PDFReaderView(book: book)
            }
        }
    }
}
