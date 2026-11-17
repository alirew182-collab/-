package com.neuron.academy.content

/** Подробная теория и разбор несколькими способами для темы. */
class DeepEntry(val theory: String, val multi: List<MultiSolution>)

class DeepBuilder {
    var theory: String = ""
    private val multis = mutableListOf<MultiSolution>()

    /** Задача, решённая несколькими способами: пары «название способа» to «решение». */
    fun multi(problem: String, answer: String, compare: String, vararg methods: Pair<String, String>) {
        multis += MultiSolution(problem, answer, methods.map { SolutionMethod(it.first, it.second.trimIndent()) }, compare)
    }

    fun build() = DeepEntry(theory, multis)
}

fun deep(block: DeepBuilder.() -> Unit): DeepEntry = DeepBuilder().apply(block).build()
