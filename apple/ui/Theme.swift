import SwiftUI

/// The app's visual language, in one place.
///
/// The palette is the Android app's, taken from its Material scheme rather than approximated,
/// so the two read as the same product: a warm paper ground, a deep green accent, and ink that
/// is brown-black rather than pure black. The type is Google Sans Flex, the same file Android
/// ships, bundled under the SIL Open Font License.
enum Theme {

    // MARK: Surfaces, from the Android colour scheme

    /// background / surface
    static let canvas = adaptive(light: 0xF1ECE4, dark: 0x15130F)
    /// surfaceContainerLowest, what a card sits on
    static let card = adaptive(light: 0xFFFFFF, dark: 0x1C1A15)
    /// surfaceContainerHigh, for a raised or selected row
    static let raised = adaptive(light: 0xE7E1D8, dark: 0x2A2721)
    /// outlineVariant
    static let hairline = adaptive(light: 0xDCD4C9, dark: 0x39352E)

    // MARK: Ink

    /// onBackground
    static let ink = adaptive(light: 0x1E1B17, dark: 0xEDE7DF)
    /// onSurfaceVariant
    static let inkSoft = adaptive(light: 0x5B5650, dark: 0xC5BEB4)

    /// primary
    static let accent = adaptive(light: 0x1F6F5C, dark: 0x8ED9C2)
    /// primaryContainer, for a tinted chip behind accent text
    static let accentSoft = adaptive(light: 0xCDEBE0, dark: 0x1B5446)
    /// onPrimaryContainer
    static let onAccentSoft = adaptive(light: 0x0B2E25, dark: 0xCDEBE0)
    /// secondary, for pins and anything warm
    static let warm = adaptive(light: 0x8A5A2B, dark: 0xE6BE95)
    /// error
    static let danger = adaptive(light: 0xB3261E, dark: 0xF2B8B5)

    // MARK: Metrics

    static let cardRadius: CGFloat = 12
    static let readingWidth: CGFloat = 660
    static let readingLeading: CGFloat = 7

    private static func adaptive(light: Int, dark: Int) -> Color {
        #if os(macOS)
        Color(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
            return NSColor(hex: isDark ? dark : light)
        })
        #else
        Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
        #endif
    }
}

/// The type scale, on Google Sans Flex.
///
/// Sizes are set explicitly rather than taken from the platform's text styles, which are tuned
/// for controls: 13pt with control leading is a list row, not a page. Reading sizes and chrome
/// sizes are kept apart on purpose.
enum Type {
    private static let family = "Google Sans Flex"

    /// Falls back to the system font if the bundled file ever fails to register, so a missing
    /// font is a slightly plainer app rather than no text at all.
    private static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        registered
            ? .custom(family, size: size).weight(weight)
            : .system(size: size, weight: weight)
    }

    private static var registered: Bool {
        #if os(macOS)
        NSFont(name: family, size: 12) != nil
        #else
        UIFont(name: family, size: 12) != nil
        #endif
    }

    // Reading: the memo itself.
    static let body = sans(15)
    static let title = sans(27, .semibold)
    static let heading1 = sans(21, .semibold)
    static let heading2 = sans(18, .semibold)
    static let heading3 = sans(16, .semibold)
    static let code = Font.system(size: 13, design: .monospaced)
    static let quote = sans(15)

    // Chrome.
    static let rowTitle = sans(13.5, .medium)
    static let rowBody = sans(12.5)
    static let rowMeta = sans(11)
    static let sectionHeader = sans(10.5, .semibold)
    static let sidebar = sans(13)
    static let numeral = sans(32, .semibold)
    static let label = sans(9.5, .semibold)
}

#if os(macOS)
extension NSColor {
    convenience init(hex: Int) {
        self.init(
            srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
#else
extension UIColor {
    convenience init(hex: Int) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
#endif

extension Color {
    /// A memo's tint, softened into something a page of text can sit on.
    ///
    /// The stored value is one of the sixteen basic web colours, which at full strength is
    /// unreadable behind text. This is the same pastel treatment the Android app applies,
    /// adjusted so it survives dark mode rather than glowing.
    static func memoTint(_ hex: Int64, isDark: Bool) -> Color? {
        guard hex >= 0 else { return nil }
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        return isDark
            ? Color(red: r * 0.22 + 0.09, green: g * 0.22 + 0.09, blue: b * 0.22 + 0.08)
            : Color(red: r * 0.14 + 0.86, green: g * 0.14 + 0.86, blue: b * 0.14 + 0.84)
    }
}
