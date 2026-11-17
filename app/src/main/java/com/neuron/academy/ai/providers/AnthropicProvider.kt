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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Anthropic Messages API: POST /v1/messages */
class AnthropicProvider(private val client: HttpClient) : AiProvider {
    override val type = AiProviderType.ANTHROPIC

    override suspend fun complete(config: ApiKeyConfig, request: AiRequest): String {
        val base = config.effectiveBaseUrl
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("system", request.system)
            putJsonArray("messages") {
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == AiMessage.Role.USER) "user" else "assistant")
                        put("content", m.content)
                    }
                }
            }
        }
        val json = safeCall {
            client.post(url) {
                header("x-api-key", config.apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = json["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        if (text.isNullOrBlank()) throw AiException(AiErrorKind.PARSE, 200, "Пустой ответ от Claude")
        return text
    }
}
