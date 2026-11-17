package com.neuron.academy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.NeuronRoot

class MainActivity : ComponentActivity() {
    /** Маршрут, переданный из виджета (например, "daily" или "topic/a9_quad"). */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(ROUTE_EXTRA)
        val container = (application as NeuronApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                NeuronRoot(container, pendingRoute.value) { pendingRoute.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ROUTE_EXTRA)?.let { pendingRoute.value = it }
    }

    companion object {
        const val ROUTE_EXTRA = "route"
    }
}
