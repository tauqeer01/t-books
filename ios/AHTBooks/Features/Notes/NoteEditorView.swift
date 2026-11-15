import SwiftUI
import PencilKit
import PhotosUI

public struct NoteEditorView: View {
    @Binding public var note: Note
    @ObservedObject var storage = StorageManager.shared
    @Environment(\.dismiss) private var dismiss
    
    // Page state
    @State private var currentPageIndex: Int = 0
    @State private var currentDrawing: PKDrawing = PKDrawing()
    @State private var isFingerDrawing: Bool = true
    @State private var isRulerActive: Bool = false
    
    // Liquid Tool Dial State
    // 0 = Floating Liquid FAB at bottom-right
    // 1 = Spinning Liquid Radial Tool Dial (centered on right edge)
    // 2 = Collapsed Liquid Tool Rail on right edge
    @State private var dialRailMode: Int = 0
    @State private var isDialOpen: Bool = false
    @State private var selectedDialTool: DialToolItem = .pen(name: "Fine Pen", width: 1.5, isPencil: false)
    @State private var activeColorHex: String = "#5935FF"
    @State private var showingColorFlyout: Bool = false
    
    // Sheets & modals
    @State private var showingPageOverview: Bool = false
    @State private var showingTemplatePicker: Bool = false
    @State private var templatePickerMode: TemplatePickerMode = .changeCurrent
    @State private var showingAddElementSheet: Bool = false
    @State private var showingPhotoPicker: Bool = false
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var showingShareSheet: Bool = false
    @State private var shareURL: URL? = nil
    @State private var alertMessage: String? = nil
    @State private var showingAlert: Bool = false
    
    // Canvas interaction
    @State private var canvasProxy = CanvasProxy()
    @State private var selectedElementId: String? = nil
    @State private var revealedTapes: Set<String> = []
    
    // Temporary template selection
    @State private var selectedTemplate: PageBackground = .BLANK
    @State private var selectedTheme: PaperTheme = .white
    
    public enum TemplatePickerMode {
        case changeCurrent
        case addNewPage
    }
    
    public init(note: Binding<Note>) {
        self._note = note
    }
    
    private var currentPage: NotePage {
        if currentPageIndex < note.pages.count {
            return note.pages[currentPageIndex]
        }
        return NotePage(noteId: note.id)
    }
    
    // Full Premium Suite of Tools for Note Taking
    private var noteDialItems: [DialToolItem] {
        [
            .hand,
            .pen(name: "Fine Pen", width: 1.5, isPencil: false),
            .pen(name: "Medium Pen", width: 3.0, isPencil: false),
            .pen(name: "Fountain Pen", width: 5.0, isPencil: false),
            .pen(name: "Pencil", width: 2.0, isPencil: true),
            .highlighter,
            .eraser,
            .lasso,
            .ruler,
            .line,
            .arrow,
            .rectangle,
            .ellipse,
            .stickyNote,
            .tape,
            .textBox
        ]
    }
    
    public var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                // Background Desk and Structure
                VStack(spacing: 0) {
                    // Top Page Controls & Navigation Bar
                    topControlBar
                    
                    // Canvas Desk Area
                    deskCanvasView
                    
                    // Bottom Bar: Quick Action Pills
                    bottomActionBar
                }
                
                // Liquid Native Tool Dial Floating Overlay
                toolDialOverlay
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    TextField("Untitled Note", text: $note.title)
                        .font(.system(size: 16, weight: .bold))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 240)
                        .textFieldStyle(.plain)
                        .submitLabel(.done)
                        .onSubmit {
                            persistNote()
                        }
                }
                
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") {
                        saveCurrentDrawing()
                        persistNote()
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 8) {
                        // Undo
                        Button(action: {
                            canvasProxy.undo()
                            saveCurrentDrawing()
                        }) {
                            Image(systemName: "arrow.uturn.backward")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                        }
                        
                        // Redo
                        Button(action: {
                            canvasProxy.redo()
                            saveCurrentDrawing()
                        }) {
                            Image(systemName: "arrow.uturn.forward")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                        }
                        
                        // All Pages Grid Overview
                        Button(action: {
                            saveCurrentDrawing()
                            showingPageOverview = true
                        }) {
                            Image(systemName: "square.grid.2x2")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                        }
                        
                        // More Menu
                        Menu {
                            pagesMenuSection
                            stylusMenuSection
                            exportMenuSection
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(Theme.brandPurple)
                        }
                    }
                }
            }
            .onAppear {
                if note.pages.isEmpty {
                    note.pages = [NotePage(noteId: note.id)]
                }
                loadCurrentDrawing()
            }
            // Page Overview Sheet
            .sheet(isPresented: $showingPageOverview) {
                PageOverviewView(
                    note: $note,
                    currentPageIndex: $currentPageIndex,
                    loadDrawing: { idx in
                        let fileName = "note_\(note.id)_p\(idx).drawing"
                        return storage.loadDrawing(fileName: fileName)
                    },
                    onSelectPage: { idx in
                        saveCurrentDrawing()
                        currentPageIndex = idx
                        loadCurrentDrawing()
                    },
                    onAddPage: {
                        addNewPage(template: .BLANK, theme: .white)
                    },
                    onDeletePage: { idx in
                        deletePage(at: idx)
                    },
                    onMovePage: { from, to in
                        movePage(from: from, to: to)
                    }
                )
            }
            // Template Picker Sheet
            .sheet(isPresented: $showingTemplatePicker) {
                PageTemplatePicker(
                    selectedTemplate: $selectedTemplate,
                    selectedTheme: $selectedTheme
                ) { template, theme in
                    if templatePickerMode == .addNewPage {
                        addNewPage(template: template, theme: theme)
                    } else {
                        if currentPageIndex < note.pages.count {
                            note.pages[currentPageIndex].background = template
                            note.pages[currentPageIndex].paperTheme = theme
                            persistNote()
                        }
                    }
                }
            }
            // Add Element Sheet
            .sheet(isPresented: $showingAddElementSheet) {
                AddElementSheet { text, kind, colorHex in
                    insertElement(content: text, kind: kind, fillColor: colorHex)
                }
            }
            // Photo Picker
            .photosPicker(isPresented: $showingPhotoPicker, selection: $selectedPhotoItem, matching: .images)
            .onChange(of: selectedPhotoItem) { _, newItem in
                Task {
                    if let data = try? await newItem?.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: data) {
                        insertImageElement(uiImage)
                    }
                }
            }
            // Share Sheet
            .sheet(isPresented: $showingShareSheet) {
                if let url = shareURL {
                    iPadShareSheet(items: [url])
                }
            }
            // Alert message
            .alert("Notes", isPresented: $showingAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(alertMessage ?? "")
            }
        }
    }
    
    // MARK: - Menu Sections
    
    @ViewBuilder
    private var pagesMenuSection: some View {
        Section("Pages") {
            Button {
                templatePickerMode = .addNewPage
                selectedTemplate = currentPage.background
                selectedTheme = currentPage.paperTheme
                showingTemplatePicker = true
            } label: {
                Label("Add Page…", systemImage: "doc.badge.plus")
            }
            
            Button {
                templatePickerMode = .changeCurrent
                selectedTemplate = currentPage.background
                selectedTheme = currentPage.paperTheme
                showingTemplatePicker = true
            } label: {
                Label("Change Page Template…", systemImage: "slider.horizontal.3")
            }
            
            Button {
                duplicateCurrentPage()
            } label: {
                Label("Duplicate Page", systemImage: "plus.square.on.square")
            }
            
            if note.pages.count > 1 {
                Button(role: .destructive) {
                    deleteCurrentPage()
                } label: {
                    Label("Delete Page \(currentPageIndex + 1)", systemImage: "trash")
                }
            }
        }
    }
    
    @ViewBuilder
    private var stylusMenuSection: some View {
        Section("Stylus & Apple Pencil") {
            Toggle(isOn: $isFingerDrawing) {
                Label("Draw with Finger", systemImage: "hand.draw")
            }
            
            Button {
                isRulerActive.toggle()
            } label: {
                Label(isRulerActive ? "Hide Ruler" : "Show Ruler", systemImage: "ruler")
            }
            
            Button {
                withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                    dialRailMode = 1
                    isDialOpen = true
                }
            } label: {
                Label("Open Tools Dial", systemImage: "circle.grid.cross")
            }
        }
    }
    
    @ViewBuilder
    private var exportMenuSection: some View {
        Section("Export & Share") {
            Button {
                exportAsPdf()
            } label: {
                Label("Share as PDF", systemImage: "doc.richtext")
            }
            
            Button {
                exportAsImage()
            } label: {
                Label("Share as Image (PNG)", systemImage: "photo")
            }
        }
    }
    
    // MARK: - Subviews
    
    private var topControlBar: some View {
        HStack(spacing: 12) {
            // Previous Page
            Button(action: {
                if currentPageIndex > 0 {
                    saveCurrentDrawing()
                    currentPageIndex -= 1
                    loadCurrentDrawing()
                }
            }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(currentPageIndex > 0 ? Theme.brandPurple : Color.gray.opacity(0.3))
                    .padding(6)
            }
            .disabled(currentPageIndex == 0)
            
            Text("Page \(currentPageIndex + 1) of \(max(1, note.pages.count))")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Theme.appTextPrimary)
            
            // Next Page
            Button(action: {
                if currentPageIndex < note.pages.count - 1 {
                    saveCurrentDrawing()
                    currentPageIndex += 1
                    loadCurrentDrawing()
                }
            }) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(currentPageIndex < note.pages.count - 1 ? Theme.brandPurple : Color.gray.opacity(0.3))
                    .padding(6)
            }
            .disabled(currentPageIndex >= note.pages.count - 1)
            
            Spacer()
            
            // Template label pill
            Button(action: {
                templatePickerMode = .changeCurrent
                selectedTemplate = currentPage.background
                selectedTheme = currentPage.paperTheme
                showingTemplatePicker = true
            }) {
                HStack(spacing: 5) {
                    Image(systemName: "square.grid.3x3")
                        .font(.system(size: 11))
                    Text(currentPage.background.title)
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundColor(Theme.brandPurple)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Capsule().fill(Theme.brandPurpleSoft))
            }
            
            // Stylus mode indicator pill
            HStack(spacing: 4) {
                Image(systemName: isFingerDrawing ? "hand.draw" : "applepencil")
                    .font(.system(size: 11))
                Text(isFingerDrawing ? "Finger + Pen" : "Stylus Only")
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundColor(isFingerDrawing ? Theme.appTextSecondary : Theme.brandPurple)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Capsule().fill(isFingerDrawing ? Color(hex: "#F1F5F9") : Theme.brandPurpleSoft))
            .onTapGesture {
                isFingerDrawing.toggle()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(Theme.appSurface)
        .overlay(Divider(), alignment: .bottom)
    }
    
    private var deskCanvasView: some View {
        GeometryReader { geo in
            let deskWidth = geo.size.width
            let deskHeight = geo.size.height
            
            // A4 aspect ratio 595.2 x 841.8
            let a4Ratio: CGFloat = NoteExporter.a4Width / NoteExporter.a4Height
            let availRatio = deskWidth / deskHeight
            
            let paperWidth: CGFloat = {
                if availRatio > a4Ratio {
                    // Taller or iPad landscape: constrained by height with comfortable margins
                    return min(deskHeight * 0.95 * a4Ratio, deskWidth - 32)
                } else {
                    // iPhone or iPad portrait: constrained by width
                    return deskWidth - 24
                }
            }()
            let paperHeight = paperWidth / a4Ratio
            let scale = paperWidth / NoteExporter.a4Width
            
            ScrollView([.horizontal, .vertical], showsIndicators: false) {
                ZStack {
                    // Paper Sheet
                    ZStack {
                        // 1. Paper Template Background
                        PageTemplateBackground(
                            template: currentPage.background,
                            paperTheme: currentPage.paperTheme
                        )
                        
                        // 2. Elements Layer (Sticky Notes, Text, Quotes, Images)
                        ForEach(currentPage.elements) { el in
                            NoteElementCanvasItem(
                                element: el,
                                scale: scale,
                                isSelected: selectedElementId == el.id,
                                isTapeRevealed: revealedTapes.contains(el.id),
                                onSelect: {
                                    if el.kind == .TAPE {
                                        if revealedTapes.contains(el.id) {
                                            revealedTapes.remove(el.id)
                                        } else {
                                            revealedTapes.insert(el.id)
                                        }
                                    } else {
                                        selectedElementId = (selectedElementId == el.id ? nil : el.id)
                                    }
                                },
                                onDelete: {
                                    deleteElement(el.id)
                                },
                                onPositionChanged: { newX, newY in
                                    updateElementPosition(el.id, x: newX, y: newY)
                                }
                            )
                        }
                        
                        // 3. Native PencilKit Canvas Layer (Bound to Tool Dial active tool)
                        PencilKitCanvasRepresentable(
                            drawing: $currentDrawing,
                            selectedTool: selectedDialTool,
                            activeColorHex: activeColorHex,
                            isFingerDrawing: isFingerDrawing,
                            isRulerActive: isRulerActive,
                            canvasProxy: canvasProxy,
                            onDrawingChanged: {
                                saveCurrentDrawing()
                            }
                        )
                    }
                    .frame(width: paperWidth, height: paperHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    .shadow(color: Color.black.opacity(0.14), radius: 10, x: 0, y: 4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Color.black.opacity(0.08), lineWidth: 1)
                    )
                }
                .frame(maxWidth: .infinity, minHeight: deskHeight)
                .padding(.vertical, 16)
            }
            .background(Color(hex: "#F1F5F9"))
        }
    }
    
    private var bottomActionBar: some View {
        HStack(spacing: 12) {
            // Tools Dial Launcher Pill
            Button(action: {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                    dialRailMode = 1
                    isDialOpen = true
                }
            }) {
                HStack(spacing: 5) {
                    Image(systemName: selectedDialTool.iconName)
                        .font(.system(size: 13, weight: .bold))
                    Text(selectedDialTool.label)
                        .font(.system(size: 11, weight: .bold))
                        .lineLimit(1)
                }
                .foregroundColor(Theme.brandPurple)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(Theme.brandPurpleSoft))
            }
            
            // Insert Sticky Note
            Button(action: {
                insertElement(content: "Note text…", kind: .STICKY, fillColor: activeColorHex)
            }) {
                VStack(spacing: 2) {
                    Image(systemName: "note.text")
                        .font(.system(size: 15, weight: .semibold))
                    Text("Sticky")
                        .font(.system(size: 10, weight: .medium))
                }
                .foregroundColor(Theme.brandPurple)
            }
            
            // Insert Text Box
            Button(action: {
                showingAddElementSheet = true
            }) {
                VStack(spacing: 2) {
                    Image(systemName: "character.textbox")
                        .font(.system(size: 15, weight: .semibold))
                    Text("Text")
                        .font(.system(size: 10, weight: .medium))
                }
                .foregroundColor(Theme.brandPurple)
            }
            
            // Insert Photo / Image
            Button(action: {
                showingPhotoPicker = true
            }) {
                VStack(spacing: 2) {
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 15, weight: .semibold))
                    Text("Image")
                        .font(.system(size: 10, weight: .medium))
                }
                .foregroundColor(Theme.brandPurple)
            }
            
            // Insert Study Tape
            Button(action: {
                insertTapeElement()
            }) {
                VStack(spacing: 2) {
                    Image(systemName: "bandage")
                        .font(.system(size: 15, weight: .semibold))
                    Text("Tape")
                        .font(.system(size: 10, weight: .medium))
                }
                .foregroundColor(Theme.brandPurple)
            }
            
            Spacer()
            
            // Add Page Pill
            Button(action: {
                templatePickerMode = .addNewPage
                selectedTemplate = currentPage.background
                selectedTheme = currentPage.paperTheme
                showingTemplatePicker = true
            }) {
                HStack(spacing: 5) {
                    Image(systemName: "plus.circle.fill")
                    Text("New Page")
                }
                .font(.system(size: 12, weight: .bold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Capsule().fill(Theme.brandPurple))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 7)
        .background(
            Rectangle()
                .fill(.ultraThinMaterial)
                .shadow(color: Color.black.opacity(0.06), radius: 6, x: 0, y: -2)
        )
    }
    
    // MARK: - Tool Dial Overlay
    
    private var toolDialOverlay: some View {
        VStack {
            Spacer()
            HStack(alignment: .center) {
                Spacer()
                
                switch dialRailMode {
                case 0:
                    // Mode 0: Floating Liquid FAB
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                            dialRailMode = 1
                            isDialOpen = true
                        }
                    }) {
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [Theme.brandPurple, Color(hex: "#7C3AED")],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .overlay(
                                    Circle()
                                        .stroke(
                                            LinearGradient(
                                                colors: [.white.opacity(0.65), .white.opacity(0.15)],
                                                startPoint: .top,
                                                endPoint: .bottom
                                            ),
                                            lineWidth: 1.5
                                        )
                                )
                                .frame(width: 52, height: 52)
                                .shadow(color: Theme.brandPurple.opacity(0.4), radius: 10, x: 0, y: 4)
                            
                            Image(systemName: selectedDialTool.iconName)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                    .buttonStyle(LiquidScaleButtonStyle())
                    .padding(.trailing, 18)
                    .padding(.bottom, 64)
                    .transition(.scale(scale: 0.8).combined(with: .opacity))
                    
                case 1:
                    // Mode 1: Spinning Liquid Tool Dial
                    RadialToolDial(
                        items: noteDialItems,
                        selectedTool: $selectedDialTool,
                        isDialOpen: $isDialOpen,
                        onPick: { tool, _ in
                            handleDialPick(tool: tool)
                        }
                    )
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                    
                default:
                    // Mode 2: Liquid Collapsed Tool Rail
                    HStack(spacing: 8) {
                        if showingColorFlyout {
                            RailFlyoutColorCard(selectedColorHex: $activeColorHex) { hex in
                                activeColorHex = hex
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                    showingColorFlyout = false
                                }
                            }
                            .transition(.scale(scale: 0.85).combined(with: .opacity))
                        }
                        
                        CollapsedToolBar(
                            activeTool: selectedDialTool,
                            activeColorHex: activeColorHex,
                            onOpenDial: {
                                withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                    dialRailMode = 1
                                    isDialOpen = true
                                    showingColorFlyout = false
                                }
                            },
                            onToggleColorPalette: {
                                withAnimation(.spring(response: 0.32, dampingFraction: 0.75)) {
                                    showingColorFlyout.toggle()
                                }
                            },
                            onUndo: {
                                canvasProxy.undo()
                                saveCurrentDrawing()
                            },
                            onRedo: {
                                canvasProxy.redo()
                                saveCurrentDrawing()
                            },
                            onClearPage: {
                                canvasProxy.clear()
                                currentDrawing = PKDrawing()
                                saveCurrentDrawing()
                            },
                            onToggleStylusOnly: {
                                isFingerDrawing.toggle()
                            },
                            isStylusOnly: !isFingerDrawing,
                            onDone: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
                                    dialRailMode = 0
                                    isDialOpen = false
                                    showingColorFlyout = false
                                }
                            }
                        )
                    }
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                }
            }
            if dialRailMode != 0 {
                Spacer()
            }
        }
    }
    
    // MARK: - Actions
    
    private func handleDialPick(tool: DialToolItem) {
        selectedDialTool = tool
        withAnimation(.spring(response: 0.38, dampingFraction: 0.74)) {
            dialRailMode = 2
            isDialOpen = false
        }
        
        switch tool {
        case .stickyNote:
            insertElement(content: "New note…", kind: .STICKY, fillColor: activeColorHex)
        case .tape:
            insertTapeElement()
        case .textBox:
            showingAddElementSheet = true
        case .ruler:
            isRulerActive.toggle()
        default:
            break
        }
    }
    
    private func addNewPage(template: PageBackground, theme: PaperTheme) {
        saveCurrentDrawing()
        let newPage = NotePage(
            noteId: note.id,
            pageIndex: note.pages.count,
            background: template,
            paperTheme: theme
        )
        note.pages.append(newPage)
        currentPageIndex = note.pages.count - 1
        persistNote()
        loadCurrentDrawing()
    }
    
    private func duplicateCurrentPage() {
        saveCurrentDrawing()
        guard currentPageIndex < note.pages.count else { return }
        let current = note.pages[currentPageIndex]
        var duplicate = current
        duplicate.pageIndex = note.pages.count
        
        // Copy drawing
        let newDrawFile = "note_\(note.id)_p\(note.pages.count).drawing"
        duplicate.drawingFileName = newDrawFile
        storage.saveDrawing(currentDrawing, fileName: newDrawFile)
        
        note.pages.append(duplicate)
        currentPageIndex = note.pages.count - 1
        persistNote()
        loadCurrentDrawing()
    }
    
    private func deleteCurrentPage() {
        guard note.pages.count > 1 else { return }
        deletePage(at: currentPageIndex)
    }
    
    private func deletePage(at index: Int) {
        guard index < note.pages.count else { return }
        saveCurrentDrawing()
        note.pages.remove(at: index)
        if currentPageIndex >= note.pages.count {
            currentPageIndex = max(0, note.pages.count - 1)
        }
        persistNote()
        loadCurrentDrawing()
    }
    
    private func movePage(from: Int, to: Int) {
        guard from >= 0, from < note.pages.count, to >= 0, to < note.pages.count else { return }
        saveCurrentDrawing()
        let page = note.pages.remove(at: from)
        note.pages.insert(page, at: to)
        currentPageIndex = to
        persistNote()
        loadCurrentDrawing()
    }
    
    private func insertElement(content: String, kind: ElementKind, fillColor: String? = nil) {
        let el = NoteElement(
            kind: kind,
            x: 60,
            y: 80,
            width: kind == .STICKY ? 200 : 260,
            height: kind == .STICKY ? 120 : 90,
            content: content,
            fillColor: fillColor
        )
        if currentPageIndex < note.pages.count {
            note.pages[currentPageIndex].elements.append(el)
            persistNote()
        }
    }
    
    private func insertTapeElement() {
        let el = NoteElement(
            kind: .TAPE,
            x: 80,
            y: 120,
            width: 220,
            height: 36,
            content: "Study Tape",
            fillColor: activeColorHex
        )
        if currentPageIndex < note.pages.count {
            note.pages[currentPageIndex].elements.append(el)
            persistNote()
        }
    }
    
    private func insertImageElement(_ uiImage: UIImage) {
        let imgId = UUID().uuidString
        let url = storage.documentsDirectory.appendingPathComponent("Photos").appendingPathComponent("\(imgId).jpg")
        if let data = uiImage.jpegData(compressionQuality: 0.85) {
            try? data.write(to: url)
            let el = NoteElement(
                kind: .IMAGE,
                x: 60,
                y: 100,
                width: 240,
                height: 180,
                content: "",
                imagePath: url.path
            )
            if currentPageIndex < note.pages.count {
                note.pages[currentPageIndex].elements.append(el)
                persistNote()
            }
        }
    }
    
    private func deleteElement(_ id: String) {
        if currentPageIndex < note.pages.count {
            note.pages[currentPageIndex].elements.removeAll { $0.id == id }
            selectedElementId = nil
            persistNote()
        }
    }
    
    private func updateElementPosition(_ id: String, x: Float, y: Float) {
        if currentPageIndex < note.pages.count {
            if let idx = note.pages[currentPageIndex].elements.firstIndex(where: { $0.id == id }) {
                note.pages[currentPageIndex].elements[idx].x = x
                note.pages[currentPageIndex].elements[idx].y = y
                persistNote()
            }
        }
    }
    
    private func loadCurrentDrawing() {
        let fileName = "note_\(note.id)_p\(currentPageIndex).drawing"
        self.currentDrawing = storage.loadDrawing(fileName: fileName)
    }
    
    private func saveCurrentDrawing() {
        let fileName = "note_\(note.id)_p\(currentPageIndex).drawing"
        storage.saveDrawing(currentDrawing, fileName: fileName)
        if currentPageIndex < note.pages.count {
            note.pages[currentPageIndex].drawingFileName = fileName
        }
    }
    
    private func persistNote() {
        note.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        if let idx = storage.notes.firstIndex(where: { $0.id == note.id }) {
            storage.notes[idx] = note
        } else {
            storage.notes.append(note)
        }
        storage.saveAll()
    }
    
    private func exportAsPdf() {
        saveCurrentDrawing()
        do {
            let url = try NoteExporter.exportPdf(note: note) { idx in
                let fileName = "note_\(note.id)_p\(idx).drawing"
                return storage.loadDrawing(fileName: fileName)
            }
            self.shareURL = url
            self.showingShareSheet = true
        } catch {
            alertMessage = "Couldn't export PDF: \(error.localizedDescription)"
            showingAlert = true
        }
    }
    
    private func exportAsImage() {
        saveCurrentDrawing()
        do {
            let url = try NoteExporter.exportPng(
                note: note,
                pageIndex: currentPageIndex,
                drawing: currentDrawing
            )
            self.shareURL = url
            self.showingShareSheet = true
        } catch {
            alertMessage = "Couldn't export Image: \(error.localizedDescription)"
            showingAlert = true
        }
    }
}

// MARK: - Canvas Proxy for Programmatic Undo/Redo

public class CanvasProxy: ObservableObject {
    fileprivate weak var canvasView: PKCanvasView?
    
    public func undo() {
        canvasView?.undoManager?.undo()
    }
    
    public func redo() {
        canvasView?.undoManager?.redo()
    }
    
    public func clear() {
        canvasView?.drawing = PKDrawing()
    }
}

// MARK: - PencilKit Canvas Representable

public struct PencilKitCanvasRepresentable: UIViewRepresentable {
    @Binding public var drawing: PKDrawing
    public var selectedTool: DialToolItem
    public var activeColorHex: String
    public var isFingerDrawing: Bool
    public var isRulerActive: Bool
    public var canvasProxy: CanvasProxy
    public var onDrawingChanged: () -> Void
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public func makeUIView(context: Context) -> PKCanvasView {
        let canvas = PKCanvasView()
        canvas.backgroundColor = .clear
        canvas.isOpaque = false
        canvas.drawing = drawing
        canvas.delegate = context.coordinator
        canvas.drawingPolicy = isFingerDrawing ? .anyInput : .pencilOnly
        canvas.isRulerActive = isRulerActive
        updateTool(for: canvas)
        
        canvasProxy.canvasView = canvas
        
        return canvas
    }
    
    public func updateUIView(_ canvas: PKCanvasView, context: Context) {
        if canvas.drawing != drawing {
            canvas.drawing = drawing
        }
        let policy: PKCanvasViewDrawingPolicy = isFingerDrawing ? .anyInput : .pencilOnly
        if canvas.drawingPolicy != policy {
            canvas.drawingPolicy = policy
        }
        if canvas.isRulerActive != isRulerActive {
            canvas.isRulerActive = isRulerActive
        }
        updateTool(for: canvas)
    }
    
    private func updateTool(for canvas: PKCanvasView) {
        let color = UIColor(Color(hex: activeColorHex))
        switch selectedTool {
        case .pen(_, let width, let isPencil):
            if isPencil {
                canvas.tool = PKInkingTool(.pencil, color: color, width: width)
            } else {
                canvas.tool = PKInkingTool(.pen, color: color, width: width)
            }
        case .highlighter:
            canvas.tool = PKInkingTool(.marker, color: color.withAlphaComponent(0.42), width: 14.0)
        case .eraser:
            canvas.tool = PKEraserTool(.vector)
        case .lasso:
            canvas.tool = PKLassoTool()
        default:
            canvas.tool = PKInkingTool(.pen, color: color, width: 2.0)
        }
    }
    
    public class Coordinator: NSObject, PKCanvasViewDelegate {
        var parent: PencilKitCanvasRepresentable
        init(_ parent: PencilKitCanvasRepresentable) { self.parent = parent }
        
        public func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
            parent.drawing = canvasView.drawing
            parent.onDrawingChanged()
        }
    }
}

// MARK: - Note Element Canvas Item

public struct NoteElementCanvasItem: View {
    public let element: NoteElement
    public let scale: CGFloat
    public let isSelected: Bool
    public let isTapeRevealed: Bool
    public let onSelect: () -> Void
    public let onDelete: () -> Void
    public let onPositionChanged: (Float, Float) -> Void
    
    @State private var dragOffset: CGSize = .zero
    
    public var body: some View {
        let x = CGFloat(element.x) * scale + dragOffset.width
        let y = CGFloat(element.y) * scale + dragOffset.height
        let w = CGFloat(element.width) * scale
        let h = CGFloat(element.height) * scale
        
        ZStack(alignment: .topTrailing) {
            Group {
                switch element.kind {
                case .STICKY:
                    VStack(alignment: .leading, spacing: 4) {
                        Text(element.content)
                            .font(.system(size: 13 * scale, design: .serif))
                            .foregroundColor(Color(hex: "#1E293B"))
                    }
                    .padding(8 * scale)
                    .frame(width: w, height: h, alignment: .topLeading)
                    .background(
                        RoundedRectangle(cornerRadius: 6 * scale)
                            .fill(Color(hex: element.fillColor ?? "#FEF08A"))
                            .shadow(color: Color.black.opacity(0.14), radius: 4 * scale, x: 0, y: 2 * scale)
                    )
                    
                case .QUOTE:
                    HStack(spacing: 6 * scale) {
                        Rectangle()
                            .fill(Theme.brandPurple)
                            .frame(width: 4 * scale)
                        VStack(alignment: .leading, spacing: 3 * scale) {
                            Text(element.sourceBookId != nil ? "Book Excerpt" : "Quote")
                                .font(.system(size: 10 * scale, weight: .bold))
                                .foregroundColor(Theme.brandPurple)
                            Text(element.content)
                                .font(.system(size: 12 * scale, design: .serif))
                                .italic()
                                .foregroundColor(Theme.appTextPrimary)
                        }
                    }
                    .padding(8 * scale)
                    .frame(width: w, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 6 * scale)
                            .fill(Theme.brandPurpleSoft)
                    )
                    
                case .TEXT:
                    Text(element.content)
                        .font(.system(size: 14 * scale))
                        .foregroundColor(Theme.appTextPrimary)
                        .padding(6 * scale)
                        .frame(width: w, alignment: .leading)
                    
                case .IMAGE:
                    if let path = element.imagePath, let img = UIImage(contentsOfFile: path) {
                        Image(uiImage: img)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: w, height: h)
                            .clipShape(RoundedRectangle(cornerRadius: 6 * scale))
                            .shadow(color: Color.black.opacity(0.12), radius: 4 * scale, x: 0, y: 2 * scale)
                    } else {
                        Rectangle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(width: w, height: h)
                    }
                    
                case .TAPE:
                    // Study tape: opaque hazard stripes; revealed shows translucent outline
                    ZStack {
                        if isTapeRevealed {
                            RoundedRectangle(cornerRadius: 4 * scale)
                                .stroke(Color(hex: element.fillColor ?? "#F59E0B"), lineWidth: 2 * scale)
                                .background(Color(hex: element.fillColor ?? "#F59E0B").opacity(0.15))
                        } else {
                            RoundedRectangle(cornerRadius: 4 * scale)
                                .fill(Color(hex: element.fillColor ?? "#F59E0B"))
                            
                            // Tape stripes
                            Canvas { ctx, sz in
                                ctx.stroke(
                                    Path { p in
                                        var sx = -sz.height
                                        while sx < sz.width {
                                            p.move(to: CGPoint(x: sx, y: sz.height))
                                            p.addLine(to: CGPoint(x: sx + sz.height, y: 0))
                                            sx += 14 * scale
                                        }
                                    },
                                    with: .color(Color.white.opacity(0.25)),
                                    lineWidth: 3 * scale
                                )
                            }
                        }
                    }
                    .frame(width: w, height: h)
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: 6 * scale)
                    .stroke(isSelected ? Theme.brandPurple : Color.clear, lineWidth: 2)
            )
            .onTapGesture {
                onSelect()
            }
            .gesture(
                DragGesture()
                    .onChanged { g in
                        dragOffset = g.translation
                    }
                    .onEnded { g in
                        let finalX = element.x + Float(g.translation.width / scale)
                        let finalY = element.y + Float(g.translation.height / scale)
                        dragOffset = .zero
                        onPositionChanged(finalX, finalY)
                    }
            )
            
            // Delete badge if selected
            if isSelected {
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18 * scale))
                        .foregroundColor(Color.red)
                        .background(Circle().fill(Color.white))
                }
                .offset(x: 8 * scale, y: -8 * scale)
            }
        }
        .position(x: x + w / 2, y: y + h / 2)
    }
}

// MARK: - Add Element Sheet

public struct AddElementSheet: View {
    public var onAdd: (String, ElementKind, String?) -> Void
    @State private var text: String = ""
    @State private var kind: ElementKind = .STICKY
    @State private var selectedStickyColor: String = "#FEF08A"
    @Environment(\.dismiss) private var dismiss
    
    private let stickyColors = [
        "#FEF08A", // Canary Yellow
        "#BBF7D0", // Mint Green
        "#BFDBFE", // Sky Blue
        "#FBCFE8", // Pastel Pink
        "#FED7AA", // Soft Orange
        "#E9D5FF"  // Lavender
    ]
    
    public var body: some View {
        NavigationStack {
            Form {
                Section("Item Type") {
                    Picker("Kind", selection: $kind) {
                        Text("Sticky Note").tag(ElementKind.STICKY)
                        Text("Text Block").tag(ElementKind.TEXT)
                        Text("Quote Box").tag(ElementKind.QUOTE)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                }
                
                if kind == .STICKY {
                    Section("Sticky Color") {
                        HStack(spacing: 12) {
                            ForEach(stickyColors, id: \.self) { hex in
                                Circle()
                                    .fill(Color(hex: hex))
                                    .frame(width: 32, height: 32)
                                    .overlay(
                                        Circle()
                                            .stroke(selectedStickyColor == hex ? Theme.brandPurple : Color.gray.opacity(0.3), lineWidth: selectedStickyColor == hex ? 3 : 1)
                                    )
                                    .onTapGesture {
                                        selectedStickyColor = hex
                                    }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                
                Section("Content") {
                    TextEditor(text: $text)
                        .frame(height: 120)
                }
            }
            .navigationTitle("Insert Item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Insert") {
                        guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                        onAdd(text, kind, kind == .STICKY ? selectedStickyColor : nil)
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
        }
    }
}

// MARK: - iPad Safe Share Sheet

public struct iPadShareSheet: UIViewControllerRepresentable {
    public let items: [Any]
    
    public func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let popover = controller.popoverPresentationController {
            let window = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }
            popover.sourceView = window?.rootViewController?.view
            popover.sourceRect = CGRect(x: UIScreen.main.bounds.midX, y: UIScreen.main.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        return controller
    }
    
    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
