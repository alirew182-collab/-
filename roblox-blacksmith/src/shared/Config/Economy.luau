--!strict
-- ЭКОНОМИКА, ИНВЕНТАРЬ, ДОБЫЧА, ДИСТАНЦИИ.

local Economy = {
	StartingGold = 0,
	StartingPickaxe = "Stone",
	InventoryCapacity = 30, -- слотов под оружие (x2 — геймпасс в Этапе 3)
	WeaponSellBonus = 1.5, -- оружие продаётся дороже, чем руда, из которой сделано
	MaxSellBatch = 50,

	Mining = {
		HitCooldownSeconds = 0.3,
		MaxDistance = 14,
	},

	InteractMaxDistance = 18, -- дистанция до кузни/торговца для действий
}

return Economy
