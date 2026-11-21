package com.neuron.academy.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.neuron.academy.content.ChemLab
import com.neuron.academy.content.ReactionEffect
import com.neuron.academy.ui.LocalContainer
import com.neuron.academy.ui.components.InfoCard
import com.neuron.academy.ui.components.MathText
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimScaffold(nav: NavHostController, title: String, content: @Composable () -> Unit) {
    val c = LocalContainer.current
    LaunchedEffect(Unit) { c.game.onSimulation() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }
}

private fun fmt(v: Float) = if (abs(v - Math.round(v)) < 0.005f) Math.round(v).toString() else "%.2f".format(v)

private fun DrawScope.axes(cx: Float, cy: Float, scale: Float, color: Color) {
    val grid = color.copy(alpha = 0.12f)
    var x = cx % scale
    while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height)); x += scale }
    var y = cy % scale
    while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y)); y += scale }
    drawLine(color.copy(alpha = 0.6f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 3f)
    drawLine(color.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 3f)
}

// ========================= ПАРАБОЛА =========================

@Composable
fun ParabolaScreen(nav: NavHostController) = SimScaffold(nav, "Парабола y = ax² + bx + c") {
    var a by remember { mutableFloatStateOf(1f) }
    var b by remember { mutableFloatStateOf(0f) }
    var cc by remember { mutableFloatStateOf(-2f) }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val vertexColor = Color(0xFFFF7043)
    val rootColor = Color(0xFF43A047)

    val aa = if (abs(a) < 0.05f) 0.05f else a
    val d = b * b - 4 * aa * cc
    val x0 = -b / (2 * aa)
    val y0 = aa * x0 * x0 + b * x0 + cc
    val roots = when {
        d > 0.0001f -> listOf((-b - sqrt(d)) / (2 * aa), (-b + sqrt(d)) / (2 * aa)).sorted()
        abs(d) <= 0.0001f -> listOf(x0)
        else -> emptyList()
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Canvas(Modifier.fillMaxWidth().height(320.dp).padding(8.dp)) {
            val scale = size.width / 16f
            val cx = size.width / 2
            val cy = size.height / 2
            axes(cx, cy, scale, onSurface)
            val path = Path()
            var first = true
            var px = -8f
            while (px <= 8f) {
                val py = aa * px * px + b * px + cc
                val sx = cx + px * scale
                val sy = (cy - py * scale).coerceIn(-2000f, 4000f)
                if (first) { path.moveTo(sx, sy); first = false } else path.lineTo(sx, sy)
                px += 0.05f
            }
            drawPath(path, primary, style = Stroke(6f))
            drawLine(vertexColor.copy(alpha = 0.6f), Offset(cx + x0 * scale, 0f), Offset(cx + x0 * scale, size.height), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
            drawCircle(vertexColor, 10f, Offset(cx + x0 * scale, cy - y0 * scale))
            roots.forEach { r -> drawCircle(rootColor, 10f, Offset(cx + r * scale, cy)) }
        }
    }
    Spacer(Modifier.height(12.dp))
    MathText("\\[y = ${fmt(aa)}x^2 ${if (b >= 0) "+" else "-"} ${fmt(abs(b))}x ${if (cc >= 0) "+" else "-"} ${fmt(abs(cc))}\\]")
    SliderRow("a", a, -3f..3f) { a = it }
    SliderRow("b", b, -6f..6f) { b = it }
    SliderRow("c", cc, -6f..6f) { cc = it }
    Spacer(Modifier.height(8.dp))
    InfoCard("Что видно на графике") {
        Text("• Ветви направлены ${if (aa > 0) "вверх (a > 0)" else "вниз (a < 0)"}")
        Text("• Вершина (оранжевая точка): (${fmt(x0)}; ${fmt(y0)})")
        Text("• Дискриминант D = ${fmt(d)}")
        Text("• Корни (зелёные точки): " + if (roots.isEmpty()) "нет (D < 0 — парабола не пересекает ось Ox)" else roots.joinToString("; ") { fmt(it) })
        Spacer(Modifier.height(6.dp))
        Text("Попробуй: поставь a отрицательным — «улыбка» станет «грустью». Двигай c — парабола поднимается и опускается.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("$label = ${fmt(value)}", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Medium)
        Slider(value, { onChange((it * 10).toInt() / 10f) }, valueRange = range, modifier = Modifier.weight(1f))
    }
}

// ========================= БРОСОК ПОД УГЛОМ =========================

@Composable
fun ProjectileScreen(nav: NavHostController) = SimScaffold(nav, "Бросок под углом") {
    var angle by remember { mutableFloatStateOf(45f) }
    var v0 by remember { mutableFloatStateOf(15f) }
    var gIndex by remember { mutableIntStateOf(0) }
    val planets = listOf("Земля" to 9.8f, "Луна" to 1.6f, "Марс" to 3.7f, "Юпитер" to 24.8f)
    val g = planets[gIndex].second
    val t = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val trails = remember { mutableStateListOf<List<Offset>>() }

    val rad = angle * PI.toFloat() / 180f
    val flight = 2 * v0 * sin(rad) / g
    val range = v0 * v0 * sin(2 * rad) / g
    val hMax = v0 * v0 * sin(rad) * sin(rad) / (2 * g)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Canvas(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
            val worldW = maxOf(range * 1.15f, 10f)
            val worldH = maxOf(hMax * 1.3f, worldW * size.height / size.width * 0.6f)
            val sc = minOf(size.width / worldW, (size.height - 20f) / worldH)
            val ox = 20f
            val oy = size.height - 20f
            drawLine(onSurface.copy(alpha = 0.5f), Offset(0f, oy), Offset(size.width, oy), strokeWidth = 4f)
            // прошлые траектории
            trails.forEach { pts ->
                for (i in 1 until pts.size) drawLine(primary.copy(alpha = 0.25f), Offset(ox + pts[i - 1].x * sc, oy - pts[i - 1].y * sc), Offset(ox + pts[i].x * sc, oy - pts[i].y * sc), strokeWidth = 3f)
            }
            // предполагаемая траектория
            val path = Path()
            var tt = 0f
            path.moveTo(ox, oy)
            while (tt <= flight) {
                val x = v0 * cos(rad) * tt
                val y = v0 * sin(rad) * tt - g * tt * tt / 2
                path.lineTo(ox + x * sc, oy - y * sc); tt += flight / 60f
            }
            drawPath(path, onSurface.copy(alpha = 0.3f), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            // мяч
            val ct = t.value * flight
            val bx = v0 * cos(rad) * ct
            val by = (v0 * sin(rad) * ct - g * ct * ct / 2).coerceAtLeast(0f)
            drawCircle(Color(0xFFFF7043), 14f, Offset(ox + bx * sc, oy - by * sc))
            // вектор скорости
            drawLine(Color(0xFF43A047), Offset(ox, oy), Offset(ox + cos(rad) * 60f, oy - sin(rad) * 60f), strokeWidth = 5f)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        planets.forEachIndexed { i, p -> FilterChip(gIndex == i, { gIndex = i }, label = { Text(p.first) }) }
    }
    SliderRow("α°", angle, 5f..85f) { angle = it }
    SliderRow("v₀", v0, 5f..40f) { v0 = it }
    Button(onClick = {
        scope.launch {
            t.snapTo(0f)
            t.animateTo(1f, tween((flight * 1000 / 2).toInt().coerceIn(600, 6000), easing = LinearEasing))
            val pts = (0..40).map { i ->
                val tt = flight * i / 40f
                Offset(v0 * cos(rad) * tt, (v0 * sin(rad) * tt - g * tt * tt / 2).coerceAtLeast(0f))
            }
            if (trails.size >= 3) trails.removeAt(0)
            trails.add(pts)
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Бросить") }
    Spacer(Modifier.height(8.dp))
    InfoCard("Расчёт") {
        MathText("\\(L = \\frac{v_0^2 \\sin 2\\alpha}{g}\\) = **${fmt(range)} м**\n\\(H = \\frac{v_0^2 \\sin^2\\alpha}{2g}\\) = **${fmt(hMax)} м**\n\\(t = \\frac{2 v_0 \\sin\\alpha}{g}\\) = **${fmt(flight)} с**")
        Text("Подсказка: максимальная дальность — при угле 45°. На Луне тот же бросок улетит в 6 раз дальше.", style = MaterialTheme.typography.bodySmall)
    }
}

// ========================= СМЕШИВАНИЕ ВЕЩЕСТВ =========================

private data class Bubble(val x: Float, val speed: Float, val size: Float, val phase: Float)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChemMixScreen(nav: NavHostController) = SimScaffold(nav, "Смешивание веществ") {
    var a by remember { mutableStateOf<String?>(null) }
    var b by remember { mutableStateOf<String?>(null) }
    var mixedKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reaction = mixedKey?.let { ChemLab.react(it.first, it.second) }
    val bubbles = remember { List(40) { Bubble(Random.nextFloat(), 0.3f + Random.nextFloat(), 3f + Random.nextFloat() * 6f, Random.nextFloat()) } }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(mixedKey) {
        if (mixedKey != null) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(6000, easing = LinearEasing))
        }
    }
    val base = Color(0x3390CAF9)
    val liquid by animateColorAsState(
        when {
            mixedKey == null -> base
            reaction == null -> base
            else -> Color(reaction.resultColor)
        }, tween(1500), label = "liquid",
    )

    Text("Выбери два вещества:", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChemLab.reagents.forEach { r ->
            val sel = a == r.id || b == r.id
            FilterChip(sel, {
                when {
                    sel && a == r.id -> a = null
                    sel && b == r.id -> b = null
                    a == null -> a = r.id
                    b == null -> b = r.id
                    else -> { a = b; b = r.id }
                }
                mixedKey = null
            }, label = { Text(r.formula) })
        }
    }
    Text(
        listOfNotNull(a, b).joinToString(" + ") { ChemLab.reagent(it).name }.ifBlank { "Ничего не выбрано" },
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp),
    )
    Button(
        onClick = {
            val x = a; val y = b
            if (x != null && y != null) scope.launch { mixedKey = x to y; progress.snapTo(0f); progress.animateTo(1f, tween(1200)) }
        },
        enabled = a != null && b != null, modifier = Modifier.fillMaxWidth(),
    ) { Text("Смешать") }
    Spacer(Modifier.height(10.dp))

    val glass = MaterialTheme.colorScheme.onSurface
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Canvas(Modifier.fillMaxWidth().height(280.dp).padding(16.dp)) {
            val w = size.width * 0.5f
            val left = (size.width - w) / 2
            val top = 20f
            val bottom = size.height - 10f
            val level = top + (bottom - top) * (0.75f - 0.35f * progress.value)
            // жидкость
            drawRoundRect(liquid, Offset(left, level), Size(w, bottom - level), CornerRadius(24f, 24f))
            val tm = anim.value
            if (reaction != null && mixedKey != null) {
                when (reaction.effect) {
                    ReactionEffect.GAS -> bubbles.forEach { bb ->
                        val yy = bottom - ((tm * 6 * bb.speed + bb.phase) % 1f) * (bottom - level)
                        drawCircle(Color(reaction.particleColor).copy(alpha = 0.8f), bb.size, Offset(left + 12 + bb.x * (w - 24), yy), style = Stroke(2f))
                    }
                    ReactionEffect.PRECIPITATE, ReactionEffect.METAL -> bubbles.forEach { bb ->
                        val fall = (tm * 1.5f * bb.speed + bb.phase * 0.3f).coerceAtMost(1f)
                        val yy = level + fall * (bottom - level - 10)
                        drawCircle(Color(reaction.particleColor), bb.size, Offset(left + 12 + bb.x * (w - 24), yy))
                    }
                    ReactionEffect.HEAT -> repeat(3) { i ->
                        val p = ((tm * 3 + i / 3f) % 1f)
                        drawLine(Color(0xFFFF7043).copy(alpha = 1 - p), Offset(left + w * (0.3f + 0.2f * i), level - 10 - p * 60), Offset(left + w * (0.3f + 0.2f * i) + 10 * sin(p * 10), level - 30 - p * 60), strokeWidth = 4f)
                    }
                    else -> {}
                }
            }
            // колба
            drawRoundRect(glass.copy(alpha = 0.6f), Offset(left, top), Size(w, bottom - top), CornerRadius(24f, 24f), style = Stroke(5f))
        }
    }
    Spacer(Modifier.height(10.dp))
    if (mixedKey != null) {
        if (reaction != null) {
            InfoCard(reaction.effect.title) {
                Text(reaction.equation, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(reaction.comment)
            }
        } else {
            InfoCard("Реакция не идёт") {
                Text("Видимых изменений нет: не образуется ни осадка, ни газа, ни воды. Попробуй другую пару. Подсказка: щёлочь + соль меди, кислота + сода, нитрат серебра + хлорид…")
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Это виртуальная лаборатория. Настоящие опыты — только с учителем и в защитных очках.", style = MaterialTheme.typography.bodySmall)
}
