package com.neuron.academy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neuron.academy.data.db.CardEntity
import com.neuron.academy.ui.LocalContainer
import com.neuron.gamification.AchievementCatalog
import com.neuron.gamification.CardCatalog
import com.neuron.gamification.CardDef
import com.neuron.gamification.CardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(nav: NavHostController) {
    val c = LocalContainer.current
    val data by c.game.collection.collectAsState(initial = emptySet<String>() to emptyMap<String, CardEntity>())
    val (achievements, cards) = data
    var tab by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<CardDef?>(null) }

    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("Достижения и коллекция") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("🏅 Бейджи ${achievements.size}/${AchievementCatalog.all.size}") })
                Tab(tab == 1, { tab = 1 }, text = { Text("🃏 Карточки ${cards.size}/${CardCatalog.all.size}") })
            }
        }
    }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (tab == 0) 2 else 3),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0) {
                items(AchievementCatalog.all, key = { it.id }) { a ->
                    val has = a.id in achievements
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp).alpha(if (has) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (has) a.emoji else "🔒", fontSize = 36.sp)
                            Text(a.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                            Text(a.description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            Text("+${a.rewardCrystals} 💎", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                CardType.entries.forEach { type ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_${type.name}") {
                        Text(type.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(CardCatalog.all.filter { it.type == type }, key = { it.id }) { card ->
                        val owned = cards[card.id]
                        Card(
                            onClick = { if (owned != null) opened = card },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (owned != null) Color(card.rarity.color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (owned != null) card.emoji else "❓", fontSize = 32.sp)
                                Text(if (owned != null) card.title else "???", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                if (owned != null) Text(card.rarity.title, color = Color(card.rarity.color), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
    opened?.let { card ->
        AlertDialog(
            onDismissRequest = { opened = null },
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Круто!") } },
            title = { Text("${card.emoji} ${card.title}") },
            text = {
                Column {
                    Text(card.subtitle, style = MaterialTheme.typography.labelLarge)
                    Text("${card.type.title} · ${card.rarity.title}", color = Color(card.rarity.color))
                    Spacer(Modifier.height(8.dp))
                    Text(card.fact)
                }
            },
        )
    }
}
