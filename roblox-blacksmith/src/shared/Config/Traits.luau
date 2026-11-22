--!strict
--[[
	ЧЕРТЫ ОРУЖИЯ (случайные, число зависит от редкости).
	Все эффекты считает сервер (CombatService / MiningService).
]]

export type TraitDef = {
	Id: string,
	NameKey: string,
	Weight: number,
	Color: Color3,
	-- эффекты (любое подмножество)
	DamageMult: number?, -- множитель урона (виден в карточке)
	CooldownMult: number?, -- множитель паузы между ударами
	CritChance: number?,
	CritMult: number?,
	LifeSteal: number?, -- доля урона в лечение
	BurnFraction: number?, -- урон огнём в секунду = доля от урона удара
	BurnSeconds: number?,
	ExtraOreChance: number?, -- шанс +1 руды при добыче, когда оружие надето
}

local Traits: { [string]: TraitDef } = {
	Fire = {
		Id = "Fire",
		NameKey = "TRAIT_FIRE",
		Weight = 1,
		Color = Color3.fromRGB(255, 120, 40),
		BurnFraction = 0.25,
		BurnSeconds = 3,
	},
	Vampiric = {
		Id = "Vampiric",
		NameKey = "TRAIT_VAMPIRIC",
		Weight = 1,
		Color = Color3.fromRGB(220, 40, 70),
		LifeSteal = 0.08,
	},
	Critical = {
		Id = "Critical",
		NameKey = "TRAIT_CRITICAL",
		Weight = 1,
		Color = Color3.fromRGB(255, 220, 60),
		CritChance = 0.15,
		CritMult = 2,
	},
	Swift = {
		Id = "Swift",
		NameKey = "TRAIT_SWIFT",
		Weight = 1,
		Color = Color3.fromRGB(120, 255, 200),
		CooldownMult = 0.82,
	},
	Heavy = {
		Id = "Heavy",
		NameKey = "TRAIT_HEAVY",
		Weight = 0.8,
		Color = Color3.fromRGB(160, 140, 120),
		DamageMult = 1.25,
		CooldownMult = 1.15,
	},
	Lucky = {
		Id = "Lucky",
		NameKey = "TRAIT_LUCKY",
		Weight = 0.7,
		Color = Color3.fromRGB(120, 230, 90),
		ExtraOreChance = 0.25,
	},
}

return Traits
