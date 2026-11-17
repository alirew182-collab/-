package com.neuron.academy.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.components.NeuronAvatar
import kotlinx.coroutines.launch

private data class Page(val emoji: String, val title: String, val text: String)

private val pages = listOf(
    Page("👋", "Привет! Я — Нейрон", "Робот-помощник и твой личный репетитор по алгебре, физике и химии. Мои микросхемы обожают объяснять сложное простыми словами!"),
    Page("🗺️", "Три мира знаний", "Город чисел — алгебра\nЛаборатория сил — физика\nАлхимическая башня — химия\n\nКлассы 9 → 10 → 11 — это регионы, которые открываются по мере прохождения."),
    Page("⚔️", "Боссы и награды", "В конце каждого класса тебя ждёт битва с боссом. Побеждай, собирай карточки учёных и элементов, копи кристаллы и XP, держи серию дней."),
    Page("🧠", "Встроенный ИИ", "Я умею работать прямо на телефоне — без ключей и интернета. Скачай модель один раз в «Настройках ИИ», и я смогу объяснять «иначе», придумывать задачи и проверять решения. Теория, тесты и игры работают и без неё."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val last = pages.size

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            NeuronAvatar("skin_classic", 140.dp, mood = if (page == last) com.neuron.academy.ui.components.MascotMood.HAPPY else com.neuron.academy.ui.components.MascotMood.NORMAL)
            Spacer(Modifier.height(24.dp))
            AnimatedContent(page, transitionSpec = { (slideInHorizontally { it / 2 } + fadeIn()) togetherWith fadeOut() }, label = "page", modifier = Modifier.weight(1f)) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (p < last) {
                        Text(pages[p].title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(pages[p].text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    } else {
                        Text("Как тебя зовут?", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(name, { name = it.take(24) }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text("Начинаем с 9 класса. Вперёд, к звёздам! 🚀", textAlign = TextAlign.Center)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(last + 1) { i ->
                    Box(Modifier.size(if (i == page) 12.dp else 8.dp).clip(CircleShape).background(if (i == page) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (page < last) page++ else scope.launch {
                        c.game.setName(name.ifBlank { "Исследователь" })
                        c.settings.setOnboardingDone()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (page < last) "Дальше" else "Поехали!", fontWeight = FontWeight.Bold) }
            if (page < last) TextButton(onClick = { page = last }) { Text("Пропустить") }
        }
    }
}
