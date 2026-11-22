--!strict
--[[
	КОВКА — мини-игра из 3 шагов. Итоговое качество 0–100%.
	Качество = 100 * (Bellows.Weight * счёт_мехов + Pour.Weight * счёт_заливки + Hammer.Weight * счёт_молота)
	Счёт каждого шага 0..1 считает СЕРВЕР из сырых замеров клиента.
]]

local Forging = {
	-- Качество 0% → оружие с уроном x0.7, 100% → x1.3
	QualityMultMin = 0.7,
	QualityMultMax = 1.3,

	SessionTimeoutSeconds = 90, -- сессия ковки протухает
	MinDurationTolerance = 0.85, -- сервер не примет результат быстрее 85% от минимальной длительности мини-игры

	-- Редкость: удача = 1 + качество(0..1) * RarityLuckPerQuality + RarityLuck руды
	RarityLuckPerQuality = 0.6,
	AnnounceMinRarity = "Mythic", -- объявление на весь сервер
	CelebrateMinRarity = "Legendary", -- большой эффект у самого игрока

	XpBase = 10, -- опыт за ковку = XpBase + OreCost * XpPerOre руды * XpOreFactor
	XpOreFactor = 0.5,

	Bellows = {
		Weight = 0.3,
		DurationSeconds = 4, -- сколько длится шаг
		HeatDecayPerSecond = 0.55, -- насколько остывает горн в секунду (0..1)
		HeatPerPump = 0.16, -- сколько добавляет одно нажатие
		ZoneWidth = 0.22, -- ширина «зелёной зоны»
		ZoneCenterMin = 0.55, -- центр зоны случайный в этом диапазоне
		ZoneCenterMax = 0.75,
		FullScoreFraction = 0.8, -- если в зоне ≥80% времени → 100% счёта
	},

	Pour = {
		Weight = 0.3,
		FillPerSecond = 0.5, -- скорость заполнения формы при удержании
		TargetMin = 0.55, -- целевой уровень — случайный в диапазоне
		TargetMax = 0.85,
		PerfectTolerance = 0.03, -- ближе этого → 100%
		Tolerance = 0.2, -- дальше этого → 0%
		MinSeconds = 0.8, -- минимальная реальная длительность шага
	},

	Hammer = {
		Weight = 0.4,
		Beats = 5,
		IntervalMin = 0.75, -- интервал между ударами (с), случайный
		IntervalMax = 0.95,
		LeadInSeconds = 1.0, -- пауза до первого удара
		PerfectWindow = 0.07, -- ±с для 100%
		HitWindow = 0.25, -- ±с, дальше — промах
	},
}

return Forging
