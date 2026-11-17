package com.neuron.academy.ai.local

/**
 * Модели, которые работают прямо на телефоне через LiteRT-LM (формат .litertlm).
 * Источник — открытые репозитории litert-community на Hugging Face; ссылки закреплены за
 * конкретной версией файла, чтобы размер и содержимое не менялись.
 */
enum class LocalModel(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    /** С какого объёма ОЗУ модель работает комфортно. */
    val minRamGb: Int,
    /** Пробовать ускорение на видеокарте. */
    val gpu: Boolean,
    /** Модель с рекуррентным состоянием: на каждый запрос нужен новый движок (особенность рантайма). */
    val freshEngine: Boolean,
    val contextTokens: Int,
) {
    MINI(
        "qwen3.5-0.8b",
        "Нейрон Мини (Qwen 3.5 · 0.8B)",
        "Для любых телефонов, даже недорогих: весит меньше гигабайта, хорошо понимает русский.",
        "https://huggingface.co/litert-community/Qwen3.5-0.8B/resolve/8eb8a8428f9caaf0965260b97d919437bc9466e6/Qwen3.5-0.8B_int8.litertlm",
        "qwen3.5-0.8b-int8.litertlm",
        964_657_152L, 3, gpu = false, freshEngine = true, contextTokens = 4096,
    ),
    GEMMA_E2B(
        "gemma4-e2b",
        "Нейрон (Gemma 4 · E2B)",
        "Новая модель Google для телефонов: заметно умнее в математике и объяснениях, отвечает быстро благодаря видеокарте.",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/2d74ac41a33d7da82f39f7d5720a04c68514a103/gemma-4-E2B-it.litertlm",
        "gemma-4-e2b-it.litertlm",
        2_588_147_712L, 6, gpu = true, freshEngine = false, contextTokens = 8192,
    ),
    GEMMA_E4B(
        "gemma4-e4b",
        "Нейрон Про (Gemma 4 · E4B)",
        "Самая умная встроенная модель: сложные задачи, подробные решения. Для мощных телефонов.",
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/f254b5ba6e36f7d89cc7892e268e9877bc20f0a0/gemma-4-E4B-it.litertlm",
        "gemma-4-e4b-it.litertlm",
        3_659_530_240L, 8, gpu = true, freshEngine = false, contextTokens = 8192,
    );

    val sizeLabel: String get() = "%.1f ГБ".format(sizeBytes / 1_000_000_000.0)

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }

        /** Лучшая модель, которая потянет телефон с таким объёмом ОЗУ (система показывает чуть меньше номинала). */
        fun recommended(ramGb: Double): LocalModel = when {
            ramGb + 0.8 >= GEMMA_E4B.minRamGb + 3 -> GEMMA_E4B // 12 ГБ и больше
            ramGb + 0.8 >= GEMMA_E2B.minRamGb -> GEMMA_E2B
            else -> MINI
        }
    }
}
