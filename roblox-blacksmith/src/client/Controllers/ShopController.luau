--!strict
--[[
	ЛАВКА.
	• Моя лавка (Инвентарь → «Моя лавка» или свой прилавок): выбрать оружие, указать цену, выставить;
	  справа — выставленное, кнопка «Снять».
	• Чужая лавка (подойти к прилавку → «Лавка»): выбрать предмет и купить (второе нажатие подтверждает).
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Format = require(ReplicatedStorage.Shared.Format)
local Localization = require(ReplicatedStorage.Shared.Localization)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local WeaponCard = require(script.Parent.Parent.UI.WeaponCard)
local DataController = require(script.Parent.DataController)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local SH = Config.Trading.Shop
local player = Players.LocalPlayer

local ShopController = {}

local panel: Frame
local title: TextLabel
local manageFrame: Frame
local browseFrame: Frame
-- моя лавка
local summaryLabel: TextLabel
local noStallLabel: TextLabel
local ownGrid: ScrollingFrame
local priceBox: TextBox
local listButton: TextButton
local listedList: ScrollingFrame
local pickedId: string? = nil
-- чужая лавка
local browseGrid: ScrollingFrame
local emptyLabel: TextLabel
local buyButton: TextButton
local browse: { [string]: any } = {}
local browsePick: { Weapon: any, Price: number }? = nil
local confirmUntil = 0

local mode = "manage"

local function clear(frame: Instance)
	for _, child in frame:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
end

local function suggestedPrice(weapon: any): number
	return math.max(SH.MinPrice, math.floor(WeaponMath.SellPrice(weapon) * SH.SuggestedPriceMult))
end

-- ===== Моя лавка =====

local function refreshManage()
	local data = DataController:Get()
	if not data then
		return
	end
	local count = 0
	for _ in data.Shop.Listings do
		count += 1
	end
	C.Bind(summaryLabel, "UI_SHOP_LISTINGS", {
		count = count,
		max = SH.MaxListings,
		fee = math.floor(SH.TaxRate * 100 + 0.5),
	})
	noStallLabel.Visible = player:GetAttribute("HasStall") ~= true

	-- оружие, которое можно выставить
	WeaponCard.Clear(ownGrid)
	if pickedId and (not data.Weapons[pickedId] or data.Shop.Listings[pickedId]) then
		pickedId = nil
	end
	local order = 0
	for _, weapon in WeaponCard.Sorted(data.Weapons) do
		if weapon.Id ~= data.EquippedWeapon and not data.Shop.Listings[weapon.Id] then
			order += 1
			WeaponCard.Create({
				Weapon = weapon,
				Selected = pickedId == weapon.Id,
				LayoutOrder = order,
				Parent = ownGrid,
				OnClick = function()
					pickedId = weapon.Id
					priceBox.Text = tostring(suggestedPrice(weapon))
					refreshManage()
				end,
			})
		end
	end
	listButton.BackgroundColor3 = if pickedId then Theme.Accent else Theme.Disabled
	listButton.TextColor3 = if pickedId then Theme.Bg else Theme.Muted

	-- выставленное
	clear(listedList)
	local rows = {}
	for id, price in data.Shop.Listings do
		local weapon = data.Weapons[id]
		if weapon then
			table.insert(rows, { Weapon = weapon, Price = price })
		end
	end
	table.sort(rows, function(a, b)
		return a.Price > b.Price
	end)
	for i, entry in rows do
		local row = C.Make("Frame", {
			Size = UDim2.new(1, -S(8), 0, S(48)),
			BackgroundColor3 = Theme.PanelLight,
			LayoutOrder = i,
			Parent = listedList,
		})
		C.Corner(row, 6)
		C.Text({
			Text = Localization.WeaponName(entry.Weapon.Type, entry.Weapon.Ore),
			Font = Theme.FontBold,
			TextSize = 13,
			Size = UDim2.new(1, -S(96), 0, S(20)),
			Position = UDim2.fromOffset(S(8), S(4)),
			Parent = row,
		})
		C.Text({
			Key = "UI_PRICE_LINE",
			Args = { gold = Format.Short(entry.Price) },
			TextSize = 12,
			Color = Theme.Gold,
			Size = UDim2.new(1, -S(96), 0, S(16)),
			Position = UDim2.fromOffset(S(8), S(24)),
			Parent = row,
		})
		C.Button({
			Key = "UI_SHOP_UNLIST",
			TextSize = 12,
			Size = UDim2.fromOffset(S(80), S(32)),
			Position = UDim2.new(1, -S(8), 0.5, 0),
			AnchorPoint = Vector2.new(1, 0.5),
			OnClick = function()
				UIController:Handle(UIController:Invoke("ShopUnlist", entry.Weapon.Id))
			end,
			Parent = row,
		})
	end
end

local function listPicked()
	local id = pickedId
	if not id then
		return
	end
	local price = tonumber(priceBox.Text)
	if not price or price ~= math.floor(price) or price < SH.MinPrice or price > SH.MaxPrice then
		UIController:Toast("ERR_PRICE", { min = SH.MinPrice, max = SH.MaxPrice }, "error")
		return
	end
	local result = UIController:Invoke("ShopList", id, price)
	if UIController:Handle(result) then
		pickedId = nil
		UIController:Toast("TOAST_LISTED", { price = price }, "success")
	end
end

-- ===== Чужая лавка =====

local function refreshBuyButton()
	local pick = browsePick
	if not pick then
		C.Bind(buyButton, "UI_SHOP_PICK")
		buyButton.BackgroundColor3 = Theme.Disabled
		buyButton.TextColor3 = Theme.Muted
	elseif os.clock() < confirmUntil then
		C.Bind(buyButton, "UI_CONFIRM")
		buyButton.BackgroundColor3 = Theme.Danger
		buyButton.TextColor3 = Theme.Text
	else
		C.Bind(buyButton, "UI_BUY", { price = Format.Short(pick.Price) })
		buyButton.BackgroundColor3 = Theme.Accent
		buyButton.TextColor3 = Theme.Bg
	end
end

local function refreshBrowse()
	C.Bind(title, "UI_SHOP_TITLE", { name = browse.OwnerName or "" })
	WeaponCard.Clear(browseGrid)
	local listings = browse.Listings or {}
	emptyLabel.Visible = #listings == 0
	local pickStillThere = false
	for i, entry in listings do
		local selected = browsePick ~= nil and browsePick.Weapon.Id == entry.Weapon.Id
		if selected then
			pickStillThere = true
		end
		WeaponCard.Create({
			Weapon = entry.Weapon,
			Selected = selected,
			PriceText = Format.Short(entry.Price),
			LayoutOrder = i,
			Parent = browseGrid,
			OnClick = function()
				browsePick = entry
				confirmUntil = 0
				refreshBrowse()
			end,
		})
	end
	if not pickStillThere then
		browsePick = nil
	end
	refreshBuyButton()
end

local function reloadBrowse()
	local result = UIController:Invoke("ShopFetch", browse.OwnerUserId)
	if type(result) == "table" and result.ok then
		browse = result
	else
		UIController:Handle(result)
		browse.Listings = {}
	end
	refreshBrowse()
end

local function buyPicked()
	local pick = browsePick
	if not pick then
		return
	end
	if os.clock() > confirmUntil then
		confirmUntil = os.clock() + 3
		refreshBuyButton()
		task.delay(3.05, function()
			if panel.Visible and mode == "browse" then
				refreshBuyButton()
			end
		end)
		return
	end
	confirmUntil = 0
	local result = UIController:Invoke("ShopBuy", browse.OwnerUserId, pick.Weapon.Id, pick.Price)
	if UIController:Handle(result) then
		Sound.Play("Coins")
		UIController:Toast("TOAST_BOUGHT", { item = `@WEAPON:{pick.Weapon.Type}:{pick.Weapon.Ore}` }, "success")
	end
	browsePick = nil
	reloadBrowse()
end

-- ===== Окно =====

local function build()
	panel = C.Panel({ Name = "ShopPanel", Size = UDim2.fromOffset(S(540), S(332)) })
	title = C.TitleBar(panel, "UI_MY_SHOP", function()
		UIController:ClosePanel()
	end)
	local contentSize = UDim2.new(1, -S(32), 1, -S(64))
	local contentPos = UDim2.fromOffset(S(16), S(52))

	-- моя лавка
	manageFrame = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Parent = panel })
	summaryLabel = C.Text({ TextSize = 13, Color = Theme.TextDim, Size = UDim2.new(1, 0, 0, S(16)), Parent = manageFrame })
	noStallLabel = C.Text({
		Key = "UI_SHOP_NO_STALL",
		TextSize = 12,
		Color = Theme.Muted,
		Wrapped = true,
		Size = UDim2.new(1, 0, 0, S(16)),
		Position = UDim2.fromOffset(0, S(16)),
		Visible = false,
		Parent = manageFrame,
	})
	local left = C.Make("Frame", {
		Size = UDim2.new(0.5, -S(8), 1, -S(36)),
		Position = UDim2.fromOffset(0, S(36)),
		BackgroundTransparency = 1,
		Parent = manageFrame,
	})
	ownGrid = WeaponCard.Grid(left, UDim2.new(1, 0, 1, -S(52)), UDim2.new())
	priceBox = C.Make("TextBox", {
		Size = UDim2.new(1, -S(104), 0, S(44)),
		Position = UDim2.fromScale(0, 1),
		AnchorPoint = Vector2.new(0, 1),
		BackgroundColor3 = Theme.Bg,
		Font = Theme.FontBold,
		TextSize = S(16),
		TextColor3 = Theme.Text,
		PlaceholderColor3 = Theme.Muted,
		ClearTextOnFocus = false,
		Text = "",
		Parent = left,
	})
	C.Corner(priceBox, 8)
	C.Stroke(priceBox)
	listButton = C.Button({
		Key = "UI_SHOP_LIST",
		TextSize = 14,
		Size = UDim2.fromOffset(S(96), S(44)),
		Position = UDim2.fromScale(1, 1),
		AnchorPoint = Vector2.new(1, 1),
		Color = Theme.Disabled,
		OnClick = listPicked,
		Parent = left,
	})
	listedList = C.Make("ScrollingFrame", {
		Size = UDim2.new(0.5, -S(8), 1, -S(36)),
		Position = UDim2.new(0.5, S(8), 0, S(36)),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(4),
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		Parent = manageFrame,
	})
	C.List(listedList, Enum.FillDirection.Vertical, 8)

	-- чужая лавка
	browseFrame = C.Make("Frame", { Size = contentSize, Position = contentPos, BackgroundTransparency = 1, Visible = false, Parent = panel })
	browseGrid = WeaponCard.Grid(browseFrame, UDim2.new(1, 0, 1, -S(56)), UDim2.new())
	emptyLabel = C.Text({
		Key = "UI_SHOP_EMPTY",
		TextSize = 15,
		Color = Theme.TextDim,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, 0, 0, S(40)),
		Position = UDim2.fromOffset(0, S(64)),
		Visible = false,
		Parent = browseFrame,
	})
	buyButton = C.Button({
		Font = Theme.FontTitle,
		TextSize = 16,
		Size = UDim2.new(1, 0, 0, S(48)),
		Position = UDim2.fromScale(0, 1),
		AnchorPoint = Vector2.new(0, 1),
		OnClick = buyPicked,
		Parent = browseFrame,
	})
end

function ShopController:Start()
	build()
	priceBox.PlaceholderText = Localization.Get("UI_SHOP_PRICE")
	Localization.Changed:Connect(function()
		priceBox.PlaceholderText = Localization.Get("UI_SHOP_PRICE")
		if panel.Visible and mode == "manage" then
			refreshManage()
		end
	end)

	UIController:RegisterPanel("Shop", {
		Frame = panel,
		OnOpen = function(payload: any)
			mode = if type(payload) == "table" and payload.Mode == "browse" then "browse" else "manage"
			manageFrame.Visible = mode == "manage"
			browseFrame.Visible = mode == "browse"
			if mode == "browse" then
				browse = payload
				browsePick = nil
				confirmUntil = 0
				refreshBrowse()
			else
				C.Bind(title, "UI_MY_SHOP")
				pickedId = nil
				priceBox.Text = ""
				refreshManage()
			end
		end,
	})

	DataController.Changed:Connect(function(key: string)
		if not panel.Visible then
			return
		end
		if mode == "manage" and (key == "*" or key == "Weapons" or key == "Shop" or key == "EquippedWeapon") then
			refreshManage()
		end
	end)
	player:GetAttributeChangedSignal("HasStall"):Connect(function()
		if panel.Visible and mode == "manage" then
			refreshManage()
		end
	end)
end

return ShopController
