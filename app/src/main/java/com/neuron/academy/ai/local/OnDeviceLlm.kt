package com.neuron.academy.ai.local

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Downloading(val progress: Float, val downloadedBytes: Long) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Встроенный ИИ: языковая модель, которая работает прямо на телефоне — без ключей,
 * без интернета (после однократного скачивания) и без отправки данных на сервер.
 */
class OnDeviceLlm(private val context: Context) {
    private val prefs = context.getSharedPreferences("neuron_local_llm", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val inferenceMutex = Mutex()

    private var engine: LlmInference? = null
    private var engineModel: LocalModel? = null
    private val pollers = mutableMapOf<LocalModel, Job>()

    private val _status = MutableStateFlow(LocalModel.entries.associateWith { initialStatus(it) })
    val status: StateFlow<Map<LocalModel, ModelStatus>> = _status.asStateFlow()

    private val _active = MutableStateFlow(LocalModel.byId(prefs.getString("active", null)) ?: LocalModel.QWEN_1_5B)
    val active: StateFlow<LocalModel> = _active.asStateFlow()

    init {
        // если приложение перезапустили во время скачивания — продолжаем следить за прогрессом
        LocalModel.entries.forEach { m -> if (prefs.contains(downloadKey(m)) && !isReady(m)) poll(m) }
    }

    fun file(m: LocalModel) = File(context.getExternalFilesDir("models") ?: context.filesDir, m.fileName)

    fun isReady(m: LocalModel = _active.value): Boolean = file(m).let { it.exists() && it.length() >= m.sizeBytes * 0.98 }

    val isActiveReady: Boolean get() = isReady(_active.value)

    /** Сколько ОЗУ у телефона, ГБ (для подсказки, какая модель подойдёт). */
    fun deviceRamGb(): Double {
        val mi = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return mi.totalMem / 1_073_741_824.0
    }

    fun setActive(m: LocalModel) {
        prefs.edit().putString("active", m.id).apply()
        _active.value = m
    }

    private fun initialStatus(m: LocalModel): ModelStatus = if (isReady(m)) ModelStatus.Ready else ModelStatus.NotDownloaded

    private fun downloadKey(m: LocalModel) = "download_${m.id}"

    private fun update(m: LocalModel, s: ModelStatus) {
        _status.value = _status.value + (m to s)
    }

    fun download(m: LocalModel, allowMobileData: Boolean) {
        if (isReady(m) || prefs.contains(downloadKey(m))) return
        file(m).parentFile?.mkdirs()
        file(m).delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("Нейрон: ${m.title}")
            .setDescription("Встроенный ИИ · ${m.sizeLabel}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", m.fileName)
            .setAllowedOverMetered(allowMobileData)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        prefs.edit().putLong(downloadKey(m), id).apply()
        update(m, ModelStatus.Downloading(0f, 0))
        poll(m)
    }

    fun cancel(m: LocalModel) {
        val id = prefs.getLong(downloadKey(m), -1)
        if (id >= 0) dm.remove(id)
        prefs.edit().remove(downloadKey(m)).apply()
        pollers.remove(m)?.cancel()
        file(m).delete()
        update(m, ModelStatus.NotDownloaded)
    }

    fun delete(m: LocalModel) {
        scope.launch {
            inferenceMutex.withLock {
                if (engineModel == m) { engine?.close(); engine = null; engineModel = null }
            }
            file(m).delete()
            update(m, ModelStatus.NotDownloaded)
        }
    }

    private fun poll(m: LocalModel) {
        pollers[m]?.cancel()
        pollers[m] = scope.launch {
            while (true) {
                val id = prefs.getLong(downloadKey(m), -1)
                if (id < 0) break
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                if (c == null || !c.moveToFirst()) {
                    c?.close()
                    prefs.edit().remove(downloadKey(m)).apply()
                    update(m, if (isReady(m)) ModelStatus.Ready else ModelStatus.Failed("Загрузка прервана"))
                    break
                }
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                c.close()
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs.edit().remove(downloadKey(m)).apply()
                        update(m, if (isReady(m)) ModelStatus.Ready else ModelStatus.Failed("Файл модели повреждён — скачай заново"))
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().remove(downloadKey(m)).apply()
                        file(m).delete()
                        update(m, ModelStatus.Failed("Не удалось скачать (код $reason). Проверь интернет и свободное место"))
                        break
                    }
                    else -> update(m, ModelStatus.Downloading((done.toFloat() / m.sizeBytes).coerceIn(0f, 1f), done))
                }
                delay(1000)
            }
        }
    }

    /** Генерация ответа на телефоне. Модель загружается в память при первом запросе. */
    suspend fun complete(request: AiRequest): String = withContext(Dispatchers.Default) {
        val m = _active.value
        if (!isReady(m)) throw AiException(AiErrorKind.BAD_REQUEST, null, "Встроенный ИИ ещё не скачан")
        inferenceMutex.withLock {
            val llm = try {
                engine?.takeIf { engineModel == m } ?: run {
                    engine?.close()
                    val opts = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(file(m).absolutePath)
                        .setMaxTokens(4096)
                        .build()
                    LlmInference.createFromOptions(context, opts).also { engine = it; engineModel = m }
                }
            } catch (e: Throwable) {
                engine = null; engineModel = null
                throw AiException(AiErrorKind.SERVER, null, "Не удалось запустить модель на этом телефоне: ${e.message ?: e.javaClass.simpleName}")
            }
            // Встроенной модели нужен более короткий контекст: окно 4096 токенов
            val compact = request.copy(system = request.system.take(1800))
            val prompt = m.template.format(compact)
            val raw = try {
                llm.generateResponse(prompt)
            } catch (e: Throwable) {
                throw AiException(AiErrorKind.SERVER, null, "Ошибка встроенной модели: ${e.message ?: e.javaClass.simpleName}")
            }
            clean(raw).ifBlank { throw AiException(AiErrorKind.PARSE, null, "Модель вернула пустой ответ") }
        }
    }

    private fun clean(s: String) = s
        .replace("<|im_end|>", "").replace("<|end|>", "").replace("<|endoftext|>", "")
        .substringBefore("<|im_start|>").substringBefore("<|user|>")
        .trim()
}
