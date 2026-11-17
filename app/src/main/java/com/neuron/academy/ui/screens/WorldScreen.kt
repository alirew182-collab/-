package com.neuron.academy.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.data.LearningState
import com.neuron.academy.data.TopicStatus
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.theme.GameColors
import kotlinx.coroutines.launch

private val zigzag = listOf(0f, 0.55f, 0.85f, 0.55f, 0f, -0.55f, -0.85f, -0.55f)

private sealed interface PathNode {
    val grade: Int
    data class Header(override val grade: Int) : PathNode
    data class TopicNode(override val grade: Int, val index: Int, val topicId: String) : PathNode
    data class BossNode(override val grade: Int, val index: Int) : PathNode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(nav: NavHostController, subject: Subject) {
    val c = LocalContainer.current
    val ls by c.progress.learningState.collectAsState(initial = LearningState.EMPTY)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val color = Color(subject.color)

    val nodes = remember(subject) {
        val list = mutableListOf<PathNode>()
        Curriculum.grades.forEach { g ->
            list += PathNode.Header(g)
            Curriculum.topicsFor(subject, g).forEachIndexed { i, t -> list += PathNode.TopicNode(g, i, t.id) }
            list += PathNode.BossNode(g, Curriculum.topicsFor(subject, g).size)
        }
        list
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("${subject.emoji} ${subject.worldName}") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = color.copy(alpha = 0.15f)),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.08f), MaterialTheme.colorScheme.background))),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            nodes.forEachIndexed { idx, node ->
                item(key = "n$idx") {
                    val gs = ls.grade(node.grade)
                    when (node) {
                        is PathNode.Header -> {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (gs.unlocked) color.copy(alpha = 0.2f) else GameColors.Locked.copy(alpha = 0.15f)).padding(14.dp),
                            ) {
                                Text("${regionEmoji[node.grade]} Регион «${regionNames[node.grade]}» · ${node.grade} класс", style = MaterialTheme.typography.titleMedium)
                                val sg = gs.subject(subject)
                                Text(
                                    if (!gs.unlocked) "🔒 ${gs.lockHint}" else "Пройдено ${sg.completedCount} из ${sg.total} тем",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        is PathNode.TopicNode -> {
                            val st = ls.topic(node.topicId) ?: return@item
                            val nextOffset = zigzag[(node.index + 1) % zigzag.size]
                            PathRow(
                                offset = zigzag[node.index % zigzag.size], nextOffset = nextOffset, color = color,
                                done = st.status == TopicStatus.COMPLETED,
                            ) {
                                TopicCircle(
                                    emoji = st.topic.emoji, title = st.topic.title, status = st.status, color = color, perfect = st.perfect,
                                    score = st.bestScore,
                                    onClick = {
                                        if (st.status == TopicStatus.LOCKED) scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("🔒 ${st.lockHint}") }
                                        else nav.navigate(Routes.topic(st.topic.id))
                                    },
                                )
                            }
                        }
                        is PathNode.BossNode -> {
                            val sg = gs.subject(subject)
                            val boss = Curriculum.boss(subject, node.grade)
                            PathRow(offset = zigzag[node.index % zigzag.size], nextOffset = null, color = color, done = sg.examPassed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
                                    Box(
                                        Modifier.size(88.dp).clip(CircleShape)
                                            .background(if (sg.examPassed) GameColors.Success else if (sg.examAvailable) GameColors.Error else GameColors.Locked)
                                            .clickable {
                                                if (sg.examAvailable) nav.navigate(Routes.exam(subject, node.grade))
                                                else scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("🔒 ${sg.examLockHint}") }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (sg.examPassed) "🏆" else boss.emoji, fontSize = 40.sp) }
                                    Text("Босс: ${boss.name}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PathRow(offset: Float, nextOffset: Float?, color: Color, done: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(130.dp).drawBehind {
            if (nextOffset != null) {
                val amp = size.width * 0.28f
                val start = Offset(size.width / 2 + offset * amp, 50.dp.toPx())
                val end = Offset(size.width / 2 + nextOffset * amp, size.height + 50.dp.toPx())
                drawLine(
                    color = if (done) color else Color.Gray.copy(alpha = 0.4f),
                    start = start, end = end, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (done) null else PathEffect.dashPathEffect(floatArrayOf(20f, 20f)),
                )
            }
        },
    ) {
        Box(
            Modifier.fillMaxWidth().offsetFraction(offset),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
    }
}

@Composable
private fun Modifier.offsetFraction(f: Float): Modifier {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return this.offset(x = (w * 0.28f * f).dp)
}

@Composable
private fun TopicCircle(emoji: String, title: String, status: TopicStatus, color: Color, perfect: Boolean, score: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(1f, if (status == TopicStatus.AVAILABLE) 1.08f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp)) {
        Box(
            Modifier.size(78.dp).scale(scale).clip(CircleShape)
                .background(
                    when (status) {
                        TopicStatus.COMPLETED -> color
                        TopicStatus.AVAILABLE -> color.copy(alpha = 0.55f)
                        TopicStatus.LOCKED -> GameColors.Locked.copy(alpha = 0.5f)
                    },
                )
                .border(4.dp, if (perfect) GameColors.Gold else Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (status == TopicStatus.LOCKED) Icon(Icons.Filled.Lock, "Закрыто", tint = Color.White)
            else Text(emoji, fontSize = 32.sp, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.SemiBold)
        if (status == TopicStatus.COMPLETED) {
            Row { Text((if (perfect) "⭐ " else "") + "${(score * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = GameColors.Success) }
        }
    }
}
