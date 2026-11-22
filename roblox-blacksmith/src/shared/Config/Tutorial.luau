--!strict
--[[
	ОБУЧЕНИЕ / КВЕСТЫ СИСТЕМЫ первого захода.
	Event — событие, которое шлют сервисы (OreMined, WeaponForged, WeaponEquipped,
	MerchantOpened, GateEntered, GateCleared). TargetTag — куда рисовать луч-подсказку.
]]

export type TutorialStep = {
	Id: string,
	TitleKey: string,
	Event: string,
	Count: number,
	OreId: string?,
	TargetTag: string?,
	TargetOre: string?,
	RewardXp: number,
	RewardGold: number,
}

local Tutorial: { Steps: { TutorialStep } } = {
	Steps = {
		{
			Id = "MineCopper",
			TitleKey = "Q_MINE_COPPER",
			Event = "OreMined",
			OreId = "Copper",
			Count = 3,
			TargetTag = "OreNode",
			TargetOre = "Copper",
			RewardXp = 20,
			RewardGold = 0,
		},
		{
			Id = "ForgeFirst",
			TitleKey = "Q_FORGE_FIRST",
			Event = "WeaponForged",
			Count = 1,
			TargetTag = "Forge",
			RewardXp = 30,
			RewardGold = 10,
		},
		{
			Id = "EquipWeapon",
			TitleKey = "Q_EQUIP",
			Event = "WeaponEquipped",
			Count = 1,
			RewardXp = 10,
			RewardGold = 0,
		},
		{
			Id = "VisitMerchant",
			TitleKey = "Q_MERCHANT",
			Event = "MerchantOpened",
			Count = 1,
			TargetTag = "Merchant",
			RewardXp = 10,
			RewardGold = 15,
		},
		{
			Id = "EnterGate",
			TitleKey = "Q_ENTER_GATE",
			Event = "GateEntered",
			Count = 1,
			TargetTag = "GatePortal",
			RewardXp = 20,
			RewardGold = 0,
		},
		{
			Id = "ClearGate",
			TitleKey = "Q_CLEAR_GATE",
			Event = "GateCleared",
			Count = 1,
			RewardXp = 60,
			RewardGold = 50,
		},
	},
}

return Tutorial
