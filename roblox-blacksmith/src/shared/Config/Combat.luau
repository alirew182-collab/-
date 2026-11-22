--!strict
-- БОЙ: блок, парирование, кулаки (если оружие не надето).

local Combat = {
	BlockDamageReduction = 0.6, -- блок снимает 60% урона
	ParryWindowSeconds = 0.3, -- нажал блок не раньше чем за 0.3с до удара → парирование (0 урона)
	ParryStunSeconds = 1.5, -- моб оглушён после парирования
	BlockToggleCooldown = 0.35, -- защита от спама блоком ради парирования
	BlockWalkSpeed = 8,
	DefaultWalkSpeed = 16,

	Fists = {
		Damage = 3,
		AttackCooldown = 0.6,
		Range = 5,
		Arc = 0.3,
		MaxTargets = 1,
	},

	-- Анимация взмаха (Animation ID из Creator Store / своя). Пусто = без анимации.
	SwingAnimationId = "",

	MobAiTickSeconds = 0.15,
	DamageFxRadius = 150, -- кому показывать цифры урона
}

return Combat
