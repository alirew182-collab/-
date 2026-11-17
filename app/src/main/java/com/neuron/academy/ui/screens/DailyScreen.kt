package com.neuron.academy.ui.screens

import com.neuron.academy.ui.LocalContainer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.AnswerChecker
import com.neuron.academy.content.OfflineDailyProblems
import com.neuron.academy.content.Subject
import com.neuron.academy.data.GameRepository
import com.neuron.academy.data.QuestState
import com.neuron.academy.data.db.DailyProblemEntity
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.MathText
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.CrystalRules
import com.neuron.gamification.ShopCatalog
import com.neuron.gamification.XpRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DailyViewModel(private val c: AppContainer) : ViewModel() {
    val quests: StateFlow<List<QuestState>> = c.game.quests.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val player: StateFlow<PlayerEntity> = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())
    val problem: StateFlow<DailyProblemEntity?> = c.game.dailyProblem().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var loading by mutableStateOf(false)
    var answer by mutableStateOf("")
    var wrong by mutableStateOf(false)
    var showHint by mutableStateOf(false)
    var showSolution by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun ensureProblem() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val day = c.game.today()
            if (c.db.playerDao().dailyProblem(day) == null) {
                val subject = Subject.entries[Math.floorMod(day, 3L).toInt()]
                val entity = try {
                    if (!c.router.isAvailable()) throw IllegalStateException()
                    val ls = c.progress.snapshot()
                    val g = c.tutor.dailyProblem(subject, ls.currentGrade, ls.completedTopics.map { it.title })
                    if (g.problem.isBlank() || g.answer.isBlank()) throw IllegalStateException()
                    DailyProblemEntity(day, subject.key, g.problem, g.answer, g.hint, g.solution)
                } catch (e: Exception) {
                    val p = OfflineDailyProblems.forDay(day)
                    DailyProblemEntity(day, p.subject.key, p.problem, p.answer, p.hint, p.solution)
                }
                c.game.saveDailyProblem(entity)
            }
            loading = false
        }
    }

    fun check() {
        val p = problem.value ?: return
        if (AnswerChecker.matches(answer, p.answer)) {
            wrong = false
            c.feedback.correct()
            viewModelScope.launch { c.game.onDailyProblemSolved(Subject.byKey(p.subject)) }
        } else {
            wrong = true
            c.feedback.wrong()
        }
    }

    fun claim(id: String) = viewModelScope.launch { c.game.claimQuest(id) }

    fun buyFreeze() = viewModelScope.launch {
        message = when (c.game.buy(ShopCatalog.STREAK_FREEZE)) {
            GameRepository.PurchaseResult.Ok -> "Заморозка куплена!"
            GameRepository.PurchaseResult.NotEnough -> "Не хватает кристаллов 💎"
            else -> null
        }
    }
}

@Composable
fun DailyScreen(nav: NavHostController) {
    val vm = containerViewModel { DailyViewModel(it) }
    val quests by vm.quests.collectAsState()
    val player by vm.player.collectAsState()
    val problem by vm.problem.collectAsState()
    LaunchedEffect(Unit) { vm.ensureProblem() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { com.neuron.academy.ui.components.ScreenTitle("Сегодня", "Серия, задания и задача дня") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = GameColors.Fire.copy(alpha = 0.15f))) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = GameColors.Fire, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${player.streak} дн. подряд", style = MaterialTheme.typography.headlineSmall)
                        Text("Рекорд: ${player.bestStreak} · Заморозок: 🧊 ${player.freezes}", style = MaterialTheme.typography.bodyMedium)
                        Text("Заморозка спасёт серию, если пропустишь день.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.buyFreeze() }) { Text("Купить 🧊 за ${CrystalRules.STREAK_FREEZE_PRICE} 💎") }
                    Spacer(Modifier.width(8.dp))
                    vm.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            com.neuron.academy.ui.components.Panel(onClick = { nav.navigate(com.neuron.academy.ui.Routes.REVIEW) }) {
                com.neuron.academy.ui.components.Eyebrow("Повторение")
                Text("Освежи пройденное — +1 сердце за хороший результат", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Сердца: ${LocalContainer.current.game.heartsOf(player)} из ${com.neuron.gamification.Hearts.MAX}. Темы, пройденные больше недели назад, просят повторения.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Text("Задания на сегодня", style = MaterialTheme.typography.titleLarge) }
        items(quests, key = { it.def.id }) { q ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (q.claimed) Icons.Rounded.CheckCircle else if (q.done) Icons.Rounded.Redeem else Icons.Rounded.RadioButtonUnchecked, null, tint = if (q.claimed) GameColors.Success else if (q.done) GameColors.Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(q.def.title, style = MaterialTheme.typography.titleMedium)
                            Text("Награда: +${q.def.rewardXp} XP, +${q.def.rewardCrystals} 💎", style = MaterialTheme.typography.bodySmall)
                        }
                        if (q.done && !q.claimed) Button(onClick = { vm.claim(q.def.id) }) { Text("Забрать") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { q.progress.toFloat() / q.def.target }, modifier = Modifier.fillMaxWidth())
                    Text("${q.progress} / ${q.def.target}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            InfoCard("Задача дня  (+${XpRules.DAILY_PROBLEM} XP)", color = GameColors.Gold) {
                val p = problem
                when {
                    p == null -> LoadingBlock("Нейрон выбирает задачу…")
                    else -> {
                        com.neuron.academy.ui.components.Eyebrow(Subject.byKey(p.subject).title)
                        Spacer(Modifier.height(4.dp))
                        MathText(p.problem)
                        Spacer(Modifier.height(8.dp))
                        if (p.solved) {
                            Text("Решено! Возвращайся завтра за новой задачей.", color = GameColors.Success, fontWeight = FontWeight.Bold)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(vm.answer, { vm.answer = it; vm.wrong = false }, label = { Text("Ответ") }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = vm::check) { Text("✓") }
                            }
                            if (vm.wrong) Text("Пока не то. Подсказка поможет! 💡", color = GameColors.Error)
                        }
                        Row {
                            TextButton(onClick = { vm.showHint = !vm.showHint }) { Text("Подсказка") }
                            TextButton(onClick = { vm.showSolution = !vm.showSolution }) { Text("Решение") }
                        }
                        if (vm.showHint) MathText(p.hint, style = MaterialTheme.typography.bodyMedium)
                        if (vm.showSolution) MathText(p.solution + "\n\nОтвет: **${p.answer}**", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
