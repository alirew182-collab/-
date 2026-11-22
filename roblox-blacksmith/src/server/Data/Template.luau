--!strict
--[[
	ШАБЛОН ДАННЫХ ИГРОКА (схема v1).
	Новое поле: просто добавь сюда — ProfileStore:Reconcile() допишет его старым профилям.
	Переименование/преобразование поля: подними Config.Data.SchemaVersion и добавь шаг в Migrations.
	Значения nil в шаблоне нельзя — используй "" / 0 / {}.
]]

local Config = require(game:GetService("ReplicatedStorage").Shared.Config)

local Template = {
	SchemaVersion = Config.Data.SchemaVersion,
	Gold = Config.Economy.StartingGold,
	Level = 1,
	Xp = 0,
	Rank = Config.Leveling.StartingRank,
	Ores = {} :: { [string]: number },
	Weapons = {} :: { [string]: any },
	EquippedWeapon = "",
	Pickaxe = Config.Economy.StartingPickaxe,
	OwnedPickaxes = { [Config.Economy.StartingPickaxe] = true } :: { [string]: boolean },
	Tutorial = {
		Step = 1,
		Progress = 0,
		Done = false,
		Awakened = false,
	},
	Stats = {
		OresMined = 0,
		WeaponsForged = 0,
		BestQuality = 0,
		MobsKilled = 0,
		BossesKilled = 0,
		GatesCleared = 0,
		GoldEarned = 0,
		Parries = 0,
		BestDamage = 0,
		EliteKills = 0,
		MythicForged = 0,
	} :: { [string]: number },
	Settings = {
		Language = "", -- "" = определить по языку Roblox
		Sfx = true,
		Music = true,
		DamageNumbers = true,
		LowGraphics = false,
	},
	Daily = {
		LastClaimDay = 0, -- номер дня (UTC), когда забрана награда за вход
		Streak = 0,
		QuestDay = 0,
		Quests = {} :: { any }, -- { { Id, Progress, Done } }
	},
	RedeemedCodes = {} :: { [string]: boolean },
	ProcessedReceipts = {} :: { [string]: boolean }, -- для идемпотентного ProcessReceipt (Этап 3)
	FirstJoinAt = 0,
	LastJoinAt = 0,
}

return Template
