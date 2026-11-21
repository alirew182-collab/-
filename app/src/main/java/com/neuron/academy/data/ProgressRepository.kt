package com.neuron.academy.data

import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.content.Topic
import com.neuron.academy.data.db.ExamEntity
import com.neuron.academy.data.db.ProgressDao
import com.neuron.academy.data.db.TestResultEntity
import com.neuron.academy.data.db.TopicProgressEntity
import com.neuron.academy.data.db.UnlockedGradeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class TopicTestOutcome(val passed: Boolean, val firstCompletion: Boolean, val perfect: Boolean, val firstPerfect: Boolean)
data class ExamOutcome(val firstWin: Boolean, val newlyCompletedGrades: List<Int>)

class ProgressRepository(
    private val dao: ProgressDao,
    private val settings: SettingsRepository,
) {
    val learningState: Flow<LearningState> = combine(
        dao.observeTopics(),
        dao.observeExams(),
        dao.observeUnlockedGrades(),
        settings.gradeThreshold,
        settings.teacherMode,
    ) { topics, exams, unlocked, threshold, teacher ->
        UnlockEngine.compute(
            topics.associateBy { it.topicId },
            exams.associateBy { it.id },
            unlocked.map { it.grade }.toSet(),
            threshold,
            teacher,
        )
    }

    suspend fun snapshot(): LearningState = learningState.first()

    val allResults: Flow<List<TestResultEntity>> = dao.observeAllResults()

    /** @return true, если теория прочитана впервые */
    suspend fun markTheoryRead(topicId: String): Boolean {
        val old = dao.getTopic(topicId) ?: TopicProgressEntity(topicId)
        if (old.theoryRead) return false
        dao.upsertTopic(old.copy(theoryRead = true))
        return true
    }

    suspend fun recordTopicTest(topic: Topic, correct: Int, total: Int): TopicTestOutcome {
        val score = if (total == 0) 0f else correct.toFloat() / total
        val passed = score >= UnlockEngine.TOPIC_PASS_SCORE - 1e-4f
        val perfect = total > 0 && correct == total
        val old = dao.getTopic(topic.id) ?: TopicProgressEntity(topic.id)
        val firstCompletion = passed && !old.completed
        val firstPerfect = perfect && !old.perfect
        dao.upsertTopic(
            old.copy(
                bestScore = maxOf(old.bestScore, score),
                completed = old.completed || passed,
                perfect = old.perfect || perfect,
                attempts = old.attempts + 1,
                completedAt = old.completedAt ?: if (passed) System.currentTimeMillis() else null,
            ),
        )
        recordResult("TOPIC", topic.subject, topic.grade, topic.id, correct, total)
        syncUnlockedGrades()
        return TopicTestOutcome(passed, firstCompletion, perfect, firstPerfect)
    }

    suspend fun recordExam(subject: Subject, grade: Int, correct: Int, total: Int, won: Boolean, stage: Int = 0): ExamOutcome {
        val before = snapshot().completedGrades
        val id = UnlockEngine.examId(subject, grade, stage)
        val old = dao.getExam(id)
        val score = if (total == 0) 0f else correct.toFloat() / total
        dao.upsertExam(
            ExamEntity(
                id = id, subject = subject.key, grade = grade,
                passed = (old?.passed == true) || won,
                bestScore = maxOf(old?.bestScore ?: 0f, score),
                attempts = (old?.attempts ?: 0) + 1,
                passedAt = old?.passedAt ?: if (won) System.currentTimeMillis() else null,
            ),
        )
        recordResult(if (stage == 0) "EXAM" else "GUARDIAN", subject, grade, null, correct, total)
        syncUnlockedGrades()
        val after = snapshot().completedGrades
        return ExamOutcome(firstWin = won && old?.passed != true, newlyCompletedGrades = (after - before).sorted())
    }

    /** Рейд недели: id = raid_<номер недели>. @return true, если победа на этой неделе первая. */
    suspend fun recordRaid(week: Long, correct: Int, total: Int, won: Boolean): Boolean {
        val id = "raid_$week"
        val old = dao.getExam(id)
        dao.upsertExam(
            ExamEntity(id, "raid", 0, (old?.passed == true) || won, maxOf(old?.bestScore ?: 0f, correct.toFloat() / total.coerceAtLeast(1)), (old?.attempts ?: 0) + 1, old?.passedAt ?: if (won) System.currentTimeMillis() else null),
        )
        return won && old?.passed != true
    }

    suspend fun raidWonThisWeek(week: Long): Boolean = dao.getExam("raid_$week")?.passed == true

    /** Отмечает темы как повторённые. */
    suspend fun markReviewed(topicIds: Collection<String>) {
        val now = System.currentTimeMillis()
        topicIds.forEach { id -> dao.getTopic(id)?.let { dao.upsertTopic(it.copy(lastReviewedAt = now)) } }
    }

    suspend fun recordResult(kind: String, subject: Subject, grade: Int, topicId: String?, correct: Int, total: Int) {
        dao.insertResult(TestResultEntity(topicId = topicId, subject = subject.key, grade = grade, kind = kind, correct = correct, total = total))
    }

    /**
     * Восстановление прогресса из прошлой версии: отмечает первые темы пройденными, стражей
     * пройденных разделов и боссов — побеждёнными, открывает классы. Прогресс только добавляется.
     * @return сколько стражей и боссов добавлено (для счётчиков игрока).
     */
    suspend fun restore(items: List<SubjectGradeRestore>): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        var guardians = 0
        var bosses = 0
        suspend fun pass(id: String, subject: Subject, grade: Int): Boolean {
            val old = dao.getExam(id)
            if (old?.passed == true) return false
            dao.upsertExam(ExamEntity(id, subject.key, grade, true, maxOf(old?.bestScore ?: 0f, 0.8f), (old?.attempts ?: 0) + 1, now))
            return true
        }
        items.forEach { r ->
            val topics = Curriculum.topicsFor(r.subject, r.grade)
            val n = if (r.bossBeaten) topics.size else r.completed.coerceIn(0, topics.size)
            topics.take(n).forEach { t ->
                val old = dao.getTopic(t.id)
                if (old?.completed != true) {
                    dao.upsertTopic(
                        (old ?: TopicProgressEntity(t.id)).copy(
                            theoryRead = true, completed = true,
                            bestScore = maxOf(old?.bestScore ?: 0f, 0.8f),
                            completedAt = old?.completedAt ?: now, lastReviewedAt = now,
                        ),
                    )
                }
            }
            // страж раздела st побеждён, если ученик ушёл дальше этого раздела
            for (st in 1..Curriculum.guardianCount(r.subject, r.grade)) {
                if ((n > st * Curriculum.BLOCK || r.bossBeaten) && pass(UnlockEngine.examId(r.subject, r.grade, st), r.subject, r.grade)) guardians++
            }
            if (r.bossBeaten && pass(UnlockEngine.examId(r.subject, r.grade), r.subject, r.grade)) bosses++
            if (n > 0) Curriculum.grades.filter { it <= r.grade }.forEach { dao.unlockGrade(UnlockedGradeEntity(it)) }
        }
        syncUnlockedGrades()
        return guardians to bosses
    }

    /** Фиксирует открытые классы в базе, чтобы изменение порога не закрывало их обратно. */
    suspend fun syncUnlockedGrades() {
        val state = snapshot()
        state.grades.forEach { g ->
            val naturally = g.grade == 9 || state.grade(g.grade - 1).completed
            if (naturally) dao.unlockGrade(UnlockedGradeEntity(g.grade))
        }
    }
}
