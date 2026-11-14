package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.chat.MarkdownBlock
import dev.balaji.moonrose.trainfree.chat.MarkdownInline
import dev.balaji.moonrose.trainfree.chat.MarkdownParser
import dev.balaji.moonrose.trainfree.chat.MarkdownTableCell
import dev.balaji.moonrose.trainfree.chat.MarkdownTableRow
import kotlin.math.max

/**
 * Renders parsed [MarkdownParser] blocks using Material 3 typography and
 * native Compose layout primitives.
 */
@Composable
internal fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            when (block.kind) {
                MarkdownBlock.KIND_HEADING -> {
                    val headingStyle = headingStyleFor(block.level)
                    Text(
                        text = buildMarkdownAnnotatedString(
                            inlines = block.inlines,
                            codeBackground = codeBackground,
                            linkColor = linkColor,
                        ),
                        style = headingStyle,
                        color = color,
                        modifier = if (index > 0) Modifier.padding(top = 4.dp) else Modifier,
                    )
                }

                MarkdownBlock.KIND_DIVIDER -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }

                MarkdownBlock.KIND_BULLET_ITEM -> {
                    Row(
                        modifier = Modifier.padding(start = (block.indentLevel * 14).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (block.indentLevel > 0) "◦" else "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText,
                        )
                        Text(
                            text = buildMarkdownAnnotatedString(
                                inlines = block.inlines,
                                codeBackground = codeBackground,
                                linkColor = linkColor,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                MarkdownBlock.KIND_ORDERED_ITEM -> {
                    Row(
                        modifier = Modifier.padding(start = (block.indentLevel * 14).dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = block.marker,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText,
                        )
                        Text(
                            text = buildMarkdownAnnotatedString(
                                inlines = block.inlines,
                                codeBackground = codeBackground,
                                linkColor = linkColor,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                MarkdownBlock.KIND_QUOTE -> {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(1.5.dp),
                                ),
                        )
                        Text(
                            text = buildMarkdownAnnotatedString(
                                inlines = block.inlines,
                                codeBackground = codeBackground,
                                linkColor = linkColor,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                MarkdownBlock.KIND_CODE_BLOCK -> {
                    Surface(
                        color = codeBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (block.language.isNotEmpty()) {
                                Text(
                                    text = block.language,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryText,
                                )
                            }
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Text(
                                    text = block.code,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = color,
                                )
                            }
                        }
                    }
                }

                MarkdownBlock.KIND_TABLE -> {
                    MarkdownTable(
                        headers = block.tableHeaders,
                        rows = block.tableRows,
                        textColor = color,
                        codeBackground = codeBackground,
                        linkColor = linkColor,
                    )
                }

                else -> {
                    Text(
                        text = buildMarkdownAnnotatedString(
                            inlines = block.inlines,
                            codeBackground = codeBackground,
                            linkColor = linkColor,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun headingStyleFor(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MarkdownTable(
    headers: List<MarkdownTableCell>,
    rows: List<MarkdownTableRow>,
    textColor: Color,
    codeBackground: Color,
    linkColor: Color,
) {
    val columnCount = headers.size
    if (columnCount == 0) return

    Surface(
        color = codeBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            val allRows = listOf(headers) + rows.map { it.cells }
            Layout(
                content = {
                    allRows.forEachIndexed { rowIndex, rowCells ->
                        val isHeader = rowIndex == 0
                        val cellStyle = if (isHeader) {
                            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.bodySmall
                        }
                        for (col in 0 until columnCount) {
                            val cellInlines = rowCells.getOrNull(col)?.inlines.orEmpty()
                            Text(
                                text = buildMarkdownAnnotatedString(
                                    inlines = cellInlines,
                                    codeBackground = codeBackground,
                                    linkColor = linkColor,
                                ),
                                style = cellStyle,
                                color = textColor,
                            )
                        }
                    }
                },
            ) { measurables, _ ->
                val colGap = 14.dp.roundToPx()
                val rowGap = 8.dp.roundToPx()
                val maxCellWidth = 220.dp.roundToPx()
                val cellConstraints = Constraints(maxWidth = maxCellWidth)

                val placeables = measurables.map { it.measure(cellConstraints) }
                val totalRows = allRows.size
                val colWidths = IntArray(columnCount)
                val rowHeights = IntArray(totalRows)

                for (r in 0 until totalRows) {
                    for (c in 0 until columnCount) {
                        val p = placeables[r * columnCount + c]
                        colWidths[c] = max(colWidths[c], p.width)
                        rowHeights[r] = max(rowHeights[r], p.height)
                    }
                }

                val totalWidth = colWidths.sum() + max(0, columnCount - 1) * colGap
                val totalHeight = rowHeights.sum() + max(0, totalRows - 1) * rowGap

                layout(totalWidth, totalHeight) {
                    var y = 0
                    for (r in 0 until totalRows) {
                        var x = 0
                        for (c in 0 until columnCount) {
                            placeables[r * columnCount + c].placeRelative(x, y)
                            x += colWidths[c] + colGap
                        }
                        y += rowHeights[r] + rowGap
                    }
                }
            }
        }
    }
}

private fun buildMarkdownAnnotatedString(
    inlines: List<MarkdownInline>,
    codeBackground: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    for (run in inlines) {
        val textDecoration = when {
            run.strikethrough -> TextDecoration.LineThrough
            else -> null
        }
        val spanStyle = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            textDecoration = textDecoration,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            background = if (run.code) codeBackground else Color.Unspecified,
        )

        if (isSafeUrl(run.linkUrl)) {
            val link = LinkAnnotation.Url(
                url = run.linkUrl,
                styles = TextLinkStyles(
                    style = spanStyle.copy(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            )
            withLink(link) {
                append(run.text)
            }
        } else {
            withStyle(spanStyle) {
                append(run.text)
            }
        }
    }
}

private fun isSafeUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("mailto:")
}
