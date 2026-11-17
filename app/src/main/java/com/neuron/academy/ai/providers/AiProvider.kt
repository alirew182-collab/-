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

class AiProviderFactory(private val client: HttpClient) {
    private val anthropic = AnthropicProvider(client)
    private val gemini = GeminiProvider(client)
    private val openAiLike = AiProviderType.entries
        .filter { it != AiProviderType.ANTHROPIC && it != AiProviderType.GEMINI }
        .associateWith { OpenAiCompatibleProvider(client, it) }

    fun get(type: AiProviderType): AiProvider = when (type) {
        AiProviderType.ANTHROPIC -> anthropic
        AiProviderType.GEMINI -> gemini
        else -> openAiLike.getValue(type)
    }
}
