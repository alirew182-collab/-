package com.neuron.academy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neuron.academy.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не предоставлен") }

/** Создаёт ViewModel, передавая ей контейнер зависимостей (простая замена Hilt). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key) { create(c) }
}

fun Throwable.userMessage(): String = message ?: javaClass.simpleName
