package com.neuron.academy.ui.screens

import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.QuickTask
import com.neuron.academy.content.QuickTaskGenerator
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.components.ConfettiOverlay
import com.neuron.academy.ui.components.NeuronAvatar
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RoundState { PLAYING, PLAYER_WON, AI_WON, DRAW }

class DuelViewModel(private val c: AppContainer) : ViewModel() {
    val player = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())
    val totalRounds = 5
    var round by mutableIntStateOf(0)
    var playerScore by mutableIntStateOf(0)
    var aiScore by mutableIntStateOf(0)
    var task by mutableStateOf<QuickTask?>(null)
    var roundState by mutableStateOf(RoundState.PLAYING)
    var elapsedMs by mutableLongStateOf(0)
    var playerChoice by mutableStateOf<Int?>(null)
    var aiChoice by mutableStateOf<Int?>(null)
    var aiStatus by mutableStateOf("Нейрон думает…")
    var started by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var realAi by mutableStateOf(false)
    private var aiTimeMs = 8000L
    private var aiAnswer: Int? = null
    private var aiReady = false
    private var ticker: Job? = null
    private var aiJob: Job? = null

    fun start() {
        round = 0; playerScore = 0; aiScore = 0; finished = false; started = true
        nextRound()
    }

    fun nextRound() {
        if (round >= totalRounds) { finishDuel(); return }
        round++
        val t = QuickTaskGenerator.next(hard = true)
        task = t
        roundState = RoundState.PLAYING
        playerChoice = null; aiChoice = null; aiStatus = "Нейрон думает…"; elapsedMs = 0
        aiReady = false
        // ИИ решает с форой: минимальное «время на размышление», чтобы дуэль была честной
        aiTimeMs = Random.nextLong(5000, 12000)
        aiAnswer = if (Random.nextFloat() < 0.8f) t.correct else (t.correct + 1) % t.options.size
        realAi = c.router.hasKeys()
        aiJob?.cancel()
        if (realAi) {
            aiJob = viewModelScope.launch {
                try {
                    val r = c.tutor.solveQuick(t.text, t.options)
                    val n = Regex("""\d""").find(r.text)?.value?.toIntOrNull()
                    if (n != null && n in 1..t.options.size) aiAnswer = n - 1
                    aiTimeMs = maxOf(aiTimeMs, r.latencyMs + 3000)
                } catch (_: Exception) { /* остаётся симуляция */ }
                aiReady = true
            }
        } else aiReady = true
        ticker?.cancel()
        ticker = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (roundState == RoundState.PLAYING) {
                delay(100)
                elapsedMs = System.currentTimeMillis() - start
                if (aiReady && aiChoice == null && elapsedMs >= aiTimeMs) aiAnswered()
            }
        }
    }

    private fun aiAnswered() {
        val t = task ?: return
        val a = aiAnswer ?: t.correct
        aiChoice = a
        if (a == t.correct) {
            aiStatus = "Нейрон ответил верно за ${elapsedMs / 1000.0} с"
            if (playerChoice == null || playerChoice != t.correct) endRound(RoundState.AI_WON)
        } else {
            aiStatus = "Нейрон ошибся, отвечай ты"
            if (playerChoice != null && playerChoice != t.correct) endRound(RoundState.DRAW)
        }
    }

    fun answer(i: Int) {
        val t = task ?: return
        if (playerChoice != null || roundState != RoundState.PLAYING) return
        playerChoice = i
        if (i == t.correct) {
            c.feedback.correct()
            endRound(RoundState.PLAYER_WON)
        } else {
            c.feedback.wrong()
            if (aiChoice != null && aiChoice != t.correct) endRound(RoundState.DRAW)
        }
    }

    private fun endRound(s: RoundState) {
        roundState = s
        when (s) {
            RoundState.PLAYER_WON -> playerScore++
            RoundState.AI_WON -> aiScore++
            else -> {}
        }
        ticker?.cancel(); aiJob?.cancel()
    }

    private fun finishDuel() {
        finished = true
        val won = playerScore > aiScore
        viewModelScope.launch { c.game.onDuel(won) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuelScreen(nav: NavHostController) {
    val vm = containerViewModel { DuelViewModel(it) }
    val player by vm.player.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Дуэль с ИИ") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    com.neuron.academy.ui.components.IconBadge(Icons.Rounded.Person, MaterialTheme.colorScheme.primary, 52.dp); Text(player.name.ifBlank { "Ты" }); Text("${vm.playerScore}", style = MaterialTheme.typography.headlineMedium)
                }
                Text("VS", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    NeuronAvatar(player.skin, 52.dp); Text("Нейрон"); Text("${vm.aiScore}", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                !vm.started -> {
                    Text("5 раундов. Кто первым верно ответит — получает очко. Нейрон думает с небольшой форой, так что у тебя есть шанс" +
                        "\n\nЕсли добавлен API-ключ, задачу по-настоящему решает ИИ.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Начать дуэль") }
                }
                vm.finished -> {
                    val won = vm.playerScore > vm.aiScore
                    Text(if (won) "Ты победил" else if (vm.playerScore == vm.aiScore) "Ничья" else "Нейрон победил", style = MaterialTheme.typography.headlineMedium)
                    Text("${vm.playerScore} : ${vm.aiScore}", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth()) { Text("Реванш") }
                    if (won) ConfettiOverlay()
                }
                else -> {
                    val t = vm.task ?: return@Column
                    Text("Раунд ${vm.round}/${vm.totalRounds} · ⏱️ ${"%.1f".format(vm.elapsedMs / 1000.0)} с", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Text(t.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    t.options.forEachIndexed { i, opt ->
                        val color = when {
                            vm.roundState != RoundState.PLAYING && i == t.correct -> GameColors.Success
                            vm.playerChoice == i && i != t.correct -> GameColors.Error
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        Button(
                            onClick = { vm.answer(i) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        ) {
                            Text(opt, fontSize = 18.sp)
                            if (vm.aiChoice == i) { Spacer(Modifier.width(8.dp)); Text("ИИ", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (vm.roundState == RoundState.PLAYING) {
                        Text(vm.aiStatus)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    } else {
                        Text(
                            when (vm.roundState) {
                                RoundState.PLAYER_WON -> "Раунд твой"
                                RoundState.AI_WON -> "Нейрон ответил быстрее"
                                else -> "Никто не ответил верно"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = vm::nextRound, modifier = Modifier.fillMaxWidth()) { Text(if (vm.round < vm.totalRounds) "Следующий раунд" else "Итоги") }
                    }
                }
            }
        }
    }
}
