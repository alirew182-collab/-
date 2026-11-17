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
import com.neuron.academy.notifications.ReminderScheduler
import com.neuron.academy.ui.Feedback
import io.ktor.client.HttpClient
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
    val router = AiRouter(keyStore, db.keyStatsDao(), settings, AiProviderFactory(http))
    val tutor = TutorAi(router)
    val progress = ProgressRepository(db.progressDao(), settings)
    val game = GameRepository(db.playerDao(), progress)
    val chatDao = db.chatDao()
    val keyStats = db.keyStatsDao()
    val feedback = Feedback(context, settings)
}

class NeuronApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        if (container.settings.notifications.value) {
            ReminderScheduler.schedule(this, container.settings.notifyHour.value, container.settings.notifyMinute.value)
        }
    }
}
