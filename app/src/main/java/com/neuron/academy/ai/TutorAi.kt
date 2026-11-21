package com.neuron.academy.ai

import com.neuron.academy.content.Question
import com.neuron.academy.content.Subject
import com.neuron.academy.content.Topic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ExplainStyle(val title: String, val emoji: String, val instruction: String) {
    SIMPLER("Проще", "🧸", "Объясни максимально просто, как другу, без сложных терминов, короткими шагами."),
    ANALOGY("Аналогия", "🔁", "Объясни через яркую аналогию из жизни, игр (например, Minecraft), спорта или космоса."),
    STORY("История", "📖", "Объясни через короткую увлекательную историю или сюжет с героем, который сталкивается с этой темой."),
    MEME("Мем", "😂", "Объясни через смешной мем-формат (опиши мем словами), но сохрани точность."),
}

data class GeneratedTask(val problem: String, val answer: String, val hint: String, val solution: String)
data class CheckResult(val correct: Boolean, val feedback: String)

/** Высокоуровневые функции ИИ-репетитора «Нейрон». */
class TutorAi(private val router: AiRouter) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun systemPrompt(grade: Int, completedTopics: List<String>, extra: String = ""): String {
        // последние пройденные темы: весь список из сотни тем только удлиняет запрос
        val done = if (completedTopics.isEmpty()) "пока ничего — он только начинает" else completedTopics.takeLast(30).joinToString(", ")
        return buildString {
            append("Ты школьный учитель и помощник «Нейрон» для ученика $grade класса. ")
            append("Ученик уже прошёл: $done. ")
            append("Объясняй понятно, пошагово, на русском, дружелюбно. ")
            append("Примеры бери из жизни, игр, спорта и космоса, когда они действительно проясняют тему. ")
            append("При ошибке не ругай, а покажи, в каком шаге она.\n\n")
            append("Ты — робот-помощник, изредка можешь пошутить про свои микросхемы. ")
            // правила «без ИИ-воды» (по мотивам no-ai-slop): так ответы читаются как от живого учителя
            append("Пиши как живой учитель, а не как чат-бот: ")
            append("начинай сразу с сути, без «Отличный вопрос», «Давай разберёмся», «Конечно» и пересказа вопроса; ")
            append("не заканчивай выводом, который повторяет сказанное, и не добавляй «Надеюсь, стало понятно»; ")
            append("никаких эмодзи и восклицательных знаков; ")
            append("не пиши «Важно отметить», «Стоит подчеркнуть», «ключевую роль», «в современном мире», «не просто X, а Y»; ")
            append("вместо общих слов — конкретные числа, шаги и примеры; ")
            append("жирным выделяй только то, что надо запомнить, не больше одного-двух мест на ответ. ")
            append("Если для темы нужны знания из прошлых классов — коротко напомни их. ")
            append("Формат: короткие абзацы, списки, **жирный** для главного. ")
            append("Все формулы пиши в LaTeX: внутри строки \\( ... \\), отдельной строкой \\[ ... \\]. ")
            append("Не используй таблицы и HTML.")
            if (extra.isNotBlank()) append("\n\n").append(extra)
        }
    }

    suspend fun chat(
        grade: Int,
        completed: List<String>,
        history: List<AiMessage>,
        topic: Topic? = null,
    ): AiResult {
        val extra = topic?.let { "Сейчас ученик изучает тему «${it.title}» (${it.subject.title}, ${it.grade} класс)." } ?: ""
        return router.complete(
            AiFeature.CHAT,
            AiRequest(systemPrompt(grade, completed, extra), history.takeLast(20), maxTokens = 1500),
        )
    }

    suspend fun explain(topic: Topic, style: ExplainStyle, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Объясни тему «${topic.title}» (${topic.subject.title}, ${topic.grade} класс) по-другому. ${style.instruction} " +
                "Ключевая теория для опоры:\n${topic.theory.take(1500)}",
        )

    /** Подробное объяснение «с нуля», понятное любому. */
    suspend fun deepExplain(topic: Topic, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Объясни тему «${topic.title}» (${topic.subject.title}, ${topic.grade} класс) максимально подробно и просто — так, чтобы понял человек, который ничего об этом не знает. " +
                "Структура: 1) жизненный пример, с которого всё начинается; 2) главные понятия простыми словами; 3) формулы с объяснением каждой буквы; " +
                "4) пошаговый алгоритм решения задач; 5) подробно разобранный пример; 6) три типичные ошибки и как их избежать; 7) вопрос для самопроверки с ответом. " +
                "Опорная теория:\n${topic.theory.take(1500)}",
            maxTokens = 2500,
        )

    /** Другие способы решения той же задачи. */
    suspend fun alternativeMethods(topic: Topic, problem: String, knownSolution: String, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Тема «${topic.title}». Задача: $problem\nУже известное решение: $knownSolution\n\n" +
                "Покажи 2–3 ДРУГИХ способа решения этой же задачи. Для каждого способа: заголовок «### Способ N. Название», идея в одном предложении, " +
                "затем решение по шагам. В конце — «### Какой способ выбрать» с коротким сравнением. Ответ во всех способах должен совпасть.",
            maxTokens = 2500,
        )

    /** Новая задача, сразу решённая несколькими способами. */
    suspend fun multiMethodProblem(topic: Topic, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.TASKS, grade, completed,
            "Придумай задачу по теме «${topic.title}» (${topic.subject.title}, ${topic.grade} класс) среднего уровня, которую можно решить несколькими способами. " +
                "Формат: «### Задача», условие; затем «### Способ 1. …», «### Способ 2. …» (и, если есть, «### Способ 3. …») с пошаговым решением; " +
                "затем «### Ответ» и «### Какой способ выбрать» — когда какой удобнее.",
            temperature = 0.9,
            maxTokens = 2500,
        )

    suspend fun whyNeeded(topic: Topic, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Зачем тема «${topic.title}» (${topic.subject.title}) нужна в реальной жизни? Приведи 4–5 ярких примеров: " +
                "профессии, техника, игры, спорт, космос. Коротко и вдохновляюще.",
        )

    suspend fun interestingFact(topic: Topic, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Расскажи один удивительный, малоизвестный, но достоверный факт, связанный с темой «${topic.title}». 3–5 предложений.",
            temperature = 1.0,
        )

    suspend fun prerequisites(topic: Topic, prereqs: List<Topic>, grade: Int, completed: List<String>): String =
        ask(
            AiFeature.EXPLAIN, grade, completed,
            "Перед темой «${topic.title}» напомни ученику, что нужно знать из прошлых тем: " +
                prereqs.joinToString { "«${it.title}» (${it.grade} кл.)" } +
                ". Дай короткую шпаргалку: главные формулы и правила, по 2–3 пункта на тему, и объясни, как они пригодятся.",
        )

    suspend fun generateTask(topic: Topic, difficulty: Difficulty, grade: Int, completed: List<String>): GeneratedTask {
        val text = ask(
            AiFeature.TASKS, grade, completed,
            "Придумай одну ${difficulty.title.lowercase()} задачу по теме «${topic.title}» (${topic.subject.title}, ${topic.grade} класс). " +
                "Сюжет задачи — из жизни, игр, спорта или космоса. Ответ должен быть числом (или несколькими числами через «;»). " +
                "Верни ТОЛЬКО JSON без пояснений: {\"problem\": \"условие\", \"answer\": \"ответ\", \"hint\": \"подсказка\", \"solution\": \"пошаговое решение\"}",
            temperature = 0.9,
        )
        val obj = extractObject(text) ?: throw AiException(AiErrorKind.PARSE, null, "ИИ вернул задачу в неожиданном формате")
        return GeneratedTask(
            problem = obj.str("problem"),
            answer = obj.str("answer"),
            hint = obj.str("hint"),
            solution = obj.str("solution"),
        )
    }

    suspend fun checkSolution(
        topicTitle: String,
        problem: String,
        studentSolution: String,
        expectedAnswer: String?,
        grade: Int,
        completed: List<String>,
    ): CheckResult {
        val text = ask(
            AiFeature.CHECK, grade, completed,
            "Проверь решение ученика по теме «$topicTitle».\nЗадача: $problem\n" +
                (expectedAnswer?.let { "Правильный ответ: $it\n" } ?: "") +
                "Решение ученика: $studentSolution\n\n" +
                "Первой строкой напиши ровно ВЕРНО или НЕВЕРНО. Затем разбери решение: что сделано хорошо, где ошибка (если есть), " +
                "и как её исправить. Не ругай, подсказывай.",
            temperature = 0.3,
        )
        val first = text.trim().lineSequence().firstOrNull().orEmpty().uppercase()
        val correct = first.contains("ВЕРНО") && !first.contains("НЕВЕРНО")
        return CheckResult(correct, text.trim().lines().drop(1).joinToString("\n").trim().ifBlank { text })
    }

    suspend fun generateTest(subject: Subject, grade: Int, topics: List<Topic>, count: Int, completed: List<String>, exam: Boolean = false): List<Question> {
        val names = topics.joinToString { "«${it.title}»" }
        val text = router.complete(
            AiFeature.TESTS,
            AiRequest(
                systemPrompt(grade, completed),
                listOf(
                    AiMessage(
                        AiMessage.Role.USER,
                        "Составь ${if (exam) "итоговый экзамен" else "тест"} из $count вопросов по предмету ${subject.title}, ${grade} класс, темы: $names. " +
                            "У каждого вопроса 4 варианта ответа, ровно один верный. Формулы — в LaTeX \\( ... \\). " +
                            "Верни ТОЛЬКО JSON-массив без пояснений: " +
                            "[{\"q\": \"вопрос\", \"options\": [\"a\",\"b\",\"c\",\"d\"], \"correct\": 0, \"explanation\": \"почему\"}]",
                    ),
                ),
                maxTokens = 3000,
                temperature = 0.8,
            ),
        ).text
        val arr = extractArray(text) ?: throw AiException(AiErrorKind.PARSE, null, "ИИ вернул тест в неожиданном формате")
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val options = o["options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return@mapNotNull null
            val correct = o["correct"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (options.size < 2 || correct !in options.indices) return@mapNotNull null
            Question(o.str("q"), options, correct, o.str("explanation"))
        }.ifEmpty { throw AiException(AiErrorKind.PARSE, null, "В тесте нет вопросов") }
    }

    suspend fun dailyProblem(subject: Subject, grade: Int, completed: List<String>): GeneratedTask {
        val text = ask(
            AiFeature.TASKS, grade, completed,
            "Придумай «Задачу дня» по предмету ${subject.title} для $grade класса: интересную, с неожиданным сюжетом " +
                "(космос, спорт, игры, супергерои), решаемую за 5 минут, с числовым ответом. " +
                "Верни ТОЛЬКО JSON: {\"problem\": \"...\", \"answer\": \"число\", \"hint\": \"...\", \"solution\": \"...\"}",
            temperature = 1.0,
        )
        val obj = extractObject(text) ?: throw AiException(AiErrorKind.PARSE, null, "Неожиданный формат")
        return GeneratedTask(obj.str("problem"), obj.str("answer"), obj.str("hint"), obj.str("solution"))
    }

    /** Для «Дуэли»: ИИ решает задачу и возвращает только ответ. */
    suspend fun solveQuick(problem: String, options: List<String>): AiResult =
        router.complete(
            AiFeature.DUEL,
            AiRequest(
                "Ты соревнуешься в скорости решения школьных задач. Отвечай только номером верного варианта (1-${options.size}).",
                listOf(
                    AiMessage(
                        AiMessage.Role.USER,
                        problem + "\n" + options.mapIndexed { i, s -> "${i + 1}) $s" }.joinToString("\n"),
                    ),
                ),
                maxTokens = 20,
                temperature = 0.0,
            ),
        )

    private suspend fun ask(feature: AiFeature, grade: Int, completed: List<String>, prompt: String, temperature: Double = 0.7, maxTokens: Int = 1500): String =
        router.complete(
            feature,
            AiRequest(systemPrompt(grade, completed), listOf(AiMessage(AiMessage.Role.USER, prompt)), maxTokens = maxTokens, temperature = temperature),
        ).text

    private fun JsonObject.str(key: String) = this[key]?.let { el ->
        try { el.jsonPrimitive.contentOrNull } catch (e: Exception) { el.toString() }
    }.orEmpty()

    private fun extractObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject } catch (e: Exception) { null }
    }

    private fun extractArray(text: String): JsonArray? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return try { json.parseToJsonElement(text.substring(start, end + 1)).jsonArray } catch (e: Exception) { null }
    }
}

enum class Difficulty(val title: String) { EASY("Лёгкая"), MEDIUM("Средняя"), HARD("Сложная") }
