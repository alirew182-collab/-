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
        "Привет! Я Нейрон. Сегодня отличный день, чтобы прокачать мозг! 🧠",
        "Бип-буп! Мои датчики фиксируют высокий уровень любопытства. Начнём?",
        "Говорят, Ньютону яблоко упало на голову. Нам повезло больше — у нас есть я! 🍎",
        "Каждая решённая задача — это +1 к суперсиле. Погнали!",
        "Мой процессор соскучился по формулам. Давай что-нибудь решим!",
    )
    val correct = listOf("Точно в цель! 🎯", "Супер! Твой мозг работает как квантовый компьютер!", "Верно! Я горжусь тобой 🤖💙", "Есть! Так держать!", "Блестяще, как золото в таблице Менделеева!")
    val wrong = listOf("Почти! Давай разберёмся вместе 🙂", "Ошибки — это ступеньки к успеху. Попробуем ещё?", "Не беда! Даже ракеты не всегда взлетают с первого раза 🚀", "Хм, тут есть подвох. Посмотри подсказку!")
    val levelUp = listOf("Новый уровень! Мои схемы искрятся от восторга! ✨", "Ты растёшь быстрее, чем ракета на старте!", "Уровень повышен! Ещё немного — и станешь Гением!")
    val bossWin = listOf("Босс повержен! Новый регион ждёт тебя! 🏰", "Ты победил! Даже я так не умею… а я робот!")
    val bossLose = listOf("Босс оказался крепким. Подучим темы и вернёмся сильнее! 💪")

    fun random(list: List<String>) = list.random()
}
