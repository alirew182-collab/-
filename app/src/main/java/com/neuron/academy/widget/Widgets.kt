package com.neuron.academy.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.neuron.academy.MainActivity
import com.neuron.academy.R
import com.neuron.academy.ui.theme.appColorScheme

val RouteKey = ActionParameters.Key<String>("route")

private val widgetColors = ColorProviders(
    light = appColorScheme("theme_default", false),
    dark = appColorScheme("theme_default", true),
)

private val Fire = ColorProvider(Color(0xFFE0561B))
private val Crystal = ColorProvider(Color(0xFF1E88B8))
private val Success = ColorProvider(Color(0xFF1F8A55))

private fun open(route: String) = actionStartActivity<MainActivity>(actionParametersOf(RouteKey to route))

@Composable
private fun WidgetShell(route: String, padding: Int = 14, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp)
            .background(GlanceTheme.colors.surface).padding(padding.dp).clickable(open(route)),
    ) { content() }
}

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(),
    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    maxLines = 1,
)

@Composable
private fun Stat(value: String, label: String, color: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier) {
        Text(value, style = TextStyle(color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
    }
}

// ======================= Прогресс =======================

class ProgressWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(260.dp, 110.dp), DpSize(260.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetRepository.load(context)
        provideContent { GlanceTheme(colors = widgetColors) { Content(d) } }
    }

    @Composable
    private fun Content(d: WidgetData) {
        val wide = LocalSize.current.width >= 250.dp
        val tall = LocalSize.current.height >= 170.dp
        WidgetShell("home") {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_mascot), "Нейрон", GlanceModifier.size(40.dp))
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Eyebrow("${d.grade} класс · ${(d.gradeProgress * 100).toInt()}%")
                        Text(
                            "Уровень ${d.level} · ${d.rank}",
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = d.levelProgress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text("${d.xp} XP · ещё ${d.xpToNext} до следующего", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("${d.streak}", "дн. подряд", Fire, GlanceModifier.defaultWeight())
                    Stat("${d.crystals}", "кристаллы", Crystal, GlanceModifier.defaultWeight())
                    if (wide) Stat("${d.questsDone}/${d.questsTotal}", "задания", Success, GlanceModifier.defaultWeight())
                }
                if (tall && d.nextTopicTitle != null) {
                    Spacer(GlanceModifier.height(10.dp))
                    Box(
                        GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(GlanceTheme.colors.primaryContainer).padding(10.dp)
                            .clickable(open("topic/${d.nextTopicId}")),
                    ) {
                        Column {
                            Eyebrow("Продолжить · ${d.nextTopicSubject}")
                            Text(d.nextTopicTitle, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

// ======================= Задача дня =======================

class DailyProblemWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetRepository.load(context)
        provideContent { GlanceTheme(colors = widgetColors) { Content(d) } }
    }

    @Composable
    private fun Content(d: WidgetData) {
        WidgetShell("daily") {
            Column(GlanceModifier.fillMaxSize()) {
                Eyebrow("Задача дня · ${d.problemSubject}")
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    d.problemText,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    maxLines = if (LocalSize.current.height > 170.dp) 7 else 3,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (d.problemSolved) "Решено — возвращайся завтра" else "+40 XP за решение",
                        style = TextStyle(color = if (d.problemSolved) Success else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1,
                    )
                    if (!d.problemSolved) {
                        Box(
                            GlanceModifier.cornerRadius(12.dp).background(GlanceTheme.colors.primary).padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text("Решить", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

class DailyProblemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyProblemWidget()
}

// ======================= Серия =======================

class StreakWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetRepository.load(context)
        provideContent { GlanceTheme(colors = widgetColors) { Content(d) } }
    }

    @Composable
    private fun Content(d: WidgetData) {
        WidgetShell("daily") {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(R.drawable.widget_flame), "Серия", GlanceModifier.size(30.dp))
                Text("${d.streak}", style = TextStyle(color = Fire, fontSize = 36.sp, fontWeight = FontWeight.Bold))
                Text(daysWord(d.streak) + " подряд", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    if (d.activeToday) "сегодня ✓" else "загляни сегодня",
                    style = TextStyle(color = if (d.activeToday) Success else GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    private fun daysWord(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> "день"
            m10 in 2..4 && m100 !in 12..14 -> "дня"
            else -> "дней"
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

// ======================= Быстрый старт =======================

class QuickStartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WidgetRepository.load(context)
        provideContent { GlanceTheme(colors = widgetColors) { Content(d) } }
    }

    @Composable
    private fun Content(d: WidgetData) {
        WidgetShell(if (d.nextTopicId != null) "topic/${d.nextTopicId}" else "home", padding = 10) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer).padding(10.dp)
                        .clickable(open(if (d.nextTopicId != null) "topic/${d.nextTopicId}" else "home")),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow("Продолжить")
                    Text(
                        d.nextTopicTitle ?: "Карта знаний",
                        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                QuickButton("Блиц", "blitz")
                Spacer(GlanceModifier.width(8.dp))
                QuickButton("Чат", "chat")
            }
        }
    }

    @Composable
    private fun QuickButton(title: String, route: String) {
        Box(
            GlanceModifier.width(64.dp).fillMaxHeight().cornerRadius(16.dp).background(GlanceTheme.colors.surfaceVariant).clickable(open(route)),
            contentAlignment = Alignment.Center,
        ) {
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class QuickStartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickStartWidget()
}

/** Обновляет все виджеты после изменения данных. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { ProgressWidget().updateAll(context) }
        runCatching { DailyProblemWidget().updateAll(context) }
        runCatching { StreakWidget().updateAll(context) }
        runCatching { QuickStartWidget().updateAll(context) }
    }
}
