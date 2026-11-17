package com.neuron.academy.ai.local

import com.neuron.academy.ai.AiMessage
import com.neuron.academy.ai.AiRequest

/** Шаблон диалога, который понимает конкретная модель. */
enum class ChatTemplate {
    CHATML, // Qwen
    PHI;    // Phi-4

    fun format(request: AiRequest, maxHistory: Int = 6): String {
        val history = request.messages.takeLast(maxHistory)
        return when (this) {
            CHATML -> buildString {
                append("<|im_start|>system\n").append(request.system).append("<|im_end|>\n")
                history.forEach { m ->
                    append("<|im_start|>").append(if (m.role == AiMessage.Role.USER) "user" else "assistant").append('\n')
                    append(m.content).append("<|im_end|>\n")
                }
                append("<|im_start|>assistant\n")
            }
            PHI -> buildString {
                append("<|system|>").append(request.system).append("<|end|>")
                history.forEach { m ->
                    append(if (m.role == AiMessage.Role.USER) "<|user|>" else "<|assistant|>").append(m.content).append("<|end|>")
                }
                append("<|assistant|>")
            }
        }
    }
}

/**
 * Модели, которые работают прямо на телефоне (MediaPipe LLM Inference, формат .task).
 * Источник — открытые репозитории litert-community на Hugging Face (лицензии Apache 2.0 / MIT).
 */
enum class LocalModel(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val template: ChatTemplate,
) {
    QWEN_1_5B(
        "qwen2.5-1.5b",
        "Нейрон Лайт (Qwen 2.5 · 1.5B)",
        "Быстрая модель, хорошо понимает русский. Подходит большинству телефонов.",
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
        "qwen2.5-1.5b-q8-ekv4096.task",
        1_598_556_720L, 4, ChatTemplate.CHATML,
    ),
    PHI4_MINI(
        "phi-4-mini",
        "Нейрон Про (Phi-4 mini · 3.8B)",
        "Умнее в математике и логике, но медленнее. Нужен мощный телефон (8 ГБ ОЗУ).",
        "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task",
        "phi-4-mini-q8-ekv4096.task",
        3_910_050_199L, 8, ChatTemplate.PHI;

    val sizeLabel: String get() = "%.1f ГБ".format(sizeBytes / 1_000_000_000.0)

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}
