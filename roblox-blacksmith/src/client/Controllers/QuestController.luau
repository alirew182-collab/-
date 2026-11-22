--!strict
--[[
	ТРЕКЕР КВЕСТА СИСТЕМЫ + луч-подсказка от персонажа к цели.
	Во время обучения — текущий шаг обучения, после — первое невыполненное ежедневное задание.
	Цели ищутся по тегам CollectionService на клиенте (кузня и торговец всегда подгружены:
	ModelStreamingMode = Persistent).
]]

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local C = require(script.Parent.Parent.UI.Components)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local HudController = require(script.Parent.HudController)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local QuestController = {}

-- куда вести луч для событий ежедневных заданий
local EVENT_TARGET: { [string]: string } = {
	OreMined = Config.Tags.OreNode,
	WeaponForged = Config.Tags.Forge,
	QualityForge80 = Config.Tags.Forge,
	ItemSold = Config.Tags.Merchant,
	GateCleared = Config.Tags.GatePortal,
}

type Current = {
	TitleKey: string,
	Count: number,
	Progress: number,
	Event: string,
	TargetTag: string?,
	TargetOre: string?,
	Daily: boolean,
}

local box: Frame
local headerLabel: TextLabel
local titleLabel: TextLabel
local progressLabel: TextLabel
local beam: Beam
local fromAttachment: Attachment
local toAttachment: Attachment

local function positionOf(instance: Instance): Vector3?
	if instance:IsA("BasePart") then
		return instance.Position
	elseif instance:IsA("Model") then
		return instance:GetPivot().Position
	end
	return nil
end

local function current(): Current?
	local data = DataController:Get()
	if not data then
		return nil
	end
	if not data.Tutorial.Done then
		local step = Config.Tutorial.Steps[data.Tutorial.Step]
		if not step then
			return nil
		end
		return {
			TitleKey = step.TitleKey,
			Count = step.Count,
			Progress = data.Tutorial.Progress,
			Event = step.Event,
			TargetTag = step.TargetTag,
			TargetOre = step.TargetOre,
			Daily = false,
		}
	end
	for _, q in data.Daily.Quests do
		if not q.Done then
			for _, def in Config.Daily.QuestPool do
				if def.Id == q.Id then
					return {
						TitleKey = def.TitleKey,
						Count = def.Count,
						Progress = q.Progress,
						Event = def.Event,
						TargetTag = EVENT_TARGET[def.Event],
						TargetOre = nil,
						Daily = true,
					}
				end
			end
		end
	end
	return nil
end

local function findTarget(q: Current, from: Vector3): Vector3?
	if not q.TargetTag then
		return nil
	end
	local data = DataController:Get()
	local best: Vector3? = nil
	local bestDist = math.huge
	for _, instance in CollectionService:GetTagged(q.TargetTag) do
		if instance:IsDescendantOf(workspace) then
			local ok = true
			local oreId = instance:GetAttribute("OreId")
			if q.TargetOre and oreId ~= q.TargetOre then
				ok = false
			end
			-- для «добудь руду» — только руда, доступная по уровню
			if ok and q.TargetTag == Config.Tags.OreNode and type(oreId) == "string" and data then
				local ore = Config.Ores[oreId]
				ok = ore ~= nil and data.Level >= ore.RequiredLevel
			end
			local pos = positionOf(instance)
			if ok and pos then
				local d = (pos - from).Magnitude
				if d < bestDist then
					best, bestDist = pos, d
				end
			end
		end
	end
	return best
end

local function refreshText()
	local q = current()
	if not q then
		box.Visible = false
		HudController:SetInventoryPulse(false)
		return
	end
	box.Visible = true
	C.Bind(headerLabel, if q.Daily then "QUEST_DAILY_HEADER" else "QUEST_HEADER")
	C.Bind(titleLabel, q.TitleKey, { count = q.Count })
	C.Bind(progressLabel, "QUEST_PROGRESS", { progress = q.Progress, count = q.Count })
	HudController:SetInventoryPulse(q.Event == "WeaponEquipped")
end

local function updateBeam()
	local q = current()
	local character = Players.LocalPlayer.Character
	local root = character and character:FindFirstChild("HumanoidRootPart") :: BasePart?
	if not q or not root or UIController:GetOpenPanel() ~= nil then
		beam.Enabled = false
		return
	end
	local target = findTarget(q, root.Position)
	if q.TargetTag == Config.Tags.GatePortal then
		if target then
			refreshText()
		else
			C.Bind(progressLabel, "QUEST_WAIT_GATE")
		end
	end
	if not target then
		beam.Enabled = false
		return
	end
	if fromAttachment.Parent ~= root then
		fromAttachment.Parent = root
	end
	toAttachment.WorldPosition = target + Vector3.new(0, 2, 0)
	beam.Enabled = (target - root.Position).Magnitude > 6
end

local function build()
	box = C.Panel({
		Name = "Quest",
		Size = UDim2.fromOffset(S(250), S(58)),
		Position = UDim2.fromOffset(S(10), S(100)),
		AnchorPoint = Vector2.zero,
		Transparency = 0.2,
		StrokeColor = Theme.Gold,
		Visible = false,
		Adaptive = true,
		Parent = UIController.Layers.Hud,
	})
	headerLabel = C.Text({
		Key = "QUEST_HEADER",
		Font = Theme.FontBold,
		TextSize = 11,
		Color = Theme.Gold,
		Size = UDim2.new(1, -S(16), 0, S(14)),
		Position = UDim2.fromOffset(S(8), S(5)),
		Parent = box,
	})
	titleLabel = C.Text({
		Font = Theme.FontBold,
		TextSize = 15,
		Size = UDim2.new(1, -S(16), 0, S(18)),
		Position = UDim2.fromOffset(S(8), S(20)),
		Parent = box,
	})
	progressLabel = C.Text({
		TextSize = 13,
		Color = Theme.TextDim,
		Size = UDim2.new(1, -S(16), 0, S(15)),
		Position = UDim2.fromOffset(S(8), S(39)),
		Parent = box,
	})

	fromAttachment = Instance.new("Attachment")
	fromAttachment.Name = "QuestBeamFrom"
	toAttachment = Instance.new("Attachment")
	toAttachment.Name = "QuestBeamTo"
	toAttachment.Parent = workspace.Terrain

	beam = Instance.new("Beam")
	beam.Attachment0 = fromAttachment
	beam.Attachment1 = toAttachment
	beam.Color = ColorSequence.new(Theme.Gold)
	beam.Transparency = NumberSequence.new(0.25, 0.6)
	beam.Width0 = 0.35
	beam.Width1 = 0.35
	beam.FaceCamera = true
	beam.LightEmission = 1
	beam.Segments = 1
	beam.Enabled = false
	beam.Parent = workspace.Terrain
end

function QuestController:Start()
	build()
	DataController.Changed:Connect(function(key: string)
		if key == "*" or key == "Tutorial" or key == "Daily" then
			refreshText()
		end
	end)
	refreshText()
	task.spawn(function()
		while true do
			pcall(updateBeam)
			task.wait(0.4)
		end
	end)
end

return QuestController
