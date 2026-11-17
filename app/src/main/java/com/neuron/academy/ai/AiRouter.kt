package com.neuron.academy.ai

import com.neuron.academy.data.AiSource
import com.neuron.academy.ai.local.OnDeviceLlm
import com.neuron.academy.ai.providers.AiProviderFactory
import com.neuron.academy.data.SettingsRepository
import com.neuron.academy.data.db.KeyStatEntity
import com.neuron.academy.data.db.KeyStatsDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Маршрутизатор запросов к ИИ.
 * 1. Выбирает порядок ключей согласно режиму (приоритет / по кругу / вручную).
 * 2. Отправляет запрос, а при любой ошибке (429, 401, 5xx, таймаут, сеть) сразу пробует следующий ключ.
 * 3. Ведёт статистику по каждому ключу.
 */
class AiRouter(
    private val keyStore: SecureKeyStore,
    private val statsDao: KeyStatsDao,
    private val settings: SettingsRepository,
    private val factory: AiProviderFactory,
    private val local: OnDeviceLlm,
) {
    private val roundRobin = AtomicInteger(0)
    private val statsMutex = Mutex()

    fun hasKeys(): Boolean = keyStore.keys.value.any { it.enabled }

    /** Есть ли вообще чем отвечать: встроенная модель или облачный ключ. */
    fun isAvailable(): Boolean = local.isActiveReady || hasKeys()

    val usesBuiltin: Boolean
        get() = local.isActiveReady && (settings.aiSource.value == AiSource.BUILTIN || !hasKeys())

    private suspend fun completeLocal(request: AiRequest): AiResult {
        val started = System.currentTimeMillis()
        val text = local.complete(request)
        return AiResult(text, "Встроенный ИИ", local.active.value.title, System.currentTimeMillis() - started)
    }

    /** Порядок ключей для конкретной функции. */
    fun orderedKeys(feature: AiFeature): List<ApiKeyConfig> {
        val active = keyStore.keys.value.filter { it.enabled }.sortedByDescending { it.priority }
        if (active.isEmpty()) return emptyList()
        return when (settings.keyMode.value) {
            KeySelectionMode.PRIORITY -> active
            KeySelectionMode.ROUND_ROBIN -> {
                val start = Math.floorMod(roundRobin.getAndIncrement(), active.size)
                active.drop(start) + active.take(start)
            }
            KeySelectionMode.MANUAL -> {
                val chosenId = settings.featureKeyMap.value[feature]
                val chosen = active.firstOrNull { it.id == chosenId }
                if (chosen != null) listOf(chosen) + active.filter { it.id != chosen.id } else active
            }
        }
    }

    suspend fun complete(feature: AiFeature, request: AiRequest): AiResult {
        // 1) встроенный ИИ — по умолчанию
        if (usesBuiltin) return completeLocal(request)
        val keys = orderedKeys(feature)
        if (keys.isEmpty()) throw NoKeysException()
        val errors = mutableListOf<Pair<String, AiException>>()
        for (key in keys) {
            val started = System.currentTimeMillis()
            try {
                val text = factory.get(key.provider).complete(key, request)
                val latency = System.currentTimeMillis() - started
                record(key.id, success = true, error = null, latency = latency)
                return AiResult(text.trim(), key.name, key.model, latency)
            } catch (e: AiException) {
                record(key.id, success = false, error = "${e.kind.title}: ${e.message}", latency = System.currentTimeMillis() - started)
                errors += key.name to e
                // fallback: переходим к следующему ключу
            } catch (e: Exception) {
                val ae = AiException(AiErrorKind.NETWORK, null, e.message ?: e.javaClass.simpleName)
                record(key.id, success = false, error = ae.message, latency = System.currentTimeMillis() - started)
                errors += key.name to ae
            }
        }
        // 2) все облачные ключи упали — пробуем встроенную модель, если она скачана
        if (local.isActiveReady) return completeLocal(request)
        throw AllKeysFailedException(errors)
    }

    /** «Проверить ключ»: короткий тестовый запрос именно к этому ключу. */
    suspend fun testKey(key: ApiKeyConfig): Result<String> {
        val started = System.currentTimeMillis()
        return try {
            val text = factory.get(key.provider).complete(
                key,
                AiRequest(
                    system = "Ты тестовый помощник. Отвечай очень коротко.",
                    messages = listOf(AiMessage(AiMessage.Role.USER, "Ответь одним словом: «Работает»")),
                    maxTokens = 50,
                ),
            )
            val latency = System.currentTimeMillis() - started
            record(key.id, true, null, latency)
            Result.success("✅ Ключ работает (${latency} мс): ${text.trim().take(60)}")
        } catch (e: AiException) {
            record(key.id, false, "${e.kind.title}: ${e.message}", System.currentTimeMillis() - started)
            Result.failure(Exception("❌ ${e.kind.title}${e.httpCode?.let { " [$it]" } ?: ""}: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("❌ ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private suspend fun record(keyId: String, success: Boolean, error: String?, latency: Long) {
        statsMutex.withLock {
            val old = statsDao.get(keyId) ?: KeyStatEntity(keyId)
            statsDao.upsert(
                old.copy(
                    requests = old.requests + 1,
                    errors = old.errors + if (success) 0 else 1,
                    lastUsed = System.currentTimeMillis(),
                    lastError = if (success) old.lastError else error,
                    lastSuccess = success,
                    lastLatencyMs = latency,
                ),
            )
        }
    }
}
