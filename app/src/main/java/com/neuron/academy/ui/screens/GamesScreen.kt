package com.neuron.academy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.SectionTitle

@Composable
fun GamesScreen(nav: NavHostController) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Игры и симуляции", style = MaterialTheme.typography.headlineSmall) }
        item { SectionTitle("Мини-игры") }
        item { GameCard("⚡", "Блиц", "60 секунд — реши как можно больше быстрых задач!", listOf(Color(0xFFFF9800), Color(0xFFF44336))) { nav.navigate(Routes.BLITZ) } }
        item { GameCard("🤺", "Дуэль с ИИ", "Кто быстрее решит задачу — ты или Нейрон? 5 раундов.", listOf(Color(0xFF7C4DFF), Color(0xFF448AFF))) { nav.navigate(Routes.DUEL) } }
        item { SectionTitle("Симуляции") }
        item { GameCard("📈", "Парабола", "Двигай ползунки a, b, c — смотри, как меняется график", listOf(Color(0xFF5C6BC0), Color(0xFF26C6DA))) { nav.navigate(Routes.SIM_PARABOLA) } }
        item { GameCard("🏀", "Бросок под углом", "Запусти мяч на Земле, Луне или Марсе", listOf(Color(0xFFFF7043), Color(0xFFFFCA28))) { nav.navigate(Routes.SIM_PROJECTILE) } }
        item { GameCard("⚗️", "Смешивание веществ", "Виртуальная лаборатория: осадки, газы и цвета", listOf(Color(0xFF26A69A), Color(0xFF66BB6A))) { nav.navigate(Routes.SIM_CHEM) } }
    }
}

@Composable
private fun GameCard(emoji: String, title: String, desc: String, gradient: List<Color>, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(gradient)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 30.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(desc, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
