--!strict
--[[
	РЕДКОСТИ. В Этапе 1 вся ковка даёт Common; таблица уже здесь, чтобы данные оружия
	не пришлось мигрировать, когда в Этапе 2 появятся шансы редкостей.
]]

export type RarityDef = {
	Id: string,
	NameKey: string,
	Order: number,
	DamageMult: number,
	SellMult: number,
	Color: Color3,
}

local Rarities: { [string]: RarityDef } = {
	Common = {
		Id = "Common",
		NameKey = "RARITY_COMMON",
		Order = 1,
		DamageMult = 1.0,
		SellMult = 1.0,
		Color = Color3.fromRGB(190, 196, 206),
	},
	Rare = {
		Id = "Rare",
		NameKey = "RARITY_RARE",
		Order = 2,
		DamageMult = 1.2,
		SellMult = 1.6,
		Color = Color3.fromRGB(70, 150, 255),
	},
	Epic = {
		Id = "Epic",
		NameKey = "RARITY_EPIC",
		Order = 3,
		DamageMult = 1.45,
		SellMult = 2.6,
		Color = Color3.fromRGB(170, 90, 255),
	},
	Legendary = {
		Id = "Legendary",
		NameKey = "RARITY_LEGENDARY",
		Order = 4,
		DamageMult = 1.8,
		SellMult = 4.5,
		Color = Color3.fromRGB(255, 170, 40),
	},
	Mythic = {
		Id = "Mythic",
		NameKey = "RARITY_MYTHIC",
		Order = 5,
		DamageMult = 2.3,
		SellMult = 8,
		Color = Color3.fromRGB(255, 60, 90),
	},
	Divine = {
		Id = "Divine",
		NameKey = "RARITY_DIVINE",
		Order = 6,
		DamageMult = 3.0,
		SellMult = 15,
		Color = Color3.fromRGB(255, 250, 200),
	},
}

return Rarities
