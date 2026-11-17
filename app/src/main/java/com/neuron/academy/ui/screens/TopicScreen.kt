package com.neuron.academy.ui.screens

import com.neuron.academy.ui.theme.accent
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neuron.academy.ai.Difficulty
import com.neuron.academy.ai.ExplainStyle
import com.neuron.academy.data.TopicStatus
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.EmptyHint
import com.neuron.academy.ui.components.ErrorBlock
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.MathText
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.NeuronLines

private val tabTitles = listOf("Теория", "Видео", "Примеры", "Практика", "Контрольная", "Факт")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(nav: NavHostController, topicId: String) {
    val vm = containerViewModel(key = "topic_$topicId") { TopicViewModel(it, topicId) }
    val topic = vm.topic
    if (topic == null) { EmptyHint("Тема не найдена"); return }
    val ls by vm.learning.collectAsState()
    val state = ls.topic(topicId)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val color = topic.subject.accent
    LaunchedEffect(Unit) { vm.onTheoryOpened() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(topic.title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                            Text("${topic.subject.title} · ${topic.grade} класс" + if (state?.status == TopicStatus.COMPLETED) " · ✅ пройдена" else "", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabTitles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                0 -> theoryTab(vm, nav, color)
                1 -> videoTab(vm)
                2 -> examplesTab(vm)
                3 -> practiceTab(vm, nav)
                4 -> testTab(vm, nav)
                else -> factTab(vm, nav)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.theoryTab(vm: TopicViewModel, nav: NavHostController, color: Color) {
    val topic = vm.topic!!
    if (vm.prereqs.isNotEmpty()) item {
        InfoCard("Что вспомнить из прошлых тем", color = color) {
            vm.prereqs.forEach { p ->
                Text("• ${p.title} (${p.grade} кл.)", modifier = Modifier.clickable { nav.navigate(Routes.topic(p.id)) }.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            AiSection(vm.prereq, nav, buttonText = "Нейрон, напомни главное", onClick = vm::askPrereq)
        }
    }
    item {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MathText(topic.theory, Modifier.padding(16.dp))
        }
    }
    item {
        InfoCard("Объясни иначе", color = color) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExplainStyle.entries.forEach { s ->
                    AssistChip(onClick = { vm.explainAs(s) }, label = { Text(s.title) }, border = if (vm.explainStyle == s) BorderStroke(2.dp, color) else null)
                }
            }
            AiResult(vm.explain, nav)
        }
    }
    item {
        InfoCard("Зачем это нужно в жизни?", color = color) {
            Text(topic.realLife, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            AiSection(vm.why, nav, buttonText = "Расскажи подробнее", onClick = vm::askWhy)
        }
    }
    item { VideoLessonCard(topic) }
    item {
        FilledTonalButton(onClick = { nav.navigate(Routes.topicChat(topic.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Задать вопрос Нейрону по теме") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.examplesTab(vm: TopicViewModel) {
    val topic = vm.topic!!
    itemsIndexed(topic.examples) { i, ex ->
        val shown = remember { mutableStateMapOf<Int, Boolean>() }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp)) {
                Text("Пример ${i + 1}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                MathText(ex.problem)
                Spacer(Modifier.height(8.dp))
                if (shown[i] == true) {
                    Text("Решение:", fontWeight = FontWeight.Bold, color = GameColors.Success)
                    MathText(ex.solution)
                } else OutlinedButton(onClick = { shown[i] = true }) { Text("Показать решение") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.practiceTab(vm: TopicViewModel, nav: NavHostController) {
    item {
        InfoCard("Задача от ИИ") {
            Text("Нейрон придумает задачу с сюжетом из игр, спорта или космоса.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    OutlinedButton(onClick = { vm.generateTask(d) }, enabled = !vm.generate.loading, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(d.title) }
                }
            }
            if (vm.generate.loading) LoadingBlock("Придумываю задачу…")
            vm.generate.error?.let { ErrorBlock(it, if (vm.generate.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) }
        }
    }
    itemsIndexed(vm.practice, key = { i, it -> "p${it.hashCode()}_$i" }) { i, item ->
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Задача ${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (item.aiGenerated) Text("ИИ ", style = MaterialTheme.typography.labelSmall)
                    if (item.task.hard) Text("Сложная", style = MaterialTheme.typography.labelSmall, color = GameColors.Fire)
                }
                Spacer(Modifier.height(6.dp))
                MathText(item.task.problem)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        item.input, { item.input = it; item.result = null },
                        label = { Text("Ответ") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.checkPractice(item) }) { Text("✓") }
                }
                item.result?.let { ok ->
                    Text(
                        if (ok) "${NeuronLines.random(NeuronLines.correct)}" else "${NeuronLines.random(NeuronLines.wrong)}",
                        color = if (ok) GameColors.Success else GameColors.Error, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row {
                    TextButton(onClick = { item.showHint = !item.showHint }) { Text("Подсказка") }
                    TextButton(onClick = { item.showSolution = !item.showSolution }) { Text("Решение") }
                }
                AnimatedVisibility(item.showHint) { MathText("Подсказка: " + item.task.hint, style = MaterialTheme.typography.bodyMedium) }
                AnimatedVisibility(item.showSolution) { MathText("Решение: ${item.task.solution}\n\nОтвет: **${item.task.answer}**", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    item.fullSolution, { item.fullSolution = it },
                    label = { Text("Или напиши решение целиком — ИИ проверит") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                TextButton(onClick = { vm.checkFullSolution(item) }, enabled = item.fullSolution.isNotBlank() && !item.check.loading) { Text("Проверить решение с разбором") }
                AiResult(item.check, nav)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.videoTab(vm: TopicViewModel) {
    val topic = vm.topic!!
    item { VideoLessonCard(topic) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.testTab(vm: TopicViewModel, nav: NavHostController) {
    if (vm.control.isEmpty()) { item { EmptyHint("Заданий нет") }; return }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                com.neuron.academy.ui.components.Eyebrow(if (vm.aiTestLabel) "Контрольная от ИИ" else "Контрольная · средний уровень")
                Text("${vm.control.size} заданий, нужно 70%", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (!vm.started && !vm.finished) {
        item {
            com.neuron.academy.ui.components.Panel {
                Text("В контрольной: вопросы по теме урока, задачи с открытым ответом и вопросы из прошлых тем.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("Провал (меньше 70%) отнимает одно сердце.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = vm::startControl, modifier = Modifier.fillMaxWidth()) { Text("Начать контрольную") }
                vm.heartsMessage?.let { Spacer(Modifier.height(8.dp)); ErrorBlock(it) }
                TextButton(onClick = { nav.navigate(Routes.REVIEW) }) { Text("Повторение (+1 сердце)") }
            }
        }
        item {
            TextButton(onClick = { vm.generateAiTest() }, enabled = !vm.testGen.loading) { Text("Сгенерировать контрольную через ИИ") }
            if (vm.testGen.loading) LoadingBlock("Составляю контрольную…")
            vm.testGen.error?.let { ErrorBlock(it, if (vm.testGen.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) }
        }
        return
    }
    if (vm.finished) {
        item {
            val total = vm.control.size
            com.neuron.academy.ui.components.Panel(color = (if (vm.passed) GameColors.Success else GameColors.Fire).copy(alpha = 0.12f)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.neuron.academy.ui.components.NeuronMascot(
                        LocalContainer.current.game.player.collectAsState(initial = null).value?.skin ?: "skin_classic", 80.dp,
                        if (vm.passed) com.neuron.academy.ui.components.MascotMood.HAPPY else com.neuron.academy.ui.components.MascotMood.THINKING,
                    )
                    Text("${vm.correctCount} из $total", style = MaterialTheme.typography.headlineMedium)
                    Text("${vm.correctCount * 100 / total}%", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(vm.testResultText ?: "")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.restartTest() }) { Text("Ещё раз") }
                        Button(onClick = { vm.generateAiTest() }, enabled = !vm.testGen.loading) { Text("Новая от ИИ") }
                    }
                }
            }
        }
        if (vm.testGen.loading) item { LoadingBlock("Составляю контрольную…") }
        vm.testGen.error?.let { e -> item { ErrorBlock(e, if (vm.testGen.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) } }
        return
    }
    item {
        LinearProgressIndicator(progress = { (vm.qIndex + 1f) / vm.control.size }, modifier = Modifier.fillMaxWidth())
        Text("Задание ${vm.qIndex + 1} из ${vm.control.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
    item {
        when (val ci = vm.control[vm.qIndex]) {
            is ControlItem.Choice -> {
                com.neuron.academy.ui.components.Eyebrow(ci.source)
                Spacer(Modifier.height(6.dp))
                QuestionCard(question = ci.q, selected = vm.selected, removed = vm.removedOptions, onAnswer = vm::answer)
            }
            is ControlItem.Open -> {
                com.neuron.academy.ui.components.Eyebrow("Задача с открытым ответом")
                Spacer(Modifier.height(6.dp))
                com.neuron.academy.ui.components.Panel {
                    MathText(ci.p.problem, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(vm.openInput, { s -> if (vm.openResult == null) vm.openInput = s }, label = { Text("Ответ") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = vm::submitOpen, enabled = vm.openResult == null && vm.openInput.isNotBlank()) { Text("Ответить") }
                    }
                    vm.openResult?.let { ok ->
                        Spacer(Modifier.height(6.dp))
                        Text(if (ok) "Верно!" else "Неверно. Правильный ответ: ${ci.p.answer}", color = if (ok) GameColors.Success else GameColors.Error)
                        if (!ok) MathText("Решение: " + ci.p.solution, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    item {
        val player = LocalContainer.current.game.player.collectAsState(initial = null).value
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.control[vm.qIndex] is ControlItem.Choice) {
                OutlinedButton(onClick = vm::useFiftyFifty, enabled = vm.selected == null && vm.removedOptions.isEmpty() && (player?.hints ?: 0) > 0) {
                    Text("50/50 (осталось ${player?.hints ?: 0})")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = vm::next, enabled = vm.answeredCurrent) { Text(if (vm.qIndex < vm.control.lastIndex) "Далее" else "Завершить") }
        }
    }
}

@Composable
fun QuestionCard(question: com.neuron.academy.content.Question, selected: Int?, removed: Set<Int>, onAnswer: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MathText(question.text, style = MaterialTheme.typography.titleMedium)
            question.options.forEachIndexed { i, opt ->
                if (i in removed) return@forEachIndexed
                val bg by animateColorAsState(
                    when {
                        selected == null -> MaterialTheme.colorScheme.surfaceVariant
                        i == question.correct -> GameColors.Success.copy(alpha = 0.3f)
                        i == selected -> GameColors.Error.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }, label = "opt",
                )
                Card(
                    onClick = { onAnswer(i) },
                    enabled = selected == null,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = bg, disabledContainerColor = bg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${'A' + i}", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        MathText(opt, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (selected != null && i == question.correct) Icon(Icons.Rounded.Check, null, tint = GameColors.Success)
                        if (selected == i && i != question.correct) Icon(Icons.Rounded.Close, null, tint = GameColors.Error)
                    }
                }
            }
            if (selected != null && question.explanation.isNotBlank()) {
                MathText("" + question.explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.factTab(vm: TopicViewModel, nav: NavHostController) {
    val topic = vm.topic!!
    item {
        val skin = LocalContainer.current.game.player.collectAsState(initial = null).value?.skin ?: "skin_classic"
        NeuronBubble("А вот интересный факт! 🤓", skin)
    }
    item {
        InfoCard("Интересный факт", color = GameColors.Gold) { Text(topic.fact, style = MaterialTheme.typography.bodyLarge) }
    }
    item {
        InfoCard("Ещё факт от ИИ") { AiSection(vm.fact, nav, buttonText = "Удиви меня!", onClick = vm::askFact) }
    }
}

@Composable
fun AiSection(block: com.neuron.academy.ui.screens.AiBlock, nav: NavHostController, buttonText: String, onClick: () -> Unit) {
    if (block.text == null && !block.loading) {
        FilledTonalButton(onClick = onClick) { Text(buttonText) }
    }
    AiResult(block, nav)
    if (block.text != null && !block.loading) TextButton(onClick = onClick) { Text("Ещё вариант") }
}

@Composable
fun AiResult(block: com.neuron.academy.ui.screens.AiBlock, nav: NavHostController) {
    if (block.loading) LoadingBlock()
    block.error?.let { ErrorBlock(it, if (block.noKeys) ({ nav.navigate(Routes.KEYS) }) else null) }
    block.text?.let { t ->
        if (!block.loading) {
            Spacer(Modifier.height(8.dp))
            MathText(t, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
