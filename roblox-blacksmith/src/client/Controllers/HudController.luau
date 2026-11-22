--!strict
--[[
	HUD: ранг, уровень, опыт, золото, бонус опыта, язык (слева сверху);
	кнопки «Меню» и «Инвентарь» справа по центру (не мешают стику и прыжку на телефоне);
	полоса подсказок управления внизу — только для ПК и геймпада.
	Горячие клавиши: I / Y(геймпад) — инвентарь, M / ↑(геймпад) — меню.
]]

local ContextActionService = game:GetService("ContextActionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Config = require(ReplicatedStorage.Shared.Config)
local Format = require(ReplicatedStorage.Shared.Format)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local InputController = require(script.Parent.InputController)
local LocalizationController = require(script.Parent.LocalizationController)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local player = Players.LocalPlayer

local HudController = {}

local rankLabel: TextLabel
local levelLabel: TextLabel
local xpFill: Frame
local xpLabel: TextLabel
local goldLabel: TextLabel
local bonusLabel: TextLabel
local inventoryButton: TextButton
local controlsBar: TextLabel
local lastGold = -1

local function refresh()
	local data = DataController:Get()
	if not data then
		return
	end
	C.SetRaw(rankLabel, WeaponMath.RankById(data.Rank or "F").Short)
	C.Bind(levelLabel, "UI_LEVEL", { level = data.Level })
	if data.Level >= Config.Leveling.MaxLevel then
		xpFill.Size = UDim2.fromScale(1, 1)
		C.Bind(xpLabel, "UI_MAX_LEVEL")
	else
		local need = WeaponMath.XpToNext(data.Level)
		TweenService:Create(xpFill, TweenInfo.new(0.3), { Size = UDim2.fromScale(math.clamp(data.Xp / need, 0, 1), 1) })
			:Play()
		C.Bind(xpLabel, "UI_XP", { xp = Format.Short(data.Xp), need = Format.Short(need) })
	end
	C.SetRaw(goldLabel, Format.Short(data.Gold))
	if lastGold >= 0 and data.Gold > lastGold then
		Sound.Play("Coins", 0.4)
		goldLabel.TextSize = S(22)
		TweenService:Create(goldLabel, TweenInfo.new(0.25), { TextSize = S(17) }):Play()
	end
	lastGold = data.Gold
end

local function refreshBonus()
	local bonus = player:GetAttribute("XpBonus")
	local pct = if type(bonus) == "number" then math.floor(bonus * 100 + 0.5) else 0
	bonusLabel.Visible = pct > 0
	if pct > 0 then
		C.Bind(bonusLabel, "UI_XP_BONUS", { pct = pct })
	end
end

local function refreshControls()
	local mode = InputController.Mode
	controlsBar.Visible = mode ~= "Touch"
	if mode == "Gamepad" then
		C.Bind(controlsBar, "CONTROLS_PAD")
	else
		C.Bind(controlsBar, "CONTROLS_KBM")
	end
end

local function build()
	local hud = UIController.Layers.Hud

	-- ===== левый верхний блок =====
	local top = C.Panel({
		Name = "TopLeft",
		Size = UDim2.fromOffset(S(252), S(84)),
		Position = UDim2.fromOffset(S(12), S(8)),
		AnchorPoint = Vector2.zero,
		Transparency = 0.2,
		Adaptive = true,
		Parent = hud,
	})

	local badge = C.Make("Frame", {
		Name = "RankBadge",
		Size = UDim2.fromOffset(S(48), S(48)),
		Position = UDim2.fromOffset(S(8), S(8)),
		BackgroundColor3 = Theme.Bg,
		Parent = top,
	})
	C.Corner(badge, 6)
	C.Stroke(badge)
	rankLabel = C.Text({
		Font = Theme.FontTitle,
		TextSize = 30,
		Color = Theme.Text,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.fromScale(1, 1),
		Text = "F",
		Parent = badge,
	})

	levelLabel = C.Text({
		Font = Theme.FontTitle,
		TextSize = 18,
		Size = UDim2.new(1, -S(68), 0, S(20)),
		Position = UDim2.fromOffset(S(64), S(8)),
		Parent = top,
	})
	bonusLabel = C.Text({
		Font = Theme.FontBold,
		TextSize = 12,
		Color = Theme.Success,
		AlignX = Enum.TextXAlignment.Right,
		Size = UDim2.new(0, S(92), 0, S(20)),
		Position = UDim2.new(1, -S(8), 0, S(8)),
		AnchorPoint = Vector2.new(1, 0),
		Visible = false,
		Parent = top,
	})
	local _, fill = C.Bar({
		Size = UDim2.new(1, -S(72), 0, S(12)),
		Position = UDim2.fromOffset(S(64), S(28)),
		Parent = top,
	})
	xpFill = fill
	xpLabel = C.Text({
		TextSize = 12,
		Color = Theme.TextDim,
		Size = UDim2.new(1, -S(68), 0, S(16)),
		Position = UDim2.fromOffset(S(64), S(40)),
		Parent = top,
	})

	-- золото
	local goldIcon = C.Make("Frame", {
		Size = UDim2.fromOffset(S(16), S(16)),
		Position = UDim2.fromOffset(S(12), S(64)),
		BackgroundColor3 = Theme.Gold,
		Parent = top,
	})
	C.Round(goldIcon)
	goldLabel = C.Text({
		Font = Theme.FontBold,
		TextSize = 17,
		Color = Theme.Gold,
		Size = UDim2.fromOffset(S(120), S(20)),
		Position = UDim2.fromOffset(S(32), S(60)),
		Text = "0",
		Parent = top,
	})

	-- язык
	C.Button({
		Name = "Language",
		Key = "UI_LANG",
		TextSize = 14,
		Size = UDim2.fromOffset(S(48), S(28)),
		Position = UDim2.new(1, -S(8), 0, S(52)),
		AnchorPoint = Vector2.new(1, 0),
		Color = Theme.PanelLight,
		OnClick = function()
			LocalizationController:Toggle()
		end,
		Parent = top,
	})

	-- ===== правая колонка: Меню и Инвентарь =====
	local column = C.Make("Frame", {
		Name = "RightButtons",
		Size = UDim2.fromOffset(S(84), S(132)),
		Position = UDim2.new(1, -S(12), 0.5, -S(32)),
		AnchorPoint = Vector2.new(1, 0.5),
		BackgroundTransparency = 1,
		Parent = hud,
	})
	C.Adaptive(column)
	C.List(column, Enum.FillDirection.Vertical, 8)
	local menuButton = C.Button({
		Name = "Menu",
		Key = "UI_MENU",
		TextSize = 13,
		Size = UDim2.fromOffset(S(84), S(60)),
		Color = Theme.Panel,
		Transparency = 0.1,
		LayoutOrder = 1,
		OnClick = function()
			UIController:TogglePanel("Menu")
		end,
		Parent = column,
	})
	C.KeyHint(menuButton, { KeyboardMouse = "M", Gamepad = "↑" })
	inventoryButton = C.Button({
		Name = "Inventory",
		Key = "UI_INVENTORY",
		TextSize = 13,
		Size = UDim2.fromOffset(S(84), S(60)),
		Color = Theme.Panel,
		Transparency = 0.1,
		LayoutOrder = 2,
		OnClick = function()
			UIController:TogglePanel("Inventory")
		end,
		Parent = column,
	})
	C.KeyHint(inventoryButton, { KeyboardMouse = "I", Gamepad = "Y" })

	-- ===== подсказки управления (ПК / геймпад) =====
	controlsBar = C.Make("TextLabel", {
		Name = "Controls",
		Size = UDim2.fromOffset(S(520), S(24)),
		Position = UDim2.new(0.5, 0, 1, -S(8)),
		AnchorPoint = Vector2.new(0.5, 1),
		BackgroundColor3 = Theme.Bg,
		BackgroundTransparency = 0.45,
		Font = Theme.Font,
		TextSize = S(13),
		TextColor3 = Theme.TextDim,
		Parent = hud,
	})
	C.Corner(controlsBar, 6)
	C.Adaptive(controlsBar)
end

-- Подсказка обучения «надень оружие»: кнопка инвентаря получает рамку цвета углей.
-- Статично: мигание без живого события только отвлекает.
function HudController:SetInventoryPulse(enabled: boolean)
	local stroke = inventoryButton:FindFirstChildOfClass("UIStroke")
	if not stroke then
		return
	end
	if enabled then
		stroke.Color = Theme.Accent
		stroke.Thickness = S(2)
		stroke.Transparency = 0
	else
		stroke.Color = Theme.Text
		stroke.Thickness = S(1)
		stroke.Transparency = Theme.HairlineTransparency
	end
end

local function bindShortcut(name: string, panel: string, ...: Enum.KeyCode)
	ContextActionService:BindAction(name, function(_, state)
		if state ~= Enum.UserInputState.Begin then
			return Enum.ContextActionResult.Pass
		end
		UIController:TogglePanel(panel)
		return Enum.ContextActionResult.Sink
	end, false, ...)
end

function HudController:Start()
	build()
	DataController.Changed:Connect(function(key: string)
		if key == "*" or key == "Level" or key == "Xp" or key == "Gold" or key == "Rank" then
			refresh()
		end
	end)
	player:GetAttributeChangedSignal("XpBonus"):Connect(refreshBonus)
	InputController.Changed:Connect(refreshControls)
	refresh()
	refreshBonus()
	refreshControls()

	bindShortcut("ToggleInventory", "Inventory", Enum.KeyCode.I, Enum.KeyCode.ButtonY)
	bindShortcut("ToggleMenu", "Menu", Enum.KeyCode.M, Enum.KeyCode.DPadUp)
end

return HudController
