package com.neuron.gamification

/** Итог одного игрового события — что получил игрок. Используется UI для празднования. */
data class RewardOutcome(
    val xp: Int = 0,
    val crystals: Int = 0,
    val newLevel: Int? = null,
    val newRank: Rank? = null,
    val achievements: List<AchievementDef> = emptyList(),
    val card: CardDef? = null,
    val message: String? = null,
) {
    val isBigEvent: Boolean get() = newLevel != null || achievements.isNotEmpty() || card != null
    val isEmpty: Boolean get() = xp == 0 && crystals == 0 && !isBigEvent && message == null

    operator fun plus(o: RewardOutcome) = RewardOutcome(
        xp = xp + o.xp,
        crystals = crystals + o.crystals,
        newLevel = o.newLevel ?: newLevel,
        newRank = o.newRank ?: newRank,
        achievements = achievements + o.achievements,
        card = o.card ?: card,
        message = o.message ?: message,
    )
}

/** Реплики робота-помощника «Нейрона» — сюжетная обёртка. */
object NeuronLines {
    val greetings = listOf(
        "Привет, я Нейрон. Продолжим с того места, где ты остановился?",
        "Бип-буп. Датчики фиксируют высокий уровень любопытства. Начнём?",
        "Одна тема занимает минут пятнадцать. Хватит, чтобы закрыть ещё одну сегодня",
        "Мой процессор соскучился по формулам. Давай что-нибудь решим",
        "Если тема не идёт, нажми «Объясни иначе», и я расскажу по-другому",
    )
    val correct = listOf("Верно", "Точно в цель", "Есть, так держать", "Верно. Следующая будет чуть сложнее", "Всё сходится")
    val wrong = listOf("Почти. Посмотри, где разошлось с ответом", "Не сошлось. Открой подсказку", "Тут есть подвох. Сравни со своим решением по шагам", "Ошибка в одном месте. Найдём её?")
    val levelUp = listOf("Новый уровень. Мои схемы искрятся", "Уровень повышен", "Новый уровень: до следующего звания осталось меньше")
    val bossWin = listOf("Босс повержен, новый раздел открыт", "Ты победил. Даже у меня так быстро не выходит, а я робот")
    val bossLose = listOf("Босс оказался крепким. Повтори темы раздела и возвращайся")

    fun random(list: List<String>) = list.random()
}
