--!strict
--[[
	ДОБЫЧА. Жила = BasePart (или Model) с тегом "OreNode" и атрибутом OreId ("Copper" | "Iron" | ...).
	Атрибут AutoStyle = false → не перекрашивать твою модель в цвет руды.

	Каждое нажатие подсказки «Добыть» = один удар киркой (сила кирки).
	Разбил жилу — получил руду; жила «спит» RespawnSeconds и появляется снова.
	Все проверки (дистанция, кулдаун, уровень, кирка) — на сервере.
]]

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Proximity = require(script.Parent.Parent.Util.Proximity)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local LevelService = require(script.Parent.LevelService)
local NetService = require(script.Parent.NetService)
local SystemService = require(script.Parent.SystemService)
local TutorialService = require(script.Parent.TutorialService)

local MiningService = {}

type NodeState = {
	Instance: Instance,
	Anchor: BasePart,
	OreId: string,
	Health: number,
	Depleted: boolean,
	Prompt: ProximityPrompt,
	Parts: { BasePart },
	Transparency: { [BasePart]: number },
}

local nodes: { [Instance]: NodeState } = {}
local lastHit: { [Player]: number } = {}

local function setDepletedVisual(state: NodeState, depleted: boolean)
	for _, part in state.Parts do
		part.Transparency = if depleted then 0.85 else state.Transparency[part]
		part.CanCollide = not depleted
	end
	state.Prompt.Enabled = not depleted
end

local function onHit(player: Player, state: NodeState)
	if state.Depleted then
		return
	end
	local data = DataService:Get(player)
	if not data then
		return
	end

	local now = os.clock()
	if (lastHit[player] or 0) + Config.Economy.Mining.HitCooldownSeconds > now then
		return
	end
	lastHit[player] = now

	if Proximity.DistanceTo(player, state.Anchor.Position) > Config.Economy.Mining.MaxDistance then
		SystemService:Error(player, "ERR_TOO_FAR")
		return
	end

	local ore = Config.Ores[state.OreId]
	local pickaxe = Config.Pickaxes[data.Pickaxe] or Config.Pickaxes[Config.Economy.StartingPickaxe]
	if data.Level < ore.RequiredLevel then
		SystemService:Error(player, "ERR_LEVEL_LOW", { level = ore.RequiredLevel })
		return
	end
	if pickaxe.Tier < ore.RequiredPickaxeTier then
		SystemService:Error(player, "ERR_PICKAXE_WEAK")
		return
	end

	state.Health -= pickaxe.Power
	NetService:FxNear(state.Anchor.Position, 80, {
		Kind = "MineHit",
		Position = state.Anchor.Position,
		Color = ore.Color,
		Progress = math.clamp(1 - state.Health / ore.NodeHealth, 0, 1),
	})

	if state.Health > 0 then
		return
	end

	-- жила разбита: награда последнему ударившему
	state.Depleted = true
	setDepletedVisual(state, true)

	local amount = math.random(ore.YieldMin, ore.YieldMax)
	EconomyService:AddOre(player, ore.Id, amount)
	LevelService:AddXp(player, ore.XpPerOre * amount)
	DataService:AddStat(player, "OresMined", amount)
	SystemService:Toast(player, "TOAST_ORE", { count = amount, ore = "@" .. ore.NameKey }, "ore")
	NetService:FxNear(state.Anchor.Position, 80, { Kind = "OreBreak", Position = state.Anchor.Position, Color = ore.Color })
	TutorialService:Record(player, "OreMined", { OreId = ore.Id, Count = amount })

	task.delay(ore.RespawnSeconds, function()
		if nodes[state.Instance] == state then
			state.Health = ore.NodeHealth
			state.Depleted = false
			setDepletedVisual(state, false)
		end
	end)
end

local function setupNode(instance: Instance)
	if nodes[instance] then
		return
	end
	local oreId = instance:GetAttribute("OreId")
	if type(oreId) ~= "string" or not Config.Ores[oreId] then
		warn(`[Mining] {instance:GetFullName()}: нет атрибута OreId или руда не найдена в Config.Ores`)
		return
	end
	local anchor = Proximity.AnchorPart(instance)
	if not anchor then
		return
	end
	local ore = Config.Ores[oreId]

	local parts: { BasePart } = {}
	if instance:IsA("BasePart") then
		table.insert(parts, instance)
	end
	for _, d in instance:GetDescendants() do
		if d:IsA("BasePart") then
			table.insert(parts, d)
		end
	end
	local transparency = {}
	for _, part in parts do
		transparency[part] = part.Transparency
	end

	if instance:GetAttribute("AutoStyle") ~= false then
		for _, part in parts do
			part.Color = ore.Color
			part.Material = ore.Material
		end
	end

	local prompt = Prompts.Create(anchor, "PROMPT_MINE", { ObjectKey = ore.NameKey, Distance = 10 })

	local state: NodeState = {
		Instance = instance,
		Anchor = anchor,
		OreId = oreId,
		Health = ore.NodeHealth,
		Depleted = false,
		Prompt = prompt,
		Parts = parts,
		Transparency = transparency,
	}
	nodes[instance] = state

	prompt.Triggered:Connect(function(player: Player)
		onHit(player, state)
	end)
end

function MiningService:Start()
	for _, instance in CollectionService:GetTagged(Config.Tags.OreNode) do
		setupNode(instance)
	end
	CollectionService:GetInstanceAddedSignal(Config.Tags.OreNode):Connect(setupNode)
	CollectionService:GetInstanceRemovedSignal(Config.Tags.OreNode):Connect(function(instance)
		local state = nodes[instance]
		if state then
			nodes[instance] = nil
			state.Prompt:Destroy()
		end
	end)
	Players.PlayerRemoving:Connect(function(player)
		lastHit[player] = nil
	end)

	if #CollectionService:GetTagged(Config.Tags.OreNode) == 0 then
		warn("[Mining] В карте нет Part с тегом OreNode — см. README, раздел «Карта»")
	end
end

return MiningService
