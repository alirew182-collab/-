--!strict
-- МЕНЮ: ежедневные задания и серия входов, коды, настройки, статистика.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Format = require(ReplicatedStorage.Shared.Format)
local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local C = require(script.Parent.Parent.UI.Components)
local ClientSettings = require(script.Parent.Parent.UI.ClientSettings)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local LocalizationController = require(script.Parent.LocalizationController)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local MenuController = {}

local panel: Frame
local tabButtons: { [string]: TextButton } = {}
local tabFrames: { [string]: Frame } = {}
local currentTab = "Quests"
local questList: ScrollingFrame
local streakLabel: TextLabel
local codeBox: TextBox
local settingsList: ScrollingFrame
local statsList: ScrollingFrame

local SETTINGS = {
	{ Key = "Sfx", TitleKey = "UI_SET_SFX" },
	{ Key = "Music", TitleKey = "UI_SET_MUSIC" },
	{ Key = "DamageNumbers", TitleKey = "UI_SET_DMG" },
	{ Key = "LowGraphics", TitleKey = "UI_SET_LOW" },
}

local STATS = {
	"OresMined",
	"WeaponsForged",
	"BestQuality",
	"BestDamage",
	"MythicForged",
	"MobsKilled",
	"BossesKilled",
	"EliteKills",
	"GatesCleared",
	"Parries",
	"GoldEarned",
	"Trades",
	"ShopSales",
}

local function clear(frame: Instance)
	for _, child in frame:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
end

local function scroll(parent: Instance): ScrollingFrame
	local list = C.Make("ScrollingFrame", {
		Size = UDim2.new(1, 0, 1, 0),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(4),
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		Parent = parent,
	})
	C.List(list, Enum.FillDirection.Vertical, 6)
	return list
end

local function row(parent: Instance, order: number, height: number?): Frame
	local frame = C.Make("Frame", {
		Size = UDim2.new(1, -S(8), 0, S(height or 44)),
		BackgroundColor3 = Theme.PanelLight,
		BackgroundTransparency = 0.2,
		LayoutOrder = order,
		Parent = parent,
	})
	C.Corner(frame, 6)
	return frame
end

local function questDef(id: string): any
	for _, q in Config.Daily.QuestPool do
		if q.Id == id then
			return q
		end
	end
	return nil
end

local function refreshQuests(data: any)
	clear(questList)
	local daily = data.Daily
	C.Bind(streakLabel, "UI_STREAK", { days = daily.Streak, next = daily.Streak + 1 })
	for i, q in daily.Quests do
		local def = questDef(q.Id)
		if def then
			local r = row(questList, i, 58)
			C.Text({
				Key = def.TitleKey,
				Args = { count = def.Count },
				Font = Theme.FontBold,
				TextSize = 14,
				Color = if q.Done then Theme.Success else Theme.Text,
				Size = UDim2.new(0.65, 0, 0, S(20)),
				Position = UDim2.fromOffset(S(12), S(8)),
				Parent = r,
			})
			C.Text({
				Key = "UI_REWARD",
				Args = { gold = def.RewardGold, xp = def.RewardXp },
				TextSize = 12,
				Color = Theme.Gold,
				AlignX = Enum.TextXAlignment.Right,
				Size = UDim2.new(0.35, -S(16), 0, S(20)),
				Position = UDim2.new(0.65, 0, 0, S(8)),
				Parent = r,
			})
			C.Bar({
				Size = UDim2.new(0.7, 0, 0, S(12)),
				Position = UDim2.fromOffset(S(12), S(36)),
				Value = math.clamp(q.Progress / def.Count, 0, 1),
				Color = if q.Done then Theme.Success else Theme.Accent,
				Parent = r,
			})
			C.Text({
				Text = if q.Done then Localization.Get("UI_DONE") else `{q.Progress}/{def.Count}`,
				TextSize = 13,
				Font = Theme.FontBold,
				Color = if q.Done then Theme.Success else Theme.TextDim,
				AlignX = Enum.TextXAlignment.Right,
				Size = UDim2.new(0.3, -S(16), 0, S(20)),
				Position = UDim2.new(0.7, 0, 0, S(32)),
				Parent = r,
			})
		end
	end
end

local function refreshSettings(data: any)
	clear(settingsList)
	-- язык
	local langRow = row(settingsList, 0)
	C.Text({ Key = "UI_SET_LANGUAGE", TextSize = 15, Size = UDim2.new(0.6, 0, 1, 0), Position = UDim2.fromOffset(S(10), 0), Parent = langRow })
	C.Button({
		Key = "UI_LANG",
		TextSize = 14,
		Size = UDim2.fromOffset(S(92), S(36)),
		Position = UDim2.new(1, -S(8), 0.5, 0),
		AnchorPoint = Vector2.new(1, 0.5),
		OnClick = function()
			LocalizationController:Toggle()
		end,
		Parent = langRow,
	})
	for i, setting in SETTINGS do
		local value = data.Settings[setting.Key]
		if value == nil then
			value = (ClientSettings :: any)[setting.Key]
		end
		local r = row(settingsList, i)
		C.Text({ Key = setting.TitleKey, TextSize = 15, Size = UDim2.new(0.6, 0, 1, 0), Position = UDim2.fromOffset(S(10), 0), Parent = r })
		C.Button({
			Key = if value then "UI_ON" else "UI_OFF",
			TextSize = 14,
			Size = UDim2.fromOffset(S(92), S(36)),
			Position = UDim2.new(1, -S(8), 0.5, 0),
			AnchorPoint = Vector2.new(1, 0.5),
			Color = if value then Theme.Accent else Theme.Disabled,
			TextColor = if value then Theme.Bg else Theme.Text,
			OnClick = function()
				local newValue = not value
				data.Settings[setting.Key] = newValue
				ClientSettings.Set(setting.Key, newValue)
				Net.Event("SetSetting"):FireServer(setting.Key, newValue)
				refreshSettings(data)
			end,
			Parent = r,
		})
	end
end

local function refreshStats(data: any)
	clear(statsList)
	for i, key in STATS do
		local r = row(statsList, i, 30)
		C.Text({
			Key = "UI_STAT_LINE",
			Args = { name = "@STAT_" .. string.upper(key), value = Format.Short(data.Stats[key] or 0) },
			TextSize = 14,
			Size = UDim2.new(1, -S(20), 1, 0),
			Position = UDim2.fromOffset(S(12), 0),
			Parent = r,
		})
	end
end

local function refresh()
	local data = DataController:Get()
	if not data or not panel.Visible then
		return
	end
	for name, button in tabButtons do
		local active = name == currentTab
		button.BackgroundColor3 = if active then Theme.Accent else Theme.PanelLight
		button.TextColor3 = if active then Theme.Bg else Theme.Text
		tabFrames[name].Visible = active
	end
	if currentTab == "Quests" then
		refreshQuests(data)
	elseif currentTab == "Settings" then
		refreshSettings(data)
	elseif currentTab == "Stats" then
		refreshStats(data)
	end
end

local function redeem()
	local code = codeBox.Text
	if code == "" then
		return
	end
	local result = UIController:Invoke("RedeemCode", code)
	if UIController:Handle(result) then
		codeBox.Text = ""
		Sound.Play("Coins")
		UIController:Toast("TOAST_CODE_OK", nil, "success")
	end
end

local function build()
	panel = C.Panel({ Name = "MenuPanel", Size = UDim2.fromOffset(S(540), S(332)) })
	C.TitleBar(panel, "UI_MENU", function()
		UIController:ClosePanel()
	end)

	local tabs = C.Make("Frame", {
		Size = UDim2.new(1, -S(24), 0, S(36)),
		Position = UDim2.fromOffset(S(12), S(48)),
		BackgroundTransparency = 1,
		Parent = panel,
	})
	C.List(tabs, Enum.FillDirection.Horizontal, 6)
	local contentSize = UDim2.new(1, -S(24), 1, -S(100))
	local contentPos = UDim2.fromOffset(S(12), S(88))
	for i, info in {
		{ "Quests", "UI_TAB_QUESTS" },
		{ "Codes", "UI_TAB_CODES" },
		{ "Settings", "UI_TAB_SETTINGS" },
		{ "Stats", "UI_TAB_STATS" },
	} do
		local name, key = info[1], info[2]
		tabButtons[name] = C.Button({
			Key = key,
			TextSize = 14,
			Size = UDim2.new(0.25, -S(4), 1, 0),
			LayoutOrder = i,
			OnClick = function()
				currentTab = name
				refresh()
			end,
			Parent = tabs,
		})
		tabFrames[name] = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Visible = false, Parent = panel })
	end

	-- задания
	streakLabel = C.Text({ TextSize = 13, Color = Theme.Gold, Size = UDim2.new(1, 0, 0, S(20)), Parent = tabFrames.Quests })
	local questHolder = C.Make("Frame", {
		Size = UDim2.new(1, 0, 1, -S(24)),
		Position = UDim2.fromOffset(0, S(24)),
		BackgroundTransparency = 1,
		Parent = tabFrames.Quests,
	})
	questList = scroll(questHolder)

	-- коды
	local codes = tabFrames.Codes
	codeBox = C.Make("TextBox", {
		Size = UDim2.new(1, -S(152), 0, S(48)),
		Position = UDim2.fromOffset(0, S(20)),
		BackgroundColor3 = Theme.Bg,
		Font = Theme.FontBold,
		TextSize = S(18),
		TextColor3 = Theme.Text,
		PlaceholderColor3 = Theme.TextDim,
		ClearTextOnFocus = false,
		Text = "",
		Parent = codes,
	})
	C.Corner(codeBox, 6)
	C.Stroke(codeBox)
	C.Button({
		Key = "UI_REDEEM",
		TextSize = 16,
		Size = UDim2.fromOffset(S(140), S(48)),
		Position = UDim2.new(1, 0, 0, S(20)),
		AnchorPoint = Vector2.new(1, 0),
		Color = Theme.Accent,
		TextColor = Theme.Bg,
		OnClick = redeem,
		Parent = codes,
	})
	codeBox.FocusLost:Connect(function(enter)
		if enter then
			redeem()
		end
	end)
	C.Text({
		Key = "UI_CODES_HINT",
		TextSize = 13,
		Color = Theme.TextDim,
		Wrapped = true,
		Size = UDim2.new(1, 0, 0, S(40)),
		Position = UDim2.fromOffset(0, S(80)),
		Parent = codes,
	})

	settingsList = scroll(tabFrames.Settings)
	statsList = scroll(tabFrames.Stats)
end

local function applySettings(data: any)
	for _, setting in SETTINGS do
		local value = data.Settings[setting.Key]
		if type(value) == "boolean" then
			ClientSettings.Set(setting.Key, value)
		end
	end
end

function MenuController:Start()
	build()
	-- плейсхолдер у TextBox — через PlaceholderText, а не Text
	Localization.Changed:Connect(function()
		codeBox.PlaceholderText = Localization.Get("UI_CODE_PLACEHOLDER")
		refresh()
	end)
	codeBox.PlaceholderText = Localization.Get("UI_CODE_PLACEHOLDER")

	UIController:RegisterPanel("Menu", { Frame = panel, OnOpen = refresh })
	DataController.Changed:Connect(function(key: string)
		if key == "*" then
			applySettings(DataController:Get())
		end
		if key == "*" or key == "Daily" or key == "Stats" or key == "Settings" then
			refresh()
		end
	end)
	local data = DataController:Get()
	if data then
		applySettings(data)
	end
end

return MenuController
