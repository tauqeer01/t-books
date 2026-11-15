import UIKit
import PencilKit
import SwiftUI

public enum NoteExporter {
    public static let a4Width: CGFloat = 595.2
    public static let a4Height: CGFloat = 841.8
    
    /// Renders a single page to a UIImage at specified point dimensions and scale.
    public static func renderPageImage(
        page: NotePage,
        drawing: PKDrawing,
        size: CGSize = CGSize(width: a4Width, height: a4Height),
        scale: CGFloat = 2.0
    ) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        
        return renderer.image { ctx in
            let rect = CGRect(origin: .zero, size: size)
            
            // 1. Draw template background
            PageTemplateRenderer.draw(
                context: ctx.cgContext,
                background: page.background,
                paperTheme: page.paperTheme,
                rect: rect
            )
            
            // 2. Draw non-tape elements (sticky notes, quotes, text)
            let scaleX = size.width / a4Width
            let scaleY = size.height / a4Height
            
            for el in page.elements where el.kind != .TAPE {
                drawElement(el, in: ctx.cgContext, scaleX: scaleX, scaleY: scaleY)
            }
            
            // 3. Draw PencilKit ink
            let inkImage = drawing.image(from: CGRect(origin: .zero, size: CGSize(width: a4Width, height: a4Height)), scale: scale)
            inkImage.draw(in: rect)
            
            // 4. Draw tape elements (on top of ink)
            for el in page.elements where el.kind == .TAPE {
                drawTape(el, in: ctx.cgContext, scaleX: scaleX, scaleY: scaleY)
            }
        }
    }
    
    /// Exports the full multi-page note as an A4 vector PDF.
    public static func exportPdf(
        note: Note,
        loadDrawing: (Int) -> PKDrawing
    ) throws -> URL {
        let safeTitle = note.title.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
        let filename = safeTitle.isEmpty ? "Note.pdf" : "\(safeTitle).pdf"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        
        let pageRect = CGRect(x: 0, y: 0, width: a4Width, height: a4Height)
        let format = UIGraphicsPDFRendererFormat()
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)
        
        let data = renderer.pdfData { context in
            for (index, page) in note.pages.enumerated() {
                context.beginPage()
                let cgContext = context.cgContext
                
                // 1. Template
                PageTemplateRenderer.draw(
                    context: cgContext,
                    background: page.background,
                    paperTheme: page.paperTheme,
                    rect: pageRect
                )
                
                // 2. Elements (below ink)
                for el in page.elements where el.kind != .TAPE {
                    drawElement(el, in: cgContext, scaleX: 1.0, scaleY: 1.0)
                }
                
                // 3. Ink layer
                let drawing = loadDrawing(index)
                let inkImage = drawing.image(from: pageRect, scale: 2.0)
                inkImage.draw(in: pageRect)
                
                // 4. Tape layer (above ink)
                for el in page.elements where el.kind == .TAPE {
                    drawTape(el, in: cgContext, scaleX: 1.0, scaleY: 1.0)
                }
            }
        }
        
        try data.write(to: url, options: .atomic)
        return url
    }
    
    /// Exports a specific note page as a high-resolution PNG image.
    public static func exportPng(
        note: Note,
        pageIndex: Int,
        drawing: PKDrawing
    ) throws -> URL {
        guard pageIndex < note.pages.count else {
            throw NSError(domain: "NoteExporter", code: 404, userInfo: [NSLocalizedDescriptionKey: "Page index out of bounds"])
        }
        let page = note.pages[pageIndex]
        let safeTitle = note.title.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
        let filename = "\(safeTitle.isEmpty ? "Note" : safeTitle)_p\(pageIndex + 1).png"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        
        // High-res rendering: scale 3.0 gives 1785 x 2525 px (~300 DPI A4)
        let image = renderPageImage(page: page, drawing: drawing, size: CGSize(width: a4Width, height: a4Height), scale: 3.0)
        guard let pngData = image.pngData() else {
            throw NSError(domain: "NoteExporter", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to encode PNG"])
        }
        
        try pngData.write(to: url, options: .atomic)
        return url
    }
    
    // MARK: - Private Drawing Helpers
    
    private static func drawElement(_ el: NoteElement, in context: CGContext, scaleX: CGFloat, scaleY: CGFloat) {
        let x = CGFloat(el.x) * scaleX
        let y = CGFloat(el.y) * scaleY
        let w = CGFloat(el.width) * scaleX
        let h = CGFloat(el.height) * scaleY
        let rect = CGRect(x: x, y: y, width: w, height: h)
        
        switch el.kind {
        case .STICKY:
            let bgHex = el.fillColor ?? "#FEF08A"
            let bgColor = UIColor(Color(hex: bgHex))
            context.saveGState()
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 8 * scaleX)
            context.setFillColor(bgColor.cgColor)
            context.setShadow(offset: CGSize(width: 0, height: 2), blur: 4, color: UIColor.black.withAlphaComponent(0.15).cgColor)
            path.fill()
            context.restoreGState()
            
            // Text inside sticky note
            let pad: CGFloat = 8 * scaleX
            let textRect = rect.insetBy(dx: pad, dy: pad)
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byWordWrapping
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13 * scaleX, weight: .medium),
                .foregroundColor: UIColor(red: 0.12, green: 0.15, blue: 0.20, alpha: 1.0),
                .paragraphStyle: para
            ]
            (el.content as NSString).draw(in: textRect, withAttributes: attrs)
            
        case .QUOTE:
            context.saveGState()
            let bgColor = UIColor(Color(hex: "#EEF2FF")) // Soft indigo
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 6 * scaleX)
            context.setFillColor(bgColor.cgColor)
            path.fill()
            
            // Left purple accent border
            let leftBorder = CGRect(x: rect.minX, y: rect.minY, width: 4 * scaleX, height: rect.height)
            let borderPath = UIBezierPath(roundedRect: leftBorder, byRoundingCorners: [.topLeft, .bottomLeft], cornerRadii: CGSize(width: 6 * scaleX, height: 6 * scaleX))
            UIColor(Color(hex: "#6366F1")).setFill()
            borderPath.fill()
            context.restoreGState()
            
            let pad: CGFloat = 10 * scaleX
            let textRect = CGRect(x: rect.minX + pad + 2, y: rect.minY + pad, width: rect.width - pad * 2, height: rect.height - pad * 2)
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byWordWrapping
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.italicSystemFont(ofSize: 12 * scaleX),
                .foregroundColor: UIColor(red: 0.2, green: 0.25, blue: 0.35, alpha: 1.0),
                .paragraphStyle: para
            ]
            (el.content as NSString).draw(in: textRect, withAttributes: attrs)
            
        case .TEXT:
            let para = NSMutableParagraphStyle()
            para.lineBreakMode = .byWordWrapping
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 14 * scaleX, weight: .regular),
                .foregroundColor: UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1.0),
                .paragraphStyle: para
            ]
            (el.content as NSString).draw(in: rect, withAttributes: attrs)
            
        case .IMAGE:
            if let img = NoteElementStyle.image(at: el.imagePath) {
                img.draw(in: rect)
            }
            
        case .TAPE:
            break
        }
    }
    
    private static func drawTape(_ el: NoteElement, in context: CGContext, scaleX: CGFloat, scaleY: CGFloat) {
        let x = CGFloat(el.x) * scaleX
        let y = CGFloat(el.y) * scaleY
        let w = CGFloat(el.width) * scaleX
        let h = CGFloat(el.height) * scaleY
        let rect = CGRect(x: x, y: y, width: w, height: h)
        
        let colorHex = el.fillColor ?? "#F59E0B"
        let tapeColor = UIColor(Color(hex: colorHex))
        
        context.saveGState()
        let path = UIBezierPath(roundedRect: rect, cornerRadius: 4 * scaleX)
        context.setFillColor(tapeColor.cgColor)
        path.fill()
        
        // Diagonal tape stripes pattern
        context.clip(to: rect)
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.25).cgColor)
        context.setLineWidth(3 * scaleX)
        var stripeX = rect.minX - rect.height
        while stripeX < rect.maxX {
            context.beginPath()
            context.move(to: CGPoint(x: stripeX, y: rect.maxY))
            context.addLine(to: CGPoint(x: stripeX + rect.height, y: rect.minY))
            context.strokePath()
            stripeX += 14 * scaleX
        }
        context.restoreGState()
    }
}
