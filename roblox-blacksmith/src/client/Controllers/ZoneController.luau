--!strict
--[[
	ЗОНЫ: баннер «Медные холмы · Рекомендуемый ур. 1+» при входе в зону, музыка зоны,
	вращение Кристалла Системы (тег Spin), режим «слабая графика» для телефонов.
]]

local CollectionService = game:GetService("CollectionService")
local Lighting = game:GetService("Lighting")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local SoundService = game:GetService("SoundService")
local TweenService = game:GetService("TweenService")

local Config = require(ReplicatedStorage.Shared.Config)
local C = require(script.Parent.Parent.UI.Components)
local ClientSettings = require(script.Parent.Parent.UI.ClientSettings)
local Theme = require(script.Parent.Parent.UI.Theme)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local player = Players.LocalPlayer

local ZoneController = {}

local banner: CanvasGroup
local bannerTitle: TextLabel
local bannerSub: TextLabel
local currentZone: string? = nil
local music: Sound

local function zoneAt(position: Vector3): any
	local best: any = nil
	for _, zone in Config.World.Zones do
		local offset = position - zone.Center
		local d = Vector3.new(offset.X, 0, offset.Z).Magnitude
		if d <= zone.Radius and (best == nil or zone.Radius < best.Radius) then
			best = zone
		end
	end
	return best
end

local function showBanner(zone: any)
	C.Bind(bannerTitle, zone.NameKey)
	if zone.Safe then
		C.Bind(bannerSub, "UI_ZONE_SAFE")
		bannerSub.TextColor3 = Theme.Success
	else
		C.Bind(bannerSub, "UI_ZONE_LEVEL", { level = zone.Level })
		bannerSub.TextColor3 = Theme.TextDim
	end
	banner.Visible = true
	banner.GroupTransparency = 1
	TweenService:Create(banner, TweenInfo.new(0.4), { GroupTransparency = 0 }):Play()
	task.delay(2.6, function()
		if currentZone == zone.Id then
			local t = TweenService:Create(banner, TweenInfo.new(0.6), { GroupTransparency = 1 })
			t:Play()
		end
	end)
end

local function playMusic(zone: any?)
	local id = if zone then Config.Audio[zone.Music] else nil
	if not ClientSettings.Music or not id or id == "" then
		music:Stop()
		return
	end
	if music.SoundId == id and music.IsPlaying then
		return
	end
	music.SoundId = id
	music.Volume = 0
	music:Play()
	TweenService:Create(music, TweenInfo.new(2), { Volume = 0.3 }):Play()
end

local function applyGraphics()
	local low = ClientSettings.LowGraphics
	for _, effect in Lighting:GetChildren() do
		if effect:IsA("PostEffect") then
			effect.Enabled = not low
		end
	end
	Lighting.GlobalShadows = not low
end

local function build()
	banner = C.Make("CanvasGroup", {
		Name = "ZoneBanner",
		Size = UDim2.fromOffset(S(420), S(72)),
		Position = UDim2.new(0.5, 0, 0.26, 0),
		AnchorPoint = Vector2.new(0.5, 0.5),
		BackgroundTransparency = 1,
		GroupTransparency = 1,
		Visible = false,
		Parent = UIController.Layers.Overlay,
	})
	C.Adaptive(banner)
	bannerTitle = C.Text({
		Font = Theme.FontTitle,
		TextSize = 30,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(40)),
		Parent = banner,
	})
	bannerTitle.TextStrokeTransparency = 0.4
	bannerSub = C.Text({
		Font = Theme.FontBold,
		TextSize = 15,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(24)),
		Position = UDim2.fromOffset(0, S(40)),
		Parent = banner,
	})
	bannerSub.TextStrokeTransparency = 0.5

	music = Instance.new("Sound")
	music.Name = "ZoneMusic"
	music.Looped = true
	music.Parent = SoundService
end

function ZoneController:Start()
	build()
	applyGraphics()
	ClientSettings.Changed:Connect(function(key: string)
		if key == "LowGraphics" then
			applyGraphics()
		elseif key == "Music" then
			if ClientSettings.Music then
				local id = currentZone
				playMusic(if id then Config.World.Zones[id] else nil)
			else
				music:Stop()
			end
		end
	end)

	task.spawn(function()
		while true do
			task.wait(0.5)
			local character = player.Character
			local root = character and character:FindFirstChild("HumanoidRootPart") :: BasePart?
			if root then
				local zone = zoneAt(root.Position)
				local id = if zone then zone.Id else nil
				if id ~= currentZone then
					currentZone = id
					if zone then
						showBanner(zone)
					end
					playMusic(zone)
				end
			end
		end
	end)

	-- вращение деталей с тегом Spin (только на клиенте, сервер не нагружается)
	RunService.RenderStepped:Connect(function(dt)
		for _, inst in CollectionService:GetTagged(Config.Tags.Spin) do
			if inst:IsA("BasePart") then
				inst.CFrame = inst.CFrame * CFrame.Angles(0, dt * 0.8, 0)
			end
		end
	end)
end

return ZoneController
