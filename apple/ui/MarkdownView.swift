import SwiftUI

/// Renders a memo's Markdown.
///
/// Deliberately a block renderer rather than one `AttributedString` over the whole text:
/// headings, task lists and quotes need their own layout, and a task list rendered as plain
/// text loses the thing that makes it a task list. Inline formatting inside each block still
/// goes through `AttributedString`, which handles emphasis, code spans and links.
struct MarkdownView: View {
    let text: String
    var lineLimit: Int?
    /// Set by a list card: row sizes, tighter spacing, a few lines per block at most, and no
    /// text selection, which would otherwise swallow the click that selects the row.
    var card = false
    /// Called with the source line and the new state when a task's box is tapped. Nil leaves
    /// the boxes as pictures, which is what a preview wants.
    var onToggleTask: ((Int, Bool) -> Void)? = nil

    var body: some View {
        let stack = VStack(alignment: .leading, spacing: card ? 4 : 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                view(for: block)
                    .lineLimit(card ? 3 : nil)
            }
        }
        .lineSpacing(card ? 2.5 : Theme.readingLeading)

        if card {
            stack.textSelection(.disabled)
        } else {
            stack.textSelection(.enabled)
        }
    }

    private var bodyFont: Font { card ? Type.rowBody : Type.body }

    // A card draws its tags as chips underneath, so a line of nothing but tags would say it twice.
    private var blocks: [Block] { Block.parse(text, limit: lineLimit, skippingTagLines: card) }

    @ViewBuilder
    private func view(for block: Block) -> some View {
        switch block {
        case let .heading(level, content):
            Text(inline(content))
                .font(card ? Type.rowTitle : headingFont(level))
                .padding(.top, card ? 0 : (level <= 2 ? 8 : 4))

        case let .paragraph(content):
            Text(inline(content)).font(bodyFont)

        case let .quote(content):
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Theme.accent.opacity(0.45)).frame(width: 3)
                Text(inline(content)).font(card ? Type.rowBody : Type.quote).italic().foregroundStyle(Theme.inkSoft)
            }
            .fixedSize(horizontal: false, vertical: true)

        case let .bullet(depth, content):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("•").font(bodyFont).foregroundStyle(Theme.inkSoft)
                Text(inline(content)).font(bodyFont)
            }
            .padding(.leading, CGFloat(depth) * indent)

        case let .numbered(depth, marker, content):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(marker).font(bodyFont).foregroundStyle(Theme.inkSoft).monospacedDigit()
                Text(inline(content)).font(bodyFont)
            }
            .padding(.leading, CGFloat(depth) * indent)

        case let .task(depth, done, content, line):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                let box = Image(systemName: done ? "checkmark.square.fill" : "square")
                    .font(.system(size: card ? 11 : (onToggleTask == nil ? 14 : 18)))
                    .foregroundStyle(done ? Theme.accent : Theme.inkSoft.opacity(0.75))
                // Without a handler the box is only a picture, and a disabled button would still
                // take the click that a list card needs for selecting itself.
                if let onToggleTask {
                    Button { onToggleTask(line, !done) } label: { box }
                        .buttonStyle(.plain)
                } else {
                    box
                }
                Text(inline(content))
                    .font(bodyFont)
                    .strikethrough(done, color: Theme.inkSoft)
                    .foregroundStyle(done ? Theme.inkSoft : Theme.ink)
            }
            .padding(.leading, CGFloat(depth) * indent)

        case let .code(content):
            Text(content)
                .font(card ? .system(size: 11.5, design: .monospaced) : Type.code)
                .lineSpacing(3)
                .padding(card ? 8 : 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.raised.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Theme.hairline, lineWidth: 0.5))

        case .rule:
            Divider()
        }
    }

    private var indent: CGFloat { card ? 14 : 18 }

    private func headingFont(_ level: Int) -> Font {
        switch level {
        case 1: return Type.heading1
        case 2: return Type.heading2
        default: return Type.heading3
        }
    }

    /// Emphasis, code spans and links, with tags left alone so they read as tags.
    private func inline(_ source: String) -> AttributedString {
        if let parsed = try? AttributedString(
            markdown: source,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            return parsed
        }
        return AttributedString(source)
    }
}

// MARK: - A very small block parser

enum Block {
    case heading(Int, String)
    case paragraph(String)
    case quote(String)
    case bullet(Int, String)
    case numbered(Int, String, String)
    /// Depth, done, content, and the line of the source text the task sits on.
    case task(Int, Bool, String, Int)
    case code(String)
    case rule

    static func parse(_ text: String, limit: Int?, skippingTagLines: Bool = false) -> [Block] {
        var blocks: [Block] = []
        var lines = text.components(separatedBy: "\n")
        if let limit, lines.count > limit { lines = Array(lines.prefix(limit)) }

        var index = 0
        while index < lines.count {
            let raw = lines[index]
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            let depth = indentDepth(raw)

            if trimmed.hasPrefix("```") {
                var body: [String] = []
                index += 1
                while index < lines.count, !lines[index].trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                    body.append(lines[index])
                    index += 1
                }
                blocks.append(.code(body.joined(separator: "\n")))
                index += 1
                continue
            }

            if trimmed.isEmpty {
                index += 1
                continue
            }
            if trimmed == "---" || trimmed == "***" || trimmed == "___" {
                blocks.append(.rule)
                index += 1
                continue
            }
            if trimmed.hasPrefix("#") {
                let hashes = trimmed.prefix { $0 == "#" }.count
                if hashes <= 6, trimmed.dropFirst(hashes).hasPrefix(" ") {
                    blocks.append(.heading(hashes, String(trimmed.dropFirst(hashes + 1))))
                    index += 1
                    continue
                }
            }
            if trimmed.hasPrefix("> ") {
                blocks.append(.quote(String(trimmed.dropFirst(2))))
                index += 1
                continue
            }
            if let task = taskItem(trimmed) {
                blocks.append(.task(depth, task.done, task.content, index))
                index += 1
                continue
            }
            if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") || trimmed.hasPrefix("+ ") {
                blocks.append(.bullet(depth, String(trimmed.dropFirst(2))))
                index += 1
                continue
            }
            if let numbered = numberedItem(trimmed) {
                blocks.append(.numbered(depth, numbered.marker, numbered.content))
                index += 1
                continue
            }
            if skippingTagLines, isTagLine(trimmed) {
                index += 1
                continue
            }
            blocks.append(.paragraph(trimmed))
            index += 1
        }
        return blocks
    }

    /// The same tag pattern the shared code extracts tags with, so what is hidden here is
    /// exactly what shows as a chip.
    private static let tag = try! NSRegularExpression(pattern: "(?<![\\w/])#[\\p{L}\\p{N}_/\\-]+")

    /// True for a line that is only tags, such as `#work #ideas`. A sentence that mentions a
    /// tag keeps it, because taking the word out would change what the sentence says.
    private static func isTagLine(_ line: String) -> Bool {
        let range = NSRange(location: 0, length: (line as NSString).length)
        guard tag.firstMatch(in: line, range: range) != nil else { return false }
        return tag.stringByReplacingMatches(in: line, range: range, withTemplate: "")
            .trimmingCharacters(in: .whitespaces).isEmpty
    }

    private static func indentDepth(_ line: String) -> Int {
        let spaces = line.prefix { $0 == " " }.count
        let tabs = line.prefix { $0 == "\t" }.count
        return min(3, tabs + spaces / 2)
    }

    private static func taskItem(_ line: String) -> (done: Bool, content: String)? {
        for marker in ["- ", "* ", "+ "] where line.hasPrefix(marker) {
            let rest = line.dropFirst(marker.count)
            if rest.hasPrefix("[ ] ") { return (false, String(rest.dropFirst(4))) }
            if rest.lowercased().hasPrefix("[x] ") { return (true, String(rest.dropFirst(4))) }
        }
        return nil
    }

    private static func numberedItem(_ line: String) -> (marker: String, content: String)? {
        let digits = line.prefix { $0.isNumber }
        guard !digits.isEmpty else { return nil }
        let rest = line.dropFirst(digits.count)
        guard rest.hasPrefix(". ") || rest.hasPrefix(") ") else { return nil }
        return (String(digits) + ".", String(rest.dropFirst(2)))
    }
}
