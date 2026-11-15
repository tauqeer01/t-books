import SwiftUI

public enum Theme {
    // Brand Colors (matching Android BookFlow / AHT Books palette)
    public static let brandPurple = Color(hex: "#5935FF")
    public static let brandPurpleLight = Color(hex: "#6366F1")
    public static let brandPurpleSoft = Color(hex: "#ECEBFA")
    public static let brandPurpleDark = Color(hex: "#3730A3")
    
    // Backgrounds & Neutrals
    public static let appBackground = Color(hex: "#F8F8FF")
    public static let appSurface = Color.white
    public static let appSurfaceVariant = Color(hex: "#F1F5F9")
    public static let appTextPrimary = Color(hex: "#101326")
    public static let appTextSecondary = Color(hex: "#666B85")
    public static let appDivider = Color(hex: "#E2E8F0")
    
    // Pastel Collection Colors
    public static let pastelPurple = Color(hex: "#EDE9FE")
    public static let pastelMint = Color(hex: "#DCFCE7")
    public static let pastelPink = Color(hex: "#FFE4E6")
    public static let pastelYellow = Color(hex: "#FEF3C7")
    public static let pastelGreen = Color(hex: "#D1FAE5")
    public static let pastelOrange = Color(hex: "#FFEDD5")
    public static let pastelBlue = Color(hex: "#DBEAFE")
    
    // Annotation Highlight Swatches
    public static let highlightYellow = Color(hex: "#FFE600")
    public static let highlightGreen = Color(hex: "#4ADE80")
    public static let highlightBlue = Color(hex: "#38BDF8")
    public static let highlightPink = Color(hex: "#F472B6")
    public static let highlightPurple = Color(hex: "#A78BFA")
    public static let annotationRed = Color(hex: "#EF4444")
    
    public static let highlightColors: [Color] = [
        highlightYellow, highlightGreen, highlightBlue, highlightPink, highlightPurple
    ]
    
    public static let highlightHexes: [String] = [
        "#FFE600", "#4ADE80", "#38BDF8", "#F472B6", "#A78BFA"
    ]
}

extension Color {
    public init(hex: String) {
        let cleanHex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: cleanHex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch cleanHex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: Double(a) / 255.0
        )
    }
    
    public func toHex() -> String {
        guard let components = UIColor(self).cgColor.components, components.count >= 3 else {
            return "#5935FF"
        }
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        return String(format: "#%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255))
    }
}
