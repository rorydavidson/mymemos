import SwiftUI
import Shared

/// Completions for the word being typed: tags the account already uses after `#`, and days
/// after `@`. The date tokens come from the same parser the tasks screen reads, so a
/// completion can never mean a different day than it shows.
struct SuggestionBar: View {
    let word: String
    let tags: [String]
    let dates: [DateSuggestionRow]
    let choose: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                if word.hasPrefix("#") {
                    ForEach(tags, id: \.self) { tag in
                        chip("#\(tag)", hint: nil) { choose("#\(tag)") }
                    }
                } else {
                    ForEach(dates, id: \.token) { date in
                        chip("@\(date.token)", hint: date.hint.isEmpty ? nil : date.hint) { choose("@\(date.token)") }
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
        }
        .frame(height: 34)
        .background(Theme.canvas)
    }

    private func chip(_ text: String, hint: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(text).font(Type.rowBody)
                if let hint { Text(hint).font(Type.rowMeta).foregroundStyle(Theme.inkSoft) }
            }
            .foregroundStyle(Theme.onAccentSoft)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(Capsule().fill(Theme.accentSoft))
        }
        .buttonStyle(.plain)
    }
}
