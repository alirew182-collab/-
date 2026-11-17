package com.neuron.academy.ai.providers

import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiMessage
import com.neuron.academy.ai.AiProviderType
import com.neuron.academy.ai.AiRequest
import com.neuron.academy.ai.ApiKeyConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Google Gemini API: POST /v1beta/models/{model}:generateContent */
class GeminiProvider(private val client: HttpClient) : AiProvider {
    override val type = AiProviderType.GEMINI

    override suspend fun complete(config: ApiKeyConfig, request: AiRequest): String {
        val base = config.effectiveBaseUrl
        val url = "$base/v1beta/models/${config.model}:generateContent"
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", request.system) } }
            }
            putJsonArray("contents") {
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiMessage.Role.USER) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", (request.maxTokens * 3).coerceAtLeast(2048))
                put("temperature", request.temperature)
            }
        }
        val json = safeCall {
            client.post(url) {
                header("x-goog-api-key", config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val parts = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        if (text.isNullOrBlank()) throw AiException(AiErrorKind.PARSE, 200, "Пустой ответ от Gemini")
        return text
    }
}
