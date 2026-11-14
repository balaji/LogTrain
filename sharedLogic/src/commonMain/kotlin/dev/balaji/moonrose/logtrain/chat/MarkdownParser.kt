package dev.balaji.moonrose.logtrain.chat

/**
 * Inline styled run inside a [MarkdownBlock]. Flat styling flags keep both
 * Compose `AnnotatedString` and SwiftUI `AttributedString` rendering simple
 * and SKIE-friendly.
 */
data class MarkdownInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String = "",
)

data class MarkdownTableCell(
    val inlines: List<MarkdownInline>,
)

data class MarkdownTableRow(
    val cells: List<MarkdownTableCell>,
)

/**
 * Block-level element produced by [MarkdownParser.parse].
 * [kind] uses the `KIND_*` constants on [Companion] for SKIE compatibility.
 */
data class MarkdownBlock(
    val kind: String,
    val level: Int = 0,
    val indentLevel: Int = 0,
    val marker: String = "",
    val code: String = "",
    val language: String = "",
    val inlines: List<MarkdownInline> = emptyList(),
    val tableHeaders: List<MarkdownTableCell> = emptyList(),
    val tableRows: List<MarkdownTableRow> = emptyList(),
) {
    companion object {
        const val KIND_PARAGRAPH = "paragraph"
        const val KIND_HEADING = "heading"
        const val KIND_BULLET_ITEM = "bullet_item"
        const val KIND_ORDERED_ITEM = "ordered_item"
        const val KIND_DIVIDER = "divider"
        const val KIND_QUOTE = "quote"
        const val KIND_CODE_BLOCK = "code_block"
        const val KIND_TABLE = "table"
    }
}

/**
 * Lightweight, streaming-safe Markdown parser shared by Android (Compose) and
 * iOS (SwiftUI) chat output views.
 */
object MarkdownParser {
    private val ORDERED_ITEM_REGEX = Regex("""^(\s*)(\d{1,9})([.)])[ \t]+(.*)$""")
    private val BULLET_ITEM_REGEX = Regex("""^(\s*)([-*+•])[ \t]+(.*)$""")
    private val ATX_HEADING_REGEX = Regex("""^[ ]{0,3}(#{1,6})(?:[ \t]+(.*))?$""")
    private val TABLE_CELL_SEPARATOR_REGEX = Regex("""^:?-{3,}:?$""")
    private const val ESCAPABLE_CHARS = """\/*_{}[]()#+-.!~|>`"""

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()

        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = mutableListOf<MarkdownBlock>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }

            // 1. Fenced code block (``` or ~~~). Unclosed blocks close at EOF for streaming safety.
            val fence = parseFenceStart(line)
            if (fence != null) {
                val (fenceChar, fenceLen, language) = fence
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !isFenceEnd(lines[i], fenceChar, fenceLen)) {
                    codeLines += lines[i]
                    i++
                }
                if (i < lines.size) i++ // consume closing fence
                blocks += MarkdownBlock(
                    kind = MarkdownBlock.KIND_CODE_BLOCK,
                    code = codeLines.joinToString("\n"),
                    language = language,
                )
                continue
            }

            // 2. Thematic break / horizontal rule (check before bullet lists so `- - -` is a divider).
            if (isHorizontalRule(line)) {
                blocks += MarkdownBlock(kind = MarkdownBlock.KIND_DIVIDER)
                i++
                continue
            }

            // 3. ATX Heading (# .. ######).
            val heading = parseHeading(line)
            if (heading != null) {
                val (level, text) = heading
                if (text.isNotEmpty()) {
                    blocks += MarkdownBlock(
                        kind = MarkdownBlock.KIND_HEADING,
                        level = level,
                        inlines = parseInlines(text),
                    )
                }
                i++
                continue
            }

            // 4. GFM Pipe Table (header + separator + body rows).
            if (i + 1 < lines.size && line.contains('|') && isTableSeparator(lines[i + 1])) {
                val headerCells = splitTableRow(line)
                if (headerCells.isNotEmpty()) {
                    val colCount = headerCells.size
                    val headers = headerCells.map { MarkdownTableCell(parseInlines(it)) }
                    val rows = mutableListOf<MarkdownTableRow>()
                    i += 2
                    while (i < lines.size) {
                        val rowLine = lines[i]
                        if (rowLine.isBlank() || isBlockInterruptor(rowLine) || !rowLine.contains('|')) {
                            break
                        }
                        val rawCells = splitTableRow(rowLine)
                        val normalizedCells = (0 until colCount).map { col ->
                            MarkdownTableCell(parseInlines(rawCells.getOrElse(col) { "" }))
                        }
                        rows += MarkdownTableRow(normalizedCells)
                        i++
                    }
                    blocks += MarkdownBlock(
                        kind = MarkdownBlock.KIND_TABLE,
                        tableHeaders = headers,
                        tableRows = rows,
                    )
                    continue
                }
            }

            // 5. Blockquote (`> ...`).
            if (isBlockquoteLine(line)) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && isBlockquoteLine(lines[i])) {
                    quoteLines += stripBlockquotePrefix(lines[i])
                    i++
                }
                val content = quoteLines.joinToString("\n").trim()
                if (content.isNotEmpty()) {
                    blocks += MarkdownBlock(
                        kind = MarkdownBlock.KIND_QUOTE,
                        inlines = parseInlines(content),
                    )
                }
                continue
            }

            // 6. Unordered list item.
            val bulletMatch = BULLET_ITEM_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                val indentLevel = (bulletMatch.groupValues[1].length / 2).coerceAtMost(4)
                val itemText = StringBuilder(bulletMatch.groupValues[3])
                i++
                while (i < lines.size && isListContinuationLine(lines[i])) {
                    itemText.append(' ').append(lines[i].trim())
                    i++
                }
                blocks += MarkdownBlock(
                    kind = MarkdownBlock.KIND_BULLET_ITEM,
                    indentLevel = indentLevel,
                    inlines = parseInlines(itemText.toString()),
                )
                continue
            }

            // 7. Ordered list item.
            val orderedMatch = ORDERED_ITEM_REGEX.matchEntire(line)
            if (orderedMatch != null) {
                val indentLevel = (orderedMatch.groupValues[1].length / 2).coerceAtMost(4)
                val marker = "${orderedMatch.groupValues[2]}${orderedMatch.groupValues[3]}"
                val itemText = StringBuilder(orderedMatch.groupValues[4])
                i++
                while (i < lines.size && isListContinuationLine(lines[i])) {
                    itemText.append(' ').append(lines[i].trim())
                    i++
                }
                blocks += MarkdownBlock(
                    kind = MarkdownBlock.KIND_ORDERED_ITEM,
                    indentLevel = indentLevel,
                    marker = marker,
                    inlines = parseInlines(itemText.toString()),
                )
                continue
            }

            // 8. Paragraph: consecutive non-blank lines not starting another block.
            val paraLines = mutableListOf(line)
            i++
            while (i < lines.size) {
                val nextLine = lines[i]
                if (nextLine.isBlank() || isBlockInterruptor(nextLine)) break
                if (i + 1 < lines.size && nextLine.contains('|') && isTableSeparator(lines[i + 1])) break
                paraLines += nextLine
                i++
            }
            val paraText = paraLines.joinToString("\n")
            blocks += MarkdownBlock(
                kind = MarkdownBlock.KIND_PARAGRAPH,
                inlines = parseInlines(paraText),
            )
        }

        return blocks
    }

    fun parseInlines(text: String): List<MarkdownInline> {
        if (text.isEmpty()) return emptyList()
        val raw = mutableListOf<MarkdownInline>()
        parseInlinesInto(
            text = text,
            start = 0,
            end = text.length,
            bold = false,
            italic = false,
            strikethrough = false,
            linkUrl = "",
            out = raw,
        )
        return coalesce(raw)
    }

    private fun parseInlinesInto(
        text: String,
        start: Int,
        end: Int,
        bold: Boolean,
        italic: Boolean,
        strikethrough: Boolean,
        linkUrl: String,
        out: MutableList<MarkdownInline>,
    ) {
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out += MarkdownInline(
                    text = plain.toString(),
                    bold = bold,
                    italic = italic,
                    strikethrough = strikethrough,
                    code = false,
                    linkUrl = linkUrl,
                )
                plain.clear()
            }
        }

        var i = start
        while (i < end) {
            val ch = text[i]

            // Backslash escape for ASCII punctuation.
            if (ch == '\\' && i + 1 < end && text[i + 1] in ESCAPABLE_CHARS) {
                plain.append(text[i + 1])
                i += 2
                continue
            }

            // Inline code span (`...` or ``...``).
            if (ch == '`') {
                val tickCount = countRun(text, i, end, '`')
                val closeIdx = findClosingBackticks(text, i + tickCount, end, tickCount)
                if (closeIdx != -1) {
                    flushPlain()
                    var codeContent = text.substring(i + tickCount, closeIdx)
                    if (codeContent.length >= 2 &&
                        codeContent.first() == ' ' &&
                        codeContent.last() == ' ' &&
                        codeContent.any { it != ' ' }
                    ) {
                        codeContent = codeContent.substring(1, codeContent.length - 1)
                    }
                    out += MarkdownInline(
                        text = codeContent,
                        bold = bold,
                        italic = italic,
                        strikethrough = strikethrough,
                        code = true,
                        linkUrl = linkUrl,
                    )
                    i = closeIdx + tickCount
                    continue
                } else {
                    repeat(tickCount) { plain.append('`') }
                    i += tickCount
                    continue
                }
            }

            // Link: [label](url) (only parse when not already inside a link).
            if (ch == '[' && linkUrl.isEmpty()) {
                val linkMatch = matchLink(text, i, end)
                if (linkMatch != null) {
                    flushPlain()
                    parseInlinesInto(
                        text = text,
                        start = linkMatch.labelStart,
                        end = linkMatch.labelEnd,
                        bold = bold,
                        italic = italic,
                        strikethrough = strikethrough,
                        linkUrl = linkMatch.url,
                        out = out,
                    )
                    i = linkMatch.afterEnd
                    continue
                }
            }

            // Strikethrough: ~~...~~
            if (ch == '~' && i + 1 < end && text[i + 1] == '~') {
                val closeIdx = findClosingDelimiter(text, i + 2, end, "~~", wordBoundary = false)
                if (closeIdx != -1) {
                    flushPlain()
                    parseInlinesInto(
                        text = text,
                        start = i + 2,
                        end = closeIdx,
                        bold = bold,
                        italic = italic,
                        strikethrough = true,
                        linkUrl = linkUrl,
                        out = out,
                    )
                    i = closeIdx + 2
                    continue
                }
            }

            // Emphasis: *, **, ***, _, __, ___
            if (ch == '*' || ch == '_') {
                val runLen = countRun(text, i, end, ch)
                val wordBoundary = ch == '_'
                val validOpener = (!wordBoundary || i == start || !text[i - 1].isLetterOrDigit()) &&
                    i + runLen < end && !text[i + runLen].isWhitespace()

                if (validOpener) {
                    if (runLen >= 3) {
                        val close3 = findClosingDelimiter(text, i + 3, end, "$ch$ch$ch", wordBoundary)
                        if (close3 != -1) {
                            flushPlain()
                            parseInlinesInto(
                                text = text,
                                start = i + 3,
                                end = close3,
                                bold = true,
                                italic = true,
                                strikethrough = strikethrough,
                                linkUrl = linkUrl,
                                out = out,
                            )
                            i = close3 + 3
                            continue
                        }
                    }
                    if (runLen == 2) {
                        val close2 = findClosingDelimiter(text, i + 2, end, "$ch$ch", wordBoundary)
                        if (close2 != -1) {
                            flushPlain()
                            parseInlinesInto(
                                text = text,
                                start = i + 2,
                                end = close2,
                                bold = true,
                                italic = italic,
                                strikethrough = strikethrough,
                                linkUrl = linkUrl,
                                out = out,
                            )
                            i = close2 + 2
                            continue
                        }
                    }
                    if (runLen == 1 || runLen >= 3) {
                        val close1 = findClosingSingleDelimiter(text, i + 1, end, ch, wordBoundary)
                        if (close1 != -1) {
                            flushPlain()
                            parseInlinesInto(
                                text = text,
                                start = i + 1,
                                end = close1,
                                bold = bold,
                                italic = true,
                                strikethrough = strikethrough,
                                linkUrl = linkUrl,
                                out = out,
                            )
                            i = close1 + 1
                            continue
                        }
                    }
                }
            }

            plain.append(ch)
            i++
        }

        flushPlain()
    }

    private fun coalesce(runs: List<MarkdownInline>): List<MarkdownInline> {
        if (runs.isEmpty()) return emptyList()
        val result = ArrayList<MarkdownInline>(runs.size)
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val prev = result.lastOrNull()
            if (prev != null &&
                prev.bold == run.bold &&
                prev.italic == run.italic &&
                prev.strikethrough == run.strikethrough &&
                prev.code == run.code &&
                prev.linkUrl == run.linkUrl
            ) {
                result[result.lastIndex] = prev.copy(text = prev.text + run.text)
            } else {
                result += run
            }
        }
        return result
    }

    private fun countRun(text: String, index: Int, end: Int, ch: Char): Int {
        var count = 0
        while (index + count < end && text[index + count] == ch) {
            count++
        }
        return count
    }

    private fun findClosingBackticks(text: String, from: Int, end: Int, tickCount: Int): Int {
        var i = from
        while (i < end) {
            if (text[i] == '`') {
                val run = countRun(text, i, end, '`')
                if (run == tickCount) return i
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    private fun findClosingDelimiter(
        text: String,
        from: Int,
        end: Int,
        delim: String,
        wordBoundary: Boolean,
    ): Int {
        if (from >= end || text[from].isWhitespace()) return -1
        var i = from
        while (i <= end - delim.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < end) {
                i += 2
                continue
            }
            if (ch == '`') {
                val tickCount = countRun(text, i, end, '`')
                val closeTick = findClosingBackticks(text, i + tickCount, end, tickCount)
                i = if (closeTick != -1) closeTick + tickCount else i + tickCount
                continue
            }
            if (text.regionMatches(i, delim, 0, delim.length)) {
                val prevNotSpace = i > from && !text[i - 1].isWhitespace()
                val nextOk = !wordBoundary || i + delim.length >= end || !text[i + delim.length].isLetterOrDigit()
                if (prevNotSpace && nextOk) {
                    return i
                }
            }
            i++
        }
        return -1
    }

    private fun findClosingSingleDelimiter(
        text: String,
        from: Int,
        end: Int,
        delimChar: Char,
        wordBoundary: Boolean,
    ): Int {
        if (from >= end || text[from].isWhitespace()) return -1
        var i = from
        while (i < end) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < end) {
                i += 2
                continue
            }
            if (ch == '`') {
                val tickCount = countRun(text, i, end, '`')
                val closeTick = findClosingBackticks(text, i + tickCount, end, tickCount)
                i = if (closeTick != -1) closeTick + tickCount else i + tickCount
                continue
            }
            if (ch == delimChar) {
                val run = countRun(text, i, end, delimChar)
                if (run == 1 || run == 3) {
                    val prevNotSpace = i > from && !text[i - 1].isWhitespace()
                    val nextOk = !wordBoundary || i + 1 >= end || !text[i + 1].isLetterOrDigit()
                    if (prevNotSpace && nextOk) {
                        return i
                    }
                }
                i += run
                continue
            }
            i++
        }
        return -1
    }

    private data class LinkMatch(
        val labelStart: Int,
        val labelEnd: Int,
        val url: String,
        val afterEnd: Int,
    )

    private fun matchLink(text: String, start: Int, end: Int): LinkMatch? {
        var i = start + 1
        var bracketDepth = 1
        while (i < end && bracketDepth > 0) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < end) {
                i += 2
                continue
            }
            if (ch == '`') {
                val ticks = countRun(text, i, end, '`')
                val close = findClosingBackticks(text, i + ticks, end, ticks)
                i = if (close != -1) close + ticks else i + ticks
                continue
            }
            if (ch == '[') bracketDepth++
            else if (ch == ']') bracketDepth--
            i++
        }
        if (bracketDepth != 0) return null
        val closeBracket = i - 1
        if (closeBracket + 1 >= end || text[closeBracket + 1] != '(') return null

        var j = closeBracket + 2
        var parenDepth = 1
        while (j < end && parenDepth > 0) {
            val ch = text[j]
            if (ch == '\\' && j + 1 < end) {
                j += 2
                continue
            }
            if (ch == '(') parenDepth++
            else if (ch == ')') parenDepth--
            j++
        }
        if (parenDepth != 0) return null
        val rawDest = text.substring(closeBracket + 2, j - 1).trim()
        if (rawDest.isEmpty()) return null

        // Strip optional title `"title"` / `'title'` and `<...>` wrapper.
        val destToken = if (rawDest.startsWith("<") && rawDest.contains('>')) {
            rawDest.substring(1, rawDest.indexOf('>')).trim()
        } else {
            rawDest.split(Regex("""\s+"""), limit = 2).first()
        }
        if (destToken.isEmpty()) return null

        return LinkMatch(
            labelStart = start + 1,
            labelEnd = closeBracket,
            url = destToken,
            afterEnd = j,
        )
    }

    private fun parseFenceStart(line: String): Triple<Char, Int, String>? {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces > 3 || leadingSpaces >= line.length) return null
        val ch = line[leadingSpaces]
        if (ch != '`' && ch != '~') return null
        val len = countRun(line, leadingSpaces, line.length, ch)
        if (len < 3) return null
        val info = line.substring(leadingSpaces + len).trim()
        if (ch == '`' && info.contains('`')) return null
        val language = info.split(Regex("""\s+"""), limit = 2).firstOrNull().orEmpty()
        return Triple(ch, len, language)
    }

    private fun isFenceEnd(line: String, fenceChar: Char, minLen: Int): Boolean {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces > 3 || leadingSpaces >= line.length) return false
        if (line[leadingSpaces] != fenceChar) return false
        val len = countRun(line, leadingSpaces, line.length, fenceChar)
        if (len < minLen) return false
        return line.substring(leadingSpaces + len).isBlank()
    }

    private fun isHorizontalRule(line: String): Boolean {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces > 3) return false
        val trimmed = line.substring(leadingSpaces).trimEnd()
        if (trimmed.length < 3) return false
        val marker = trimmed[0]
        if (marker != '-' && marker != '*' && marker != '_') return false
        var count = 0
        for (c in trimmed) {
            if (c == marker) count++
            else if (c != ' ' && c != '\t') return false
        }
        return count >= 3
    }

    private fun parseHeading(line: String): Pair<Int, String>? {
        val match = ATX_HEADING_REGEX.matchEntire(line) ?: return null
        val level = match.groupValues[1].length
        var content = match.groupValues[2].trim()
        // Strip optional closing ATX hashes preceded by space/tab.
        val stripped = content.trimEnd('#')
        if (stripped.length < content.length && (stripped.isEmpty() || stripped.last().isWhitespace())) {
            content = stripped.trimEnd()
        }
        return level to content
    }

    private fun isBlockquoteLine(line: String): Boolean {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        return leadingSpaces <= 3 && leadingSpaces < line.length && line[leadingSpaces] == '>'
    }

    private fun stripBlockquotePrefix(line: String): String {
        val leadingSpaces = line.takeWhile { it == ' ' }.length
        val afterCaret = leadingSpaces + 1
        return if (afterCaret < line.length && (line[afterCaret] == ' ' || line[afterCaret] == '\t')) {
            line.substring(afterCaret + 1)
        } else {
            line.substring(afterCaret)
        }
    }

    private fun isTableSeparator(line: String): Boolean {
        if (!line.contains('|')) return false
        val cells = splitTableRow(line)
        return cells.isNotEmpty() && cells.all { TABLE_CELL_SEPARATOR_REGEX.matches(it) }
    }

    private fun splitTableRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith('|')) trimmed = trimmed.substring(1)
        if (trimmed.endsWith('|') && !trimmed.endsWith("""\|""")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }
        if (trimmed.isEmpty()) return emptyList()

        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < trimmed.length) {
            val ch = trimmed[i]
            if (ch == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|') {
                current.append('|')
                i += 2
                continue
            }
            if (ch == '`') {
                val ticks = countRun(trimmed, i, trimmed.length, '`')
                val close = findClosingBackticks(trimmed, i + ticks, trimmed.length, ticks)
                val endIdx = if (close != -1) close + ticks else i + ticks
                current.append(trimmed, i, endIdx)
                i = endIdx
                continue
            }
            if (ch == '|') {
                cells += current.toString().trim()
                current.clear()
                i++
                continue
            }
            current.append(ch)
            i++
        }
        cells += current.toString().trim()
        return cells
    }

    private fun isListContinuationLine(line: String): Boolean {
        if (line.isBlank()) return false
        val indented = line.startsWith("  ") || line.startsWith("\t")
        if (!indented) return false
        return !isBlockInterruptor(line)
    }

    private fun isBlockInterruptor(line: String): Boolean {
        return parseFenceStart(line) != null ||
            isHorizontalRule(line) ||
            parseHeading(line) != null ||
            isBlockquoteLine(line) ||
            BULLET_ITEM_REGEX.matches(line) ||
            ORDERED_ITEM_REGEX.matches(line)
    }
}
