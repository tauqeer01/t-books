import SwiftUI

// MARK: - Selection actions

/// Floating actions for selected text: mark it, add a note, copy, send to a notebook or a flashcard.
struct SelectionActionBar: View {
    let selection: ReaderTextSelection
    var onHighlight: (String) -> Void
    var onUnderline: () -> Void
    var onStrikethrough: () -> Void
    var onAddNote: () -> Void
    var onCopy: () -> Void
    var onSendToNotebook: () -> Void
    var onFlashcard: () -> Void
    var onClozeCard: () -> Void
    var onClose: () -> Void

    static let highlightColors = ["#FDE047", "#86EFAC", "#7DD3FC", "#F472B6", "#C084FC"]

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                ForEach(Self.highlightColors, id: \.self) { hex in
                    Button { onHighlight(hex) } label: {
                        Circle().fill(Color(hex: hex)).frame(width: 28, height: 28)
                            .overlay(Circle().stroke(Color.black.opacity(0.08), lineWidth: 1))
                    }
                    .accessibilityLabel("Highlight \(hex)")
                    .accessibilityIdentifier("selection.highlight.\(hex)")
                }
                Divider().frame(height: 24)
                iconButton("underline", "Underline", "selection.underline", onUnderline)
                iconButton("strikethrough", "Strikethrough", "selection.strikethrough", onStrikethrough)
                Spacer(minLength: 0)
                iconButton("xmark", "Close", "selection.close", onClose)
            }
            HStack(spacing: 8) {
                textButton("Note", "text.bubble", "selection.note", onAddNote)
                textButton("Copy", "doc.on.doc", "selection.copy", onCopy)
                textButton("To note", "square.and.pencil", "selection.toNotebook", onSendToNotebook)
                textButton("Card", "rectangle.on.rectangle", "selection.card", onFlashcard)
                textButton("Cloze", "text.badge.minus", "selection.cloze", onClozeCard)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.18), radius: 18, x: 0, y: 6)
        )
        .padding(.horizontal, 16)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("selection.bar")
    }

    private func iconButton(_ icon: String, _ label: String, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.appTextPrimary)
                .frame(width: 34, height: 34)
        }
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }

    private func textButton(_ title: String, _ icon: String, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 15, weight: .semibold))
                Text(title).font(.system(size: 11, weight: .semibold))
            }
            .foregroundColor(Theme.brandPurple)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 10).fill(Theme.brandPurpleSoft.opacity(0.6)))
        }
        .accessibilityLabel(title)
        .accessibilityIdentifier(id)
    }
}

// MARK: - Annotation menu

/// Menu for an annotation tapped while reading: recolor, edit its note, copy its text, delete.
struct AnnotationMenuSheet: View {
    let annotation: BookAnnotation
    var onColor: (String) -> Void
    var onEditNote: () -> Void
    var onCopy: () -> Void
    var onDelete: () -> Void

    private var title: String {
        switch annotation.type {
        case .HIGHLIGHT: return "Highlight"
        case .UNDERLINE: return "Underline"
        case .STRIKETHROUGH: return "Strikethrough"
        case .NOTE: return "Sticky note"
        case .DRAWING: return "Drawing"
        }
    }

    private var palette: [String] {
        annotation.type == .HIGHLIGHT || annotation.type == .NOTE
            ? ReaderAnnotationController.highlighterPalette : ReaderAnnotationController.inkPalette
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(title).font(.system(size: 17, weight: .bold))
                Spacer()
                Text("Page \(annotation.pageIndex + 1)")
                    .font(.system(size: 13)).foregroundColor(Theme.appTextSecondary)
            }
            if !annotation.selectedText.isEmpty && annotation.type != .NOTE {
                Text("“\(annotation.selectedText)”")
                    .font(.system(size: 14)).foregroundColor(Theme.appTextSecondary).lineLimit(3)
            }
            if !annotation.noteContent.isEmpty {
                Text(annotation.noteContent).font(.system(size: 14)).lineLimit(4)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(palette, id: \.self) { hex in
                        Button { onColor(hex) } label: {
                            Circle().fill(Color(hex: hex)).frame(width: 30, height: 30)
                                .overlay(Circle().stroke(annotation.colorHex.lowercased() == hex.lowercased()
                                                         ? Theme.brandPurple : Color.black.opacity(0.08),
                                                         lineWidth: annotation.colorHex.lowercased() == hex.lowercased() ? 3 : 1))
                        }
                        .accessibilityIdentifier("annotationMenu.color.\(hex)")
                    }
                }
                .padding(.vertical, 2)
            }
            HStack(spacing: 10) {
                if annotation.type != .DRAWING {
                    menuButton(annotation.noteContent.isEmpty ? "Add note" : "Edit note", "text.bubble", "annotationMenu.note", onEditNote)
                }
                if !annotation.selectedText.isEmpty && annotation.type != .NOTE {
                    menuButton("Copy", "doc.on.doc", "annotationMenu.copy", onCopy)
                }
                Button(role: .destructive, action: onDelete) {
                    Label("Delete", systemImage: "trash")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.red.opacity(0.1)))
                }
                .accessibilityIdentifier("annotationMenu.delete")
            }
        }
        .padding(20)
        .presentationDetents([.height(260)])
        .presentationDragIndicator(.visible)
    }

    private func menuButton(_ title: String, _ icon: String, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.brandPurple)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12).fill(Theme.brandPurpleSoft.opacity(0.6)))
        }
        .accessibilityIdentifier(id)
    }
}

// MARK: - Note editor

/// Edits the note on a sticky note or another annotation.
struct AnnotationNoteEditor: View {
    let annotation: BookAnnotation
    var onSave: (String) -> Void
    var onCancel: () -> Void
    @State private var text: String
    @FocusState private var focused: Bool

    init(annotation: BookAnnotation, onSave: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        self.annotation = annotation
        self.onSave = onSave
        self.onCancel = onCancel
        self._text = State(initialValue: annotation.noteContent)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                if !annotation.selectedText.isEmpty && annotation.type != .NOTE {
                    Text("“\(annotation.selectedText)”")
                        .font(.system(size: 14)).foregroundColor(Theme.appTextSecondary).lineLimit(3)
                }
                TextEditor(text: $text)
                    .focused($focused)
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: annotation.colorHex).opacity(0.18)))
                    .accessibilityIdentifier("noteEditor.text")
            }
            .padding(16)
            .navigationTitle(annotation.type == .NOTE ? "Sticky note" : "Note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel).accessibilityIdentifier("noteEditor.cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(text.trimmingCharacters(in: .whitespacesAndNewlines)) }
                        .fontWeight(.bold)
                        .accessibilityIdentifier("noteEditor.save")
                }
            }
            .onAppear { focused = true }
        }
        .presentationDetents([.medium])
    }
}

// MARK: - Notebook picker

/// Picks the notebook that receives a clip or quote; `nil` means a new notebook.
struct NotebookPickerSheet: View {
    let title: String
    var onPick: (String?) -> Void
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Button {
                    onPick(nil)
                    dismiss()
                } label: {
                    Label("New notebook", systemImage: "plus.circle.fill").foregroundColor(Theme.brandPurple)
                }
                .accessibilityIdentifier("notebookPicker.new")
                Section("Notebooks") {
                    ForEach(storage.notes) { note in
                        Button {
                            onPick(note.id)
                            dismiss()
                        } label: {
                            HStack {
                                Image(systemName: "book.closed").foregroundColor(Theme.brandPurple)
                                Text(note.title).foregroundColor(Theme.appTextPrimary)
                                Spacer()
                                Text("\(note.pages.count) pages").font(.system(size: 12)).foregroundColor(Theme.appTextSecondary)
                            }
                        }
                        .accessibilityIdentifier("notebookPicker.note.\(note.title)")
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
    }
}

// MARK: - Annotations list

/// Every annotation in the book, by page (Android's Annotations drawer tab).
struct AnnotationsListSheet: View {
    @ObservedObject var controller: ReaderAnnotationController
    @ObservedObject var storage = StorageManager.shared
    var onJump: (Int) -> Void
    @Environment(\.dismiss) private var dismiss

    private var items: [BookAnnotation] {
        storage.annotations.filter { $0.bookId == controller.bookId }
            .sorted { ($0.pageIndex, $0.createdAt) < ($1.pageIndex, $1.createdAt) }
    }

    var body: some View {
        NavigationStack {
            List {
                if items.isEmpty {
                    Text("No annotations yet. Tap the pen to highlight, draw or add notes.")
                        .foregroundColor(Theme.appTextSecondary)
                }
                ForEach(items) { ann in
                    Button {
                        onJump(ann.pageIndex)
                        dismiss()
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: icon(ann.type))
                                .foregroundColor(Color(hex: ann.colorHex))
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(summary(ann)).font(.system(size: 14)).foregroundColor(Theme.appTextPrimary).lineLimit(2)
                                if !ann.noteContent.isEmpty && ann.type != .NOTE {
                                    Text(ann.noteContent).font(.system(size: 12)).foregroundColor(Theme.appTextSecondary).lineLimit(2)
                                }
                            }
                            Spacer()
                            Text("p. \(ann.pageIndex + 1)").font(.system(size: 12)).foregroundColor(Theme.brandPurple)
                        }
                    }
                }
                .onDelete { offsets in
                    offsets.map { items[$0] }.forEach(controller.delete)
                }
            }
            .navigationTitle("Annotations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    private func icon(_ type: AnnotationType) -> String {
        switch type {
        case .HIGHLIGHT: return "highlighter"
        case .UNDERLINE: return "underline"
        case .STRIKETHROUGH: return "strikethrough"
        case .NOTE: return "note.text"
        case .DRAWING: return "scribble"
        }
    }

    private func summary(_ ann: BookAnnotation) -> String {
        switch ann.type {
        case .NOTE: return ann.noteContent.isEmpty ? "Sticky note" : ann.noteContent
        case .DRAWING: return "Drawing"
        default: return ann.selectedText.isEmpty ? "Marked text" : ann.selectedText
        }
    }
}

// MARK: - Toast

struct ReaderToast: View {
    let message: String
    var body: some View {
        Text(message)
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Capsule().fill(Color.black.opacity(0.78)))
            .accessibilityIdentifier("reader.toast")
    }
}
