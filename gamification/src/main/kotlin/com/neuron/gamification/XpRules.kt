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
}
