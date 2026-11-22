--!strict
--[[
	ТИПЫ ОРУЖИЯ.
	Урон = BaseDamage * PowerMult руды * множитель качества * множитель редкости * бонус уровня.
	Arc — косинус половины угла удара (0.3 ≈ широкий взмах, 0.7 ≈ узкий выпад).
	Модель: ServerStorage/WeaponModels/<Id> (Model или BasePart). Если нет — рисуется заглушка размером Size.
]]

export type WeaponDef = {
	Id: string,
	NameKey: string,
	Order: number,
	OreCost: number, -- сколько руды уходит на ковку
	BaseDamage: number,
	AttackCooldown: number,
	Range: number,
	Arc: number,
	MaxTargets: number,
	SellMult: number,
	Size: Vector3,
}

local Weapons: { [string]: WeaponDef } = {
	Sword = {
		Id = "Sword",
		NameKey = "WPN_SWORD",
		Order = 1,
		OreCost = 3,
		BaseDamage = 10,
		AttackCooldown = 0.5,
		Range = 7,
		Arc = 0.3,
		MaxTargets = 3,
		SellMult = 1.0,
		Size = Vector3.new(0.3, 0.6, 4),
	},
	Axe = {
		Id = "Axe",
		NameKey = "WPN_AXE",
		Order = 2,
		OreCost = 4,
		BaseDamage = 16,
		AttackCooldown = 0.8,
		Range = 7,
		Arc = 0.2,
		MaxTargets = 4,
		SellMult = 1.25,
		Size = Vector3.new(0.4, 1.4, 3.4),
	},
	Spear = {
		Id = "Spear",
		NameKey = "WPN_SPEAR",
		Order = 3,
		OreCost = 3,
		BaseDamage = 9,
		AttackCooldown = 0.55,
		Range = 10,
		Arc = 0.7,
		MaxTargets = 2,
		SellMult = 1.0,
		Size = Vector3.new(0.3, 0.3, 6),
	},
}

return Weapons
