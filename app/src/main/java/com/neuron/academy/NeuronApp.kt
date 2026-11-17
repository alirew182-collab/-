package com.neuron.academy

import android.app.Application
import android.content.Context
import com.neuron.academy.ai.AiRouter
import com.neuron.academy.ai.SecureKeyStore
import com.neuron.academy.ai.TutorAi
import com.neuron.academy.ai.providers.AiProviderFactory
import com.neuron.academy.data.GameRepository
import com.neuron.academy.data.ProgressRepository
import com.neuron.academy.data.SettingsRepository
import com.neuron.academy.data.db.AppDatabase
import com.neuron.academy.net.TrustedCerts
import com.neuron.academy.notifications.ReminderScheduler
import com.neuron.academy.ui.Feedback
import com.neuron.academy.widget.WidgetUpdater
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/** Простое ручное внедрение зависимостей. */
class AppContainer(context: Context) {
    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val keyStore = SecureKeyStore(context)
    val http = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 90_000
        }
    }
    /** Клиент для GigaChat: дополнительно доверяет сертификатам Минцифры (если они есть в assets/certs). */
    val gigaHttp = TrustedCerts.create(context)?.let { (factory, tm) ->
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 90_000
            }
            engine { config { sslSocketFactory(factory, tm) } }
        }
    } ?: http
    val router = AiRouter(keyStore, db.keyStatsDao(), settings, AiProviderFactory(http, gigaHttp))
    val tutor = TutorAi(router)
    val progress = ProgressRepository(db.progressDao(), settings)
    val game = GameRepository(db.playerDao(), progress)
    val chatDao = db.chatDao()
    val keyStats = db.keyStatsDao()
    val feedback = Feedback(context, settings)
}

class NeuronApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        // Обновляем виджеты, когда меняются данные игрока или прогресс
        appScope.launch {
            combine(container.game.player, container.progress.learningState, container.db.playerDao().observeDailyProblem(container.game.today())) { p, l, d -> Triple(p, l.completedTopicIds.size, d?.solved) }
                .distinctUntilChanged()
                .debounce(1500)
                .collect { WidgetUpdater.updateAll(this@NeuronApp) }
        }
        if (container.settings.notifications.value) {
            ReminderScheduler.schedule(this, container.settings.notifyHour.value, container.settings.notifyMinute.value)
        }
    }
}
