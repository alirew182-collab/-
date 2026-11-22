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
	MaxActivePortals = 3,
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

	-- КРАСНЫЕ ВРАТА: редкое событие — мобы сильнее, награда больше
	RedGate = {
		Chance = 0.1,
		HealthMult = 1.6,
		DamageMult = 1.3,
		RewardMult = 2.5,
		Color = Color3.fromRGB(255, 40, 40),
	},

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
		D = {
			Id = "D",
			Rank = "D",
			NameKey = "GATE_D",
			RequiredLevel = 8,
			SpawnWeight = 0.6,
			Arena = "GateArena",
			Waves = {
				{ { Mob = "QuarryCrawler", Count = 4 } },
				{ { Mob = "QuarryCrawler", Count = 3 }, { Mob = "CinderHound", Count = 3 } },
				{ { Mob = "CinderHound", Count = 5 } },
			},
			Boss = "StoneColossus",
			Rewards = {
				Gold = 180,
				Xp = 600,
				OreDrops = {
					{ OreId = "Iron", Chance = 1.0, Min = 3, Max = 6 },
					{ OreId = "Moonsilver", Chance = 0.4, Min = 1, Max = 2 },
				},
			},
			PortalColor = Color3.fromRGB(120, 255, 140),
		},
		C = {
			Id = "C",
			Rank = "C",
			NameKey = "GATE_C",
			RequiredLevel = 15,
			SpawnWeight = 0.35,
			Arena = "GateArena",
			Waves = {
				{ { Mob = "FrostWisp", Count = 4 } },
				{ { Mob = "FrostWisp", Count = 3 }, { Mob = "GraveSentinel", Count = 2 } },
				{ { Mob = "GraveSentinel", Count = 4 } },
			},
			Boss = "RimeTyrant",
			Rewards = {
				Gold = 450,
				Xp = 1500,
				OreDrops = {
					{ OreId = "Moonsilver", Chance = 1.0, Min = 2, Max = 4 },
				},
			},
			PortalColor = Color3.fromRGB(190, 110, 255),
		},
	} :: { [string]: GateTypeDef },
}

return Gates
