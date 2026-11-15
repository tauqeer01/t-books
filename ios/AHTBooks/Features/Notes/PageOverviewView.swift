import SwiftUI
import PencilKit

public struct PageOverviewView: View {
    @Binding public var note: Note
    @Binding public var currentPageIndex: Int
    public var loadDrawing: (Int) -> PKDrawing
    public var onSelectPage: (Int) -> Void
    public var onAddPage: () -> Void
    public var onDeletePage: (Int) -> Void
    public var onMovePage: (Int, Int) -> Void
    @Environment(\.dismiss) private var dismiss
    
    @State private var pageToDelete: Int? = nil
    @State private var showingDeleteAlert: Bool = false
    
    private let columns = [
        GridItem(.adaptive(minimum: 140, maximum: 180), spacing: 16)
    ]
    
    public init(
        note: Binding<Note>,
        currentPageIndex: Binding<Int>,
        loadDrawing: @escaping (Int) -> PKDrawing,
        onSelectPage: @escaping (Int) -> Void,
        onAddPage: @escaping () -> Void,
        onDeletePage: @escaping (Int) -> Void,
        onMovePage: @escaping (Int, Int) -> Void
    ) {
        self._note = note
        self._currentPageIndex = currentPageIndex
        self.loadDrawing = loadDrawing
        self.onSelectPage = onSelectPage
        self.onAddPage = onAddPage
        self.onDeletePage = onDeletePage
        self.onMovePage = onMovePage
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 20) {
                    ForEach(Array(note.pages.enumerated()), id: \.element.id) { index, page in
                        PageThumbnailCard(
                            page: page,
                            index: index,
                            isSelected: index == currentPageIndex,
                            isFirst: index == 0,
                            isLast: index == note.pages.count - 1,
                            drawing: loadDrawing(index),
                            onTap: {
                                onSelectPage(index)
                                dismiss()
                            },
                            onMoveLeft: {
                                onMovePage(index, index - 1)
                            },
                            onMoveRight: {
                                onMovePage(index, index + 1)
                            },
                            onDelete: {
                                pageToDelete = index
                                showingDeleteAlert = true
                            }
                        )
                    }
                    
                    // Add Page Card
                    Button(action: onAddPage) {
                        VStack(spacing: 12) {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(Theme.brandPurple)
                            Text("Add Page")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                        }
                        .frame(maxWidth: .infinity)
                        .aspectRatio(595.0 / 842.0, contentMode: .fit)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Theme.brandPurple.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
                                .background(Theme.brandPurpleSoft.opacity(0.3))
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(16)
            }
            .background(Color(hex: "#F8FAFC"))
            .navigationTitle("Pages (\(note.pages.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
            .alert("Delete Page", isPresented: $showingDeleteAlert) {
                Button("Delete", role: .destructive) {
                    if let idx = pageToDelete {
                        onDeletePage(idx)
                    }
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                if let idx = pageToDelete {
                    Text("Are you sure you want to delete page \(idx + 1)? This action cannot be undone.")
                }
            }
        }
    }
}

// MARK: - Page Thumbnail Card

public struct PageThumbnailCard: View {
    public let page: NotePage
    public let index: Int
    public let isSelected: Bool
    public let isFirst: Bool
    public let isLast: Bool
    public let drawing: PKDrawing
    public let onTap: () -> Void
    public let onMoveLeft: () -> Void
    public let onMoveRight: () -> Void
    public let onDelete: () -> Void
    
    @State private var thumbnailImage: UIImage? = nil
    
    public var body: some View {
        VStack(spacing: 6) {
            // Preview Image
            Button(action: onTap) {
                ZStack {
                    if let img = thumbnailImage {
                        Image(uiImage: img)
                            .resizable()
                            .aspectRatio(595.0 / 842.0, contentMode: .fit)
                    } else {
                        PageTemplateBackground(template: page.background, paperTheme: page.paperTheme)
                            .aspectRatio(595.0 / 842.0, contentMode: .fit)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .shadow(color: Color.black.opacity(0.12), radius: 5, x: 0, y: 2)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(isSelected ? Theme.brandPurple : Color.black.opacity(0.12), lineWidth: isSelected ? 2.5 : 1)
                )
            }
            .buttonStyle(.plain)
            
            // Reorder and Page Number controls
            HStack(spacing: 4) {
                Button(action: onMoveLeft) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(isFirst ? Color.gray.opacity(0.3) : Theme.brandPurple)
                }
                .disabled(isFirst)
                .frame(width: 26, height: 26)
                
                Text("\(index + 1)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(isSelected ? Theme.brandPurple : Theme.appTextPrimary)
                    .frame(minWidth: 20)
                
                Button(action: onMoveRight) {
                    Image(systemName: "arrow.right")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(isLast ? Color.gray.opacity(0.3) : Theme.brandPurple)
                }
                .disabled(isLast)
                .frame(width: 26, height: 26)
                
                Spacer()
                
                Button(action: onDelete) {
                    Image(systemName: "trash")
                        .font(.system(size: 11))
                        .foregroundColor(Color.red.opacity(0.8))
                }
                .frame(width: 26, height: 26)
            }
            .padding(.horizontal, 4)
        }
        .onAppear {
            generateThumbnail()
        }
    }
    
    private func generateThumbnail() {
        DispatchQueue.global(qos: .userInitiated).async {
            let img = NoteExporter.renderPageImage(
                page: page,
                drawing: drawing,
                size: CGSize(width: 140, height: 198),
                scale: 1.5
            )
            DispatchQueue.main.async {
                self.thumbnailImage = img
            }
        }
    }
}
