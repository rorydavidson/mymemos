import SwiftUI
import Shared

/// Memos and the references between them, as a small force layout. Run once when the data
/// arrives, then still; tap a node to open the memo. The same idea as Android's GraphView.
struct GraphView: View {
    let graph: GraphData
    let open: (String) -> Void

    @State private var positions: [String: CGPoint] = [:]
    @State private var size = CGSize.zero

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Canvas { context, _ in
                    for edge in graph.edges {
                        guard let a = positions[edge.from], let b = positions[edge.to] else { continue }
                        var path = Path()
                        path.move(to: a)
                        path.addLine(to: b)
                        context.stroke(path, with: .color(Theme.inkSoft.opacity(0.5)), lineWidth: 1)
                    }
                }
                ForEach(graph.nodes, id: \.localId) { node in
                    if let point = positions[node.localId] {
                        Button { open(node.localId) } label: {
                            Text(node.locked ? "Locked" : node.title)
                                .font(Type.rowMeta)
                                .lineLimit(1)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(Theme.accentSoft))
                                .overlay(Capsule().strokeBorder(Theme.accent.opacity(0.6)))
                                .frame(maxWidth: 140)
                        }
                        .buttonStyle(.plain)
                        .position(point)
                    }
                }
            }
            .onAppear { layout(in: geometry.size) }
            .onChange(of: geometry.size) { _, new in layout(in: new) }
        }
        .frame(height: 320)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
    }

    /// Fruchterman-Reingold, a few hundred rounds, which is plenty for the dozens of nodes a
    /// personal graph has. Deterministic start so the picture is the same each time.
    private func layout(in area: CGSize) {
        guard area.width > 0, area.height > 0, !graph.nodes.isEmpty else { return }
        size = area
        let ids = graph.nodes.map(\.localId)
        var pos: [String: CGPoint] = [:]
        for (i, id) in ids.enumerated() {
            let angle = Double(i) / Double(ids.count) * 2 * .pi
            pos[id] = CGPoint(x: area.width / 2 + cos(angle) * area.width * 0.35, y: area.height / 2 + sin(angle) * area.height * 0.35)
        }
        let k = sqrt(area.width * area.height / CGFloat(max(1, ids.count))) * 0.9
        var temperature = area.width / 8
        for _ in 0..<200 {
            var disp: [String: CGVector] = [:]
            for a in ids {
                var d = CGVector.zero
                for b in ids where a != b {
                    let delta = CGVector(dx: pos[a]!.x - pos[b]!.x, dy: pos[a]!.y - pos[b]!.y)
                    let dist = max(0.01, hypot(delta.dx, delta.dy))
                    let force = k * k / dist
                    d.dx += delta.dx / dist * force
                    d.dy += delta.dy / dist * force
                }
                disp[a] = d
            }
            for edge in graph.edges {
                guard let a = pos[edge.from], let b = pos[edge.to] else { continue }
                let delta = CGVector(dx: a.x - b.x, dy: a.y - b.y)
                let dist = max(0.01, hypot(delta.dx, delta.dy))
                let force = dist * dist / k
                disp[edge.from]!.dx -= delta.dx / dist * force
                disp[edge.from]!.dy -= delta.dy / dist * force
                disp[edge.to]!.dx += delta.dx / dist * force
                disp[edge.to]!.dy += delta.dy / dist * force
            }
            for id in ids {
                let d = disp[id]!
                let len = max(0.01, hypot(d.dx, d.dy))
                let step = min(len, temperature)
                var p = pos[id]!
                p.x = min(area.width - 40, max(40, p.x + d.dx / len * step))
                p.y = min(area.height - 20, max(20, p.y + d.dy / len * step))
                pos[id] = p
            }
            temperature *= 0.97
        }
        positions = pos
    }
}
