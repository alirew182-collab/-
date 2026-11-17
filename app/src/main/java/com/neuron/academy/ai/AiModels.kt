package com.neuron.academy.ai

import kotlinx.serialization.Serializable

/** Поддерживаемые провайдеры ИИ. */
@Serializable
enum class AiProviderType(
    val title: String,
    val defaultBaseUrl: String,
    val models: List<String>,
) {
    ANTHROPIC(
        "Anthropic (Claude)", "https://api.anthropic.com",
        listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-4-5", "claude-haiku-4-5"),
    ),
    OPENAI(
        "OpenAI", "https://api.openai.com/v1",
        listOf("gpt-5-mini", "gpt-5", "gpt-4.1-mini", "gpt-4o-mini"),
    ),
    GEMINI(
        "Google Gemini", "https://generativelanguage.googleapis.com",
        listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite"),
    ),
    OPENROUTER(
        "OpenRouter", "https://openrouter.ai/api/v1",
        listOf("anthropic/claude-sonnet-4.5", "google/gemini-2.5-flash", "openai/gpt-4o-mini", "deepseek/deepseek-chat"),
    ),
    DEEPSEEK(
        "DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner"),
    ),
    CUSTOM(
        "OpenAI-совместимый (свой URL)", "",
        emptyList(),
    );
}

/** Один API-ключ пользователя. Хранится только в зашифрованном хранилище. */
@Serializable
data class ApiKeyConfig(
    val id: String,
    val name: String,
    val provider: AiProviderType,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
) {
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
    val maskedKey: String get() = if (apiKey.length <= 8) "••••" else apiKey.take(4) + "••••" + apiKey.takeLast(4)
}

/** Функции приложения, для которых можно вручную назначить ключ. */
enum class AiFeature(val title: String) {
    CHAT("Чат с репетитором"),
    TASKS("Генерация задач"),
    CHECK("Проверка решений"),
    TESTS("Тесты и экзамены"),
    EXPLAIN("Объяснения и факты"),
    DUEL("Дуэль с ИИ"),
}

enum class KeySelectionMode(val title: String, val description: String) {
    PRIORITY("По приоритету", "Сначала ключ с наивысшим приоритетом, при ошибке — следующий"),
    ROUND_ROBIN("По кругу", "Запросы распределяются между ключами, чтобы не упираться в лимиты"),
    MANUAL("Вручную для каждой функции", "Например, чат через Claude, задачи через Gemini"),
}

data class AiMessage(val role: Role, val content: String) {
    enum class Role { USER, ASSISTANT }
}

data class AiRequest(
    val system: String,
    val messages: List<AiMessage>,
    val maxTokens: Int = 1500,
    val temperature: Double = 0.7,
)

data class AiResult(val text: String, val keyName: String, val model: String, val latencyMs: Long)

enum class AiErrorKind(val title: String) {
    RATE_LIMIT("Лимит запросов (429)"),
    AUTH("Неверный ключ (401/403)"),
    SERVER("Ошибка сервера"),
    TIMEOUT("Таймаут"),
    NETWORK("Нет сети"),
    BAD_REQUEST("Неверный запрос"),
    PARSE("Непонятный ответ"),
}

class AiException(
    val kind: AiErrorKind,
    val httpCode: Int? = null,
    override val message: String,
) : Exception(message)

class NoKeysException : Exception("Не добавлено ни одного активного API-ключа. Открой Настройки → API-ключи.")

class AllKeysFailedException(val errors: List<Pair<String, AiException>>) :
    Exception("Все ключи вернули ошибку:\n" + errors.joinToString("\n") { "• ${it.first}: ${it.second.kind.title} — ${it.second.message.take(120)}" })
