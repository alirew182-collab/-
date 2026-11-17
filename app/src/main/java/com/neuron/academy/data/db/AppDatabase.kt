package com.neuron.academy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TopicProgressEntity::class, TestResultEntity::class, ExamEntity::class, UnlockedGradeEntity::class,
        ChatMessageEntity::class, PlayerEntity::class, AchievementEntity::class, CardEntity::class,
        XpEventEntity::class, QuestEntity::class, DailyProblemEntity::class, KeyStatEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun playerDao(): PlayerDao
    abstract fun chatDao(): ChatDao
    abstract fun keyStatsDao(): KeyStatsDao

    companion object {
        /** v2: сердца, стражи, рейды и повторение. Прогресс ученика сохраняется. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player ADD COLUMN hearts INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE player ADD COLUMN heartsUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE player ADD COLUMN guardiansDefeated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE player ADD COLUMN raidsWon INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE player ADD COLUMN reviewsDone INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE topic_progress ADD COLUMN lastReviewedAt INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "neuron.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
