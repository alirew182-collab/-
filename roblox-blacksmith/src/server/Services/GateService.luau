--!strict
--[[
	ВРАТА (ДАНЖИ).
	1. По таймеру на точке с тегом "GateSpawn" открывается портал (объявление на весь сервер).
	2. Игроки жмут «Войти во Врата» → группа до 4 человек, обратный отсчёт; лидер может начать раньше.
	3. Группа переносится на свою арену (далеко от лобби, слоты не пересекаются), волны мобов → босс.
	4. Победа: награда всем участникам (+бонус за группу). Поражение: все пали или вышло время.
	Портал одноразовый: после старта забега он исчезает.
]]

local CollectionService = game:GetService("CollectionService")
local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local ServerStorage = game:GetService("ServerStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Format = require(ReplicatedStorage.Shared.Format)
local Char = require(script.Parent.Parent.Util.Char)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local LevelService = require(script.Parent.LevelService)
local MobService = require(script.Parent.MobService)
local NetService = require(script.Parent.NetService)
local SystemService = require(script.Parent.SystemService)
local ProgressService = require(script.Parent.ProgressService)

local GateService = {}

local G = Config.Gates

type Portal = {
	Id: string,
	Gate: any,
	IsRed: boolean,
	Part: BasePart,
	Label: TextLabel,
	SpawnPoint: Instance?,
	ExpiresAt: number,
	Party: { Player },
	CountdownEndsAt: number?,
	State: string, -- open | closed
}

type Arena = {
	Root: Instance,
	Center: Vector3,
	Size: number,
	PlayerSpawns: { CFrame },
	MobSpawns: { CFrame },
}

type Run = {
	Id: string,
	Gate: any,
	IsRed: boolean,
	Slot: number,
	Arena: Arena,
	Players: { Player },
	Dead: { [Player]: boolean },
	Wave: number,
	Mobs: { [any]: boolean },
	Boss: any?,
	BossPhase: boolean,
	EndsAt: number,
	State: string, -- fighting | ended
	ReturnCFrame: CFrame,
	LastBossUpdate: number,
}

local portals: { Portal } = {}
local runs: { [string]: Run } = {}
local playerPortal: { [Player]: Portal } = {}
local playerRun: { [Player]: Run } = {}
local slots: { [number]: Run } = {}
local arenas: { [string]: Arena } = {}
local rng = Random.new()

local arenasFolder: Folder
local portalsFolder: Folder

local function now(): number
	return workspace:GetServerTimeNow()
end

local function partyBonus(count: number): number
	return 1 + math.max(0, count - 1) * G.PartyXpBonusPerPlayer
end

-- ===== Арены =====

local function collectSpawns(root: Instance, name: string): { CFrame }
	local list = {}
	for _, d in root:GetDescendants() do
		if d:IsA("BasePart") and d.Name == name then
			table.insert(list, d.CFrame + Vector3.new(0, 3, 0))
			d.Transparency = 1
			d.CanCollide = false
		end
	end
	return list
end

local function buildFallbackArena(origin: Vector3): Instance
	local size = G.Arena.FallbackSize
	local model = Instance.new("Model")
	model.Name = "FallbackArena"

	local function part(name: string, partSize: Vector3, cf: CFrame, color: Color3, material: Enum.Material, transparency: number?)
		local p = Instance.new("Part")
		p.Name = name
		p.Size = partSize
		p.CFrame = cf
		p.Anchored = true
		p.Color = color
		p.Material = material
		p.Transparency = transparency or 0
		p.Parent = model
		return p
	end

	part("Floor", Vector3.new(size, 2, size), CFrame.new(origin), Color3.fromRGB(45, 35, 40), Enum.Material.Basalt)
	local h = 16
	local wallColor = Color3.fromRGB(60, 140, 255)
	part("WallN", Vector3.new(size, h, 2), CFrame.new(origin + Vector3.new(0, h / 2, -size / 2)), wallColor, Enum.Material.ForceField, 0.2)
	part("WallS", Vector3.new(size, h, 2), CFrame.new(origin + Vector3.new(0, h / 2, size / 2)), wallColor, Enum.Material.ForceField, 0.2)
	part("WallE", Vector3.new(2, h, size), CFrame.new(origin + Vector3.new(size / 2, h / 2, 0)), wallColor, Enum.Material.ForceField, 0.2)
	part("WallW", Vector3.new(2, h, size), CFrame.new(origin + Vector3.new(-size / 2, h / 2, 0)), wallColor, Enum.Material.ForceField, 0.2)

	local spawnColor = Color3.new(1, 1, 1)
	for i = 1, 4 do
		local x = (i - 2.5) * 6
		part("PlayerSpawn", Vector3.new(2, 1, 2), CFrame.new(origin + Vector3.new(x, 1.5, size / 2 - 15)), spawnColor, Enum.Material.Neon)
	end
	for i = 1, 6 do
		local x = (i - 3.5) * 14
		part("MobSpawn", Vector3.new(2, 1, 2), CFrame.new(origin + Vector3.new(x, 1.5, -size / 2 + 20)), spawnColor, Enum.Material.Neon)
	end
	return model
end

local function getArena(slot: number, gate: any): Arena
	local key = `{slot}:{gate.Arena}`
	local cached = arenas[key]
	if cached then
		return cached
	end
	local origin = G.Arena.Origin + Vector3.new(G.Arena.SlotSpacing * (slot - 1), 0, 0)
	local folder = ServerStorage:FindFirstChild("Arenas")
	local template = folder and folder:FindFirstChild(gate.Arena)
	local root: Instance
	local size = G.Arena.FallbackSize
	if template and template:IsA("Model") then
		local model = template:Clone()
		model:PivotTo(CFrame.new(origin))
		local _, extents = model:GetBoundingBox()
		size = math.max(extents.X, extents.Z)
		root = model
	else
		root = buildFallbackArena(origin)
	end
	-- арену держим загруженной у клиентов целиком, когда они рядом
	if root:IsA("Model") then
		root.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
	end
	root.Parent = arenasFolder

	local arena: Arena = {
		Root = root,
		Center = origin,
		Size = size,
		PlayerSpawns = collectSpawns(root, "PlayerSpawn"),
		MobSpawns = collectSpawns(root, "MobSpawn"),
	}
	if #arena.PlayerSpawns == 0 then
		arena.PlayerSpawns = { CFrame.new(origin + Vector3.new(0, 5, size / 3)) }
	end
	if #arena.MobSpawns == 0 then
		arena.MobSpawns = { CFrame.new(origin + Vector3.new(0, 5, -size / 3)) }
	end
	arenas[key] = arena
	return arena
end

local function freeSlot(): number?
	for i = 1, G.Arena.MaxConcurrentRuns do
		if slots[i] == nil then
			return i
		end
	end
	return nil
end

-- ===== Состояние для клиента =====

local function sendLobbyState(portal: Portal)
	local t = now()
	for i, player in portal.Party do
		NetService:Fire(player, "GateState", {
			Phase = "lobby",
			IsRed = portal.IsRed,
			GateNameKey = portal.Gate.NameKey,
			Rank = portal.Gate.Rank,
			Count = #portal.Party,
			Max = G.MaxPartySize,
			StartsIn = if portal.CountdownEndsAt then math.max(0, portal.CountdownEndsAt - t) else nil,
			IsLeader = i == 1,
		})
	end
end

local function activeMembers(run: Run): { Player }
	local list = {}
	for _, player in run.Players do
		if playerRun[player] == run and player.Parent == Players then
			table.insert(list, player)
		end
	end
	return list
end

local function sendRunState(run: Run)
	local members = activeMembers(run)
	local alive = 0
	for _, player in members do
		if not run.Dead[player] then
			alive += 1
		end
	end
	local boss = run.Boss
	NetService:FireList(members, "GateState", {
		Phase = "run",
		IsRed = run.IsRed,
		GateNameKey = run.Gate.NameKey,
		Rank = run.Gate.Rank,
		Wave = run.Wave,
		TotalWaves = #run.Gate.Waves,
		BossPhase = run.BossPhase,
		TimeLeft = math.max(0, run.EndsAt - now()),
		Alive = alive,
		Total = #members,
		Boss = if boss
			then { NameKey = boss.Def.NameKey, Health = boss.Health, MaxHealth = boss.MaxHealth }
			else nil,
	})
end

-- ===== Порталы =====

local function removeFromParty(player: Player, toastKey: string?)
	local portal = playerPortal[player]
	if not portal then
		return
	end
	playerPortal[player] = nil
	local index = table.find(portal.Party, player)
	if index then
		table.remove(portal.Party, index)
	end
	if #portal.Party == 0 then
		portal.CountdownEndsAt = nil
	end
	if player.Parent == Players then
		NetService:Fire(player, "GateState", { Phase = "none" })
		if toastKey then
			SystemService:Toast(player, toastKey)
		end
	end
	sendLobbyState(portal)
end

local function closePortal(portal: Portal)
	portal.State = "closed"
	local index = table.find(portals, portal)
	if index then
		table.remove(portals, index)
	end
	for _, player in table.clone(portal.Party) do
		removeFromParty(player, "TOAST_GATE_CLOSED")
	end
	portal.Part:Destroy()
end

local function pickGateType(): any
	local total = 0
	for _, gate in G.Types do
		total += gate.SpawnWeight
	end
	local roll = rng:NextNumber(0, total)
	local last = nil
	for _, gate in G.Types do
		last = gate
		roll -= gate.SpawnWeight
		if roll <= 0 then
			return gate
		end
	end
	return last
end

local warnedNoSpawns = false

local function spawnPortal()
	local used: { [Instance]: boolean } = {}
	for _, other in portals do
		if other.SpawnPoint then
			used[other.SpawnPoint] = true
		end
	end
	local candidates = {}
	for _, point in CollectionService:GetTagged(Config.Tags.GateSpawn) do
		if point:IsA("BasePart") and point:IsDescendantOf(workspace) and not used[point] then
			table.insert(candidates, point)
		end
	end

	local point: BasePart? = nil
	local baseCFrame: CFrame
	if #candidates > 0 then
		point = candidates[rng:NextInteger(1, #candidates)]
		baseCFrame = (point :: BasePart).CFrame
	else
		if #CollectionService:GetTagged(Config.Tags.GateSpawn) > 0 then
			return -- все точки заняты
		end
		if not warnedNoSpawns then
			warnedNoSpawns = true
			warn("[Gates] Нет Part с тегом GateSpawn — портал появится возле центра карты")
		end
		if #portals > 0 then
			return
		end
		baseCFrame = CFrame.new(0, 0.5, 45)
	end

	-- новичкам всегда должны быть доступны Врата самого низкого ранга
	local easiest: any = nil
	for _, g in G.Types do
		if not easiest or g.RequiredLevel < easiest.RequiredLevel then
			easiest = g
		end
	end
	local hasEasy = false
	for _, other in portals do
		if other.Gate == easiest then
			hasEasy = true
		end
	end
	local gate = if hasEasy then pickGateType() else easiest
	if not gate then
		return
	end
	local isRed = rng:NextNumber() < G.RedGate.Chance
	local color = if isRed then G.RedGate.Color else gate.PortalColor

	local part = Instance.new("Part")
	part.Name = "GatePortal"
	part.Shape = Enum.PartType.Cylinder
	part.Size = Vector3.new(0.8, 11, 11)
	part.CFrame = baseCFrame * CFrame.new(0, 6, 0)
	part.Anchored = true
	part.CanCollide = false
	part.CanQuery = true
	part.Material = Enum.Material.Neon
	part.Color = color
	part.Transparency = 0.2
	part:SetAttribute("GateType", gate.Id)

	local light = Instance.new("PointLight")
	light.Color = color
	light.Range = 18
	light.Brightness = 3
	light.Parent = part

	local billboard = Instance.new("BillboardGui")
	billboard.Size = UDim2.fromOffset(160, 50)
	billboard.StudsOffsetWorldSpace = Vector3.new(0, 7.5, 0)
	billboard.MaxDistance = 200
	billboard.AlwaysOnTop = true
	billboard.LightInfluence = 0
	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.Font = Enum.Font.GothamMedium
	label.TextScaled = true
	label.TextColor3 = Color3.new(1, 1, 1)
	label.TextStrokeTransparency = 0.3
	label.Text = `[{gate.Rank}]`
	label.TextColor3 = if isRed then Color3.fromRGB(255, 90, 90) else Color3.new(1, 1, 1)
	label.Parent = billboard
	billboard.Parent = part

	local portal: Portal = {
		Id = HttpService:GenerateGUID(false),
		Gate = gate,
		IsRed = isRed,
		Part = part,
		Label = label,
		SpawnPoint = point,
		ExpiresAt = now() + G.PortalLifetimeSeconds,
		Party = {},
		CountdownEndsAt = nil,
		State = "open",
	}

	local prompt = Prompts.Create(part, "PROMPT_ENTER_GATE", { ObjectKey = gate.NameKey, Distance = 14 })
	prompt.Triggered:Connect(function(player: Player)
		GateService:Join(player, portal)
	end)

	part.Parent = portalsFolder
	CollectionService:AddTag(part, Config.Tags.GatePortal)
	table.insert(portals, portal)

	if isRed then
		SystemService:Announce("Warning", "SYS_REDGATE_TITLE", "SYS_REDGATE_BODY", {
			gate = "@" .. gate.NameKey,
			mult = G.RedGate.RewardMult,
		})
	else
		SystemService:Announce("Info", "SYS_GATE_OPEN_TITLE", "SYS_GATE_OPEN_BODY", {
			gate = "@" .. gate.NameKey,
			minutes = math.floor(G.PortalLifetimeSeconds / 60),
		})
	end
end

function GateService:Join(player: Player, portal: Portal)
	local data = DataService:Get(player)
	if not data or not Char.IsAlive(player) then
		return
	end
	if playerRun[player] then
		SystemService:Error(player, "ERR_IN_GATE")
		return
	end
	if portal.State ~= "open" then
		SystemService:Error(player, "ERR_GATE_BUSY")
		return
	end
	if playerPortal[player] == portal then
		sendLobbyState(portal)
		return
	end
	if data.Level < portal.Gate.RequiredLevel then
		SystemService:Error(player, "ERR_LEVEL_LOW", { level = portal.Gate.RequiredLevel })
		return
	end
	if #portal.Party >= G.MaxPartySize then
		SystemService:Error(player, "ERR_PARTY_FULL")
		return
	end
	if playerPortal[player] then
		removeFromParty(player)
	end

	local others = table.clone(portal.Party)
	table.insert(portal.Party, player)
	playerPortal[player] = portal
	if not portal.CountdownEndsAt then
		portal.CountdownEndsAt = now() + G.LobbyCountdownSeconds
	end
	SystemService:Toast(player, "TOAST_JOINED_PARTY")
	SystemService:ToastList(others, "TOAST_PARTY_JOINED", { name = player.DisplayName })
	sendLobbyState(portal)
end

-- ===== Забег =====

local function teleport(player: Player, cframe: CFrame)
	local character = player.Character
	if not character or not Char.IsAlive(player) then
		return
	end
	pcall(function()
		player:RequestStreamAroundAsync(cframe.Position, 5)
	end)
	if player.Character == character then
		character:PivotTo(cframe)
	end
end

local function runTargets(run: Run): { Player }
	local list = {}
	local radius = run.Arena.Size
	for _, player in run.Players do
		if playerRun[player] == run and not run.Dead[player] then
			local root = Char.Root(player)
			if root and (root.Position - run.Arena.Center).Magnitude < radius then
				table.insert(list, player)
			end
		end
	end
	return list
end

local function randomMobSpawn(run: Run): CFrame
	local spawns = run.Arena.MobSpawns
	local base = spawns[rng:NextInteger(1, #spawns)]
	return base * CFrame.new(rng:NextNumber(-4, 4), 0, rng:NextNumber(-4, 4))
end

local function spawnWave(run: Run, index: number)
	if run.State ~= "fighting" then
		return
	end
	run.Wave = index
	local healthMult = (1 + math.max(0, #activeMembers(run) - 1) * G.HealthPerExtraPlayer)
		* (if run.IsRed then G.RedGate.HealthMult else 1)
	local damageMult = if run.IsRed then G.RedGate.DamageMult else 1
	for _, entry in run.Gate.Waves[index] do
		for _ = 1, entry.Count do
			local mob = MobService:Spawn(entry.Mob, randomMobSpawn(run), {
				RunId = run.Id,
				HealthMult = healthMult,
				DamageMult = damageMult,
				GetTargets = function()
					return runTargets(run)
				end,
			})
			if mob then
				run.Mobs[mob] = true
			end
		end
	end
	SystemService:ToastList(activeMembers(run), "TOAST_WAVE", { wave = index, total = #run.Gate.Waves })
	sendRunState(run)
end

local function spawnBoss(run: Run)
	if run.State ~= "fighting" then
		return
	end
	run.BossPhase = true
	local healthMult = (1 + math.max(0, #activeMembers(run) - 1) * G.HealthPerExtraPlayer)
		* (if run.IsRed then G.RedGate.HealthMult else 1)
	local damageMult = if run.IsRed then G.RedGate.DamageMult else 1
	local boss = MobService:Spawn(run.Gate.Boss, CFrame.new(run.Arena.Center + Vector3.new(0, 6, -run.Arena.Size / 4)), {
		RunId = run.Id,
		HealthMult = healthMult,
		DamageMult = damageMult,
		GetTargets = function()
			return runTargets(run)
		end,
	})
	if not boss then
		return
	end
	run.Boss = boss
	run.Mobs[boss] = true
	SystemService:NotifyList(activeMembers(run), "Boss", "SYS_BOSS_TITLE", "SYS_BOSS_BODY", {
		boss = "@" .. boss.Def.NameKey,
	})
	sendRunState(run)
end

local function finishRun(run: Run, won: boolean)
	if run.State ~= "fighting" then
		return
	end
	run.State = "ended"
	MobService:DespawnRun(run.Id)

	local members = activeMembers(run)
	local gate = run.Gate
	if won then
		local bonus = partyBonus(#members)
		for _, player in members do
			local rewardMult = if run.IsRed then G.RedGate.RewardMult else 1
			local gold = math.floor(gate.Rewards.Gold * rewardMult)
			local xp = math.floor(gate.Rewards.Xp * bonus * rewardMult)
			EconomyService:AddGold(player, gold)
			LevelService:AddXp(player, xp)
			for _, drop in gate.Rewards.OreDrops do
				if rng:NextNumber() <= drop.Chance then
					local count = math.floor(rng:NextInteger(drop.Min, drop.Max) * rewardMult + 0.5)
					EconomyService:AddOre(player, drop.OreId, count)
					SystemService:Toast(player, "TOAST_ORE", {
						count = count,
						ore = "@" .. Config.Ores[drop.OreId].NameKey,
					}, "ore")
				end
			end
			DataService:AddStat(player, "GatesCleared", 1)
			DataService:AddStat(player, "BossesKilled", 1)
			SystemService:Notify(player, "GateCleared", gate.NameKey, "SYS_GATE_CLEARED_BODY", { gold = gold, xp = xp })
			ProgressService:Record(player, "GateCleared")
		end
	else
		SystemService:NotifyList(members, "GateFailed", gate.NameKey, "SYS_GATE_FAILED_BODY")
	end
	NetService:FireList(members, "GateState", { Phase = "ended", Won = won, GateNameKey = gate.NameKey })

	task.delay(G.ReturnDelaySeconds, function()
		for _, player in activeMembers(run) do
			playerRun[player] = nil
			local offset = CFrame.new(rng:NextNumber(-5, 5), 0, rng:NextNumber(-5, 5))
			teleport(player, run.ReturnCFrame * offset)
			NetService:Fire(player, "GateState", { Phase = "none" })
		end
		for _, player in run.Players do
			if playerRun[player] == run then
				playerRun[player] = nil
			end
		end
		slots[run.Slot] = nil
		runs[run.Id] = nil
	end)
end

local function startRun(portal: Portal)
	local slot = freeSlot()
	if not slot then
		portal.CountdownEndsAt = now() + 10
		SystemService:ToastList(portal.Party, "ERR_ARENAS_FULL", nil, "error")
		return
	end

	local members = {}
	for _, player in portal.Party do
		playerPortal[player] = nil
		if player.Parent == Players and Char.IsAlive(player) and not playerRun[player] then
			table.insert(members, player)
		end
	end
	portal.Party = {}
	local returnCFrame = portal.Part.CFrame * CFrame.new(0, -3, 8)
	closePortal(portal)
	if #members == 0 then
		return
	end

	local gate = portal.Gate
	local isRed = portal.IsRed
	local arena = getArena(slot, gate)
	local run: Run = {
		Id = HttpService:GenerateGUID(false),
		Gate = gate,
		IsRed = isRed,
		Slot = slot,
		Arena = arena,
		Players = members,
		Dead = {},
		Wave = 0,
		Mobs = {},
		Boss = nil,
		BossPhase = false,
		EndsAt = now() + G.RunTimeLimitSeconds + G.WaveStartDelaySeconds,
		State = "fighting",
		ReturnCFrame = returnCFrame,
		LastBossUpdate = 0,
	}
	slots[slot] = run
	runs[run.Id] = run

	for i, player in members do
		playerRun[player] = run
		local spawnCf = arena.PlayerSpawns[((i - 1) % #arena.PlayerSpawns) + 1]
		task.spawn(teleport, player, spawnCf)
		ProgressService:Record(player, "GateEntered")
	end
	sendRunState(run)
	task.delay(G.WaveStartDelaySeconds, spawnWave, run, 1)
end

local function leave(player: Player)
	if playerPortal[player] then
		removeFromParty(player, "TOAST_LEFT_PARTY")
		return
	end
	local run = playerRun[player]
	if run then
		playerRun[player] = nil
		NetService:Fire(player, "GateState", { Phase = "none" })
		if run.State == "fighting" then
			teleport(player, run.ReturnCFrame)
			if #activeMembers(run) == 0 then
				finishRun(run, false)
			else
				sendRunState(run)
			end
		end
	end
end

-- ===== Подписки =====

local function onMobDied(mob: any, killer: Player?)
	local run = runs[mob.RunId]
	if not run or run.State ~= "fighting" then
		return
	end
	run.Mobs[mob] = nil
	if killer and playerRun[killer] == run then
		local bonus = partyBonus(#activeMembers(run))
		LevelService:AddXp(killer, mob.Def.XpReward * bonus)
		EconomyService:AddGold(killer, mob.Def.GoldReward)
		DataService:AddStat(killer, "MobsKilled", 1)
		ProgressService:Record(killer, "MobKilled")
	end

	if mob == run.Boss then
		run.Boss = nil
		finishRun(run, true)
		return
	end
	if next(run.Mobs) == nil then
		if run.Wave < #run.Gate.Waves then
			task.delay(1.5, spawnWave, run, run.Wave + 1)
		elseif not run.BossPhase then
			task.delay(1.5, spawnBoss, run)
		end
	end
	sendRunState(run)
end

local function onMobDamaged(mob: any)
	local run = runs[mob.RunId]
	if not run or mob ~= run.Boss then
		return
	end
	local t = os.clock()
	if t - run.LastBossUpdate < 0.2 then
		return
	end
	run.LastBossUpdate = t
	sendRunState(run)
end

local function onCharacterAdded(player: Player, character: Model)
	local humanoid = character:WaitForChild("Humanoid", 10) :: Humanoid?
	if not humanoid then
		return
	end
	humanoid.Died:Connect(function()
		local run = playerRun[player]
		if not run or run.State ~= "fighting" then
			return
		end
		run.Dead[player] = true
		SystemService:Toast(player, "TOAST_DIED")
		local anyAlive = false
		for _, member in activeMembers(run) do
			if not run.Dead[member] then
				anyAlive = true
				break
			end
		end
		if anyAlive then
			sendRunState(run)
		else
			finishRun(run, false)
		end
	end)
end

local function tick()
	local t = now()

	for _, portal in table.clone(portals) do
		if portal.State ~= "open" then
			continue
		end
		for _, player in table.clone(portal.Party) do
			local root = Char.Root(player)
			if player.Parent ~= Players or not root or (root.Position - portal.Part.Position).Magnitude > G.LobbyLeaveRadius then
				removeFromParty(player, "TOAST_LEFT_PARTY")
			end
		end
		if portal.CountdownEndsAt and #portal.Party > 0 then
			if t >= portal.CountdownEndsAt then
				startRun(portal)
				continue
			end
			portal.Label.Text = `[{portal.Gate.Rank}{if portal.IsRed then "!" else ""}]  {Format.Time(portal.CountdownEndsAt - t)}`
			sendLobbyState(portal)
		elseif t >= portal.ExpiresAt then
			closePortal(portal)
		else
			portal.Label.Text = `[{portal.Gate.Rank}{if portal.IsRed then "!" else ""}]  {Format.Time(portal.ExpiresAt - t)}`
		end
	end

	for _, run in runs do
		if run.State == "fighting" then
			if t >= run.EndsAt or #activeMembers(run) == 0 then
				finishRun(run, false)
			else
				sendRunState(run)
			end
		end
	end
end

function GateService:Init()
	arenasFolder = Instance.new("Folder")
	arenasFolder.Name = "GateArenas"
	arenasFolder.Parent = workspace
	portalsFolder = Instance.new("Folder")
	portalsFolder.Name = "GatePortals"
	portalsFolder.Parent = workspace
end

function GateService:Start()
	NetService:OnEvent("GateLeave", leave)
	NetService:OnEvent("GateStart", function(player: Player)
		local portal = playerPortal[player]
		if not portal or portal.State ~= "open" then
			return
		end
		if portal.Party[1] ~= player then
			SystemService:Error(player, "ERR_NOT_LEADER")
			return
		end
		startRun(portal)
	end)

	MobService.MobDied:Connect(onMobDied)
	MobService.MobDamaged:Connect(onMobDamaged)

	local function hookPlayer(player: Player)
		player.CharacterAdded:Connect(function(character)
			onCharacterAdded(player, character)
		end)
		if player.Character then
			task.spawn(onCharacterAdded, player, player.Character)
		end
	end
	Players.PlayerAdded:Connect(hookPlayer)
	for _, player in Players:GetPlayers() do
		hookPlayer(player)
	end
	Players.PlayerRemoving:Connect(function(player)
		leave(player)
		playerPortal[player] = nil
		playerRun[player] = nil
	end)

	task.spawn(function()
		local firstDelay = G.FirstSpawnDelaySeconds
		if RunService:IsStudio() and Config.Debug.StudioFastGates then
			firstDelay = 5
		end
		local nextSpawnAt = now() + firstDelay
		while true do
			task.wait(1)
			local ok, err = pcall(tick)
			if not ok then
				warn(`[Gates] {err}`)
			end
			if now() >= nextSpawnAt then
				nextSpawnAt = now() + G.SpawnIntervalSeconds
				if #portals < G.MaxActivePortals then
					local spawned, spawnErr = pcall(spawnPortal)
					if not spawned then
						warn(`[Gates] {spawnErr}`)
					end
				end
			end
		end
	end)
end

return GateService
