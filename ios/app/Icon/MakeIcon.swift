import AppKit
import CoreGraphics

/// Draws the iOS app icon: the Android launcher artwork on the app's green, filling the
/// square, since iOS applies its own corner mask at display time. The macOS icon is the same
/// drawing on an inset plate; see macos/app/Icon.
@main
struct MakeIcon {

    // The Android drawable's own numbers, so the proportions carry across exactly.
    static let androidViewport: CGFloat = 108
    /// The part of that viewport Android actually shows once masked.
    static let androidSafeZone: CGFloat = 72

    static let green = CGColor(srgbRed: 0x1F / 255, green: 0x6F / 255, blue: 0x5C / 255, alpha: 1)
    static let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)

    static func main() {
        let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "icon-1024.png"
        guard let data = render(size: 1024) else {
            FileHandle.standardError.write("could not render\n".data(using: .utf8)!)
            exit(1)
        }
        try! data.write(to: URL(fileURLWithPath: out))
    }

    static func render(size: CGFloat) -> Data? {
        guard let context = CGContext(
            data: nil,
            width: Int(size), height: Int(size),
            bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }

        context.setShouldAntialias(true)
        context.setFillColor(green)
        context.fill(CGRect(x: 0, y: 0, width: size, height: size))

        // Android's masked area becomes 80% of the square, which leaves the card the same
        // breathing room the Mac's plate gives it.
        let scale = size * 0.8 / androidSafeZone
        let origin = CGPoint(x: size / 2 - androidViewport / 2 * scale, y: size / 2 - androidViewport / 2 * scale)
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: origin.x + x * scale, y: origin.y + (androidViewport - y) * scale)
        }
        func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
            let topLeft = point(x, y)
            return CGRect(x: topLeft.x, y: topLeft.y - h * scale, width: w * scale, height: h * scale)
        }

        let card = rect(34, 30, 40, 48)
        context.addPath(CGPath(roundedRect: card, cornerWidth: 4 * scale, cornerHeight: 4 * scale, transform: nil))
        context.setFillColor(white)
        context.fillPath()

        context.setFillColor(green)
        for (y, width) in [(CGFloat(42), CGFloat(28)), (52, 28), (62, 18)] {
            let line = rect(40, y, width, 4)
            let lineRadius = min(2 * scale, line.height / 2)
            context.addPath(CGPath(roundedRect: line, cornerWidth: lineRadius, cornerHeight: lineRadius, transform: nil))
        }
        context.fillPath()

        guard let image = context.makeImage() else { return nil }
        return NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])
    }
}
