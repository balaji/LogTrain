package dev.balaji.moonrose.logtrain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {
    @Test
    fun blankInputReturnsEmptyList() {
        assertEquals(emptyList(), MarkdownParser.parse(""))
        assertEquals(emptyList(), MarkdownParser.parse("   \n\n  "))
    }

    @Test
    fun parsesCoachFormResponseWithDividersHeadingsAndBoldBullets() {
        val input = """
            The Romanian Deadlift targets the posterior chain.

            ---

            ### Setup & Positioning
            - **Starting stance**: Feet hip-width apart.
            - **Bar placement**: Grip just outside knees.

            ---
        """.trimIndent()

        val blocks = MarkdownParser.parse(input)
        assertEquals(
            listOf(
                MarkdownBlock.KIND_PARAGRAPH,
                MarkdownBlock.KIND_DIVIDER,
                MarkdownBlock.KIND_HEADING,
                MarkdownBlock.KIND_BULLET_ITEM,
                MarkdownBlock.KIND_BULLET_ITEM,
                MarkdownBlock.KIND_DIVIDER,
            ),
            blocks.map { it.kind },
        )

        assertEquals(3, blocks[2].level)
        assertEquals(listOf(MarkdownInline("Setup & Positioning")), blocks[2].inlines)
        assertEquals(
            listOf(
                MarkdownInline("Starting stance", bold = true),
                MarkdownInline(": Feet hip-width apart."),
            ),
            blocks[3].inlines,
        )
    }

    @Test
    fun parsesHeadingsAndLeavesHashWithoutSpaceAsParagraph() {
        val blocks = MarkdownParser.parse("# Title\n## Subtitle ##\n#notAHeading\n####### TooDeep")
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.KIND_HEADING, blocks[0].kind)
        assertEquals(1, blocks[0].level)
        assertEquals("Title", blocks[0].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_HEADING, blocks[1].kind)
        assertEquals(2, blocks[1].level)
        assertEquals("Subtitle", blocks[1].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_PARAGRAPH, blocks[2].kind)
        assertEquals("#notAHeading\n####### TooDeep", blocks[2].inlines.single().text)
    }

    @Test
    fun parsesNestedBulletAndOrderedListsWithContinuations() {
        val input = """
            - Top item
              wrapped continuation
              - Nested bullet
            1. First step
            2) Second step
            Summary note
        """.trimIndent()

        val blocks = MarkdownParser.parse(input)
        assertEquals(5, blocks.size)
        assertEquals(MarkdownBlock.KIND_BULLET_ITEM, blocks[0].kind)
        assertEquals(0, blocks[0].indentLevel)
        assertEquals("Top item wrapped continuation", blocks[0].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_BULLET_ITEM, blocks[1].kind)
        assertEquals(1, blocks[1].indentLevel)
        assertEquals("Nested bullet", blocks[1].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_ORDERED_ITEM, blocks[2].kind)
        assertEquals("1.", blocks[2].marker)
        assertEquals("First step", blocks[2].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_ORDERED_ITEM, blocks[3].kind)
        assertEquals("2)", blocks[3].marker)
        assertEquals("Second step", blocks[3].inlines.single().text)

        assertEquals(MarkdownBlock.KIND_PARAGRAPH, blocks[4].kind)
        assertEquals("Summary note", blocks[4].inlines.single().text)
    }

    @Test
    fun parsesBlockquotesAndHorizontalRules() {
        val input = """
            > Keep your spine neutral.
            > Push hips back.

            * * *
            ___
        """.trimIndent()

        val blocks = MarkdownParser.parse(input)
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.KIND_QUOTE, blocks[0].kind)
        assertEquals("Keep your spine neutral.\nPush hips back.", blocks[0].inlines.single().text)
        assertEquals(MarkdownBlock.KIND_DIVIDER, blocks[1].kind)
        assertEquals(MarkdownBlock.KIND_DIVIDER, blocks[2].kind)
    }

    @Test
    fun parsesFencedAndStreamingUnclosedCodeBlocks() {
        val closed = """
            ```kotlin
            val reps = 8
            ```
            ~~~
            3x8 @ 80 kg
        """.trimIndent()

        val blocks = MarkdownParser.parse(closed)
        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.KIND_CODE_BLOCK, blocks[0].kind)
        assertEquals("kotlin", blocks[0].language)
        assertEquals("val reps = 8", blocks[0].code)

        assertEquals(MarkdownBlock.KIND_CODE_BLOCK, blocks[1].kind)
        assertEquals("", blocks[1].language)
        assertEquals("3x8 @ 80 kg", blocks[1].code)
    }

    @Test
    fun parsesPipeTablesWithInlineFormatting() {
        val input = """
            | Exercise | Sets | Notes |
            | :--- | :---: | ---: |
            | **RDL** | `3x8` | Slow \| eccentric |
            | Squat | 3x5 | |
        """.trimIndent()

        val blocks = MarkdownParser.parse(input)
        assertEquals(1, blocks.size)
        val table = blocks.single()
        assertEquals(MarkdownBlock.KIND_TABLE, table.kind)
        assertEquals(listOf("Exercise", "Sets", "Notes"), table.tableHeaders.map { it.inlines.single().text })
        assertEquals(2, table.tableRows.size)
        assertEquals(MarkdownInline("RDL", bold = true), table.tableRows[0].cells[0].inlines.single())
        assertEquals(MarkdownInline("3x8", code = true), table.tableRows[0].cells[1].inlines.single())
        assertEquals(MarkdownInline("Slow | eccentric"), table.tableRows[0].cells[2].inlines.single())
        assertTrue(table.tableRows[1].cells[2].inlines.isEmpty())
    }

    @Test
    fun parsesInlineEmphasisCodeLinksEscapesAndStreamingPartialMarkers() {
        assertEquals(
            listOf(
                MarkdownInline("both", bold = true, italic = true),
                MarkdownInline(" and "),
                MarkdownInline("bold", bold = true, italic = true),
                MarkdownInline(" italic", italic = true),
            ),
            MarkdownParser.parseInlines("***both*** and ***bold** italic*"),
        )

        assertEquals(
            listOf(
                MarkdownInline("old", strikethrough = true),
                MarkdownInline(" "),
                MarkdownInline("**code**", code = true),
                MarkdownInline(" "),
                MarkdownInline("guide", bold = true, linkUrl = "https://example.com/rdl"),
            ),
            MarkdownParser.parseInlines("~~old~~ `**code**` [**guide**](https://example.com/rdl)"),
        )

        // Snake case, math expressions, escapes, and unclosed streaming markers stay intact.
        assertEquals(
            listOf(MarkdownInline("romanian_deadlift 2 * 3 * 4 *literal* **unclosed")),
            MarkdownParser.parseInlines("""romanian_deadlift 2 * 3 * 4 \*literal\* **unclosed"""),
        )
    }
}
