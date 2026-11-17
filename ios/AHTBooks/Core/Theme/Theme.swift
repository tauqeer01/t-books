import SwiftUI

/// App-wide appearance, matching Android's AppThemeMode. Stored with @AppStorage(AppThemeMode.storageKey).
public enum AppThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    public static let storageKey = "app.themeMode"
    public var id: String { rawValue }
    public var displayName: String {
        switch self {
        case .system: return "System default"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }
    public var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

public enum Theme {
    /// A color that follows light/dark appearance (values mirror Android's light and dark color schemes).
    private static func adaptive(_ light: String, _ dark: String) -> Color {
        let lightColor = UIColor(Color(hex: light))
        let darkColor = UIColor(Color(hex: dark))
        return Color(UIColor { $0.userInterfaceStyle == .dark ? darkColor : lightColor })
    }

    // Brand Colors (matching Android BookFlow / AHT Books palette)
    public static let brandPurple = Color(hex: "#5935FF")
    public static let brandPurpleLight = Color(hex: "#6366F1")
    public static let brandPurpleSoft = adaptive("#ECEBFA", "#1E1B4B")
    public static let brandPurpleDark = adaptive("#3730A3", "#C7D2FE")

    // Backgrounds & Neutrals
    public static let appBackground = adaptive("#F8F8FF", "#0B1120")
    public static let appSurface = adaptive("#FFFFFF", "#151E2E")
    public static let appSurfaceVariant = adaptive("#F1F5F9", "#243044")
    public static let appTextPrimary = adaptive("#101326", "#F1F5F9")
    public static let appTextSecondary = adaptive("#666B85", "#A3B1C6")
    public static let appDivider = adaptive("#E2E8F0", "#2A3547")

    /// Text drawn on top of the fixed pastel collection colors, which stay light in both appearances.
    public static let onPastelPrimary = Color(hex: "#101326")
    public static let onPastelSecondary = Color(hex: "#4B5068")
    
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
