package com.neuron.academy.data

import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import com.neuron.academy.content.Topic
import com.neuron.academy.data.db.AchievementEntity
import com.neuron.academy.data.db.CardEntity
import com.neuron.academy.data.db.PlayerDao
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.data.db.QuestEntity
import com.neuron.academy.data.db.XpEventEntity
import com.neuron.gamification.AchievementCatalog
import com.neuron.gamification.CardCatalog
import com.neuron.gamification.CardDef
import com.neuron.gamification.CrystalRules
import com.neuron.gamification.DailyQuestGenerator
import com.neuron.gamification.LevelSystem
import com.neuron.gamification.NeuronLines
import com.neuron.gamification.PlayerSnapshot
import com.neuron.gamification.QuestDef
import com.neuron.gamification.QuestEvents
import com.neuron.gamification.RewardOutcome
import com.neuron.gamification.ShopCatalog
import com.neuron.gamification.ShopCategory
import com.neuron.gamification.StreakCalculator
import com.neuron.gamification.XpRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class QuestState(val def: QuestDef, val progress: Int, val claimed: Boolean) {
    val done get() = progress >= def.target
}

/**
 * Применяет правила геймификации (модуль :gamification) к данным игрока в Room
 * и публикует награды в [rewards], чтобы UI мог их отпраздновать.
 */
class GameRepository(
    private val dao: PlayerDao,
    private val progress: ProgressRepository,
) {
    private val mutex = Mutex()
    private val _rewards = MutableSharedFlow<RewardOutcome>(extraBufferCapacity = 8)
    val rewards: SharedFlow<RewardOutcome> = _rewards

    val player: Flow<PlayerEntity> = dao.observe().map { it ?: PlayerEntity() }
    val achievements = dao.observeAchievements()
    val cards = dao.observeCards()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun xpSince(days: Int) = dao.observeXpSince(today() - days + 1)

    val quests: Flow<List<QuestState>>
        get() {
            val day = today()
            return dao.observeQuests(day).map { saved ->
                DailyQuestGenerator.questsFor(day).map { def ->
                    val s = saved.firstOrNull { it.questId == def.id }
                    QuestState(def, s?.progress ?: 0, s?.claimed ?: false)
                }
            }
        }

    fun dailyProblem() = dao.observeDailyProblem(today())

    private suspend fun current(): PlayerEntity = dao.get() ?: PlayerEntity().also { dao.upsert(it) }

    suspend fun setName(name: String) = mutex.withLock { dao.upsert(current().copy(name = name)) }

    // ======================== события ========================

    /** Ежедневный вход: серия дней 🔥 и +5 XP. */
    suspend fun dailyCheckIn(): RewardOutcome = reward {
        val p = current()
        val today = today()
        val r = StreakCalculator.checkIn(p.streak, p.lastActiveDay, today, p.freezes)
        if (!r.isNewDay) return@reward RewardOutcome()
        dao.upsert(p.copy(streak = r.streak, bestStreak = maxOf(p.bestStreak, r.streak), lastActiveDay = today, freezes = r.freezesLeft))
        val msg = when {
            r.freezesUsed > 0 -> "🧊 Заморозка спасла твою серию! 🔥 ${r.streak} дн."
            r.wasReset -> "Новая серия начинается сегодня! 🔥"
            r.streak > 1 -> "🔥 Серия: ${r.streak} дн. подряд!"
            else -> "С возвращением! 🔥"
        }
        addXp(XpRules.DAILY_LOGIN, "Ежедневный вход", null) +
            addCrystals(CrystalRules.DAILY_LOGIN) + RewardOutcome(message = msg)
    }

    suspend fun onTheoryRead(topic: Topic, first: Boolean): RewardOutcome = reward {
        if (!first) return@reward RewardOutcome()
        quest(QuestEvents.THEORY_READ, 1)
    }

    suspend fun onTopicTest(topic: Topic, correct: Int, total: Int, outcome: TopicTestOutcome): RewardOutcome = reward {
        var r = addXp(correct * XpRules.TEST_CORRECT_ANSWER, "Тест: ${topic.title}", topic.subject)
        if (outcome.passed) r += quest(QuestEvents.TEST_PASSED, 1)
        if (outcome.perfect) {
            r += quest(QuestEvents.TEST_PERFECT, 1)
            if (outcome.firstPerfect) {
                dao.upsert(current().let { it.copy(perfectTests = it.perfectTests + 1) })
                r += addCrystals(CrystalRules.PERFECT_TEST)
            }
        }
        if (outcome.firstCompletion) {
            r += addXp(XpRules.TOPIC_COMPLETED, "Тема пройдена: ${topic.title}", topic.subject)
            r += addCrystals(CrystalRules.TOPIC_COMPLETED)
            r += quest(QuestEvents.TOPIC_COMPLETED, 1)
            CardCatalog.byId(topic.cardId)?.let { r += giveCard(it) }
        }
        r
    }

    suspend fun onPracticeSolved(topic: Topic?, subject: Subject, usedHint: Boolean, hard: Boolean): RewardOutcome = reward {
        var r = addXp(if (usedHint) XpRules.TASK_WITH_HINTS else XpRules.TASK_WITHOUT_HINTS, "Задача: ${topic?.title ?: subject.title}", subject)
        if (hard) {
            dao.upsert(current().let { it.copy(hardTasksSolved = it.hardTasksSolved + 1) })
            r += addCrystals(CrystalRules.HARD_TASK)
        }
        r + quest("${QuestEvents.TASK_SOLVED}:${subject.key}", 1)
    }

    suspend fun onExam(subject: Subject, grade: Int, correct: Int, won: Boolean, outcome: ExamOutcome): RewardOutcome = reward {
        var r = addXp(correct * XpRules.TEST_CORRECT_ANSWER, "Экзамен ${subject.title} $grade кл.", subject)
        if (won && outcome.firstWin) {
            dao.upsert(current().let { it.copy(bossesDefeated = it.bossesDefeated + 1) })
            r += addXp(XpRules.BOSS_DEFEATED, "Босс побеждён", subject)
            r += addCrystals(CrystalRules.BOSS_DEFEATED)
            CardCatalog.byId(Curriculum.boss(subject, grade).rewardCardId)?.let { r += giveCard(it) }
            r += RewardOutcome(message = NeuronLines.random(NeuronLines.bossWin))
        }
        outcome.newlyCompletedGrades.forEach { g ->
            r += RewardOutcome(message = if (g < 11) "🎉 $g класс завершён! Открыт ${g + 1} класс!" else "🏆 Ты прошёл всю программу 9–11 классов!")
        }
        r
    }

    suspend fun onBlitz(score: Int, subject: Subject?): RewardOutcome = reward {
        val p = current()
        if (score > p.blitzBest) dao.upsert(p.copy(blitzBest = score))
        addXp(score * XpRules.BLITZ_CORRECT, "Блиц", subject) + quest(QuestEvents.BLITZ_SCORE, score)
    }

    suspend fun onDuel(won: Boolean): RewardOutcome = reward {
        if (!won) return@reward addXp(5, "Дуэль: за участие", null)
        dao.upsert(current().let { it.copy(duelWins = it.duelWins + 1) })
        addXp(XpRules.DUEL_WIN, "Победа в дуэли", null) + addCrystals(CrystalRules.DUEL_WIN) + quest(QuestEvents.DUEL_WIN, 1)
    }

    suspend fun onChatMessage(): RewardOutcome = reward {
        dao.upsert(current().let { it.copy(chatMessages = it.chatMessages + 1) })
        quest(QuestEvents.CHAT_MESSAGE, 1)
    }

    suspend fun onSimulation(): RewardOutcome = reward {
        dao.upsert(current().let { it.copy(simulationsRun = it.simulationsRun + 1) })
        addXp(XpRules.SIMULATION, "Симуляция", null) + quest(QuestEvents.SIMULATION, 1)
    }

    suspend fun saveDailyProblem(p: com.neuron.academy.data.db.DailyProblemEntity) = dao.upsertDailyProblem(p)

    suspend fun onDailyProblemSolved(subject: Subject): RewardOutcome = reward {
        val dp = dao.dailyProblem(today()) ?: return@reward RewardOutcome()
        if (dp.solved) return@reward RewardOutcome()
        dao.upsertDailyProblem(dp.copy(solved = true))
        addXp(XpRules.DAILY_PROBLEM, "Задача дня", subject) + quest(QuestEvents.DAILY_PROBLEM, 1) +
            quest("${QuestEvents.TASK_SOLVED}:${subject.key}", 1)
    }

    suspend fun claimQuest(questId: String): RewardOutcome = reward {
        val def = DailyQuestGenerator.byId(questId) ?: return@reward RewardOutcome()
        val day = today()
        val key = "$day:$questId"
        val q = dao.questsFor(day).firstOrNull { it.key == key } ?: return@reward RewardOutcome()
        if (q.claimed || q.progress < def.target) return@reward RewardOutcome()
        dao.upsertQuest(q.copy(claimed = true))
        addXp(def.rewardXp, "Задание: ${def.title}", null) + addCrystals(def.rewardCrystals) +
            RewardOutcome(message = "Задание выполнено: ${def.title}")
    }

    // ======================== магазин ========================

    sealed interface PurchaseResult {
        data object Ok : PurchaseResult
        data object NotEnough : PurchaseResult
        data object AlreadyOwned : PurchaseResult
    }

    suspend fun buy(itemId: String): PurchaseResult = buyLocked(itemId)

    private suspend fun buyLocked(itemId: String): PurchaseResult {
        mutex.lock()
        try {
            return buyInternal(itemId)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun buyInternal(itemId: String): PurchaseResult {
        val item = ShopCatalog.byId(itemId) ?: return PurchaseResult.NotEnough
        val p = current()
        if (item.category != ShopCategory.CONSUMABLE && itemId in p.owned) return PurchaseResult.AlreadyOwned
        if (p.crystals < item.price) return PurchaseResult.NotEnough
        val updated = when (itemId) {
            ShopCatalog.HINTS_PACK -> p.copy(hints = p.hints + 3)
            ShopCatalog.STREAK_FREEZE -> p.copy(freezes = p.freezes + 1)
            else -> p.copy(ownedItems = (p.owned + itemId).joinToString(","))
        }.let { it.copy(crystals = it.crystals - item.price, itemsBought = it.itemsBought + 1) }
        dao.upsert(updated)
        val r = checkAchievementsLocked()
        if (!r.isEmpty) _rewards.tryEmit(r)
        return PurchaseResult.Ok
    }

    suspend fun equip(itemId: String) = mutex.withLock {
        val item = ShopCatalog.byId(itemId) ?: return@withLock
        val p = current()
        if (itemId !in p.owned) return@withLock
        dao.upsert(if (item.category == ShopCategory.SKIN) p.copy(skin = itemId) else p.copy(theme = itemId))
    }

    /** Тратит одну подсказку «50/50». */
    suspend fun useHint(): Boolean = mutex.withLock {
        val p = current()
        if (p.hints <= 0) {
            false
        } else {
            dao.upsert(p.copy(hints = p.hints - 1))
            true
        }
    }

    // ======================== внутреннее ========================

    private suspend fun reward(block: suspend () -> RewardOutcome): RewardOutcome {
        val result = mutex.withLock {
            val r = block()
            r + checkAchievementsLocked()
        }
        if (!result.isEmpty) _rewards.tryEmit(result)
        return result
    }

    private suspend fun addXp(amount: Int, reason: String, subject: Subject?): RewardOutcome {
        if (amount <= 0) return RewardOutcome()
        val p = current()
        val before = LevelSystem.levelFor(p.xp)
        val newXp = p.xp + amount
        val after = LevelSystem.levelFor(newXp)
        var crystals = 0
        if (after > before) crystals = CrystalRules.LEVEL_UP * (after - before)
        dao.upsert(p.copy(xp = newXp, crystals = p.crystals + crystals))
        dao.insertXp(XpEventEntity(amount = amount, reason = reason, subject = subject?.key, day = today()))
        return if (after > before) {
            val rankBefore = LevelSystem.rankFor(before)
            val rankAfter = LevelSystem.rankFor(after)
            RewardOutcome(
                xp = amount, crystals = crystals, newLevel = after,
                newRank = if (rankAfter != rankBefore) rankAfter else null,
                message = NeuronLines.random(NeuronLines.levelUp),
            )
        } else RewardOutcome(xp = amount)
    }

    private suspend fun addCrystals(amount: Int): RewardOutcome {
        val p = current()
        dao.upsert(p.copy(crystals = p.crystals + amount))
        return RewardOutcome(crystals = amount)
    }

    private suspend fun giveCard(card: CardDef): RewardOutcome {
        val existing = dao.getCard(card.id)
        dao.upsertCard(existing?.copy(count = existing.count + 1) ?: CardEntity(card.id))
        return if (existing == null) RewardOutcome(card = card) else RewardOutcome()
    }

    private suspend fun quest(eventKey: String, value: Int): RewardOutcome {
        val day = today()
        val defs = DailyQuestGenerator.questsFor(day).filter { it.eventKey == eventKey }
        if (defs.isEmpty()) return RewardOutcome()
        val saved = dao.questsFor(day)
        var r = RewardOutcome()
        defs.forEach { def ->
            val key = "$day:${def.id}"
            val old = saved.firstOrNull { it.key == key } ?: QuestEntity(key, day, def.id)
            val newProgress = if (def.isMax) maxOf(old.progress, value) else old.progress + value
            dao.upsertQuest(old.copy(progress = newProgress.coerceAtMost(def.target)))
            if (old.progress < def.target && newProgress >= def.target) {
                r += RewardOutcome(message = "✅ Задание «${def.title}» выполнено — забери награду!")
            }
        }
        return r
    }

    private suspend fun checkAchievementsLocked(): RewardOutcome {
        val p = current()
        val ls = progress.snapshot()
        val snap = PlayerSnapshot(
            level = LevelSystem.levelFor(p.xp),
            topicsCompleted = ls.completedTopicIds.size,
            completedTopicIds = ls.completedTopicIds,
            perfectTopicIds = ls.perfectTopicIds,
            perfectTests = p.perfectTests,
            streak = p.streak,
            hardTasksSolved = p.hardTasksSolved,
            bossesDefeated = p.bossesDefeated,
            cardsCollected = dao.cardCount(),
            blitzBest = p.blitzBest,
            duelWins = p.duelWins,
            chatMessages = p.chatMessages,
            simulationsRun = p.simulationsRun,
            gradesCompleted = ls.completedGrades,
            itemsBought = p.itemsBought,
        )
        val unlocked = dao.achievementIds().toSet()
        val fresh = AchievementCatalog.newlyUnlocked(snap, unlocked)
        if (fresh.isEmpty()) return RewardOutcome()
        fresh.forEach { dao.insertAchievement(AchievementEntity(it.id)) }
        val crystals = fresh.sumOf { it.rewardCrystals }
        val pp = current()
        dao.upsert(pp.copy(crystals = pp.crystals + crystals))
        return RewardOutcome(crystals = crystals, achievements = fresh)
    }

    /** Комбинированный поток для экрана коллекции. */
    val collection = combine(achievements, cards) { a, c -> a.map { it.id }.toSet() to c.associateBy { it.id } }
}
