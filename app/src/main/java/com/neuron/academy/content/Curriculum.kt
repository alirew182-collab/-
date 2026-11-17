package com.neuron.academy.content

/** Вся школьная программа 9–11 классов. */
object Curriculum {
    val grades = listOf(9, 10, 11)

    /** Темы идут блоками по 4. После каждого блока (кроме последнего) — страж раздела. */
    const val BLOCK = 4

    /** Порядок тем внутри класса. Определяет карту-путь и последовательность открытия. */
    private val order: Map<Pair<Subject, Int>, List<String>> = mapOf(
        (Subject.ALGEBRA to 9) to listOf(
            "a9_identities", "a9_algfrac", "a9_roots9", "a9_numsets",
            "a9_funcs", "a9_quad", "a9_quadeq", "a9_rational",
            "a9_biquad", "a9_sys", "a9_graphsys", "a9_powerfunc",
            "a9_ineq", "a9_interval", "a9_sysineq", "a9_logic",
            "a9_wordprob", "a9_percent", "a9_estimation", "a9_ogepract",
            "a9_prog", "a9_sums", "a9_seqrec", "a9_param",
            "a9_comb", "a9_prob", "a9_stat", "a9_ogegraphs",
        ),
        (Subject.ALGEBRA to 10) to listOf(
            "a10_roots", "a10_powerfunc", "a10_irrat", "a10_rootsineq",
            "a10_func", "a10_inverse", "a10_ratfunc", "a10_ratineq",
            "a10_modeq", "a10_symsys", "a10_numtheory", "a10_induction",
            "a10_radians", "a10_trig", "a10_identities", "a10_trigform",
            "a10_sumprod", "a10_trigfunc", "a10_arc", "a10_trigeq",
            "a10_homogeneous", "a10_trigselect", "a10_trigineq", "a10_polynom",
            "a10_numseq", "a10_limitfunc", "a10_derivintro", "a10_complex",
        ),
        (Subject.ALGEBRA to 11) to listOf(
            "a11_deriv", "a11_derivrules", "a11_tangent", "a11_extrema",
            "a11_secondderiv", "a11_optim", "a11_egederiv", "a11_integral",
            "a11_integralprops", "a11_area", "a11_volume", "a11_diffeq",
            "a11_expfunc", "a11_lnfunc", "a11_exp", "a11_expineq",
            "a11_log", "a11_logfunc", "a11_logeq", "a11_mixeq",
            "a11_logsys", "a11_funcmethods", "a11_param", "a11_complex2",
            "a11_probstat", "a11_egeprob", "a11_egetext", "a11_egeecon",
        ),
        (Subject.PHYSICS to 9) to listOf(
            "p9_motion", "p9_vectors", "p9_kin", "p9_graphs",
            "p9_freefall", "p9_newton", "p9_friction", "p9_gravity",
            "p9_impulse", "p9_reactive", "p9_energy", "p9_simple",
            "p9_archimedes", "p9_heat9", "p9_waves", "p9_pendulum",
            "p9_sound", "p9_currentbasics", "p9_emfield", "p9_electromagnet",
            "p9_light", "p9_mirrors", "p9_refraction", "p9_lenses",
            "p9_atom", "p9_nuclearpower", "p9_space", "p9_oge",
        ),
        (Subject.PHYSICS to 10) to listOf(
            "p10_measure", "p10_kinem", "p10_circular", "p10_dyn",
            "p10_inclined", "p10_connected", "p10_gravity10", "p10_celestial",
            "p10_statics", "p10_cons", "p10_collisions", "p10_workpower",
            "p10_oscill", "p10_waves10", "p10_hydro", "p10_mkt",
            "p10_gaslaws", "p10_surface", "p10_solids", "p10_thermo",
            "p10_phase", "p10_heatengines", "p10_estat", "p10_dielectric",
            "p10_capacitor", "p10_dccircuit", "p10_semi", "p10_egemech",
        ),
        (Subject.PHYSICS to 11) to listOf(
            "p11_current", "p11_circuits", "p11_magnet", "p11_lorentz",
            "p11_magmat", "p11_induction", "p11_ac", "p11_rlc",
            "p11_energytrans", "p11_emosc", "p11_radiowave", "p11_spectra",
            "p11_optics", "p11_lenses11", "p11_instruments", "p11_waveoptics",
            "p11_relativity", "p11_photoeffect", "p11_wavepart", "p11_atommodel",
            "p11_lasers", "p11_quantum", "p11_radioactivity", "p11_nuclear",
            "p11_particles", "p11_astro", "p11_physworld", "p11_ege",
        ),
        (Subject.CHEMISTRY to 9) to listOf(
            "c9_safety", "c9_periodic", "c9_mole", "c9_oxides",
            "c9_bases", "c9_acids", "c9_salts", "c9_genetic9",
            "c9_dissoc", "c9_ionic", "c9_react", "c9_rate9",
            "c9_hydrolysis", "c9_hydrogen", "c9_nonmet", "c9_halogens",
            "c9_oxygen", "c9_sulfur", "c9_nitrogen", "c9_fertilizers",
            "c9_carbon", "c9_organic9", "c9_met", "c9_alkali",
            "c9_iron", "c9_corrosion", "c9_ecology", "c9_oge",
        ),
        (Subject.CHEMISTRY to 10) to listOf(
            "c10_theory", "c10_nomencl", "c10_isomer", "c10_reactorg",
            "c10_radicals", "c10_hydro", "c10_cycloalk", "c10_alkenes",
            "c10_rubber", "c10_alkynes", "c10_oil", "c10_halogenderiv",
            "c10_alc", "c10_glycerol", "c10_phenol", "c10_aldehyde",
            "c10_acid", "c10_esters", "c10_soaps", "c10_carbs",
            "c10_cellulose", "c10_amines", "c10_nitro", "c10_polymers",
            "c10_nucleic", "c10_drugs", "c10_genetic", "c10_ege",
        ),
        (Subject.CHEMISTRY to 11) to listOf(
            "c11_atom", "c11_quantum", "c11_isotopes", "c11_periodic11",
            "c11_bond", "c11_hybrid", "c11_crystal", "c11_gases",
            "c11_solutions", "c11_disperse", "c11_classes", "c11_acidbase",
            "c11_ionprod", "c11_hydrolysis11", "c11_redox", "c11_electrolysis",
            "c11_batteries", "c11_metals11", "c11_nonmetals11", "c11_complex",
            "c11_rate", "c11_equil", "c11_thermochem", "c11_industry",
            "c11_calcs", "c11_analysis", "c11_ege", "c11_life",
        ),
    )

    private val all: Map<String, Topic> = (
        AlgebraContent.topics + AlgebraExtra.topics + AlgebraMore.topics + AlgebraPlus.topics +
            PhysicsContent.topics + PhysicsExtra.topics + PhysicsMore.topics + PhysicsPlus.topics +
            ChemistryContent.topics + ChemistryExtra.topics + ChemistryMore.topics + ChemistryPlus.topics
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
        // ---- стражи 3 и 4 ----
        Boss(Subject.ALGEBRA, 9, "Процентный Гоблин", "👺", "Я накручу тебе сложные проценты!", "Твой расчёт точнее моего…", "sci_bernoulli", stage = 3),
        Boss(Subject.ALGEBRA, 9, "Параметрический Хамелеон", "🦎", "Я меняюсь при каждом значении a!", "Ты разобрал все случаи…", "sci_bayes", stage = 4),
        Boss(Subject.ALGEBRA, 10, "Тождественный Оборотень", "🐺", "Я выгляжу иначе, но я тот же!", "Ты доказал тождество…", "sci_hipparchus", stage = 3),
        Boss(Subject.ALGEBRA, 10, "Мнимый Джинн", "🧞", "Я существую, хотя i² = −1!", "Мой модуль оказался равен нулю…", "sci_euler", stage = 4),
        Boss(Subject.ALGEBRA, 11, "Объёмный Голем", "🗿", "Посчитай объём моего тела вращения!", "Интеграл сошёлся…", "sci_archimedes", stage = 3),
        Boss(Subject.ALGEBRA, 11, "Логарифмический Вихрь", "🌪️", "Я сжимаю миллионы до единиц!", "Основание раскрыто…", "sci_napier", stage = 4),
        Boss(Subject.PHYSICS, 9, "Маятниковый Призрак", "👻", "Мои колебания в резонансе с твоим страхом!", "Резонанс погашен…", "sci_huygens", stage = 3),
        Boss(Subject.PHYSICS, 9, "Линзовый Циклоп", "👁️", "Мой глаз — собирающая линза!", "Изображение оказалось мнимым…", "sci_galileo", stage = 4),
        Boss(Subject.PHYSICS, 10, "Волновой Кракен", "🐙", "Мои волны сотрут тебя в порошок!", "Волна погасла…", "sci_doppler", stage = 3),
        Boss(Subject.PHYSICS, 10, "Паровой Титан", "🚂", "Мой КПД почти Карно!", "Холодильник победил…", "sci_carnot", stage = 4),
        Boss(Subject.PHYSICS, 11, "Оптический Мираж", "🌫️", "Ты видишь меня, но меня здесь нет!", "Свет преломился не в мою пользу…", "sci_young", stage = 3),
        Boss(Subject.PHYSICS, 11, "Фотонный Дракон", "🐲", "Мой фотон выбьет твои электроны!", "Работа выхода оказалась больше…", "sci_stoletov", stage = 4),
        Boss(Subject.CHEMISTRY, 9, "Серный Демон", "😈", "Чувствуешь запах сероводорода?", "Ты осадил меня барием…", "el_S", stage = 3),
        Boss(Subject.CHEMISTRY, 9, "Ржавый Рыцарь", "🛡️", "Коррозия — моя броня!", "Цинк защитил тебя…", "el_Zn", stage = 4),
        Boss(Subject.CHEMISTRY, 10, "Нефтяной Левиафан", "🐋", "Я крекингом разорву твои цепи!", "Октановое число слишком высокое…", "el_C", stage = 3),
        Boss(Subject.CHEMISTRY, 10, "Мыльный Слизень", "🫧", "Мои мицеллы поглотят тебя!", "Жёсткая вода победила…", "el_Na", stage = 4),
        Boss(Subject.CHEMISTRY, 11, "Коллоидный Фантом", "🌫️", "Меня видно только в луче Тиндаля!", "Коагуляция!", "el_Ag", stage = 3),
        Boss(Subject.CHEMISTRY, 11, "Комплексный Лич", "💠", "Мои лиганды окружили тебя!", "Внутренняя сфера разрушена…", "el_Cu", stage = 4),
        // ---- стражи 5 и 6 ----
        Boss(Subject.ALGEBRA, 9, "Рекуррентный Змей", "🐍", "Каждый мой шаг зависит от предыдущего!", "Последовательность оборвалась…", "sci_pascal", stage = 5),
        Boss(Subject.ALGEBRA, 9, "Экзаменатор ОГЭ", "📋", "Покажи, чему научился за год!", "Зачёт! Ты готов к ОГЭ.", "sci_kovalevskaya", stage = 6),
        Boss(Subject.ALGEBRA, 10, "Индукционный Голем", "🗿", "Докажи, что победишь меня для любого n!", "База и шаг верны…", "sci_cauchy", stage = 5),
        Boss(Subject.ALGEBRA, 10, "Отборщик Корней", "✂️", "Я спрячу корни за краем отрезка!", "Все корни найдены…", "sci_lobachevsky", stage = 6),
        Boss(Subject.ALGEBRA, 11, "Системный Сфинкс", "🦁", "Реши мою систему — или пропадёшь!", "Пары найдены…", "sci_ostrogradsky", stage = 5),
        Boss(Subject.ALGEBRA, 11, "Экзаменатор ЕГЭ", "🎓", "Профильная математика — мой конёк!", "Сотка твоя!", "sci_kolmogorov", stage = 6),
        Boss(Subject.PHYSICS, 9, "Электромоторный Робот", "🤖", "Мой ротор не остановить!", "Индукция против меня…", "sci_tesla", stage = 5),
        Boss(Subject.PHYSICS, 9, "Экзаменатор ОГЭ", "📋", "Объясни явление — и переведи в СИ!", "Зачёт! Ты готов к ОГЭ.", "sci_newton", stage = 6),
        Boss(Subject.PHYSICS, 10, "Капиллярный Слизень", "🦠", "Я просочусь в любую щель!", "Поверхностное натяжение не выдержало…", "sci_lomonosov", stage = 5),
        Boss(Subject.PHYSICS, 10, "Экзаменатор ЕГЭ-Механик", "📐", "Нарисуй силы, если сможешь!", "Механика сдана!", "sci_galileo", stage = 6),
        Boss(Subject.PHYSICS, 11, "Распадный Призрак", "☢️", "Половина меня уже исчезла!", "Период полураспада истёк…", "sci_kurchatov", stage = 5),
        Boss(Subject.PHYSICS, 11, "Экзаменатор ЕГЭ", "🎓", "Электродинамика — моя стихия!", "Ты сдал физику!", "sci_einstein", stage = 6),
        Boss(Subject.CHEMISTRY, 9, "Солевой Великан", "🧂", "Я — кислая, средняя и основная соль сразу!", "Ты разложил меня на ионы…", "sci_lavoisier", stage = 5),
        Boss(Subject.CHEMISTRY, 9, "Экзаменатор ОГЭ", "📋", "Посчитай массу осадка!", "Зачёт! Ты готов к ОГЭ.", "sci_mendeleev", stage = 6),
        Boss(Subject.CHEMISTRY, 10, "Целлюлозный Ент", "🌳", "Мои цепи прочнее дуба!", "Гидролиз победил…", "el_C", stage = 5),
        Boss(Subject.CHEMISTRY, 10, "Экзаменатор ЕГЭ-Органик", "⬡", "Пять стрелок — пять ловушек!", "Цепочка решена!", "sci_butlerov", stage = 6),
        Boss(Subject.CHEMISTRY, 11, "Батарейный Голем", "🔋", "Я разряжу тебя до нуля!", "Ток иссяк…", "el_Li", stage = 5),
        Boss(Subject.CHEMISTRY, 11, "Экзаменатор ЕГЭ", "🎓", "Задача 34 ждёт тебя!", "Ты сдал химию!", "sci_le_chatelier", stage = 6),
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
