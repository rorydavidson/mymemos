import SwiftUI
import Shared

/// A map made of OpenStreetMap tiles.
///
/// No MapKit, matching the Android app: tiles come straight from openstreetmap.org, and only
/// when the user has turned them on. That switch is not
/// decoration. Drawing a memo's location asks OSM for the tiles around it, which tells them
/// roughly where the memo was written, so with tiles off nothing is drawn and nothing leaves
/// the machine.
struct TileMapView: View {
    let points: [MapPoint]
    var showsRoute = false
    var height: CGFloat = 180

    var body: some View {
        GeometryReader { geometry in
            let layout = MapLayout(points: points, size: geometry.size)
            ZStack(alignment: .topLeading) {
                Theme.canvas
                ForEach(layout.tiles, id: \.id) { tile in
                    TileImage(tile: tile)
                        .frame(width: MapLayout.tileSize, height: MapLayout.tileSize)
                        .offset(x: tile.offset.x, y: tile.offset.y)
                }
                if showsRoute, layout.screenPoints.count > 1 {
                    Path { path in
                        path.move(to: layout.screenPoints[0])
                        for point in layout.screenPoints.dropFirst() { path.addLine(to: point) }
                    }
                    .stroke(Theme.accent, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                }
                ForEach(Array(layout.screenPoints.enumerated()), id: \.offset) { index, point in
                    Marker(number: showsRoute ? index + 1 : nil)
                        .position(point)
                }
            }
            .clipped()
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }
}

struct MapPoint: Equatable {
    let latitude: Double
    let longitude: Double
}

private struct Marker: View {
    let number: Int?

    var body: some View {
        ZStack {
            Circle().fill(Theme.accent)
            Circle().strokeBorder(.white, lineWidth: 1.5)
            if let number {
                Text("\(number)")
                    .font(Type.label)
                    .foregroundStyle(Theme.card)
            }
        }
        .frame(width: number == nil ? 12 : 18, height: number == nil ? 12 : 18)
        .shadow(color: .black.opacity(0.3), radius: 2, y: 1)
    }
}

/// Web Mercator, the projection every slippy map uses.
struct MapLayout {
    static let tileSize: CGFloat = 256

    struct Tile: Identifiable {
        let x: Int
        let y: Int
        let zoom: Int
        let offset: CGPoint
        var id: String { "\(zoom)/\(x)/\(y)" }
    }

    let tiles: [Tile]
    let screenPoints: [CGPoint]

    init(points: [MapPoint], size: CGSize) {
        guard !points.isEmpty, size.width > 0, size.height > 0 else {
            tiles = []
            screenPoints = []
            return
        }

        // The largest zoom at which every point still fits, with a margin so markers are not
        // pinned to the edge.
        let margin: CGFloat = 40
        var chosen = 1
        for zoom in stride(from: 18, through: 1, by: -1) {
            let projected = points.map { Self.project($0, zoom: zoom) }
            let spanX = (projected.map(\.x).max() ?? 0) - (projected.map(\.x).min() ?? 0)
            let spanY = (projected.map(\.y).max() ?? 0) - (projected.map(\.y).min() ?? 0)
            if spanX <= size.width - margin, spanY <= size.height - margin {
                chosen = zoom
                break
            }
        }

        let projected = points.map { Self.project($0, zoom: chosen) }
        let centreX = ((projected.map(\.x).min() ?? 0) + (projected.map(\.x).max() ?? 0)) / 2
        let centreY = ((projected.map(\.y).min() ?? 0) + (projected.map(\.y).max() ?? 0)) / 2
        let originX = centreX - size.width / 2
        let originY = centreY - size.height / 2

        screenPoints = projected.map { CGPoint(x: $0.x - originX, y: $0.y - originY) }

        let n = 1 << chosen
        let firstX = Int(floor(originX / Self.tileSize))
        let firstY = Int(floor(originY / Self.tileSize))
        let lastX = Int(floor((originX + size.width) / Self.tileSize))
        let lastY = Int(floor((originY + size.height) / Self.tileSize))

        var found: [Tile] = []
        for x in firstX...lastX {
            for y in firstY...lastY where y >= 0 && y < n {
                found.append(
                    Tile(
                        x: ((x % n) + n) % n,
                        y: y,
                        zoom: chosen,
                        offset: CGPoint(
                            x: CGFloat(x) * Self.tileSize - originX,
                            y: CGFloat(y) * Self.tileSize - originY
                        )
                    )
                )
            }
        }
        tiles = found
    }

    /// Latitude and longitude to pixels at a zoom level.
    static func project(_ point: MapPoint, zoom: Int) -> CGPoint {
        let n = Double(1 << zoom)
        let x = (point.longitude + 180.0) / 360.0 * n * Double(tileSize)
        let latRad = point.latitude * .pi / 180.0
        let y = (1.0 - log(tan(latRad) + 1 / cos(latRad)) / .pi) / 2.0 * n * Double(tileSize)
        return CGPoint(x: x, y: y)
    }
}

/// One tile, fetched and cached in memory for the session.
private struct TileImage: View {
    let tile: MapLayout.Tile
    @State private var image: PlatformImage?

    var body: some View {
        Group {
            if let image {
                Image(platform: image).resizable()
            } else {
                Theme.hairline.opacity(0.3)
            }
        }
        .task(id: tile.id) { image = await TileLoader.shared.tile(tile) }
    }
}

/// Fetches tiles from openstreetmap.org.
///
/// Their usage policy asks for an identifying User-Agent and no bulk downloading, so this
/// sends one and keeps what it has already fetched rather than asking twice.
///
/// The setting is enforced here rather than only at the call sites. A view that forgot to
/// check would otherwise quietly start telling openstreetmap.org where memos were written,
/// and that is not a mistake worth leaving available: this is the only code in the app that
/// fetches a tile, so this is where the guarantee belongs.
actor TileLoader {
    static let shared = TileLoader()

    #if os(macOS)
    private static let platform = "macOS"
    #else
    private static let platform = "iOS"
    #endif

    private var cache: [String: PlatformImage] = [:]
    private var enabled = false

    func setEnabled(_ value: Bool) {
        enabled = value
        if !value { cache.removeAll() }
    }

    /// Exposed so the guarantee can be checked rather than assumed.
    func isEnabled() -> Bool { enabled }

    func tile(_ tile: MapLayout.Tile) async -> PlatformImage? {
        guard enabled else { return nil }
        if let cached = cache[tile.id] { return cached }
        guard let url = URL(string: "https://tile.openstreetmap.org/\(tile.zoom)/\(tile.x)/\(tile.y).png")
        else { return nil }

        var request = URLRequest(url: url)
        request.setValue("MyMemos/0.1 (\(Self.platform); https://github.com/usememos/memos)", forHTTPHeaderField: "User-Agent")
        guard
            let (data, _) = try? await URLSession.shared.data(for: request),
            let image = PlatformImage(data: data)
        else { return nil }

        cache[tile.id] = image
        return image
    }
}

/// Shown in place of a map when tiles are turned off.
struct MapTilesOffNotice: View {
    let enable: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "map").font(.system(size: 16)).foregroundStyle(Theme.inkSoft)
            VStack(alignment: .leading, spacing: 3) {
                Text("Map previews are off").font(Type.rowTitle)
                Text("Drawing a map asks openstreetmap.org for the tiles around a memo, which tells them roughly where it was written.")
                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 12)
            Button("Turn On", action: enable)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }
}
