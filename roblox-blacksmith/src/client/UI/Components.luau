--!strict
--[[
	Строительные блоки интерфейса. Весь текст — через ключи локализации:
		C.Text({ Key = "UI_LEVEL", Args = { level = 5 }, ... })
	При смене языка все привязанные тексты обновятся сами.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Localization = require(ReplicatedStorage.Shared.Localization)
local Theme = require(script.Parent.Theme)
local Sound = require(script.Parent.Sound)

local S = Theme.S

local C = {}

-- ===== Локализация текста =====

type Binding = { Key: string, Args: { [string]: any }? }
local bindings: { [Instance]: Binding } = setmetatable({}, { __mode = "k" }) :: any

local function render(obj: Instance, b: Binding)
	(obj :: any).Text = Localization.Get(b.Key, b.Args)
end

function C.Bind(obj: Instance, key: string, args: { [string]: any }?)
	local b = { Key = key, Args = args }
	bindings[obj] = b
	render(obj, b)
end

-- Обычный текст без перевода (числа, имена игроков)
function C.SetRaw(obj: Instance, text: string)
	bindings[obj] = nil;
	(obj :: any).Text = text
end

Localization.Changed:Connect(function()
	for obj, b in bindings do
		if obj.Parent then
			render(obj, b)
		end
	end
end)

-- ===== Базовые =====

function C.Make(className: string, props: { [string]: any }, children: { Instance }?): any
	local inst = Instance.new(className)
	for key, value in props do
		if key ~= "Parent" then
			(inst :: any)[key] = value
		end
	end
	if children then
		for _, child in children do
			child.Parent = inst
		end
	end
	if props.Parent then
		inst.Parent = props.Parent
	end
	return inst
end

function C.Corner(parent: Instance, radius: number?): UICorner
	return C.Make("UICorner", { CornerRadius = UDim.new(0, S(radius or 8)), Parent = parent })
end

function C.Round(parent: Instance): UICorner
	return C.Make("UICorner", { CornerRadius = UDim.new(1, 0), Parent = parent })
end

function C.Stroke(parent: Instance, color: Color3?, thickness: number?, transparency: number?): UIStroke
	return C.Make("UIStroke", {
		Color = color or Theme.Accent,
		Thickness = math.max(1, S(thickness or 1.5)),
		Transparency = transparency or 0,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
		Parent = parent,
	})
end

function C.Padding(parent: Instance, px: number): UIPadding
	local p = UDim.new(0, S(px))
	return C.Make("UIPadding", { PaddingTop = p, PaddingBottom = p, PaddingLeft = p, PaddingRight = p, Parent = parent })
end

function C.List(
	parent: Instance,
	direction: Enum.FillDirection,
	padding: number,
	hAlign: Enum.HorizontalAlignment?,
	vAlign: Enum.VerticalAlignment?
): UIListLayout
	return C.Make("UIListLayout", {
		FillDirection = direction,
		Padding = UDim.new(0, S(padding)),
		SortOrder = Enum.SortOrder.LayoutOrder,
		HorizontalAlignment = hAlign or Enum.HorizontalAlignment.Left,
		VerticalAlignment = vAlign or Enum.VerticalAlignment.Top,
		Parent = parent,
	})
end

-- Окно в стиле Системы
function C.Panel(props: { [string]: any }): Frame
	local frame = C.Make("Frame", {
		Name = props.Name or "Panel",
		Size = props.Size,
		Position = props.Position or UDim2.fromScale(0.5, 0.5),
		AnchorPoint = props.AnchorPoint or Vector2.new(0.5, 0.5),
		BackgroundColor3 = props.Color or Theme.Panel,
		BackgroundTransparency = props.Transparency or 0.06,
		Visible = if props.Visible == nil then true else props.Visible,
		ZIndex = props.ZIndex or 1,
		Parent = props.Parent,
	})
	C.Corner(frame, props.Radius or 10)
	C.Stroke(frame, props.StrokeColor or Theme.Accent, props.StrokeThickness or 2, 0.1)
	C.Make("UIGradient", {
		Rotation = 90,
		Color = ColorSequence.new(Color3.new(1, 1, 1), Color3.fromRGB(150, 160, 190)),
		Parent = frame,
	})
	return frame
end

function C.Text(props: { [string]: any }): TextLabel
	local label = C.Make("TextLabel", {
		Name = props.Name or "Text",
		Size = props.Size or UDim2.fromScale(1, 0),
		Position = props.Position or UDim2.new(),
		AnchorPoint = props.AnchorPoint or Vector2.zero,
		BackgroundTransparency = 1,
		Font = props.Font or Theme.Font,
		TextSize = S(props.TextSize or 16),
		TextColor3 = props.Color or Theme.Text,
		TextXAlignment = props.AlignX or Enum.TextXAlignment.Left,
		TextYAlignment = props.AlignY or Enum.TextYAlignment.Center,
		TextWrapped = props.Wrapped or false,
		TextTruncate = if props.Wrapped then Enum.TextTruncate.None else Enum.TextTruncate.AtEnd,
		RichText = props.RichText or false,
		AutomaticSize = props.AutomaticSize or Enum.AutomaticSize.None,
		LayoutOrder = props.LayoutOrder or 0,
		ZIndex = props.ZIndex or 1,
		Visible = if props.Visible == nil then true else props.Visible,
		Text = props.Text or "",
		Parent = props.Parent,
	})
	if props.Key then
		C.Bind(label, props.Key, props.Args)
	end
	return label
end

-- Крупная кнопка с «нажатием» (удобно пальцем)
function C.Button(props: { [string]: any }): TextButton
	local color: Color3 = props.Color or Theme.AccentDim
	local button = C.Make("TextButton", {
		Name = props.Name or "Button",
		Size = props.Size,
		Position = props.Position or UDim2.new(),
		AnchorPoint = props.AnchorPoint or Vector2.zero,
		BackgroundColor3 = color,
		BackgroundTransparency = props.Transparency or 0,
		AutoButtonColor = false,
		Font = props.Font or Theme.FontBold,
		TextSize = S(props.TextSize or 18),
		TextColor3 = props.TextColor or Theme.Text,
		TextWrapped = true,
		LayoutOrder = props.LayoutOrder or 0,
		ZIndex = props.ZIndex or 1,
		Visible = if props.Visible == nil then true else props.Visible,
		Text = props.Text or "",
		Parent = props.Parent,
	})
	C.Corner(button, props.Radius or 8)
	if props.Round then
		(button:FindFirstChildOfClass("UICorner") :: UICorner).CornerRadius = UDim.new(1, 0)
	end
	C.Stroke(button, props.StrokeColor or Theme.Accent, 1.5, 0.25)
	if props.Key then
		C.Bind(button, props.Key, props.Args)
	end

	button.MouseButton1Down:Connect(function()
		TweenService:Create(button, TweenInfo.new(0.06), { BackgroundTransparency = 0.35 }):Play()
	end)
	local function release()
		TweenService:Create(button, TweenInfo.new(0.12), { BackgroundTransparency = props.Transparency or 0 }):Play()
	end
	button.MouseButton1Up:Connect(release)
	button.MouseLeave:Connect(release)

	if props.OnClick then
		button.Activated:Connect(function()
			if not props.Silent then
				Sound.Play("UiClick")
			end
			props.OnClick()
		end)
	end
	return button
end

-- Полоса прогресса: возвращает (рамка, заливка)
function C.Bar(props: { [string]: any }): (Frame, Frame)
	local bg = C.Make("Frame", {
		Name = props.Name or "Bar",
		Size = props.Size,
		Position = props.Position or UDim2.new(),
		AnchorPoint = props.AnchorPoint or Vector2.zero,
		BackgroundColor3 = props.BgColor or Theme.Bg,
		BackgroundTransparency = 0.2,
		LayoutOrder = props.LayoutOrder or 0,
		Parent = props.Parent,
	})
	C.Corner(bg, 4)
	local fill = C.Make("Frame", {
		Name = "Fill",
		Size = UDim2.fromScale(props.Value or 0, 1),
		BackgroundColor3 = props.Color or Theme.Accent,
		Parent = bg,
	})
	C.Corner(fill, 4)
	return bg, fill
end

-- Кнопка-заголовок окна с крестиком
function C.TitleBar(panel: Frame, titleKey: string, onClose: () -> ()): TextLabel
	local title = C.Text({
		Key = titleKey,
		Font = Theme.FontTitle,
		TextSize = 22,
		Color = Theme.Accent,
		Size = UDim2.new(1, -S(60), 0, S(34)),
		Position = UDim2.fromOffset(S(14), S(6)),
		Parent = panel,
	})
	C.Button({
		Name = "Close",
		Text = "X",
		Size = UDim2.fromOffset(S(40), S(40)),
		Position = UDim2.new(1, -S(6), 0, S(6)),
		AnchorPoint = Vector2.new(1, 0),
		Color = Theme.PanelLight,
		TextSize = 20,
		OnClick = onClose,
		Parent = panel,
	})
	return title
end

return C
