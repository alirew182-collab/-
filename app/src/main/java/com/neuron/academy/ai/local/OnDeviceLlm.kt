package com.neuron.academy.ai.local

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.neuron.academy.ai.AiErrorKind
import com.neuron.academy.ai.AiException
import com.neuron.academy.ai.AiMessage
import com.neuron.academy.ai.AiRequest
import com.neuron.academy.ai.AiStream
import kotlinx.coroutines.CancellationException
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

/** Состояние движка модели в памяти. */
sealed interface EngineState {
    data object Idle : EngineState
    data object Loading : EngineState
    data class Loaded(val onGpu: Boolean) : EngineState
}

/**
 * Встроенный ИИ: языковая модель, которая работает прямо на телефоне — без ключей,
 * без интернета (после однократного скачивания) и без отправки данных на сервер.
 *
 * Движок — LiteRT-LM от Google: ускорение на видеокарте (с запасным вариантом на процессоре),
 * потоковый вывод ответа и предзагрузка модели, чтобы первый ответ не ждал.
 */
class OnDeviceLlm(private val context: Context) {
    private val prefs = context.getSharedPreferences("neuron_local_llm", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val engineMutex = Mutex()

    private var engine: Engine? = null
    private var engineModel: LocalModel? = null
    private val pollers = mutableMapOf<LocalModel, Job>()

    private val _status = MutableStateFlow(LocalModel.entries.associateWith { initialStatus(it) })
    val status: StateFlow<Map<LocalModel, ModelStatus>> = _status.asStateFlow()

    val recommended: LocalModel = LocalModel.recommended(deviceRamGb())

    private val _active = MutableStateFlow(LocalModel.byId(prefs.getString("active", null)) ?: defaultModel())
    val active: StateFlow<LocalModel> = _active.asStateFlow()

    private val _useGpu = MutableStateFlow(prefs.getBoolean("use_gpu", true))
    val useGpu: StateFlow<Boolean> = _useGpu.asStateFlow()

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    init {
        runCatching { Engine.setNativeMinLogSeverity(LogSeverity.ERROR) }
        // если приложение перезапустили во время скачивания — продолжаем следить за прогрессом
        LocalModel.entries.forEach { m -> if (prefs.contains(downloadKey(m)) && !isReady(m)) poll(m) }
        scope.launch { removeObsoleteModels() }
    }

    /** Уже скачанная модель приоритетнее рекомендации. */
    private fun defaultModel(): LocalModel =
        LocalModel.entries.firstOrNull { isReady(it) } ?: recommended

    private fun modelsDir() = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")

    fun file(m: LocalModel) = File(modelsDir(), m.fileName)

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
        scope.launch { engineMutex.withLock { releaseEngine() } }
    }

    fun setUseGpu(on: Boolean) {
        prefs.edit().putBoolean("use_gpu", on).remove("gpu_failed").apply()
        _useGpu.value = on
        scope.launch { engineMutex.withLock { releaseEngine() } }
    }

    /** Файлы старых моделей (прошлые версии приложения) — удаляем, чтобы не занимали место. */
    private fun removeObsoleteModels() {
        val keep = LocalModel.entries.map { it.fileName }.toSet()
        modelsDir().listFiles()?.forEach { f ->
            if (f.isFile && f.name !in keep && (f.name.endsWith(".task") || f.name.endsWith(".litertlm"))) f.delete()
        }
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
            engineMutex.withLock { if (engineModel == m) releaseEngine() }
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
                        if (isReady(m) && m == _active.value) warmUp()
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

    /**
     * Заранее загружает модель в память (в фоне), чтобы первый ответ пришёл сразу.
     * Вызывается при открытии чата или урока.
     */
    fun warmUp() {
        val m = _active.value
        if (!isReady(m) || m.freshEngine) return
        scope.launch {
            engineMutex.withLock { runCatching { obtainEngine(m) } }
        }
    }

    /** Освобождает память, занятую моделью. */
    fun release() {
        scope.launch { engineMutex.withLock { releaseEngine() } }
    }

    private fun releaseEngine() {
        runCatching { engine?.close() }
        engine = null; engineModel = null
        _engineState.value = EngineState.Idle
    }

    /** Должен вызываться под [engineMutex]. Сначала видеокарта, при ошибке — процессор. */
    private fun obtainEngine(m: LocalModel): Engine {
        engine?.takeIf { engineModel == m }?.let { return it }
        releaseEngine()
        _engineState.value = EngineState.Loading
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
        // если прошлый запуск на видеокарте «уронил» приложение — больше не пробуем GPU для этой модели
        val crashedOnGpu = prefs.getString("gpu_init_in_progress", null) == m.id
        if (crashedOnGpu) prefs.edit().putBoolean("gpu_failed_${m.id}", true).remove("gpu_init_in_progress").apply()
        val tryGpu = m.gpu && _useGpu.value && !prefs.getBoolean("gpu_failed_${m.id}", false)

        fun create(gpu: Boolean): Engine {
            val cfg = EngineConfig(
                modelPath = file(m).absolutePath,
                backend = if (gpu) Backend.GPU() else Backend.CPU(),
                maxNumTokens = m.contextTokens,
                cacheDir = cacheDir,
            )
            val e = Engine(cfg)
            try {
                e.initialize()
            } catch (t: Throwable) {
                runCatching { e.close() }
                throw t
            }
            return e
        }

        val created = try {
            if (tryGpu) {
                prefs.edit().putString("gpu_init_in_progress", m.id).commit()
                try {
                    create(gpu = true).also { _engineState.value = EngineState.Loaded(onGpu = true) }
                } catch (e: Throwable) {
                    prefs.edit().putBoolean("gpu_failed_${m.id}", true).apply()
                    create(gpu = false).also { _engineState.value = EngineState.Loaded(onGpu = false) }
                } finally {
                    prefs.edit().remove("gpu_init_in_progress").apply()
                }
            } else {
                create(gpu = false).also { _engineState.value = EngineState.Loaded(onGpu = false) }
            }
        } catch (e: Throwable) {
            _engineState.value = EngineState.Idle
            throw AiException(AiErrorKind.SERVER, null, "Не удалось запустить модель на этом телефоне: ${e.message ?: e.javaClass.simpleName}. Попробуй «Нейрон Мини».")
        }
        engine = created; engineModel = m
        return created
    }

    /** Генерация ответа на телефоне. Если в контексте есть [AiStream] — текст приходит по мере набора. */
    suspend fun complete(request: AiRequest): String = withContext(Dispatchers.Default) {
        val m = _active.value
        if (!isReady(m)) throw AiException(AiErrorKind.BAD_REQUEST, null, "Встроенный ИИ ещё не скачан")
        val stream = coroutineContext[AiStream]
        engineMutex.withLock {
            val llm = obtainEngine(m)
            try {
                val history = request.messages.takeLast(6)
                val last = history.lastOrNull { it.role == AiMessage.Role.USER }?.content
                    ?: throw AiException(AiErrorKind.BAD_REQUEST, null, "Пустой запрос")
                val initial = history.dropLast(1).map {
                    if (it.role == AiMessage.Role.USER) Message.user(it.content.take(1500)) else Message.model(it.content.take(1500))
                }
                val cfg = ConversationConfig(
                    systemInstruction = Contents.of(request.system.take(if (m == LocalModel.MINI) 1800 else 3500)),
                    initialMessages = initial,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = request.temperature.coerceIn(0.1, 1.0)),
                    maxOutputToken = request.maxTokens.coerceIn(256, 2048),
                )
                val conversation = try {
                    llm.createConversation(cfg)
                } catch (e: Throwable) {
                    throw AiException(AiErrorKind.SERVER, null, "Ошибка встроенной модели: ${e.message ?: e.javaClass.simpleName}")
                }
                conversation.use { conv ->
                    val sb = StringBuilder()
                    var lastEmit = 0L
                    try {
                        conv.sendMessageAsync(last).collect { msg ->
                            sb.append(msg.toString())
                            val now = System.currentTimeMillis()
                            if (stream != null && now - lastEmit > 120) {
                                lastEmit = now
                                stream.onPartial(clean(sb.toString()))
                            }
                        }
                    } catch (e: CancellationException) {
                        runCatching { conv.cancelProcess() }
                        throw e
                    } catch (e: AiException) {
                        throw e
                    } catch (e: Throwable) {
                        if (sb.isBlank()) throw AiException(AiErrorKind.SERVER, null, "Ошибка встроенной модели: ${e.message ?: e.javaClass.simpleName}")
                    }
                    val text = clean(sb.toString())
                    stream?.onPartial(text)
                    text.ifBlank { throw AiException(AiErrorKind.PARSE, null, "Модель вернула пустой ответ") }
                }
            } finally {
                // у моделей с рекуррентным состоянием каждый запрос — на чистом движке
                if (m.freshEngine) releaseEngine()
            }
        }
    }

    /** Понимает ли активная модель картинки и готова ли она. */
    val canSeeImages: Boolean get() = _active.value.vision && isActiveReady

    /**
     * Запрос с картинками (например, скриншотами). Для него нужен отдельный движок с модулем
     * зрения, поэтому обычный движок на время выгружается из памяти.
     */
    suspend fun completeWithImages(system: String, prompt: String, images: List<File>): String = withContext(Dispatchers.Default) {
        val m = _active.value
        if (!isReady(m)) throw AiException(AiErrorKind.BAD_REQUEST, null, "Встроенный ИИ ещё не скачан")
        if (!m.vision) throw AiException(AiErrorKind.BAD_REQUEST, null, "«${m.title}» не умеет читать картинки. Выбери модель Gemma 4 в «Настройках ИИ»")
        engineMutex.withLock {
            releaseEngine()
            _engineState.value = EngineState.Loading
            val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
            fun create(gpu: Boolean): Engine {
                val b = if (gpu) Backend.GPU() else Backend.CPU()
                val e = Engine(
                    EngineConfig(
                        modelPath = file(m).absolutePath, backend = b, visionBackend = if (gpu) Backend.GPU() else Backend.CPU(),
                        maxNumTokens = m.contextTokens, maxNumImages = images.size.coerceAtLeast(1), cacheDir = cacheDir,
                    ),
                )
                try { e.initialize() } catch (t: Throwable) { runCatching { e.close() }; throw t }
                return e
            }
            val useGpu = m.gpu && _useGpu.value && !prefs.getBoolean("gpu_failed_${m.id}", false)
            val eng = try {
                if (useGpu) runCatching { create(true) }.getOrElse { create(false) } else create(false)
            } catch (e: Throwable) {
                _engineState.value = EngineState.Idle
                throw AiException(AiErrorKind.SERVER, null, "Не удалось запустить распознавание картинок: ${e.message ?: e.javaClass.simpleName}")
            }
            try {
                val cfg = ConversationConfig(
                    systemInstruction = Contents.of(system),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                    maxOutputToken = 1024,
                )
                eng.createConversation(cfg).use { conv ->
                    val parts = listOf<Content>(Content.Text(prompt)) + images.map { Content.ImageFile(it.absolutePath) }
                    val sb = StringBuilder()
                    try {
                        conv.sendMessageAsync(Contents.of(parts)).collect { sb.append(it.toString()) }
                    } catch (e: CancellationException) {
                        runCatching { conv.cancelProcess() }
                        throw e
                    } catch (e: Throwable) {
                        if (sb.isBlank()) throw AiException(AiErrorKind.SERVER, null, "Ошибка распознавания: ${e.message ?: e.javaClass.simpleName}")
                    }
                    clean(sb.toString()).ifBlank { throw AiException(AiErrorKind.PARSE, null, "Модель вернула пустой ответ") }
                }
            } finally {
                runCatching { eng.close() }
                _engineState.value = EngineState.Idle
            }
        }
    }

    private fun clean(s: String) = s
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace("<think>", "").replace("</think>", "")
        .replace("<end_of_turn>", "").replace("<|im_end|>", "").replace("<|endoftext|>", "")
        .trim()
}
