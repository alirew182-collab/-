package com.neuron.academy.ai

import kotlinx.serialization.Serializable

/** Как устроен API провайдера. */
enum class ProviderKind { ANTHROPIC, GEMINI, OPENAI_COMPATIBLE, AZURE_OPENAI, GIGACHAT }

/**
 * Поддерживаемые провайдеры ИИ. Большинство современных сервисов совместимы с OpenAI Chat Completions,
 * поэтому для них достаточно base URL и списка моделей. Имена констант не менять — они сохраняются в хранилище.
 */
@Serializable
enum class AiProviderType(
    val title: String,
    val defaultBaseUrl: String,
    val models: List<String>,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val hint: String = "",
    val keyRequired: Boolean = true,
    val baseUrlRequired: Boolean = false,
) {
    // ---- исходные провайдеры ----
    ANTHROPIC(
        "Anthropic (Claude)", "https://api.anthropic.com",
        listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-4-5", "claude-haiku-4-5"),
        ProviderKind.ANTHROPIC, "Ключ: console.anthropic.com → API Keys",
    ),
    OPENAI(
        "OpenAI (ChatGPT)", "https://api.openai.com/v1",
        listOf("gpt-5-mini", "gpt-5", "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini", "gpt-4o-mini"),
        hint = "Ключ: platform.openai.com → API keys",
    ),
    GEMINI(
        "Google Gemini", "https://generativelanguage.googleapis.com",
        listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite"),
        ProviderKind.GEMINI, "Ключ: aistudio.google.com → Get API key (есть бесплатный лимит)",
    ),
    OPENROUTER(
        "OpenRouter", "https://openrouter.ai/api/v1",
        listOf("anthropic/claude-sonnet-4.5", "google/gemini-2.5-flash", "openai/gpt-4o-mini", "deepseek/deepseek-chat", "meta-llama/llama-3.3-70b-instruct:free"),
        hint = "Один ключ — сотни моделей. Ключ: openrouter.ai/keys",
    ),
    DEEPSEEK(
        "DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner"),
        hint = "Ключ: platform.deepseek.com",
    ),
    CUSTOM(
        "Свой OpenAI-совместимый сервер", "",
        emptyList(),
        hint = "Любой сервер с /chat/completions: vLLM, LiteLLM, LocalAI и т. д.",
        keyRequired = false, baseUrlRequired = true,
    ),

    // ---- новые облачные провайдеры ----
    XAI("xAI (Grok)", "https://api.x.ai/v1", listOf("grok-4", "grok-3", "grok-3-mini"), hint = "Ключ: console.x.ai"),
    MISTRAL("Mistral AI", "https://api.mistral.ai/v1", listOf("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest", "open-mistral-nemo"), hint = "Ключ: console.mistral.ai"),
    GROQ("Groq (очень быстро)", "https://api.groq.com/openai/v1", listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "moonshotai/kimi-k2-instruct", "qwen/qwen3-32b"), hint = "Ключ: console.groq.com (есть бесплатный лимит)"),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1", listOf("llama-3.3-70b", "gpt-oss-120b", "qwen-3-32b"), hint = "Ключ: cloud.cerebras.ai"),
    TOGETHER("Together AI", "https://api.together.xyz/v1", listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct-Turbo"), hint = "Ключ: api.together.ai"),
    FIREWORKS("Fireworks AI", "https://api.fireworks.ai/inference/v1", listOf("accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/deepseek-v3", "accounts/fireworks/models/qwen2p5-72b-instruct"), hint = "Ключ: fireworks.ai → API Keys"),
    PERPLEXITY("Perplexity", "https://api.perplexity.ai", listOf("sonar", "sonar-pro", "sonar-reasoning"), hint = "Ключ: perplexity.ai → Settings → API"),
    COHERE("Cohere", "https://api.cohere.ai/compatibility/v1", listOf("command-a-03-2025", "command-r-plus-08-2024", "command-r7b-12-2024"), hint = "Ключ: dashboard.cohere.com"),
    AI21("AI21 (Jamba)", "https://api.ai21.com/studio/v1", listOf("jamba-large", "jamba-mini"), hint = "Ключ: studio.ai21.com"),
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai", listOf("meta-llama/Llama-3.3-70B-Instruct", "deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"), hint = "Ключ: deepinfra.com/dash/api_keys"),
    HUGGINGFACE("Hugging Face", "https://router.huggingface.co/v1", listOf("meta-llama/Llama-3.3-70B-Instruct", "Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3-0324"), hint = "Токен: huggingface.co/settings/tokens"),
    NVIDIA("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", listOf("meta/llama-3.3-70b-instruct", "deepseek-ai/deepseek-r1", "nvidia/llama-3.1-nemotron-70b-instruct"), hint = "Ключ: build.nvidia.com"),
    SAMBANOVA("SambaNova", "https://api.sambanova.ai/v1", listOf("Meta-Llama-3.3-70B-Instruct", "DeepSeek-V3-0324", "Qwen3-32B"), hint = "Ключ: cloud.sambanova.ai"),
    NOVITA("Novita AI", "https://api.novita.ai/v3/openai", listOf("meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-v3-0324", "qwen/qwen-2.5-72b-instruct"), hint = "Ключ: novita.ai"),
    HYPERBOLIC("Hyperbolic", "https://api.hyperbolic.xyz/v1", listOf("meta-llama/Llama-3.3-70B-Instruct", "deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"), hint = "Ключ: app.hyperbolic.xyz"),
    QWEN("Alibaba Qwen (DashScope)", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen3-235b-a22b"), hint = "Ключ: Alibaba Cloud Model Studio"),
    MOONSHOT("Moonshot (Kimi)", "https://api.moonshot.ai/v1", listOf("kimi-k2-0711-preview", "moonshot-v1-8k", "moonshot-v1-32k"), hint = "Ключ: platform.moonshot.ai"),
    ZHIPU("Zhipu (GLM)", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-4.5", "glm-4-plus", "glm-4-flash"), hint = "Ключ: open.bigmodel.cn"),
    MINIMAX("MiniMax", "https://api.minimax.io/v1", listOf("MiniMax-M1", "MiniMax-Text-01"), hint = "Ключ: minimax.io"),
    YANDEX(
        "YandexGPT", "https://llm.api.cloud.yandex.net/v1",
        listOf("gpt://<folder_id>/yandexgpt/latest", "gpt://<folder_id>/yandexgpt-lite/latest"),
        hint = "API-ключ сервисного аккаунта Yandex Cloud. В модели замените <folder_id> на ID каталога",
    ),
    GIGACHAT(
        "GigaChat (Сбер)", "https://gigachat.devices.sberbank.ru/api/v1",
        listOf("GigaChat-2", "GigaChat-2-Pro", "GigaChat-2-Max", "GigaChat"),
        ProviderKind.GIGACHAT, "Вставьте «Authorization key» из developers.sber.ru (scope GIGACHAT_API_PERS)",
    ),
    AZURE_OPENAI(
        "Azure OpenAI", "",
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1"),
        ProviderKind.AZURE_OPENAI, "Base URL: https://<ресурс>.openai.azure.com, модель = имя деплоймента",
        baseUrlRequired = true,
    ),

    // ---- локальные модели ----
    OLLAMA(
        "Ollama (локально)", "http://192.168.1.100:11434/v1",
        listOf("llama3.2", "qwen2.5", "gemma3", "mistral", "deepseek-r1"),
        hint = "Запустите Ollama на компьютере в той же Wi-Fi сети и укажите его IP (OLLAMA_HOST=0.0.0.0)",
        keyRequired = false, baseUrlRequired = true,
    ),
    LM_STUDIO(
        "LM Studio (локально)", "http://192.168.1.100:1234/v1",
        listOf("local-model"),
        hint = "Включите сервер в LM Studio (Serve on Local Network) и укажите IP компьютера",
        keyRequired = false, baseUrlRequired = true,
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

class NoKeysException : Exception("Встроенный ИИ ещё не установлен. Открой «Настройки ИИ» и скачай модель — один раз, дальше работает без интернета.")

class AllKeysFailedException(val errors: List<Pair<String, AiException>>) :
    Exception("Все ключи вернули ошибку:\n" + errors.joinToString("\n") { "• ${it.first}: ${it.second.kind.title} — ${it.second.message.take(120)}" })
