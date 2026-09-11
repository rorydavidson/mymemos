import SwiftUI
import Shared

/// Every tag the account uses, with the emoji and colour the user has given it. Styles live
/// in the config memo, so they are the same on every device, and the colour is mirrored to
/// the server's own tag setting so the web UI matches.
struct TagsView: View {
    @ObservedObject var model: SessionModel
    var open: (String) -> Void = { _ in }

    @State private var editing: String?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 6) {
                ForEach(model.tags, id: \.self) { tag in
                    HStack(spacing: 10) {
                        TagChip(tag: tag, style: model.tagStyles[tag])
                        Spacer()
                        Text("\(model.count(forTag: tag))").font(Type.rowMeta).monospacedDigit().foregroundStyle(Theme.inkSoft)
                        Button("Style") { editing = tag }.linkButton().font(Type.rowMeta)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Theme.hairline))
                    .contentShape(Rectangle())
                    .onTapGesture { open(tag) }
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.readingWidth, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.canvas)
        .overlay(alignment: .center) {
            if model.tags.isEmpty {
                EmptyState(icon: "number", title: "No tags yet", detail: "Write #something in a memo and it will be here.")
            }
        }
        .sheet(item: Binding(get: { editing.map(TagTarget.init) }, set: { if $0 == nil { editing = nil } })) { target in
            TagStyleEditor(model: model, tag: target.id)
        }
        .task { await model.loadTagStyles() }
    }
}

private struct TagTarget: Identifiable { let id: String }

private struct TagStyleEditor: View {
    @ObservedObject var model: SessionModel
    let tag: String
    @Environment(\.dismiss) private var dismiss
    @State private var emoji = ""
    @State private var colourName: String?
    @State private var loaded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("#\(tag)").font(Type.heading3)
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") {
                    Task {
                        await model.setTagStyle(tag, emoji: emoji.trimmingCharacters(in: .whitespaces), colourName: colourName)
                        dismiss()
                    }
                }
                .keyboardShortcut(.defaultAction)
            }
            .padding(14)
            Divider()
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Emoji").font(Type.rowBody)
                    TextField("None", text: $emoji).textFieldStyle(.roundedBorder).frame(maxWidth: 90)
                    if !emoji.isEmpty {
                        Button("Clear") { emoji = "" }.linkButton().font(Type.rowMeta)
                    }
                    Spacer()
                    TagChip(tag: tag, style: TagStyleRow(tag: tag, emoji: emoji, colourName: colourName, colourHex: hex))
                }
                // The same catalogue Android offers, by category. Anything else can be typed above.
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(model.emojiCatalogue, id: \.name) { group in
                            Text(group.name.uppercased()).font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 32), spacing: 4)], spacing: 4) {
                                ForEach(group.emoji, id: \.self) { candidate in
                                    Button { emoji = candidate } label: {
                                        Text(candidate)
                                            .font(.system(size: 20))
                                            .frame(width: 32, height: 32)
                                            .background(RoundedRectangle(cornerRadius: 6).fill(emoji == candidate ? Theme.accentSoft : .clear))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .padding(.trailing, 4)
                }
                .frame(height: 180)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Theme.hairline))
                Text("Colour").font(Type.rowBody)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 34), spacing: 8)], spacing: 8) {
                    swatch(nil, hex: -1)
                    ForEach(model.colourOptions, id: \.name) { option in
                        swatch(option.name, hex: option.hex)
                    }
                }
                Text("Shown on every device you sign in on. The colour is also set on the server, so the web app agrees.")
                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft).fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
        }
        .sheetWidth(440)
        .background(Theme.canvas)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            emoji = model.tagStyles[tag]?.emoji ?? ""
            colourName = model.tagStyles[tag]?.colourName
        }
    }

    private var hex: Int64 {
        colourName.flatMap { name in model.colourOptions.first { $0.name == name }?.hex } ?? -1
    }

    @Environment(\.colorScheme) private var scheme

    private func swatch(_ name: String?, hex: Int64) -> some View {
        Button { colourName = name } label: {
            ZStack {
                Circle().fill(Color.memoTint(hex, isDark: scheme == .dark) ?? Theme.card)
                if name == nil { Image(systemName: "slash.circle").foregroundStyle(Theme.inkSoft) }
                if colourName == name { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.ink) }
            }
            .frame(width: 34, height: 34)
            .overlay(Circle().strokeBorder(Theme.hairline))
        }
        .buttonStyle(.plain)
    }
}
