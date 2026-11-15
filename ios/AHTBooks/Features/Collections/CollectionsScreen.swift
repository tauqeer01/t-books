import SwiftUI

public struct CollectionsScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @Binding var activeBook: Book?
    @State private var showingCreateAlert = false
    @State private var newCollectionName = ""
    @State private var newCollectionDesc = ""
    @State private var selectedCollection: BookCollection? = nil
    
    public init(activeBook: Binding<Book?>) {
        self._activeBook = activeBook
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let col = selectedCollection {
                        // Selected Collection Header & Books
                        HStack {
                            Button(action: { selectedCollection = nil }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "chevron.left")
                                    Text("All Collections")
                                }
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text(col.name)
                                .font(.system(size: 26, weight: .bold))
                                .foregroundColor(Theme.appTextPrimary)
                            Text(col.description.isEmpty ? "\(col.bookCount) documents in this shelf" : col.description)
                                .font(.system(size: 14))
                                .foregroundColor(Theme.appTextSecondary)
                        }
                        .padding(.horizontal, 20)
                        
                        let booksInCol = storage.books.filter { $0.collectionIds.contains(col.id) }
                        if booksInCol.isEmpty {
                            VStack(spacing: 12) {
                                Spacer(minLength: 40)
                                Image(systemName: "folder")
                                    .font(.system(size: 42))
                                    .foregroundColor(Theme.appTextSecondary.opacity(0.6))
                                Text("This collection is empty")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Theme.appTextPrimary)
                                Text("Add books from your library shelf")
                                    .font(.system(size: 13))
                                    .foregroundColor(Theme.appTextSecondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 18) {
                                ForEach(booksInCol) { book in
                                    BookCoverCard(book: book, onOpen: {
                                        activeBook = book
                                    })
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    } else {
                        // All Collections Grid
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                            ForEach(storage.collections) { col in
                                PastelCollectionCard(collection: col) {
                                    selectedCollection = col
                                }
                                .contextMenu {
                                    Button(role: .destructive) {
                                        storage.collections.removeAll { $0.id == col.id }
                                        storage.saveAll()
                                    } label: {
                                        Label("Delete Collection", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                .padding(.vertical, 16)
            }
            .navigationTitle(selectedCollection == nil ? "Collections" : "")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingCreateAlert = true }) {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.brandPurple)
                    }
                }
            }
            .alert("New Collection", isPresented: $showingCreateAlert) {
                TextField("Collection Name", text: $newCollectionName)
                TextField("Description (optional)", text: $newCollectionDesc)
                Button("Cancel", role: .cancel) {
                    newCollectionName = ""
                    newCollectionDesc = ""
                }
                Button("Create") {
                    guard !newCollectionName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    let pastels = ["#EDE9FE", "#DCFCE7", "#FFE4E6", "#FEF3C7", "#D1FAE5", "#FFEDD5", "#DBEAFE"]
                    let chosen = pastels.randomElement() ?? "#EDE9FE"
                    let newCol = BookCollection(
                        name: newCollectionName.trimmingCharacters(in: .whitespaces),
                        description: newCollectionDesc.trimmingCharacters(in: .whitespaces),
                        pastelColorHex: chosen,
                        iconName: "folder.fill"
                    )
                    storage.collections.append(newCol)
                    storage.saveAll()
                    newCollectionName = ""
                    newCollectionDesc = ""
                }
            } message: {
                Text("Create a custom shelf to organize your PDFs.")
            }
            .background(Theme.appBackground.ignoresSafeArea())
        }
    }
}
