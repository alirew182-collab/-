package com.neuron.academy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.data.GradeState
import com.neuron.academy.data.LearningState
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.components.PlayerStatsBar
import com.neuron.academy.ui.components.ProgressRing
import com.neuron.academy.ui.navigateTab
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.NeuronLines
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(c: AppContainer) : ViewModel() {
    val learning: StateFlow<LearningState> = c.progress.learningState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningState.EMPTY)
    val player: StateFlow<PlayerEntity> = c.game.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerEntity())
    val greeting = NeuronLines.random(NeuronLines.greetings)
}

val regionNames = mapOf(9 to "Долина Начинающих", 10 to "Горы Открытий", 11 to "Космодром Знаний")
val regionEmoji = mapOf(9 to "🏕️", 10 to "⛰️", 11 to "🛸")

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = containerViewModel { HomeViewModel(it) }
    val ls by vm.learning.collectAsState()
    val player by vm.player.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun hint(text: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("🔒 $text") }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Привет, ${player.name.ifBlank { "друг" }}!", style = MaterialTheme.typography.headlineSmall)
                        Text("Сейчас ты в ${ls.currentGrade} классе" + if (ls.teacherMode) " · 👩‍🏫 режим учителя" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Настройки") }
                }
            }
            item { PlayerStatsBar(player) }
            item { NeuronBubble(vm.greeting, player.skin) }
            item { OverallProgress(ls) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("🧩", "Задача дня", Modifier.weight(1f)) { nav.navigateTab(Routes.DAILY) }
                    QuickAction("⚡", "Блиц", Modifier.weight(1f)) { nav.navigate(Routes.BLITZ) }
                    QuickAction("💬", "Спросить", Modifier.weight(1f)) { nav.navigateTab(Routes.CHAT) }
                }
            }
            ls.grades.forEach { g ->
                item(key = "region_${g.grade}") {
                    RegionCard(
                        g,
                        onSubject = { s -> if (g.unlocked) nav.navigate(Routes.world(s)) else hint(g.lockHint ?: "") },
                        onBoss = { s ->
                            val sg = g.subject(s)
                            if (sg.examAvailable) nav.navigate(Routes.exam(s, g.grade)) else hint(sg.examLockHint ?: "")
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun QuickAction(emoji: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OverallProgress(ls: LearningState) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Text("Путь 9 → 10 → 11", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ls.grades.forEachIndexed { i, g ->
                    val color = when {
                        g.completed -> GameColors.Success
                        g.unlocked -> MaterialTheme.colorScheme.primary
                        else -> GameColors.Locked
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Text(if (g.completed) "✓" else "${g.grade}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (i < ls.grades.lastIndex) {
                        LinearProgressIndicator(
                            progress = { g.fraction },
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = color, trackColor = color.copy(alpha = 0.2f), strokeCap = StrokeCap.Round,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Общий прогресс: ${(ls.overallFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RegionCard(g: GradeState, onSubject: (Subject) -> Unit, onBoss: (Subject) -> Unit) {
    val locked = !g.unlocked
    val gradient = when (g.grade) {
        9 -> listOf(Color(0xFF43A047), Color(0xFF26A69A))
        10 -> listOf(Color(0xFF5E35B1), Color(0xFF8E24AA))
        else -> listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column {
            Box(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(gradient)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(regionEmoji[g.grade] ?: "", fontSize = 34.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${g.grade} класс", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(regionNames[g.grade] ?: "", color = Color.White.copy(alpha = 0.85f))
                    }
                    when {
                        g.completed -> Text("🏆", fontSize = 28.sp)
                        locked -> Icon(Icons.Filled.Lock, "Закрыто", tint = Color.White)
                        else -> Text("${(g.fraction * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
            if (locked) {
                Text("🔒 ${g.lockHint}", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp).alpha(if (locked) 0.45f else 1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                g.subjects.forEach { sg ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSubject(sg.subject) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressRing(sg.fraction, Color(sg.subject.color), 64.dp) { Text(sg.subject.emoji, fontSize = 26.sp) }
                        Spacer(Modifier.height(4.dp))
                        Text(sg.subject.worldName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text("${sg.completedCount}/${sg.total} тем", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.subjects.forEach { sg ->
                    val boss = Curriculum.boss(sg.subject, g.grade)
                    val bg = when {
                        sg.examPassed -> GameColors.Success.copy(alpha = 0.18f)
                        sg.examAvailable -> GameColors.Error.copy(alpha = 0.15f)
                        else -> GameColors.Locked.copy(alpha = 0.15f)
                    }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onBoss(sg.subject) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (sg.examPassed) "✅" else if (sg.examAvailable) boss.emoji else "🔒", fontSize = 18.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("Босс", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
