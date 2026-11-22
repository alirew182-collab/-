--!strict
--[[
	ОКНО СИСТЕМЫ — главная фишка. Синее окно в стиле маньхуа:
	мерцание рамки → окно выезжает → текст «печатается» → само закрывается или по тапу.
	Окна идут очередью; если их много — показываются быстрее.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local Types = require(ReplicatedStorage.Shared.Types)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local SystemWindowController = {}

local queue: { Types.NotifyPayload } = {}
local showing = false

local KIND_STYLE: { [string]: { Color: Color3, Sound: string, Duration: number } } = {
	Awakening = { Color = Theme.Accent, Sound = "SystemOpen", Duration = 6 },
	Quest = { Color = Theme.Accent, Sound = "SystemOpen", Duration = 3.5 },
	QuestDone = { Color = Theme.Success, Sound = "SystemQuestDone", Duration = 3 },
	LevelUp = { Color = Theme.Gold, Sound = "SystemLevelUp", Duration = 3.5 },
	Reward = { Color = Theme.Gold, Sound = "SystemQuestDone", Duration = 4 },
	Info = { Color = Theme.Accent, Sound = "SystemOpen", Duration = 3.5 },
	Warning = { Color = Theme.Danger, Sound = "SystemWarning", Duration = 3.5 },
	Boss = { Color = Theme.Danger, Sound = "SystemBoss", Duration = 4 },
	GateCleared = { Color = Theme.Gold, Sound = "SystemQuestDone", Duration = 4.5 },
	GateFailed = { Color = Theme.Danger, Sound = "SystemWarning", Duration = 4 },
	Forge = { Color = Theme.Accent, Sound = "ForgeDone", Duration = 3.5 },
}

-- элементы окна
local root: CanvasGroup
local frame: Frame
local stroke: UIStroke
local header: TextLabel
local title: TextLabel
local body: TextLabel
local closeRequested = false

local function build()
	root = C.Make("CanvasGroup", {
		Name = "SystemWindow",
		Size = UDim2.fromOffset(S(430), S(150)),
		Position = UDim2.new(0.5, 0, 0, S(86)),
		AnchorPoint = Vector2.new(0.5, 0),
		BackgroundTransparency = 1,
		GroupTransparency = 1,
		Visible = false,
		Parent = UIController.Layers.Overlay,
	})

	frame = C.Make("Frame", {
		Size = UDim2.new(1, -S(8), 1, -S(8)),
		Position = UDim2.fromScale(0.5, 0.5),
		AnchorPoint = Vector2.new(0.5, 0.5),
		BackgroundColor3 = Theme.Bg,
		BackgroundTransparency = 0.08,
		Parent = root,
	})
	C.Corner(frame, 6)
	stroke = C.Stroke(frame, Theme.Accent, 2, 0)
	C.Make("UIGradient", {
		Rotation = 90,
		Color = ColorSequence.new(Color3.fromRGB(40, 80, 140), Color3.fromRGB(10, 20, 45)),
		Parent = frame,
	})
	C.Padding(frame, 12)
	C.List(frame, Enum.FillDirection.Vertical, 4)

	header = C.Text({
		Name = "Header",
		Font = Theme.FontBold,
		TextSize = 14,
		Color = Theme.Accent,
		Size = UDim2.new(1, 0, 0, S(18)),
		LayoutOrder = 1,
		Parent = frame,
	})
	C.Make("Frame", {
		Name = "Divider",
		Size = UDim2.new(1, 0, 0, 1),
		BackgroundColor3 = Theme.Accent,
		BackgroundTransparency = 0.5,
		BorderSizePixel = 0,
		LayoutOrder = 2,
		Parent = frame,
	})
	title = C.Text({
		Name = "Title",
		Font = Theme.FontTitle,
		TextSize = 21,
		Wrapped = true,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		LayoutOrder = 3,
		Parent = frame,
	})
	body = C.Text({
		Name = "Body",
		TextSize = 15,
		Color = Theme.TextDim,
		Wrapped = true,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, 0),
		AutomaticSize = Enum.AutomaticSize.Y,
		LayoutOrder = 4,
		Parent = frame,
	})
	C.Text({
		Name = "Hint",
		Key = "SYS_TAP_TO_CLOSE",
		TextSize = 11,
		Color = Theme.TextDim,
		AlignX = Enum.TextXAlignment.Right,
		Size = UDim2.new(1, 0, 0, S(14)),
		LayoutOrder = 5,
		Parent = frame,
	})

	local tap = C.Make("TextButton", {
		Name = "Tap",
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		Text = "",
		ZIndex = 5,
		Parent = root,
	})
	tap.Activated:Connect(function()
		closeRequested = true
	end)
end

local function typewriter(label: TextLabel, seconds: number)
	local length = utf8.len(label.ContentText) or #label.ContentText
	label.MaxVisibleGraphemes = 0
	local started = os.clock()
	while label.MaxVisibleGraphemes ~= -1 do
		local p = math.clamp((os.clock() - started) / seconds, 0, 1)
		if p >= 1 or closeRequested then
			label.MaxVisibleGraphemes = -1
			break
		end
		label.MaxVisibleGraphemes = math.floor(length * p)
		task.wait()
	end
end

local function fitHeight()
	-- подогнать высоту CanvasGroup под содержимое
	local layout = frame:FindFirstChildOfClass("UIListLayout")
	if layout then
		local h = layout.AbsoluteContentSize.Y + S(24) + S(8)
		root.Size = UDim2.fromOffset(S(430), math.max(S(110), h))
	end
end

local function show(payload: Types.NotifyPayload)
	local style = KIND_STYLE[payload.Kind] or KIND_STYLE.Info
	closeRequested = false

	C.SetRaw(header, `[ {Localization.Get("SYS_HEADER")} ]  {Localization.Get("SYS_KIND_" .. string.upper(payload.Kind))}`)
	header.TextColor3 = style.Color
	stroke.Color = style.Color
	C.SetRaw(title, Localization.Get(payload.Title, payload.Args))
	if payload.Body then
		C.SetRaw(body, Localization.Get(payload.Body, payload.Args))
		body.Visible = true
	else
		body.Visible = false
	end
	title.MaxVisibleGraphemes = 0
	body.MaxVisibleGraphemes = 0

	root.Visible = true
	task.wait() -- дать AutomaticSize посчитаться
	fitHeight()

	Sound.Play(style.Sound)

	-- появление: мерцание рамки + выезд сверху
	local targetPos = UDim2.new(0.5, 0, 0, S(86))
	root.Position = targetPos - UDim2.fromOffset(0, S(18))
	root.GroupTransparency = 1
	TweenService:Create(root, TweenInfo.new(0.25, Enum.EasingStyle.Back, Enum.EasingDirection.Out), {
		Position = targetPos,
		GroupTransparency = 0,
	}):Play()
	for i = 1, 4 do
		stroke.Transparency = if i % 2 == 1 then 0.8 else 0
		task.wait(0.05)
	end
	stroke.Transparency = 0

	typewriter(title, 0.35)
	if body.Visible then
		typewriter(body, 0.5)
	end

	local duration = style.Duration
	if #queue >= 3 then
		duration = 1.5
	end
	local started = os.clock()
	while os.clock() - started < duration and not closeRequested do
		task.wait(0.05)
	end

	local tween = TweenService:Create(root, TweenInfo.new(0.2), {
		GroupTransparency = 1,
		Position = targetPos - UDim2.fromOffset(0, S(10)),
	})
	tween:Play()
	tween.Completed:Wait()
	root.Visible = false
end

local function pump()
	if showing then
		return
	end
	showing = true
	while #queue > 0 do
		local payload = table.remove(queue, 1) :: Types.NotifyPayload
		local ok, err = pcall(show, payload)
		if not ok then
			warn("[SystemWindow]", err)
			root.Visible = false
		end
	end
	showing = false
end

function SystemWindowController:Notify(payload: Types.NotifyPayload)
	table.insert(queue, payload)
	task.spawn(pump)
end

function SystemWindowController:Start()
	build()
	Net.Event("SystemNotify").OnClientEvent:Connect(function(payload: Types.NotifyPayload)
		if type(payload) == "table" and type(payload.Title) == "string" then
			SystemWindowController:Notify(payload)
		end
	end)
end

return SystemWindowController
