import SwiftUI
import PDFKit

public struct SignEditorView: View {
    public let book: Book
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var pdfDocument: PDFDocument?
    @State private var currentPageIndex: Int = 0
    @State private var totalPages: Int = 1
    @State private var placedFields: [SignField] = []
    @State private var selectedFieldId: String? = nil
    @State private var showingSignaturePicker: Bool = false
    @State private var showingShareSheet: Bool = false
    @State private var signedPDFURL: URL? = nil
    
    public init(book: Book) {
        self.book = book
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Top Tool Selector Bar
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        signToolButton(title: "Signature", icon: "signature") {
                            showingSignaturePicker = true
                        }
                        signToolButton(title: "Initials", icon: "person.crop.rectangle") {
                            addInitialsField()
                        }
                        signToolButton(title: "Date", icon: "calendar") {
                            addDateField()
                        }
                        signToolButton(title: "Text", icon: "textformat") {
                            addTextField()
                        }
                        signToolButton(title: "Check", icon: "checkmark") {
                            addCheckField()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
                .background(Theme.appSurface)
                
                // Document Page Canvas with Draggable Fields
                GeometryReader { geo in
                    ZStack {
                        if let doc = pdfDocument, let page = doc.page(at: currentPageIndex) {
                            let pageImg = page.thumbnail(of: geo.size, for: .mediaBox)
                            Image(uiImage: pageImg)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 3)
                            
                            // Fields on this page
                            ForEach($placedFields) { $field in
                                if field.page == currentPageIndex {
                                    DraggableSignFieldView(
                                        field: $field,
                                        containerSize: geo.size,
                                        isSelected: selectedFieldId == field.id,
                                        onSelect: { selectedFieldId = field.id },
                                        onDelete: {
                                            placedFields.removeAll { $0.id == field.id }
                                        }
                                    )
                                }
                            }
                        } else {
                            ProgressView()
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .background(Color(hex: "#F1F5F9"))
                
                // Bottom Page Switcher Bar
                HStack {
                    Button(action: {
                        if currentPageIndex > 0 { currentPageIndex -= 1 }
                    }) {
                        Image(systemName: "chevron.left")
                    }
                    .disabled(currentPageIndex == 0)
                    
                    Spacer()
                    Text("Page \(currentPageIndex + 1) of \(max(1, totalPages))")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Theme.appTextPrimary)
                    Spacer()
                    
                    Button(action: {
                        if currentPageIndex < totalPages - 1 { currentPageIndex += 1 }
                    }) {
                        Image(systemName: "chevron.right")
                    }
                    .disabled(currentPageIndex >= totalPages - 1)
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .background(Theme.appSurface)
            }
            .navigationTitle("Sign Document")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save Signed PDF") {
                        generateAndExportSignedPDF()
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
            .onAppear {
                loadDocument()
            }
            .sheet(isPresented: $showingSignaturePicker) {
                SignaturePickerSheet { sig in
                    addSignatureField(sig: sig)
                }
            }
            .sheet(isPresented: $showingShareSheet) {
                if let url = signedPDFURL {
                    ShareSheet(items: [url])
                }
            }
        }
    }
    
    private func signToolButton(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title)
            }
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(Theme.brandPurple)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(Theme.brandPurpleSoft))
        }
    }
    
    private func loadDocument() {
        let url = URL(fileURLWithPath: book.filePath)
        if let doc = PDFDocument(url: url) {
            self.pdfDocument = doc
            self.totalPages = doc.pageCount
        }
    }
    
    private func addSignatureField(sig: SavedSignature) {
        let field = SignField(
            page: currentPageIndex,
            kind: .SIGNATURE,
            x: 0.35,
            y: 0.45,
            width: 0.3,
            height: 0.1,
            signatureId: sig.id
        )
        placedFields.append(field)
    }
    
    private func addInitialsField() {
        if let initSig = storage.savedSignatures.first(where: { $0.kind == .INITIALS }) {
            let field = SignField(
                page: currentPageIndex,
                kind: .INITIALS,
                x: 0.4,
                y: 0.45,
                width: 0.18,
                height: 0.08,
                signatureId: initSig.id
            )
            placedFields.append(field)
        }
    }
    
    private func addDateField() {
        let df = DateFormatter()
        df.dateStyle = .medium
        let field = SignField(
            page: currentPageIndex,
            kind: .DATE,
            x: 0.4,
            y: 0.45,
            width: 0.25,
            height: 0.05,
            text: df.string(from: Date())
        )
        placedFields.append(field)
    }
    
    private func addTextField() {
        let field = SignField(
            page: currentPageIndex,
            kind: .TEXT,
            x: 0.35,
            y: 0.45,
            width: 0.3,
            height: 0.06,
            text: "Full Name"
        )
        placedFields.append(field)
    }
    
    private func addCheckField() {
        let field = SignField(
            page: currentPageIndex,
            kind: .CHECK,
            x: 0.45,
            y: 0.45,
            width: 0.08,
            height: 0.05
        )
        placedFields.append(field)
    }
    
    private func generateAndExportSignedPDF() {
        guard let doc = pdfDocument else { return }
        let destURL = FileManager.default.temporaryDirectory.appendingPathComponent("Signed_\(book.title).pdf")
        
        // Render document with stamped signatures
        let pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdfData, .zero, nil)
        
        for pIndex in 0..<doc.pageCount {
            guard let page = doc.page(at: pIndex) else { continue }
            let pageBounds = page.bounds(for: .mediaBox)
            UIGraphicsBeginPDFPageWithInfo(pageBounds, nil)
            guard let context = UIGraphicsGetCurrentContext() else { continue }
            
            // Draw original PDF page
            context.saveGState()
            context.translateBy(x: 0, y: pageBounds.height)
            context.scaleBy(x: 1.0, y: -1.0)
            page.draw(with: .mediaBox, to: context)
            context.restoreGState()
            
            // Draw placed fields
            let fieldsOnPage = placedFields.filter { $0.page == pIndex }
            for field in fieldsOnPage {
                let rect = CGRect(
                    x: CGFloat(field.x) * pageBounds.width,
                    y: CGFloat(field.y) * pageBounds.height,
                    width: CGFloat(field.width) * pageBounds.width,
                    height: CGFloat(field.height) * pageBounds.height
                )
                
                if field.kind == .SIGNATURE || field.kind == .INITIALS,
                   let sigId = field.signatureId,
                   let sig = storage.savedSignatures.first(where: { $0.id == sigId }),
                   let img = storage.getSignatureImage(fileName: sig.imageFileName) {
                    img.draw(in: rect)
                } else if field.kind == .DATE || field.kind == .TEXT {
                    let attr: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 14, weight: .semibold),
                        .foregroundColor: UIColor(Color(hex: field.colorHex))
                    ]
                    field.text.draw(in: rect, withAttributes: attr)
                } else if field.kind == .CHECK {
                    let attr: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 20, weight: .bold),
                        .foregroundColor: UIColor.blue
                    ]
                    "✓".draw(in: rect, withAttributes: attr)
                }
            }
        }
        
        UIGraphicsEndPDFContext()
        pdfData.write(to: destURL, atomically: true)
        
        self.signedPDFURL = destURL
        self.showingShareSheet = true
    }
}

// MARK: - Draggable Sign Field Component

private struct DraggableSignFieldView: View {
    @Binding var field: SignField
    let containerSize: CGSize
    let isSelected: Bool
    var onSelect: () -> Void
    var onDelete: () -> Void
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.white.opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(isSelected ? Theme.brandPurple : Color.blue.opacity(0.6), lineWidth: isSelected ? 2 : 1)
                )
            
            if field.kind.isImage,
               let sigId = field.signatureId,
               let sig = StorageManager.shared.savedSignatures.first(where: { $0.id == sigId }),
               let img = StorageManager.shared.getSignatureImage(fileName: sig.imageFileName) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .padding(4)
            } else if field.kind == .CHECK {
                Text("✓")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
            } else {
                Text(field.text)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.appTextPrimary)
                    .padding(4)
            }
            
            if isSelected {
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.red)
                        .font(.system(size: 16))
                        .offset(x: 6, y: -6)
                }
            }
        }
        .frame(
            width: max(50, CGFloat(field.width) * containerSize.width),
            height: max(30, CGFloat(field.height) * containerSize.height)
        )
        .position(
            x: CGFloat(field.x) * containerSize.width + (CGFloat(field.width) * containerSize.width) / 2,
            y: CGFloat(field.y) * containerSize.height + (CGFloat(field.height) * containerSize.height) / 2
        )
        .gesture(
            DragGesture()
                .onChanged { gesture in
                    onSelect()
                    field.x = Float((gesture.location.x - (CGFloat(field.width) * containerSize.width) / 2) / containerSize.width)
                    field.y = Float((gesture.location.y - (CGFloat(field.height) * containerSize.height) / 2) / containerSize.height)
                }
        )
        .onTapGesture {
            onSelect()
        }
    }
}

// MARK: - Signature Picker Sheet

private struct SignaturePickerSheet: View {
    @ObservedObject var storage = StorageManager.shared
    var onSelect: (SavedSignature) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            List {
                ForEach(storage.savedSignatures) { sig in
                    Button(action: {
                        onSelect(sig)
                        dismiss()
                    }) {
                        HStack {
                            if let img = storage.getSignatureImage(fileName: sig.imageFileName) {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(height: 50)
                            }
                            Spacer()
                            Text(sig.kind.title)
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                    }
                }
            }
            .navigationTitle("Select Signature")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
