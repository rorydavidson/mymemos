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

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        .textSelection(.enabled)
        .lineSpacing(Theme.readingLeading)
    }

    private var blocks: [Block] { Block.parse(text, limit: lineLimit) }

    @ViewBuilder
    private func view(for block: Block) -> some View {
        switch block {
        case let .heading(level, content):
            Text(inline(content))
                .font(headingFont(level))
                .padding(.top, level <= 2 ? 8 : 4)

        case let .paragraph(content):
            Text(inline(content)).font(Type.body)

        case let .quote(content):
            HStack(alignment: .top, spacing: 10) {
                Rectangle().fill(Theme.accent.opacity(0.5)).frame(width: 3)
                Text(inline(content)).font(Type.body).foregroundStyle(Theme.inkSoft)
            }
            .fixedSize(horizontal: false, vertical: true)

        case let .bullet(depth, content):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("•").font(Type.body).foregroundStyle(Theme.inkSoft)
                Text(inline(content)).font(Type.body)
            }
            .padding(.leading, CGFloat(depth) * 18)

        case let .numbered(depth, marker, content):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(marker).font(Type.body).foregroundStyle(Theme.inkSoft).monospacedDigit()
                Text(inline(content)).font(Type.body)
            }
            .padding(.leading, CGFloat(depth) * 18)

        case let .task(depth, done, content):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: done ? "checkmark.square.fill" : "square")
                    .font(.system(size: 14))
                    .foregroundStyle(done ? Theme.accent : Theme.inkSoft)
                Text(inline(content))
                    .font(Type.body)
                    .strikethrough(done, color: Theme.inkSoft)
                    .foregroundStyle(done ? Theme.inkSoft : Theme.ink)
            }
            .padding(.leading, CGFloat(depth) * 18)

        case let .code(content):
            Text(content)
                .font(Type.code)
                .lineSpacing(3)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.canvas, in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(Theme.hairline))

        case .rule:
            Divider()
        }
    }

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
    case task(Int, Bool, String)
    case code(String)
    case rule

    static func parse(_ text: String, limit: Int?) -> [Block] {
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
                blocks.append(.task(depth, task.done, task.content))
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
            blocks.append(.paragraph(trimmed))
            index += 1
        }
        return blocks
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
