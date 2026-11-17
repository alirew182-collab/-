package com.neuron.academy.content

import kotlin.math.abs
import kotlin.math.max

/** Сравнение ответа ученика с эталоном: числа — с допуском 1%, текст — без учёта регистра. */
object AnswerChecker {
    private val numberRegex = Regex("""-?\d+(?:[.,]\d+)?""")

    fun numbers(s: String): List<Double> =
        numberRegex.findAll(s.replace('−', '-').replace(" ", "")).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

    fun matches(user: String, expected: String): Boolean {
        val u = user.trim()
        if (u.isEmpty()) return false
        val en = numbers(expected)
        if (en.isNotEmpty()) {
            val un = numbers(u)
            if (un.size != en.size) return false
            val a = un.sorted()
            val b = en.sorted()
            return a.indices.all { i -> close(a[i], b[i]) }
        }
        return normalize(u) == normalize(expected) || normalize(expected).contains(normalize(u)) && u.length >= 3
    }

    private fun close(a: Double, b: Double): Boolean {
        val diff = abs(a - b)
        return diff < 1e-6 || diff <= 0.01 * max(abs(a), abs(b)) || diff <= 0.011 && abs(b) < 1.5
    }

    private fun normalize(s: String) = s.lowercase().replace("ё", "е").filter { it.isLetterOrDigit() }
}
