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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Chat Completions API. Подходит для OpenAI, OpenRouter, DeepSeek и любого
 * OpenAI-совместимого сервера (LM Studio, Ollama, vLLM, Groq, Together...).
 */
class OpenAiCompatibleProvider(
    private val client: HttpClient,
    override val type: AiProviderType,
) : AiProvider {

    override suspend fun complete(config: ApiKeyConfig, request: AiRequest): String {
        val base = config.effectiveBaseUrl
        if (base.isBlank()) throw AiException(AiErrorKind.BAD_REQUEST, null, "Не указан base URL")
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = buildJsonObject {
            put("model", config.model)
            if (type == AiProviderType.OPENAI) {
                // Новые модели OpenAI принимают только max_completion_tokens и температуру по умолчанию
                put("max_completion_tokens", request.maxTokens * 3)
            } else {
                put("max_tokens", request.maxTokens)
                put("temperature", request.temperature)
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", request.system)
                }
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
                if (config.apiKey.isNotBlank()) {
                    when (type) {
                        // Yandex Cloud принимает API-ключ сервисного аккаунта со схемой Api-Key
                        AiProviderType.YANDEX -> header("Authorization", if (config.apiKey.startsWith("t1.") || config.apiKey.startsWith("Bearer")) "Bearer ${config.apiKey.removePrefix("Bearer ")}" else "Api-Key ${config.apiKey}")
                        else -> header("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                if (type == AiProviderType.OPENROUTER) {
                    header("HTTP-Referer", "https://neuron.academy.app")
                    header("X-Title", "Neuron Academy")
                }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val message = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val content = message?.get("content")
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            else -> null
        }
        if (text.isNullOrBlank()) throw AiException(AiErrorKind.PARSE, 200, "Пустой ответ модели")
        return text
    }
}
