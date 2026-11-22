--!strict
--[[
	РЕДКОСТИ ОРУЖИЯ.
	Weight — базовый «вес» шанса. Итоговый шанс зависит от качества ковки и руды:
		вес_редкости = Weight * удача ^ (Order - 1),   удача = 1 + качество% * RarityLuckPerQuality + RarityLuck руды
	Шансы показываются игроку в кузне (кнопка «Шансы») — так требуют правила Roblox для случайных наград.
	Traits — сколько случайных черт получает оружие этой редкости.
]]

export type RarityDef = {
	Id: string,
	NameKey: string,
	Order: number,
	Weight: number,
	Traits: number,
	DamageMult: number,
	SellMult: number,
	Color: Color3,
}

local Rarities: { [string]: RarityDef } = {
	Common = {
		Id = "Common",
		NameKey = "RARITY_COMMON",
		Order = 1,
		Weight = 75,
		Traits = 0,
		DamageMult = 1.0,
		SellMult = 1.0,
		Color = Color3.fromRGB(190, 196, 206),
	},
	Rare = {
		Id = "Rare",
		NameKey = "RARITY_RARE",
		Order = 2,
		Weight = 20,
		Traits = 1,
		DamageMult = 1.2,
		SellMult = 1.6,
		Color = Color3.fromRGB(70, 150, 255),
	},
	Epic = {
		Id = "Epic",
		NameKey = "RARITY_EPIC",
		Order = 3,
		Weight = 4,
		Traits = 1,
		DamageMult = 1.45,
		SellMult = 2.6,
		Color = Color3.fromRGB(170, 90, 255),
	},
	Legendary = {
		Id = "Legendary",
		NameKey = "RARITY_LEGENDARY",
		Order = 4,
		Weight = 0.6,
		Traits = 2,
		DamageMult = 1.8,
		SellMult = 4.5,
		Color = Color3.fromRGB(255, 170, 40),
	},
	Mythic = {
		Id = "Mythic",
		NameKey = "RARITY_MYTHIC",
		Order = 5,
		Weight = 0.08,
		Traits = 2,
		DamageMult = 2.3,
		SellMult = 8,
		Color = Color3.fromRGB(255, 60, 90),
	},
	Divine = {
		Id = "Divine",
		NameKey = "RARITY_DIVINE",
		Order = 6,
		Weight = 0.008,
		Traits = 3,
		DamageMult = 3.0,
		SellMult = 15,
		Color = Color3.fromRGB(255, 250, 200),
	},
}

return Rarities
