package com.neuron.gamification

enum class ShopCategory(val title: String) { SKIN("Скины Нейрона"), THEME("Темы оформления"), CONSUMABLE("Полезности") }

data class ShopItem(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val price: Int,
    val category: ShopCategory,
)

object ShopCatalog {
    const val DEFAULT_SKIN = "skin_classic"
    const val DEFAULT_THEME = "theme_default"
    const val HINTS_PACK = "hints_3"
    const val STREAK_FREEZE = "streak_freeze"

    val items = listOf(
        ShopItem(DEFAULT_SKIN, "Классический Нейрон", "Верный робот-помощник", "🤖", 0, ShopCategory.SKIN),
        ShopItem("skin_alien", "Нейрон-пришелец", "Прилетел с Альфы Центавра", "👽", 100, ShopCategory.SKIN),
        ShopItem("skin_fox", "Лис-хакер", "Хитрый и умный", "🦊", 150, ShopCategory.SKIN),
        ShopItem("skin_astronaut", "Космонавт", "Готов к старту!", "🧑‍🚀", 200, ShopCategory.SKIN),
        ShopItem("skin_wizard", "Маг формул", "Колдует уравнения", "🧙", 250, ShopCategory.SKIN),
        ShopItem("skin_dragon", "Дракон знаний", "Дышит формулами", "🐉", 400, ShopCategory.SKIN),
        ShopItem(DEFAULT_THEME, "Стандартная", "Фиолетовая классика", "🎨", 0, ShopCategory.THEME),
        ShopItem("theme_space", "Космос", "Глубокий синий и звёзды", "🌌", 150, ShopCategory.THEME),
        ShopItem("theme_ocean", "Океан", "Бирюзовые волны", "🌊", 150, ShopCategory.THEME),
        ShopItem("theme_forest", "Лес", "Спокойный зелёный", "🌲", 150, ShopCategory.THEME),
        ShopItem("theme_sunset", "Закат", "Тёплый оранжевый", "🌅", 200, ShopCategory.THEME),
        ShopItem(HINTS_PACK, "3 подсказки «50/50»", "Убирают два неверных варианта в тесте", "💡", 30, ShopCategory.CONSUMABLE),
        ShopItem(STREAK_FREEZE, "Заморозка серии", "Сохранит 🔥, если пропустишь день", "🧊", CrystalRules.STREAK_FREEZE_PRICE, ShopCategory.CONSUMABLE),
    )

    fun byId(id: String) = items.firstOrNull { it.id == id }
    fun isConsumable(id: String) = byId(id)?.category == ShopCategory.CONSUMABLE
}
