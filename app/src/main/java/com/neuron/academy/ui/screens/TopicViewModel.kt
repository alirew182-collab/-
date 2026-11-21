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
    /** Текст, который встроенный ИИ печатает прямо сейчас. */
    var partial by mutableStateOf<String?>(null)
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

/** Задание контрольной. */
sealed interface ControlItem {
    data class Choice(val q: Question, val source: String) : ControlItem
    data class Open(val p: Practice) : ControlItem
}

class TopicViewModel(private val c: AppContainer, topicId: String) : ViewModel() {
    val topic: Topic? = Curriculum.topic(topicId)
    val prereqs: List<Topic> = topic?.let { Curriculum.prerequisitesOf(it) } ?: emptyList()
    val learning: StateFlow<LearningState> = c.progress.learningState.stateIn(viewModelScope, SharingStarted.Eagerly, LearningState.EMPTY)
    val hasKeys get() = c.router.isAvailable()

    val explain = AiBlock()
    var explainStyle by mutableStateOf<ExplainStyle?>(null)
    val why = AiBlock()
    val fact = AiBlock()
    val prereq = AiBlock()
    val generate = AiBlock()
    val deep = AiBlock()
    val multiAi = AiBlock()
    /** ИИ-разборы «другими способами» для примеров и задач (ключ — текст условия). */
    val altBlocks = mutableMapOf<String, AiBlock>()

    init {
        // встроенный ИИ заранее загружается в память — первый ответ придёт быстрее
        if (c.router.usesBuiltin) c.localLlm.warmUp()
    }

    fun altBlock(key: String): AiBlock = altBlocks.getOrPut(key) { AiBlock() }

    fun askDeep() { val t = topic ?: return; run(deep) { c.tutor.deepExplain(t, grade(), completedTitles()) } }
    fun askMultiProblem() { val t = topic ?: return; run(multiAi) { c.tutor.multiMethodProblem(t, grade(), completedTitles()) } }
    fun askAlternative(problem: String, solution: String) {
        val t = topic ?: return
        run(altBlock(problem)) { c.tutor.alternativeMethods(t, problem, solution, grade(), completedTitles()) }
    }

    val practice = mutableStateListOf<PracticeItem>().apply { topic?.practice?.forEach { add(PracticeItem(it)) } }

    // ---- контрольная ----
    var control by mutableStateOf(buildControl())
        private set
    var started by mutableStateOf(false)
    var qIndex by mutableStateOf(0)
    var selected by mutableStateOf<Int?>(null)
    var openInput by mutableStateOf("")
    var openResult by mutableStateOf<Boolean?>(null)
    var correctCount by mutableStateOf(0)
    var finished by mutableStateOf(false)
    var removedOptions by mutableStateOf(setOf<Int>())
    var aiTestLabel by mutableStateOf(false)
    var testResultText by mutableStateOf<String?>(null)
    var passed by mutableStateOf(false)
    var heartsMessage by mutableStateOf<String?>(null)
    val testGen = AiBlock()

    val answeredCurrent get() = selected != null || openResult != null

    /**
     * Контрольная среднего уровня: вопросы теста по теме, задачи с открытым ответом
     * и пара вопросов из прошлых тем, на которые опирается урок.
     */
    private fun buildControl(): List<ControlItem> {
        val t = topic ?: return emptyList()
        val own = t.test.shuffled().map { ControlItem.Choice(it, "По теме урока") }
        val open = t.practice.filter { !it.hard }.shuffled().take(2).map { ControlItem.Open(it) }
        val past = prereqs.flatMap { p -> p.test.map { ControlItem.Choice(it, "Из темы «${p.title}»") } }.shuffled().take(2)
        return (own + open + past).shuffled()
    }

    private fun completedTitles() = learning.value.completedTopics.map { it.title }
    private fun grade() = learning.value.currentGrade

    private fun run(block: AiBlock, action: suspend () -> String) {
        if (block.loading) return
        block.loading = true; block.error = null; block.noKeys = false; block.partial = null
        viewModelScope.launch {
            try {
                block.text = kotlinx.coroutines.withContext(com.neuron.academy.ai.AiStream { p -> viewModelScope.launch { if (block.loading) block.partial = p } }) { action() }
            } catch (e: com.neuron.academy.ai.NoKeysException) {
                block.noKeys = true; block.error = e.userMessage()
            } catch (e: Exception) {
                block.error = e.userMessage()
            } finally {
                block.loading = false
                block.partial = null
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
            "Новая задача добавлена"
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
            (if (r.correct) "Верно.\n\n" else "Есть неточности.\n\n") + r.feedback
        }
    }

    // ---- контрольная ----
    /** Начать контрольную: нужно хотя бы одно сердце. */
    fun startControl() {
        viewModelScope.launch {
            if (!c.game.hasHearts()) {
                val p = c.db.playerDao().get() ?: com.neuron.academy.data.db.PlayerEntity()
                heartsMessage = "Нет сердец. Новое появится через ${c.game.minutesToNextHeart(p)} мин. Можно пройти повторение (+1 сердце) или купить сердца в магазине."
                return@launch
            }
            heartsMessage = null
            started = true
        }
    }

    fun answer(i: Int) {
        val item = control.getOrNull(qIndex) as? ControlItem.Choice ?: return
        if (selected != null || finished) return
        selected = i
        if (i == item.q.correct) { correctCount++; c.feedback.correct() } else c.feedback.wrong()
    }

    fun submitOpen() {
        val item = control.getOrNull(qIndex) as? ControlItem.Open ?: return
        if (openResult != null || openInput.isBlank()) return
        val ok = AnswerChecker.matches(openInput, item.p.answer)
        openResult = ok
        if (ok) { correctCount++; c.feedback.correct() } else c.feedback.wrong()
    }

    fun useFiftyFifty() {
        val item = control.getOrNull(qIndex) as? ControlItem.Choice ?: return
        if (selected != null || removedOptions.isNotEmpty()) return
        viewModelScope.launch {
            if (c.game.useHint()) {
                val q = item.q
                removedOptions = q.options.indices.filter { it != q.correct }.shuffled().take((q.options.size - 2).coerceAtLeast(1)).toSet()
            }
        }
    }

    fun next() {
        if (qIndex < control.lastIndex) {
            qIndex++; selected = null; openInput = ""; openResult = null; removedOptions = emptySet()
        } else finishTest()
    }

    private fun finishTest() {
        val t = topic ?: return
        finished = true
        val total = control.size
        val correct = correctCount
        viewModelScope.launch {
            val outcome = c.progress.recordTopicTest(t, correct, total)
            passed = outcome.passed
            c.game.onTopicTest(t, correct, total, outcome)
            if (!outcome.passed) c.game.spendHeart()
            testResultText = when {
                outcome.perfect -> "Без единой ошибки"
                outcome.passed -> "Контрольная сдана, тема пройдена"
                else -> "Нужно хотя бы 70%. Ты теряешь одно сердце. Повтори теорию и попробуй ещё раз."
            }
        }
    }

    fun restartTest(newItems: List<ControlItem>? = null) {
        control = newItems ?: buildControl()
        qIndex = 0; selected = null; openInput = ""; openResult = null; correctCount = 0; finished = false
        removedOptions = emptySet(); testResultText = null; started = false
        aiTestLabel = newItems != null
    }

    fun generateAiTest() {
        val t = topic ?: return
        run(testGen) {
            val qs = c.tutor.generateTest(t.subject, t.grade, listOf(t), 8, completedTitles())
            restartTest(qs.map { ControlItem.Choice(it, "Вопрос от ИИ") })
            "Новый тест готов"
        }
    }
}
