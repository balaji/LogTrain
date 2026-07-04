package dev.balaji.moonrose.logtrain.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response models for the moonrose /api/v1 JSON (snake_case fields; extra
// fields like timestamps are dropped via ignoreUnknownKeys). Not @JsExport:
// the api package uses suspend functions, which cannot be exported to JS.

@Serializable
data class Plan(
    val id: Long,
    val name: String,
)

@Serializable
data class Routine(
    val id: Long,
    val name: String,
    @SerialName("plan_id") val planId: Long,
    val position: Int,
)

// Request bodies. Rails' `params.require(:plan)` / `require(:routine)`
// demands this exact nesting.

@Serializable
internal data class PlanRequest(val plan: PlanAttrs)

@Serializable
internal data class PlanAttrs(val name: String)

@Serializable
internal data class RoutineRequest(val routine: RoutineAttrs)

@Serializable
internal data class RoutineAttrs(val name: String)
