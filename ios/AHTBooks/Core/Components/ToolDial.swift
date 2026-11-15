import SwiftUI
import UIKit

// MARK: - Tool Items

public enum DialToolItem: Identifiable, Equatable {
    case hand
    case selectText
    case clip
    case pen(name: String, width: CGFloat, isPencil: Bool)
    case highlighter
    case textHighlight
    case textUnderline
    case textStrikethrough
    case line
    case arrow
    case rectangle
    case ellipse
    case stickyNote
    case tape
    case textBox
    case lasso
    case ruler
    case eraser
    
    public var id: String { label }
    
    public var label: String {
        switch self {
        case .hand: return "Scroll"
        case .selectText: return "Select Text"
        case .clip: return "Clip to Note"
        case .pen(let name, _, _): return name
        case .highlighter: return "Marker"
        case .textHighlight: return "Highlight"
        case .textUnderline: return "Underline"
        case .textStrikethrough: return "Strikethrough"
        case .line: return "Line"
        case .arrow: return "Arrow"
        case .rectangle: return "Rectangle"
        case .ellipse: return "Ellipse"
        case .stickyNote: return "Sticky Note"
        case .tape: return "Study Tape"
        case .textBox: return "Text Box"
        case .lasso: return "Lasso Select"
        case .ruler: return "Ruler"
        case .eraser: return "Eraser"
        }
    }
    
    public var iconName: String {
        switch self {
        case .hand: return "hand.point.up.left.fill"
        case .selectText: return "selection.pin.in.out"
        case .clip: return "scissors"
        case .pen(_, _, let isPencil): return isPencil ? "pencil" : "pencil.tip"
        case .highlighter: return "paintbrush.pointed.fill"
        case .textHighlight: return "highlighter"
        case .textUnderline: return "underline"
        case .textStrikethrough: return "strikethrough"
        case .line: return "line.diagonal"
        case .arrow: return "arrow.up.right"
        case .rectangle: return "rectangle"
        case .ellipse: return "oval"
        case .stickyNote: return "note.text"
        case .tape: return "bandage"
        case .textBox: return "character.textbox"
        case .lasso: return "lasso"
        case .ruler: return "ruler"
        case .eraser: return "eraser.fill"
        }
    }
    
    public var hasColor: Bool {
        switch self {
        case .pen, .highlighter, .textHighlight, .textUnderline, .textStrikethrough, .line, .arrow, .rectangle, .ellipse, .stickyNote, .tape, .textBox:
            return true
        default:
            return false
        }
    }
    
    public var isDrawingInk: Bool {
        switch self {
        case .pen, .highlighter, .eraser, .lasso, .line, .arrow, .rectangle, .ellipse:
            return true
        default:
            return false
        }
    }
}

// MARK: - Liquid Native iOS Radial Tool Dial

public struct RadialToolDial: View {
    public let items: [DialToolItem]
    @Binding public var selectedTool: DialToolItem
    @Binding public var isDialOpen: Bool
    public var onPick: (DialToolItem, Bool) -> Void
    
    // Rotation state in slot units (0.0 = first item at 9 o'clock slot)
    @State private var rotation: Double = 0.0
    @State private var dragOffset: Double = 0.0
    @State private var lastHapticIndex: Int = 0
    
    private let dialRadius: CGFloat = 160
    private let baseItemSize: CGFloat = 44
    private let hubRadius: CGFloat = 40
    private let stepAngle: Double = 23.0 * .pi / 180.0 // 23 degrees per tool
    
    private let feedbackGenerator = UISelectionFeedbackGenerator()
    
    public init(
        items: [DialToolItem],
        selectedTool: Binding<DialToolItem>,
        isDialOpen: Binding<Bool>,
        onPick: @escaping (DialToolItem, Bool) -> Void
    ) {
        self.items = items
        self._selectedTool = selectedTool
        self._isDialOpen = isDialOpen
        self.onPick = onPick
        
        let initialIndex = items.firstIndex(of: selectedTool.wrappedValue) ?? 0
        self._rotation = State(initialValue: Double(initialIndex))
        self._lastHapticIndex = State(initialValue: initialIndex)
    }
    
    private var ringRadius: CGFloat {
        dialRadius - 38
    }
    
    private var currentPosition: Double {
        rotation - dragOffset
    }
    
    private var activeIndex: Int {
        min(max(0, Int(round(currentPosition))), items.count - 1)
    }
    
    public var body: some View {
        HStack(alignment: .center, spacing: 14) {
            // Liquid Frosted Label Pill (floats to the left of the active slot)
            if activeIndex >= 0 && activeIndex < items.count {
                let activeItem = items[activeIndex]
                HStack(spacing: 8) {
                    Image(systemName: activeItem.iconName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Theme.brandPurple)
                    
                    Text(activeItem.label)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.appTextPrimary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(.ultraThinMaterial)
                        .overlay(
                            Capsule()
                                .stroke(
                                    LinearGradient(
                                        colors: [.white.opacity(0.8), .white.opacity(0.2)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1
                                )
                        )
                        .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 3)
                )
                .transition(.asymmetric(
                    insertion: .scale(scale: 0.85).combined(with: .opacity),
                    removal: .opacity
                ))
                .animation(.spring(response: 0.28, dampingFraction: 0.75), value: activeIndex)
            }
            
            // Liquid Frosted Glass Half-Disc Dial
            ZStack(alignment: .trailing) {
                // 1. Frosted Translucent Glass Half-Disc Background
                HalfCircleShape()
                    .fill(.ultraThinMaterial)
                    .overlay(
                        HalfCircleShape()
                            .fill(
                                LinearGradient(
                                    colors: [
                                        Color(hex: "#EDE9FE").opacity(0.4),
                                        Color(hex: "#F5F3FF").opacity(0.15)
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                    )
                    .overlay(
                        HalfCircleShape()
                            .stroke(
                                LinearGradient(
                                    colors: [
                                        Color.white.opacity(0.9),
                                        Color.white.opacity(0.2),
                                        Theme.brandPurple.opacity(0.25)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1.5
                            )
                    )
                    .frame(width: dialRadius, height: dialRadius * 2)
                    .shadow(color: Color.black.opacity(0.14), radius: 18, x: -6, y: 0)
                    .shadow(color: Theme.brandPurple.opacity(0.15), radius: 30, x: -10, y: 0)
                
                // 2. Active Slot Detent Halo & Glowing Guides
                Canvas { context, size in
                    let slotCenter = CGPoint(x: size.width - ringRadius, y: size.height / 2.0)
                    
                    // Outer diffuse glow ring
                    let glowRect = CGRect(x: slotCenter.x - 30, y: slotCenter.y - 30, width: 60, height: 60)
                    context.stroke(Path(ellipseIn: glowRect), with: .color(Theme.brandPurple.opacity(0.15)), lineWidth: 6)
                    
                    // Sharp active slot ring
                    let slotRect = CGRect(x: slotCenter.x - 27, y: slotCenter.y - 27, width: 54, height: 54)
                    context.stroke(Path(ellipseIn: slotRect), with: .color(Theme.brandPurple.opacity(0.85)), lineWidth: 2.5)
                }
                .frame(width: dialRadius, height: dialRadius * 2)
                
                // 3. Liquid Frosted Center Hub
                ZStack {
                    // Soft glow behind hub
                    Circle()
                        .fill(Theme.brandPurple.opacity(0.3))
                        .frame(width: hubRadius * 2.3, height: hubRadius * 2.3)
                        .blur(radius: 8)
                    
                    // Hub lens
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Theme.brandPurple,
                                    Color(hex: "#7C3AED")
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .overlay(
                            Circle()
                                .stroke(
                                    LinearGradient(
                                        colors: [.white.opacity(0.6), .white.opacity(0.1)],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    ),
                                    lineWidth: 1.5
                                )
                        )
                        .shadow(color: Theme.brandPurple.opacity(0.4), radius: 8, x: -2, y: 0)
                    
                    // Subtle inner indicator glyph / dot
                    Circle()
                        .fill(Color.white.opacity(0.9))
                        .frame(width: 8, height: 8)
                        .offset(x: -hubRadius * 0.45)
                }
                .frame(width: hubRadius * 2, height: hubRadius * 2)
                .offset(x: hubRadius) // docked flush onto right edge
                
                // 4. Fisheye Magnification Tool Chips along the Arc
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    let deltaIndex = Double(index) - currentPosition
                    let angle = deltaIndex * stepAngle
                    let onArc = abs(angle) < (.pi / 2.0 - 0.08)
                    
                    if onArc {
                        // Fisheye magnification dock physics:
                        // As angle approaches 0 (the 9 o'clock active slot), item smoothly scales up from 0.92 to 1.28
                        let proximity = max(0, 1.0 - abs(angle) / (stepAngle * 2.8))
                        let scale = 0.90 + 0.38 * pow(proximity, 1.6)
                        let isMiddle = index == activeIndex
                        
                        // Distance from center along radial curve
                        let radialDistance = ringRadius + (isMiddle ? 2.0 : 0.0)
                        let x = dialRadius - radialDistance * CGFloat(cos(angle))
                        let y = dialRadius + radialDistance * CGFloat(sin(angle))
                        
                        // Liquid opacity fade toward edges
                        let edgeFade = 1.0 - 0.35 * min(1.0, abs(angle) / (.pi / 2.0))
                        
                        Button(action: {
                            triggerHaptic()
                            withAnimation(.spring(response: 0.36, dampingFraction: 0.74)) {
                                rotation = Double(index)
                                dragOffset = 0
                            }
                            onPick(item, true)
                        }) {
                            ZStack {
                                // Liquid Chip Background
                                Circle()
                                    .fill(isMiddle ? Color.white : Color.white.opacity(0.85))
                                    .overlay(
                                        Circle()
                                            .stroke(
                                                LinearGradient(
                                                    colors: isMiddle ?
                                                        [Theme.brandPurple.opacity(0.8), Theme.brandPurple.opacity(0.3)] :
                                                        [.white.opacity(0.9), .white.opacity(0.3)],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                ),
                                                lineWidth: isMiddle ? 2 : 1
                                            )
                                    )
                                    .shadow(
                                        color: isMiddle ?
                                            Theme.brandPurple.opacity(0.25) :
                                            Color.black.opacity(0.08),
                                        radius: isMiddle ? 8 : 3,
                                        x: 0,
                                        y: isMiddle ? 3 : 1
                                    )
                                
                                // Tool SF Symbol Icon
                                Image(systemName: item.iconName)
                                    .font(.system(
                                        size: isMiddle ? 19 : 15,
                                        weight: isMiddle ? .bold : .semibold,
                                        design: .rounded
                                    ))
                                    .foregroundColor(isMiddle ? Theme.brandPurple : Color(hex: "#334155"))
                                    .scaleEffect(isMiddle ? 1.08 : 1.0)
                            }
                            .frame(width: baseItemSize, height: baseItemSize)
                            .scaleEffect(scale)
                            .opacity(edgeFade)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .position(x: x, y: y)
                        .zIndex(isMiddle ? 20 : Double(10 - abs(deltaIndex)))
                    }
                }
            }
            .frame(width: dialRadius, height: dialRadius * 2)
            .contentShape(HalfCircleShape())
            .gesture(
                DragGesture(minimumDistance: 3)
                    .onChanged { gesture in
                        // Elastic rubber-banding if dragging beyond ends
                        let rawDelta = gesture.translation.height / (ringRadius * CGFloat(stepAngle))
                        let projected = rotation - Double(rawDelta)
                        
                        if projected < 0 {
                            let excess = -projected
                            let damped = excess / (1.0 + excess * 0.3)
                            dragOffset = Double(gesture.translation.height / (ringRadius * CGFloat(stepAngle))) * (damped / excess)
                        } else if projected > Double(items.count - 1) {
                            let excess = projected - Double(items.count - 1)
                            let damped = excess / (1.0 + excess * 0.3)
                            dragOffset = Double(gesture.translation.height / (ringRadius * CGFloat(stepAngle))) * (damped / excess)
                        } else {
                            dragOffset = Double(rawDelta)
                        }
                        
                        // Check if detent index changed -> fire crisp iOS haptic
                        let currentIdx = activeIndex
                        if currentIdx != lastHapticIndex {
                            triggerHaptic()
                            lastHapticIndex = currentIdx
                        }
                    }
                    .onEnded { gesture in
                        // Continuous iOS inertia momentum decay
                        let velocity = gesture.predictedEndTranslation.height - gesture.translation.height
                        let flingSlots = Double(velocity) / (Double(ringRadius) * stepAngle * 3.5)
                        let target = round(rotation - dragOffset - flingSlots)
                        let clamped = min(max(0, Int(target)), items.count - 1)
                        
                        triggerHaptic()
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.72)) {
                            rotation = Double(clamped)
                            dragOffset = 0
                            lastHapticIndex = clamped
                        }
                        onPick(items[clamped], false)
                    }
            )
        }
        .onAppear {
            feedbackGenerator.prepare()
            if let idx = items.firstIndex(of: selectedTool) {
                rotation = Double(idx)
                lastHapticIndex = idx
            }
        }
    }
    
    private func triggerHaptic() {
        feedbackGenerator.selectionChanged()
    }
}

// MARK: - Half-Circle Shape Flush with Edge

public struct HalfCircleShape: Shape {
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.maxX, y: rect.midY)
        let radius = rect.width
        
        path.move(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addArc(
            center: center,
            radius: radius,
            startAngle: .degrees(270),
            endAngle: .degrees(90),
            clockwise: true
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

// MARK: - Liquid Native iOS Collapsed Tool Rail

public struct CollapsedToolBar: View {
    public let activeTool: DialToolItem
    public let activeColorHex: String
    public var onOpenDial: () -> Void
    public var onToggleColorPalette: () -> Void
    public var onUndo: () -> Void
    public var onRedo: () -> Void
    public var onClearPage: () -> Void
    public var onToggleStylusOnly: () -> Void
    public var isStylusOnly: Bool
    public var onDone: () -> Void
    
    public var body: some View {
        VStack(spacing: 8) {
            // 1. Active Tool Glass Lens Button (tap to spin liquid dial)
            Button(action: onOpenDial) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Theme.brandPurpleSoft,
                                    Theme.brandPurpleSoft.opacity(0.6)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Theme.brandPurple.opacity(0.3), lineWidth: 1)
                        )
                        .frame(width: 44, height: 44)
                        .shadow(color: Theme.brandPurple.opacity(0.18), radius: 6, x: 0, y: 2)
                    
                    Image(systemName: activeTool.iconName)
                        .font(.system(size: 19, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.brandPurple)
                }
            }
            .buttonStyle(LiquidScaleButtonStyle())
            
            // 2. Liquid Color Pearl Swatch
            if activeTool.hasColor {
                Button(action: onToggleColorPalette) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: activeColorHex))
                            .frame(width: 26, height: 26)
                            .overlay(
                                Circle()
                                    .stroke(
                                        LinearGradient(
                                            colors: [.white, .white.opacity(0.5)],
                                            startPoint: .top,
                                            endPoint: .bottom
                                        ),
                                        lineWidth: 2
                                    )
                            )
                            .overlay(
                                // Specular glass gleam
                                Ellipse()
                                    .fill(Color.white.opacity(0.45))
                                    .frame(width: 12, height: 6)
                                    .offset(y: -6)
                            )
                            .shadow(color: Color(hex: activeColorHex).opacity(0.35), radius: 4, x: 0, y: 2)
                    }
                    .frame(width: 38, height: 38)
                }
                .buttonStyle(LiquidScaleButtonStyle())
            }
            
            // Elegant Frosted Glass Hairline Divider
            Rectangle()
                .fill(Color(hex: "#CBD5E1").opacity(0.4))
                .frame(width: 28, height: 1)
                .padding(.vertical, 2)
            
            // 3. Undo Glass Button
            Button(action: onUndo) {
                Image(systemName: "arrow.uturn.backward")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Theme.appTextPrimary)
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(LiquidScaleButtonStyle())
            
            // 4. Redo Glass Button
            Button(action: onRedo) {
                Image(systemName: "arrow.uturn.forward")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Theme.appTextPrimary)
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(LiquidScaleButtonStyle())
            
            // 5. Overflow Actions Menu (...)
            Menu {
                Button(role: .destructive, action: onClearPage) {
                    Label("Clear Page Drawings", systemImage: "trash")
                }
                Button(action: onToggleStylusOnly) {
                    Label(isStylusOnly ? "Stylus Only: On" : "Stylus Only: Off", systemImage: "applepencil")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Theme.appTextSecondary)
                    .frame(width: 38, height: 38)
            }
            
            // Elegant Frosted Glass Hairline Divider
            Rectangle()
                .fill(Color(hex: "#CBD5E1").opacity(0.4))
                .frame(width: 28, height: 1)
                .padding(.vertical, 2)
            
            // 6. Liquid Done Button (Apple-style spring action)
            Button(action: onDone) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Theme.brandPurple,
                                    Color(hex: "#7C3AED")
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .overlay(
                            Circle()
                                .stroke(
                                    LinearGradient(
                                        colors: [.white.opacity(0.6), .white.opacity(0.1)],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    ),
                                    lineWidth: 1.5
                                )
                        )
                        .frame(width: 40, height: 40)
                        .shadow(color: Theme.brandPurple.opacity(0.38), radius: 6, x: 0, y: 3)
                    
                    Image(systemName: "checkmark")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .buttonStyle(LiquidScaleButtonStyle())
        }
        .padding(.vertical, 12)
        .frame(width: 54)
        .background(
            UnevenRoundedRectangle(
                topLeadingRadius: 27,
                bottomLeadingRadius: 27,
                bottomTrailingRadius: 0,
                topTrailingRadius: 0
            )
            .fill(.ultraThinMaterial)
            .overlay(
                UnevenRoundedRectangle(
                    topLeadingRadius: 27,
                    bottomLeadingRadius: 27,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: 0
                )
                .stroke(
                    LinearGradient(
                        colors: [.white.opacity(0.85), .white.opacity(0.2)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.5
                )
            )
            .shadow(color: Color.black.opacity(0.12), radius: 14, x: -4, y: 0)
        )
    }
}

// MARK: - Liquid Flyout Color Palette

public struct RailFlyoutColorCard: View {
    @Binding public var selectedColorHex: String
    public var onSelect: (String) -> Void
    
    public static let palette = [
        "#FFE600", // Yellow
        "#4ADE80", // Green
        "#38BDF8", // Sky Blue
        "#F472B6", // Pink
        "#A78BFA", // Light Purple
        "#5935FF", // Brand Purple
        "#EF4444", // Red
        "#F97316", // Orange
        "#101326", // Dark Charcoal
        "#6B7280"  // Slate Grey
    ]
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("INK COLOR")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundColor(Theme.appTextSecondary)
                .tracking(0.8)
            
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(32), spacing: 10), count: 5), spacing: 10) {
                ForEach(Self.palette, id: \.self) { hex in
                    let isSelected = selectedColorHex.lowercased() == hex.lowercased()
                    Button(action: {
                        UISelectionFeedbackGenerator().selectionChanged()
                        selectedColorHex = hex
                        onSelect(hex)
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color(hex: hex))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Circle()
                                        .stroke(
                                            LinearGradient(
                                                colors: [.white, .white.opacity(0.4)],
                                                startPoint: .top,
                                                endPoint: .bottom
                                            ),
                                            lineWidth: isSelected ? 2.5 : 1
                                        )
                                )
                                .overlay(
                                    // Specular Pearl Gleam
                                    Ellipse()
                                        .fill(Color.white.opacity(0.45))
                                        .frame(width: 14, height: 7)
                                        .offset(y: -8)
                                )
                                .shadow(
                                    color: isSelected ?
                                        Color(hex: hex).opacity(0.45) :
                                        Color.black.opacity(0.1),
                                    radius: isSelected ? 6 : 2,
                                    x: 0,
                                    y: isSelected ? 2 : 1
                                )
                            
                            if isSelected {
                                Circle()
                                    .stroke(Color.white, lineWidth: 2)
                                    .frame(width: 22, height: 22)
                            }
                        }
                        .scaleEffect(isSelected ? 1.12 : 1.0)
                        .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isSelected)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(
                            LinearGradient(
                                colors: [.white.opacity(0.85), .white.opacity(0.2)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.5
                        )
                )
                .shadow(color: Color.black.opacity(0.16), radius: 16, x: 0, y: 6)
        )
    }
}

// MARK: - Liquid Spring Button Style

public struct LiquidScaleButtonStyle: ButtonStyle {
    public init() {}
    
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.90 : 1.0)
            .opacity(configuration.isPressed ? 0.85 : 1.0)
            .animation(.spring(response: 0.22, dampingFraction: 0.65), value: configuration.isPressed)
    }
}
