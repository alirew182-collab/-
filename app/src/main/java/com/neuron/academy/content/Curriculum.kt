package com.neuron.academy.content

/** Вся школьная программа 9–11 классов. */
object Curriculum {
    val grades = listOf(9, 10, 11)

    val topics: List<Topic> = (AlgebraContent.topics + PhysicsContent.topics + ChemistryContent.topics)
        .sortedWith(compareBy({ it.grade }, { it.subject.ordinal }, { it.order }))

    private val byId = topics.associateBy { it.id }

    fun topic(id: String): Topic? = byId[id]

    fun topicsFor(subject: Subject, grade: Int): List<Topic> =
        topics.filter { it.subject == subject && it.grade == grade }.sortedBy { it.order }

    fun topicsFor(subject: Subject): List<Topic> = topics.filter { it.subject == subject }

    fun prerequisitesOf(topic: Topic): List<Topic> = topic.prerequisites.mapNotNull { byId[it] }

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

    fun boss(subject: Subject, grade: Int): Boss = bosses.first { it.subject == subject && it.grade == grade }

    /** Вопросы для битвы с боссом — из тестов всех тем класса по предмету. */
    fun examPool(subject: Subject, grade: Int): List<Question> = topicsFor(subject, grade).flatMap { it.test }
}
