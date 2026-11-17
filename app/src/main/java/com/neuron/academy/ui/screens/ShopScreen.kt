package com.neuron.academy.ui.screens

import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neuron.academy.data.GameRepository
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.components.NeuronBubble
import com.neuron.academy.ui.components.SectionTitle
import com.neuron.academy.ui.components.StatChip
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.ShopCatalog
import com.neuron.gamification.ShopCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(nav: NavHostController) {
    val c = LocalContainer.current
    val player by c.game.player.collectAsState(initial = PlayerEntity())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Магазин") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { StatChip(Icons.Rounded.Diamond, player.crystals.toString(), GameColors.Crystal); Spacer(Modifier.width(12.dp)) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { NeuronBubble("Кристаллы даются за темы, тесты без ошибок, боссов и достижения. Трать с умом! 💎", player.skin) }
            ShopCategory.entries.forEach { cat ->
                item(key = cat.name) { SectionTitle(cat.title) }
                items(ShopCatalog.items.filter { it.category == cat }, key = { it.id }) { item ->
                    val owned = item.id in player.owned
                    val equipped = item.id == player.skin || item.id == player.theme
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (item.category) {
                                ShopCategory.SKIN -> com.neuron.academy.ui.components.NeuronMascot(item.id, 52.dp, animated = false)
                                ShopCategory.THEME -> androidx.compose.foundation.layout.Box(
                                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                        .background(com.neuron.academy.ui.theme.appColorScheme(item.id, false).primary),
                                )
                                ShopCategory.CONSUMABLE -> com.neuron.academy.ui.components.IconBadge(
                                    when (item.id) { ShopCatalog.HINTS_PACK -> Icons.Rounded.Lightbulb; ShopCatalog.HEARTS_REFILL -> Icons.Rounded.Favorite; else -> Icons.Rounded.AcUnit },
                                    when (item.id) { ShopCatalog.HINTS_PACK -> GameColors.Gold; ShopCatalog.HEARTS_REFILL -> GameColors.Error; else -> GameColors.Crystal }, 48.dp,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.description, style = MaterialTheme.typography.bodySmall)
                                if (item.id == ShopCatalog.HINTS_PACK) Text("У тебя: ${player.hints}", style = MaterialTheme.typography.labelSmall)
                                if (item.id == ShopCatalog.STREAK_FREEZE) Text("У тебя: ${player.freezes}", style = MaterialTheme.typography.labelSmall)
                                if (item.id == ShopCatalog.HEARTS_REFILL) Text("Сердец: ${c.game.heartsOf(player)} из ${com.neuron.gamification.Hearts.MAX}", style = MaterialTheme.typography.labelSmall)
                            }
                            when {
                                cat == ShopCategory.CONSUMABLE || !owned -> Button(onClick = {
                                    scope.launch {
                                        val msg = when (c.game.buy(item.id)) {
                                            GameRepository.PurchaseResult.Ok -> { c.feedback.correct(); "Куплено: ${item.title} ✨" }
                                            GameRepository.PurchaseResult.NotEnough -> "Не хватает кристаллов. Пройди пару тем! 💎"
                                            GameRepository.PurchaseResult.AlreadyOwned -> "Уже есть"
                                        }
                                        snackbar.showSnackbar(msg)
                                    }
                                }) { Text(if (item.price == 0) "Бесплатно" else "${item.price} 💎") }
                                equipped -> Text("Надето", color = GameColors.Success)
                                else -> OutlinedButton(onClick = { scope.launch { c.game.equip(item.id) } }) { Text("Надеть") }
                            }
                        }
                    }
                }
            }
        }
    }
}
