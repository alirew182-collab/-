--!strict
-- ВРАТА на клиенте: карточка группы (ожидание старта) и забега (волна, время, босс).

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Format = require(ReplicatedStorage.Shared.Format)
local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local C = require(script.Parent.Parent.UI.Components)
local Theme = require(script.Parent.Parent.UI.Theme)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local GateController = {}

local card: Frame
local line1: TextLabel
local line2: TextLabel
local startButton: TextButton
local bossFrame: Frame
local bossName: TextLabel
local bossFill: Frame

local state: { [string]: any } = { Phase = "none" }
local deadline: number? = nil -- os.clock(), когда таймер дойдёт до 0

local function render()
	local phase = state.Phase
	card.Visible = phase == "lobby" or phase == "run"
	bossFrame.Visible = phase == "run" and state.Boss ~= nil
	local stroke = card:FindFirstChildOfClass("UIStroke")
	if stroke then
		-- обычная рамка — тонкая линия; у Красных врат — красная
		stroke.Color = if state.IsRed then Theme.Danger else Theme.Text
		stroke.Transparency = if state.IsRed then 0 else Theme.HairlineTransparency
	end
	line1.TextColor3 = if state.IsRed then Theme.Danger else Theme.Text
	if phase == "lobby" then
		local text = Localization.Get("UI_GATE_LOBBY", { gate = "@" .. state.GateNameKey, count = state.Count, max = state.Max })
		C.SetRaw(line1, if state.IsRed then `{Localization.Get("UI_RED_PREFIX")} · {text}` else text)
		startButton.Visible = state.IsLeader == true
	elseif phase == "run" then
		local text = if state.BossPhase
			then Localization.Get("UI_GATE_BOSS")
			else Localization.Get("UI_GATE_WAVE", { wave = state.Wave, total = state.TotalWaves })
		C.SetRaw(line1, if state.IsRed then `{Localization.Get("UI_RED_PREFIX")} · {text}` else text)
		startButton.Visible = false
		local boss = state.Boss
		if boss then
			C.Bind(bossName, boss.NameKey)
			bossFill.Size = UDim2.fromScale(math.clamp(boss.Health / boss.MaxHealth, 0, 1), 1)
		end
	end
end

local function updateTimer()
	if not card.Visible or not deadline then
		return
	end
	local left = math.max(0, deadline - os.clock())
	if state.Phase == "lobby" then
		C.Bind(line2, "UI_GATE_STARTS_IN", { time = Format.Time(left) })
	else
		local alive = Localization.Get("UI_GATE_ALIVE", { alive = state.Alive or 0, total = state.Total or 0 })
		C.SetRaw(line2, `{Format.Time(left)}   ·   {alive}`)
	end
end

local function build()
	local hud = UIController.Layers.Hud
	card = C.Panel({
		Name = "GateCard",
		Size = UDim2.fromOffset(S(300), S(68)),
		Position = UDim2.new(0.5, 0, 0, S(8)),
		AnchorPoint = Vector2.new(0.5, 0),
		Transparency = 0.15,
		Visible = false,
		Adaptive = true,
		Parent = hud,
	})
	line1 = C.Text({
		Font = Theme.FontBold,
		TextSize = 15,
		Size = UDim2.new(1, -S(100), 0, S(24)),
		Position = UDim2.fromOffset(S(12), S(8)),
		Parent = card,
	})
	line2 = C.Text({
		TextSize = 13,
		Color = Theme.TextDim,
		Size = UDim2.new(1, -S(100), 0, S(20)),
		Position = UDim2.fromOffset(S(12), S(36)),
		Parent = card,
	})
	C.Button({
		Key = "UI_GATE_LEAVE",
		TextSize = 13,
		Size = UDim2.fromOffset(S(80), S(28)),
		Position = UDim2.new(1, -S(8), 0, S(8)),
		AnchorPoint = Vector2.new(1, 0),
		Color = Theme.PanelLight,
		OnClick = function()
			Net.Event("GateLeave"):FireServer()
		end,
		Parent = card,
	})
	startButton = C.Button({
		Key = "UI_GATE_START",
		TextSize = 13,
		Size = UDim2.fromOffset(S(80), S(28)),
		Position = UDim2.new(1, -S(8), 1, -S(8)),
		AnchorPoint = Vector2.new(1, 1),
		Color = Theme.Accent,
		TextColor = Theme.Bg,
		Visible = false,
		OnClick = function()
			Net.Event("GateStart"):FireServer()
		end,
		Parent = card,
	})

	bossFrame = C.Make("Frame", {
		Name = "BossBar",
		Size = UDim2.fromOffset(S(360), S(36)),
		Position = UDim2.new(0.5, 0, 0, S(80)),
		AnchorPoint = Vector2.new(0.5, 0),
		BackgroundTransparency = 1,
		Visible = false,
		Parent = hud,
	})
	C.Adaptive(bossFrame)
	bossName = C.Text({
		Font = Theme.FontTitle,
		TextSize = 14,
		Color = Theme.Danger,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(16)),
		Parent = bossFrame,
	})
	local _, fill = C.Bar({
		Size = UDim2.new(1, 0, 0, S(12)),
		Position = UDim2.fromOffset(0, S(20)),
		Color = Theme.Danger,
		Value = 1,
		Parent = bossFrame,
	})
	bossFill = fill
end

function GateController:Start()
	build()
	Localization.Changed:Connect(render)
	Net.Event("GateState").OnClientEvent:Connect(function(payload: { [string]: any })
		state = payload
		if payload.Phase == "lobby" then
			deadline = if payload.StartsIn then os.clock() + payload.StartsIn else nil
		elseif payload.Phase == "run" then
			deadline = os.clock() + (payload.TimeLeft or 0)
		else
			deadline = nil
		end
		render()
		updateTimer()
	end)

	local acc = 0
	RunService.Heartbeat:Connect(function(dt)
		acc += dt
		if acc >= 0.25 then
			acc = 0
			updateTimer()
		end
	end)
end

return GateController
