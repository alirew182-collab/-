package com.neuron.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Question
import com.neuron.academy.content.Topic
import com.neuron.academy.ui.components.EmptyHint
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.MascotMood
import com.neuron.academy.ui.components.NeuronMascot
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.ThinProgress
import com.neuron.academy.ui.containerViewModel
import kotlinx.coroutines.launch

/**
 * Интервальное повторение: 8 вопросов из пройденных тем, в первую очередь — из давно не повторённых.
 * Хороший результат возвращает сердце.
 */
class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    data class Item(val topic: Topic, val q: Question)

    var items by mutableStateOf<List<Item>?>(null)
    var index by mutableIntStateOf(0)
    var selected by mutableStateOf<Int?>(null)
    var correct by mutableIntStateOf(0)
    var finished by mutableStateOf(false)
    private val correctTopics = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val ls = c.progress.snapshot()
            val done = ls.allTopics.filter { it.status == com.neuron.academy.data.TopicStatus.COMPLETED }
                .sortedBy { it.reviewedAt ?: it.completedAt ?: 0L }
            val picked = done.take(8)
            items = if (picked.isEmpty()) emptyList()
            else List(8) { i -> picked[i % picked.size] }.map { st -> Item(st.topic, st.topic.test.random()) }.shuffled()
        }
    }

    fun answer(i: Int) {
        val it = items?.getOrNull(index) ?: return
        if (selected != null) return
        selected = i
        if (i == it.q.correct) { correct++; correctTopics += it.topic.id; c.feedback.correct() } else c.feedback.wrong()
    }

    fun next() {
        val list = items ?: return
        if (index < list.lastIndex) { index++; selected = null } else {
            finished = true
            viewModelScope.launch {
                c.progress.markReviewed(correctTopics)
                c.game.onReview(correct, list.size)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(nav: NavHostController) {
    val vm = containerViewModel { ReviewViewModel(it) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Повторение") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        val list = vm.items
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                list == null -> item { LoadingBlock("Собираю вопросы…") }
                list.isEmpty() -> item { EmptyHint("Повторять пока нечего — пройди хотя бы одну тему.") }
                vm.finished -> item {
                    Panel {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            NeuronMascot("skin_classic", 80.dp, if (vm.correct * 4 >= list.size * 3) MascotMood.HAPPY else MascotMood.THINKING)
                            Text("${vm.correct} из ${list.size}", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (vm.correct * 4 >= list.size * 3) "Отлично! Знания освежены, держи сердце." else "Есть что подтянуть — темы с ошибками стоит перечитать.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { nav.popBackStack() }) { Text("Готово") }
                        }
                    }
                }
                else -> {
                    item {
                        ThinProgress((vm.index + 1f) / list.size, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Eyebrow("${list[vm.index].topic.subject.title} · ${list[vm.index].topic.title}")
                    }
                    item { QuestionCard(list[vm.index].q, vm.selected, emptySet(), vm::answer) }
                    item { Button(onClick = vm::next, enabled = vm.selected != null, modifier = Modifier.fillMaxWidth()) { Text(if (vm.index < list.lastIndex) "Далее" else "Завершить") } }
                }
            }
        }
    }
}
