package com.neuron.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {
    @Test
    fun levels() {
        assertEquals(1, LevelSystem.levelFor(0))
        assertEquals(2, LevelSystem.levelFor(50))
        assertEquals(Rank.NOVICE, LevelSystem.rankFor(1))
        assertEquals(Rank.GENIUS, LevelSystem.rankFor(20))
    }

    @Test
    fun streak() {
        assertEquals(4, StreakCalculator.checkIn(3, 10, 11, 0).streak)
        assertEquals(1, StreakCalculator.checkIn(3, 10, 13, 0).streak)
        val frozen = StreakCalculator.checkIn(3, 10, 12, 1)
        assertEquals(4, frozen.streak)
        assertEquals(0, frozen.freezesLeft)
    }

    @Test
    fun achievements() {
        val fresh = AchievementCatalog.newlyUnlocked(PlayerSnapshot(topicsCompleted = 1, streak = 7), emptySet())
        assertTrue(fresh.any { it.id == "first_topic" })
        assertTrue(fresh.any { it.id == "streak_7" })
    }

    @Test
    fun quests() {
        assertEquals(DailyQuestGenerator.questsFor(100), DailyQuestGenerator.questsFor(100))
        assertEquals(3, DailyQuestGenerator.questsFor(5).size)
    }
}
