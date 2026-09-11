import SwiftUI
#if os(macOS)
import AppKit
/// The image type each platform draws with. Bytes from the shared layer become one of these.
typealias PlatformImage = NSImage
#else
import UIKit
typealias PlatformImage = UIImage
#endif

// The handful of places where AppKit and UIKit want different words for the same thing.
// Kept together so a view can read as one view and the differences are easy to find.

extension Image {
    init(platform image: PlatformImage) {
        #if os(macOS)
        self.init(nsImage: image)
        #else
        self.init(uiImage: image)
        #endif
    }
}

extension View {
    /// A button that reads as a link. macOS has a style for it; iOS's borderless is the nearest.
    @ViewBuilder func linkButton() -> some View {
        #if os(macOS)
        buttonStyle(.link)
        #else
        buttonStyle(.borderless)
        #endif
    }

    /// A fixed width for a sheet on the Mac. A phone's sheet takes the screen and needs none.
    @ViewBuilder func sheetWidth(_ width: CGFloat) -> some View {
        #if os(macOS)
        frame(width: width)
        #else
        frame(maxWidth: .infinity)
        #endif
    }

    /// A card that is a fixed width in a window and fills a phone's screen up to that width.
    @ViewBuilder func cardWidth(_ width: CGFloat) -> some View {
        #if os(macOS)
        frame(width: width)
        #else
        frame(maxWidth: width)
        #endif
    }
}

/// How the platform says "make a new memo", for empty states.
enum Hint {
    #if os(macOS)
    static let newMemo = "Press ⌘N to write a new one."
    static let firstMemo = "Press ⌘N to write the first one."
    #else
    static let newMemo = "Tap the pencil to write a new one."
    static let firstMemo = "Tap the pencil to write the first one."
    #endif
}
