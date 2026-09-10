import SwiftUI

/// The app's visual language, in one place.
///
/// Warm and paper-like rather than the default grey: these are notebooks, and the Android app
/// already reads that way. Everything below adapts to light and dark rather than being pinned
/// to one, because a Mac app that ignores the system appearance looks broken.
enum Theme {

    // MARK: Surfaces

    static let canvas = Color("canvas", bundle: nil, light: Color(red: 0.96, green: 0.95, blue: 0.93),
                             dark: Color(red: 0.11, green: 0.11, blue: 0.10))
    static let card = Color("card", bundle: nil, light: .white,
                            dark: Color(red: 0.16, green: 0.16, blue: 0.15))
    static let hairline = Color("hairline", bundle: nil, light: Color.black.opacity(0.08),
                                dark: Color.white.opacity(0.10))

    // MARK: Ink

    static let ink = Color("ink", bundle: nil, light: Color(red: 0.12, green: 0.11, blue: 0.10),
                           dark: Color(red: 0.93, green: 0.92, blue: 0.90))
    static let inkSoft = Color("inkSoft", bundle: nil, light: Color(red: 0.42, green: 0.40, blue: 0.38),
                               dark: Color(red: 0.66, green: 0.64, blue: 0.62))

    /// The one colour that means "this app", used for the accent and little else.
    static let accent = Color(red: 0.12, green: 0.42, blue: 0.35)

    // MARK: Metrics

    static let cardRadius: CGFloat = 10
    static let rowSpacing: CGFloat = 6
}

extension Color {
    /// A colour that follows the system appearance without needing an asset catalogue, which a
    /// script-built bundle does not have.
    init(_ name: String, bundle: Bundle?, light: Color, dark: Color) {
        self = Color(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
            return NSColor(isDark ? dark : light)
        })
    }

    /// A memo's tint, softened into something a page can sit on.
    static func memoTint(_ hex: Int64, isDark: Bool) -> Color? {
        guard hex >= 0 else { return nil }
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        // A full-strength web colour behind text is unreadable; this is the same pastel idea
        // the Android app uses, adjusted so it survives dark mode.
        return isDark
            ? Color(red: r * 0.30 + 0.10, green: g * 0.30 + 0.10, blue: b * 0.30 + 0.10)
            : Color(red: r * 0.18 + 0.82, green: g * 0.18 + 0.82, blue: b * 0.18 + 0.82)
    }
}
