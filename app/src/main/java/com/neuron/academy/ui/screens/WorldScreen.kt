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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.data.LearningState
import com.neuron.academy.data.TopicStatus
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.IconBadge
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.ThinProgress
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.theme.accent
import com.neuron.academy.ui.theme.icon
import kotlinx.coroutines.launch

private val zigzag = listOf(0f, 0.55f, 0.85f, 0.55f, 0f, -0.55f, -0.85f, -0.55f)

private sealed interface PathNode {
    val grade: Int
    data class Header(override val grade: Int) : PathNode
    data class TopicNode(override val grade: Int, val index: Int, val topicId: String) : PathNode
    data class BossNode(override val grade: Int, val index: Int) : PathNode
    data class GuardianNode(override val grade: Int, val index: Int, val stage: Int) : PathNode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(nav: NavHostController, subject: Subject) {
    val c = LocalContainer.current
    val ls by c.progress.learningState.collectAsState(initial = LearningState.EMPTY)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val color = subject.accent
    fun hint(t: String?) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(t ?: "") }

    val nodes = remember(subject) {
        val list = mutableListOf<PathNode>()
        Curriculum.grades.forEach { g ->
            list += PathNode.Header(g)
            var pos = 0
            val topics = Curriculum.topicsFor(subject, g)
            val guardians = Curriculum.guardianCount(subject, g)
            topics.forEachIndexed { i, t ->
                list += PathNode.TopicNode(g, pos++, t.id)
                val stage = (i + 1) / Curriculum.BLOCK
                if ((i + 1) % Curriculum.BLOCK == 0 && stage in 1..guardians) list += PathNode.GuardianNode(g, pos++, stage)
            }
            list += PathNode.BossNode(g, pos)
        }
        list
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(subject.icon, color, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(subject.worldName, style = MaterialTheme.typography.titleLarge)
                            Text(subject.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = 12.dp)) {
            nodes.forEachIndexed { idx, node ->
                item(key = "n$idx") {
                    val gs = ls.grade(node.grade)
                    when (node) {
                        is PathNode.Header -> {
                            val sg = gs.subject(subject)
                            Panel(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${node.grade}", style = MaterialTheme.typography.displaySmall, color = if (gs.unlocked) color else MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Eyebrow("${node.grade} класс")
                                        Text(regionNames[node.grade] ?: "", style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (!gs.unlocked) Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(10.dp))
                                if (!gs.unlocked) Text(gs.lockHint ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else {
                                    ThinProgress(sg.fraction, color)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Пройдено ${sg.completedCount} из ${sg.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        is PathNode.TopicNode -> {
                            val st = ls.topic(node.topicId) ?: return@item
                            PathRow(zigzag[node.index % zigzag.size], zigzag[(node.index + 1) % zigzag.size], color, st.status == TopicStatus.COMPLETED) {
                                TopicNodeView(st.topic.order, st.topic.title + if (st.needsReview) " · повторить" else "", st.status, color, st.perfect, st.bestScore) {
                                    if (st.status == TopicStatus.LOCKED) hint(st.lockHint) else nav.navigate(Routes.topic(st.topic.id))
                                }
                            }
                        }
                        is PathNode.GuardianNode -> {
                            val gst = gs.subject(subject).guardian(node.stage)
                            val guard = Curriculum.boss(subject, node.grade, node.stage)
                            PathRow(zigzag[node.index % zigzag.size], zigzag[(node.index + 1) % zigzag.size], color, gst.passed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                                    Box(
                                        Modifier.size(70.dp).clip(RoundedCornerShape(20.dp))
                                            .background(
                                                when {
                                                    gst.passed -> GameColors.Success
                                                    gst.available -> MaterialTheme.colorScheme.secondaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                },
                                            )
                                            .clickable { if (gst.available) nav.navigate(Routes.exam(subject, node.grade, node.stage)) else hint(gst.lockHint) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        when {
                                            gst.passed -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                            gst.available -> Text(guard.emoji, style = MaterialTheme.typography.headlineSmall)
                                            else -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Eyebrow("Страж раздела ${node.stage}")
                                    Text(guard.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        is PathNode.BossNode -> {
                            val sg = gs.subject(subject)
                            val boss = Curriculum.boss(subject, node.grade)
                            PathRow(zigzag[node.index % zigzag.size], null, color, sg.examPassed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                                    val bg = when {
                                        sg.examPassed -> GameColors.Success
                                        sg.examAvailable -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                    Box(
                                        Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(bg)
                                            .clickable { if (sg.examAvailable) nav.navigate(Routes.exam(subject, node.grade)) else hint(sg.examLockHint) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        when {
                                            sg.examPassed -> Icon(Icons.Rounded.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(38.dp))
                                            sg.examAvailable -> Text(boss.emoji, style = MaterialTheme.typography.headlineMedium)
                                            else -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Eyebrow("Итоговый экзамен")
                                    Text(boss.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
    val muted = MaterialTheme.colorScheme.outline
    val w = LocalConfiguration.current.screenWidthDp
    Box(
        Modifier.fillMaxWidth().height(128.dp).drawBehind {
            if (nextOffset != null) {
                val amp = size.width * 0.26f
                val start = Offset(size.width / 2 + offset * amp, 36.dp.toPx())
                val end = Offset(size.width / 2 + nextOffset * amp, size.height + 36.dp.toPx())
                drawLine(
                    color = if (done) color.copy(alpha = 0.55f) else muted,
                    start = start, end = end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (done) null else PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().offset(x = (w * 0.26f * offset).dp), contentAlignment = Alignment.TopCenter) { content() }
    }
}

@Composable
private fun TopicNodeView(number: Int, title: String, status: TopicStatus, color: Color, perfect: Boolean, score: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(1f, if (status == TopicStatus.AVAILABLE) 1.06f else 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        val base = Modifier.size(72.dp).scale(scale).clip(CircleShape)
        val styled = when (status) {
            TopicStatus.COMPLETED -> base.background(color)
            TopicStatus.AVAILABLE -> base.background(MaterialTheme.colorScheme.surfaceContainer).border(4.dp, color, CircleShape)
            TopicStatus.LOCKED -> base.background(MaterialTheme.colorScheme.surfaceVariant)
        }
        Box(styled.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            when (status) {
                TopicStatus.COMPLETED -> Icon(if (perfect) Icons.Rounded.Star else Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                TopicStatus.AVAILABLE -> Text("$number", style = MaterialTheme.typography.headlineSmall, color = color)
                TopicStatus.LOCKED -> Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
            color = if (status == TopicStatus.LOCKED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (status == TopicStatus.COMPLETED) Text("${(score * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
