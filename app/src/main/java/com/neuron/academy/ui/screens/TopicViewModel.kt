package com.neuron.academy.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuron.academy.AppContainer
import com.neuron.academy.ai.Difficulty
import com.neuron.academy.ai.ExplainStyle
import com.neuron.academy.content.AnswerChecker
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Practice
import com.neuron.academy.content.Question
import com.neuron.academy.content.Topic
import com.neuron.academy.data.LearningState
import com.neuron.academy.ui.userMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Состояние одного блока ответа ИИ. */
class AiBlock {
    var loading by mutableStateOf(false)
    var text by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var noKeys by mutableStateOf(false)
}

class PracticeItem(val task: Practice, val aiGenerated: Boolean = false) {
    var input by mutableStateOf("")
    var result by mutableStateOf<Boolean?>(null)
    var showHint by mutableStateOf(false)
    var showSolution by mutableStateOf(false)
    var rewarded by mutableStateOf(false)
    var fullSolution by mutableStateOf("")
    val check = AiBlock()
}

class TopicViewModel(private val c: AppContainer, topicId: String) : ViewModel() {
    val topic: Topic? = Curriculum.topic(topicId)
    val prereqs: List<Topic> = topic?.let { Curriculum.prerequisitesOf(it) } ?: emptyList()
    val learning: StateFlow<LearningState> = c.progress.learningState.stateIn(viewModelScope, SharingStarted.Eagerly, LearningState.EMPTY)
    val hasKeys get() = c.router.hasKeys()

    val explain = AiBlock()
    var explainStyle by mutableStateOf<ExplainStyle?>(null)
    val why = AiBlock()
    val fact = AiBlock()
    val prereq = AiBlock()
    val generate = AiBlock()

    val practice = mutableStateListOf<PracticeItem>().apply { topic?.practice?.forEach { add(PracticeItem(it)) } }

    // ---- тест ----
    var questions by mutableStateOf(topic?.test ?: emptyList<Question>())
        private set
    var qIndex by mutableStateOf(0)
    var selected by mutableStateOf<Int?>(null)
    var correctCount by mutableStateOf(0)
    var finished by mutableStateOf(false)
    var removedOptions by mutableStateOf(setOf<Int>())
    var aiTestLabel by mutableStateOf(false)
    var testResultText by mutableStateOf<String?>(null)
    var passed by mutableStateOf(false)
    val testGen = AiBlock()

    private fun completedTitles() = learning.value.completedTopics.map { it.title }
    private fun grade() = learning.value.currentGrade

    private fun run(block: AiBlock, action: suspend () -> String) {
        if (block.loading) return
        block.loading = true; block.error = null; block.noKeys = false
        viewModelScope.launch {
            try {
                block.text = action()
            } catch (e: com.neuron.academy.ai.NoKeysException) {
                block.noKeys = true; block.error = e.userMessage()
            } catch (e: Exception) {
                block.error = e.userMessage()
            } finally {
                block.loading = false
            }
        }
    }

    fun onTheoryOpened() {
        val t = topic ?: return
        viewModelScope.launch {
            val first = c.progress.markTheoryRead(t.id)
            c.game.onTheoryRead(t, first)
        }
    }

    fun explainAs(style: ExplainStyle) {
        val t = topic ?: return
        explainStyle = style
        explain.text = null
        run(explain) { c.tutor.explain(t, style, grade(), completedTitles()) }
    }

    fun askWhy() { val t = topic ?: return; run(why) { c.tutor.whyNeeded(t, grade(), completedTitles()) } }
    fun askFact() { val t = topic ?: return; run(fact) { c.tutor.interestingFact(t, grade(), completedTitles()) } }
    fun askPrereq() { val t = topic ?: return; run(prereq) { c.tutor.prerequisites(t, prereqs, grade(), completedTitles()) } }

    fun generateTask(d: Difficulty) {
        val t = topic ?: return
        run(generate) {
            val g = c.tutor.generateTask(t, d, grade(), completedTitles())
            practice.add(0, PracticeItem(Practice(g.problem, g.answer, g.hint, g.solution, hard = d == Difficulty.HARD), aiGenerated = true))
            "Новая задача добавлена!"
        }
    }

    fun checkPractice(item: PracticeItem) {
        val t = topic ?: return
        val ok = AnswerChecker.matches(item.input, item.task.answer)
        item.result = ok
        if (ok) {
            c.feedback.correct()
            if (!item.rewarded) {
                item.rewarded = true
                viewModelScope.launch { c.game.onPracticeSolved(t, t.subject, usedHint = item.showHint || item.showSolution, hard = item.task.hard) }
            }
        } else c.feedback.wrong()
    }

    fun checkFullSolution(item: PracticeItem) {
        val t = topic ?: return
        if (item.fullSolution.isBlank()) return
        run(item.check) {
            val r = c.tutor.checkSolution(t.title, item.task.problem, item.fullSolution, item.task.answer, grade(), completedTitles())
            if (r.correct && !item.rewarded) {
                item.rewarded = true; item.result = true
                c.feedback.correct()
                c.game.onPracticeSolved(t, t.subject, usedHint = item.showHint, hard = item.task.hard)
            }
            (if (r.correct) "✅ Верно!\n\n" else "🤔 Есть неточности.\n\n") + r.feedback
        }
    }

    // ---- тест ----
    fun answer(i: Int) {
        if (selected != null || finished) return
        selected = i
        if (i == questions[qIndex].correct) { correctCount++; c.feedback.correct() } else c.feedback.wrong()
    }

    fun useFiftyFifty() {
        if (selected != null || removedOptions.isNotEmpty()) return
        viewModelScope.launch {
            if (c.game.useHint()) {
                val q = questions[qIndex]
                removedOptions = q.options.indices.filter { it != q.correct }.shuffled().take((q.options.size - 2).coerceAtLeast(1)).toSet()
            }
        }
    }

    fun next() {
        if (qIndex < questions.lastIndex) {
            qIndex++; selected = null; removedOptions = emptySet()
        } else finishTest()
    }

    private fun finishTest() {
        val t = topic ?: return
        finished = true
        val total = questions.size
        val correct = correctCount
        viewModelScope.launch {
            val outcome = c.progress.recordTopicTest(t, correct, total)
            passed = outcome.passed
            c.game.onTopicTest(t, correct, total, outcome)
            testResultText = when {
                outcome.perfect -> "Идеально! Ни одной ошибки 💯"
                outcome.passed -> "Тест сдан! Тема пройдена 🎉"
                else -> "Нужно хотя бы 70%. Повтори теорию и попробуй ещё раз — у тебя получится! 💪"
            }
        }
    }

    fun restartTest(newQuestions: List<Question>? = null) {
        questions = newQuestions ?: topic?.test?.shuffled() ?: emptyList()
        qIndex = 0; selected = null; correctCount = 0; finished = false; removedOptions = emptySet(); testResultText = null
        aiTestLabel = newQuestions != null
    }

    fun generateAiTest() {
        val t = topic ?: return
        run(testGen) {
            val qs = c.tutor.generateTest(t.subject, t.grade, listOf(t), 5, completedTitles())
            restartTest(qs)
            "Новый тест готов!"
        }
    }
}
