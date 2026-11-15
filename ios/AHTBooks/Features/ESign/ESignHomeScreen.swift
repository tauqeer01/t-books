import SwiftUI

public struct ESignHomeScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @State private var showingSignaturePad: Bool = false
    @State private var signaturePadKind: SignatureKind = .SIGNATURE
    @State private var selectedBookToSign: Book? = nil
    @State private var showingDocPicker: Bool = false
    
    public init() {}
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Quick Action: Sign a Document
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SIGN A DOCUMENT")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Theme.appTextSecondary)
                            .tracking(1)
                        
                        Button(action: { showingDocPicker = true }) {
                            HStack(spacing: 16) {
                                ZStack {
                                    Circle().fill(Theme.brandPurpleSoft).frame(width: 48, height: 48)
                                    Image(systemName: "signature")
                                        .font(.system(size: 22))
                                        .foregroundColor(Theme.brandPurple)
                                }
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Choose PDF to Sign")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(Theme.appTextPrimary)
                                    Text("Place signature, initials, date, or text")
                                        .font(.system(size: 13))
                                        .foregroundColor(Theme.appTextSecondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(Theme.appTextSecondary)
                            }
                            .padding(16)
                            .background(
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(Theme.appSurface)
                                    .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    
                    // Saved Signatures Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Saved Signatures")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Button("+ Add") {
                                signaturePadKind = .SIGNATURE
                                showingSignaturePad = true
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                        }
                        .padding(.horizontal, 20)
                        
                        let signatures = storage.savedSignatures.filter { $0.kind == .SIGNATURE }
                        if signatures.isEmpty {
                            Text("No saved signatures yet. Tap + Add to draw one.")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.appTextSecondary)
                                .padding(.horizontal, 20)
                        } else {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(signatures) { sig in
                                        if let img = storage.getSignatureImage(fileName: sig.imageFileName) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 12)
                                                    .fill(Theme.appSurface)
                                                    .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                                                Image(uiImage: img)
                                                    .resizable()
                                                    .scaledToFit()
                                                    .frame(height: 70)
                                                    .padding(12)
                                            }
                                            .frame(width: 160, height: 90)
                                            .contextMenu {
                                                Button(role: .destructive) {
                                                    storage.deleteSignature(sig)
                                                } label: {
                                                    Label("Delete", systemImage: "trash")
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                    }
                    
                    // Saved Initials Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Saved Initials")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Button("+ Add") {
                                signaturePadKind = .INITIALS
                                showingSignaturePad = true
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.brandPurple)
                        }
                        .padding(.horizontal, 20)
                        
                        let initials = storage.savedSignatures.filter { $0.kind == .INITIALS }
                        if initials.isEmpty {
                            Text("No saved initials yet. Tap + Add to draw initials.")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.appTextSecondary)
                                .padding(.horizontal, 20)
                        } else {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(initials) { sig in
                                        if let img = storage.getSignatureImage(fileName: sig.imageFileName) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 12)
                                                    .fill(Theme.appSurface)
                                                    .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                                                Image(uiImage: img)
                                                    .resizable()
                                                    .scaledToFit()
                                                    .frame(height: 60)
                                                    .padding(12)
                                            }
                                            .frame(width: 110, height: 80)
                                            .contextMenu {
                                                Button(role: .destructive) {
                                                    storage.deleteSignature(sig)
                                                } label: {
                                                    Label("Delete", systemImage: "trash")
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                    }
                    
                    // Documents from Library available to sign
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Library Documents to Sign")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Theme.appTextPrimary)
                            .padding(.horizontal, 20)
                        
                        ForEach(storage.books) { book in
                            Button(action: { selectedBookToSign = book }) {
                                HStack(spacing: 14) {
                                    Image(systemName: "doc.text.fill")
                                        .font(.system(size: 24))
                                        .foregroundColor(Theme.brandPurple)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(book.title)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(Theme.appTextPrimary)
                                            .lineLimit(1)
                                        Text("\(book.pageCount) Pages")
                                            .font(.system(size: 12))
                                            .foregroundColor(Theme.appTextSecondary)
                                    }
                                    Spacer()
                                    Image(systemName: "pencil.and.scribble")
                                        .foregroundColor(Theme.brandPurple)
                                }
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Theme.appSurface)
                                        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
                                )
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle("Fill & Sign")
            .navigationBarTitleDisplayMode(.inline)
            .background(Theme.appBackground.ignoresSafeArea())
            .sheet(isPresented: $showingSignaturePad) {
                SignaturePadView(kind: signaturePadKind) { image in
                    _ = storage.saveSignatureImage(image, kind: signaturePadKind)
                }
            }
            .sheet(item: $selectedBookToSign) { book in
                SignEditorView(book: book)
            }
            .sheet(isPresented: $showingDocPicker) {
                DocumentPickerView { url in
                    if let newBook = storage.importPDF(from: url) {
                        selectedBookToSign = newBook
                    }
                }
            }
        }
    }
}
