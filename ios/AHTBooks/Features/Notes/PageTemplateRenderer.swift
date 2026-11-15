import SwiftUI
import UIKit

// MARK: - Page Template Renderer

public struct PageTemplateRenderer {
    
    public static func draw(
        context: CGContext,
        background: PageBackground,
        paperTheme: PaperTheme,
        rect: CGRect
    ) {
        let w = rect.width
        let h = rect.height
        let isDark = paperTheme.isDark
        
        // Fill paper background
        let paperColor = UIColor(Color(hex: paperTheme.hexColor))
        context.setFillColor(paperColor.cgColor)
        context.fill(rect)
        
        // Colors according to theme
        let lightLine = isDark
            ? UIColor.white.withAlphaComponent(0.12).cgColor
            : UIColor(red: 0.84, green: 0.86, blue: 0.90, alpha: 1.0).cgColor
        let faintLine = isDark
            ? UIColor.white.withAlphaComponent(0.06).cgColor
            : UIColor(red: 0.92, green: 0.93, blue: 0.95, alpha: 1.0).cgColor
        let mediumLine = isDark
            ? UIColor.white.withAlphaComponent(0.24).cgColor
            : UIColor(red: 0.76, green: 0.79, blue: 0.84, alpha: 1.0).cgColor
        let accentLine = isDark
            ? UIColor(red: 0.98, green: 0.50, blue: 0.55, alpha: 0.7).cgColor
            : UIColor(red: 0.95, green: 0.72, blue: 0.71, alpha: 1.0).cgColor
        
        func strokeLine(x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat, color: CGColor, width: CGFloat = 1.0) {
            context.setStrokeColor(color)
            context.setLineWidth(max(0.5, width))
            context.beginPath()
            context.move(to: CGPoint(x: x1, y: y1))
            context.addLine(to: CGPoint(x: x2, y: y2))
            context.strokePath()
        }
        
        func drawRuled(top: CGFloat, bottom: CGFloat, left: CGFloat, right: CGFloat, spacing: CGFloat, color: CGColor = lightLine) {
            var y = top
            while y < bottom {
                strokeLine(x1: left, y1: y, x2: right, y2: y, color: color, width: 0.8)
                y += spacing
            }
        }
        
        let margin: CGFloat = max(16, w * 0.08)
        let mm: CGFloat = w / 210.0 // 210 mm width for standard A4
        
        switch background {
        case .BLANK:
            break
            
        case .RULED:
            let spacing = max(18, 9.0 * mm) // ~9 mm ruled
            let top = spacing * 2.5
            let bottom = h - spacing
            drawRuled(top: top, bottom: bottom, left: margin, right: w - margin, spacing: spacing)
            // Left margin line
            strokeLine(x1: margin * 1.8, y1: top * 0.8, x2: margin * 1.8, y2: bottom, color: accentLine, width: 1.2)
            
        case .NARROW:
            let spacing = max(14, 6.5 * mm) // ~6.5 mm college ruled
            let top = spacing * 2.5
            let bottom = h - spacing
            drawRuled(top: top, bottom: bottom, left: margin, right: w - margin, spacing: spacing)
            strokeLine(x1: margin * 1.8, y1: top * 0.8, x2: margin * 1.8, y2: bottom, color: accentLine, width: 1.2)
            
        case .GRID:
            let step = max(10, 5.0 * mm)
            var x = step
            while x < w {
                strokeLine(x1: x, y1: 0, x2: x, y2: h, color: lightLine, width: 0.7)
                x += step
            }
            var y = step
            while y < h {
                strokeLine(x1: 0, y1: y, x2: w, y2: y, color: lightLine, width: 0.7)
                y += step
            }
            
        case .MATH_GRAPH:
            // Multi-tier graph: fine (1mm), medium (5mm), bold (10mm)
            let step1 = max(3, 1.0 * mm)
            let step5 = max(15, 5.0 * mm)
            let step10 = max(30, 10.0 * mm)
            
            var x: CGFloat = step1
            while x < w {
                let is10 = (Int(round(x)) % max(1, Int(round(step10)))) < 2
                let is5 = (Int(round(x)) % max(1, Int(round(step5)))) < 2
                let color = is10 ? mediumLine : (is5 ? lightLine : faintLine)
                let lw: CGFloat = is10 ? 1.2 : (is5 ? 0.8 : 0.4)
                strokeLine(x1: x, y1: 0, x2: x, y2: h, color: color, width: lw)
                x += step1
            }
            var y: CGFloat = step1
            while y < h {
                let is10 = (Int(round(y)) % max(1, Int(round(step10)))) < 2
                let is5 = (Int(round(y)) % max(1, Int(round(step5)))) < 2
                let color = is10 ? mediumLine : (is5 ? lightLine : faintLine)
                let lw: CGFloat = is10 ? 1.2 : (is5 ? 0.8 : 0.4)
                strokeLine(x1: 0, y1: y, x2: w, y2: y, color: color, width: lw)
                y += step1
            }
            
        case .DOTTED:
            let step = max(12, 5.0 * mm)
            let dotRadius: CGFloat = max(1.0, step * 0.05)
            context.setFillColor(mediumLine)
            var y = step
            while y < h {
                var x = step
                while x < w {
                    context.fillEllipse(in: CGRect(x: x - dotRadius, y: y - dotRadius, width: dotRadius * 2, height: dotRadius * 2))
                    x += step
                }
                y += step
            }
            
        case .CORNELL:
            let header = h * 0.09
            let summary = h * 0.82
            let cue = w * 0.30
            let spacing = max(16, 8.0 * mm)
            
            // Header divider
            strokeLine(x1: 0, y1: header, x2: w, y2: header, color: mediumLine, width: 1.5)
            // Vertical cue divider
            strokeLine(x1: cue, y1: header, x2: cue, y2: summary, color: mediumLine, width: 1.5)
            // Summary bottom divider
            strokeLine(x1: 0, y1: summary, x2: w, y2: summary, color: mediumLine, width: 1.5)
            
            // Notes area ruled lines
            drawRuled(top: header + spacing, bottom: summary - 4, left: cue + 10, right: w - margin, spacing: spacing)
            // Summary area ruled lines
            drawRuled(top: summary + spacing, bottom: h - margin * 0.5, left: margin, right: w - margin, spacing: spacing)
            
        case .MUSIC:
            let lineGap = max(3.5, 2.2 * mm)
            let staffGap = max(24, 16.0 * mm)
            var top = max(30, 22.0 * mm)
            while top + 4 * lineGap < h - 16 * mm {
                for i in 0..<5 {
                    let y = top + CGFloat(i) * lineGap
                    strokeLine(x1: margin, y1: y, x2: w - margin, y2: y, color: mediumLine, width: 0.9)
                }
                top += 4 * lineGap + staffGap
            }
            
        case .ISOMETRIC:
            // 30 degree triangular grid
            let side = max(14, 6.0 * mm)
            let colWidth = side * sqrt(3.0) / 2.0
            var x: CGFloat = 0
            while x <= w {
                strokeLine(x1: x, y1: 0, x2: x, y2: h, color: faintLine, width: 0.6)
                x += colWidth
            }
            let slope = 1.0 / sqrt(3.0) // tan 30 deg
            let rise = w * slope
            var c = -rise
            while c < h + rise {
                strokeLine(x1: 0, y1: c, x2: w, y2: c + rise, color: lightLine, width: 0.6)
                strokeLine(x1: 0, y1: c + rise, x2: w, y2: c, color: lightLine, width: 0.6)
                c += side
            }
            
        case .CHECKLIST:
            let spacing = max(18, 8.5 * mm)
            let top = spacing * 2.0
            let bottom = h - spacing
            drawRuled(top: top, bottom: bottom, left: margin + 28, right: w - margin, spacing: spacing)
            
            // Draw checkbox squares
            context.setStrokeColor(mediumLine)
            context.setLineWidth(1.2)
            var y = top
            let boxSize: CGFloat = max(10, spacing * 0.5)
            while y < bottom {
                let boxRect = CGRect(x: margin, y: y - boxSize * 0.85, width: boxSize, height: boxSize)
                let path = UIBezierPath(roundedRect: boxRect, cornerRadius: 2.5)
                context.addPath(path.cgPath)
                context.strokePath()
                y += spacing
            }
            
        case .TWO_COLUMN:
            let spacing = max(18, 8.5 * mm)
            let top = spacing * 2.0
            let bottom = h - spacing
            let midX = w / 2.0
            
            // Central divider line
            strokeLine(x1: midX, y1: top * 0.8, x2: midX, y2: bottom, color: mediumLine, width: 1.2)
            // Left column lines
            drawRuled(top: top, bottom: bottom, left: margin, right: midX - 10, spacing: spacing)
            // Right column lines
            drawRuled(top: top, bottom: bottom, left: midX + 10, right: w - margin, spacing: spacing)
        }
    }
}

// MARK: - SwiftUI Background View

public struct PageTemplateBackground: View {
    public let template: PageBackground
    public let paperTheme: PaperTheme
    
    public init(template: PageBackground, paperTheme: PaperTheme = .white) {
        self.template = template
        self.paperTheme = paperTheme
    }
    
    public var body: some View {
        Canvas { context, size in
            context.withCGContext { cgContext in
                PageTemplateRenderer.draw(
                    context: cgContext,
                    background: template,
                    paperTheme: paperTheme,
                    rect: CGRect(origin: .zero, size: size)
                )
            }
        }
        .background(Color(hex: paperTheme.hexColor))
    }
}

// MARK: - Template Picker Sheet

public struct PageTemplatePicker: View {
    @Binding public var selectedTemplate: PageBackground
    @Binding public var selectedTheme: PaperTheme
    public var onSelect: (PageBackground, PaperTheme) -> Void
    @Environment(\.dismiss) private var dismiss
    
    public init(
        selectedTemplate: Binding<PageBackground>,
        selectedTheme: Binding<PaperTheme>,
        onSelect: @escaping (PageBackground, PaperTheme) -> Void
    ) {
        self._selectedTemplate = selectedTemplate
        self._selectedTheme = selectedTheme
        self.onSelect = onSelect
    }
    
    private let columns = [
        GridItem(.adaptive(minimum: 105, maximum: 140), spacing: 14)
    ]
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Paper Theme Selector Bar
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(PaperTheme.allCases, id: \.self) { theme in
                            Button(action: {
                                selectedTheme = theme
                            }) {
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(Color(hex: theme.hexColor))
                                        .frame(width: 18, height: 18)
                                        .overlay(
                                            Circle()
                                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                                        )
                                    Text(theme.rawValue)
                                        .font(.system(size: 13, weight: selectedTheme == theme ? .bold : .medium))
                                        .foregroundColor(selectedTheme == theme ? Theme.brandPurple : Theme.appTextPrimary)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(selectedTheme == theme ? Theme.brandPurpleSoft : Color(hex: "#F1F5F9"))
                                )
                                .overlay(
                                    Capsule()
                                        .stroke(selectedTheme == theme ? Theme.brandPurple : Color.clear, lineWidth: 1.5)
                                )
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .background(Theme.appSurface)
                
                Divider()
                
                // Templates Grid
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(PageBackground.allCases, id: \.self) { tpl in
                            TemplateCard(
                                template: tpl,
                                theme: selectedTheme,
                                isSelected: selectedTemplate == tpl
                            ) {
                                selectedTemplate = tpl
                                onSelect(tpl, selectedTheme)
                                dismiss()
                            }
                        }
                    }
                    .padding(16)
                }
                .background(Color(hex: "#F8FAFC"))
            }
            .navigationTitle("Page Template")
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
        }
    }
}

// MARK: - Template Card

public struct TemplateCard: View {
    public let template: PageBackground
    public let theme: PaperTheme
    public let isSelected: Bool
    public let onSelect: () -> Void
    
    public var body: some View {
        Button(action: onSelect) {
            VStack(spacing: 8) {
                // Miniature A4 Page
                ZStack {
                    PageTemplateBackground(template: template, paperTheme: theme)
                        .aspectRatio(595.0 / 842.0, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(isSelected ? Theme.brandPurple : Color.black.opacity(0.1), lineWidth: isSelected ? 2.5 : 1)
                        )
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 2) {
                    Text(template.title)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(isSelected ? Theme.brandPurple : Theme.appTextPrimary)
                        .lineLimit(1)
                    
                    Text(template.subtitle)
                        .font(.system(size: 10))
                        .foregroundColor(Theme.appTextSecondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(6)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Theme.brandPurpleSoft.opacity(0.3) : Color.clear)
            )
        }
        .buttonStyle(.plain)
    }
}
