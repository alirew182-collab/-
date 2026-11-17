package com.neuron.academy.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Subject
import com.neuron.academy.data.LearningState
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.data.db.TestResultEntity
import com.neuron.academy.data.db.XpEventEntity
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.components.PlayerStatsBar
import com.neuron.academy.ui.components.ProgressRing
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class ProfileViewModel(c: AppContainer) : ViewModel() {
    val player: StateFlow<PlayerEntity> = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())
    val learning: StateFlow<LearningState> = c.progress.learningState.stateIn(viewModelScope, SharingStarted.Eagerly, LearningState.EMPTY)
    val xp30: StateFlow<List<XpEventEntity>> = c.game.xpSince(30).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val results: StateFlow<List<TestResultEntity>> = c.progress.allResults.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val today: Long = c.game.today()
}

/** Персональные итоги: сравнение средней точности по предмету за эту и прошлую неделю. */
fun insights(results: List<TestResultEntity>, now: Long = System.currentTimeMillis()): List<String> {
    val week = 7L * 24 * 3600 * 1000
    val out = mutableListOf<String>()
    Subject.entries.forEach { s ->
        val sr = results.filter { it.subject == s.key && it.total > 0 && it.kind != "BLITZ" }
        val thisWeek = sr.filter { it.timestamp >= now - week }
        val lastWeek = sr.filter { it.timestamp in (now - 2 * week) until (now - week) }
        fun acc(l: List<TestResultEntity>) = l.sumOf { it.correct }.toFloat() / l.sumOf { it.total }.coerceAtLeast(1)
        val name = when (s) { Subject.ALGEBRA -> "алгебре"; Subject.PHYSICS -> "физике"; Subject.CHEMISTRY -> "химии" }
        if (thisWeek.isNotEmpty() && lastWeek.isNotEmpty()) {
            val diff = ((acc(thisWeek) - acc(lastWeek)) * 100).roundToInt()
            when {
                diff > 0 -> out += "📈 Ты стал лучше в $name на $diff% за неделю!"
                diff < 0 -> out += "🔁 В $name точность снизилась на ${-diff}% — повтори теорию, и всё вернётся."
                else -> out += "➖ В $name стабильно: ${(acc(thisWeek) * 100).roundToInt()}% верных ответов."
            }
        } else if (thisWeek.isNotEmpty()) {
            out += "✨ На этой неделе в $name: ${(acc(thisWeek) * 100).roundToInt()}% верных ответов."
        }
    }
    if (out.isEmpty()) out += "Пройди пару тестов — и я покажу, как ты растёшь 📊"
    return out
}

@Composable
fun ProfileScreen(nav: NavHostController) {
    val vm = containerViewModel { ProfileViewModel(it) }
    val player by vm.player.collectAsState()
    val ls by vm.learning.collectAsState()
    val xp by vm.xp30.collectAsState()
    val results by vm.results.collectAsState()
    var period by remember { mutableIntStateOf(7) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Профиль и прогресс", style = MaterialTheme.typography.headlineSmall) }
        item { PlayerStatsBar(player) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileLink("🏅", "Награды", Modifier.weight(1f)) { nav.navigate(Routes.COLLECTION) }
                ProfileLink("🛍️", "Магазин", Modifier.weight(1f)) { nav.navigate(Routes.SHOP) }
                ProfileLink("⚙️", "Настройки", Modifier.weight(1f)) { nav.navigate(Routes.SETTINGS) }
            }
        }
        item { OverallProgress(ls) }
        item {
            InfoCard("Прогресс по предметам", "📚") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Subject.entries.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val f = ls.subjectFraction(s)
                            ProgressRing(f, Color(s.color), 72.dp) { Text("${(f * 100).roundToInt()}%", fontWeight = FontWeight.Bold) }
                            Text("${s.emoji} ${s.title}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        item {
            InfoCard("Серия дней", "🔥", color = GameColors.Fire) {
                Text("Сейчас: ${player.streak} дн. · Рекорд: ${player.bestStreak} дн. · Заморозок: ${player.freezes}")
            }
        }
        item {
            InfoCard("Опыт по дням", "📊", color = GameColors.Xp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(period == 7, { period = 7 }, label = { Text("Неделя") })
                    FilterChip(period == 30, { period = 30 }, label = { Text("Месяц") })
                }
                Spacer(Modifier.height(8.dp))
                val days = (0 until period).map { vm.today - period + 1 + it }
                val values = days.map { d -> xp.filter { it.day == d }.sumOf { it.amount } }
                BarChart(values, days.map { d -> labelFor(d, period) }, GameColors.Xp)
                Text("Всего за период: ${values.sum()} XP", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            InfoCard("Точность ответов", "🎯", color = GameColors.Success) {
                val days = (0 until period).map { vm.today - period + 1 + it }
                Subject.entries.forEach { s ->
                    val series = days.map { d ->
                        val dayRes = results.filter { it.subject == s.key && it.total > 0 && epochDay(it.timestamp) == d }
                        if (dayRes.isEmpty()) null else dayRes.sumOf { it.correct }.toFloat() / dayRes.sumOf { it.total }
                    }
                    Text("${s.emoji} ${s.title}", style = MaterialTheme.typography.labelLarge)
                    LineChart(series, Color(s.color))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item {
            InfoCard("Персональные итоги", "🧠") {
                insights(results).forEach { Text(it, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
        item {
            InfoCard("Статистика", "📋") {
                val tests = results.count { it.kind == "TOPIC" }
                StatLine("Пройдено тем", "${ls.completedTopicIds.size}")
                StatLine("Тестов сдано", "$tests")
                StatLine("Тестов на 100%", "${player.perfectTests}")
                StatLine("Боссов побеждено", "${player.bossesDefeated} / 9")
                StatLine("Сложных задач решено", "${player.hardTasksSolved}")
                StatLine("Рекорд блица", "${player.blitzBest}")
                StatLine("Побед в дуэлях", "${player.duelWins}")
                StatLine("Вопросов Нейрону", "${player.chatMessages}")
            }
        }
        item { NeuronBubble("Помни: важен не только результат, но и каждый маленький шаг. Я горжусь тобой! 🤖💙", player.skin) }
    }
}

private fun epochDay(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

private fun labelFor(day: Long, period: Int): String {
    val d = LocalDate.ofEpochDay(day)
    return if (period <= 7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")) else if (d.dayOfMonth % 5 == 0) "${d.dayOfMonth}" else ""
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileLink(emoji: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BarChart(values: List<Int>, labels: List<String>, color: Color) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(10)
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = values.size
            val slot = size.width / n
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                drawRoundRect(color.copy(alpha = 0.15f), Offset(i * slot + (slot - barW) / 2, 0f), Size(barW, size.height), CornerRadius(8f, 8f))
                drawRoundRect(color, Offset(i * slot + (slot - barW) / 2, size.height - h), Size(barW, h), CornerRadius(8f, 8f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l -> Text(l, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = onSurface, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
fun LineChart(values: List<Float?>, color: Color) {
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    if (values.all { it == null }) {
        Text("Пока нет данных", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        listOf(0f, 0.5f, 1f).forEach { y -> drawLine(grid, Offset(0f, size.height * (1 - y)), Offset(size.width, size.height * (1 - y))) }
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val p = Offset(i * step, size.height * (1 - v))
            if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            drawCircle(color, 6f, p)
        }
        drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
    }
}
