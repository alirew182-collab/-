--!strict
--[[
	HUD: ранг, уровень, полоса опыта, золото, кнопка языка (слева сверху),
	кнопка инвентаря (справа по центру — не мешает стику и прыжку на телефоне).
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Config = require(ReplicatedStorage.Shared.Config)
local Format = require(ReplicatedStorage.Shared.Format)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local LocalizationController = require(script.Parent.LocalizationController)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local HudController = {}

local rankLabel: TextLabel
local levelLabel: TextLabel
local xpFill: Frame
local xpLabel: TextLabel
local goldLabel: TextLabel
local inventoryButton: TextButton
local pulseTween: Tween? = nil
local lastGold = -1

local function refresh()
	local data = DataController:Get()
	if not data then
		return
	end
	C.SetRaw(rankLabel, data.Rank or "F")
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

local function build()
	local hud = UIController.Layers.Hud

	-- ===== левый верхний блок =====
	local top = C.Panel({
		Name = "TopLeft",
		Size = UDim2.fromOffset(S(250), S(84)),
		Position = UDim2.fromOffset(S(10), S(8)),
		AnchorPoint = Vector2.zero,
		Transparency = 0.2,
		Parent = hud,
	})

	local badge = C.Make("Frame", {
		Name = "RankBadge",
		Size = UDim2.fromOffset(S(46), S(46)),
		Position = UDim2.fromOffset(S(8), S(8)),
		BackgroundColor3 = Theme.Bg,
		Parent = top,
	})
	C.Corner(badge, 6)
	C.Stroke(badge, Theme.Accent, 2)
	rankLabel = C.Text({
		Font = Theme.FontTitle,
		TextSize = 30,
		Color = Theme.Accent,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.fromScale(1, 1),
		Text = "F",
		Parent = badge,
	})

	levelLabel = C.Text({
		Font = Theme.FontTitle,
		TextSize = 18,
		Size = UDim2.new(1, -S(66), 0, S(20)),
		Position = UDim2.fromOffset(S(62), S(6)),
		Parent = top,
	})
	local _, fill = C.Bar({
		Size = UDim2.new(1, -S(72), 0, S(10)),
		Position = UDim2.fromOffset(S(62), S(29)),
		Parent = top,
	})
	xpFill = fill
	xpLabel = C.Text({
		TextSize = 12,
		Color = Theme.TextDim,
		Size = UDim2.new(1, -S(66), 0, S(14)),
		Position = UDim2.fromOffset(S(62), S(41)),
		Parent = top,
	})

	-- золото
	local goldIcon = C.Make("Frame", {
		Size = UDim2.fromOffset(S(16), S(16)),
		Position = UDim2.fromOffset(S(10), S(62)),
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
		Size = UDim2.fromOffset(S(46), S(26)),
		Position = UDim2.new(1, -S(8), 0, S(56)),
		AnchorPoint = Vector2.new(1, 0),
		Color = Theme.PanelLight,
		OnClick = function()
			LocalizationController:Toggle()
		end,
		Parent = top,
	})

	-- ===== кнопка инвентаря =====
	inventoryButton = C.Button({
		Name = "Inventory",
		Key = "UI_INVENTORY",
		TextSize = 13,
		Size = UDim2.fromOffset(S(84), S(60)),
		Position = UDim2.new(1, -S(10), 0.5, -S(40)),
		AnchorPoint = Vector2.new(1, 0.5),
		Color = Theme.Panel,
		Transparency = 0.1,
		OnClick = function()
			UIController:TogglePanel("Inventory")
		end,
		Parent = hud,
	})
end

-- Пульсация кнопки инвентаря (подсказка обучения «надень оружие»)
function HudController:SetInventoryPulse(enabled: boolean)
	if pulseTween then
		pulseTween:Cancel()
		pulseTween = nil
	end
	local stroke = inventoryButton:FindFirstChildOfClass("UIStroke")
	if not stroke then
		return
	end
	if enabled then
		stroke.Color = Theme.Gold
		stroke.Thickness = S(3)
		local tween = TweenService:Create(
			stroke,
			TweenInfo.new(0.5, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut, -1, true),
			{ Transparency = 0.8 }
		)
		tween:Play()
		pulseTween = tween
	else
		stroke.Color = Theme.Accent
		stroke.Thickness = S(1.5)
		stroke.Transparency = 0.25
	end
end

function HudController:Start()
	build()
	DataController.Changed:Connect(function(key: string)
		if key == "*" or key == "Level" or key == "Xp" or key == "Gold" or key == "Rank" then
			refresh()
		end
	end)
	refresh()
end

return HudController
