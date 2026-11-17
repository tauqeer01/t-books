import SwiftUI

@main
struct AHTBooksApp: App {
    @StateObject private var storage = StorageManager.shared
    @State private var openedBookFromURL: Book? = nil
    @AppStorage(AppThemeMode.storageKey) private var themeMode: AppThemeMode = .system

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .onAppear { applyTheme(themeMode) }
                .onChange(of: themeMode) { _, mode in applyTheme(mode) }
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

    /// Applied on the windows rather than with preferredColorScheme, which doesn't return to the
    /// system appearance after being forced and doesn't reach every presented sheet.
    private func applyTheme(_ mode: AppThemeMode) {
        let style: UIUserInterfaceStyle
        switch mode {
        case .system: style = .unspecified
        case .light: style = .light
        case .dark: style = .dark
        }
        for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
            scene.windows.forEach { $0.overrideUserInterfaceStyle = style }
        }
    }
}
