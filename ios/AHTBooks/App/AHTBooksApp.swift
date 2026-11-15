import SwiftUI

@main
struct AHTBooksApp: App {
    @StateObject private var storage = StorageManager.shared
    @State private var openedBookFromURL: Book? = nil
    
    var body: some Scene {
        WindowGroup {
            MainTabView()
                .preferredColorScheme(storage.preferences.nightTreatment ? .dark : nil)
                .onOpenURL { url in
                    if url.pathExtension.lowercased() == "pdf" {
                        if let imported = storage.importPDF(from: url) {
                            self.openedBookFromURL = imported
                        }
                    }
                }
                .fullScreenCover(item: $openedBookFromURL) { book in
                    NavigationStack {
                        PDFReaderView(book: book)
                    }
                }
        }
    }
}
