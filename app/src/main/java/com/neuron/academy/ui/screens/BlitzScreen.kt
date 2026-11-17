package com.neuron.academy.ui.screens

import com.neuron.academy.ui.components.MascotMood
import com.neuron.academy.ui.components.NeuronMascot
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.neuron.academy.content.Subject
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlitzViewModel(private val c: AppContainer) : ViewModel() {
    val player = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())
    var subject by mutableStateOf<Subject?>(null)
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var timeLeft by mutableIntStateOf(60)
    var score by mutableIntStateOf(0)
    var mistakes by mutableIntStateOf(0)
    var task by mutableStateOf<QuickTask?>(null)
    var flash by mutableStateOf<Boolean?>(null)
    var newRecord by mutableStateOf(false)
    private var timer: Job? = null

    fun start() {
        score = 0; mistakes = 0; timeLeft = 60; finished = false; running = true; newRecord = false
        task = QuickTaskGenerator.next(subject)
        timer?.cancel()
        timer = viewModelScope.launch {
            while (timeLeft > 0) { delay(1000); timeLeft-- }
            finish()
        }
    }

    fun answer(i: Int) {
        val t = task ?: return
        if (!running) return
        if (i == t.correct) { score++; flash = true; c.feedback.correct() } else { mistakes++; flash = false; c.feedback.wrong() }
        task = QuickTaskGenerator.next(subject)
    }

    private fun finish() {
        running = false; finished = true
        newRecord = score > player.value.blitzBest
        val s = score
        viewModelScope.launch {
            c.progress.recordResult("BLITZ", subject ?: Subject.ALGEBRA, c.progress.snapshot().currentGrade, null, s, s + mistakes)
            c.game.onBlitz(s, subject)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlitzScreen(nav: NavHostController) {
    val vm = containerViewModel { BlitzViewModel(it) }
    val player by vm.player.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Блиц") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!vm.running && !vm.finished) {
                NeuronMascot(player.skin, 96.dp)
                Text("60 секунд. Максимум задач. Поехали?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("Рекорд: ${player.blitzBest}", color = GameColors.Gold)
                Spacer(Modifier.height(16.dp))
                Text("Предмет:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(vm.subject == null, { vm.subject = null }, label = { Text("Микс") })
                    Subject.entries.forEach { s -> FilterChip(vm.subject == s, { vm.subject = s }, label = { Text(s.title) }) }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Старт!", fontSize = 20.sp) }
            } else if (vm.running) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vm.timeLeft} с", style = MaterialTheme.typography.titleLarge, color = if (vm.timeLeft <= 10) GameColors.Error else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text("${vm.score}   ❌ ${vm.mistakes}", style = MaterialTheme.typography.titleLarge)
                }
                LinearProgressIndicator(progress = { vm.timeLeft / 60f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Spacer(Modifier.height(24.dp))
                val t = vm.task
                if (t != null) {
                    AnimatedContent(t, label = "task") { task ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(task.subject.title.uppercase(), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(task.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            task.options.chunked(2).forEachIndexed { row, pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                                    pair.forEachIndexed { col, opt ->
                                        val idx = row * 2 + col
                                        Button(
                                            onClick = { vm.answer(idx) },
                                            modifier = Modifier.weight(1f).height(64.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                        ) { Text(opt, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                                    }
                                }
                            }
                        }
                    }
                }
                vm.flash?.let { Text(if (it) "+1" else "❌", color = if (it) GameColors.Success else GameColors.Error, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp)) }
            } else {
                NeuronMascot(player.skin, 96.dp, if (vm.newRecord) MascotMood.HAPPY else MascotMood.NORMAL)
                Text("Время вышло!", style = MaterialTheme.typography.headlineMedium)
                Text("Решено: ${vm.score} · Ошибок: ${vm.mistakes}", style = MaterialTheme.typography.titleMedium)
                if (vm.newRecord) Text("Новый рекорд!", color = GameColors.Gold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ещё раз") }
            }
        }
    }
}

