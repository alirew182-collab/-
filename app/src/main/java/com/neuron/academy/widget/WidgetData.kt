package com.neuron.academy.widget

import android.content.Context
import com.neuron.academy.NeuronApp
import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.OfflineDailyProblems
import com.neuron.academy.content.Subject
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.screens.nextTopic
import com.neuron.gamification.DailyQuestGenerator
import com.neuron.gamification.LevelSystem
import java.time.LocalDate

/** Снимок данных для виджетов — читается напрямую из Room. */
data class WidgetData(
    val level: Int,
    val rank: String,
    val xp: Int,
    val xpToNext: Int,
    val levelProgress: Float,
    val crystals: Int,
    val streak: Int,
    val activeToday: Boolean,
    val questsDone: Int,
    val questsTotal: Int,
    val grade: Int,
    val gradeProgress: Float,
    val nextTopicId: String?,
    val nextTopicTitle: String?,
    val nextTopicSubject: String?,
    val problemSubject: String,
    val problemText: String,
    val problemSolved: Boolean,
    val skin: String,
)

object WidgetRepository {
    suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as NeuronApp).container
        val dao = c.db.playerDao()
        val p = dao.get() ?: PlayerEntity()
        val info = LevelSystem.info(p.xp)
        val ls = c.progress.snapshot()
        val today = LocalDate.now().toEpochDay()

        val defs = DailyQuestGenerator.questsFor(today)
        val saved = dao.questsFor(today)
        val done = defs.count { d -> (saved.firstOrNull { it.questId == d.id }?.progress ?: 0) >= d.target }

        // серия «сгорает», если пропущено больше дней, чем есть заморозок
        val gap = today - p.lastActiveDay
        val streak = if (p.lastActiveDay == 0L || gap > 1 + p.freezes) 0 else p.streak

        val dp = dao.dailyProblem(today)
        val (subj, text, solved) = if (dp != null) {
            Triple(Subject.byKey(dp.subject).title, dp.problem, dp.solved)
        } else {
            val o = OfflineDailyProblems.forDay(today)
            Triple(o.subject.title, o.problem, false)
        }
        val next = ls.nextTopic()
        return WidgetData(
            level = info.level, rank = info.rank.title, xp = p.xp, xpToNext = info.xpToNext, levelProgress = info.progress,
            crystals = p.crystals, streak = streak, activeToday = p.lastActiveDay == today,
            questsDone = done, questsTotal = defs.size,
            grade = ls.currentGrade, gradeProgress = ls.grade(ls.currentGrade).fraction,
            nextTopicId = next?.topic?.id, nextTopicTitle = next?.topic?.title,
            nextTopicSubject = next?.topic?.let { "${it.subject.title} · ${it.order}/${Curriculum.topicsFor(it.subject, it.grade).size}" },
            problemSubject = subj, problemText = plain(text), problemSolved = solved,
            skin = p.skin,
        )
    }

    /** Убирает LaTeX-разметку: в виджете нельзя показать KaTeX. */
    fun plain(s: String): String {
        var t = s
        t = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""").replace(t) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        t = Regex("""\\sqrt\{([^{}]*)\}""").replace(t) { "√(${it.groupValues[1]})" }
        val map = mapOf(
            "\\(" to "", "\\)" to "", "\\[" to "", "\\]" to "", "\\cdot" to "·", "\\times" to "×", "\\pi" to "π",
            "\\alpha" to "α", "\\Delta" to "Δ", "\\le" to "≤", "\\ge" to "≥", "\\ne" to "≠", "\\pm" to "±",
            "^2" to "²", "^3" to "³", "\\text" to "", "\\," to " ", "**" to "", "$" to "",
        )
        map.forEach { (k, v) -> t = t.replace(k, v) }
        return t.replace("\\", "").replace("{", "").replace("}", "").replace(Regex("\\s+"), " ").trim()
    }
}
