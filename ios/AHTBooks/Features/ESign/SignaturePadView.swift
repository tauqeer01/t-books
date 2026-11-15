import SwiftUI
import PencilKit

public struct SignaturePadView: View {
    public var kind: SignatureKind
    public var onSave: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var drawing = PKDrawing()
    
    public init(kind: SignatureKind = .SIGNATURE, onSave: @escaping (UIImage) -> Void) {
        self.kind = kind
        self.onSave = onSave
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text(kind == .SIGNATURE ? "Draw your signature below" : "Draw your initials below")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.appTextSecondary)
                    .padding(.top, 10)
                
                // Signature Drawing Canvas Box
                ZStack(alignment: .bottom) {
                    SignatureCanvas(drawing: $drawing)
                        .background(Color.white)
                        .cornerRadius(12)
                        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
                    
                    // Signature Baseline Line
                    Rectangle()
                        .fill(Theme.appDivider)
                        .frame(height: 1.5)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 40)
                }
                .frame(height: 220)
                .padding(.horizontal, 20)
                
                HStack(spacing: 16) {
                    Button(action: { drawing = PKDrawing() }) {
                        Text("Clear")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.annotationRed)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .background(Capsule().fill(Color.white))
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                
                Spacer()
            }
            .navigationTitle(kind == .SIGNATURE ? "New Signature" : "New Initials")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        let bounds = drawing.bounds
                        guard bounds.width > 0 && bounds.height > 0 else { return }
                        let targetRect = bounds.insetBy(dx: -10, dy: -10)
                        let image = drawing.image(from: targetRect, scale: 3.0)
                        onSave(image)
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.brandPurple)
                }
            }
            .background(Theme.appBackground.ignoresSafeArea())
        }
    }
}

private struct SignatureCanvas: UIViewRepresentable {
    @Binding var drawing: PKDrawing
    
    func makeUIView(context: Context) -> PKCanvasView {
        let canvas = PKCanvasView()
        canvas.backgroundColor = .clear
        canvas.isOpaque = false
        canvas.drawing = drawing
        canvas.tool = PKInkingTool(.pen, color: .black, width: 3.0)
        canvas.drawingPolicy = .anyInput
        return canvas
    }
    
    func updateUIView(_ canvas: PKCanvasView, context: Context) {
        if canvas.drawing != drawing {
            canvas.drawing = drawing
        }
    }
}
