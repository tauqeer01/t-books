import SwiftUI

public struct CollectionsScreen: View {
    @ObservedObject var storage = StorageManager.shared
    @Binding var activeBook: Book?
    @Environment(\.dismiss) private var dismiss
    @State private var showingCreateAlert = false
    @State private var newCollectionName = ""
    @State private var newCollectionDesc = ""
    @State private var selectedCollectionId: String? = nil
    @State private var collectionToDelete: BookCollection? = nil
    @State private var addingBooksTo: BookCollection? = nil

    public init(activeBook: Binding<Book?>) {
        self._activeBook = activeBook
    }

    private var selectedCollection: BookCollection? {
        selectedCollectionId.flatMap { id in storage.collections.first { $0.id == id } }
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let col = selectedCollection {
                        collectionDetail(col)
                    } else if storage.collections.isEmpty {
                        emptyState(
                            icon: "square.stack.3d.up",
                            title: "No collections yet",
                            message: "Tap + to create a collection, then add books to it."
                        )
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                            ForEach(storage.collections) { col in
                                PastelCollectionCard(collection: col, bookCount: storage.bookCount(in: col)) {
                                    selectedCollectionId = col.id
                                }
                                .contextMenu {
                                    Button {
                                        addingBooksTo = col
                                    } label: {
                                        Label("Add Books", systemImage: "plus")
                                    }
                                    Button(role: .destructive) {
                                        collectionToDelete = col
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
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if let col = selectedCollection {
                        Button(action: { addingBooksTo = col }) {
                            Label("Add Books", systemImage: "plus")
                        }
                        .accessibilityLabel("Add books to \(col.name)")
                    } else {
                        Button(action: { showingCreateAlert = true }) {
                            Image(systemName: "plus")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                        }
                        .accessibilityLabel("New collection")
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
                    let name = newCollectionName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty {
                        storage.addCollection(name: name, description: newCollectionDesc.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    newCollectionName = ""
                    newCollectionDesc = ""
                }
            } message: {
                Text("Create a custom shelf to organize your PDFs.")
            }
            .alert("Delete Collection?", isPresented: Binding(
                get: { collectionToDelete != nil },
                set: { if !$0 { collectionToDelete = nil } }
            )) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    if let col = collectionToDelete {
                        if selectedCollectionId == col.id { selectedCollectionId = nil }
                        storage.deleteCollection(id: col.id)
                    }
                }
            } message: {
                Text("“\(collectionToDelete?.name ?? "")” will be removed. Its books stay in your library.")
            }
            .sheet(item: $addingBooksTo) { col in
                CollectionBooksPicker(collection: col)
            }
            .background(Theme.appBackground.ignoresSafeArea())
        }
    }

    @ViewBuilder
    private func collectionDetail(_ col: BookCollection) -> some View {
        Button(action: { selectedCollectionId = nil }) {
            HStack(spacing: 6) {
                Image(systemName: "chevron.left")
                Text("All Collections")
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Theme.brandPurple)
        }
        .padding(.horizontal, 20)

        let booksInCol = storage.books.filter { $0.collectionIds.contains(col.id) }
        VStack(alignment: .leading, spacing: 6) {
            Text(col.name)
                .font(.system(size: 26, weight: .bold))
                .foregroundColor(Theme.appTextPrimary)
            Text(col.description.isEmpty ? "\(booksInCol.count) \(booksInCol.count == 1 ? "document" : "documents")" : col.description)
                .font(.system(size: 14))
                .foregroundColor(Theme.appTextSecondary)
        }
        .padding(.horizontal, 20)

        if booksInCol.isEmpty {
            emptyState(icon: "folder", title: "This collection is empty", message: "Add books from your library.")
            Button(action: { addingBooksTo = col }) {
                Text("Add Books")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Theme.brandPurple))
            }
            .frame(maxWidth: .infinity)
        } else {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)], spacing: 18) {
                ForEach(booksInCol) { book in
                    BookCoverCard(book: book, onOpen: { activeBook = book })
                        .contextMenu {
                            Button(role: .destructive) {
                                storage.setCollections(bookId: book.id, collectionIds: book.collectionIds.filter { $0 != col.id })
                            } label: {
                                Label("Remove from Collection", systemImage: "minus.circle")
                            }
                        }
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private func emptyState(icon: String, title: String, message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 42))
                .foregroundColor(Theme.appTextSecondary.opacity(0.6))
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.appTextPrimary)
            Text(message)
                .font(.system(size: 13))
                .foregroundColor(Theme.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 40)
        .padding(.horizontal, 40)
    }
}

/// Checklist of library books for one collection.
private struct CollectionBooksPicker: View {
    @ObservedObject var storage = StorageManager.shared
    let collection: BookCollection
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(storage.books) { book in
                let inCollection = book.collectionIds.contains(collection.id)
                Button {
                    let ids = inCollection ? book.collectionIds.filter { $0 != collection.id } : book.collectionIds + [collection.id]
                    storage.setCollections(bookId: book.id, collectionIds: ids)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(book.title)
                                .foregroundColor(Theme.appTextPrimary)
                                .lineLimit(1)
                            Text(book.author)
                                .font(.system(size: 12))
                                .foregroundColor(Theme.appTextSecondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: inCollection ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(inCollection ? Theme.brandPurple : Theme.appTextSecondary)
                    }
                }
                .accessibilityAddTraits(inCollection ? .isSelected : [])
            }
            .overlay {
                if storage.books.isEmpty {
                    Text("Your library is empty. Import a PDF first.")
                        .foregroundColor(Theme.appTextSecondary)
                }
            }
            .navigationTitle("Add to \(collection.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }
}

/// Checklist of collections for one book (from the Library and Book Details).
public struct BookCollectionsSheet: View {
    @ObservedObject var storage = StorageManager.shared
    let bookId: String
    @Environment(\.dismiss) private var dismiss
    @State private var showingCreateAlert = false
    @State private var newName = ""

    public init(bookId: String) {
        self.bookId = bookId
    }

    public var body: some View {
        let book = storage.books.first { $0.id == bookId }
        NavigationStack {
            List {
                ForEach(storage.collections) { col in
                    let selected = book?.collectionIds.contains(col.id) == true
                    Button {
                        guard let book else { return }
                        let ids = selected ? book.collectionIds.filter { $0 != col.id } : book.collectionIds + [col.id]
                        storage.setCollections(bookId: book.id, collectionIds: ids)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: col.iconName)
                                .foregroundColor(Color(hex: "#3730A3"))
                                .frame(width: 30, height: 30)
                                .background(Circle().fill(Color(hex: col.pastelColorHex)))
                            Text(col.name)
                                .foregroundColor(Theme.appTextPrimary)
                            Spacer()
                            Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(selected ? Theme.brandPurple : Theme.appTextSecondary)
                        }
                    }
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
                Button {
                    showingCreateAlert = true
                } label: {
                    Label("New Collection", systemImage: "plus")
                        .foregroundColor(Theme.brandPurple)
                }
            }
            .navigationTitle("Collections")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .alert("New Collection", isPresented: $showingCreateAlert) {
                TextField("Collection Name", text: $newName)
                Button("Cancel", role: .cancel) { newName = "" }
                Button("Create") {
                    let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty { storage.addCollection(name: name, description: "") }
                    newName = ""
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
