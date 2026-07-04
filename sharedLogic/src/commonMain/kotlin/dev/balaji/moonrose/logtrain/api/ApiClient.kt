package dev.balaji.moonrose.logtrain.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val apiJson = Json { ignoreUnknownKeys = true }

class ApiException(val status: Int, val messages: List<String>) :
    Exception("HTTP $status: ${messages.joinToString()}")

// Moonrose renders errors as either {"error": "..."} (401/404) or
// {"errors": [...]} (422).
internal fun parseErrorMessages(status: Int, body: String): List<String> =
    try {
        val obj = apiJson.parseToJsonElement(body).jsonObject
        obj["errors"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: obj["error"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: listOf("Request failed")
    } catch (_: Exception) {
        listOf("Request failed")
    }
