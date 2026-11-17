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
    /** Тема пройдена давно — пора повторить. */
    val needsReview: Boolean = false,
    val reviewedAt: Long? = null,
    val completedAt: Long? = null,
)

/** Страж раздела — мини-босс после блока из четырёх тем. */
data class GuardianState(
    val stage: Int,
    val available: Boolean,
    val passed: Boolean,
    val lockHint: String?,
)

data class SubjectGradeState(
    val subject: Subject,
    val grade: Int,
    val topics: List<TopicState>,
    val guardians: List<GuardianState>,
    val examPassed: Boolean,
    val examAvailable: Boolean,
    val examLockHint: String?,
) {
    val completedCount get() = topics.count { it.status == TopicStatus.COMPLETED }
    val total get() = topics.size
    val fraction get() = if (total == 0) 0f else completedCount.toFloat() / total
    val done get() = examPassed
    fun guardian(stage: Int) = guardians.first { it.stage == stage }
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

    val allTopics: Collection<TopicState> get() = topicMap.values
    val completedTopics: List<Topic> get() = topicMap.values.filter { it.status == TopicStatus.COMPLETED }.map { it.topic }
    val completedTopicIds: Set<String> get() = completedTopics.map { it.id }.toSet()
    val perfectTopicIds: Set<String> get() = topicMap.values.filter { it.perfect }.map { it.topic.id }.toSet()
    val completedGrades: Set<Int> get() = grades.filter { it.completed }.map { it.grade }.toSet()
    val reviewDue: List<TopicState> get() = topicMap.values.filter { it.needsReview }
    val guardiansDefeated: Int get() = grades.sumOf { g -> g.subjects.sumOf { s -> s.guardians.count { it.passed } } }

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
 * Чистая логика последовательного обучения:
 *  • 9 класс открыт всегда; класс N+1 открывается, когда класс N завершён;
 *  • темы идут блоками по 4: следующая тема открывается после контрольной ≥ 70% по предыдущей,
 *    а первая тема нового блока — только после победы над стражем раздела;
 *  • итоговый экзамен (финальный босс) открыт, когда пройдено ≥ threshold тем и побеждены все стражи;
 *  • класс завершён, если по каждому предмету пройдено ≥ threshold тем и побеждён финальный босс;
 *  • режим учителя открывает всё.
 */
object UnlockEngine {
    const val TOPIC_PASS_SCORE = 0.7f
    private const val REVIEW_AFTER_MS = 7L * 24 * 3600 * 1000

    fun examId(subject: Subject, grade: Int, stage: Int = 0) =
        if (stage == 0) "${subject.key}_$grade" else "${subject.key}_${grade}_g$stage"

    fun compute(
        progress: Map<String, TopicProgressEntity>,
        exams: Map<String, ExamEntity>,
        storedUnlocked: Set<Int>,
        threshold: Float,
        teacherMode: Boolean,
        now: Long = System.currentTimeMillis(),
    ): LearningState {
        val grades = mutableListOf<GradeState>()
        var previousCompleted = true
        for (g in Curriculum.grades) {
            val naturallyUnlocked = g == Curriculum.grades.first() || previousCompleted || g in storedUnlocked
            val unlocked = naturallyUnlocked || teacherMode
            val subjects = Subject.entries.map { subject ->
                val topics = Curriculum.topicsFor(subject, g)
                val guardianPassed = (1..Curriculum.guardianCount(subject, g)).associateWith { st ->
                    exams[examId(subject, g, st)]?.passed == true
                }
                var prevDone = true
                var prevTitle = ""
                val states = topics.mapIndexed { i, t ->
                    val p = progress[t.id]
                    val completed = p?.completed == true
                    val blockStart = i > 0 && i % Curriculum.BLOCK == 0
                    val guardianOk = !blockStart || guardianPassed[i / Curriculum.BLOCK] == true
                    val available = unlocked && ((prevDone && guardianOk) || teacherMode)
                    val status = when {
                        completed -> TopicStatus.COMPLETED
                        available -> TopicStatus.AVAILABLE
                        else -> TopicStatus.LOCKED
                    }
                    val hint = when {
                        status != TopicStatus.LOCKED -> null
                        !unlocked -> "Сначала заверши ${g - 1} класс"
                        !prevDone -> "Сдай контрольную по теме «$prevTitle» на ${(TOPIC_PASS_SCORE * 100).toInt()}% и выше"
                        else -> "Победи стража раздела «${Curriculum.boss(subject, g, i / Curriculum.BLOCK).name}»"
                    }
                    val lastTouch = p?.lastReviewedAt ?: p?.completedAt
                    prevDone = completed
                    prevTitle = t.title
                    TopicState(
                        t, status, p?.bestScore ?: 0f, p?.perfect == true, hint,
                        needsReview = completed && lastTouch != null && now - lastTouch > REVIEW_AFTER_MS,
                        reviewedAt = p?.lastReviewedAt, completedAt = p?.completedAt,
                    )
                }
                val guardians = guardianPassed.keys.sorted().map { st ->
                    val block = states.drop((st - 1) * Curriculum.BLOCK).take(Curriculum.BLOCK)
                    val blockDone = block.all { it.status == TopicStatus.COMPLETED }
                    val prevGuardianOk = st == 1 || guardianPassed[st - 1] == true
                    val available = unlocked && ((blockDone && prevGuardianOk) || teacherMode)
                    GuardianState(
                        st, available, guardianPassed[st] == true,
                        if (available) null else if (!unlocked) "Класс ещё закрыт" else "Пройди все темы раздела ${st}: ${block.joinToString { "«${it.topic.title}»" }}",
                    )
                }
                val done = states.count { it.status == TopicStatus.COMPLETED }
                val enough = states.isNotEmpty() && done.toFloat() / states.size >= threshold - 1e-4f
                val allGuardians = guardians.all { it.passed }
                val examAvailable = unlocked && ((enough && allGuardians) || teacherMode)
                val need = kotlin.math.ceil(threshold * states.size - 1e-4).toInt()
                SubjectGradeState(
                    subject, g, states, guardians,
                    examPassed = exams[examId(subject, g)]?.passed == true,
                    examAvailable = examAvailable,
                    examLockHint = when {
                        examAvailable -> null
                        !unlocked -> "Класс ещё закрыт"
                        !allGuardians -> "Сначала победи всех стражей разделов"
                        else -> "Пройди минимум $need из ${states.size} тем (${(threshold * 100).toInt()}%)"
                    },
                )
            }
            val completed = subjects.all { it.fraction >= threshold - 1e-4f && it.examPassed }
            grades += GradeState(
                grade = g,
                unlocked = unlocked,
                completed = completed,
                subjects = subjects,
                lockHint = if (unlocked) null else "Заверши ${g - 1} класс: пройди ${(threshold * 100).toInt()}% тем по каждому предмету, победи стражей и финальных боссов",
            )
            previousCompleted = completed
        }
        return LearningState(grades, teacherMode, threshold)
    }
}
