package com.neuron.academy.ai

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Потоковый вывод: если положить этот элемент в контекст корутины, встроенный ИИ будет
 * присылать текст ответа по мере набора (накопленный целиком), а не только в конце.
 *
 * withContext(AiStream { partial -> ... }) { tutor.explain(...) }
 */
class AiStream(val onPartial: (String) -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AiStream>
}
