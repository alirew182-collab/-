--!strict
--[[
	РУДЫ.
	Новая руда = новая запись здесь + в карте Part с тегом "OreNode" и атрибутом OreId = "<Id>".
	Названия берутся из таблицы локализации по ключу NameKey.
]]

export type OreDef = {
	Id: string,
	NameKey: string,
	Order: number, -- порядок в интерфейсе
	RequiredLevel: number, -- уровень игрока для добычи и ковки
	RequiredPickaxeTier: number, -- минимальный тир кирки
	NodeHealth: number, -- сколько «силы добычи» нужно, чтобы разбить жилу
	YieldMin: number,
	YieldMax: number,
	RespawnSeconds: number,
	XpPerOre: number,
	SellPrice: number, -- цена 1 руды у торговца
	PowerMult: number, -- множитель урона оружия из этой руды
	RarityLuck: number, -- бонус к шансам редкости при ковке
	Color: Color3,
	Material: Enum.Material,
}

local Ores: { [string]: OreDef } = {
	Copper = {
		Id = "Copper",
		NameKey = "ORE_COPPER",
		Order = 1,
		RequiredLevel = 1,
		RequiredPickaxeTier = 1,
		NodeHealth = 3,
		YieldMin = 1,
		YieldMax = 2,
		RespawnSeconds = 8,
		XpPerOre = 4,
		SellPrice = 3,
		PowerMult = 1.0,
		RarityLuck = 0,
		Color = Color3.fromRGB(201, 116, 62),
		Material = Enum.Material.Slate,
	},
	Iron = {
		Id = "Iron",
		NameKey = "ORE_IRON",
		Order = 2,
		RequiredLevel = 3,
		RequiredPickaxeTier = 2,
		NodeHealth = 6,
		YieldMin = 1,
		YieldMax = 2,
		RespawnSeconds = 12,
		XpPerOre = 9,
		SellPrice = 8,
		PowerMult = 1.6,
		RarityLuck = 0.15,
		Color = Color3.fromRGB(140, 146, 156),
		Material = Enum.Material.Slate,
	},
	Moonsilver = {
		Id = "Moonsilver",
		NameKey = "ORE_MOONSILVER",
		Order = 3,
		RequiredLevel = 6,
		RequiredPickaxeTier = 3,
		NodeHealth = 10,
		YieldMin = 1,
		YieldMax = 1,
		RespawnSeconds = 20,
		XpPerOre = 20,
		SellPrice = 22,
		PowerMult = 2.6,
		RarityLuck = 0.35,
		Color = Color3.fromRGB(170, 210, 255),
		Material = Enum.Material.Glass,
	},
}

return Ores
