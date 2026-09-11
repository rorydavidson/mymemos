import SwiftUI

/// The formatting buttons above the editor.
///
/// A memo is stored as Markdown, so these type the Markdown rather than hiding it: the text
/// stays exactly what will be saved, and someone who already knows the syntax loses nothing.
/// The phone's editor has the same set, which is what makes a memo written on one look right
/// on the other.
struct FormatBar: View {
    let perform: (EditorEdit) -> Void

    var body: some View {
        HStack(spacing: 2) {
            group {
                button("bold", "Bold", .wrap("**"), key: "b")
                button("italic", "Italic", .wrap("_"), key: "i")
                button("chevron.left.forwardslash.chevron.right", "Code", .wrap("`"))
                button("strikethrough", "Strikethrough", .wrap("~~"))
            }

            divider

            group {
                button("textformat.size.larger", "Heading", .prefixLines("## "))
                button("list.bullet", "Bullet list", .prefixLines("- "))
                button("list.number", "Numbered list", .prefixLines("1. "))
                button("checklist", "Task", .prefixLines("- [ ] "), key: "t")
                button("text.quote", "Quote", .prefixLines("> "))
            }

            divider

            group {
                button("number", "Tag", .insert("#"))
                button("link", "Link", .insert("[](url)"), key: "k")
                button("minus", "Divider", .insert("\n---\n"))
            }

            Spacer()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Theme.canvas)
    }

    private var divider: some View {
        Rectangle()
            .fill(Theme.hairline)
            .frame(width: 1, height: 16)
            .padding(.horizontal, 5)
    }

    private func group<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: 2) { content() }
    }

    @ViewBuilder
    private func button(_ symbol: String, _ help: String, _ edit: EditorEdit, key: Character? = nil) -> some View {
        let action = { perform(edit) }
        if let key {
            FormatButton(symbol: symbol, help: "\(help) (⌘\(key.uppercased()))", action: action)
                .keyboardShortcut(KeyEquivalent(key), modifiers: .command)
        } else {
            FormatButton(symbol: symbol, help: help, action: action)
        }
    }
}

private struct FormatButton: View {
    let symbol: String
    let help: String
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12))
                .foregroundStyle(Theme.ink)
                .frame(width: 26, height: 22)
                .background(
                    RoundedRectangle(cornerRadius: 5)
                        .fill(hovering ? Theme.hairline.opacity(0.7) : .clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(help)
    }
}
