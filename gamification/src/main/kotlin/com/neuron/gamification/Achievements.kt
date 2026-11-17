package com.neuron.gamification

/** Срез статистики игрока, по которому проверяются достижения. */
data class PlayerSnapshot(
    val level: Int = 1,
    val topicsCompleted: Int = 0,
    val completedTopicIds: Set<String> = emptySet(),
    val perfectTopicIds: Set<String> = emptySet(),
    val perfectTests: Int = 0,
    val streak: Int = 0,
    val hardTasksSolved: Int = 0,
    val bossesDefeated: Int = 0,
    val cardsCollected: Int = 0,
    val blitzBest: Int = 0,
    val duelWins: Int = 0,
    val chatMessages: Int = 0,
    val simulationsRun: Int = 0,
    val gradesCompleted: Set<Int> = emptySet(),
    val itemsBought: Int = 0,
    val guardiansDefeated: Int = 0,
    val raidsWon: Int = 0,
    val reviewsDone: Int = 0,
)

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val rewardCrystals: Int,
    val condition: (PlayerSnapshot) -> Boolean,
)

/**
 * Каталог достижений. Чтобы добавить новое — просто допишите элемент в список.
 */
object AchievementCatalog {
    val all: List<AchievementDef> = listOf(
        AchievementDef("first_topic", "Первый шаг", "Пройди первую тему", "👣", 10) { it.topicsCompleted >= 1 },
        AchievementDef("topics_5", "Разогнался", "Пройди 5 тем", "🏃", 20) { it.topicsCompleted >= 5 },
        AchievementDef("perfect_10", "10 тем без ошибок", "Сдай 10 тестов на 100%", "💯", 50) { it.perfectTests >= 10 },
        AchievementDef("streak_3", "Три дня подряд", "Занимайся 3 дня подряд", "🔥", 10) { it.streak >= 3 },
        AchievementDef("streak_7", "7 дней подряд", "Неделя без пропусков", "🔥", 30) { it.streak >= 7 },
        AchievementDef("streak_30", "Месяц знаний", "30 дней подряд", "🌋", 100) { it.streak >= 30 },
        AchievementDef("hard_task", "Решил сложную задачу", "Реши задачу сложного уровня", "🧗", 20) { it.hardTasksSolved >= 1 },
        AchievementDef("hard_10", "Покоритель вершин", "Реши 10 сложных задач", "🏔️", 60) { it.hardTasksSolved >= 10 },
        AchievementDef("trig_master", "Мастер тригонометрии", "Сдай тест по тригонометрии без ошибок", "📐", 30) { "a10_trig" in it.perfectTopicIds },
        AchievementDef("newton_friend", "Друг Ньютона", "Сдай тест по законам Ньютона без ошибок", "🍎", 30) { "p9_newton" in it.perfectTopicIds },
        AchievementDef("derivative_pro", "Повелитель производных", "Сдай тест по производной без ошибок", "🏎️", 30) { "a11_deriv" in it.perfectTopicIds },
        AchievementDef("young_alchemist", "Юный алхимик", "Пройди все темы химии 9 класса", "⚗️", 30) {
            it.completedTopicIds.containsAll(listOf("c9_dissoc", "c9_react", "c9_nonmet", "c9_met"))
        },
        AchievementDef("boss_1", "Охотник на боссов", "Победи первого босса", "⚔️", 30) { it.bossesDefeated >= 1 },
        AchievementDef("boss_all", "Легенда академии", "Победи всех 9 боссов", "👑", 200) { it.bossesDefeated >= 9 },
        AchievementDef("grade_9", "Выпускник 9 класса", "Заверши 9 класс", "🎒", 50) { 9 in it.gradesCompleted },
        AchievementDef("grade_10", "Покоритель 10 класса", "Заверши 10 класс", "🏅", 70) { 10 in it.gradesCompleted },
        AchievementDef("grade_11", "Готов к экзаменам", "Заверши 11 класс", "🏆", 100) { 11 in it.gradesCompleted },
        AchievementDef("collector_10", "Коллекционер", "Собери 10 карточек", "🃏", 30) { it.cardsCollected >= 10 },
        AchievementDef("collector_30", "Хранитель музея", "Собери 30 карточек", "🏛️", 80) { it.cardsCollected >= 30 },
        AchievementDef("blitz_15", "Молния", "Набери 15 очков в Блице", "⚡", 25) { it.blitzBest >= 15 },
        AchievementDef("duel_5", "Дуэлянт", "Победи ИИ в 5 дуэлях", "🤺", 40) { it.duelWins >= 5 },
        AchievementDef("chat_20", "Почемучка", "Задай Нейрону 20 вопросов", "💬", 20) { it.chatMessages >= 20 },
        AchievementDef("sim_10", "Экспериментатор", "Запусти 10 симуляций", "🧪", 20) { it.simulationsRun >= 10 },
        AchievementDef("shopper", "Модник", "Купи что-нибудь в магазине", "🛍️", 10) { it.itemsBought >= 1 },
        AchievementDef("topics_25", "Четверть пути", "Пройди 25 тем", "🧱", 40) { it.topicsCompleted >= 25 },
        AchievementDef("topics_50", "Полсотни", "Пройди 50 тем", "🏗️", 80) { it.topicsCompleted >= 50 },
        AchievementDef("topics_100", "Сотня тем", "Пройди 100 тем", "🏛️", 200) { it.topicsCompleted >= 100 },
        AchievementDef("topics_150", "Полторы сотни", "Пройди 150 тем", "🗼", 300) { it.topicsCompleted >= 150 },
        AchievementDef("topics_200", "Двести тем", "Пройди 200 тем", "🌟", 400) { it.topicsCompleted >= 200 },
        AchievementDef("topics_252", "Почти вся программа", "Пройди 252 темы", "🌠", 700) { it.topicsCompleted >= 252 },
        AchievementDef("topics_288", "Вся программа", "Пройди все 288 тем", "🌌", 900) { it.topicsCompleted >= 288 },
        AchievementDef("perfect_25", "Перфекционист", "25 тестов на 100%", "💎", 100) { it.perfectTests >= 25 },
        AchievementDef("guardian_1", "Первый страж", "Победи стража раздела", "🛡️", 15) { it.guardiansDefeated >= 1 },
        AchievementDef("guardian_6", "Гроза стражей", "Победи 6 стражей", "⚔️", 50) { it.guardiansDefeated >= 6 },
        AchievementDef("guardian_18", "Половина крепостей", "Победи 18 стражей", "🏯", 120) { it.guardiansDefeated >= 18 },
        AchievementDef("guardian_36", "Покоритель крепостей", "Победи 36 стражей", "🏰", 250) { it.guardiansDefeated >= 36 },
        AchievementDef("guardian_all", "Ни шагу назад", "Победи всех 63 стражей", "👑", 400) { it.guardiansDefeated >= 63 },
        AchievementDef("raid_1", "Рейдер", "Победи босса рейда недели", "🌀", 60) { it.raidsWon >= 1 },
        AchievementDef("raid_4", "Ветеран рейдов", "Победи в 4 рейдах", "🌪️", 150) { it.raidsWon >= 4 },
        AchievementDef("review_10", "Память как у слона", "Пройди 10 повторений", "🐘", 50) { it.reviewsDone >= 10 },
        AchievementDef("streak_100", "Сотня дней", "100 дней подряд", "☄️", 300) { it.streak >= 100 },
        AchievementDef("level_explorer", "Исследователь", "Достигни звания «Исследователь»", "🧭", 20) { it.level >= Rank.EXPLORER.fromLevel },
        AchievementDef("level_scientist", "Учёный", "Достигни звания «Учёный»", "🔬", 40) { it.level >= Rank.SCIENTIST.fromLevel },
        AchievementDef("level_genius", "Гений", "Достигни звания «Гений»", "🧠", 150) { it.level >= Rank.GENIUS.fromLevel },
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    /** Возвращает новые достижения, которых ещё нет в [unlocked]. */
    fun newlyUnlocked(snapshot: PlayerSnapshot, unlocked: Set<String>): List<AchievementDef> =
        all.filter { it.id !in unlocked && it.condition(snapshot) }
}
