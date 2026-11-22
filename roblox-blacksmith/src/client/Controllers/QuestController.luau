--!strict
--[[
	ТРЕКЕР КВЕСТА СИСТЕМЫ + луч-подсказка от персонажа к цели (жила, кузня, торговец, портал).
	Цели ищутся по тегам CollectionService на клиенте.
	ВАЖНО для StreamingEnabled: у кузни/торговца поставь ModelStreamingMode = Persistent (см. README),
	иначе луч появится только когда объект подгрузится рядом.
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

local box: Frame
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

local function findTarget(step: any, from: Vector3): Vector3?
	if not step.TargetTag then
		return nil
	end
	local best: Vector3? = nil
	local bestDist = math.huge
	for _, instance in CollectionService:GetTagged(step.TargetTag) do
		if instance:IsDescendantOf(workspace) then
			local ok = true
			if step.TargetOre and instance:GetAttribute("OreId") ~= step.TargetOre then
				ok = false
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

local function currentStep(): (any, any)
	local data = DataController:Get()
	if not data or data.Tutorial.Done then
		return nil, data
	end
	return Config.Tutorial.Steps[data.Tutorial.Step], data
end

local function refreshText()
	local step, data = currentStep()
	if not step then
		box.Visible = false
		HudController:SetInventoryPulse(false)
		return
	end
	box.Visible = true
	C.Bind(titleLabel, step.TitleKey)
	C.Bind(progressLabel, "QUEST_PROGRESS", { progress = data.Tutorial.Progress, count = step.Count })
	HudController:SetInventoryPulse(step.Event == "WeaponEquipped")
end

local function updateBeam()
	local step = currentStep()
	local character = Players.LocalPlayer.Character
	local root = character and character:FindFirstChild("HumanoidRootPart") :: BasePart?
	if not step or not root or UIController:GetOpenPanel() ~= nil then
		beam.Enabled = false
		return
	end
	local target = findTarget(step, root.Position)
	if step.TargetTag == Config.Tags.GatePortal then
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
		Parent = UIController.Layers.Hud,
	})
	C.Text({
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
		if key == "*" or key == "Tutorial" then
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
