package com.neuron.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.content.Subject
import com.neuron.academy.ui.Routes
import com.neuron.academy.ui.components.Eyebrow
import com.neuron.academy.ui.components.IconBadge
import com.neuron.academy.ui.components.Panel
import com.neuron.academy.ui.components.ScreenTitle
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.theme.accent

@Composable
fun GamesScreen(nav: NavHostController) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Игры", "Тренируйся на скорость и смотри, как работают формулы") }
        item { Eyebrow("Соревнование", modifier = Modifier.padding8()) }
        item { GameRow(Icons.Rounded.Timer, "Блиц", "60 секунд, как можно больше задач", GameColors.Fire) { nav.navigate(Routes.BLITZ) } }
        item { GameRow(Icons.Rounded.Psychology, "Дуэль с ИИ", "Пять раундов против Нейрона", MaterialTheme.colorScheme.primary) { nav.navigate(Routes.DUEL) } }
        item { Eyebrow("Симуляции", modifier = Modifier.padding8()) }
        item { GameRow(Icons.AutoMirrored.Rounded.ShowChart, "Парабола", "Меняй a, b и c — график меняется вслед", Subject.ALGEBRA.accent) { nav.navigate(Routes.SIM_PARABOLA) } }
        item { GameRow(Icons.Rounded.SportsBasketball, "Бросок под углом", "Земля, Луна, Марс и Юпитер", Subject.PHYSICS.accent) { nav.navigate(Routes.SIM_PROJECTILE) } }
        item { GameRow(Icons.Rounded.Science, "Смешивание веществ", "Осадки, газы и смена цвета", Subject.CHEMISTRY.accent) { nav.navigate(Routes.SIM_CHEM) } }
    }
}

private fun Modifier.padding8() = this.padding(top = 10.dp, bottom = 2.dp)

@Composable
private fun GameRow(icon: ImageVector, title: String, desc: String, color: Color, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
