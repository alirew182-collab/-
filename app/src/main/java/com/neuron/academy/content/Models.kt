package com.neuron.academy.content

enum class Subject(
    val key: String,
    val title: String,
    val worldName: String,
    val emoji: String,
    val color: Long,
    val colorDark: Long,
) {
    ALGEBRA("algebra", "Алгебра", "Город чисел", "🏙️", 0xFF5C6BC0, 0xFF3949AB),
    PHYSICS("physics", "Физика", "Лаборатория сил", "⚡", 0xFFFF7043, 0xFFE64A19),
    CHEMISTRY("chemistry", "Химия", "Алхимическая башня", "⚗️", 0xFF26A69A, 0xFF00897B);

    companion object {
        fun byKey(key: String) = entries.first { it.key == key || it.name == key }
    }
}

data class Question(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
)

data class Example(val problem: String, val solution: String)

data class Practice(
    val problem: String,
    val answer: String,
    val hint: String,
    val solution: String,
    val hard: Boolean = false,
)

data class Topic(
    val id: String,
    val subject: Subject,
    val grade: Int,
    val order: Int,
    val title: String,
    val emoji: String,
    val theory: String,
    val examples: List<Example>,
    val practice: List<Practice>,
    val test: List<Question>,
    val fact: String,
    val realLife: String,
    val prerequisites: List<String>,
    val cardId: String,
)

/** Босс итогового экзамена класса по предмету. */
data class Boss(
    val subject: Subject,
    val grade: Int,
    val name: String,
    val emoji: String,
    val taunt: String,
    val defeatLine: String,
    val rewardCardId: String,
    /** 0 — финальный босс класса, 1..n — страж раздела, 99 — рейд недели. */
    val stage: Int = 0,
) {
    val isGuardian get() = stage in 1..98
    val isRaid get() = stage == 99
    /** Сколько вопросов в битве. */
    val questions get() = when { isRaid -> 20; isGuardian -> 8; else -> 15 }
    /** Доля верных ответов для победы. */
    val passShare get() = when { isRaid -> 0.8; isGuardian -> 0.75; else -> 0.8 }
    /** Таймер на вопрос, сек (0 — без таймера). */
    val timerSec get() = when { isRaid -> 25; isGuardian -> 0; else -> 35 }
    /** В ярости (HP < 50%) ошибка стоит двух сердечек. */
    val enrages get() = !isGuardian
}

/** Маленький DSL для компактного описания контента. */
class TopicBuilder(val id: String, val subject: Subject, val grade: Int, val order: Int, val title: String, val emoji: String) {
    var theory: String = ""
    var fact: String = ""
    var realLife: String = ""
    var card: String = ""
    var prereq: List<String> = emptyList()
    private val examples = mutableListOf<Example>()
    private val practice = mutableListOf<Practice>()
    private val test = mutableListOf<Question>()

    fun example(problem: String, solution: String) { examples += Example(problem.trimIndent(), solution.trimIndent()) }
    fun task(problem: String, answer: String, hint: String, solution: String, hard: Boolean = false) {
        practice += Practice(problem, answer, hint, solution, hard)
    }
    fun q(text: String, vararg options: String, correct: Int, why: String) {
        test += Question(text, options.toList(), correct, why)
    }

    fun build() = Topic(id, subject, grade, order, title, emoji, theory.trimIndent(), examples, practice, test, fact, realLife, prereq, card)
}

fun topic(id: String, subject: Subject, grade: Int, order: Int, title: String, emoji: String, block: TopicBuilder.() -> Unit): Topic =
    TopicBuilder(id, subject, grade, order, title, emoji).apply(block).build()
