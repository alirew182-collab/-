--!strict
--[[
	КУЗНЯ: выбор оружия и руды → мини-игра из 3 шагов → результат.
	Управление одной большой кнопкой (телефон) или пробелом (ПК):
	  1. Меха   — тапай, держи жар в зелёной зоне.
	  2. Заливка — держи, отпусти на линии.
	  3. Молот  — бей в такт, когда кольцо сходится с кругом.
	Клиент только меряет и отправляет сырые замеры; качество считает сервер.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")
local ContextActionService = game:GetService("ContextActionService")

local Config = require(ReplicatedStorage.Shared.Config)
local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local F = Config.Forging

local ForgeController = {}

-- состояние
local selectedType = "Sword"
local selectedOre = "Copper"
local phase = "idle" -- idle | bellows | pour | hammer | submit
local running = false
local cancelled = false

-- мини-игра
local heat = 0
local pourLevel = 0
local holding = false
local pourDone = false
local hammerParams: any = nil
local beatTimes: { number } = {}
local offsets: { number } = {}

-- UI
local panel: Frame
local selectView: Frame
local gameView: Frame
local resultView: Frame
local weaponButtons: { [string]: TextButton } = {}
local oreButtons: { [string]: TextButton } = {}
local costLabel: TextLabel
local previewLabel: TextLabel
local forgeButton: TextButton
local stepTitle: TextLabel
local stepHint: TextLabel
local actionButton: TextButton
local timerFill: Frame
local bellowsWidget: Frame
local zoneFrame: Frame
local heatFill: Frame
local pourWidget: Frame
local pourFill: Frame
local pourLine: Frame
local hammerWidget: Frame
local hammerRing: Frame
local hammerFeedback: TextLabel
local hammerCounter: TextLabel
local resultQuality: TextLabel
local resultName: TextLabel
local resultDamage: TextLabel
local lastWeaponId: string? = nil

local function sortedKeys(tbl: { [string]: any }): { string }
	local keys = {}
	for k in tbl do
		table.insert(keys, k)
	end
	table.sort(keys, function(a, b)
		return (tbl[a].Order or 0) < (tbl[b].Order or 0)
	end)
	return keys
end

local baseSizes: { [GuiObject]: UDim2 } = {}

local function punch(button: GuiObject)
	local size = baseSizes[button]
	if not size then
		size = button.Size
		baseSizes[button] = size
	end
	button.Size = UDim2.new(size.X.Scale, size.X.Offset * 0.92, size.Y.Scale, size.Y.Offset * 0.92)
	TweenService:Create(button, TweenInfo.new(0.12, Enum.EasingStyle.Back), { Size = size }):Play()
end

-- ===== Экран выбора =====

local function refreshSelect()
	local data = DataController:Get()
	if not data or not selectView then
		return
	end
	for id, button in weaponButtons do
		local selected = id == selectedType
		button.BackgroundColor3 = if selected then Theme.Accent else Theme.PanelLight
		button.TextColor3 = if selected then Theme.Bg else Theme.Text
	end
	for id, button in oreButtons do
		local ore = Config.Ores[id]
		local locked = data.Level < ore.RequiredLevel
		local selected = id == selectedOre
		if locked then
			C.Bind(button, "UI_REQ_LEVEL", { level = ore.RequiredLevel })
		else
			C.Bind(button, "UI_ORE_COUNT", { ore = "@" .. ore.NameKey, count = data.Ores[id] or 0 })
		end
		button.BackgroundColor3 = if locked then Theme.Disabled elseif selected then Theme.Accent else Theme.PanelLight
		button.TextColor3 = if selected and not locked then Theme.Bg else Theme.Text
	end

	local def = Config.Weapons[selectedType]
	local ore = Config.Ores[selectedOre]
	local have = data.Ores[selectedOre] or 0
	C.Bind(costLabel, "UI_ORE_COST", { count = def.OreCost, ore = "@" .. ore.NameKey, have = have })
	costLabel.TextColor3 = if have >= def.OreCost then Theme.TextDim else Theme.Danger
	C.Bind(previewLabel, "UI_FORGE_PREVIEW", {
		min = WeaponMath.Preview(selectedType, selectedOre, 0),
		max = WeaponMath.Preview(selectedType, selectedOre, 100),
	})
	local canForge = have >= def.OreCost and data.Level >= ore.RequiredLevel
	forgeButton.BackgroundColor3 = if canForge then Theme.Success else Theme.Disabled
end

local function showView(name: string)
	selectView.Visible = name == "select"
	gameView.Visible = name == "game"
	resultView.Visible = name == "result"
	if name == "select" then
		refreshSelect()
	end
end

-- ===== Мини-игра =====

local function setStep(titleKey: string, hintKey: string, actionKey: string, widget: Frame?)
	C.Bind(stepTitle, titleKey)
	C.Bind(stepHint, hintKey)
	C.Bind(actionButton, actionKey)
	bellowsWidget.Visible = widget == bellowsWidget
	pourWidget.Visible = widget == pourWidget
	hammerWidget.Visible = widget == hammerWidget
end

local function feedback(key: string, color: Color3)
	C.Bind(hammerFeedback, key)
	hammerFeedback.TextColor3 = color
	hammerFeedback.TextTransparency = 0
	hammerFeedback.TextSize = S(30)
	TweenService:Create(hammerFeedback, TweenInfo.new(0.5), { TextTransparency = 1, TextSize = S(22) }):Play()
end

local function strike()
	local now = os.clock()
	local p = hammerParams
	if not p then
		return
	end
	local bestIndex: number? = nil
	local bestAbs = math.huge
	for i, t in beatTimes do
		if offsets[i] == 9 then
			local a = math.abs(now - t)
			if a < bestAbs and a <= 0.45 then
				bestIndex, bestAbs = i, a
			end
		end
	end
	if not bestIndex then
		feedback("FORGE_MISS", Theme.Danger)
		Sound.Play("ForgeHammerMiss")
		return
	end
	offsets[bestIndex] = now - beatTimes[bestIndex]
	if bestAbs <= F.Hammer.PerfectWindow then
		feedback("FORGE_PERFECT", Theme.Gold)
		Sound.Play("ForgeHammerPerfect")
	elseif bestAbs <= F.Hammer.HitWindow then
		feedback("FORGE_GOOD", Theme.Success)
		Sound.Play("ForgeHammerHit")
	else
		feedback("FORGE_MISS", Theme.Danger)
		Sound.Play("ForgeHammerMiss")
	end
end

local function onPress()
	if phase == "bellows" then
		heat = math.min(1, heat + F.Bellows.HeatPerPump)
		Sound.Play("ForgePump", 0.5, 0.9 + heat * 0.3)
		punch(actionButton)
	elseif phase == "pour" then
		holding = true
		Sound.Play("ForgePour", 0.4)
	elseif phase == "hammer" then
		strike()
		punch(actionButton)
	end
end

local function onRelease()
	if phase == "pour" and holding then
		holding = false
		if pourLevel > 0.02 then
			pourDone = true
		end
	end
end

local function runBellows(p: any): number
	phase = "bellows"
	setStep("FORGE_STEP_BELLOWS", "FORGE_HINT_BELLOWS", "FORGE_PUMP", bellowsWidget)
	zoneFrame.Position = UDim2.fromScale(0, 1 - p.ZoneMax)
	zoneFrame.Size = UDim2.fromScale(1, p.ZoneMax - p.ZoneMin)
	heat = 0.3
	local inZone, t = 0, 0
	while t < p.Duration do
		local dt = RunService.RenderStepped:Wait()
		if cancelled then
			return 0
		end
		t += dt
		heat = math.max(0, heat - p.Decay * dt)
		local inside = heat >= p.ZoneMin and heat <= p.ZoneMax
		if inside then
			inZone += dt
		end
		heatFill.Size = UDim2.fromScale(1, heat)
		heatFill.BackgroundColor3 = if inside then Theme.Success elseif heat > p.ZoneMax then Theme.Danger else Theme.Gold
		timerFill.Size = UDim2.fromScale(1 - t / p.Duration, 1)
	end
	return math.clamp(inZone / p.Duration, 0, 1)
end

local function runPour(p: any): number
	phase = "pour"
	setStep("FORGE_STEP_POUR", "FORGE_HINT_POUR", "FORGE_POUR", pourWidget)
	pourLine.Position = UDim2.fromScale(0.5, 1 - p.Target)
	pourLevel = 0
	holding = false
	pourDone = false
	timerFill.Size = UDim2.fromScale(1, 1)
	local started = os.clock()
	while not pourDone do
		local dt = RunService.RenderStepped:Wait()
		if cancelled then
			return 0
		end
		if holding then
			pourLevel = math.min(1, pourLevel + p.FillPerSecond * dt)
			if pourLevel >= 1 then
				pourDone = true
			end
		elseif os.clock() - started > 8 then
			pourDone = true -- игрок отошёл от экрана
		end
		local close = math.abs(pourLevel - p.Target) <= F.Pour.PerfectTolerance * 2
		pourFill.Size = UDim2.fromScale(1, pourLevel)
		pourFill.BackgroundColor3 = if close then Theme.Success else Color3.fromRGB(255, 140, 40)
	end
	holding = false
	-- сервер не примет слишком быстрое прохождение — подождём минимум
	local remaining = F.Pour.MinSeconds - (os.clock() - started)
	task.wait(math.max(0.4, remaining))
	return pourLevel
end

local function runHammer(p: any): { number }
	hammerParams = p
	phase = "hammer"
	setStep("FORGE_STEP_HAMMER", "FORGE_HINT_HAMMER", "FORGE_STRIKE", hammerWidget)
	local t0 = os.clock()
	beatTimes = {}
	offsets = {}
	for i = 1, p.Beats do
		beatTimes[i] = t0 + p.LeadIn + i * p.Interval
		offsets[i] = 9 -- промах, пока не ударил
	end
	local base = S(80)
	local finish = beatTimes[p.Beats] + F.Hammer.HitWindow + 0.15
	while os.clock() < finish do
		RunService.RenderStepped:Wait()
		if cancelled then
			return offsets
		end
		local now = os.clock()
		local nextIndex: number? = nil
		for i, t in beatTimes do
			if t + F.Hammer.HitWindow >= now and offsets[i] == 9 then
				nextIndex = i
				break
			end
		end
		if nextIndex then
			local timeTo = beatTimes[nextIndex] - now
			local k = math.clamp(timeTo / p.Interval, 0, 1)
			local size = base * (1 + 1.4 * k)
			hammerRing.Size = UDim2.fromOffset(size, size)
			hammerRing.Visible = true
			C.SetRaw(hammerCounter, `{nextIndex}/{p.Beats}`)
		else
			hammerRing.Visible = false
		end
		timerFill.Size = UDim2.fromScale(math.clamp(1 - (now - t0) / (finish - t0), 0, 1), 1)
	end
	hammerParams = nil
	return offsets
end

local function showResult(result: any)
	local weapon = result.weapon
	lastWeaponId = weapon.Id
	C.Bind(resultQuality, "FORGE_RESULT", { quality = result.quality })
	resultQuality.TextColor3 = if result.quality >= 90
		then Theme.Gold
		elseif result.quality >= 60 then Theme.Success
		else Theme.Text
	C.SetRaw(resultName, Localization.WeaponName(weapon.Type, weapon.Ore))
	C.Bind(resultDamage, "UI_DMG", { value = result.damage })
	showView("result")
	resultQuality.TextSize = S(48)
	TweenService:Create(resultQuality, TweenInfo.new(0.35, Enum.EasingStyle.Back), { TextSize = S(36) }):Play()
end

local function startForging()
	if running then
		return
	end
	running = true
	cancelled = false
	local response = UIController:Invoke("ForgeStart", selectedType, selectedOre)
	if not UIController:Handle(response) then
		running = false
		return
	end
	showView("game")
	-- ПК: пробел = кнопка действия (и не прыгать во время ковки)
	ContextActionService:BindActionAtPriority("ForgeAction", function(_, state: Enum.UserInputState)
		if state == Enum.UserInputState.Begin then
			onPress()
		elseif state == Enum.UserInputState.End then
			onRelease()
		end
		return Enum.ContextActionResult.Sink
	end, false, 3000, Enum.KeyCode.Space, Enum.KeyCode.ButtonA)
	local params = response.params
	local okRun, err = pcall(function()
		local bellows = runBellows(params.Bellows)
		if cancelled then
			return
		end
		local pour = runPour(params.Pour)
		if cancelled then
			return
		end
		local hammer = runHammer(params.Hammer)
		if cancelled then
			return
		end
		phase = "submit"
		setStep("FORGE_STEP_HAMMER", "FORGE_WORKING", "FORGE_STRIKE", nil)
		local result = UIController:Invoke("ForgeSubmit", response.sessionId, {
			Bellows = bellows,
			Pour = pour,
			Hammer = hammer,
		})
		if UIController:Handle(result) then
			showResult(result)
		else
			showView("select")
		end
	end)
	ContextActionService:UnbindAction("ForgeAction")
	if not okRun then
		warn("[Forge]", err)
		showView("select")
	end
	phase = "idle"
	running = false
end

-- ===== Построение окна =====

local function build()
	panel = C.Panel({
		Name = "ForgePanel",
		Size = UDim2.fromOffset(S(540), S(330)),
	})
	C.TitleBar(panel, "UI_FORGE_TITLE", function()
		UIController:ClosePanel()
	end)

	local content = UDim2.new(1, -S(24), 1, -S(58))
	local contentPos = UDim2.fromOffset(S(12), S(48))

	-- выбор
	selectView = C.Make("Frame", { Size = content, Position = contentPos, BackgroundTransparency = 1, Parent = panel })
	C.Text({ Key = "UI_CHOOSE_WEAPON", TextSize = 13, Color = Theme.TextDim, Size = UDim2.new(1, 0, 0, S(16)), Parent = selectView })
	local weaponRow = C.Make("Frame", {
		Size = UDim2.new(1, 0, 0, S(48)),
		Position = UDim2.fromOffset(0, S(18)),
		BackgroundTransparency = 1,
		Parent = selectView,
	})
	C.List(weaponRow, Enum.FillDirection.Horizontal, 8)
	for i, id in sortedKeys(Config.Weapons) do
		weaponButtons[id] = C.Button({
			Key = Config.Weapons[id].NameKey,
			Size = UDim2.new(1 / 3, -S(6), 1, 0),
			LayoutOrder = i,
			TextSize = 17,
			OnClick = function()
				selectedType = id
				refreshSelect()
			end,
			Parent = weaponRow,
		})
	end

	C.Text({
		Key = "UI_CHOOSE_ORE",
		TextSize = 13,
		Color = Theme.TextDim,
		Size = UDim2.new(1, 0, 0, S(16)),
		Position = UDim2.fromOffset(0, S(74)),
		Parent = selectView,
	})
	local oreRow = C.Make("Frame", {
		Size = UDim2.new(1, 0, 0, S(48)),
		Position = UDim2.fromOffset(0, S(92)),
		BackgroundTransparency = 1,
		Parent = selectView,
	})
	C.List(oreRow, Enum.FillDirection.Horizontal, 8)
	for i, id in sortedKeys(Config.Ores) do
		oreButtons[id] = C.Button({
			Size = UDim2.new(1 / 3, -S(6), 1, 0),
			LayoutOrder = i,
			TextSize = 14,
			OnClick = function()
				local data = DataController:Get()
				if data and data.Level >= Config.Ores[id].RequiredLevel then
					selectedOre = id
					refreshSelect()
				end
			end,
			Parent = oreRow,
		})
	end

	costLabel = C.Text({ TextSize = 14, Size = UDim2.new(1, 0, 0, S(18)), Position = UDim2.fromOffset(0, S(148)), Parent = selectView })
	previewLabel = C.Text({
		TextSize = 15,
		Font = Theme.FontBold,
		Color = Theme.Accent,
		Size = UDim2.new(1, 0, 0, S(18)),
		Position = UDim2.fromOffset(0, S(170)),
		Parent = selectView,
	})
	forgeButton = C.Button({
		Key = "UI_FORGE_BUTTON",
		Font = Theme.FontTitle,
		TextSize = 24,
		Size = UDim2.fromOffset(S(240), S(54)),
		Position = UDim2.new(0.5, 0, 1, 0),
		AnchorPoint = Vector2.new(0.5, 1),
		Color = Theme.Success,
		TextColor = Theme.Bg,
		OnClick = function()
			task.spawn(startForging)
		end,
		Parent = selectView,
	})

	-- мини-игра
	gameView = C.Make("Frame", { Size = content, Position = contentPos, BackgroundTransparency = 1, Visible = false, Parent = panel })
	stepTitle = C.Text({ Font = Theme.FontTitle, TextSize = 20, Color = Theme.Accent, Size = UDim2.new(1, 0, 0, S(22)), Parent = gameView })
	stepHint = C.Text({
		TextSize = 14,
		Color = Theme.TextDim,
		Size = UDim2.new(1, 0, 0, S(18)),
		Position = UDim2.fromOffset(0, S(24)),
		Parent = gameView,
	})
	local _, tFill = C.Bar({
		Size = UDim2.new(0.5, 0, 0, S(8)),
		Position = UDim2.new(0, 0, 1, 0),
		AnchorPoint = Vector2.new(0, 1),
		Color = Theme.Accent,
		Value = 1,
		Parent = gameView,
	})
	timerFill = tFill

	local widgetArea = C.Make("Frame", {
		Size = UDim2.new(0.5, 0, 1, -S(64)),
		Position = UDim2.fromOffset(0, S(48)),
		BackgroundTransparency = 1,
		Parent = gameView,
	})

	-- меха: вертикальный термометр с зелёной зоной
	bellowsWidget = C.Make("Frame", {
		Size = UDim2.new(0, S(46), 1, 0),
		Position = UDim2.fromScale(0.5, 0),
		AnchorPoint = Vector2.new(0.5, 0),
		BackgroundColor3 = Theme.Bg,
		Parent = widgetArea,
	})
	C.Corner(bellowsWidget, 6)
	C.Stroke(bellowsWidget, Theme.AccentDim, 2)
	zoneFrame = C.Make("Frame", {
		BackgroundColor3 = Theme.Success,
		BackgroundTransparency = 0.6,
		BorderSizePixel = 0,
		ZIndex = 2,
		Parent = bellowsWidget,
	})
	heatFill = C.Make("Frame", {
		Size = UDim2.fromScale(1, 0.3),
		Position = UDim2.fromScale(0, 1),
		AnchorPoint = Vector2.new(0, 1),
		BackgroundColor3 = Theme.Gold,
		BorderSizePixel = 0,
		ZIndex = 3,
		Parent = bellowsWidget,
	})
	C.Corner(heatFill, 6)

	-- заливка: форма, которая наполняется металлом
	pourWidget = C.Make("Frame", {
		Size = UDim2.new(0, S(70), 1, 0),
		Position = UDim2.fromScale(0.5, 0),
		AnchorPoint = Vector2.new(0.5, 0),
		BackgroundColor3 = Theme.Bg,
		Visible = false,
		Parent = widgetArea,
	})
	C.Corner(pourWidget, 6)
	C.Stroke(pourWidget, Theme.AccentDim, 2)
	pourFill = C.Make("Frame", {
		Size = UDim2.fromScale(1, 0),
		Position = UDim2.fromScale(0, 1),
		AnchorPoint = Vector2.new(0, 1),
		BackgroundColor3 = Color3.fromRGB(255, 140, 40),
		BorderSizePixel = 0,
		ZIndex = 2,
		Parent = pourWidget,
	})
	pourLine = C.Make("Frame", {
		Size = UDim2.new(1, S(16), 0, S(4)),
		AnchorPoint = Vector2.new(0.5, 0.5),
		BackgroundColor3 = Color3.new(1, 1, 1),
		BorderSizePixel = 0,
		ZIndex = 3,
		Parent = pourWidget,
	})

	-- молот: круг-цель и сходящееся кольцо
	hammerWidget = C.Make("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Visible = false, Parent = widgetArea })
	local target = C.Make("Frame", {
		Size = UDim2.fromOffset(S(80), S(80)),
		Position = UDim2.fromScale(0.5, 0.5),
		AnchorPoint = Vector2.new(0.5, 0.5),
		BackgroundColor3 = Theme.AccentDim,
		BackgroundTransparency = 0.4,
		Parent = hammerWidget,
	})
	C.Round(target)
	C.Stroke(target, Theme.Accent, 3)
	hammerRing = C.Make("Frame", {
		Size = UDim2.fromOffset(S(160), S(160)),
		Position = UDim2.fromScale(0.5, 0.5),
		AnchorPoint = Vector2.new(0.5, 0.5),
		BackgroundTransparency = 1,
		ZIndex = 2,
		Parent = hammerWidget,
	})
	C.Round(hammerRing)
	C.Stroke(hammerRing, Theme.Gold, 3)
	hammerCounter = C.Text({
		Font = Theme.FontBold,
		TextSize = 18,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.fromScale(1, 1),
		Parent = target,
	})
	hammerFeedback = C.Text({
		Font = Theme.FontTitle,
		TextSize = 22,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(30)),
		Position = UDim2.fromScale(0, 0),
		ZIndex = 3,
		Parent = hammerWidget,
	})

	-- большая кнопка действия
	actionButton = C.Button({
		Font = Theme.FontTitle,
		TextSize = 26,
		Size = UDim2.fromOffset(S(190), S(120)),
		Position = UDim2.new(0.75, 0, 0.55, 0),
		AnchorPoint = Vector2.new(0.5, 0.5),
		Color = Color3.fromRGB(200, 90, 30),
		Silent = true,
		Parent = gameView,
	})
	actionButton.InputBegan:Connect(function(input: InputObject)
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch then
			onPress()
		end
	end)
	actionButton.InputEnded:Connect(function(input: InputObject)
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch then
			onRelease()
		end
	end)

	-- результат
	resultView = C.Make("Frame", { Size = content, Position = contentPos, BackgroundTransparency = 1, Visible = false, Parent = panel })
	resultQuality = C.Text({
		Font = Theme.FontTitle,
		TextSize = 36,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(50)),
		Position = UDim2.fromOffset(0, S(10)),
		Parent = resultView,
	})
	resultName = C.Text({
		Font = Theme.FontBold,
		TextSize = 20,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(26)),
		Position = UDim2.fromOffset(0, S(66)),
		Parent = resultView,
	})
	resultDamage = C.Text({
		TextSize = 17,
		Color = Theme.Accent,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(22)),
		Position = UDim2.fromOffset(0, S(96)),
		Parent = resultView,
	})
	C.Button({
		Key = "UI_EQUIP",
		Size = UDim2.fromOffset(S(200), S(52)),
		Position = UDim2.new(0.5, -S(6), 1, 0),
		AnchorPoint = Vector2.new(1, 1),
		Color = Theme.Success,
		TextColor = Theme.Bg,
		OnClick = function()
			if lastWeaponId then
				local result = UIController:Invoke("EquipWeapon", lastWeaponId)
				if UIController:Handle(result) then
					UIController:ClosePanel()
				end
			end
		end,
		Parent = resultView,
	})
	C.Button({
		Key = "FORGE_AGAIN",
		Size = UDim2.fromOffset(S(200), S(52)),
		Position = UDim2.new(0.5, S(6), 1, 0),
		AnchorPoint = Vector2.new(0, 1),
		OnClick = function()
			showView("select")
		end,
		Parent = resultView,
	})
end

function ForgeController:Start()
	build()
	UIController:RegisterPanel("Forge", {
		Frame = panel,
		OnOpen = function()
			if not running then
				showView("select")
			end
		end,
		OnClose = function()
			if running then
				cancelled = true
				Net.Event("ForgeCancel"):FireServer()
			end
		end,
	})

	DataController.Changed:Connect(function(key: string)
		if selectView and selectView.Visible and (key == "*" or key == "Ores" or key == "Level") then
			refreshSelect()
		end
	end)
end

function ForgeController:IsBusy(): boolean
	return phase ~= "idle"
end

return ForgeController
