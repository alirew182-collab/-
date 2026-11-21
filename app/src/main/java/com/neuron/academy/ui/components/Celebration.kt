package com.neuron.academy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neuron.academy.ui.Feedback
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.RewardOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val color: Color, val w: Float, val h: Float)

// конфетти в цветах предметов и награды, без неона
private val confettiColors = listOf(Color(0xFF3550C8), Color(0xFFD2452F), Color(0xFF138A66), Color(0xFFC9930C), Color(0xFF1E88B8))

/** Конфетти поверх экрана. */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier, count: Int = 140, durationMs: Long = 3500) {
    val particles = remember {
        List(count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() - Math.PI.toFloat()
            val speed = 500f + Random.nextFloat() * 900f
            Particle(
                x = 0.5f, y = 0.35f,
                vx = cos(angle) * speed, vy = sin(angle) * speed - 300f,
                rot = Random.nextFloat() * 360f, vr = Random.nextFloat() * 600f - 300f,
                color = confettiColors.random(), w = 8f + Random.nextFloat() * 8f, h = 12f + Random.nextFloat() * 10f,
            )
        }
    }
    var frame by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        val start = last
        while ((last - start) / 1_000_000 < durationMs) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f
            last = now
            elapsed = (now - start) / 1e9f
            frame = now
        }
    }
    Canvas(modifier.fillMaxSize()) {
        frame // подписка на кадры
        val t = elapsed
        val alpha = (1f - t * 1000f / durationMs).coerceIn(0f, 1f)
        particles.forEach { p ->
            val px = p.x * size.width + p.vx * t
            val py = p.y * size.height + p.vy * t + 900f * t * t
            rotate(p.rot + p.vr * t, Offset(px, py)) {
                drawRect(p.color.copy(alpha = alpha), Offset(px - p.w / 2, py - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}

/**
 * Слушает награды из GameRepository: маленькие награды — всплывающая плашка,
 * большие (новый уровень, достижение, карточка) — окно с конфетти и звуком.
 */
@Composable
fun RewardHost(rewards: Flow<RewardOutcome>, feedback: Feedback, skin: String) {
    var toast by remember { mutableStateOf<RewardOutcome?>(null) }
    var big by remember { mutableStateOf<RewardOutcome?>(null) }
    LaunchedEffect(rewards) {
        rewards.collect { r ->
            if (r.isBigEvent) {
                big = r
                if (r.newLevel != null) feedback.levelUp() else feedback.correct()
            } else if (!r.isEmpty) {
                toast = r
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(2600); toast = null }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(toast != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            val r = toast
            if (r != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (r.xp > 0) Text("+${r.xp} XP", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Medium)
                        if (r.crystals > 0) { Spacer(Modifier.width(8.dp)); CrystalAmount(r.crystals, MaterialTheme.colorScheme.inverseOnSurface) }
                        r.message?.let { Spacer(Modifier.width(8.dp)); Text(it, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    big?.let { r ->
        Dialog(onDismissRequest = { big = null }) {
            Box {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        NeuronAvatar(skin, 72.dp)
                        Spacer(Modifier.height(12.dp))
                        if (r.newLevel != null) {
                            Text("Новый уровень", style = MaterialTheme.typography.headlineSmall)
                            AnimatedVisibility(true, enter = scaleIn()) {
                                Text("${r.newLevel}", fontSize = 64.sp, fontFamily = com.neuron.academy.ui.theme.Unbounded, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                            r.newRank?.let { Text("Новое звание: ${it.title}", style = MaterialTheme.typography.titleMedium, color = GameColors.Gold) }
                        }
                        r.achievements.forEach { a ->
                            Spacer(Modifier.height(10.dp))
                            Text("Достижение: ${a.title}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(a.description, style = MaterialTheme.typography.bodySmall)
                        }
                        r.card?.let { c ->
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(c.rarity.color).copy(alpha = 0.18f)) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Новая карточка", style = MaterialTheme.typography.labelLarge)
                                    Text(c.emoji, fontSize = 44.sp)
                                    Text(c.title, fontWeight = FontWeight.Medium)
                                    Text(c.rarity.title, color = Color(c.rarity.color), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (r.xp > 0) Text("+${r.xp} XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            if (r.crystals > 0) CrystalAmount(r.crystals, GameColors.Crystal)
                        }
                        r.message?.let { Spacer(Modifier.height(8.dp)); Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { big = null }, modifier = Modifier.fillMaxWidth()) { Text("Дальше") }
                    }
                }
                ConfettiOverlay(Modifier.matchParentSizeSafe())
            }
        }
    }
}

private fun Modifier.matchParentSizeSafe(): Modifier = this.fillMaxSize().background(Color.Transparent)
