package com.neuron.gamification

/**
 * Все правила начисления опыта и кристаллов собраны в одном месте,
 * чтобы баланс игры можно было менять, не трогая остальной код.
 */
object XpRules {
    const val TOPIC_COMPLETED = 50
    const val TEST_CORRECT_ANSWER = 10
    const val TASK_WITHOUT_HINTS = 20
    const val TASK_WITH_HINTS = 8
    const val DAILY_LOGIN = 5
    const val BLITZ_CORRECT = 2
    const val DUEL_WIN = 15
    const val BOSS_DEFEATED = 100
    const val DAILY_PROBLEM = 40
    const val SIMULATION = 3
    const val GUARDIAN_DEFEATED = 50
    const val REVIEW_CORRECT = 5
    const val RAID_WON = 150
}

object CrystalRules {
    const val TOPIC_COMPLETED = 10
    const val PERFECT_TEST = 5
    const val BOSS_DEFEATED = 50
    const val LEVEL_UP = 20
    const val DAILY_LOGIN = 2
    const val DUEL_WIN = 5
    const val HARD_TASK = 5
    const val STREAK_FREEZE_PRICE = 50
    const val GUARDIAN_DEFEATED = 20
    const val RAID_WON = 100
    const val HEARTS_REFILL_PRICE = 60
}

/**
 * Сердца: тратятся на вход в битву и за проваленный тест, восстанавливаются со временем,
 * за повторение пройденного или за кристаллы.
 */
object Hearts {
    const val MAX = 5
    const val REGEN_MINUTES = 30
    private const val REGEN_MS = REGEN_MINUTES * 60_000L

    /** Текущее число сердец и новая отметка времени с учётом восстановления. */
    fun current(stored: Int, updatedAt: Long, now: Long): Pair<Int, Long> {
        if (stored >= MAX || updatedAt <= 0L) return stored.coerceAtMost(MAX) to now
        val regen = ((now - updatedAt) / REGEN_MS).toInt().coerceAtLeast(0)
        val value = (stored + regen).coerceAtMost(MAX)
        return value to (if (value >= MAX) now else updatedAt + regen * REGEN_MS)
    }

    /** Минут до следующего сердца (0 — если сердца полные). */
    fun minutesToNext(stored: Int, updatedAt: Long, now: Long): Int {
        val (h, at) = current(stored, updatedAt, now)
        if (h >= MAX) return 0
        return (((at + REGEN_MS) - now) / 60_000L).toInt().coerceAtLeast(1)
    }
}
