package com.neuron.academy.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neuron.academy.AppContainer
import com.neuron.academy.content.Subject
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.components.RewardHost
import com.neuron.academy.ui.screens.BlitzScreen
import com.neuron.academy.ui.screens.ChatScreen
import com.neuron.academy.ui.screens.ChemMixScreen
import com.neuron.academy.ui.screens.CollectionScreen
import com.neuron.academy.ui.screens.DailyScreen
import com.neuron.academy.ui.screens.DuelScreen
import com.neuron.academy.ui.screens.ExamScreen
import com.neuron.academy.ui.screens.GamesScreen
import com.neuron.academy.ui.screens.HomeScreen
import com.neuron.academy.ui.screens.KeyEditScreen
import com.neuron.academy.ui.screens.KeysScreen
import com.neuron.academy.ui.screens.OnboardingScreen
import com.neuron.academy.ui.screens.ParabolaScreen
import com.neuron.academy.ui.screens.ProfileScreen
import com.neuron.academy.ui.screens.ProjectileScreen
import com.neuron.academy.ui.screens.SettingsScreen
import com.neuron.academy.ui.screens.ShopScreen
import com.neuron.academy.ui.screens.TopicScreen
import com.neuron.academy.ui.screens.WorldScreen
import com.neuron.academy.ui.theme.NeuronTheme

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DAILY = "daily"
    const val CHAT = "chat"
    const val GAMES = "games"
    const val PROFILE = "profile"
    const val BLITZ = "blitz"
    const val DUEL = "duel"
    const val SIM_PARABOLA = "sim_parabola"
    const val SIM_PROJECTILE = "sim_projectile"
    const val SIM_CHEM = "sim_chem"
    const val COLLECTION = "collection"
    const val SHOP = "shop"
    const val SETTINGS = "settings"
    const val KEYS = "keys"
    fun world(s: Subject) = "world/${s.key}"
    fun topic(id: String) = "topic/$id"
    fun exam(s: Subject, grade: Int) = "exam/${s.key}/$grade"
    fun topicChat(id: String) = "chat_topic/$id"
    fun keyEdit(id: String) = "key_edit/$id"
}

private data class Tab(val route: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Карта", Icons.Filled.Map),
    Tab(Routes.DAILY, "Задания", Icons.Filled.EmojiEvents),
    Tab(Routes.CHAT, "Нейрон", Icons.Filled.Chat),
    Tab(Routes.GAMES, "Игры", Icons.Filled.SportsEsports),
    Tab(Routes.PROFILE, "Профиль", Icons.Filled.Person),
)

@Composable
fun NeuronRoot(c: AppContainer) {
    val themeMode by c.settings.themeMode.collectAsState()
    val player by c.game.player.collectAsState(initial = PlayerEntity())
    val onboardingDone by c.settings.onboardingDone.collectAsState()

    NeuronTheme(themeMode, player.theme) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showBar = tabs.any { it.route == route }
        val start = androidx.compose.runtime.remember { if (c.settings.onboardingDone.value) Routes.HOME else Routes.ONBOARDING }

        LaunchedEffect(onboardingDone) {
            if (onboardingDone) c.game.dailyCheckIn()
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBar) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = { nav.navigateTab(tab.route) },
                                    icon = { Icon(tab.icon, tab.title) },
                                    label = { Text(tab.title) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = start,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(250)) },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.HOME) { HomeScreen(nav) }
                    composable(Routes.DAILY) { DailyScreen(nav) }
                    composable(Routes.CHAT) { ChatScreen(nav, topicId = null) }
                    composable(Routes.GAMES) { GamesScreen(nav) }
                    composable(Routes.PROFILE) { ProfileScreen(nav) }
                    composable("world/{subject}", listOf(navArgument("subject") { type = NavType.StringType })) {
                        WorldScreen(nav, Subject.byKey(it.arguments?.getString("subject") ?: "algebra"))
                    }
                    composable("topic/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        TopicScreen(nav, it.arguments?.getString("id") ?: "")
                    }
                    composable(
                        "exam/{subject}/{grade}",
                        listOf(navArgument("subject") { type = NavType.StringType }, navArgument("grade") { type = NavType.IntType }),
                    ) {
                        ExamScreen(nav, Subject.byKey(it.arguments?.getString("subject") ?: "algebra"), it.arguments?.getInt("grade") ?: 9)
                    }
                    composable("chat_topic/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        ChatScreen(nav, topicId = it.arguments?.getString("id"))
                    }
                    composable(Routes.BLITZ) { BlitzScreen(nav) }
                    composable(Routes.DUEL) { DuelScreen(nav) }
                    composable(Routes.SIM_PARABOLA) { ParabolaScreen(nav) }
                    composable(Routes.SIM_PROJECTILE) { ProjectileScreen(nav) }
                    composable(Routes.SIM_CHEM) { ChemMixScreen(nav) }
                    composable(Routes.COLLECTION) { CollectionScreen(nav) }
                    composable(Routes.SHOP) { ShopScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable(Routes.KEYS) { KeysScreen(nav) }
                    composable("key_edit/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        KeyEditScreen(nav, it.arguments?.getString("id") ?: "new")
                    }
                }
            }
            RewardHost(c.game.rewards, c.feedback, player.skin)
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
