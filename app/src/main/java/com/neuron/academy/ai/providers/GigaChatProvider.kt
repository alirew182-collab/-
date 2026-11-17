package com.neuron.academy.ai.providers

import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiProviderType
import com.neuron.academy.ai.AiRequest
import com.neuron.academy.ai.ApiKeyConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * GigaChat (Сбер). Сначала «Authorization key» обменивается на access token (живёт 30 минут),
 * затем запрос в формате, близком к Chat Completions.
 */
class GigaChatProvider(private val client: HttpClient) : AiProvider {
    override val type = AiProviderType.GIGACHAT

    private data class Token(val value: String, val expiresAt: Long)
    private val tokens = mutableMapOf<String, Token>()
    private val mutex = Mutex()

    private suspend fun token(authKey: String): String = mutex.withLock {
        tokens[authKey]?.takeIf { it.expiresAt - 60_000 > System.currentTimeMillis() }?.let { return@withLock it.value }
        val scope = if (authKey.contains(":")) authKey.substringAfter(":") else "GIGACHAT_API_PERS"
        val key = authKey.substringBefore(":")
        val json = safeCall {
            client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
                header("Authorization", "Basic $key")
                header("RqUID", UUID.randomUUID().toString())
                header("Accept", "application/json")
                setBody(FormDataContent(Parameters.build { append("scope", scope) }))
            }
        }
        val value = json["access_token"]?.jsonPrimitive?.content
            ?: throw AiException(AiErrorKind.AUTH, 401, "GigaChat не выдал токен")
        val exp = json["expires_at"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() + 25 * 60_000)
        tokens[authKey] = Token(value, exp)
        value
    }

    override suspend fun complete(config: ApiKeyConfig, request: AiRequest): String {
        val access = token(config.apiKey.trim())
        val json = safeCall {
            client.post("${config.effectiveBaseUrl}/chat/completions") {
                header("Authorization", "Bearer $access")
                contentType(ContentType.Application.Json)
                setBody(chatCompletionBody(config.model, request, useCompletionTokens = false))
            }
        }
        return parseChatCompletion(json)
    }
}
