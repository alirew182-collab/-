--!strict
-- ТОРГОВЕЦ: продать оружие (выбор нескольких + подтверждение), продать руду, купить кирки.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Localization = require(ReplicatedStorage.Shared.Localization)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local WeaponCard = require(script.Parent.Parent.UI.WeaponCard)
local DataController = require(script.Parent.DataController)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local MerchantController = {}

local panel: Frame
local tabButtons: { [string]: TextButton } = {}
local tabFrames: { [string]: Frame } = {}
local currentTab = "Weapons"
local selected: { [string]: boolean } = {}
local confirmUntil = 0

local weaponGrid: ScrollingFrame
local sellButton: TextButton
local oreList: ScrollingFrame
local pickList: ScrollingFrame

local function sortedIds(tbl: { [string]: any }): { string }
	local ids = {}
	for id in tbl do
		table.insert(ids, id)
	end
	table.sort(ids, function(a, b)
		return tbl[a].Order < tbl[b].Order
	end)
	return ids
end

local function clear(frame: Instance)
	for _, child in frame:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
end

local function selectedTotal(data: any): (number, number)
	local gold, count = 0, 0
	for id in selected do
		local w = data.Weapons[id]
		if w then
			gold += WeaponMath.SellPrice(w)
			count += 1
		end
	end
	return gold, count
end

local function refreshSellButton(data: any)
	local gold, count = selectedTotal(data)
	if count == 0 then
		C.Bind(sellButton, "UI_SELL_NOTHING")
		sellButton.BackgroundColor3 = Theme.Disabled
	elseif os.clock() < confirmUntil then
		C.Bind(sellButton, "UI_CONFIRM")
		sellButton.BackgroundColor3 = Theme.Danger
	else
		C.Bind(sellButton, "UI_SELL_SELECTED", { gold = gold })
		sellButton.BackgroundColor3 = Theme.Gold
	end
end

local function refreshWeapons(data: any)
	WeaponCard.Clear(weaponGrid)
	-- выбросить из выбора то, чего больше нет
	for id in selected do
		if not data.Weapons[id] or data.EquippedWeapon == id then
			selected[id] = nil
		end
	end
	for i, weapon in WeaponCard.Sorted(data.Weapons) do
		local equipped = data.EquippedWeapon == weapon.Id
		WeaponCard.Create({
			Weapon = weapon,
			Equipped = equipped,
			Selected = selected[weapon.Id] == true,
			ShowPrice = true,
			LayoutOrder = i,
			Parent = weaponGrid,
			OnClick = function()
				if equipped then
					UIController:Toast("ERR_EQUIPPED", nil, "error")
					return
				end
				selected[weapon.Id] = if selected[weapon.Id] then nil else true
				confirmUntil = 0
				refreshWeapons(DataController:Get())
			end,
		})
	end
	refreshSellButton(data)
end

local function row(parent: Instance, order: number): Frame
	local frame = C.Make("Frame", {
		Size = UDim2.new(1, -S(8), 0, S(46)),
		BackgroundColor3 = Theme.PanelLight,
		BackgroundTransparency = 0.2,
		LayoutOrder = order,
		Parent = parent,
	})
	C.Corner(frame, 6)
	return frame
end

local function refreshOres(data: any)
	clear(oreList)
	for i, id in sortedIds(Config.Ores) do
		local ore = Config.Ores[id]
		local count = data.Ores[id] or 0
		local r = row(oreList, i)
		C.Text({
			Key = "UI_ORE_COUNT",
			Args = { ore = "@" .. ore.NameKey, count = count },
			Font = Theme.FontBold,
			TextSize = 14,
			Size = UDim2.new(0.45, 0, 1, 0),
			Position = UDim2.fromOffset(S(10), 0),
			Parent = r,
		})
		C.Text({
			Key = "UI_PRICE",
			Args = { gold = ore.SellPrice },
			TextSize = 12,
			Color = Theme.Gold,
			Size = UDim2.new(0.2, 0, 1, 0),
			Position = UDim2.fromScale(0.42, 0),
			Parent = r,
		})
		local function sell(amount: number)
			local result = UIController:Invoke("SellOres", id, amount)
			if UIController:Handle(result) then
				Sound.Play("Coins")
				UIController:Toast("TOAST_SOLD", { count = result.count, gold = result.gold }, "gold")
			end
		end
		C.Button({
			Key = "UI_SELL_ONE",
			TextSize = 12,
			Size = UDim2.new(0.17, 0, 0, S(36)),
			Position = UDim2.new(0.63, 0, 0.5, 0),
			AnchorPoint = Vector2.new(0, 0.5),
			Color = if count > 0 then Theme.AccentDim else Theme.Disabled,
			OnClick = function()
				if count > 0 then
					sell(1)
				end
			end,
			Parent = r,
		})
		C.Button({
			Key = "UI_SELL_ALL",
			TextSize = 12,
			Size = UDim2.new(0.17, 0, 0, S(36)),
			Position = UDim2.new(1, -S(6), 0.5, 0),
			AnchorPoint = Vector2.new(1, 0.5),
			Color = if count > 0 then Theme.AccentDim else Theme.Disabled,
			OnClick = function()
				if count > 0 then
					sell(count)
				end
			end,
			Parent = r,
		})
	end
end

local function refreshPickaxes(data: any)
	clear(pickList)
	for i, id in sortedIds(Config.Pickaxes) do
		local def = Config.Pickaxes[id]
		local owned = data.OwnedPickaxes[id] == true
		local r = row(pickList, i)
		C.Text({
			Text = `{Localization.Get(def.NameKey)} · {Localization.Get("UI_POWER", { value = def.Power })}`,
			Font = Theme.FontBold,
			TextSize = 14,
			Size = UDim2.new(0.6, 0, 1, 0),
			Position = UDim2.fromOffset(S(10), 0),
			Parent = r,
		})
		local key, args, color
		if owned then
			key, args, color = "UI_OWNED", nil, Theme.Disabled
		elseif data.Level < def.RequiredLevel then
			key, args, color = "UI_REQ_LEVEL", { level = def.RequiredLevel }, Theme.Disabled
		else
			key, args, color = "UI_BUY", { price = def.Price }, if data.Gold >= def.Price then Theme.Gold else Theme.Disabled
		end
		C.Button({
			Key = key,
			Args = args,
			TextSize = 13,
			TextColor = if color == Theme.Gold then Theme.Bg else Theme.Text,
			Size = UDim2.new(0.34, 0, 0, S(36)),
			Position = UDim2.new(1, -S(6), 0.5, 0),
			AnchorPoint = Vector2.new(1, 0.5),
			Color = color,
			OnClick = function()
				if owned then
					return
				end
				local result = UIController:Invoke("BuyPickaxe", id)
				if UIController:Handle(result) then
					Sound.Play("Coins")
					UIController:Toast("TOAST_BOUGHT", { item = "@" .. def.NameKey }, "success")
				end
			end,
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
	if currentTab == "Weapons" then
		refreshWeapons(data)
	elseif currentTab == "Ores" then
		refreshOres(data)
	else
		refreshPickaxes(data)
	end
end

local function sellSelected()
	local data = DataController:Get()
	if not data then
		return
	end
	local ids = {}
	for id in selected do
		table.insert(ids, id)
	end
	if #ids == 0 then
		return
	end
	if os.clock() > confirmUntil then
		-- первый тап — просим подтвердить
		confirmUntil = os.clock() + 3
		refreshSellButton(data)
		task.delay(3.05, function()
			local d = DataController:Get()
			if d and panel.Visible then
				refreshSellButton(d)
			end
		end)
		return
	end
	confirmUntil = 0
	local result = UIController:Invoke("SellWeapons", ids)
	if UIController:Handle(result) then
		table.clear(selected)
		Sound.Play("Coins")
		UIController:Toast("TOAST_SOLD", { count = result.count, gold = result.gold }, "gold")
	end
	refresh()
end

local function build()
	panel = C.Panel({ Name = "MerchantPanel", Size = UDim2.fromOffset(S(540), S(330)) })
	C.TitleBar(panel, "UI_MERCHANT_TITLE", function()
		UIController:ClosePanel()
	end)

	local tabs = C.Make("Frame", {
		Size = UDim2.new(1, -S(24), 0, S(38)),
		Position = UDim2.fromOffset(S(12), S(46)),
		BackgroundTransparency = 1,
		Parent = panel,
	})
	C.List(tabs, Enum.FillDirection.Horizontal, 6)
	for i, info in { { "Weapons", "UI_WEAPONS" }, { "Ores", "UI_ORES" }, { "Pickaxes", "UI_PICKAXES" } } do
		local name, key = info[1], info[2]
		tabButtons[name] = C.Button({
			Key = key,
			TextSize = 15,
			Size = UDim2.new(1 / 3, -S(4), 1, 0),
			LayoutOrder = i,
			OnClick = function()
				currentTab = name
				refresh()
			end,
			Parent = tabs,
		})
	end

	local contentSize = UDim2.new(1, -S(24), 1, -S(100))
	local contentPos = UDim2.fromOffset(S(12), S(90))

	-- оружие
	local weaponsFrame = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Parent = panel })
	weaponGrid = WeaponCard.Grid(weaponsFrame, UDim2.new(1, 0, 1, -S(52)), UDim2.new())
	sellButton = C.Button({
		Font = Theme.FontTitle,
		TextSize = 16,
		TextColor = Theme.Bg,
		Size = UDim2.new(1, 0, 0, S(46)),
		Position = UDim2.fromScale(0, 1),
		AnchorPoint = Vector2.new(0, 1),
		Color = Theme.Gold,
		OnClick = sellSelected,
		Parent = weaponsFrame,
	})
	tabFrames.Weapons = weaponsFrame

	-- руда
	local oresFrame = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Parent = panel })
	oreList = C.Make("ScrollingFrame", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(4),
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		Parent = oresFrame,
	})
	C.List(oreList, Enum.FillDirection.Vertical, 6)
	tabFrames.Ores = oresFrame

	-- кирки
	local picksFrame = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Parent = panel })
	pickList = C.Make("ScrollingFrame", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(4),
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		Parent = picksFrame,
	})
	C.List(pickList, Enum.FillDirection.Vertical, 6)
	tabFrames.Pickaxes = picksFrame
end

function MerchantController:Start()
	build()
	UIController:RegisterPanel("Merchant", {
		Frame = panel,
		OnOpen = function()
			table.clear(selected)
			confirmUntil = 0
			refresh()
		end,
	})
	DataController.Changed:Connect(function(key: string)
		if
			key == "*"
			or key == "Weapons"
			or key == "EquippedWeapon"
			or key == "Ores"
			or key == "Gold"
			or key == "Level"
			or key == "OwnedPickaxes"
		then
			refresh()
		end
	end)
	Localization.Changed:Connect(refresh)
end

return MerchantController
