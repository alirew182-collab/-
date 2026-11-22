--!strict
--[[
	ЭФФЕКТЫ: всплывающие цифры урона, «ПАРИРОВАНИЕ!», искры добычи, вспышка экрана при попадании.
	Всё через пулы объектов (никаких Instance.new на каждый удар) — важно для слабых телефонов.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local Config = require(ReplicatedStorage.Shared.Config)
local ClientSettings = require(script.Parent.Parent.UI.ClientSettings)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local UIController = require(script.Parent.UIController)

local FxController = {}

local POOL_SIZE = 14
local PARTICLE_POOL = 6

type Popup = { Attachment: Attachment, Gui: BillboardGui, Label: TextLabel }
local popups: { Popup } = {}
local popupIndex = 0

type Burst = { Attachment: Attachment, Emitter: ParticleEmitter }
local bursts: { Burst } = {}
local burstIndex = 0

local function build()
	local terrain = workspace.Terrain
	for i = 1, POOL_SIZE do
		local attachment = Instance.new("Attachment")
		attachment.Name = "FxPopup" .. i
		attachment.Parent = terrain
		local gui = Instance.new("BillboardGui")
		gui.Size = UDim2.fromOffset(160, 50)
		gui.AlwaysOnTop = true
		gui.LightInfluence = 0
		gui.MaxDistance = 120
		gui.Adornee = attachment
		gui.Enabled = false
		gui.Parent = attachment
		local label = Instance.new("TextLabel")
		label.Size = UDim2.fromScale(1, 1)
		label.BackgroundTransparency = 1
		label.Font = Theme.FontTitle
		label.TextScaled = true
		label.TextStrokeTransparency = 0.2
		label.Parent = gui
		table.insert(popups, { Attachment = attachment, Gui = gui, Label = label })
	end
	for i = 1, PARTICLE_POOL do
		local attachment = Instance.new("Attachment")
		attachment.Name = "FxBurst" .. i
		attachment.Parent = terrain
		local emitter = Instance.new("ParticleEmitter")
		emitter.Enabled = false
		emitter.Lifetime = NumberRange.new(0.3, 0.6)
		emitter.Speed = NumberRange.new(8, 16)
		emitter.SpreadAngle = Vector2.new(180, 180)
		emitter.Size = NumberSequence.new(0.35, 0)
		emitter.LightEmission = 1
		emitter.Acceleration = Vector3.new(0, -30, 0)
		emitter.Parent = attachment
		table.insert(bursts, { Attachment = attachment, Emitter = emitter })
	end
end

local function popup(position: Vector3, text: string, color: Color3, big: boolean?)
	if not ClientSettings.DamageNumbers and not big then
		return
	end
	popupIndex = popupIndex % POOL_SIZE + 1
	local p = popups[popupIndex]
	local offset = Vector3.new(math.random() * 2 - 1, 0, math.random() * 2 - 1)
	p.Attachment.WorldPosition = position + offset
	p.Label.Text = text
	p.Label.TextColor3 = color
	p.Label.TextTransparency = 0
	p.Label.TextStrokeTransparency = 0.2
	p.Gui.Size = if big then UDim2.fromOffset(220, 64) else UDim2.fromOffset(120, 40)
	p.Gui.StudsOffsetWorldSpace = Vector3.zero
	p.Gui.Enabled = true
	local info = TweenInfo.new(0.8, Enum.EasingStyle.Quad, Enum.EasingDirection.Out)
	TweenService:Create(p.Gui, info, { StudsOffsetWorldSpace = Vector3.new(0, 3, 0) }):Play()
	local fade = TweenService:Create(p.Label, TweenInfo.new(0.35, Enum.EasingStyle.Linear, Enum.EasingDirection.In, 0, false, 0.45), {
		TextTransparency = 1,
		TextStrokeTransparency = 1,
	})
	fade:Play()
	local gui = p.Gui
	local label = p.Label
	task.delay(0.85, function()
		if label.Text == text and label.TextTransparency >= 0.99 then
			gui.Enabled = false
		end
	end)
end

local function burst(position: Vector3, color: Color3, count: number)
	if ClientSettings.LowGraphics then
		count = math.ceil(count / 4)
	end
	burstIndex = burstIndex % PARTICLE_POOL + 1
	local b = bursts[burstIndex]
	b.Attachment.WorldPosition = position
	b.Emitter.Color = ColorSequence.new(color)
	b.Emitter:Emit(count)
end

local function onFx(payload: { [string]: any })
	local kind = payload.Kind
	local position = payload.Position
	if typeof(position) ~= "Vector3" then
		return
	end
	local localId = Players.LocalPlayer.UserId

	if kind == "Damage" then
		local mine = payload.ByUserId == localId
		if payload.Crit then
			popup(position, `{Localization.Get("FX_CRIT")} {payload.Amount}`, Color3.fromRGB(255, 120, 40), true)
		else
			popup(position, tostring(payload.Amount), if mine then Theme.Gold else Theme.Text, false)
		end
		burst(position, Color3.fromRGB(255, 220, 160), 6)
		if mine then
			Sound.Play("HitMob", 0.5, 0.9 + math.random() * 0.2)
		end
	elseif kind == "PlayerHit" then
		local isMe = payload.UserId == localId
		if payload.Result == "parry" then
			popup(position + Vector3.new(0, 2, 0), Localization.Get("FX_PARRY"), Theme.Accent, true)
			burst(position, Theme.Accent, 20)
			if isMe then
				Sound.Play("Parry")
			end
		elseif payload.Result == "block" then
			popup(position + Vector3.new(0, 2, 0), `{Localization.Get("FX_BLOCK")} -{payload.Amount}`, Theme.TextDim, false)
			if isMe then
				UIController:FlashHit(0.2)
			end
		else
			popup(position + Vector3.new(0, 2, 0), `-{payload.Amount}`, Theme.Danger, false)
			if isMe then
				UIController:FlashHit(0.5)
			end
		end
	elseif kind == "MineHit" then
		local color = if typeof(payload.Color) == "Color3" then payload.Color else Color3.new(1, 1, 1)
		burst(position, color, 8)
		Sound.Play("MineHit", 0.5, 0.9 + (payload.Progress or 0) * 0.3)
	elseif kind == "OreBreak" then
		local color = if typeof(payload.Color) == "Color3" then payload.Color else Color3.new(1, 1, 1)
		burst(position, color, 25)
		Sound.Play("OreBreak")
	elseif kind == "MobDeath" then
		burst(position, Color3.fromRGB(255, 120, 60), if payload.Boss then 60 else 20)
	elseif kind == "Slam" then
		burst(position, Theme.Danger, 40)
	elseif kind == "Burn" then
		popup(position, tostring(payload.Amount), Color3.fromRGB(255, 140, 50), false)
	elseif kind == "Celebrate" then
		-- редкая ковка: столб света и взрыв частиц цвета редкости (виден всем рядом)
		local rarity = Config.Rarities[payload.Rarity] or Config.Rarities.Legendary
		burst(position, rarity.Color, 80)
		burst(position + Vector3.new(0, 4, 0), Color3.new(1, 1, 1), 40)
		popup(position + Vector3.new(0, 5, 0), Localization.Get(rarity.NameKey), rarity.Color, true)
		local pillar = Instance.new("Part")
		pillar.Anchored = true
		pillar.CanCollide = false
		pillar.CanQuery = false
		pillar.CastShadow = false
		pillar.Material = Enum.Material.Neon
		pillar.Color = rarity.Color
		pillar.Size = Vector3.new(3, 120, 3)
		pillar.CFrame = CFrame.new(position + Vector3.new(0, 60, 0))
		pillar.Transparency = 0.2
		pillar.Parent = workspace.CurrentCamera -- только на этом клиенте
		TweenService:Create(pillar, TweenInfo.new(2.5), { Transparency = 1, Size = Vector3.new(0.5, 120, 0.5) }):Play()
		task.delay(2.6, function()
			pillar:Destroy()
		end)
	end
end

function FxController:Start()
	build()
	Net.UnreliableEvent("Fx").OnClientEvent:Connect(function(payload)
		if type(payload) == "table" then
			onFx(payload)
		end
	end)
end

return FxController
