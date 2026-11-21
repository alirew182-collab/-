package com.neuron.academy.data

import com.neuron.academy.content.Curriculum
import com.neuron.academy.content.Subject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Сколько тем пройдено по предмету в классе и побеждён ли финальный босс. */
data class SubjectGradeRestore(val subject: Subject, val grade: Int, val completed: Int, val bossBeaten: Boolean)

/** Что восстановить: прогресс по темам и данные игрока (null — не менять). */
data class RestorePlan(
    val items: List<SubjectGradeRestore>,
    val level: Int? = null,
    val crystals: Int? = null,
    val streak: Int? = null,
    /** Что распознать не удалось — показываем ученику, чтобы он поправил вручную. */
    val notes: List<String> = emptyList(),
) {
    companion object {
        fun empty() = RestorePlan(
            Curriculum.grades.flatMap { g -> Subject.entries.map { SubjectGradeRestore(it, g, 0, false) } },
        )
    }
}

/**
 * Разбирает ответ ИИ (JSON) в [RestorePlan] и сопоставляет названия тем с программой.
 * Темы в программе идут по порядку и открываются одна за другой, поэтому «дошёл до темы X»
 * означает: пройдены все темы до X включительно.
 */
object RestoreParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Инструкция для ИИ: какой JSON вернуть. */
    const val FORMAT = """Верни ТОЛЬКО JSON без пояснений:
{"items":[{"subject":"алгебра|физика|химия","grade":9,"last_topic":"название последней пройденной темы или null","topics_done":число или null,"all_done":true|false,"boss":true|false}],
"level":число или null,"crystals":число или null,"streak":число или null}
Правила: один элемент на каждую пару предмет+класс, о которой есть сведения. all_done=true, если предмет в этом классе пройден целиком. boss=true, если финальный босс класса побеждён.
Если ученик учится в 10 или 11 классе и не говорит о прошлых классах, считай прошлые классы пройденными целиком (all_done=true, boss=true).
Ничего не выдумывай: если чего-то нет в сведениях — пиши null."""

    fun parse(text: String): RestorePlan {
        val obj = extractObject(text) ?: error("ИИ не смог разобрать описание. Попробуй написать подробнее или заполни вручную")
        val base = RestorePlan.empty().items.associateBy { it.subject to it.grade }.toMutableMap()
        val notes = mutableListOf<String>()
        obj["items"]?.let { arr ->
            runCatching { arr.jsonArray }.getOrNull()?.forEach { el ->
                val o = runCatching { el.jsonObject }.getOrNull() ?: return@forEach
                val subject = subjectOf(o.str("subject")) ?: return@forEach
                val grade = o.int("grade")?.takeIf { it in Curriculum.grades } ?: return@forEach
                val topics = Curriculum.topicsFor(subject, grade)
                val all = o.bool("all_done") == true
                val boss = o.bool("boss") == true
                val last = o.str("last_topic").takeIf { it.isNotBlank() && it != "null" }
                val byTitle = last?.let { matchTopic(subject, grade, it) }
                val count = when {
                    all || boss -> topics.size
                    byTitle != null -> byTitle + 1
                    o.int("topics_done") != null -> o.int("topics_done")!!.coerceIn(0, topics.size)
                    else -> 0
                }
                if (last != null && byTitle == null && !all && !boss) {
                    notes += "${subject.title}, $grade класс: не нашёл тему «$last» — выбери вручную"
                }
                base[subject to grade] = SubjectGradeRestore(subject, grade, count, boss)
            }
        }
        return RestorePlan(
            items = base.values.sortedWith(compareBy({ it.grade }, { it.subject.ordinal })),
            level = obj.int("level")?.takeIf { it in 1..200 },
            crystals = obj.int("crystals")?.takeIf { it in 0..1_000_000 },
            streak = obj.int("streak")?.takeIf { it in 0..10_000 },
            notes = notes,
        )
    }

    /** Индекс темы с самым похожим названием или null, если ничего не похоже. */
    fun matchTopic(subject: Subject, grade: Int, title: String): Int? {
        val want = words(title)
        if (want.isEmpty()) return null
        val topics = Curriculum.topicsFor(subject, grade)
        val scored = topics.mapIndexed { i, t ->
            val have = words(t.title)
            val common = want.count { w -> have.any { h -> h.startsWith(w.take(5)) || w.startsWith(h.take(5)) } }
            i to common.toDouble() / maxOf(want.size, have.size)
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return best.first.takeIf { best.second >= 0.34 }
    }

    private fun words(s: String) = s.lowercase().replace('ё', 'е')
        .split(Regex("[^а-яa-z0-9]+")).filter { it.length >= 3 && it !in stopWords }

    private val stopWords = setOf("и", "или", "для", "при", "как", "что", "это", "его", "тема", "темы", "класс")

    fun subjectOf(s: String): Subject? {
        val v = s.lowercase()
        return when {
            v.startsWith("алг") || v.startsWith("мат") || v.startsWith("alg") -> Subject.ALGEBRA
            v.startsWith("физ") || v.startsWith("phy") -> Subject.PHYSICS
            v.startsWith("хим") || v.startsWith("che") -> Subject.CHEMISTRY
            else -> null
        }
    }

    private fun extractObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
    private fun JsonObject.int(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
    private fun JsonObject.bool(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
}
