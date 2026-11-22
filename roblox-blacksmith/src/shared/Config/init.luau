--!strict
--[[
	ЕДИНАЯ ТОЧКА ВХОДА В БАЛАНС.
	Все числа игры — в соседних модулях этой папки. Код читает их только через Config.
]]

local Config = {
	Ores = require(script.Ores),
	Pickaxes = require(script.Pickaxes),
	Weapons = require(script.Weapons),
	Rarities = require(script.Rarities),
	Traits = require(script.Traits),
	Ranks = require(script.Ranks),
	Forging = require(script.Forging),
	Leveling = require(script.Leveling),
	Mobs = require(script.Mobs),
	Gates = require(script.Gates),
	Combat = require(script.Combat),
	Economy = require(script.Economy),
	Tutorial = require(script.Tutorial),
	Daily = require(script.Daily),
	Codes = require(script.Codes),
	Social = require(script.Social),
	World = require(script.World),
	Trading = require(script.Trading),
	Network = require(script.Network),
	Audio = require(script.Audio),

	Data = {
		StoreName = "PlayerData_v1", -- смена имени = чистый вайп, не трогай без нужды
		SchemaVersion = 1,
	},

	Debug = {
		-- В Studio первый портал появляется через 5 секунд
		StudioFastGates = true,
		-- Выводить в Output события анти-чита
		LogSuspicious = true,
	},

	Tags = {
		OreNode = "OreNode",
		Forge = "Forge",
		Merchant = "Merchant",
		GateSpawn = "GateSpawn",
		GatePortal = "GatePortal",
		Leaderboard = "Leaderboard",
		ShopStall = "ShopStall",
		Spin = "Spin", -- клиент медленно вращает такие детали
	},
}

return Config
