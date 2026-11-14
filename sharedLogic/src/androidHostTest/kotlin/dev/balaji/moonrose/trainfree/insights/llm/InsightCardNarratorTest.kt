package dev.balaji.moonrose.trainfree.insights.llm

import dev.balaji.moonrose.trainfree.insights.InsightCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class InsightCardNarratorTest {
    private fun card(kind: String, id: String = "test", exerciseName: String? = "Bench Press") =
        InsightCard(
            id = id,
            kind = kind,
            exerciseName = exerciseName,
            headlineKey = "",
            headlineArgs = emptyList(),
            detailKey = "",
            detailArgs = emptyList(),
            trend = "none",
            spark = emptyList(),
        )

    private val profileLine = "User: Alex, age 30, male, 80 kg. Weights are in kg."

    @Test
    fun `each kind gets its own instructions and task line`() {
        val context = "week of Jan 1: est. 1RM 100 kg"
        val progression = InsightCardNarrator.requestFor(card("progression"), context, profileLine)
        val plateau = InsightCardNarrator.requestFor(card("plateau"), context, profileLine)
        val volume = InsightCardNarrator.requestFor(card("volume", exerciseName = null), context, profileLine)

        assertNotEquals(progression.instructions, plateau.instructions)
        assertTrue(plateau.instructions.contains("plateaued"))
        assertTrue(plateau.instructions.contains("No medical advice"))
        assertTrue(progression.prompt.contains("estimated 1RM for Bench Press"))
        assertTrue(volume.instructions.contains("training volume"))
        assertTrue(volume.prompt.contains(context))
        assertTrue(volume.prompt.contains(profileLine))
        assertTrue(volume.prompt.endsWith("Write the insight."))
    }

    @Test
    fun `identical inputs hash identically and changed context changes the hash`() {
        val a = InsightCardNarrator.requestFor(card("volume"), "100 kg", profileLine)
        val b = InsightCardNarrator.requestFor(card("volume"), "100 kg", profileLine)
        val c = InsightCardNarrator.requestFor(card("volume"), "110 kg", profileLine)
        val d = InsightCardNarrator.requestFor(card("plateau"), "100 kg", profileLine)

        assertEquals(a.statsHash, b.statsHash)
        assertNotEquals(a.statsHash, c.statsHash)
        assertNotEquals(a.statsHash, d.statsHash)
    }

    @Test
    fun `multi exercise comparison cards get no narration`() = runBlocking {
        // contextFor's else-branch bails before touching any repository, so
        // no database is needed here.
        assertNull(InsightCardNarrator.buildRequest(card("progression_multi", exerciseName = null)))
    }

    @Test
    fun `fnv1a64 matches the published test vectors`() {
        assertEquals("cbf29ce484222325", InsightCardNarrator.fnv1a64(""))
        assertEquals("af63dc4c8601ec8c", InsightCardNarrator.fnv1a64("a"))
        assertEquals("85944171f73967e8", InsightCardNarrator.fnv1a64("foobar"))
    }
}
