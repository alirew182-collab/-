package com.neuron.academy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Boss
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Question
import com.neuron.academy.content.Subject
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.ConfettiOverlay
import com.neuron.academy.ui.components.ErrorBlock
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.ThinProgress
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.userMessage
import com.neuron.gamification.Hearts
import com.neuron.gamification.NeuronLines
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil

enum class BattlePhase { INTRO, FIGHT, WON, LOST }

/** Номер недели для рейда (меняется по понедельникам). */
fun currentWeek(): Long = Math.floorDiv(LocalDate.now().toEpochDay() + 3, 7L)

/**
 * Битва: финальный босс класса (stage = 0), страж раздела (stage 1..n) или рейд недели (raid = true).
 */
class ExamViewModel(
    private val c: AppContainer,
    val subject: Subject,
    val grade: Int,
    val stage: Int,
    val raid: Boolean,
) : ViewModel() {
    val week = currentWeek()
    val boss: Boss = if (raid) Curriculum.raidBoss(week) else Curriculum.boss(subject, grade, stage)
    val player = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())

    var questions by mutableStateOf<List<Question>>(emptyList())
        private set
    var phase by mutableStateOf(BattlePhase.INTRO)
    var index by mutableIntStateOf(0)
    var selected by mutableStateOf<Int?>(null)
    var correct by mutableIntStateOf(0)
    var bossHp by mutableIntStateOf(1)
    var bossMaxHp by mutableIntStateOf(1)
    var lives by mutableIntStateOf(4)
    var maxLives by mutableIntStateOf(4)
    var hitTick by mutableIntStateOf(0)
    var hurtTick by mutableIntStateOf(0)
    var timeLeft by mutableIntStateOf(0)
    var line by mutableStateOf(boss.taunt)
    var message by mutableStateOf<String?>(null)
    var raidPool by mutableStateOf<List<Question>?>(null)
    var raidDone by mutableStateOf(false)
    val aiGen = AiBlock()
    private var timer: Job? = null

    val enraged get() = boss.enrages && phase == BattlePhase.FIGHT && bossHp * 2 <= bossMaxHp && bossHp > 0

    init {
        if (raid) viewModelScope.launch {
            val ls = c.progress.snapshot()
            raidPool = ls.completedTopics.flatMap { it.test }
            raidDone = c.progress.raidWonThisWeek(week)
        }
    }

    private fun pool(): List<Question> = if (raid) raidPool.orEmpty() else Curriculum.examPool(subject, grade, stage)

    private fun setup(qs: List<Question>) {
        questions = qs
        bossMaxHp = ceil(qs.size * boss.passShare).toInt().coerceAtLeast(1)
        bossHp = bossMaxHp
        maxLives = qs.size - bossMaxHp + 1
        lives = maxLives
        index = 0; correct = 0; selected = null
        line = boss.taunt
        phase = BattlePhase.FIGHT
        startTimer()
    }

    /** Вход в битву стоит одно сердце. */
    private fun enter(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!c.game.spendHeart()) {
                val p = player.value
                message = "Нет сердец. Новое появится через ${c.game.minutesToNextHeart(p)} мин — или пройди повторение, или купи сердца в магазине."
                return@launch
            }
            message = null
            block()
        }
    }

    fun startClassic() {
        val p = pool()
        if (raid && p.size < 12) { message = "Для рейда нужно пройти хотя бы 3 темы — вопросы берутся из них."; return }
        enter { setup(p.shuffled().take(boss.questions)) }
    }

    fun startAi() {
        if (aiGen.loading || raid) return
        enter {
            aiGen.loading = true; aiGen.error = null; aiGen.noKeys = false
            try {
                val ls = c.progress.snapshot()
                val topics = if (stage == 0) Curriculum.topicsFor(subject, grade) else Curriculum.blockTopics(subject, grade, stage)
                setup(c.tutor.generateTest(subject, grade, topics, boss.questions, ls.completedTopics.map { it.title }, exam = true))
            } catch (e: com.neuron.academy.ai.NoKeysException) {
                aiGen.noKeys = true; aiGen.error = e.userMessage()
            } catch (e: Exception) {
                aiGen.error = e.userMessage()
            } finally {
                aiGen.loading = false
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        if (boss.timerSec <= 0) return
        timeLeft = boss.timerSec
        timer = viewModelScope.launch {
            while (timeLeft > 0 && selected == null && phase == BattlePhase.FIGHT) {
                delay(1000)
                if (selected == null) timeLeft--
            }
            if (selected == null && phase == BattlePhase.FIGHT) answer(-1)
        }
    }

    fun answer(i: Int) {
        if (selected != null || phase != BattlePhase.FIGHT) return
        val wasEnraged = enraged
        selected = i
        timer?.cancel()
        if (i == questions[index].correct) {
            correct++; bossHp--; hitTick++
            line = NeuronLines.random(NeuronLines.correct)
            c.feedback.correct()
        } else {
            lives -= if (wasEnraged) 2 else 1
            hurtTick++
            line = when {
                i == -1 -> "Время вышло! ${boss.name} атакует!"
                wasEnraged -> "${boss.name} в ярости: двойной урон!"
                else -> "${boss.name}: «Мимо!»"
            }
            c.feedback.wrong()
        }
    }

    fun next() {
        when {
            bossHp <= 0 -> finish(true)
            lives <= 0 || index >= questions.lastIndex -> finish(false)
            else -> { index++; selected = null; startTimer() }
        }
    }

    private fun finish(won: Boolean) {
        timer?.cancel()
        phase = if (won) BattlePhase.WON else BattlePhase.LOST
        line = if (won) boss.defeatLine else NeuronLines.random(NeuronLines.bossLose)
        val total = index + 1
        val ok = correct
        viewModelScope.launch {
            if (raid) {
                val first = c.progress.recordRaid(week, ok, total, won)
                c.game.onRaid(ok, won, first, boss.rewardCardId)
                raidDone = raidDone || won
            } else {
                val outcome = c.progress.recordExam(subject, grade, ok, total, won, stage)
                c.game.onExam(subject, grade, ok, won, outcome, stage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(nav: NavHostController, subject: Subject, grade: Int, stage: Int = 0, raid: Boolean = false) {
    val vm = containerViewModel(key = "exam_${subject.key}_${grade}_${stage}_$raid") { ExamViewModel(it, subject, grade, stage, raid) }
    val boss = vm.boss
    val player by vm.player.collectAsState()
    val bossShake = remember { Animatable(0f) }
    val playerShake = remember { Animatable(0f) }
    LaunchedEffect(vm.hitTick) { if (vm.hitTick > 0) for (x in listOf(20f, -20f, 14f, -14f, 6f, 0f)) bossShake.animateTo(x, tween(45)) }
    LaunchedEffect(vm.hurtTick) { if (vm.hurtTick > 0) for (x in listOf(12f, -12f, 8f, -8f, 0f)) playerShake.animateTo(x, tween(45)) }
    val hpFraction by animateFloatAsState(vm.bossHp.toFloat() / vm.bossMaxHp, tween(500), label = "hp")
    val kind = when { raid -> "Рейд недели"; stage > 0 -> "Страж раздела $stage"; else -> "Итоговый экзамен" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(kind, style = MaterialTheme.typography.titleLarge)
                        Text(if (raid) "Все пройденные темы" else "${subject.title} · $grade класс", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Panel(color = if (vm.enraged) GameColors.Error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (vm.phase == BattlePhase.WON) "💥" else boss.emoji, fontSize = 76.sp,
                                modifier = Modifier.graphicsLayer { translationX = bossShake.value },
                            )
                            Text(boss.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            if (vm.enraged) Eyebrow("Ярость: ошибка стоит 2 жизни", color = GameColors.Error)
                            if (vm.phase != BattlePhase.INTRO) {
                                Spacer(Modifier.height(8.dp))
                                ThinProgress(hpFraction, GameColors.Error, height = 10.dp)
                                Text("HP ${vm.bossHp.coerceAtLeast(0)} / ${vm.bossMaxHp}", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("«${vm.line}»", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                when (vm.phase) {
                    BattlePhase.INTRO -> item {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Panel {
                                Eyebrow("Правила битвы")
                                Spacer(Modifier.height(6.dp))
                                Text("• Вопросов: ${boss.questions}, чтобы победить — ${(boss.passShare * 100).toInt()}% верных.")
                                if (boss.timerSec > 0) Text("• На каждый вопрос ${boss.timerSec} секунд.")
                                if (boss.enrages) Text("• Когда у босса меньше половины HP, он входит в ярость: ошибка отнимает 2 жизни.")
                                Text("• Вход в битву стоит 1 сердце (у тебя ${c_hearts(player)} из ${Hearts.MAX}).")
                                if (raid) Text(if (vm.raidDone) "• На этой неделе ты уже победил — награда только за первую победу." else "• Первая победа за неделю: +150 XP, +100 кристаллов и редкая карточка.")
                            }
                            Button(onClick = vm::startClassic, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !raid || vm.raidPool != null) { Text("В бой") }
                            if (!raid) OutlinedButton(onClick = vm::startAi, enabled = !vm.aiGen.loading, modifier = Modifier.fillMaxWidth()) { Text("Битва с вопросами от ИИ") }
                            if (vm.aiGen.loading) LoadingBlock("Босс готовит каверзные вопросы…")
                            vm.aiGen.error?.let { ErrorBlock(it, if (vm.aiGen.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) }
                            vm.message?.let { ErrorBlock(it) }
                        }
                    }
                    BattlePhase.FIGHT -> {
                        item {
                            Row(Modifier.fillMaxWidth().graphicsLayer { translationX = playerShake.value }, verticalAlignment = Alignment.CenterVertically) {
                                repeat(vm.maxLives) { i -> Icon(if (i < vm.lives) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = GameColors.Error) }
                                Spacer(Modifier.weight(1f))
                                if (boss.timerSec > 0 && vm.selected == null) {
                                    Text("${vm.timeLeft} с  ", style = MaterialTheme.typography.titleMedium, color = if (vm.timeLeft <= 5) GameColors.Error else MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${vm.index + 1}/${vm.questions.size}", style = MaterialTheme.typography.labelLarge)
                            }
                            if (boss.timerSec > 0 && vm.selected == null) ThinProgress(vm.timeLeft / boss.timerSec.toFloat(), if (vm.timeLeft <= 5) GameColors.Error else MaterialTheme.colorScheme.primary, height = 4.dp)
                        }
                        item { QuestionCard(vm.questions[vm.index], vm.selected, emptySet(), vm::answer) }
                        item { Button(onClick = vm::next, enabled = vm.selected != null, modifier = Modifier.fillMaxWidth()) { Text("Дальше") } }
                    }
                    BattlePhase.WON, BattlePhase.LOST -> item {
                        val won = vm.phase == BattlePhase.WON
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(if (won) "Победа!" else "Поражение", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text("Правильных ответов: ${vm.correct}")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    !won -> "Повтори темы, в которых ошибся, и возвращайся. Босс никуда не денется."
                                    raid -> "Рейд недели пройден!"
                                    stage > 0 -> "Страж повержен — следующий раздел открыт!"
                                    else -> "Экзамен по предмету «${subject.title}» за $grade класс сдан!"
                                },
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (!won) Button(onClick = { vm.phase = BattlePhase.INTRO }, modifier = Modifier.fillMaxWidth()) { Text("Реванш") }
                            OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
                        }
                    }
                }
            }
            if (vm.phase == BattlePhase.WON) ConfettiOverlay()
        }
    }
}

private fun c_hearts(p: PlayerEntity) = Hearts.current(p.hearts, p.heartsUpdatedAt, System.currentTimeMillis()).first
