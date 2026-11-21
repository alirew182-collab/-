package com.neuron.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.neuron.academy.AppContainer
import com.neuron.academy.ai.AiMessage
import com.neuron.academy.ai.ExplainStyle
import com.neuron.academy.content.Curriculum
import com.neuron.academy.data.db.ChatMessageEntity
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.ErrorBlock
import com.neuron.academy.ui.components.LoadingBlock
import com.neuron.academy.ui.components.MathText
import com.neuron.academy.ui.components.NeuronAvatar
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.components.StreamingText
import com.neuron.academy.ui.containerViewModel
import com.neuron.academy.ui.userMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(private val c: AppContainer, topicId: String?) : ViewModel() {
    val topic = topicId?.let { Curriculum.topic(it) }
    private val thread = topic?.let { "topic_${it.id}" } ?: "main"
    val messages: StateFlow<List<ChatMessageEntity>> = c.chatDao.observeThread(thread).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val player: StateFlow<PlayerEntity> = c.game.player.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEntity())
    var loading by mutableStateOf(false)
    var streaming by mutableStateOf<String?>(null)
    var input by mutableStateOf("")

    init {
        if (c.router.usesBuiltin) c.localLlm.warmUp()
    }
    val hasKeys get() = c.router.isAvailable()

    fun send(text: String = input) {
        val msg = text.trim()
        if (msg.isEmpty() || loading) return
        input = ""
        loading = true
        val prior = messages.value.filter { it.role != "error" }
        viewModelScope.launch {
            c.chatDao.insert(ChatMessageEntity(thread = thread, role = "user", content = msg))
            c.game.onChatMessage()
            try {
                val ls = c.progress.snapshot()
                val raw = prior.map {
                    AiMessage(if (it.role == "user") AiMessage.Role.USER else AiMessage.Role.ASSISTANT, it.content.split("\n\n_").first())
                } + AiMessage(AiMessage.Role.USER, msg)
                // склеиваем подряд идущие сообщения одной роли — так требуют некоторые API
                val history = mutableListOf<AiMessage>()
                raw.dropWhile { it.role == AiMessage.Role.ASSISTANT }.forEach { m ->
                    val lastMsg = history.lastOrNull()
                    if (lastMsg != null && lastMsg.role == m.role) history[history.lastIndex] = AiMessage(m.role, lastMsg.content + "\n\n" + m.content)
                    else history += m
                }
                val r = kotlinx.coroutines.withContext(com.neuron.academy.ai.AiStream { p -> viewModelScope.launch { if (loading) streaming = p } }) {
                    c.tutor.chat(ls.currentGrade, ls.completedTopics.map { it.title }, history, topic)
                }
                c.chatDao.insert(ChatMessageEntity(thread = thread, role = "assistant", content = r.text + "\n\n_" + r.keyName + "_"))
            } catch (e: Exception) {
                c.chatDao.insert(ChatMessageEntity(thread = thread, role = "error", content = e.userMessage()))
            } finally {
                loading = false
                streaming = null
            }
        }
    }

    fun explainOtherwise(style: ExplainStyle) = send("Объясни это иначе — ${style.title.lowercase()}. ${style.instruction}")
    fun whyNeeded() = send("Зачем это нужно в реальной жизни? Приведи яркие примеры.")
    fun clear() = viewModelScope.launch { c.chatDao.clearThread(thread) }
}

private val starters = listOf(
    "Объясни дискриминант через футбол",
    "Как физика работает в Minecraft?",
    "Почему ракета летит в космосе, где не от чего оттолкнуться?",
    "Что такое логарифм простыми словами?",
    "Почему соль растворяется в воде, а песок нет?",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(nav: NavHostController, topicId: String?) {
    val vm = containerViewModel(key = "chat_${topicId ?: "main"}") { ChatViewModel(it, topicId) }
    val messages by vm.messages.collectAsState()
    val player by vm.player.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, vm.loading) {
        val count = messages.size + 2
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeuronAvatar(player.skin, 36.dp, animated = false)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Нейрон — репетитор")
                            Text(vm.topic?.let { "Тема: ${it.title}" } ?: "Спрашивай что угодно по алгебре, физике и химии", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    if (topicId != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = { IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.DeleteSweep, "Очистить") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().imePadding().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    vm.input, { vm.input = it },
                    modifier = Modifier.weight(1f), placeholder = { Text("Спроси Нейрона…") }, maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !vm.loading) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                NeuronBubble(
                    if (vm.topic != null) "Давай разберём тему «${vm.topic.title}». Что непонятно? Могу объяснить на примере из игр, спорта или космоса."
                    else "Спроси про любую тему из программы. Я учитываю то, что ты уже прошёл, и объясню с того места, где непонятно.",
                    player.skin,
                )
            }
            if (!vm.hasKeys) item {
                ErrorBlock("Чтобы я мог отвечать, установи встроенный ИИ: он скачивается один раз и потом работает без интернета и без ключей.") { nav.navigate(Routes.KEYS) }
            }
            if (messages.isEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starters.forEach { s -> AssistChip(onClick = { vm.send(s) }, label = { Text(s) }) }
                }
            }
            items(messages, key = { it.id }) { m -> MessageBubble(m) }
            item {
                if (vm.loading) {
                    val p = vm.streaming
                    if (p.isNullOrBlank()) LoadingBlock() else StreamingText(p)
                }
                val last = messages.lastOrNull()
                if (!vm.loading && last?.role == "assistant") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExplainStyle.entries.forEach { s -> AssistChip(onClick = { vm.explainOtherwise(s) }, label = { Text(s.title) }) }
                        AssistChip(onClick = { vm.whyNeeded() }, label = { Text("Зачем это в жизни?") })
                    }
                }
                if (!vm.loading && last?.role == "error") {
                    TextButton(onClick = { nav.navigate(Routes.KEYS) }) { Text("Настройки ИИ") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessageEntity) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = when (m.role) {
                "user" -> MaterialTheme.colorScheme.primary
                "error" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (mine) Text(m.content, color = MaterialTheme.colorScheme.onPrimary)
                else if (m.role == "error") Text("" + m.content, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                else {
                    val parts = m.content.split("\n\n_")
                    MathText(parts.first(), style = MaterialTheme.typography.bodyMedium)
                    if (parts.size > 1) Text("via " + parts.last().trimEnd('_'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
