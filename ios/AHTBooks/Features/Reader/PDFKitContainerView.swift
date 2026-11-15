import SwiftUI
import PDFKit

public struct PDFKitContainerView: UIViewRepresentable {
    public let pdfDocument: PDFDocument
    public let initialPageIndex: Int
    public let scrollMode: PageScrollMode
    public let theme: ReaderTheme
    public var onPageChanged: (Int, Int) -> Void
    public var onTextSelected: (String, CGRect, Int) -> Void
    
    public init(
        pdfDocument: PDFDocument,
        initialPageIndex: Int,
        scrollMode: PageScrollMode,
        theme: ReaderTheme,
        onPageChanged: @escaping (Int, Int) -> Void,
        onTextSelected: @escaping (String, CGRect, Int) -> Void
    ) {
        self.pdfDocument = pdfDocument
        self.initialPageIndex = initialPageIndex
        self.scrollMode = scrollMode
        self.theme = theme
        self.onPageChanged = onPageChanged
        self.onTextSelected = onTextSelected
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }
    
    public func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.document = pdfDocument
        pdfView.autoScales = true
        pdfView.delegate = context.coordinator
        
        applyDisplayMode(to: pdfView)
        applyTheme(to: pdfView)
        
        // Jump to initial page
        if initialPageIndex >= 0, initialPageIndex < pdfDocument.pageCount,
           let targetPage = pdfDocument.page(at: initialPageIndex) {
            DispatchQueue.main.async {
                pdfView.go(to: targetPage)
            }
        }
        
        // Listen to page changes
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.pageChanged(_:)),
            name: .PDFViewPageChanged,
            object: pdfView
        )
        
        // Listen to text selections
        NotificationCenter.default.addObserver(
            context.coordinator,
            selector: #selector(Coordinator.selectionChanged(_:)),
            name: .PDFViewSelectionChanged,
            object: pdfView
        )
        
        return pdfView
    }
    
    public func updateUIView(_ pdfView: PDFView, context: Context) {
        if pdfView.document !== pdfDocument {
            pdfView.document = pdfDocument
        }
        applyDisplayMode(to: pdfView)
        applyTheme(to: pdfView)
    }
    
    private func applyDisplayMode(to pdfView: PDFView) {
        switch scrollMode {
        case .CONTINUOUS_VERTICAL:
            pdfView.displayMode = .singlePageContinuous
            pdfView.displayDirection = .vertical
            pdfView.usePageViewController(false)
        case .HORIZONTAL_PAGING:
            pdfView.displayMode = .singlePage
            pdfView.displayDirection = .horizontal
            pdfView.usePageViewController(false)
        case .SINGLE_PAGE:
            pdfView.displayMode = .singlePage
            pdfView.displayDirection = .vertical
            pdfView.usePageViewController(false)
        case .BOOK_CURL:
            pdfView.displayMode = .singlePage
            pdfView.usePageViewController(true)
        }
    }
    
    private func applyTheme(to pdfView: PDFView) {
        switch theme {
        case .LIGHT:
            pdfView.backgroundColor = UIColor(Color(hex: "#F8F8FF"))
        case .DARK:
            pdfView.backgroundColor = UIColor(Color(hex: "#0B1120"))
        case .SEPIA:
            pdfView.backgroundColor = UIColor(Color(hex: "#F8F1E5"))
        case .MINT:
            pdfView.backgroundColor = UIColor(Color(hex: "#E8F5E9"))
        }
    }
    
    public class Coordinator: NSObject, PDFViewDelegate {
        var parent: PDFKitContainerView
        
        init(parent: PDFKitContainerView) {
            self.parent = parent
        }
        
        @objc func pageChanged(_ notification: Notification) {
            guard let pdfView = notification.object as? PDFView,
                  let currentPage = pdfView.currentPage,
                  let doc = pdfView.document else { return }
            let index = doc.index(for: currentPage)
            parent.onPageChanged(index, doc.pageCount)
        }
        
        @objc func selectionChanged(_ notification: Notification) {
            guard let pdfView = notification.object as? PDFView,
                  let selection = pdfView.currentSelection,
                  let text = selection.string, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  let currentPage = pdfView.currentPage,
                  let doc = pdfView.document else { return }
            
            let pageIndex = doc.index(for: currentPage)
            let bounds = selection.bounds(for: currentPage)
            parent.onTextSelected(text, bounds, pageIndex)
        }
    }
}
