--!strict
-- Карточка оружия для инвентаря и торговца.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Localization = require(ReplicatedStorage.Shared.Localization)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local C = require(script.Parent.Components)
local Theme = require(script.Parent.Theme)

local S = Theme.S

local WeaponCard = {}

export type CardProps = {
	Weapon: any,
	Equipped: boolean?,
	Selected: boolean?,
	ShowPrice: boolean?,
	LayoutOrder: number?,
	OnClick: (() -> ())?,
	Parent: Instance,
}

function WeaponCard.Create(props: CardProps): TextButton
	local weapon = props.Weapon
	local rarity = Config.Rarities[weapon.Rarity] or Config.Rarities.Common

	local card = C.Make("TextButton", {
		Name = weapon.Id,
		BackgroundColor3 = if props.Selected then Theme.AccentDim else Theme.PanelLight,
		AutoButtonColor = true,
		Text = "",
		LayoutOrder = props.LayoutOrder or 0,
		Parent = props.Parent,
	})
	C.Corner(card, 6)
	C.Stroke(card, if props.Equipped then Theme.Gold elseif props.Selected then Theme.Accent else rarity.Color, if props.Selected or props.Equipped then 2.5 else 1.2, 0.1)

	C.Make("Frame", {
		Name = "RarityStrip",
		Size = UDim2.new(0, S(4), 1, -S(10)),
		Position = UDim2.fromOffset(S(5), S(5)),
		BackgroundColor3 = rarity.Color,
		BorderSizePixel = 0,
		Parent = card,
	})

	C.Text({
		Text = Localization.WeaponName(weapon.Type, weapon.Ore),
		Font = Theme.FontBold,
		TextSize = 14,
		Size = UDim2.new(1, -S(18), 0, S(18)),
		Position = UDim2.fromOffset(S(14), S(5)),
		Parent = card,
	})
	C.Text({
		Key = rarity.NameKey,
		TextSize = 11,
		Color = rarity.Color,
		Size = UDim2.new(1, -S(18), 0, S(13)),
		Position = UDim2.fromOffset(S(14), S(23)),
		Parent = card,
	})
	C.Text({
		Key = "UI_DMG",
		Args = { value = WeaponMath.Damage(weapon) },
		Font = Theme.FontBold,
		TextSize = 14,
		Color = Theme.Accent,
		Size = UDim2.new(0.5, -S(10), 0, S(16)),
		Position = UDim2.fromOffset(S(14), S(38)),
		Parent = card,
	})
	C.Text({
		Key = "UI_QUALITY",
		Args = { value = weapon.Quality },
		TextSize = 12,
		Color = Theme.TextDim,
		Size = UDim2.new(1, -S(18), 0, S(14)),
		Position = UDim2.fromOffset(S(14), S(56)),
		Parent = card,
	})

	if props.Equipped then
		C.Text({
			Key = "UI_EQUIPPED",
			Font = Theme.FontBold,
			TextSize = 11,
			Color = Theme.Gold,
			AlignX = Enum.TextXAlignment.Right,
			Size = UDim2.new(0.5, -S(6), 0, S(16)),
			Position = UDim2.new(0.5, 0, 0, S(38)),
			Parent = card,
		})
	elseif props.ShowPrice then
		C.Text({
			Text = `+{WeaponMath.SellPrice(weapon)}`,
			Font = Theme.FontBold,
			TextSize = 13,
			Color = Theme.Gold,
			AlignX = Enum.TextXAlignment.Right,
			Size = UDim2.new(0.5, -S(6), 0, S(16)),
			Position = UDim2.new(0.5, 0, 0, S(38)),
			Parent = card,
		})
	end

	if props.OnClick then
		card.Activated:Connect(props.OnClick)
	end
	return card
end

-- Оружие игрока, отсортированное по урону (сильное сверху)
function WeaponCard.Sorted(weapons: { [string]: any }): { any }
	local list = {}
	for _, w in weapons do
		table.insert(list, w)
	end
	table.sort(list, function(a, b)
		local da, db = WeaponMath.Damage(a), WeaponMath.Damage(b)
		if da ~= db then
			return da > db
		end
		return a.CreatedAt > b.CreatedAt
	end)
	return list
end

-- Скролл с сеткой карточек
function WeaponCard.Grid(parent: Instance, size: UDim2, position: UDim2): ScrollingFrame
	local scroll = C.Make("ScrollingFrame", {
		Size = size,
		Position = position,
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = S(6),
		ScrollBarImageColor3 = Theme.Accent,
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
		ScrollingDirection = Enum.ScrollingDirection.Y,
		Parent = parent,
	})
	C.Make("UIGridLayout", {
		CellSize = UDim2.fromOffset(S(158), S(76)),
		CellPadding = UDim2.fromOffset(S(6), S(6)),
		SortOrder = Enum.SortOrder.LayoutOrder,
		Parent = scroll,
	})
	return scroll
end

function WeaponCard.Clear(scroll: ScrollingFrame)
	for _, child in scroll:GetChildren() do
		if child:IsA("GuiObject") then
			child:Destroy()
		end
	end
end

return WeaponCard
