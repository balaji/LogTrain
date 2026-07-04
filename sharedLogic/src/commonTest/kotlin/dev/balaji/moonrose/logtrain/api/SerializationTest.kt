package dev.balaji.moonrose.logtrain.api

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

// Payloads mirror moonrose's PlanBlueprint / RoutineBlueprint output exactly,
// including the fields the client drops (timestamps, user_id).
class SerializationTest {
    @Test
    fun decodesPlanListFromBlueprintJson() {
        val json = """
            [{"id":1,"name":"Strength Block","user_id":7,
              "created_at":"2026-07-04T10:00:00.000Z","updated_at":"2026-07-04T10:00:00.000Z"}]
        """.trimIndent()
        val plans = apiJson.decodeFromString<List<Plan>>(json)
        assertEquals(listOf(Plan(id = 1, name = "Strength Block")), plans)
    }

    @Test
    fun decodesRoutineFromBlueprintJson() {
        val json = """
            {"id":5,"name":"Push Day","plan_id":1,"position":2,
             "created_at":"2026-07-04T10:00:00.000Z","updated_at":"2026-07-04T10:00:00.000Z"}
        """.trimIndent()
        val routine = apiJson.decodeFromString<Routine>(json)
        assertEquals(Routine(id = 5, name = "Push Day", planId = 1, position = 2), routine)
    }

    @Test
    fun encodesPlanRequestWithRailsNesting() {
        val body = apiJson.encodeToString(PlanRequest(PlanAttrs("Strength Block")))
        assertEquals("""{"plan":{"name":"Strength Block"}}""", body)
    }

    @Test
    fun encodesRoutineRequestWithRailsNesting() {
        val body = apiJson.encodeToString(RoutineRequest(RoutineAttrs("Push Day")))
        assertEquals("""{"routine":{"name":"Push Day"}}""", body)
    }

    @Test
    fun parsesSingleErrorBody() {
        assertEquals(listOf("Not found"), parseErrorMessages(404, """{"error":"Not found"}"""))
    }

    @Test
    fun parsesValidationErrorsBody() {
        assertEquals(
            listOf("Name can't be blank"),
            parseErrorMessages(422, """{"errors":["Name can't be blank"]}"""),
        )
    }

    @Test
    fun fallsBackOnUnparseableErrorBody() {
        assertEquals(listOf("Request failed"), parseErrorMessages(500, "<html>oops</html>"))
    }
}
