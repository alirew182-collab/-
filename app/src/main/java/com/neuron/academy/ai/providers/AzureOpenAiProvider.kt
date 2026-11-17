package com.neuron.academy.ai.providers

import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiProviderType
import com.neuron.academy.ai.AiRequest
import com.neuron.academy.ai.ApiKeyConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Azure OpenAI: https://<ресурс>.openai.azure.com/openai/deployments/<деплоймент>/chat/completions?api-version=…
 * Поле «модель» = имя деплоймента. Можно указать и полный URL деплоймента в base URL.
 */
class AzureOpenAiProvider(private val client: HttpClient) : AiProvider {
    override val type = AiProviderType.AZURE_OPENAI

    override suspend fun complete(config: ApiKeyConfig, request: AiRequest): String {
        val base = config.effectiveBaseUrl
        if (base.isBlank()) throw AiException(AiErrorKind.BAD_REQUEST, null, "Укажите base URL ресурса Azure")
        val deploymentUrl = when {
            base.contains("/deployments/") -> base.substringBefore("/chat/completions")
            else -> "$base/openai/deployments/${config.model}"
        }
        val url = "$deploymentUrl/chat/completions?api-version=2024-10-21"
        val json = safeCall {
            client.post(url) {
                header("api-key", config.apiKey)
                contentType(ContentType.Application.Json)
                setBody(chatCompletionBody(config.model, request, useCompletionTokens = true))
            }
        }
        return parseChatCompletion(json)
    }
}
