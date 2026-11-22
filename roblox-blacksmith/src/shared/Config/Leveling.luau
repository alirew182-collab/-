--!strict
-- УРОВНИ. Опыт до следующего уровня = floor(XpBase * level ^ XpExponent).

local Leveling = {
	MaxLevel = 100,
	XpBase = 40,
	XpExponent = 1.45,

	BaseHealth = 100,
	HealthPerLevel = 6,
	DamagePerLevel = 0.03, -- +3% урона за уровень

	-- Ранг пока только визуальный (испытания рангов — Этап 2)
	StartingRank = "F",
}

return Leveling
