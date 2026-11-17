package com.neuron.academy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.NeuronRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NeuronApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                NeuronRoot(container)
            }
        }
    }
}
