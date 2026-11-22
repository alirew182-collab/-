--!strict
--[[
	МИР. Если в карте нет ни одного объекта с тегом OreNode, сервер сам строит полный мир
	(Terrain + постройки): город, 4 зоны, озеро, горы по краю. Как только поставишь свои
	объекты с тегами — автопостройка отключится (или выключи AutoBuild вручную).

	Зоны используются и для автопостройки, и для мобов, и для баннера «Ты вошёл в зону».
]]

export type ZoneDef = {
	Id: string,
	NameKey: string,
	Center: Vector3,
	Radius: number,
	Level: number, -- рекомендуемый уровень (баннер)
	Safe: boolean, -- мобы сюда не заходят
	Music: string, -- ключ в Config.Audio
}

export type MobSpawnDef = { Zone: string, Mob: string, Max: number, RespawnSeconds: number }

local World = {
	AutoBuild = true,
	Seed = 1337, -- одинаковый мир при каждом запуске
	Size = 760,
	RemoveDefaultBaseplate = true, -- убрать Baseplate/SpawnLocation шаблона Studio при автопостройке

	Zones = {
		Town = {
			Id = "Town",
			NameKey = "ZONE_TOWN",
			Center = Vector3.new(0, 0, 0),
			Radius = 70,
			Level = 1,
			Safe = true,
			Music = "MusicTown",
		},
		CopperHills = {
			Id = "CopperHills",
			NameKey = "ZONE_COPPER",
			Center = Vector3.new(-200, 0, 20),
			Radius = 100,
			Level = 1,
			Safe = false,
			Music = "MusicWild",
		},
		IronQuarry = {
			Id = "IronQuarry",
			NameKey = "ZONE_QUARRY",
			Center = Vector3.new(10, 0, -210),
			Radius = 100,
			Level = 4,
			Safe = false,
			Music = "MusicWild",
		},
		MoonGrotto = {
			Id = "MoonGrotto",
			NameKey = "ZONE_GROTTO",
			Center = Vector3.new(210, 0, -10),
			Radius = 95,
			Level = 7,
			Safe = false,
			Music = "MusicWild",
		},
		GatePlains = {
			Id = "GatePlains",
			NameKey = "ZONE_PLAINS",
			Center = Vector3.new(0, 0, 210),
			Radius = 105,
			Level = 1,
			Safe = true,
			Music = "MusicGate",
		},
	} :: { [string]: ZoneDef },

	-- Сколько руды ставить в зону при автопостройке
	OreNodes = {
		{ Zone = "CopperHills", OreId = "Copper", Count = 14 },
		{ Zone = "Town", OreId = "Copper", Count = 3 }, -- рядом со спавном, для обучения
		{ Zone = "IronQuarry", OreId = "Iron", Count = 12 },
		{ Zone = "MoonGrotto", OreId = "Moonsilver", Count = 9 },
	},

	-- Мобы открытого мира
	MobSpawns = {
		{ Zone = "CopperHills", Mob = "EmberSlime", Max = 7, RespawnSeconds = 10 },
		{ Zone = "IronQuarry", Mob = "QuarryCrawler", Max = 7, RespawnSeconds = 12 },
		{ Zone = "MoonGrotto", Mob = "FrostWisp", Max = 6, RespawnSeconds = 15 },
	} :: { MobSpawnDef },
	-- Мобы открытого мира появляются только в автопостроенном мире (на своей карте зоны могут не совпасть).
	-- Поставь true, если твоя карта повторяет координаты зон из этого файла.
	MobsOnCustomMap = false,
	AggroRadius = 38,
	LeashRadius = 70, -- дальше от точки появления моб не побежит

	-- Элитный монстр: появляется по таймеру, объявление на весь сервер, награда всем участникам
	Elite = {
		Mob = "StoneColossus",
		Zone = "IronQuarry",
		IntervalSeconds = 480,
		FirstDelaySeconds = 120,
		Rewards = { Gold = 250, Xp = 500 },
		MinDamageShare = 0.03, -- нанёс хотя бы 3% HP — получаешь награду
	},

	Lighting = {
		ClockTime = 14.5,
		Brightness = 2.2,
		AtmosphereDensity = 0.3,
	},
}

return World
