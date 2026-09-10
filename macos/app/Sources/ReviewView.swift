import SwiftUI
import Shared

/// Looking back: how long the writing streak is, what the year looks like, and what was
/// written on this day in earlier months and years.
struct ReviewView: View {
    @ObservedObject var model: SessionModel
    var openMemo: (String) -> Void
    @State private var journeyDay = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                streakCard
                HeatmapView(activeDays: model.activeDays)
                journey
                onThisDay
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
