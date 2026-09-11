import SwiftUI
import Shared

/// Looking back: how long the writing streak is, what the year looks like, and what was
/// written on this day in earlier months and years.
struct ReviewView: View {
    @ObservedObject var model: SessionModel
    var openMemo: (String) -> Void
    @State private var journeyDay = ""
    @State private var showGraph = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                streakCard
                HeatmapView(activeDays: model.activeDays)
                DayReview(model: model, openMemo: openMemo)
                onThisDay
                #if os(iOS)
                NearbySection(model: model, openMemo: openMemo)
                #endif
                journey
                graphSection
            }
            .padding(24)
            .frame(maxWidth: Theme.readingWidth + 120, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .background(Theme.canvas)
        .task {
            await model.loadReview()
            await model.loadPlaces()
        }
    }

    private var streakCard: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(model.streak)")
                    .font(Type.numeral)
                    .foregroundStyle(Theme.accent)
                Text(model.streak == 1 ? "day in a row" : "days in a row")
                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
            }
            Divider().frame(height: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(model.activeDays.count)")
                    .font(Type.numeral)
                    .foregroundStyle(Theme.ink)
                Text("days with writing").font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
            }
            Spacer()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }

    /// A day's located memos as a route. No device location is involved: these are the places
    /// the memos already carry, drawn on OpenStreetMap tiles.
    @ViewBuilder
    private var journey: some View {
        if !model.placed.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("JOURNEY")
                        .font(Type.label).tracking(0.7)
                        .foregroundStyle(Theme.inkSoft.opacity(0.8))
                    Spacer()
                    if model.journeyDays.count > 1 {
                        Picker("", selection: $journeyDay) {
                            ForEach(model.journeyDays, id: \.self) { Text($0).tag($0) }
                        }
                        .labelsHidden()
                        .frame(width: 130)
                    }
                }

                let day = journeyDay.isEmpty ? (model.journeyDays.first ?? "") : journeyDay
                let stops = model.journey(for: day)

                if !model.mapTiles {
                    MapTilesOffNotice { Task { await model.setMapTiles(true) } }
                } else if !stops.isEmpty {
                    TileMapView(
                        points: stops.map { MapPoint(latitude: $0.latitude, longitude: $0.longitude) },
                        showsRoute: true,
                        height: 240
                    )
                }

                ForEach(Array(stops.enumerated()), id: \.element.row.localId) { index, stop in
                    Button { openMemo(stop.row.localId) } label: {
                        HStack(alignment: .top, spacing: 10) {
                            Text("\(index + 1)")
                                .font(Type.label)
                                .foregroundStyle(Theme.card)
                                .frame(width: 18, height: 18)
                                .background(Circle().fill(Theme.accent))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(stop.row.locked ? "Locked memo" : stop.row.title)
                                    .font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                                Text(stop.placeName.isEmpty ? "Somewhere unnamed" : stop.placeName)
                                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                            }
                            Spacer()
                            Text(stop.row.timeLabel)
                                .font(Type.rowMeta.monospacedDigit()).foregroundStyle(Theme.inkSoft)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
                        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// Drawn on request: a graph is worth a look now and then, not on every visit.
    @ViewBuilder
    private var graphSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("GRAPH").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                Spacer()
                Button(showGraph ? "Hide" : "Show") {
                    showGraph.toggle()
                    if showGraph { Task { await model.loadGraph() } }
                }
                .controlSize(.small)
            }
            if showGraph {
                if let graph = model.graph, !graph.nodes.isEmpty {
                    GraphView(graph: graph, open: openMemo)
                } else if model.graph != nil {
                    Text("No memo references another yet. Add a reference from a memo's menu and the links appear here.")
                        .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
                } else {
                    ProgressView()
                }
            }
        }
    }

    @ViewBuilder
    private var onThisDay: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ON THIS DAY")
                .font(Type.label).tracking(0.7)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))

            if model.throwbacks.isEmpty {
                Text("Nothing from this date in an earlier month or year. Come back when there is.")
                    .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
            } else {
                ForEach(model.throwbacks, id: \.row.localId) { throwback in
                    Button { openMemo(throwback.row.localId) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(throwback.whenLabel)
                                    .font(Type.rowMeta.weight(.medium))
                                    .foregroundStyle(Theme.accent)
                                Spacer()
                                Text(throwback.row.dateLabel)
                                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                            }
                            Text(throwback.row.locked ? "Locked memo" : throwback.row.title)
                                .font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                            if !throwback.row.locked, !throwback.row.body.isEmpty {
                                Text(throwback.row.body)
                                    .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
                                    .lineSpacing(2.5).lineLimit(3)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
                        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

/// One day at a time, starting with yesterday: what was written, with archive or keep for
/// each, which is the phone's swipe review in the shape a tap makes.
struct DayReview: View {
    @ObservedObject var model: SessionModel
    var openMemo: (String) -> Void

    @State private var day = Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date()
    @State private var memos: [MemoRow] = []
    @State private var kept: Set<String> = []

    private static let key: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private static let display: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .full
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("DAY BY DAY").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                Spacer()
                Button { shift(-1) } label: { Image(systemName: "chevron.left") }.buttonStyle(.plain)
                Text(Self.display.string(from: day)).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                Button { shift(1) } label: { Image(systemName: "chevron.right") }.buttonStyle(.plain)
                    .disabled(Calendar.current.isDateInToday(day))
            }
            if memos.isEmpty {
                Text("Nothing written that day.").font(Type.rowBody).foregroundStyle(Theme.inkSoft)
            }
            ForEach(memos, id: \.localId) { memo in
                HStack(alignment: .top, spacing: 10) {
                    Button { openMemo(memo.localId) } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(memo.locked ? "Locked memo" : memo.title).font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                            if !memo.locked, !memo.body.isEmpty {
                                Text(memo.body).font(Type.rowBody).foregroundStyle(Theme.inkSoft).lineLimit(2)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if kept.contains(memo.localId) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                    } else {
                        Button("Keep") { kept.insert(memo.localId) }.linkButton().font(Type.rowMeta)
                        Button("Archive") {
                            Task {
                                await model.setArchived(memo.localId, true)
                                memos.removeAll { $0.localId == memo.localId }
                            }
                        }
                        .linkButton().font(Type.rowMeta).foregroundStyle(Theme.warm)
                    }
                }
                .padding(12)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
                .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
            }
        }
        .task(id: day) { memos = await model.memosOn(Self.key.string(from: day)) }
    }

    private func shift(_ days: Int) {
        day = Calendar.current.date(byAdding: .day, value: days, to: day) ?? day
    }
}

#if os(iOS)
/// Located memos by distance from here. Asks the phone where it is once, on request.
struct NearbySection: View {
    @ObservedObject var model: SessionModel
    var openMemo: (String) -> Void
    @State private var asked = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("NEARBY").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                Spacer()
                Button(asked ? "Refresh" : "Find") { Task { await locate() } }.controlSize(.small)
            }
            if let failure = model.nearbyFailure {
                Text(failure).font(Type.rowMeta).foregroundStyle(Theme.danger)
            } else if asked && model.nearby.isEmpty {
                Text("No memo carries a location yet.").font(Type.rowBody).foregroundStyle(Theme.inkSoft)
            } else if !asked {
                Text("Memos written near where you are now. Your location is read once, when you ask.")
                    .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
            }
            ForEach(model.nearby, id: \.row.localId) { near in
                Button { openMemo(near.row.localId) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(near.row.locked ? "Locked memo" : near.row.title).font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                            Text(near.placeName.isEmpty ? "Somewhere unnamed" : near.placeName).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        }
                        Spacer()
                        Text(distance(near.metres)).font(Type.rowMeta.monospacedDigit()).foregroundStyle(Theme.inkSoft)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
                    .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func locate() async {
        asked = true
        do {
            let fix = try await LocationCapture.shared.current()
            await model.loadNearby(latitude: fix.latitude, longitude: fix.longitude)
        } catch {
            model.nearbyFailure = "Could not find where you are: \(error.localizedDescription)"
        }
    }

    private func distance(_ metres: Double) -> String {
        metres < 1000 ? "\(Int(metres)) m" : String(format: "%.1f km", metres / 1000)
    }
}
#endif

/// A year of writing, one square per day, the way the phone shows it.
struct HeatmapView: View {
    let activeDays: Set<String>

    private let columns = 26
    private let square: CGFloat = 11
    private let gap: CGFloat = 3

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("THE LAST SIX MONTHS")
                .font(Type.label).tracking(0.7)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))

            HStack(alignment: .top, spacing: gap) {
                ForEach(0..<columns, id: \.self) { week in
                    VStack(spacing: gap) {
                        ForEach(0..<7, id: \.self) { weekday in
                            let day = date(week: week, weekday: weekday)
                            RoundedRectangle(cornerRadius: 2)
                                .fill(fill(for: day))
                                .frame(width: square, height: square)
                                .help(day.map { Self.display.string(from: $0) } ?? "")
                        }
                    }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
            .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
        }
    }

    private static let key: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private static let display: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        return f
    }()

    /// The grid runs left to right in weeks, ending on the column containing today.
    private func date(week: Int, weekday: Int) -> Date? {
        let calendar = Calendar.current
        let today = Date()
        guard let startOfThisWeek = calendar.dateInterval(of: .weekOfYear, for: today)?.start else { return nil }
        guard let weekStart = calendar.date(byAdding: .weekOfYear, value: week - (columns - 1), to: startOfThisWeek)
        else { return nil }
        guard let day = calendar.date(byAdding: .day, value: weekday, to: weekStart) else { return nil }
        return day > today ? nil : day
    }

    private func fill(for day: Date?) -> Color {
        guard let day else { return Theme.hairline.opacity(0.4) }
        return activeDays.contains(Self.key.string(from: day))
            ? Theme.accent.opacity(0.8)
            : Theme.hairline
    }
}
