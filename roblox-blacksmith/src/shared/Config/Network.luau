--!strict
--[[
	RATE-LIMIT на все Remote: token bucket на игрока.
	PerSecond — скорость восстановления токенов, Burst — максимум подряд.
	Remote без записи получают Default.
]]

export type Limit = { PerSecond: number, Burst: number }

local Network: { Default: Limit, Limits: { [string]: Limit } } = {
	Default = { PerSecond = 2, Burst = 4 },
	Limits = {
		RequestData = { PerSecond = 0.5, Burst = 3 },
		CombatAttack = { PerSecond = 8, Burst = 10 },
		CombatBlock = { PerSecond = 6, Burst = 8 },
		ForgeStart = { PerSecond = 1, Burst = 3 },
		ForgeSubmit = { PerSecond = 1, Burst = 3 },
		ForgeCancel = { PerSecond = 2, Burst = 4 },
		EquipWeapon = { PerSecond = 3, Burst = 5 },
		EquipPickaxe = { PerSecond = 2, Burst = 4 },
		SellWeapons = { PerSecond = 2, Burst = 4 },
		SellOres = { PerSecond = 3, Burst = 6 },
		BuyPickaxe = { PerSecond = 1, Burst = 3 },
		SetLanguage = { PerSecond = 0.5, Burst = 2 },
		GateStart = { PerSecond = 1, Burst = 2 },
		GateLeave = { PerSecond = 1, Burst = 2 },
		RedeemCode = { PerSecond = 0.3, Burst = 3 },
		SetSetting = { PerSecond = 2, Burst = 6 },
	},
}

return Network
