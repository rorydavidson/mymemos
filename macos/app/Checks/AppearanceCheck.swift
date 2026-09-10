import AppKit

@main
struct AppearanceCheck {
    static func main() {
        var failures = 0

        for option in Appearance.allCases {
            let mapped = option.nsAppearance
            let expected: NSAppearance.Name?
            switch option {
            case .system: expected = nil
            case .light: expected = .aqua
            case .dark: expected = .darkAqua
            }
            let ok = mapped?.name == expected
            print(ok ? "ok   \(option.title) -> \(expected?.rawValue ?? "system")"
                     : "FAIL \(option.title) mapped to \(mapped?.name.rawValue ?? "nil")")
            if !ok { failures += 1 }
        }

        // Round trips through the raw value the model persists.
        for option in Appearance.allCases {
            let restored = Appearance(rawValue: option.rawValue)
            let ok = restored == option
            print(ok ? "ok   \(option.rawValue) round trips" : "FAIL \(option.rawValue) did not round trip")
            if !ok { failures += 1 }
        }

        // An unknown stored value must not crash or blank the app.
        let unknown = Appearance(rawValue: "sepia")
        print(unknown == nil ? "ok   an unknown stored value falls back rather than crashing"
                             : "FAIL unknown value produced \(unknown!.title)")

        print(failures == 0 ? "\nappearance check passed" : "\n\(failures) failure(s)")
        exit(failures == 0 ? 0 : 1)
    }
}
