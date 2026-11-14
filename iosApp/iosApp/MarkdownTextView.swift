import SwiftUI
import SharedUI

/// Renders parsed `MarkdownParser` blocks using native SwiftUI typography,
/// lists, dividers, blockquotes, code blocks, and tables.
struct MarkdownTextView: View {
    let markdown: String

    private var blocks: [MarkdownBlock] {
        MarkdownParser.shared.parse(markdown: markdown)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                blockView(block, isFirst: index == 0)
            }
        }
        .textSelection(.enabled)
    }

    @ViewBuilder
    private func blockView(_ block: MarkdownBlock, isFirst: Bool) -> some View {
        switch block.kind {
        case MarkdownBlock.companion.KIND_HEADING:
            let style = headingStyle(for: Int(block.level))
            Text(attributedText(for: block.inlines, baseFont: style.font, codeTextStyle: style.codeStyle))
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, isFirst ? 0 : 4)

        case MarkdownBlock.companion.KIND_DIVIDER:
            Divider()
                .padding(.vertical, 2)

        case MarkdownBlock.companion.KIND_BULLET_ITEM:
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(block.indentLevel > 0 ? "◦" : "•")
                    .font(.body)
                    .foregroundStyle(.secondary)
                Text(attributedText(for: block.inlines, baseFont: .body))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, CGFloat(block.indentLevel) * 14)

        case MarkdownBlock.companion.KIND_ORDERED_ITEM:
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(block.marker)
                    .font(.body.monospacedDigit())
                    .foregroundStyle(.secondary)
                Text(attributedText(for: block.inlines, baseFont: .body))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, CGFloat(block.indentLevel) * 14)

        case MarkdownBlock.companion.KIND_QUOTE:
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Color.accentColor.opacity(0.6))
                    .frame(width: 3)
                Text(attributedText(for: block.inlines, baseFont: .body))
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

        case MarkdownBlock.companion.KIND_CODE_BLOCK:
            VStack(alignment: .leading, spacing: 4) {
                if !block.language.isEmpty {
                    Text(block.language)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    Text(block.code)
                        .font(.system(.callout, design: .monospaced))
                        .fixedSize(horizontal: true, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))

        case MarkdownBlock.companion.KIND_TABLE:
            ScrollView(.horizontal, showsIndicators: false) {
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                    GridRow {
                        ForEach(Array(block.tableHeaders.enumerated()), id: \.offset) { _, cell in
                            Text(attributedText(for: cell.inlines, baseFont: .subheadline.bold(), codeTextStyle: .footnote))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Divider()
                    ForEach(Array(block.tableRows.enumerated()), id: \.offset) { _, row in
                        GridRow {
                            ForEach(Array(row.cells.enumerated()), id: \.offset) { _, cell in
                                Text(attributedText(for: cell.inlines, baseFont: .subheadline, codeTextStyle: .footnote))
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
                .padding(10)
            }
            .background(Color(.tertiarySystemFill).opacity(0.6), in: RoundedRectangle(cornerRadius: 10))

        default:
            Text(attributedText(for: block.inlines, baseFont: .body))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func headingStyle(for level: Int) -> (font: Font, codeStyle: Font.TextStyle) {
        switch level {
        case 1: return (.title2.bold(), .title3)
        case 2: return (.title3.bold(), .headline)
        case 3: return (.headline, .subheadline)
        default: return (.subheadline.bold(), .footnote)
        }
    }

    private func attributedText(
        for inlines: [MarkdownInline],
        baseFont: Font,
        codeTextStyle: Font.TextStyle = .callout
    ) -> AttributedString {
        var result = AttributedString()
        for run in inlines {
            var part = AttributedString(run.text)
            var font: Font = run.code ? .system(codeTextStyle, design: .monospaced) : baseFont
            if run.bold { font = font.bold() }
            if run.italic { font = font.italic() }
            part.font = font
            if run.code {
                part.backgroundColor = Color(.tertiarySystemFill)
            }
            if run.strikethrough {
                part.strikethroughStyle = .single
            }
            if !run.linkUrl.isEmpty,
               let url = URL(string: run.linkUrl),
               let scheme = url.scheme?.lowercased(),
               scheme == "http" || scheme == "https" || scheme == "mailto" {
                part.link = url
            }
            result.append(part)
        }
        return result
    }
}
