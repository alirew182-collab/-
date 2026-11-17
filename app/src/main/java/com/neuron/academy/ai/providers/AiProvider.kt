package com.neuron.academy.ai.providers

import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiProviderType
import com.neuron.academy.ai.AiRequest
import com.neuron.academy.ai.ApiKeyConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException

/** Общий интерфейс провайдера ИИ. Каждая реализация знает формат своего API. */
interface AiProvider {
    val type: AiProviderType
    suspend fun complete(config: ApiKeyConfig, request: AiRequest): String
}

internal val aiJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Выполняет HTTP-вызов и переводит сетевые ошибки в [AiException]. */
internal suspend fun safeCall(block: suspend () -> HttpResponse): JsonObject {
    val response = try {
        block()
    } catch (e: HttpRequestTimeoutException) {
        throw AiException(AiErrorKind.TIMEOUT, null, "Сервер не ответил вовремя")
    } catch (e: SocketTimeoutException) {
        throw AiException(AiErrorKind.TIMEOUT, null, "Сервер не ответил вовремя")
    } catch (e: IOException) {
        throw AiException(AiErrorKind.NETWORK, null, e.message ?: "Ошибка сети")
    } catch (e: AiException) {
        throw e
    } catch (e: Exception) {
        if (e.javaClass.simpleName.contains("Timeout")) throw AiException(AiErrorKind.TIMEOUT, null, "Таймаут")
        throw AiException(AiErrorKind.NETWORK, null, e.message ?: e.javaClass.simpleName)
    }
    val code = response.status.value
    val body = response.bodyAsText()
    if (code !in 200..299) {
        val kind = when (code) {
            429 -> AiErrorKind.RATE_LIMIT
            401, 403 -> AiErrorKind.AUTH
            408 -> AiErrorKind.TIMEOUT
            in 500..599 -> AiErrorKind.SERVER
            else -> AiErrorKind.BAD_REQUEST
        }
        throw AiException(kind, code, extractErrorMessage(body) ?: "HTTP $code")
    }
    return try {
        aiJson.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
        throw AiException(AiErrorKind.PARSE, code, "Ответ не является JSON")
    }
}

private fun extractErrorMessage(body: String): String? = try {
    val obj = aiJson.parseToJsonElement(body).jsonObject
    val err: JsonElement? = obj["error"]
    when {
        err is JsonObject -> err["message"]?.jsonPrimitive?.content
        err != null -> err.jsonPrimitive.content
        else -> obj["message"]?.jsonPrimitive?.content
    }
} catch (e: Exception) {
    body.take(200).ifBlank { null }
}

/**
 * Выдаёт реализацию [AiProvider] по типу. [gigaClient] — отдельный HTTP-клиент
 * с доверием к сертификату Минцифры (нужен для серверов Сбера).
 */
class AiProviderFactory(private val client: HttpClient, gigaClient: HttpClient = client) {
    private val anthropic = AnthropicProvider(client)
    private val gemini = GeminiProvider(client)
    private val azure = AzureOpenAiProvider(client)
    private val giga = GigaChatProvider(gigaClient)
    private val openAiLike = AiProviderType.entries
        .filter { it.kind == com.neuron.academy.ai.ProviderKind.OPENAI_COMPATIBLE }
        .associateWith { OpenAiCompatibleProvider(client, it) }

    fun get(type: AiProviderType): AiProvider = when (type.kind) {
        com.neuron.academy.ai.ProviderKind.ANTHROPIC -> anthropic
        com.neuron.academy.ai.ProviderKind.GEMINI -> gemini
        com.neuron.academy.ai.ProviderKind.AZURE_OPENAI -> azure
        com.neuron.academy.ai.ProviderKind.GIGACHAT -> giga
        com.neuron.academy.ai.ProviderKind.OPENAI_COMPATIBLE -> openAiLike.getValue(type)
    }
}

/** Общий разбор ответа в формате Chat Completions. */
internal fun parseChatCompletion(json: kotlinx.serialization.json.JsonObject): String {
    val message = json["choices"]?.let { it as? kotlinx.serialization.json.JsonArray }?.firstOrNull()
        ?.let { it as? JsonObject }?.get("message") as? JsonObject
    val content = message?.get("content")
    val text = when (content) {
        is kotlinx.serialization.json.JsonPrimitive -> content.content
        is kotlinx.serialization.json.JsonArray -> content.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.content.orEmpty() }
        else -> null
    }
    if (text.isNullOrBlank()) throw AiException(AiErrorKind.PARSE, 200, "Пустой ответ модели")
    return text
}

/** Тело запроса Chat Completions. */
internal fun chatCompletionBody(model: String, request: AiRequest, useCompletionTokens: Boolean): String =
    kotlinx.serialization.json.buildJsonObject {
        put("model", kotlinx.serialization.json.JsonPrimitive(model))
        if (useCompletionTokens) {
            put("max_completion_tokens", kotlinx.serialization.json.JsonPrimitive(request.maxTokens * 3))
        } else {
            put("max_tokens", kotlinx.serialization.json.JsonPrimitive(request.maxTokens))
            put("temperature", kotlinx.serialization.json.JsonPrimitive(request.temperature))
        }
        put("messages", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonObject {
                put("role", kotlinx.serialization.json.JsonPrimitive("system"))
                put("content", kotlinx.serialization.json.JsonPrimitive(request.system))
            })
            request.messages.forEach { m ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", kotlinx.serialization.json.JsonPrimitive(if (m.role == com.neuron.academy.ai.AiMessage.Role.USER) "user" else "assistant"))
                    put("content", kotlinx.serialization.json.JsonPrimitive(m.content))
                })
            }
        })
    }.toString()
