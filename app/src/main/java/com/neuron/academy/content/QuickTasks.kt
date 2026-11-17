package com.neuron.academy.content

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Быстрая задача с вариантами ответа — для Блица и Дуэли (генерируется офлайн). */
data class QuickTask(
    val subject: Subject,
    val text: String,
    val options: List<String>,
    val correct: Int,
)

object QuickTaskGenerator {
    private val rnd = Random(System.nanoTime())

    fun next(subject: Subject? = null, hard: Boolean = false): QuickTask {
        val s = subject ?: Subject.entries.random(rnd)
        return when (s) {
            Subject.ALGEBRA -> algebra(hard)
            Subject.PHYSICS -> physics(hard)
            Subject.CHEMISTRY -> chemistry()
        }
    }

    private fun numeric(subject: Subject, text: String, answer: Double): QuickTask {
        val fmt: (Double) -> String = { v ->
            if (abs(v - v.roundToInt()) < 1e-9) v.roundToInt().toString()
            else "%.2f".format(v).trimEnd('0').trimEnd(',', '.')
        }
        val opts = mutableSetOf(fmt(answer))
        var guard = 0
        while (opts.size < 4 && guard++ < 50) {
            val delta = when (rnd.nextInt(4)) {
                0 -> answer + (rnd.nextInt(1, 6))
                1 -> answer - (rnd.nextInt(1, 6))
                2 -> answer * 2
                else -> if (answer != 0.0) answer / 2 else answer + 10
            }
            opts += fmt(delta)
        }
        val list = opts.toList().shuffled(rnd)
        return QuickTask(subject, text, list, list.indexOf(fmt(answer)))
    }

    private fun choice(subject: Subject, text: String, right: String, vararg wrong: String): QuickTask {
        val list = (listOf(right) + wrong.toList().shuffled(rnd).take(3)).shuffled(rnd)
        return QuickTask(subject, text, list, list.indexOf(right))
    }

    private fun algebra(hard: Boolean): QuickTask = when (rnd.nextInt(if (hard) 8 else 6)) {
        0 -> { val a = rnd.nextInt(3, 13); val b = rnd.nextInt(3, 13); numeric(Subject.ALGEBRA, "$a × $b = ?", (a * b).toDouble()) }
        1 -> { val x = rnd.nextInt(-9, 10); val a = rnd.nextInt(2, 7); val b = rnd.nextInt(-10, 11); numeric(Subject.ALGEBRA, "Реши: ${a}x ${sign(b)} = ${a * x + b}", x.toDouble()) }
        2 -> { val x1 = rnd.nextInt(-5, 6); val x2 = rnd.nextInt(-5, 6); val b = -(x1 + x2); val c = x1 * x2
            numeric(Subject.ALGEBRA, "Сумма корней x² ${sign(b)}x ${sign(c)} = 0", (x1 + x2).toDouble()) }
        3 -> { val a1 = rnd.nextInt(1, 10); val d = rnd.nextInt(2, 6); val n = rnd.nextInt(5, 12)
            numeric(Subject.ALGEBRA, "a₁ = $a1, d = $d. Найди a${sub(n)}", (a1 + (n - 1) * d).toDouble()) }
        4 -> { val p = rnd.nextInt(2, 11); numeric(Subject.ALGEBRA, "log₂ ${1 shl p} = ?", p.toDouble()) }
        5 -> choice(Subject.ALGEBRA, "sin 30° = ?", "1/2", "√3/2", "1", "√2/2", "0")
        6 -> { val n = rnd.nextInt(2, 6); val x = rnd.nextInt(1, 4); numeric(Subject.ALGEBRA, "f(x) = x${sup(n)}. Найди f′($x)", (n * pow(x, n - 1)).toDouble()) }
        else -> { val b = rnd.nextInt(1, 6) * 2; numeric(Subject.ALGEBRA, "∫ x dx от 0 до $b = ?", (b * b / 2).toDouble()) }
    }

    private fun physics(hard: Boolean): QuickTask = when (rnd.nextInt(if (hard) 8 else 6)) {
        0 -> { val v = rnd.nextInt(2, 30); val t = rnd.nextInt(2, 20); numeric(Subject.PHYSICS, "v = $v м/с, t = $t с. Путь s = ? (м)", (v * t).toDouble()) }
        1 -> { val m = rnd.nextInt(1, 20); val a = rnd.nextInt(1, 10); numeric(Subject.PHYSICS, "m = $m кг, a = $a м/с². F = ? (Н)", (m * a).toDouble()) }
        2 -> { val m = rnd.nextInt(1, 10); val v = rnd.nextInt(1, 20); numeric(Subject.PHYSICS, "m = $m кг, v = $v м/с. Импульс p = ?", (m * v).toDouble()) }
        3 -> { val u = rnd.nextInt(1, 13) * 12; val r = listOf(2, 3, 4, 6, 12).random(rnd); numeric(Subject.PHYSICS, "U = $u В, R = $r Ом. I = ? (А)", (u / r).toDouble()) }
        4 -> { val m = rnd.nextInt(1, 50); numeric(Subject.PHYSICS, "Сила тяжести на тело $m кг (g = 10), Н", (m * 10).toDouble()) }
        5 -> choice(Subject.PHYSICS, "Единица силы:", "Ньютон", "Джоуль", "Ватт", "Паскаль", "Ампер")
        6 -> { val m = rnd.nextInt(1, 6) * 2; val v = rnd.nextInt(1, 10); numeric(Subject.PHYSICS, "m = $m кг, v = $v м/с. Eₖ = ? (Дж)", (m * v * v / 2).toDouble()) }
        else -> { val f = listOf(0.1, 0.2, 0.25, 0.5, 1.0).random(rnd); numeric(Subject.PHYSICS, "F = ${f.toString().replace('.', ',')} м. Оптическая сила D = ? (дптр)", 1 / f) }
    }

    private val elements = listOf("H" to 1, "He" to 2, "Li" to 3, "C" to 6, "N" to 7, "O" to 8, "F" to 9, "Na" to 11, "Mg" to 12, "Al" to 13, "S" to 16, "Cl" to 17, "K" to 19, "Ca" to 20, "Fe" to 26, "Cu" to 29)
    private val molar = listOf("H₂O" to 18, "CO₂" to 44, "CH₄" to 16, "NH₃" to 17, "O₂" to 32, "NaOH" to 40, "H₂SO₄" to 98, "CaCO₃" to 100, "C₂H₅OH" to 46, "N₂" to 28, "HNO₃" to 63, "MgO" to 40)

    private fun chemistry(): QuickTask = when (rnd.nextInt(5)) {
        0 -> { val (s, z) = elements.random(rnd); numeric(Subject.CHEMISTRY, "Сколько протонов в атоме $s?", z.toDouble()) }
        1 -> { val (f, m) = molar.random(rnd); numeric(Subject.CHEMISTRY, "Молярная масса $f (г/моль)", m.toDouble()) }
        2 -> choice(Subject.CHEMISTRY, "Какая связь в NaCl?", "Ионная", "Ковалентная полярная", "Металлическая", "Ковалентная неполярная")
        3 -> choice(Subject.CHEMISTRY, "Что вытеснит водород из HCl?", listOf("Zn", "Mg", "Fe", "Al").random(rnd), "Cu", "Ag", "Au", "Hg")
        else -> { val n = rnd.nextInt(1, 9); numeric(Subject.CHEMISTRY, "Число атомов H в алкане с ${n} атомами C", (2 * n + 2).toDouble()) }
    }

    private fun sign(v: Int) = if (v >= 0) "+ $v" else "− ${-v}"
    private fun pow(b: Int, e: Int): Int { var r = 1; repeat(e) { r *= b }; return r }
    private fun sub(n: Int) = n.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")
    private fun sup(n: Int) = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
}

/** Офлайн-«Задачи дня» на случай, если ключей ИИ нет. */
object OfflineDailyProblems {
    data class P(val subject: Subject, val problem: String, val answer: String, val hint: String, val solution: String)

    val list = listOf(
        P(Subject.PHYSICS, "Марсоход проехал 300 м за 10 минут. Какова его средняя скорость в см/с?", "50", "Переведи метры в сантиметры, а минуты в секунды.", "30 000 см / 600 с = 50 см/с."),
        P(Subject.ALGEBRA, "В игре урон растёт на 15% за уровень. Начальный урон 100. Какой урон на 3-м уровне (после двух повышений)? Округли до целых.", "132", "Это геометрическая прогрессия с q = 1,15.", "100 · 1,15² = 132,25 ≈ 132."),
        P(Subject.CHEMISTRY, "Космонавт выдыхает около 1 кг CO₂ в сутки. Сколько это моль? (M = 44 г/моль, округли до целых)", "23", "ν = m / M, не забудь перевести кг в граммы.", "1000 / 44 ≈ 22,7 ≈ 23 моль."),
        P(Subject.PHYSICS, "Футболист бьёт по мячу 0,43 кг, и тот вылетает со скоростью 30 м/с. Какова кинетическая энергия мяча (Дж)? Округли до целых.", "194", "Eₖ = mv²/2", "0,43 · 900 / 2 = 193,5 ≈ 194 Дж."),
        P(Subject.ALGEBRA, "Скейтер съезжает по рампе-параболе y = x² − 4x + 7. На какой минимальной высоте он окажется?", "3", "Найди вершину параболы.", "x₀ = 2, y = 4 − 8 + 7 = 3."),
        P(Subject.PHYSICS, "На Луне g ≈ 1,6 м/с². С какой высоты (м) нужно уронить камень, чтобы он падал 5 секунд?", "20", "h = gt²/2", "1,6 · 25 / 2 = 20 м."),
        P(Subject.CHEMISTRY, "Сколько атомов водорода в 3 молекулах этанола C₂H₅OH?", "18", "Посчитай все H в одной молекуле.", "6 · 3 = 18."),
        P(Subject.ALGEBRA, "Видео набирает просмотры, удваиваясь каждый день. В первый день 500 просмотров. Через сколько дней (считая первый) будет 16 000?", "6", "500 · 2ⁿ⁻¹ = 16 000.", "2ⁿ⁻¹ = 32, n = 6."),
        P(Subject.PHYSICS, "Электросамокат: аккумулятор 36 В, ток 10 А. Какова мощность мотора (Вт)?", "360", "P = UI", "36 · 10 = 360 Вт."),
        P(Subject.CHEMISTRY, "Сколько граммов соли в 500 г физраствора с массовой долей 0,9%?", "4.5", "m = w · m(раствора)", "0,009 · 500 = 4,5 г."),
    )

    fun forDay(day: Long): P = list[Math.floorMod(day, list.size.toLong()).toInt()]
}

/** Реакции для симуляции «Смешивание веществ». */
data class Reagent(val id: String, val name: String, val formula: String, val color: Long)

enum class ReactionEffect(val title: String) { NONE("Без видимых изменений"), PRECIPITATE("Осадок"), GAS("Газ"), COLOR("Изменение цвета"), HEAT("Выделение тепла"), METAL("Выделение металла") }

data class Reaction(val a: String, val b: String, val equation: String, val effect: ReactionEffect, val resultColor: Long, val particleColor: Long, val comment: String)

object ChemLab {
    val reagents = listOf(
        Reagent("hcl", "Соляная кислота", "HCl", 0x33E0F7FA),
        Reagent("naoh", "Гидроксид натрия", "NaOH", 0x33E8EAF6),
        Reagent("cuso4", "Медный купорос", "CuSO₄", 0xCC29B6F6),
        Reagent("agno3", "Нитрат серебра", "AgNO₃", 0x33F5F5F5),
        Reagent("nacl", "Поваренная соль", "NaCl", 0x33FFFFFF),
        Reagent("na2co3", "Сода", "Na₂CO₃", 0x33FFF8E1),
        Reagent("zn", "Цинк", "Zn", 0xFF9E9E9E),
        Reagent("fe", "Железо (гвоздь)", "Fe", 0xFF616161),
        Reagent("bacl2", "Хлорид бария", "BaCl₂", 0x33F3E5F5),
        Reagent("h2so4", "Серная кислота", "H₂SO₄", 0x33FFFDE7),
        Reagent("fecl3", "Хлорид железа(III)", "FeCl₃", 0xCCFFB300),
        Reagent("phenol", "Фенолфталеин", "Ф-ф", 0x22FFFFFF),
        Reagent("ki", "Иодид калия", "KI", 0x33FFFFFF),
        Reagent("pbno3", "Нитрат свинца", "Pb(NO₃)₂", 0x33FFFFFF),
    )

    private val reactions = listOf(
        Reaction("hcl", "naoh", "HCl + NaOH → NaCl + H₂O", ReactionEffect.HEAT, 0x33FFFFFF, 0xFFFF7043, "Нейтрализация: кислота и щёлочь «гасят» друг друга, раствор слегка нагревается."),
        Reaction("cuso4", "naoh", "CuSO₄ + 2NaOH → Cu(OH)₂↓ + Na₂SO₄", ReactionEffect.PRECIPITATE, 0x6681D4FA, 0xFF1E88E5, "Выпадает голубой студенистый осадок гидроксида меди(II)."),
        Reaction("agno3", "nacl", "AgNO₃ + NaCl → AgCl↓ + NaNO₃", ReactionEffect.PRECIPITATE, 0x55FFFFFF, 0xFFFFFFFF, "Белый творожистый осадок хлорида серебра — качественная реакция на Cl⁻."),
        Reaction("agno3", "hcl", "AgNO₃ + HCl → AgCl↓ + HNO₃", ReactionEffect.PRECIPITATE, 0x55FFFFFF, 0xFFFFFFFF, "Белый осадок AgCl — так находят хлорид-ионы."),
        Reaction("na2co3", "hcl", "Na₂CO₃ + 2HCl → 2NaCl + H₂O + CO₂↑", ReactionEffect.GAS, 0x33FFFFFF, 0xFFE0F7FA, "Бурное «вскипание» — выделяется углекислый газ."),
        Reaction("na2co3", "h2so4", "Na₂CO₃ + H₂SO₄ → Na₂SO₄ + H₂O + CO₂↑", ReactionEffect.GAS, 0x33FFFFFF, 0xFFE0F7FA, "Пузыри CO₂ — карбонаты всегда «шипят» с кислотами."),
        Reaction("zn", "hcl", "Zn + 2HCl → ZnCl₂ + H₂↑", ReactionEffect.GAS, 0x33FFFFFF, 0xFFFFFFFF, "Цинк растворяется, выделяется водород. Поднеси спичку — услышишь «хлопок»!"),
        Reaction("zn", "h2so4", "Zn + H₂SO₄ → ZnSO₄ + H₂↑", ReactionEffect.GAS, 0x33FFFFFF, 0xFFFFFFFF, "Выделяется водород — так его получают в лаборатории."),
        Reaction("bacl2", "h2so4", "BaCl₂ + H₂SO₄ → BaSO₄↓ + 2HCl", ReactionEffect.PRECIPITATE, 0x66FFFFFF, 0xFFFAFAFA, "Белый осадок сульфата бария — качественная реакция на SO₄²⁻."),
        Reaction("fecl3", "naoh", "FeCl₃ + 3NaOH → Fe(OH)₃↓ + 3NaCl", ReactionEffect.PRECIPITATE, 0x66A1887F, 0xFF8D4E2A, "Бурый осадок гидроксида железа(III) — цвет ржавчины."),
        Reaction("phenol", "naoh", "Фенолфталеин + щёлочь → малиновая окраска", ReactionEffect.COLOR, 0xCCE91E63, 0xFFE91E63, "Индикатор становится малиновым в щелочной среде."),
        Reaction("ki", "pbno3", "Pb(NO₃)₂ + 2KI → PbI₂↓ + 2KNO₃", ReactionEffect.PRECIPITATE, 0x88FFEB3B, 0xFFFFD600, "Ярко-жёлтый осадок иодида свинца — опыт «Золотой дождь»."),
        Reaction("fe", "cuso4", "Fe + CuSO₄ → FeSO₄ + Cu", ReactionEffect.METAL, 0x6680CBC4, 0xFFD84315, "Железо покрывается красной медью, а синий раствор бледнеет."),
    )

    fun react(a: String, b: String): Reaction? =
        reactions.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    fun reagent(id: String) = reagents.first { it.id == id }
}
