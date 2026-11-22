--!strict
--[[
	ЕЖЕДНЕВНОЕ: награда за вход (7-дневный цикл, серия входов) и задания Системы на день.
	День считается по UTC. Задания выбираются случайно из пула.
]]

export type DailyReward = { Gold: number, Ores: { [string]: number }? }
export type DailyQuest = {
	Id: string,
	TitleKey: string,
	Event: string,
	Count: number,
	RewardGold: number,
	RewardXp: number,
}

local Daily: {
	Rewards: { DailyReward },
	QuestCount: number,
	QuestPool: { DailyQuest },
} = {
	Rewards = {
		{ Gold = 50 },
		{ Gold = 80, Ores = { Copper = 6 } },
		{ Gold = 120 },
		{ Gold = 150, Ores = { Iron = 4 } },
		{ Gold = 200 },
		{ Gold = 260, Ores = { Iron = 6 } },
		{ Gold = 400, Ores = { Moonsilver = 3 } },
	},
	QuestCount = 3,
	QuestPool = {
		{ Id = "Mine20", TitleKey = "DQ_MINE", Event = "OreMined", Count = 20, RewardGold = 60, RewardXp = 80 },
		{ Id = "Forge3", TitleKey = "DQ_FORGE", Event = "WeaponForged", Count = 3, RewardGold = 70, RewardXp = 90 },
		{ Id = "Kill25", TitleKey = "DQ_KILL", Event = "MobKilled", Count = 25, RewardGold = 90, RewardXp = 120 },
		{ Id = "Gate1", TitleKey = "DQ_GATE", Event = "GateCleared", Count = 1, RewardGold = 120, RewardXp = 150 },
		{ Id = "Parry5", TitleKey = "DQ_PARRY", Event = "Parry", Count = 5, RewardGold = 80, RewardXp = 100 },
		{ Id = "Sell5", TitleKey = "DQ_SELL", Event = "ItemSold", Count = 5, RewardGold = 40, RewardXp = 60 },
		{ Id = "Quality80", TitleKey = "DQ_QUALITY", Event = "QualityForge80", Count = 1, RewardGold = 100, RewardXp = 120 },
	},
}

return Daily
