import AppKit
import CoreGraphics

/// Draws the app icon and writes an iconset.
///
/// The artwork is the Android launcher icon's: a white rounded square with three lines on the
/// app's green, from `ic_launcher_foreground.xml` and `ic_launcher_background`. It is redrawn
/// rather than exported, because the two platforms want different shapes. Android masks a
/// 108dp square down to its own shape at display time; macOS expects a rounded rectangle
/// inset within the canvas, so scaling the Android file up would give an icon that either
/// filled the square like nothing else in the Dock, or floated inside two sets of margins.
@main
struct MakeIcon {

    // The Android drawable's own numbers, so the proportions carry across exactly.
    static let androidViewport: CGFloat = 108
    /// The part of that viewport Android actually shows once masked.
    static let androidSafeZone: CGFloat = 72

    static let green = CGColor(srgbRed: 0x1F / 255, green: 0x6F / 255, blue: 0x5C / 255, alpha: 1)
    static let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)

    static func main() {
        let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "MyMemos.iconset"
        try? FileManager.default.createDirectory(atPath: out, withIntermediateDirectories: true)

        // The sizes an icns needs, as name plus pixel dimension.
        let wanted: [(String, Int)] = [
            ("icon_16x16", 16), ("icon_16x16@2x", 32),
            ("icon_32x32", 32), ("icon_32x32@2x", 64),
            ("icon_128x128", 128), ("icon_128x128@2x", 256),
            ("icon_256x256", 256), ("icon_256x256@2x", 512),
            ("icon_512x512", 512), ("icon_512x512@2x", 1024),
        ]

        for (name, size) in wanted {
            guard let data = render(size: CGFloat(size)) else { continue }
            try? data.write(to: URL(fileURLWithPath: "\(out)/\(name).png"))
        }
        print("wrote \(wanted.count) sizes to \(out)")
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
        context.interpolationQuality = .high

        // macOS's own icon grid: the rounded rectangle is 824 of 1024 with a 185.4 radius.
        // Everything below is that ratio, so it holds at every size.
        let plate = size * (824.0 / 1024.0)
        let inset = (size - plate) / 2
        let radius = plate * (185.4 / 824.0)
        let plateRect = CGRect(x: inset, y: inset, width: plate, height: plate)

        context.addPath(CGPath(roundedRect: plateRect, cornerWidth: radius, cornerHeight: radius, transform: nil))
        context.setFillColor(green)
        context.fillPath()

        // The Android artwork, mapped so its masked area fills the plate.
        let scale = plate / androidSafeZone
        let origin = CGPoint(
            x: plateRect.midX - androidViewport / 2 * scale,
            y: plateRect.midY - androidViewport / 2 * scale
        )
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            // The drawable's y runs downwards; Core Graphics runs up.
            CGPoint(x: origin.x + x * scale, y: origin.y + (androidViewport - y) * scale)
        }
        func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
            let topLeft = point(x, y)
            return CGRect(x: topLeft.x, y: topLeft.y - h * scale, width: w * scale, height: h * scale)
        }

        // M34,30h40a4,4 ... a 40x48 rounded card with a 4 unit radius.
        let card = rect(34, 30, 40, 48)
        context.addPath(CGPath(roundedRect: card, cornerWidth: 4 * scale, cornerHeight: 4 * scale, transform: nil))
        context.setFillColor(white)
        context.fillPath()

        // M40,42h28v4 ... three lines, the last one short.
        context.setFillColor(green)
        for (y, width) in [(CGFloat(42), CGFloat(28)), (52, 28), (62, 18)] {
            let line = rect(40, y, width, 4)
            let lineRadius = min(2 * scale, line.height / 2)
            context.addPath(CGPath(roundedRect: line, cornerWidth: lineRadius, cornerHeight: lineRadius, transform: nil))
        }
        context.fillPath()

        guard let image = context.makeImage() else { return nil }
        let rep = NSBitmapImageRep(cgImage: image)
        return rep.representation(using: .png, properties: [:])
    }
}
