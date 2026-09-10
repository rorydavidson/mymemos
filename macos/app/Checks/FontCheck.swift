import AppKit
import CoreText

/// Checks the bundled font is actually usable and named what the theme asks for.
///
/// Worth checking because the fallback is silent: `Type` drops to the system font when the
/// family will not resolve, so a font that failed to register looks like a slightly plainer
/// app rather than an error, and the whole point of bundling it goes quietly missing.
@main
struct FontCheck {
    static func main() {
        let family = "Google Sans Flex"
        let app = "macos/app/build/MyMemos.app"
        var failures = 0

        // 1. The bundle declares where its fonts live, and they are there.
        let plist = "\(app)/Contents/Info.plist"
        let declared = shell("/usr/libexec/PlistBuddy", ["-c", "Print :ATSApplicationFontsPath", plist])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        print(declared == "Fonts" ? "ok   Info.plist points at Contents/Resources/Fonts"
                                  : "FAIL Info.plist ATSApplicationFontsPath is \"\(declared)\"")
        if declared != "Fonts" { failures += 1 }

        let ttf = "\(app)/Contents/Resources/Fonts/GoogleSansFlex.ttf"
        let bundled = FileManager.default.fileExists(atPath: ttf)
        print(bundled ? "ok   the font file is in the bundle" : "FAIL no font file at \(ttf)")
        if !bundled { failures += 1 }

        // 2. The licence travels with it, as the OFL requires.
        let licence = FileManager.default.fileExists(atPath: "\(app)/Contents/Resources/GOOGLE_SANS_FLEX_OFL.txt")
        print(licence ? "ok   the OFL licence is in the bundle" : "FAIL the licence is missing")
        if !licence { failures += 1 }

        // 3. Registering it makes the family the theme asks for resolvable. Before registering
        //    it must not resolve, or this check would pass on a system that happens to have it
        //    installed and prove nothing about the bundle.
        let before = NSFont(name: family, size: 13)
        print(before == nil ? "ok   control: the family is not already installed"
                            : "note the family is installed system-wide, so this is a weaker check")

        guard bundled, let url = URL(string: "file://" + FileManager.default.currentDirectoryPath + "/" + ttf) else {
            report(failures + 1)
            return
        }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        let after = NSFont(name: family, size: 13)
        print(after != nil ? "ok   \"\(family)\" resolves once registered"
                           : "FAIL \"\(family)\" will not resolve, so the theme falls back")
        if after == nil { failures += 1 }

        report(failures)
    }

    static func report(_ failures: Int) {
        print(failures == 0 ? "\nfont check passed" : "\n\(failures) failure(s)")
        exit(failures == 0 ? 0 : 1)
    }

    static func shell(_ command: String, _ arguments: [String]) -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: command)
        process.arguments = arguments
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = Pipe()
        try? process.run()
        process.waitUntilExit()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        return String(data: data, encoding: .utf8) ?? ""
    }
}
