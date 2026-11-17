package com.neuron.academy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TopicProgressEntity::class, TestResultEntity::class, ExamEntity::class, UnlockedGradeEntity::class,
        ChatMessageEntity::class, PlayerEntity::class, AchievementEntity::class, CardEntity::class,
        XpEventEntity::class, QuestEntity::class, DailyProblemEntity::class, KeyStatEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun playerDao(): PlayerDao
    abstract fun chatDao(): ChatDao
    abstract fun keyStatsDao(): KeyStatsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "neuron.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
