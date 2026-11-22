--!strict
--[[
	ВРАТА (ДАНЖИ).
	Порталы появляются на Part с тегом "GateSpawn". Новый тип Врат = новая запись в Types.
	Арена: ServerStorage/Arenas/<Arena> (Model, внутри Part с именами "PlayerSpawn" и "MobSpawn").
	Если модели нет — сервер построит простую арену-заглушку.
]]

export type WaveEntry = { Mob: string, Count: number }
export type OreDrop = { OreId: string, Chance: number, Min: number, Max: number }
export type GateTypeDef = {
	Id: string,
	Rank: string,
	NameKey: string,
	RequiredLevel: number,
	SpawnWeight: number,
	Arena: string,
	Waves: { { WaveEntry } },
	Boss: string,
	Rewards: { Gold: number, Xp: number, OreDrops: { OreDrop } },
	PortalColor: Color3,
}

local Gates = {
	FirstSpawnDelaySeconds = 25,
	SpawnIntervalSeconds = 90,
	MaxActivePortals = 2,
	PortalLifetimeSeconds = 240,
	LobbyCountdownSeconds = 20,
	MaxPartySize = 4,
	LobbyLeaveRadius = 60, -- ушёл дальше от портала — вылетаешь из группы
	RunTimeLimitSeconds = 300,
	WaveStartDelaySeconds = 3,
	ReturnDelaySeconds = 6, -- через сколько секунд после победы/поражения вернуть в лобби

	-- Масштаб сложности под группу: +60% HP мобам за каждого игрока сверх первого
	HealthPerExtraPlayer = 0.6,
	-- Бонус опыта за игру в группе: +10% за каждого игрока сверх первого
	PartyXpBonusPerPlayer = 0.1,

	Arena = {
		Origin = Vector3.new(6000, 300, 0), -- далеко от лобби
		SlotSpacing = 800,
		MaxConcurrentRuns = 6,
		FallbackSize = 140,
	},

	Types = {
		E = {
			Id = "E",
			Rank = "E",
			NameKey = "GATE_E",
			RequiredLevel = 1,
			SpawnWeight = 1,
			Arena = "GateArena",
			Waves = {
				{ { Mob = "Ashling", Count = 4 } },
				{ { Mob = "Ashling", Count = 3 }, { Mob = "CinderHound", Count = 2 } },
			},
			Boss = "CinderWarden",
			Rewards = {
				Gold = 60,
				Xp = 150,
				OreDrops = {
					{ OreId = "Copper", Chance = 1.0, Min = 2, Max = 4 },
					{ OreId = "Iron", Chance = 0.6, Min = 1, Max = 3 },
					{ OreId = "Moonsilver", Chance = 0.12, Min = 1, Max = 1 },
				},
			},
			PortalColor = Color3.fromRGB(60, 170, 255),
		},
	} :: { [string]: GateTypeDef },
}

return Gates
