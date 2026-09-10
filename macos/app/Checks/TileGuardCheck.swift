import AppKit

/// Checks the tile loader refuses to fetch while map previews are off, whatever a view asks.
@main
struct TileGuardCheck {
    static func main() async {
        let tile = MapLayout.Tile(x: 0, y: 0, zoom: 1, offset: .zero)

        await TileLoader.shared.setEnabled(false)
        let whenOff = await TileLoader.shared.tile(tile)
        print("with previews off: \(whenOff == nil ? "no tile fetched" : "FETCHED — LEAK")")

        await TileLoader.shared.setEnabled(true)
        let whenOn = await TileLoader.shared.tile(tile)
        print("with previews on:  \(whenOn == nil ? "no tile (offline?)" : "tile fetched")")

        await TileLoader.shared.setEnabled(false)
        let afterOff = await TileLoader.shared.tile(tile)
        print("turned off again:  \(afterOff == nil ? "no tile, cache cleared" : "SERVED FROM CACHE — LEAK")")
    }
}
