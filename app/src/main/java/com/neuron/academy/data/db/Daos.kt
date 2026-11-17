package com.neuron.academy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM topic_progress")
    fun observeTopics(): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress WHERE topicId = :id")
    suspend fun getTopic(id: String): TopicProgressEntity?

    @Query("SELECT * FROM topic_progress")
    suspend fun allTopics(): List<TopicProgressEntity>

    @Upsert
    suspend fun upsertTopic(e: TopicProgressEntity)

    @Query("SELECT * FROM exams")
    fun observeExams(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams")
    suspend fun allExams(): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getExam(id: String): ExamEntity?

    @Upsert
    suspend fun upsertExam(e: ExamEntity)

    @Query("SELECT * FROM unlocked_grades")
    fun observeUnlockedGrades(): Flow<List<UnlockedGradeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlockGrade(e: UnlockedGradeEntity)

    @Insert
    suspend fun insertResult(e: TestResultEntity)

    @Query("SELECT * FROM test_results WHERE timestamp >= :since ORDER BY timestamp")
    fun observeResultsSince(since: Long): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results ORDER BY timestamp")
    fun observeAllResults(): Flow<List<TestResultEntity>>
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM player WHERE id = 1")
    fun observe(): Flow<PlayerEntity?>

    @Query("SELECT * FROM player WHERE id = 1")
    suspend fun get(): PlayerEntity?

    @Upsert
    suspend fun upsert(p: PlayerEntity)

    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT id FROM achievements")
    suspend fun achievementIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(a: AchievementEntity)

    @Query("SELECT * FROM cards")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCard(id: String): CardEntity?

    @Upsert
    suspend fun upsertCard(c: CardEntity)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun cardCount(): Int

    @Insert
    suspend fun insertXp(e: XpEventEntity)

    @Query("SELECT * FROM xp_events WHERE day >= :fromDay ORDER BY day")
    fun observeXpSince(fromDay: Long): Flow<List<XpEventEntity>>

    @Query("SELECT * FROM daily_quests WHERE day = :day")
    fun observeQuests(day: Long): Flow<List<QuestEntity>>

    @Query("SELECT * FROM daily_quests WHERE day = :day")
    suspend fun questsFor(day: Long): List<QuestEntity>

    @Upsert
    suspend fun upsertQuest(q: QuestEntity)

    @Query("SELECT * FROM daily_problem WHERE day = :day")
    fun observeDailyProblem(day: Long): Flow<DailyProblemEntity?>

    @Query("SELECT * FROM daily_problem WHERE day = :day")
    suspend fun dailyProblem(day: Long): DailyProblemEntity?

    @Upsert
    suspend fun upsertDailyProblem(p: DailyProblemEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE thread = :thread ORDER BY id")
    fun observeThread(thread: String): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(m: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE thread = :thread")
    suspend fun clearThread(thread: String)
}

@Dao
interface KeyStatsDao {
    @Query("SELECT * FROM key_stats")
    fun observeAll(): Flow<List<KeyStatEntity>>

    @Query("SELECT * FROM key_stats WHERE keyId = :id")
    suspend fun get(id: String): KeyStatEntity?

    @Upsert
    suspend fun upsert(s: KeyStatEntity)
}
