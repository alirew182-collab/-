package com.neuron.academy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.content.MultiSolution
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.MathText
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.theme.GameColors

/** Вкладка «Способы»: одна задача — несколько решений. */
fun LazyListScope.methodsTab(vm: TopicViewModel, nav: NavHostController, color: Color) {
    val topic = vm.topic ?: return
    item {
        Panel {
            Eyebrow("Одна задача — несколько способов", color = color)
            Spacer(Modifier.height(6.dp))
            Text(
                "Сравни способы и выбери тот, что понятнее тебе. На экзамене пригодится любой — главное, чтобы ответ был верным. " +
                    "А решив задачу двумя способами, ты сам себя проверишь.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (topic.multi.isEmpty()) {
        item {
            Text(
                "Для этой темы разбор готовит Нейрон: нажми кнопку ниже. А у каждого примера и задачи есть кнопка «Другие способы решения».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(topic.multi) { i, ms -> MultiSolutionCard(i + 1, ms, color) }
    item {
        Panel {
            Eyebrow("Ещё задача от Нейрона")
            Spacer(Modifier.height(6.dp))
            Text("Нейрон придумает новую задачу и решит её несколькими способами.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            AiSection(vm.multiAi, nav, buttonText = "Задача с разными способами", onClick = vm::askMultiProblem)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSolutionCard(number: Int, ms: MultiSolution, color: Color) {
    var selected by rememberSaveable(ms.problem) { mutableIntStateOf(0) }
    var showAll by rememberSaveable(ms.problem) { mutableStateOf(false) }
    Panel {
        Eyebrow("Задача $number · способов: ${ms.methods.size}", color = color)
        Spacer(Modifier.height(6.dp))
        MathText(ms.problem, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ms.methods.forEachIndexed { i, m ->
                FilterChip(selected == i && !showAll, { selected = i; showAll = false }, label = { Text("Способ ${i + 1}") })
            }
            FilterChip(showAll, { showAll = !showAll }, label = { Text("Все сразу") })
        }
        Spacer(Modifier.height(8.dp))
        if (showAll) {
            ms.methods.forEach { m ->
                Text(m.name, style = MaterialTheme.typography.titleSmall, color = color)
                MathText(m.steps, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            val m = ms.methods[selected.coerceIn(0, ms.methods.lastIndex)]
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = color)
            MathText(m.steps, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text("Ответ: ${ms.answer}", fontWeight = FontWeight.Bold, color = GameColors.Success)
        Spacer(Modifier.height(6.dp))
        Eyebrow("Какой способ выбрать")
        Text(ms.compare, style = MaterialTheme.typography.bodySmall)
    }
}

/** Блок «Подробно, с нуля» в теории. */
@Composable
fun DeepTheoryCard(vm: TopicViewModel, nav: NavHostController, color: Color) {
    val topic = vm.topic ?: return
    var open by rememberSaveable(topic.id) { mutableStateOf(false) }
    Panel {
        Eyebrow("Подробно, с нуля", color = color)
        Spacer(Modifier.height(4.dp))
        Text(
            "Если краткая теория показалась сложной — здесь всё по шагам: с примером из жизни, формулами, алгоритмом и типичными ошибками.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        if (topic.deepTheory.isNotBlank()) {
            if (!open) FilledTonalButton(onClick = { open = true }) { Text("Читать подробное объяснение") }
            AnimatedVisibility(open) {
                Column {
                    MathText(topic.deepTheory)
                    TextButton(onClick = { open = false }) { Text("Свернуть") }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = vm::askDeep, enabled = !vm.deep.loading) { Text("Ещё объяснение от Нейрона") }
            AiResult(vm.deep, nav)
        } else {
            AiSection(vm.deep, nav, buttonText = "Объясни подробно, как новичку", onClick = vm::askDeep)
        }
    }
}

/** Кнопка «Другие способы решения» под решением примера или задачи. */
@Composable
fun AlternativeMethods(vm: TopicViewModel, nav: NavHostController, problem: String, solution: String) {
    val block = vm.altBlock(problem)
    Spacer(Modifier.height(6.dp))
    if (block.text == null && !block.loading) {
        OutlinedButton(onClick = { vm.askAlternative(problem, solution) }) { Text("Другие способы решения") }
    }
    AiResult(block, nav)
}
