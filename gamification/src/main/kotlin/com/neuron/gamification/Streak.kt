package com.neuron.gamification

data class StreakResult(
    val streak: Int,
    val freezesLeft: Int,
    val freezesUsed: Int,
    val isNewDay: Boolean,
    val wasReset: Boolean,
)

/** Логика серии дней 🔥 с «заморозками». Дни считаются как epochDay. */
object StreakCalculator {
    fun checkIn(currentStreak: Int, lastActiveDay: Long, today: Long, freezes: Int): StreakResult {
        if (lastActiveDay == today) return StreakResult(currentStreak.coerceAtLeast(1), freezes, 0, false, false)
        if (lastActiveDay <= 0L) return StreakResult(1, freezes, 0, true, false)
        val gap = (today - lastActiveDay).toInt()
        return when {
            gap == 1 -> StreakResult(currentStreak + 1, freezes, 0, true, false)
            gap > 1 && freezes >= gap - 1 -> StreakResult(currentStreak + 1, freezes - (gap - 1), gap - 1, true, false)
            else -> StreakResult(1, freezes, 0, true, currentStreak > 1)
        }
    }
}
