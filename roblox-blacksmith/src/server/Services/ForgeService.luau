--!strict
--[[
	КОВКА. Наковальня = Part/Model с тегом "Forge".

	Поток:
	1. Игрок жмёт подсказку «Ковать» → клиенту открывается окно кузни.
	2. ForgeStart(type, ore): сервер проверяет дистанцию, уровень, руду, место в инвентаре
	   и выдаёт сессию со СЛУЧАЙНЫМИ параметрами мини-игры (зона мехов, линия заливки, ритм).
	3. Клиент проводит мини-игру и шлёт сырые замеры: доля времени в зоне, уровень заливки,
	   отклонения ударов от ритма.
	4. ForgeSubmit: сервер проверяет сессию и реальное время (быстрее физически невозможного → отказ),
	   сам считает качество, списывает руду и создаёт оружие.

	Честно: мини-игру на клиенте нельзя сделать 100% защищённой от читов. Мы ограничиваем вред:
	параметры случайные и знает их только эта сессия, время проверяется сервером, качество влияет
	на урон лишь в пределах QualityMultMin..Max, а подозрительные серии 100% пишутся в лог.
]]

local CollectionService = game:GetService("CollectionService")
local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local Guards = require(script.Parent.Parent.Util.Guards)
local Proximity = require(script.Parent.Parent.Util.Proximity)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local InventoryService = require(script.Parent.InventoryService)
local LevelService = require(script.Parent.LevelService)
local NetService = require(script.Parent.NetService)
local SystemService = require(script.Parent.SystemService)
local TutorialService = require(script.Parent.TutorialService)

local ForgeService = {}

type Session = {
	Id: string,
	Type: string,
	Ore: string,
	StartedAt: number,
	Params: {
		Bellows: { Duration: number, ZoneMin: number, ZoneMax: number, Decay: number, PerPump: number },
		Pour: { Target: number, FillPerSecond: number },
		Hammer: { Beats: number, Interval: number, LeadIn: number },
	},
}

local sessions: { [Player]: Session } = {}
local perfectStreak: { [Player]: number } = {}
local rng = Random.new()

local F = Config.Forging

local function makeParams()
	local zoneCenter = rng:NextNumber(F.Bellows.ZoneCenterMin, F.Bellows.ZoneCenterMax)
	return {
		Bellows = {
			Duration = F.Bellows.DurationSeconds,
			ZoneMin = zoneCenter - F.Bellows.ZoneWidth / 2,
			ZoneMax = zoneCenter + F.Bellows.ZoneWidth / 2,
			Decay = F.Bellows.HeatDecayPerSecond,
			PerPump = F.Bellows.HeatPerPump,
		},
		Pour = {
			Target = rng:NextNumber(F.Pour.TargetMin, F.Pour.TargetMax),
			FillPerSecond = F.Pour.FillPerSecond,
		},
		Hammer = {
			Beats = F.Hammer.Beats,
			Interval = rng:NextNumber(F.Hammer.IntervalMin, F.Hammer.IntervalMax),
			LeadIn = F.Hammer.LeadInSeconds,
		},
	}
end

local function minDuration(session: Session): number
	local p = session.Params
	return p.Bellows.Duration + F.Pour.MinSeconds + p.Hammer.LeadIn + p.Hammer.Interval * p.Hammer.Beats
end

-- Счёт шагов 0..1 — считает только сервер
local function scoreBellows(fractionInZone: number): number
	return math.clamp(fractionInZone / F.Bellows.FullScoreFraction, 0, 1)
end

local function scorePour(level: number, target: number): number
	local diff = math.abs(level - target)
	if diff <= F.Pour.PerfectTolerance then
		return 1
	end
	return math.clamp(1 - (diff - F.Pour.PerfectTolerance) / (F.Pour.Tolerance - F.Pour.PerfectTolerance), 0, 1)
end

local function scoreHammer(offsets: { number }, beats: number): number
	local total = 0
	for i = 1, beats do
		local o = offsets[i]
		if o ~= nil then
			local a = math.abs(o)
			if a <= F.Hammer.PerfectWindow then
				total += 1
			elseif a <= F.Hammer.HitWindow then
				total += 1 - (a - F.Hammer.PerfectWindow) / (F.Hammer.HitWindow - F.Hammer.PerfectWindow)
			end
		end
	end
	return total / beats
end

local function startForge(player: Player, weaponType: any, oreId: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(weaponType, 32) or not Guards.String(oreId, 32) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local def = Config.Weapons[weaponType]
	local ore = Config.Ores[oreId]
	if not def or not ore then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	if not Proximity.NearTagged(player, Config.Tags.Forge, Config.Economy.InteractMaxDistance) then
		return { ok = false, err = "ERR_TOO_FAR" }
	end
	if data.Level < ore.RequiredLevel then
		return { ok = false, err = "ERR_LEVEL_LOW", args = { level = ore.RequiredLevel } }
	end
	if (data.Ores[oreId] or 0) < def.OreCost then
		return { ok = false, err = "ERR_NOT_ENOUGH_ORE" }
	end
	if InventoryService:IsFull(player) then
		return { ok = false, err = "ERR_INVENTORY_FULL" }
	end

	local session: Session = {
		Id = HttpService:GenerateGUID(false),
		Type = weaponType,
		Ore = oreId,
		StartedAt = os.clock(),
		Params = makeParams(),
	}
	sessions[player] = session
	return { ok = true, sessionId = session.Id, params = session.Params }
end

local function submitForge(player: Player, sessionId: any, results: any)
	local session = sessions[player]
	if not session or sessionId ~= session.Id then
		return { ok = false, err = "ERR_NO_SESSION" }
	end
	sessions[player] = nil -- сессия одноразовая

	local elapsed = os.clock() - session.StartedAt
	if elapsed > F.SessionTimeoutSeconds then
		return { ok = false, err = "ERR_NO_SESSION" }
	end
	if elapsed < minDuration(session) * F.MinDurationTolerance then
		if Config.Debug.LogSuspicious then
			warn(`[Forge] {player.Name}: слишком быстро ({string.format("%.2f", elapsed)}с)`)
		end
		return { ok = false, err = "ERR_TOO_FAST" }
	end

	if type(results) ~= "table" then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local beats = session.Params.Hammer.Beats
	if
		not Guards.Number(results.Bellows, 0, 1)
		or not Guards.Number(results.Pour, 0, 1.5)
		or not Guards.NumberArray(results.Hammer, beats, -60, 60)
	then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end

	local sb = scoreBellows(results.Bellows)
	local sp = scorePour(results.Pour, session.Params.Pour.Target)
	local sh = scoreHammer(results.Hammer, beats)
	local quality = math.floor(100 * (F.Bellows.Weight * sb + F.Pour.Weight * sp + F.Hammer.Weight * sh) + 0.5)

	-- анти-чит: серия идеальных ковок подряд
	if sb == 1 and sp == 1 and sh == 1 then
		perfectStreak[player] = (perfectStreak[player] or 0) + 1
		if perfectStreak[player] >= 5 and Config.Debug.LogSuspicious then
			warn(`[Forge] {player.Name}: {perfectStreak[player]} идеальных ковок подряд`)
		end
	else
		perfectStreak[player] = 0
	end

	local def = Config.Weapons[session.Type]
	local ore = Config.Ores[session.Ore]
	if InventoryService:IsFull(player) then
		return { ok = false, err = "ERR_INVENTORY_FULL" }
	end
	if not EconomyService:TrySpendOre(player, session.Ore, def.OreCost) then
		return { ok = false, err = "ERR_NOT_ENOUGH_ORE" }
	end
	local weapon = InventoryService:AddWeapon(player, session.Type, session.Ore, quality, "Common")
	if not weapon then
		EconomyService:AddOre(player, session.Ore, def.OreCost) -- вернуть руду
		return { ok = false, err = "ERR_INVENTORY_FULL" }
	end

	local data = DataService:Get(player)
	DataService:AddStat(player, "WeaponsForged", 1)
	if data and quality > (data.Stats.BestQuality or 0) then
		data.Stats.BestQuality = quality
	end
	LevelService:AddXp(player, F.XpBase + def.OreCost * ore.XpPerOre * F.XpOreFactor)

	local damage = WeaponMath.Damage(weapon)
	SystemService:Notify(
		player,
		"Forge",
		"SYS_FORGE_TITLE",
		if quality >= 95 then "SYS_FORGE_BODY_MASTER" else "SYS_FORGE_BODY",
		{
			-- имя оружия переводит клиент на своём языке
			weapon = `@WEAPON:{session.Type}:{session.Ore}`,
			quality = quality,
			damage = damage,
		}
	)
	TutorialService:Record(player, "WeaponForged")

	return {
		ok = true,
		weapon = weapon,
		quality = quality,
		damage = damage,
		scores = { Bellows = sb, Pour = sp, Hammer = sh },
	}
end

local function setupForge(instance: Instance)
	local anchor = Proximity.AnchorPart(instance)
	if not anchor or anchor:FindFirstChild("GamePrompt") then
		return
	end
	local prompt = Prompts.Create(anchor, "PROMPT_FORGE", { Distance = 12 })
	prompt.Triggered:Connect(function(player: Player)
		if DataService:IsLoaded(player) then
			NetService:Fire(player, "OpenPanel", { Panel = "Forge" })
		end
	end)
end

function ForgeService:Start()
	NetService:OnInvoke("ForgeStart", startForge)
	NetService:OnInvoke("ForgeSubmit", submitForge)
	NetService:OnEvent("ForgeCancel", function(player: Player)
		sessions[player] = nil
	end)

	for _, instance in CollectionService:GetTagged(Config.Tags.Forge) do
		setupForge(instance)
	end
	CollectionService:GetInstanceAddedSignal(Config.Tags.Forge):Connect(setupForge)

	Players.PlayerRemoving:Connect(function(player)
		sessions[player] = nil
		perfectStreak[player] = nil
	end)
end

return ForgeService
