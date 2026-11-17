package com.neuron.academy.content

/** Вся школьная программа 9–11 классов. */
object Curriculum {
    val grades = listOf(9, 10, 11)

    /** Темы идут блоками по 4. После каждого блока (кроме последнего) — страж раздела. */
    const val BLOCK = 4

    /** Порядок тем внутри класса. Определяет карту-путь и последовательность открытия. */
    private val order: Map<Pair<Subject, Int>, List<String>> = mapOf(
        (Subject.ALGEBRA to 9) to listOf(
            "a9_numsets", "a9_funcs", "a9_quad", "a9_quadeq",
            "a9_ineq", "a9_interval", "a9_sys", "a9_sysineq",
            "a9_prog", "a9_comb", "a9_prob", "a9_stat",
        ),
        (Subject.ALGEBRA to 10) to listOf(
            "a10_roots", "a10_irrat", "a10_func", "a10_inverse",
            "a10_trig", "a10_trigform", "a10_trigfunc", "a10_arc",
            "a10_trigeq", "a10_trigineq", "a10_polynom", "a10_numseq",
        ),
        (Subject.ALGEBRA to 11) to listOf(
            "a11_deriv", "a11_derivrules", "a11_tangent", "a11_extrema",
            "a11_optim", "a11_integral", "a11_area", "a11_expfunc",
            "a11_exp", "a11_expineq", "a11_log", "a11_logeq",
        ),
        (Subject.PHYSICS to 9) to listOf(
            "p9_motion", "p9_kin", "p9_freefall", "p9_newton",
            "p9_gravity", "p9_impulse", "p9_energy", "p9_waves",
            "p9_sound", "p9_emfield", "p9_light", "p9_atom",
        ),
        (Subject.PHYSICS to 10) to listOf(
            "p10_kinem", "p10_dyn", "p10_statics", "p10_cons",
            "p10_hydro", "p10_mkt", "p10_gaslaws", "p10_thermo",
            "p10_phase", "p10_estat", "p10_capacitor", "p10_semi",
        ),
        (Subject.PHYSICS to 11) to listOf(
            "p11_current", "p11_circuits", "p11_magnet", "p11_induction",
            "p11_ac", "p11_emosc", "p11_optics", "p11_waveoptics",
            "p11_relativity", "p11_quantum", "p11_nuclear", "p11_astro",
        ),
        (Subject.CHEMISTRY to 9) to listOf(
            "c9_periodic", "c9_dissoc", "c9_ionic", "c9_react",
            "c9_hydrolysis", "c9_halogens", "c9_nonmet", "c9_nitrogen",
            "c9_carbon", "c9_met", "c9_alkali", "c9_iron",
        ),
        (Subject.CHEMISTRY to 10) to listOf(
            "c10_theory", "c10_hydro", "c10_alkenes", "c10_alkynes",
            "c10_alc", "c10_phenol", "c10_aldehyde", "c10_acid",
            "c10_esters", "c10_carbs", "c10_amines", "c10_polymers",
        ),
        (Subject.CHEMISTRY to 11) to listOf(
            "c11_atom", "c11_bond", "c11_crystal", "c11_solutions",
            "c11_classes", "c11_redox", "c11_electrolysis", "c11_rate",
            "c11_equil", "c11_thermochem", "c11_calcs", "c11_life",
        ),
    )

    private val all: Map<String, Topic> = (
        AlgebraContent.topics + AlgebraExtra.topics +
            PhysicsContent.topics + PhysicsExtra.topics +
            ChemistryContent.topics + ChemistryExtra.topics
        ).associateBy { it.id }

    val topics: List<Topic> = order.flatMap { (key, ids) ->
        ids.mapIndexedNotNull { i, id -> all[id]?.copy(subject = key.first, grade = key.second, order = i + 1) }
    }.sortedWith(compareBy({ it.grade }, { it.subject.ordinal }, { it.order }))

    private val byId = topics.associateBy { it.id }

    fun topic(id: String): Topic? = byId[id]

    fun topicsFor(subject: Subject, grade: Int): List<Topic> =
        topics.filter { it.subject == subject && it.grade == grade }.sortedBy { it.order }

    fun topicsFor(subject: Subject): List<Topic> = topics.filter { it.subject == subject }

    fun prerequisitesOf(topic: Topic): List<Topic> = topic.prerequisites.mapNotNull { byId[it] }

    /** Число стражей в классе по предмету. */
    fun guardianCount(subject: Subject, grade: Int): Int {
        val n = topicsFor(subject, grade).size
        return ((n + BLOCK - 1) / BLOCK - 1).coerceAtLeast(0)
    }

    /** Темы блока, который охраняет страж [stage] (1, 2, …). */
    fun blockTopics(subject: Subject, grade: Int, stage: Int): List<Topic> =
        topicsFor(subject, grade).drop((stage - 1) * BLOCK).take(BLOCK)

    val bosses: List<Boss> = listOf(
        Boss(Subject.ALGEBRA, 9, "Повелитель Парабол", "🐲", "Мои ветви направлены вниз… прямо на тебя!", "Ты нашёл мою вершину… и это был мой конец!", "sci_kovalevskaya"),
        Boss(Subject.ALGEBRA, 10, "Тригонометрический Титан", "🗿", "Я кружу по единичной окружности быстрее тебя!", "Мой синус упал до нуля…", "sci_lobachevsky"),
        Boss(Subject.ALGEBRA, 11, "Интегральный Дракон", "🐉", "Попробуй найти площадь под моим хвостом!", "Моя производная… стала отрицательной…", "sci_kolmogorov"),
        Boss(Subject.PHYSICS, 9, "Инерционный Голем", "🗿", "Меня не сдвинуть! Первый закон Ньютона на моей стороне!", "Ты приложил достаточную силу!", "sci_tsiolkovsky"),
        Boss(Subject.PHYSICS, 10, "Термо-Колосс", "🌋", "Мой КПД — 100%! …ну почти.", "Энтропия победила меня…", "sci_tesla"),
        Boss(Subject.PHYSICS, 11, "Квантовый Фантом", "👻", "Я одновременно здесь и не здесь!", "Ты измерил меня — и моя волновая функция схлопнулась!", "sci_bohr"),
        Boss(Subject.CHEMISTRY, 9, "Кислотный Слизень", "🟢", "Я растворю твои знания на ионы!", "Ты нейтрализовал меня щёлочью знаний!", "el_Au"),
        Boss(Subject.CHEMISTRY, 10, "Углеродный Змей", "🐍", "У меня миллион изомеров!", "Ты разорвал мою двойную связь!", "sci_butlerov"),
        Boss(Subject.CHEMISTRY, 11, "Электронный Лич", "💀", "Мои электроны вращаются вокруг тебя!", "Равновесие смещено… в твою сторону!", "sci_mendeleev"),
    )

    /** Стражи разделов — мини-боссы после каждых четырёх тем. */
    val guardians: List<Boss> = listOf(
        Boss(Subject.ALGEBRA, 9, "Модульный Цербер", "🐺", "У меня три головы, и все по модулю!", "Ладно, твой модуль сильнее…", "sci_viete", stage = 1),
        Boss(Subject.ALGEBRA, 9, "Интервальный Паук", "🕷️", "Я запутаю тебя в своих промежутках!", "Ты расставил все знаки верно…", "sci_pascal", stage = 2),
        Boss(Subject.ALGEBRA, 10, "Корневой Тролль", "🧌", "Под мостом корней прячется ОДЗ!", "Ты не забыл проверку…", "sci_cauchy", stage = 1),
        Boss(Subject.ALGEBRA, 10, "Синусоидальный Змей", "🐍", "Мои волны тебя укачают!", "Период закончился…", "sci_fermat", stage = 2),
        Boss(Subject.ALGEBRA, 11, "Касательный Ястреб", "🦅", "Я падаю на тебя под углом arctg k!", "Мой экстремум пройден…", "sci_lagrange", stage = 1),
        Boss(Subject.ALGEBRA, 11, "Экспоненциальный Голем", "🗿", "Я расту быстрее, чем ты учишь!", "Основание оказалось меньше единицы…", "sci_ostrogradsky", stage = 2),
        Boss(Subject.PHYSICS, 9, "Ускоренный Вихрь", "🌪️", "Я разгоняюсь до a = 100 м/с²!", "Тормозной путь закончился…", "sci_kepler", stage = 1),
        Boss(Subject.PHYSICS, 9, "Энерго-Вампир", "🧛", "Я высосу всю твою кинетическую энергию!", "Энергия сохранилась… у тебя.", "sci_hertz", stage = 2),
        Boss(Subject.PHYSICS, 10, "Рычажный Великан", "🏋️", "Дай мне точку опоры, и я сдвину тебя!", "Моменты сил уравновешены…", "sci_torricelli", stage = 1),
        Boss(Subject.PHYSICS, 10, "Изобарный Джинн", "🧞", "Я расширяюсь при постоянном давлении!", "Мой объём сжался до нуля…", "sci_boyle", stage = 2),
        Boss(Subject.PHYSICS, 11, "Индукционный Кракен", "🐙", "Мои щупальца меняют магнитный поток!", "Правило Ленца против меня…", "sci_lenz", stage = 1),
        Boss(Subject.PHYSICS, 11, "Интерференционный Призрак", "👻", "Мои максимумы гасят твои!", "Разность хода стала полуволной…", "sci_young", stage = 2),
        Boss(Subject.CHEMISTRY, 9, "Ионный Элементаль", "⚡", "Я распадусь на катионы и анионы!", "Ты связал меня в осадок…", "sci_arrhenius", stage = 1),
        Boss(Subject.CHEMISTRY, 9, "Галогенный Василиск", "🦎", "Мой хлор вытеснит твой бром!", "Неметалл повержен…", "el_Br", stage = 2),
        Boss(Subject.CHEMISTRY, 10, "Бензольный Голем", "⬡", "Моё кольцо не разорвать!", "Ароматичность не спасла…", "sci_markovnikov", stage = 1),
        Boss(Subject.CHEMISTRY, 10, "Альдегидный Мимик", "📦", "Я притворяюсь спиртом!", "Серебряное зеркало меня выдало…", "sci_zinin", stage = 2),
        Boss(Subject.CHEMISTRY, 11, "Кристаллический Страж", "💎", "Моя решётка непробиваема!", "Связи разорваны…", "el_Si", stage = 1),
        Boss(Subject.CHEMISTRY, 11, "Окислительный Феникс", "🐦‍🔥", "Я забираю твои электроны!", "Электронный баланс сошёлся…", "sci_faraday", stage = 2),
    )

    /** Боссы еженедельного рейда — смешивают все пройденные темы. */
    val raidBosses: List<Boss> = listOf(
        Boss(Subject.ALGEBRA, 0, "Хаос-Профессор", "🧙", "Я задам вопросы из ВСЕХ тем сразу!", "Ты знаешь больше, чем я думал…", "sci_gagarin", stage = 99),
        Boss(Subject.PHYSICS, 0, "Чёрная Дыра Забвения", "🕳️", "Я поглощу всё, что ты выучил!", "Знания вырвались из горизонта событий!", "sci_korolev", stage = 99),
        Boss(Subject.CHEMISTRY, 0, "Философский Камень", "🪨", "Я превращу твои ответы в свинец!", "Ты сам превратил свинец в золото…", "el_Ti", stage = 99),
        Boss(Subject.ALGEBRA, 0, "Бесконечный Лабиринт", "🌀", "Отсюда нет выхода… кроме правильных ответов!", "Ты нашёл выход…", "sci_planck", stage = 99),
    )

    fun boss(subject: Subject, grade: Int, stage: Int = 0): Boss =
        if (stage == 0) bosses.first { it.subject == subject && it.grade == grade }
        else guardians.first { it.subject == subject && it.grade == grade && it.stage == stage }

    fun raidBoss(week: Long): Boss = raidBosses[Math.floorMod(week, raidBosses.size.toLong()).toInt()]

    /** Вопросы для битвы: страж — темы его блока, финальный босс — весь класс. */
    fun examPool(subject: Subject, grade: Int, stage: Int = 0): List<Question> =
        if (stage == 0) topicsFor(subject, grade).flatMap { it.test }
        else blockTopics(subject, grade, stage).flatMap { it.test }
}
