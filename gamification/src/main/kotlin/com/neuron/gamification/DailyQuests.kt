package com.neuron.gamification

/**
 * Ежедневные задания. eventKey — ключ события, которое увеличивает прогресс.
 * Если [isMax] = true, прогресс — это максимум значения (например, рекорд в блице).
 */
data class QuestDef(
    val id: String,
    val title: String,
    val eventKey: String,
    val target: Int,
    val rewardXp: Int,
    val rewardCrystals: Int,
    val isMax: Boolean = false,
)

object QuestEvents {
    const val TASK_SOLVED = "task_solved"            // + ":algebra" / ":physics" / ":chemistry"
    const val TEST_PERFECT = "test_perfect"
    const val TEST_PASSED = "test_passed"
    const val TOPIC_COMPLETED = "topic_completed"
    const val BLITZ_SCORE = "blitz_score"
    const val CHAT_MESSAGE = "chat_message"
    const val DUEL_WIN = "duel_win"
    const val SIMULATION = "simulation"
    const val DAILY_PROBLEM = "daily_problem"
    const val THEORY_READ = "theory_read"
}

object DailyQuestGenerator {
    private val pool = listOf(
        QuestDef("solve_phys_5", "Реши 5 задач по физике", "${QuestEvents.TASK_SOLVED}:physics", 5, 30, 10),
        QuestDef("solve_alg_5", "Реши 5 задач по алгебре", "${QuestEvents.TASK_SOLVED}:algebra", 5, 30, 10),
        QuestDef("solve_chem_3", "Реши 3 задачи по химии", "${QuestEvents.TASK_SOLVED}:chemistry", 3, 25, 8),
        QuestDef("perfect_test", "Пройди тест без ошибок", QuestEvents.TEST_PERFECT, 1, 30, 10),
        QuestDef("pass_2_tests", "Сдай 2 теста", QuestEvents.TEST_PASSED, 2, 25, 8),
        QuestDef("topic_1", "Освой новую тему", QuestEvents.TOPIC_COMPLETED, 1, 30, 10),
        QuestDef("blitz_10", "Набери 10 очков в Блице", QuestEvents.BLITZ_SCORE, 10, 25, 8, isMax = true),
        QuestDef("chat_3", "Задай Нейрону 3 вопроса", QuestEvents.CHAT_MESSAGE, 3, 15, 5),
        QuestDef("duel_1", "Выиграй дуэль у ИИ", QuestEvents.DUEL_WIN, 1, 30, 10),
        QuestDef("sim_2", "Запусти 2 симуляции", QuestEvents.SIMULATION, 2, 15, 5),
        QuestDef("theory_2", "Прочитай теорию 2 тем", QuestEvents.THEORY_READ, 2, 15, 5),
    )

    fun all() = pool
    fun byId(id: String) = pool.firstOrNull { it.id == id }

    /** Детерминированно выбирает 3 задания на день. */
    fun questsFor(epochDay: Long, count: Int = 3): List<QuestDef> {
        val rnd = kotlin.random.Random(epochDay * 7919 + 17)
        return pool.shuffled(rnd).take(count)
    }
}
