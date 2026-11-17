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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Question
import com.neuron.academy.content.Subject
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.ConfettiOverlay
import com.neuron.academy.ui.components.ErrorBlock
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.userMessage
import com.neuron.gamification.NeuronLines
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class BattlePhase { INTRO, FIGHT, WON, LOST }

class ExamViewModel(private val c: AppContainer, val subject: Subject, val grade: Int) : ViewModel() {
    val boss = Curriculum.boss(subject, grade)
    var questions by mutableStateOf<List<Question>>(emptyList())
        private set
    var phase by mutableStateOf(BattlePhase.INTRO)
    var index by mutableIntStateOf(0)
    var selected by mutableStateOf<Int?>(null)
    var correct by mutableIntStateOf(0)
    var bossHp by mutableIntStateOf(1)
    var bossMaxHp by mutableIntStateOf(1)
    var hearts by mutableIntStateOf(4)
    var maxHearts by mutableIntStateOf(4)
    var hitTick by mutableIntStateOf(0)
    var hurtTick by mutableIntStateOf(0)
    var line by mutableStateOf(boss.taunt)
    val aiGen = AiBlock()

    private fun setup(qs: List<Question>) {
        questions = qs
        bossMaxHp = ceil(qs.size * 0.7).toInt().coerceAtLeast(1)
        bossHp = bossMaxHp
        maxHearts = qs.size - bossMaxHp + 1
        hearts = maxHearts
        index = 0; correct = 0; selected = null
        line = boss.taunt
        phase = BattlePhase.FIGHT
    }

    fun startClassic() = setup(Curriculum.examPool(subject, grade).shuffled().take(10))

    fun startAi() {
        if (aiGen.loading) return
        aiGen.loading = true; aiGen.error = null; aiGen.noKeys = false
        viewModelScope.launch {
            try {
                val ls = c.progress.snapshot()
                val qs = c.tutor.generateTest(subject, grade, Curriculum.topicsFor(subject, grade), 10, ls.completedTopics.map { it.title }, exam = true)
                setup(qs)
            } catch (e: com.neuron.academy.ai.NoKeysException) {
                aiGen.noKeys = true; aiGen.error = e.userMessage()
            } catch (e: Exception) {
                aiGen.error = e.userMessage()
            } finally {
                aiGen.loading = false
            }
        }
    }

    fun answer(i: Int) {
        if (selected != null || phase != BattlePhase.FIGHT) return
        selected = i
        if (i == questions[index].correct) {
            correct++; bossHp--; hitTick++
            line = NeuronLines.random(NeuronLines.correct)
            c.feedback.correct()
        } else {
            hearts--; hurtTick++
            line = "${boss.name}: «Ха! Мимо!»"
            c.feedback.wrong()
        }
    }

    fun next() {
        when {
            bossHp <= 0 -> finish(true)
            hearts <= 0 || index >= questions.lastIndex -> finish(false)
            else -> { index++; selected = null }
        }
    }

    private fun finish(won: Boolean) {
        phase = if (won) BattlePhase.WON else BattlePhase.LOST
        line = if (won) boss.defeatLine else NeuronLines.random(NeuronLines.bossLose)
        val total = index + 1
        viewModelScope.launch {
            val outcome = c.progress.recordExam(subject, grade, correct, total, won)
            c.game.onExam(subject, grade, correct, won, outcome)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(nav: NavHostController, subject: Subject, grade: Int) {
    val vm = containerViewModel(key = "exam_${subject.key}_$grade") { ExamViewModel(it, subject, grade) }
    val boss = vm.boss
    val bossShake = remember { Animatable(0f) }
    val playerShake = remember { Animatable(0f) }
    LaunchedEffect(vm.hitTick) {
        if (vm.hitTick > 0) for (x in listOf(20f, -20f, 14f, -14f, 6f, 0f)) bossShake.animateTo(x, tween(45))
    }
    LaunchedEffect(vm.hurtTick) {
        if (vm.hurtTick > 0) for (x in listOf(12f, -12f, 8f, -8f, 0f)) playerShake.animateTo(x, tween(45))
    }
    val hpFraction by animateFloatAsState(vm.bossHp.toFloat() / vm.bossMaxHp, tween(500), label = "hp")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Итоговый экзамен · ${subject.title} ${grade} кл.") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF311B92).copy(alpha = 0.35f), MaterialTheme.colorScheme.background))),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (vm.phase == BattlePhase.WON) "💥" else boss.emoji,
                            fontSize = 84.sp,
                            modifier = Modifier.graphicsLayer {
                                translationX = bossShake.value
                                alpha = if (vm.phase == BattlePhase.WON) 0.7f else 1f
                            },
                        )
                        Text(boss.name, style = MaterialTheme.typography.titleLarge)
                        if (vm.phase != BattlePhase.INTRO) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { hpFraction },
                                modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)),
                                color = GameColors.Error, trackColor = GameColors.Error.copy(alpha = 0.2f), strokeCap = StrokeCap.Round,
                            )
                            Text("HP босса: ${vm.bossHp.coerceAtLeast(0)} / ${vm.bossMaxHp}", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("«${vm.line}»", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                when (vm.phase) {
                    BattlePhase.INTRO -> item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Каждый правильный ответ отнимает у босса 1 HP. За ошибку ты теряешь сердечко ❤️. " +
                                    "Победи босса — и экзамен по предмету сдан!",
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = vm::startClassic, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("⚔️ В бой! (вопросы из курса)") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = vm::startAi, enabled = !vm.aiGen.loading, modifier = Modifier.fillMaxWidth()) { Text("🤖 Экзамен, составленный ИИ") }
                            if (vm.aiGen.loading) LoadingBlock("Босс готовит каверзные вопросы…")
                            vm.aiGen.error?.let { ErrorBlock(it, if (vm.aiGen.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) }
                        }
                    }
                    BattlePhase.FIGHT -> {
                        item {
                            Row(Modifier.fillMaxWidth().graphicsLayer { translationX = playerShake.value }, verticalAlignment = Alignment.CenterVertically) {
                                Text("Ты: ", style = MaterialTheme.typography.titleMedium)
                                repeat(vm.maxHearts) { i -> Text(if (i < vm.hearts) "❤️" else "🖤", fontSize = 22.sp) }
                                Spacer(Modifier.weight(1f))
                                Text("Вопрос ${vm.index + 1}/${vm.questions.size}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        item {
                            QuestionCard(vm.questions[vm.index], vm.selected, emptySet(), vm::answer)
                        }
                        item {
                            Button(onClick = vm::next, enabled = vm.selected != null, modifier = Modifier.fillMaxWidth()) { Text("Дальше ➜") }
                        }
                    }
                    BattlePhase.WON, BattlePhase.LOST -> item {
                        val won = vm.phase == BattlePhase.WON
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(if (won) "🏆 Победа!" else "😵 Поражение", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text("Правильных ответов: ${vm.correct}")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (won) "Экзамен по предмету «${subject.title}» за $grade класс сдан!" else "Повтори темы, в которых ошибся, и возвращайся. Босс никуда не денется 😉",
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (!won) Button(onClick = { vm.phase = BattlePhase.INTRO }, modifier = Modifier.fillMaxWidth()) { Text("Реванш!") }
                            OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("На карту") }
                        }
                    }
                }
            }
            if (vm.phase == BattlePhase.WON) ConfettiOverlay()
        }
    }
}
