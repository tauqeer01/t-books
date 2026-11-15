import SwiftUI
import PencilKit

// MARK: - Main tool bar

struct NoteToolBar: View {
    @ObservedObject var model: NoteEditorModel
    var onSticky: () -> Void
    var onPhoto: () -> Void
    var onCamera: () -> Void
    var onAddPage: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    ForEach(NoteTool.allCases) { tool in
                        let selected = model.tool == tool
                        Button {
                            UISelectionFeedbackGenerator().selectionChanged()
                            if tool != .text { model.finishEditing() }
                            model.tool = tool
                        } label: {
                            VStack(spacing: 3) {
                                Image(systemName: tool == .pen ? model.pen.type.icon : tool.icon)
                                    .font(.system(size: 17, weight: selected ? .bold : .regular))
                                    .foregroundColor(selected ? Theme.brandPurple : Theme.appTextPrimary)
                                    .frame(height: 22)
                                Text(tool.title)
                                    .font(.system(size: 10, weight: selected ? .bold : .medium))
                                    .foregroundColor(selected ? Theme.brandPurple : Theme.appTextSecondary)
                            }
                            .frame(width: 50, height: 46)
                            .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Theme.brandPurpleSoft : .clear))
                        }
                        .accessibilityLabel(tool.title)
                        .accessibilityIdentifier("notes.tool.\(tool.rawValue)")
                    }
                }
                .padding(.horizontal, 6)
            }
            Divider().frame(height: 30)
            Menu {
                Button(action: onSticky) { Label("Sticky note", systemImage: "note.text") }
                Button(action: onPhoto) { Label("Photo", systemImage: "photo") }
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    Button(action: onCamera) { Label("Camera", systemImage: "camera") }
                }
                Button(action: onAddPage) { Label("Page…", systemImage: "doc.badge.plus") }
                Button { model.paste(page: model.currentPage) } label: { Label("Paste", systemImage: "doc.on.clipboard") }
                    .disabled(!model.hasClipboard)
            } label: {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 26))
                    .foregroundColor(Theme.brandPurple)
                    .frame(width: 50, height: 46)
            }
            .accessibilityLabel("Insert")
            .accessibilityIdentifier("notes.insert")
        }
        .padding(.vertical, 4)
        .background(.ultraThinMaterial)
    }
}

// MARK: - Context bars

struct NoteContextBar: View {
    @ObservedObject var model: NoteEditorModel
    var onEditPen: () -> Void
    var onFormat: (NoteTextFormat) -> Void
    var onOpenSource: (NoteElement) -> Void

    var body: some View {
        Group {
            if model.editingElementId != nil {
                textFormatBar
            } else if model.selection != nil {
                selectionBar
            } else {
                switch model.tool {
                case .pen: penBar
                case .shape: shapeBar
                case .eraser: eraserBar
                case .tape: tapeBar
                case .lasso: hint("Draw around ink, text or images to select them")
                case .text: hint("Tap the page to type. Tap a text box or sticky note to edit it")
                case .hand: hint("Scroll and zoom. Tap an item to select it; tap tape to peek")
                case .laser: hint("Point with a glowing trail that fades away")
                }
            }
        }
        .frame(height: 52)
        .frame(maxWidth: .infinity)
        .background(.ultraThinMaterial)
    }

    private func hint(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(Theme.appTextSecondary)
            .padding(.horizontal, 16)
            .accessibilityIdentifier("notes.hint")
    }

    // Pens

    private var penBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(Array(model.pens.enumerated()), id: \.element.id) { index, pen in
                    let active = index == model.activePen && model.tool == .pen
                    Button {
                        if active { onEditPen() } else { model.selectPen(index) }
                    } label: {
                        PenGlyph(pen: pen, active: active)
                    }
                    .accessibilityLabel("\(pen.type.title) \(pen.colorHex)")
                    .accessibilityIdentifier("notes.pen.\(index)")
                }
                Button { model.addPen(); onEditPen() } label: {
                    Image(systemName: "plus").font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.brandPurple)
                        .frame(width: 34, height: 34)
                        .background(Circle().stroke(Theme.brandPurple.opacity(0.4), style: StrokeStyle(lineWidth: 1, dash: [3])))
                }
                .accessibilityLabel("Add pen")
                .accessibilityIdentifier("notes.pen.add")
            }
            .padding(.horizontal, 14)
        }
    }

    // Shapes

    private var shapeBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip("Auto", "wand.and.stars", selected: model.shapeKind == nil, id: "notes.shape.auto") { model.shapeKind = nil }
                ForEach(ShapeRecognizer.ShapeKind.allCases) { kind in
                    chip(kind.title, kind.icon, selected: model.shapeKind == kind, id: "notes.shape.\(kind.rawValue)") { model.shapeKind = kind }
                }
                Button(action: onEditPen) { PenGlyph(pen: model.pen, active: false) }
                    .accessibilityLabel("Shape pen")
            }
            .padding(.horizontal, 14)
        }
    }

    // Eraser

    private var eraserBar: some View {
        HStack(spacing: 10) {
            Picker("Eraser", selection: $model.eraserMode) {
                ForEach(NoteEraserMode.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 250)
            .accessibilityIdentifier("notes.eraser.mode")
            if model.eraserMode != .stroke {
                Slider(value: $model.eraserSize, in: 6...80).frame(maxWidth: 110)
                    .accessibilityIdentifier("notes.eraser.size")
            }
            Spacer(minLength: 0)
            Button("Clear page") { model.clearPageInk(model.currentPage) }
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.red)
                .accessibilityIdentifier("notes.eraser.clear")
        }
        .padding(.horizontal, 14)
    }

    // Tape

    private var tapeBar: some View {
        HStack(spacing: 10) {
            ForEach(noteTapeColors, id: \.self) { hex in
                Button { model.tapeColor = hex } label: {
                    RoundedRectangle(cornerRadius: 3).fill(Color(hex: hex)).frame(width: 30, height: 16)
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(model.tapeColor == hex ? Theme.brandPurple : .clear, lineWidth: 2.5).padding(-3))
                }
                .accessibilityIdentifier("notes.tape.\(hex)")
            }
            Spacer(minLength: 0)
            Button("Show all") { model.revealAllTapes(true) }.accessibilityIdentifier("notes.tape.showAll")
            Button("Hide all") { model.revealAllTapes(false) }.accessibilityIdentifier("notes.tape.hideAll")
        }
        .font(.system(size: 13, weight: .semibold))
        .padding(.horizontal, 14)
    }

    // Selection

    private var selectedElement: NoteElement? {
        guard let sel = model.selection, sel.strokeIndices.isEmpty, sel.elementIds.count == 1, let id = sel.elementIds.first else { return nil }
        return model.element(id)?.element
    }

    private var selectionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                if let el = selectedElement, el.kind == .TEXT || el.kind == .STICKY {
                    action("Edit", "pencil", "notes.sel.edit") {
                        model.selection = nil
                        model.checkpoint()
                        model.editingElementId = el.id
                    }
                }
                Menu {
                    ForEach(NotePen.palette + noteStickyColors, id: \.self) { hex in
                        Button { model.recolorSelection(hex) } label: { Label(hex, systemImage: "circle.fill") }
                    }
                } label: {
                    actionLabel("Color", "paintpalette")
                }
                .accessibilityIdentifier("notes.sel.color")
                action("Duplicate", "plus.square.on.square", "notes.sel.duplicate") { model.duplicateSelection() }
                action("Copy", "doc.on.doc", "notes.sel.copy") { model.copySelection() }
                action("Cut", "scissors", "notes.sel.cut") { model.cutSelection() }
                action("Front", "square.3.layers.3d.top.filled", "notes.sel.front") { model.bringSelectionToFront() }
                if let el = selectedElement, el.sourceBookId != nil {
                    action("Source", "book", "notes.sel.source") { onOpenSource(el) }
                }
                action("Delete", "trash", "notes.sel.delete", tint: .red) { model.deleteSelection() }
                action("Done", "checkmark", "notes.sel.done") { model.selection = nil }
            }
            .padding(.horizontal, 10)
        }
    }

    // Text

    private var textFormatBar: some View {
        HStack(spacing: 4) {
            action("Bold", "bold", "notes.fmt.bold") { onFormat(.bold) }
            action("Italic", "italic", "notes.fmt.italic") { onFormat(.italic) }
            action("Heading", "textformat.size", "notes.fmt.heading") { onFormat(.heading) }
            action("Bullet", "list.bullet", "notes.fmt.bullet") { onFormat(.bullet) }
            action("Checklist", "checklist", "notes.fmt.checklist") { onFormat(.checklist) }
            Spacer(minLength: 0)
            Button("Done") { model.finishEditing() }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Theme.brandPurple)
                .padding(.trailing, 12)
                .accessibilityIdentifier("notes.fmt.done")
        }
        .padding(.horizontal, 6)
    }

    // Helpers

    private func chip(_ title: String, _ icon: String, selected: Bool, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(selected ? .white : Theme.appTextPrimary)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Capsule().fill(selected ? Theme.brandPurple : Theme.brandPurpleSoft))
        }
        .accessibilityIdentifier(id)
    }

    private func actionLabel(_ title: String, _ icon: String, tint: Color = Theme.appTextPrimary) -> some View {
        VStack(spacing: 2) {
            Image(systemName: icon).font(.system(size: 15, weight: .semibold))
            Text(title).font(.system(size: 10, weight: .medium))
        }
        .foregroundColor(tint)
        .frame(minWidth: 50, minHeight: 44)
    }

    private func action(_ title: String, _ icon: String, _ id: String, tint: Color = Theme.appTextPrimary, _ run: @escaping () -> Void) -> some View {
        Button(action: run) { actionLabel(title, icon, tint: tint) }
            .accessibilityLabel(title)
            .accessibilityIdentifier(id)
    }
}

// MARK: - Pen glyph and editor

/// A pen drawn as a little nib in its color; the active pen is raised.
struct PenGlyph: View {
    let pen: NotePen
    let active: Bool

    var body: some View {
        VStack(spacing: 2) {
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 5)
                    .fill(LinearGradient(colors: [Color(hex: "#F8FAFC"), Color(hex: "#E2E8F0")], startPoint: .leading, endPoint: .trailing))
                    .frame(width: 16, height: 36)
                    .overlay(RoundedRectangle(cornerRadius: 5).stroke(Color.black.opacity(0.08)))
                Triangle()
                    .fill(Color(hex: pen.colorHex).opacity(pen.type.isTranslucent ? 0.7 : 1))
                    .frame(width: pen.type == .highlighter || pen.type == .marker ? 14 : 10, height: 10)
                    .offset(y: -9)
            }
            Capsule().fill(Color(hex: pen.colorHex)).frame(width: max(min(pen.width * 1.2, 16), 3), height: 3)
        }
        .offset(y: active ? -4 : 4)
        .frame(width: 30, height: 46)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: active)
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: rect.midX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            p.closeSubpath()
        }
    }
}

struct PenEditorSheet: View {
    @ObservedObject var model: NoteEditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var custom = Color.black

    private var palette: [String] { model.pen.type.isTranslucent ? NotePen.highlighterPalette : NotePen.palette }

    var body: some View {
        NavigationStack {
            Form {
                Section("Pen") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(NotePenType.allCases) { type in
                                let selected = model.pen.type == type
                                Button {
                                    model.updateActivePen { pen in
                                        pen.type = type
                                        pen.width = min(max(pen.width, type.widthRange.lowerBound), type.widthRange.upperBound)
                                        if type.isTranslucent && !NotePen.highlighterPalette.contains(pen.colorHex) { pen.colorHex = NotePen.highlighterPalette[0] }
                                    }
                                } label: {
                                    VStack(spacing: 4) {
                                        Image(systemName: type.icon).font(.system(size: 18))
                                        Text(type.title).font(.system(size: 11, weight: .medium))
                                    }
                                    .foregroundColor(selected ? .white : Theme.appTextPrimary)
                                    .frame(width: 84, height: 54)
                                    .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Theme.brandPurple : Theme.brandPurpleSoft))
                                }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("penEditor.type.\(type.rawValue)")
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                Section("Color") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 12) {
                        ForEach(palette, id: \.self) { hex in
                            Button { model.updateActivePen { $0.colorHex = hex } } label: {
                                Circle().fill(Color(hex: hex)).frame(width: 30, height: 30)
                                    .overlay(Circle().stroke(model.pen.colorHex == hex ? Theme.brandPurple : Color.black.opacity(0.1),
                                                             lineWidth: model.pen.colorHex == hex ? 3 : 1))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("penEditor.color.\(hex)")
                        }
                    }
                    .padding(.vertical, 4)
                    ColorPicker("Custom color", selection: Binding(get: { Color(hex: model.pen.colorHex) }, set: { color in
                        model.updateActivePen { $0.colorHex = color.hexString }
                    }), supportsOpacity: false)
                }
                Section {
                    HStack {
                        Text("Thickness")
                        Spacer()
                        Text(String(format: "%.1f pt", model.pen.width)).foregroundColor(Theme.appTextSecondary)
                    }
                    Slider(value: Binding(get: { model.pen.width }, set: { w in model.updateActivePen { $0.width = w } }),
                           in: model.pen.type.widthRange)
                        .accessibilityIdentifier("penEditor.width")
                    PenPreviewStroke(pen: model.pen).frame(height: 44)
                }
                if model.pens.count > 1 {
                    Section {
                        Button("Remove pen", role: .destructive) {
                            model.removeActivePen()
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Pen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.accessibilityIdentifier("penEditor.done")
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// A sample squiggle drawn with the real PencilKit ink.
struct PenPreviewStroke: UIViewRepresentable {
    let pen: NotePen

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .center
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) {
        let points = (0...40).map { i -> PKStrokePoint in
            let x = CGFloat(i) * 6 + 10
            let y = 22 + sin(CGFloat(i) / 4) * 10
            return PKStrokePoint(location: CGPoint(x: x, y: y), timeOffset: TimeInterval(i) * 0.01,
                                 size: CGSize(width: pen.width, height: pen.width), opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
        }
        let stroke = PKStroke(ink: PKInk(pen.type.inkType, color: pen.color), path: PKStrokePath(controlPoints: points, creationDate: Date()))
        view.image = PKDrawing(strokes: [stroke]).image(from: CGRect(x: 0, y: 0, width: 270, height: 44), scale: UIScreen.main.scale)
    }
}

extension Color {
    /// "#RRGGBB" for a SwiftUI color.
    var hexString: String {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X", Int(round(max(0, min(1, r)) * 255)), Int(round(max(0, min(1, g)) * 255)), Int(round(max(0, min(1, b)) * 255)))
    }
}

// MARK: - Camera

struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}
