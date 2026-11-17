package com.neuron.gamification

import kotlin.math.floor
import kotlin.math.sqrt

/** Звание ученика. Диапазоны уровней задают «прогресс по званиям». */
enum class Rank(val title: String, val emoji: String, val fromLevel: Int) {
    NOVICE("Новичок", "🌱", 1),
    EXPLORER("Исследователь", "🧭", 4),
    SCIENTIST("Учёный", "🔬", 7),
    PROFESSOR("Профессор", "🎓", 11),
    GENIUS("Гений", "🧠", 16);
}

data class LevelInfo(
    val level: Int,
    val rank: Rank,
    val xp: Int,
    val currentLevelXp: Int,
    val nextLevelXp: Int,
) {
    /** Прогресс внутри текущего уровня, 0..1 */
    val progress: Float
        get() = if (nextLevelXp == currentLevelXp) 1f
        else ((xp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp)).coerceIn(0f, 1f)
    val xpToNext: Int get() = (nextLevelXp - xp).coerceAtLeast(0)
}

object LevelSystem {
    private const val K = 25

    /** Сколько всего XP нужно, чтобы достичь уровня [level]. */
    fun xpForLevel(level: Int): Int = K * level * (level - 1)

    fun levelFor(xp: Int): Int {
        // решаем K*n*(n-1) <= xp
        var n = floor((1 + sqrt(1.0 + 4.0 * xp / K)) / 2).toInt().coerceAtLeast(1)
        while (xpForLevel(n + 1) <= xp) n++
        while (n > 1 && xpForLevel(n) > xp) n--
        return n
    }

    fun rankFor(level: Int): Rank = Rank.entries.last { level >= it.fromLevel }

    fun info(xp: Int): LevelInfo {
        val level = levelFor(xp)
        return LevelInfo(level, rankFor(level), xp, xpForLevel(level), xpForLevel(level + 1))
    }
}
