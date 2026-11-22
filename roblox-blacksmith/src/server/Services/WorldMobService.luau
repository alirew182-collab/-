--!strict
--[[
	МОБЫ ОТКРЫТОГО МИРА: держат заданное число мобов в каждой зоне (Config.World.MobSpawns),
	возрождают их по таймеру, выдают награды (опыт, золото, руда).
	ЭЛИТНЫЙ МОНСТР: по таймеру появляется в зоне, объявление на весь сервер,
	награду получают все, кто нанёс хотя бы MinDamageShare его здоровья.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Char = require(script.Parent.Parent.Util.Char)
local WorldBuilder = require(script.Parent.Parent.World.WorldBuilder)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local LevelService = require(script.Parent.LevelService)
local MobService = require(script.Parent.MobService)
local ProgressService = require(script.Parent.ProgressService)
local SystemService = require(script.Parent.SystemService)

local WorldMobService = {}

local W = Config.World
local rng = Random.new()

type Spawner = {
	Def: any,
	Zone: any,
	RunId: string,
	Alive: { [any]: boolean },
	Count: number,
	Pending: { number }, -- времена будущих возрождений
}

local spawners: { Spawner } = {}
local byRunId: { [string]: Spawner } = {}
local elite: any = nil
local eliteDamage: { [Player]: number } = {}

local function playersNear(zone: any, extra: number): { Player }
	local list = {}
	local center = zone.Center
	for _, player in Players:GetPlayers() do
		local root = Char.Root(player)
		if root and Char.IsAlive(player) then
			local offset = root.Position - center
			if Vector3.new(offset.X, 0, offset.Z).Magnitude <= zone.Radius + extra then
				table.insert(list, player)
			end
		end
	end
	return list
end

local function spawnPoint(zone: any): Vector3
	for _ = 1, 12 do
		local angle = rng:NextNumber(0, math.pi * 2)
		local r = rng:NextNumber(12, zone.Radius * 0.8)
		local x, z = zone.Center.X + math.cos(angle) * r, zone.Center.Z + math.sin(angle) * r
		local y = WorldBuilder.GroundAt(x, z)
		if y < 8 then
			return Vector3.new(x, y + 3, z)
		end
	end
	return zone.Center + Vector3.new(0, 4, 0)
end

local function spawnOne(spawner: Spawner)
	local zone = spawner.Zone
	local pos = spawnPoint(zone)
	local mob = MobService:Spawn(spawner.Def.Mob, CFrame.new(pos), {
		RunId = spawner.RunId,
		Home = pos,
		LeashRadius = W.LeashRadius,
		AggroRadius = W.AggroRadius,
		GetTargets = function()
			return playersNear(zone, W.LeashRadius)
		end,
	})
	if mob then
		spawner.Alive[mob] = true
		spawner.Count += 1
	end
end

local function rollDrops(player: Player, drops: { any }?)
	if not drops then
		return
	end
	for _, drop in drops do
		if rng:NextNumber() <= drop.Chance then
			local count = rng:NextInteger(drop.Min, drop.Max)
			EconomyService:AddOre(player, drop.OreId, count)
			SystemService:Toast(player, "TOAST_ORE", { count = count, ore = "@" .. Config.Ores[drop.OreId].NameKey }, "ore")
		end
	end
end

local function rewardKill(player: Player, def: any)
	if not DataService:IsLoaded(player) then
		return
	end
	LevelService:AddXp(player, def.XpReward)
	EconomyService:AddGold(player, def.GoldReward)
	DataService:AddStat(player, "MobsKilled", 1)
	rollDrops(player, def.Drops)
	ProgressService:Record(player, "MobKilled")
end

local function spawnElite()
	local E = W.Elite
	local zone = W.Zones[E.Zone]
	if not zone then
		return
	end
	table.clear(eliteDamage)
	elite = MobService:Spawn(E.Mob, CFrame.new(spawnPoint(zone) + Vector3.new(0, 4, 0)), {
		RunId = "elite",
		Home = zone.Center,
		LeashRadius = zone.Radius,
		AggroRadius = 55,
		HealthMult = 1 + math.max(0, #Players:GetPlayers() - 1) * 0.3,
		GetTargets = function()
			return playersNear(zone, 20)
		end,
	})
	if elite then
		SystemService:Announce("Elite", "SYS_ELITE_TITLE", "SYS_ELITE_BODY", {
			boss = "@" .. Config.Mobs[E.Mob].NameKey,
			zone = "@" .. zone.NameKey,
		})
	end
end

local function onEliteDied(mob: any)
	local E = W.Elite
	elite = nil
	local need = mob.MaxHealth * E.MinDamageShare
	for player, damage in eliteDamage do
		if player.Parent == Players and damage >= need and DataService:IsLoaded(player) then
			EconomyService:AddGold(player, E.Rewards.Gold)
			LevelService:AddXp(player, E.Rewards.Xp)
			rollDrops(player, mob.Def.Drops)
			DataService:AddStat(player, "EliteKills", 1)
			DataService:AddStat(player, "BossesKilled", 1)
			ProgressService:Record(player, "MobKilled")
			SystemService:Notify(player, "Reward", "SYS_ELITE_DEFEATED_TITLE", "SYS_REWARD_LINE", {
				boss = "@" .. mob.Def.NameKey,
				gold = E.Rewards.Gold,
				xp = E.Rewards.Xp,
			})
		end
	end
	table.clear(eliteDamage)
end

function WorldMobService:Start()
	if not WorldBuilder.Built and not W.MobsOnCustomMap then
		return
	end
	for i, def in W.MobSpawns do
		local zone = W.Zones[def.Zone]
		if zone and Config.Mobs[def.Mob] then
			local spawner: Spawner = {
				Def = def,
				Zone = zone,
				RunId = `world:{i}`,
				Alive = {},
				Count = 0,
				Pending = {},
			}
			table.insert(spawners, spawner)
			byRunId[spawner.RunId] = spawner
			for _ = 1, def.Max do
				spawnOne(spawner)
			end
		end
	end

	MobService.MobDamaged:Connect(function(mob: any, amount: number, attacker: Player?)
		if mob == elite and attacker then
			eliteDamage[attacker] = (eliteDamage[attacker] or 0) + amount
		end
	end)

	MobService.MobDied:Connect(function(mob: any, killer: Player?)
		if mob.RunId == "elite" then
			onEliteDied(mob)
			return
		end
		local spawner = byRunId[mob.RunId]
		if not spawner then
			return
		end
		if spawner.Alive[mob] then
			spawner.Alive[mob] = nil
			spawner.Count -= 1
			table.insert(spawner.Pending, os.clock() + spawner.Def.RespawnSeconds)
		end
		if killer then
			rewardKill(killer, mob.Def)
		end
	end)

	task.spawn(function()
		local nextElite = os.clock() + W.Elite.FirstDelaySeconds
		while true do
			task.wait(1)
			local now = os.clock()
			for _, spawner in spawners do
				for i = #spawner.Pending, 1, -1 do
					if now >= spawner.Pending[i] then
						table.remove(spawner.Pending, i)
						if spawner.Count < spawner.Def.Max then
							spawnOne(spawner)
						end
					end
				end
			end
			if elite == nil and now >= nextElite then
				nextElite = now + W.Elite.IntervalSeconds
				local ok, err = pcall(spawnElite)
				if not ok then
					warn("[WorldMobs] элита:", err)
				end
			end
		end
	end)
end

return WorldMobService
