package dev.balaji.moonrose.logtrain.api

import dev.balaji.moonrose.logtrain.auth.AppAuth
import dev.balaji.moonrose.logtrain.auth.AuthTokenStore
import dev.balaji.moonrose.logtrain.auth.MoonroseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString

/**
 * Bearer-token client for the moonrose /api/v1 endpoints. JSON is encoded and
 * decoded explicitly with [apiJson] rather than via ContentNegotiation so the
 * wire format is unambiguous on every target (including JS). The token and
 * base URL are read per request, so sign-out/sign-in and config changes take
 * effect immediately.
 */
class MoonroseApi internal constructor(
    private val tokenStore: AuthTokenStore,
    private val client: HttpClient = HttpClient(),
) {
    suspend fun listPlans(): List<Plan> =
        apiJson.decodeFromString(request(HttpMethod.Get, "/api/v1/plans"))

    suspend fun createPlan(name: String): Plan =
        apiJson.decodeFromString(
            request(HttpMethod.Post, "/api/v1/plans", apiJson.encodeToString(PlanRequest(PlanAttrs(name)))),
        )

    suspend fun updatePlan(id: Long, name: String): Plan =
        apiJson.decodeFromString(
            request(HttpMethod.Patch, "/api/v1/plans/$id", apiJson.encodeToString(PlanRequest(PlanAttrs(name)))),
        )

    suspend fun deletePlan(id: Long) {
        request(HttpMethod.Delete, "/api/v1/plans/$id")
    }

    suspend fun listRoutines(planId: Long): List<Routine> =
        apiJson.decodeFromString(request(HttpMethod.Get, "/api/v1/plans/$planId/routines"))

    suspend fun createRoutine(planId: Long, name: String): Routine =
        apiJson.decodeFromString(
            request(
                HttpMethod.Post,
                "/api/v1/plans/$planId/routines",
                apiJson.encodeToString(RoutineRequest(RoutineAttrs(name))),
            ),
        )

    suspend fun updateRoutine(id: Long, name: String): Routine =
        apiJson.decodeFromString(
            request(
                HttpMethod.Patch,
                "/api/v1/routines/$id",
                apiJson.encodeToString(RoutineRequest(RoutineAttrs(name))),
            ),
        )

    suspend fun deleteRoutine(id: Long) {
        request(HttpMethod.Delete, "/api/v1/routines/$id")
    }

    private suspend fun request(method: HttpMethod, path: String, jsonBody: String? = null): String {
        val response = client.request("${MoonroseConfig.baseUrl}$path") {
            this.method = method
            tokenStore.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (jsonBody != null) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, parseErrorMessages(response.status.value, body))
        }
        return body
    }
}

object AppApi {
    val api by lazy { MoonroseApi(AppAuth.tokenStore) }
}
