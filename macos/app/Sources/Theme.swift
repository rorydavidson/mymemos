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

    /// A comfortable measure for prose. Much past this and the eye loses the line.
    static let readingWidth: CGFloat = 680

    /// Extra leading for body text. The default is tuned for controls, not paragraphs.
    static let readingLeading: CGFloat = 6
}

/// The type scale.
///
/// The first pass used the platform defaults everywhere, which is right for controls and far
/// too small for reading someone's writing: 13pt with default leading is a list row, not a
/// page. Reading sizes are set explicitly here, with the leading they need.
enum Type {

    // Reading: the memo itself.
    static let body = Font.system(size: 15)
    static let bodyLeading: CGFloat = 7
    static let title = Font.system(size: 26, weight: .semibold)
    static let heading1 = Font.system(size: 21, weight: .semibold)
    static let heading2 = Font.system(size: 18, weight: .semibold)
    static let heading3 = Font.system(size: 16, weight: .semibold)
    static let code = Font.system(size: 13.5, design: .monospaced)

    // Lists and chrome, where the platform's own sizes are right.
    static let rowTitle = Font.system(size: 13.5, weight: .semibold)
    static let rowBody = Font.system(size: 12.5)
    static let rowMeta = Font.system(size: 11)
    static let sectionHeader = Font.system(size: 11, weight: .semibold)
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
