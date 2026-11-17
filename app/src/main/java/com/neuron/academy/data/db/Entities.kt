package com.neuron.academy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey val topicId: String,
    val theoryRead: Boolean = false,
    val bestScore: Float = 0f,
    val completed: Boolean = false,
    val perfect: Boolean = false,
    val attempts: Int = 0,
    val completedAt: Long? = null,
    /** Когда тему последний раз повторяли (интервальное повторение). */
    val lastReviewedAt: Long? = null,
)

/** Результаты тестов, экзаменов, блица и практики — основа графиков и статистики. */
@Entity(tableName = "test_results")
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String?,
    val subject: String,
    val grade: Int,
    val kind: String, // TOPIC, EXAM, BLITZ, PRACTICE, DUEL
    val correct: Int,
    val total: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Итоговые экзамены (битвы с боссами). id = "${subject}_${grade}" */
@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val grade: Int,
    val passed: Boolean,
    val bestScore: Float,
    val attempts: Int,
    val passedAt: Long?,
)

/** Открытые классы фиксируются, чтобы прогресс не «откатывался» при смене порога. */
@Entity(tableName = "unlocked_grades")
data class UnlockedGradeEntity(
    @PrimaryKey val grade: Int,
    val unlockedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thread: String,
    val role: String, // user / assistant / error
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "player")
data class PlayerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val xp: Int = 0,
    val crystals: Int = 50,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: Long = 0,
    val freezes: Int = 0,
    val hints: Int = 3,
    val ownedItems: String = "skin_classic,theme_default",
    val skin: String = "skin_classic",
    val theme: String = "theme_default",
    val hardTasksSolved: Int = 0,
    val blitzBest: Int = 0,
    val duelWins: Int = 0,
    val chatMessages: Int = 0,
    val perfectTests: Int = 0,
    val simulationsRun: Int = 0,
    val bossesDefeated: Int = 0,
    val itemsBought: Int = 0,
    val hearts: Int = 5,
    val heartsUpdatedAt: Long = 0,
    val guardiansDefeated: Int = 0,
    val raidsWon: Int = 0,
    val reviewsDone: Int = 0,
) {
    val owned: Set<String> get() = ownedItems.split(',').filter { it.isNotBlank() }.toSet()
}

@Entity(tableName = "achievements")
data class AchievementEntity(@PrimaryKey val id: String, val unlockedAt: Long = System.currentTimeMillis())

@Entity(tableName = "cards")
data class CardEntity(@PrimaryKey val id: String, val obtainedAt: Long = System.currentTimeMillis(), val count: Int = 1)

@Entity(tableName = "xp_events")
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val reason: String,
    val subject: String?,
    val day: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "daily_quests")
data class QuestEntity(
    @PrimaryKey val key: String, // "$day:$questId"
    val day: Long,
    val questId: String,
    val progress: Int = 0,
    val claimed: Boolean = false,
)

/** Кэш «Задачи дня». */
@Entity(tableName = "daily_problem")
data class DailyProblemEntity(
    @PrimaryKey val day: Long,
    val subject: String,
    val problem: String,
    val answer: String,
    val hint: String,
    val solution: String,
    val solved: Boolean = false,
)

@Entity(tableName = "key_stats")
data class KeyStatEntity(
    @PrimaryKey val keyId: String,
    val requests: Int = 0,
    val errors: Int = 0,
    val lastUsed: Long = 0,
    val lastError: String? = null,
    val lastSuccess: Boolean? = null,
    val lastLatencyMs: Long = 0,
)
