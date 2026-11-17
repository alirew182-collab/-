package com.neuron.academy.data

import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.content.Topic
import com.neuron.academy.data.db.ExamEntity
import com.neuron.academy.data.db.TopicProgressEntity

enum class TopicStatus { LOCKED, AVAILABLE, COMPLETED }

data class TopicState(
    val topic: Topic,
    val status: TopicStatus,
    val bestScore: Float,
    val perfect: Boolean,
    val lockHint: String?,
)

data class SubjectGradeState(
    val subject: Subject,
    val grade: Int,
    val topics: List<TopicState>,
    val examPassed: Boolean,
    val examAvailable: Boolean,
    val examLockHint: String?,
) {
    val completedCount get() = topics.count { it.status == TopicStatus.COMPLETED }
    val total get() = topics.size
    val fraction get() = if (total == 0) 0f else completedCount.toFloat() / total
    val done get() = examPassed
}

data class GradeState(
    val grade: Int,
    val unlocked: Boolean,
    val completed: Boolean,
    val subjects: List<SubjectGradeState>,
    val lockHint: String?,
) {
    val fraction get() = subjects.map { it.fraction }.average().toFloat()
    fun subject(s: Subject) = subjects.first { it.subject == s }
}

data class LearningState(
    val grades: List<GradeState>,
    val teacherMode: Boolean,
    val threshold: Float,
) {
    private val topicMap = grades.flatMap { g -> g.subjects.flatMap { it.topics } }.associateBy { it.topic.id }

    fun topic(id: String) = topicMap[id]
    fun grade(g: Int) = grades.first { it.grade == g }
    fun subjectGrade(s: Subject, g: Int) = grade(g).subject(s)

    val completedTopics: List<Topic> get() = topicMap.values.filter { it.status == TopicStatus.COMPLETED }.map { it.topic }
    val completedTopicIds: Set<String> get() = completedTopics.map { it.id }.toSet()
    val perfectTopicIds: Set<String> get() = topicMap.values.filter { it.perfect }.map { it.topic.id }.toSet()
    val completedGrades: Set<Int> get() = grades.filter { it.completed }.map { it.grade }.toSet()

    /** Текущий класс ученика — самый старший открытый по-честному (без режима учителя). */
    val currentGrade: Int get() = grades.lastOrNull { it.unlocked && (it.grade == 9 || grades.first { g -> g.grade == it.grade - 1 }.completed) }?.grade ?: 9

    /** Общий прогресс 9 → 11 (0..1). */
    val overallFraction: Float get() = grades.map { it.fraction }.average().toFloat()

    fun subjectFraction(s: Subject): Float = grades.map { it.subject(s).fraction }.average().toFloat()

    companion object {
        val EMPTY = UnlockEngine.compute(emptyMap(), emptyMap(), emptySet(), 0.8f, false)
    }
}

/**
 * Чистая логика последовательного обучения (легко тестировать):
 *  • 9 класс открыт всегда; класс N+1 открывается, когда класс N завершён;
 *  • класс завершён, если по каждому предмету пройдено ≥ threshold тем и сдан итоговый экзамен;
 *  • внутри класса темы идут по порядку: следующая открывается после теста ≥ 70% по предыдущей;
 *  • экзамен доступен, когда пройдено ≥ threshold тем предмета;
 *  • режим учителя открывает всё.
 */
object UnlockEngine {
    const val TOPIC_PASS_SCORE = 0.7f

    fun compute(
        progress: Map<String, TopicProgressEntity>,
        exams: Map<String, ExamEntity>,
        storedUnlocked: Set<Int>,
        threshold: Float,
        teacherMode: Boolean,
    ): LearningState {
        val grades = mutableListOf<GradeState>()
        var previousCompleted = true
        for (g in Curriculum.grades) {
            val naturallyUnlocked = g == Curriculum.grades.first() || previousCompleted || g in storedUnlocked
            val unlocked = naturallyUnlocked || teacherMode
            val subjects = Subject.entries.map { subject ->
                val topics = Curriculum.topicsFor(subject, g)
                var prevDone = true
                var prevTitle = ""
                val states = topics.map { t ->
                    val p = progress[t.id]
                    val completed = p?.completed == true
                    val available = unlocked && (prevDone || teacherMode)
                    val status = when {
                        completed -> TopicStatus.COMPLETED
                        available -> TopicStatus.AVAILABLE
                        else -> TopicStatus.LOCKED
                    }
                    val hint = when {
                        status != TopicStatus.LOCKED -> null
                        !unlocked -> "Сначала заверши ${g - 1} класс"
                        else -> "Сдай тест по теме «$prevTitle» на ${(TOPIC_PASS_SCORE * 100).toInt()}% и выше"
                    }
                    prevDone = completed
                    prevTitle = t.title
                    TopicState(t, status, p?.bestScore ?: 0f, p?.perfect == true, hint)
                }
                val done = states.count { it.status == TopicStatus.COMPLETED }
                val enough = states.isNotEmpty() && done.toFloat() / states.size >= threshold - 1e-4f
                val exam = exams["${subject.key}_$g"]
                val examAvailable = unlocked && (enough || teacherMode)
                val need = kotlin.math.ceil(threshold * states.size - 1e-4).toInt()
                SubjectGradeState(
                    subject, g, states,
                    examPassed = exam?.passed == true,
                    examAvailable = examAvailable,
                    examLockHint = if (examAvailable) null else if (!unlocked) "Класс ещё закрыт" else "Пройди минимум $need из ${states.size} тем (${(threshold * 100).toInt()}%)",
                )
            }
            val completed = subjects.all { it.fraction >= threshold - 1e-4f && it.examPassed }
            grades += GradeState(
                grade = g,
                unlocked = unlocked,
                completed = completed,
                subjects = subjects,
                lockHint = if (unlocked) null else "Заверши ${g - 1} класс: пройди ${(threshold * 100).toInt()}% тем по каждому предмету и победи всех трёх боссов",
            )
            previousCompleted = completed
        }
        return LearningState(grades, teacherMode, threshold)
    }
}
