package com.neuron.academy.content

/** Подробные объяснения «с нуля» и разборы несколькими способами для ключевых тем. */
object DeepContent {
    val entries: Map<String, DeepEntry> by lazy { DeepAlgebra.entries + DeepPhysics.entries + DeepChemistry.entries }
}
