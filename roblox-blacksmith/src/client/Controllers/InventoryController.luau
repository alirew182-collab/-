--!strict
-- ИНВЕНТАРЬ: руда, кирки, оружие. Тап по карточке — надеть оружие.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Localization = require(ReplicatedStorage.Shared.Localization)
local C = require(script.Parent.Parent.UI.Components)
local Theme = require(script.Parent.Parent.UI.Theme)
local WeaponCard = require(script.Parent.Parent.UI.WeaponCard)
local DataController = require(script.Parent.DataController)
local UIController = require(script.Parent.UIController)

local S = Theme.S

local InventoryController = {}

local panel: Frame
local slotsLabel: TextLabel
local sideList: Frame
local grid: ScrollingFrame
local emptyLabel: TextLabel

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

local function refresh()
	local data = DataController:Get()
	if not data or not panel.Visible then
		return
	end

	-- слева: руда и кирки
	for _, child in sideList:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
	local order = 0
	local function nextOrder(): number
		order += 1
		return order
	end
	C.Text({ Key = "UI_ORES", Font = Theme.FontBold, TextSize = 13, Color = Theme.Muted, Size = UDim2.new(1, 0, 0, S(20)), LayoutOrder = nextOrder(), Parent = sideList })
	for _, id in sortedIds(Config.Ores) do
		C.Text({
			Key = "UI_ORE_COUNT",
			Args = { ore = "@" .. Config.Ores[id].NameKey, count = data.Ores[id] or 0 },
			TextSize = 13,
			Size = UDim2.new(1, 0, 0, S(20)),
			LayoutOrder = nextOrder(),
			Parent = sideList,
		})
	end
	C.Text({ Key = "UI_PICKAXES", Font = Theme.FontBold, TextSize = 13, Color = Theme.Muted, Size = UDim2.new(1, 0, 0, S(24)), AlignY = Enum.TextYAlignment.Bottom, LayoutOrder = nextOrder(), Parent = sideList })
	for _, id in sortedIds(Config.Pickaxes) do
		if data.OwnedPickaxes[id] then
			local def = Config.Pickaxes[id]
			local equipped = data.Pickaxe == id
			C.Button({
				Text = `{Localization.Get(def.NameKey)} · {Localization.Get("UI_POWER", { value = def.Power })}`,
				TextSize = 12,
				Size = UDim2.new(1, 0, 0, S(36)),
				Color = if equipped then Theme.AccentDim else Theme.PanelLight,
				StrokeColor = if equipped then Theme.Accent else nil,
				LayoutOrder = nextOrder(),
				OnClick = function()
					if not equipped then
						UIController:Handle(UIController:Invoke("EquipPickaxe", id))
					end
				end,
				Parent = sideList,
			})
		end
	end

	-- справа: оружие
	WeaponCard.Clear(grid)
	local list = WeaponCard.Sorted(data.Weapons)
	C.Bind(slotsLabel, "UI_SLOTS", { count = #list, max = Config.Economy.InventoryCapacity })
	emptyLabel.Visible = #list == 0
	for i, weapon in list do
		local equipped = data.EquippedWeapon == weapon.Id
		WeaponCard.Create({
			Weapon = weapon,
			Equipped = equipped,
			LayoutOrder = i,
			Parent = grid,
			OnClick = function()
				if equipped then
					return
				end
				local result = UIController:Invoke("EquipWeapon", weapon.Id)
				if UIController:Handle(result) then
					UIController:Toast("TOAST_EQUIPPED", { item = `@WEAPON:{weapon.Type}:{weapon.Ore}` }, "success")
				end
			end,
		})
	end
end

local function build()
	panel = C.Panel({ Name = "InventoryPanel", Size = UDim2.fromOffset(S(540), S(332)) })
	C.TitleBar(panel, "UI_INVENTORY", function()
		UIController:ClosePanel()
	end)
	slotsLabel = C.Text({
		TextSize = 14,
		Color = Theme.TextDim,
		AlignX = Enum.TextXAlignment.Right,
		Size = UDim2.fromOffset(S(80), S(36)),
		Position = UDim2.new(1, -S(56), 0, S(8)),
		AnchorPoint = Vector2.new(1, 0),
		Parent = panel,
	})

	sideList = C.Make("ScrollingFrame", {
		Size = UDim2.new(0, S(172), 1, -S(60)),
		Position = UDim2.fromOffset(S(12), S(48)),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(4),
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		Parent = panel,
	})
	C.List(sideList, Enum.FillDirection.Vertical, 4)

	grid = WeaponCard.Grid(panel, UDim2.new(1, -S(204), 1, -S(60)), UDim2.fromOffset(S(192), S(48)))
	emptyLabel = C.Text({
		Key = "UI_EMPTY_INV",
		TextSize = 15,
		Color = Theme.TextDim,
		Wrapped = true,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, -S(204), 0, S(60)),
		Position = UDim2.fromOffset(S(192), S(120)),
		Visible = false,
		Parent = panel,
	})
end

function InventoryController:Start()
	build()
	UIController:RegisterPanel("Inventory", {
		Frame = panel,
		OnOpen = refresh,
	})
	DataController.Changed:Connect(function(key: string)
		if
			key == "*"
			or key == "Weapons"
			or key == "EquippedWeapon"
			or key == "Ores"
			or key == "Pickaxe"
			or key == "OwnedPickaxes"
		then
			refresh()
		end
	end)
	Localization.Changed:Connect(refresh)
end

return InventoryController
