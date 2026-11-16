import SwiftUI
import PencilKit
import PhotosUI

/// The note editor: a zoomable stack of pages with PencilKit ink, text, sticky notes, images, study tape,
/// shapes, lasso editing and a laser pointer (feature parity with the Android app).
public struct NoteEditorView: View {
    @Binding public var note: Note
    @StateObject private var model: NoteEditorModel
    @ObservedObject private var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showingPenEditor = false
    @State private var showingPageOverview = false
    @State private var templateTarget: TemplateTarget?
    @State private var selectedTemplate: PageBackground = .RULED
    @State private var selectedTheme: PaperTheme = .white
    @State private var showingPhotoPicker = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showingCamera = false
    @State private var shareItem: NoteShareItem?
    @State private var sourceBook: Book?
    @State private var confirmDeletePage = false
    @AppStorage(NoteToolbarPosition.storageKey) private var toolbarPositionRaw = NoteToolbarPosition.top.rawValue

    private var toolbarPosition: NoteToolbarPosition { NoteToolbarPosition(rawValue: toolbarPositionRaw) ?? .top }

    private let isUITesting = ProcessInfo.processInfo.arguments.contains("-UITests")

    private enum TemplateTarget: Identifiable {
        case newPage, thisPage, allPages
        var id: Int { hashValue }
    }

    public init(note: Binding<Note>) {
        self._note = note
        let binding = note
        self._model = StateObject(wrappedValue: NoteEditorModel(note: note.wrappedValue) { saved in
            binding.wrappedValue = saved
        })
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if toolbarPosition == .top {
                    toolBar(compact: true)
                    Divider()
                    contextBar
                    Divider()
                }
                ZStack(alignment: .topTrailing) {
                    NoteCanvasRepresentable(model: model)
                        .background { NoteCanvasRepresentable.deskColor }
                        .ignoresSafeArea(edges: .horizontal)
                        // With the tools on top, the pages run down behind the home indicator
                        .ignoresSafeArea(.container, edges: toolbarPosition == .top ? .bottom : [])
                    pageIndicator
                    if let message = model.message {
                        VStack {
                            Spacer()
                            Text(message)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16).padding(.vertical, 9)
                                .background(Capsule().fill(Color.black.opacity(0.78)))
                                .padding(.bottom, 14)
                                .accessibilityIdentifier("notes.toast")
                        }
                        .frame(maxWidth: .infinity)
                        .transition(.opacity)
                        .task(id: message) {
                            try? await Task.sleep(nanoseconds: 1_800_000_000)
                            if model.message == message { withAnimation { model.message = nil } }
                        }
                    }
                    if isUITesting { testProbe }
                }
                if toolbarPosition == .bottom {
                    contextBar
                    Divider()
                    toolBar(compact: false)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { editorToolbar }
            .sheet(isPresented: $showingPenEditor) { PenEditorSheet(model: model) }
            .sheet(isPresented: $showingPageOverview) {
                PageOverviewView(
                    note: Binding(get: { model.note }, set: { _ in }),
                    currentPageIndex: Binding(get: { model.currentPage }, set: { model.currentPage = $0 }),
                    loadDrawing: { model.pageInk($0) },
                    onSelectPage: { model.goToPage($0) },
                    onAddPage: { model.addPage(after: model.pageCount - 1) },
                    onDeletePage: { model.deletePage($0) },
                    onMovePage: { model.movePage(from: $0, to: $1) }
                )
            }
            .sheet(item: $templateTarget) { target in
                PageTemplatePicker(selectedTemplate: $selectedTemplate, selectedTheme: $selectedTheme) { template, theme in
                    switch target {
                    case .newPage: model.addPage(after: model.currentPage, background: template, theme: theme)
                    case .thisPage: model.setBackground(template, theme: theme, page: model.currentPage, allPages: false)
                    case .allPages: model.setBackground(template, theme: theme, page: model.currentPage, allPages: true)
                    }
                }
            }
            .photosPicker(isPresented: $showingPhotoPicker, selection: $photoItem, matching: .images)
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                        model.addImage(image, page: model.currentPage)
                    }
                    photoItem = nil
                }
            }
            .fullScreenCover(isPresented: $showingCamera) {
                CameraPicker { model.addImage($0, page: model.currentPage) }.ignoresSafeArea()
            }
            .sheet(item: $shareItem) { item in ShareSheet(items: [item.url]) }
            .fullScreenCover(item: $sourceBook) { book in
                NavigationStack { PDFReaderView(book: book) }
            }
            .confirmationDialog("Delete page \(model.currentPage + 1)?", isPresented: $confirmDeletePage, titleVisibility: .visible) {
                Button("Delete page", role: .destructive) { model.deletePage(model.currentPage) }
            } message: {
                Text("You can undo this.")
            }
            .animation(.easeInOut(duration: 0.2), value: model.message)
        }
        .onDisappear { model.saveNow() }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var editorToolbar: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button("Done") {
                model.finishEditing()
                model.saveNow()
                dismiss()
            }
            .font(.system(size: 15, weight: .bold))
            .foregroundColor(Theme.brandPurple)
            .accessibilityIdentifier("notes.done")
        }
        ToolbarItem(placement: .principal) {
            TextField("Untitled Note", text: Binding(get: { model.note.title }, set: { model.rename($0) }))
                .font(.system(size: 16, weight: .bold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: 200)
                .submitLabel(.done)
                .accessibilityIdentifier("notes.title")
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            Button(action: model.undo) { Image(systemName: "arrow.uturn.backward") }
                .disabled(!model.canUndo)
                .accessibilityLabel("Undo")
                .accessibilityIdentifier("notes.undo")
            Button(action: model.redo) { Image(systemName: "arrow.uturn.forward") }
                .disabled(!model.canRedo)
                .accessibilityLabel("Redo")
                .accessibilityIdentifier("notes.redo")
            Button { showingPageOverview = true } label: { Image(systemName: "square.grid.2x2") }
                .accessibilityLabel("Pages")
                .accessibilityIdentifier("notes.pages")
            Menu {
                Section("Page \(model.currentPage + 1)") {
                    Button { beginTemplatePick(.newPage) } label: { Label("Add page…", systemImage: "doc.badge.plus") }
                    Button { beginTemplatePick(.thisPage) } label: { Label("Page template…", systemImage: "square.grid.3x3") }
                    Button { beginTemplatePick(.allPages) } label: { Label("Template for all pages…", systemImage: "square.grid.3x3.fill") }
                    Button { model.duplicatePage(model.currentPage) } label: { Label("Duplicate page", systemImage: "plus.square.on.square") }
                    Button(role: .destructive) { confirmDeletePage = true } label: { Label("Delete page", systemImage: "trash") }
                        .disabled(model.pageCount < 2)
                }
                Section("Study tape") {
                    Button { model.revealAllTapes(true) } label: { Label("Show all tape", systemImage: "eye") }
                    Button { model.revealAllTapes(false) } label: { Label("Hide all tape", systemImage: "eye.slash") }
                }
                Section {
                    Picker(selection: $toolbarPositionRaw) {
                        ForEach(NoteToolbarPosition.allCases) { position in
                            Label(position.title, systemImage: position.icon).tag(position.rawValue)
                        }
                    } label: {
                        Label("Toolbar position", systemImage: toolbarPosition.icon)
                    }
                    .pickerStyle(.menu)
                }
                Section("Apple Pencil") {
                    Toggle(isOn: Binding(get: { model.fingerDrawing }, set: { model.setFingerDrawing($0) })) {
                        Label("Draw with finger", systemImage: "hand.draw")
                    }
                }
                Section("Export") {
                    Button { export(pdf: true) } label: { Label("Share as PDF", systemImage: "doc.richtext") }
                    Button { export(pdf: false) } label: { Label("Share page as image", systemImage: "photo") }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
            .accessibilityLabel("More")
            .accessibilityIdentifier("notes.more")
        }
    }

    private var contextBar: some View {
        NoteContextBar(model: model,
                       onEditPen: { showingPenEditor = true },
                       onFormat: { model.formatHandler?($0) },
                       onOpenSource: openSource)
    }

    private func toolBar(compact: Bool) -> some View {
        NoteToolBar(model: model,
                    compact: compact,
                    onSticky: { model.addStickyNote(page: model.currentPage) },
                    onPhoto: { showingPhotoPicker = true },
                    onCamera: { showingCamera = true },
                    onAddPage: { beginTemplatePick(.newPage) })
    }

    private var pageIndicator: some View {
        Text("\(model.currentPage + 1) / \(model.pageCount)")
            .font(.system(size: 12, weight: .bold, design: .rounded))
            .foregroundColor(Theme.appTextSecondary)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(Capsule().fill(.ultraThinMaterial))
            .padding(10)
            .allowsHitTesting(false)
            .accessibilityIdentifier("notes.pageIndicator")
    }

    // MARK: - Actions

    private func beginTemplatePick(_ target: TemplateTarget) {
        let page = model.note.pages[min(model.currentPage, model.pageCount - 1)]
        selectedTemplate = page.background
        selectedTheme = page.paperTheme
        templateTarget = target
    }

    private func export(pdf: Bool) {
        model.finishEditing()
        model.saveNow()
        do {
            let url: URL
            if pdf {
                url = try NoteExporter.exportPdf(note: model.note) { model.pageInk($0) }
            } else {
                url = try NoteExporter.exportPng(note: model.note, pageIndex: model.currentPage, drawing: model.pageInk(model.currentPage))
            }
            shareItem = NoteShareItem(url: url)
        } catch {
            model.message = "Couldn't export: \(error.localizedDescription)"
        }
    }

    private func openSource(_ element: NoteElement) {
        guard let id = element.sourceBookId, var book = storage.books.first(where: { $0.id == id }) else {
            model.message = "The source book is no longer in your library"
            return
        }
        if let page = element.sourcePage { book.currentPage = page }
        sourceBook = book
    }

    private var testProbe: some View {
        let model = self.model
        return VStack {
            HStack {
                NoteTestProbe { model.testStateDescription() }.frame(width: 2, height: 2)
                Spacer()
            }
            Spacer()
        }
        .allowsHitTesting(false)
    }
}

struct NoteShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

/// Exposes editor state to UI tests through an accessibility value computed at query time.
private struct NoteTestProbe: UIViewRepresentable {
    let describe: () -> String
    func makeUIView(context: Context) -> ProbeView {
        let v = ProbeView()
        v.describe = describe
        return v
    }
    func updateUIView(_ v: ProbeView, context: Context) { v.describe = describe }

    final class ProbeView: UIView {
        var describe: () -> String = { "" }
        override var isAccessibilityElement: Bool { get { true } set {} }
        override var accessibilityIdentifier: String? { get { "notes.state" } set {} }
        override var accessibilityValue: String? { get { describe() } set {} }
        override var accessibilityLabel: String? { get { describe() } set {} }
    }
}
