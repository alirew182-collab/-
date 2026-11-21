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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.data.GradeState
import com.neuron.academy.data.LearningState
import com.neuron.academy.data.TopicState
import com.neuron.academy.data.TopicStatus
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.IconBadge
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.PlayerStatsBar
import com.neuron.academy.ui.components.ThinProgress
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.navigateTab
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.theme.accent
import com.neuron.academy.ui.theme.icon
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

/** Следующая доступная тема — «продолжить с того места, где остановился». */
fun LearningState.nextTopic(): TopicState? {
    val g = grade(currentGrade)
    return Subject.entries.flatMap { g.subject(it).topics }.firstOrNull { it.status == TopicStatus.AVAILABLE }
        ?: grades.filter { it.unlocked }.flatMap { gr -> gr.subjects.flatMap { it.topics } }.firstOrNull { it.status == TopicStatus.AVAILABLE }
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = containerViewModel { HomeViewModel(it) }
    val ls by vm.learning.collectAsState()
    val player by vm.player.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun hint(text: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        // заголовок называет, где ты сейчас; приветствие говорит Нейрон ниже
                        Eyebrow("${ls.currentGrade} класс" + if (ls.teacherMode) " · режим учителя" else "")
                        Spacer(Modifier.height(4.dp))
                        Text(regionNames[ls.currentGrade] ?: "${ls.currentGrade} класс", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Настройки") }
                }
            }
            item { PlayerStatsBar(player) }
            item { NeuronBubble(vm.greeting, player.skin) }
            item { InstallAiCard { nav.navigate(Routes.KEYS) } }
            item { ContinueCard(ls) { nav.navigate(Routes.topic(it)) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Rounded.Lightbulb, "Задача дня", GameColors.Gold, Modifier.weight(1f)) { nav.navigateTab(Routes.DAILY) }
                    QuickAction(Icons.Rounded.Timer, "Блиц", GameColors.Fire, Modifier.weight(1f)) { nav.navigate(Routes.BLITZ) }
                    QuickAction(Icons.Rounded.ChatBubbleOutline, "Спросить", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.navigateTab(Routes.CHAT) }
                }
            }
            item { OverallProgress(ls) }
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

/** Подсказка установить встроенный ИИ, если ИИ пока недоступен. */
@Composable
private fun InstallAiCard(onOpen: () -> Unit) {
    val c = com.neuron.academy.ui.LocalContainer.current
    val statuses by c.localLlm.status.collectAsState()
    val downloading = statuses.values.any { it is com.neuron.academy.ai.local.ModelStatus.Downloading }
    if (c.router.isAvailable()) return
    Panel(onClick = onOpen) {
        Eyebrow(if (downloading) "Встроенный ИИ скачивается" else "Встроенный ИИ")
        Spacer(Modifier.height(4.dp))
        Text(
            if (downloading) "Можно заниматься дальше — загрузка идёт в фоне" else "Установи Нейрона на телефон: без ключей и без интернета",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text("Один раз скачать ${c.localLlm.recommended.sizeLabel} — и чат, «Объясни иначе» и генерация задач заработают.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContinueCard(ls: LearningState, onOpen: (String) -> Unit) {
    val next = ls.nextTopic() ?: return
    val t = next.topic
    val color = t.subject.accent
    val total = Curriculum.topicsFor(t.subject, t.grade).size
    Panel(color = MaterialTheme.colorScheme.primaryContainer, border = Color.Transparent, onClick = { onOpen(t.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Продолжить · ${t.subject.title}", color = color)
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("Тема ${t.order} из $total · ${t.grade} класс", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onOpen(t.id) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Открыть")
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, color, 38.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OverallProgress(ls: LearningState) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Путь 9 → 10 → 11", modifier = Modifier.weight(1f))
            Text("${(ls.overallFraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ls.grades.forEachIndexed { i, g ->
                val color = when {
                    g.completed -> GameColors.Success
                    g.unlocked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (g.unlocked) color else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        g.completed -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        g.unlocked -> Text("${g.grade}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        else -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                if (i < ls.grades.lastIndex) ThinProgress(g.fraction, color, Modifier.weight(1f).padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun RegionCard(g: GradeState, onSubject: (Subject) -> Unit, onBoss: (Subject) -> Unit) {
    val locked = !g.unlocked
    Panel(padding = PaddingValues(0.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${g.grade}",
                style = MaterialTheme.typography.displaySmall,
                color = if (locked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(if (g.completed) "Пройден" else if (locked) "Закрыт" else "Класс · ${(g.fraction * 100).toInt()}%")
                Text(regionNames[g.grade] ?: "", style = MaterialTheme.typography.titleMedium)
            }
            if (locked) Icon(Icons.Rounded.Lock, "Закрыто", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (g.completed) Icon(Icons.Rounded.Check, "Пройдено", tint = GameColors.Success)
        }
        if (locked) {
            Text(
                g.lockHint ?: "", modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).alpha(if (locked) 0.5f else 1f)) {
            g.subjects.forEach { sg ->
                val color = sg.subject.accent
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onSubject(sg.subject) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(sg.subject.icon, color, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sg.subject.worldName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${sg.completedCount}/${sg.total}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        ThinProgress(sg.fraction, color, height = 5.dp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            g.subjects.forEach { sg ->
                val state = when {
                    sg.examPassed -> "сдан"
                    sg.examAvailable -> "в бой"
                    else -> "закрыт"
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onBoss(sg.subject) }.padding(6.dp),
                ) {
                    Text("Стражи ${sg.guardians.count { it.passed }}/${sg.guardians.size} · экзамен", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        "${sg.subject.title} · $state",
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            sg.examPassed -> GameColors.Success
                            sg.examAvailable -> sg.subject.accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
