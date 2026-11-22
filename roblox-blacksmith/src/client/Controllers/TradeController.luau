--!strict
--[[
	ОБМЕН НА КЛИЕНТЕ.
	• Приглашение: карточка сверху «{имя} предлагает обмен» с кнопками «Принять»/«Отказать».
	• Окно обмена: слева твоё оружие (нажатие добавляет/убирает из предложения) и золото,
	  справа — что отдаёт другой игрок, с настоящими характеристиками.
	• Кнопка внизу: «Готов» → после готовности обоих отсчёт → «Подтвердить обмен».
	Всё решает сервер; клиент только показывает состояние и шлёт намерения.
]]

local GuiService = game:GetService("GuiService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Format = require(ReplicatedStorage.Shared.Format)
local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local C = require(script.Parent.Parent.UI.Components)
local Theme = require(script.Parent.Parent.UI.Theme)
local WeaponCard = require(script.Parent.Parent.UI.WeaponCard)
local DataController = require(script.Parent.DataController)
local InputController = require(script.Parent.InputController)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local player = Players.LocalPlayer

local TradeController = {}

-- приглашение
local invite: Frame
local inviteText: TextLabel
local inviteFrom = 0
local inviteToken = 0

-- окно
local panel: Frame
local title: TextLabel
local theirHeader: TextLabel
local yourGrid: ScrollingFrame
local theirGrid: ScrollingFrame
local goldBox: TextBox
local theirGold: TextLabel
local theirStatus: TextLabel
local actionButton: TextButton

local state: { [string]: any }? = nil
local confirmDeadline = 0 -- os.clock(), когда станет можно подтвердить
local closingByServer = false

-- ===== Приглашение =====

local function hideInvite()
	invite.Visible = false
	inviteFrom = 0
end

local function showInvite(payload: { [string]: any })
	inviteFrom = payload.FromUserId
	C.Bind(inviteText, "UI_TRADE_INVITE", { name = payload.FromName })
	invite.Visible = true
	if InputController:IsGamepad() then
		GuiService.SelectedObject = invite:FindFirstChild("Accept") :: GuiObject?
	end
	inviteToken += 1
	local token = inviteToken
	task.delay(payload.Timeout or 20, function()
		if token == inviteToken then
			hideInvite()
		end
	end)
end

local function answer(accept: boolean)
	if inviteFrom ~= 0 then
		Net.Event("TradeRespond"):FireServer(inviteFrom, accept)
	end
	hideInvite()
end

-- ===== Окно обмена =====

local function offeredIds(): { string }
	local ids = {}
	if state then
		for _, w in state.You.Weapons do
			table.insert(ids, w.Id)
		end
	end
	return ids
end

local function sendOffer(ids: { string }, gold: number)
	local s = state
	if not s then
		return
	end
	UIController:Handle(UIController:Invoke("TradeSetOffer", s.Id, ids, gold))
end

local function toggleWeapon(id: string)
	local s = state
	if not s then
		return
	end
	local ids = offeredIds()
	local index = table.find(ids, id)
	if index then
		table.remove(ids, index)
	else
		table.insert(ids, id)
	end
	sendOffer(ids, s.You.Gold)
end

local function refreshButton()
	local s = state
	if not s then
		return
	end
	local left = confirmDeadline - os.clock()
	local function set(key: string, args: { [string]: any }?, active: boolean)
		C.Bind(actionButton, key, args)
		actionButton.BackgroundColor3 = if active then Theme.Accent else Theme.PanelLight
		actionButton.TextColor3 = if active then Theme.Bg else Theme.TextDim
	end
	if s.Phase == "confirm" then
		if s.You.Confirmed then
			set("UI_TRADE_WAITING", { name = s.PartnerName }, false)
		elseif left > 0 then
			set("UI_TRADE_WAIT", { sec = math.ceil(left) }, false)
		else
			set("UI_TRADE_CONFIRM", nil, true)
		end
	elseif s.You.Ready then
		set("UI_TRADE_WAITING", { name = s.PartnerName }, false)
	else
		set("UI_TRADE_READY", nil, true)
	end
end

local function render()
	local s = state
	local data = DataController:Get()
	if not s or not data then
		return
	end
	C.Bind(title, "UI_TRADE_TITLE", { name = s.PartnerName })
	C.Bind(theirHeader, "UI_TRADE_THEIRS", { name = s.PartnerName })

	-- моё оружие: всё, что можно отдать (не надето, не в лавке); выбранное — в предложении
	local offered: { [string]: boolean } = {}
	for _, w in s.You.Weapons do
		offered[w.Id] = true
	end
	WeaponCard.Clear(yourGrid)
	local order = 0
	for _, weapon in WeaponCard.Sorted(data.Weapons) do
		if weapon.Id ~= data.EquippedWeapon and not data.Shop.Listings[weapon.Id] then
			order += 1
			WeaponCard.Create({
				Weapon = weapon,
				Selected = offered[weapon.Id] == true,
				LayoutOrder = order,
				Parent = yourGrid,
				OnClick = function()
					toggleWeapon(weapon.Id)
				end,
			})
		end
	end
	if not goldBox:IsFocused() then
		goldBox.Text = if s.You.Gold > 0 then tostring(s.You.Gold) else ""
	end

	-- их предложение
	WeaponCard.Clear(theirGrid)
	for i, weapon in s.Them.Weapons do
		WeaponCard.Create({ Weapon = weapon, LayoutOrder = i, Parent = theirGrid })
	end
	C.Bind(theirGold, "UI_TRADE_GOLD_LINE", { gold = Format.Short(s.Them.Gold) })
	local themReady = s.Them.Ready or s.Them.Confirmed
	C.Bind(theirStatus, if themReady then "UI_TRADE_READY" else "UI_TRADE_UNREADY")
	theirStatus.TextColor3 = if themReady then Theme.Success else Theme.Muted
	refreshButton()
end

local function onAction()
	local s = state
	if not s then
		return
	end
	if s.Phase == "confirm" then
		if not s.You.Confirmed and os.clock() >= confirmDeadline then
			Net.Event("TradeConfirm"):FireServer(s.Id, s.Version)
		end
	else
		Net.Event("TradeReady"):FireServer(s.Id, s.Version, not s.You.Ready)
	end
end

local function applyGold()
	local s = state
	if not s then
		return
	end
	local gold = tonumber(goldBox.Text) or 0
	gold = math.max(0, math.floor(gold))
	if gold ~= s.You.Gold then
		sendOffer(offeredIds(), gold)
	end
end

local function build()
	-- приглашение
	invite = C.Panel({
		Name = "TradeInvite",
		Size = UDim2.fromOffset(S(320), S(96)),
		Position = UDim2.new(0.5, 0, 0, S(160)),
		AnchorPoint = Vector2.new(0.5, 0),
		Visible = false,
		Adaptive = true,
		Parent = UIController.Layers.Overlay,
	})
	inviteText = C.Text({
		Font = Theme.FontBold,
		TextSize = 15,
		AlignX = Enum.TextXAlignment.Center,
		Size = UDim2.new(1, -S(24), 0, S(24)),
		Position = UDim2.fromOffset(S(12), S(12)),
		Parent = invite,
	})
	C.Button({
		Name = "Accept",
		Key = "UI_ACCEPT",
		TextSize = 14,
		Size = UDim2.new(0.5, -S(16), 0, S(40)),
		Position = UDim2.new(0, S(12), 1, -S(12)),
		AnchorPoint = Vector2.new(0, 1),
		Color = Theme.Accent,
		TextColor = Theme.Bg,
		OnClick = function()
			answer(true)
		end,
		Parent = invite,
	})
	C.Button({
		Name = "Decline",
		Key = "UI_DECLINE",
		TextSize = 14,
		Size = UDim2.new(0.5, -S(16), 0, S(40)),
		Position = UDim2.new(1, -S(12), 1, -S(12)),
		AnchorPoint = Vector2.new(1, 1),
		OnClick = function()
			answer(false)
		end,
		Parent = invite,
	})

	-- окно обмена
	panel = C.Panel({ Name = "TradePanel", Size = UDim2.fromOffset(S(540), S(332)) })
	title = C.TitleBar(panel, "UI_TRADE_TITLE", function()
		UIController:ClosePanel()
	end)
	local top = S(48)
	local bottom = S(92)
	C.Text({
		Key = "UI_TRADE_YOURS",
		Font = Theme.FontBold,
		TextSize = 13,
		Color = Theme.TextDim,
		Size = UDim2.new(0.5, -S(24), 0, S(16)),
		Position = UDim2.fromOffset(S(16), top),
		Parent = panel,
	})
	yourGrid = WeaponCard.Grid(panel, UDim2.new(0.5, -S(24), 1, -(top + S(20) + bottom)), UDim2.fromOffset(S(16), top + S(20)))
	goldBox = C.Make("TextBox", {
		Size = UDim2.new(0.5, -S(24), 0, S(32)),
		Position = UDim2.new(0, S(16), 1, -(bottom - S(4))),
		BackgroundColor3 = Theme.Bg,
		Font = Theme.FontBold,
		TextSize = S(14),
		TextColor3 = Theme.Gold,
		PlaceholderColor3 = Theme.Muted,
		ClearTextOnFocus = false,
		Text = "",
		Parent = panel,
	})
	C.Corner(goldBox, 8)
	C.Stroke(goldBox)
	goldBox.FocusLost:Connect(applyGold)

	theirHeader = C.Text({
		Font = Theme.FontBold,
		TextSize = 13,
		Color = Theme.TextDim,
		Size = UDim2.new(0.5, -S(24), 0, S(16)),
		Position = UDim2.new(0.5, S(8), 0, top),
		Parent = panel,
	})
	theirGrid = WeaponCard.Grid(panel, UDim2.new(0.5, -S(24), 1, -(top + S(20) + bottom)), UDim2.new(0.5, S(8), 0, top + S(20)))
	theirGold = C.Text({
		Font = Theme.FontBold,
		TextSize = 14,
		Color = Theme.Gold,
		Size = UDim2.new(0.25, 0, 0, S(32)),
		Position = UDim2.new(0.5, S(8), 1, -(bottom - S(4))),
		Parent = panel,
	})
	theirStatus = C.Text({
		Font = Theme.FontBold,
		TextSize = 13,
		AlignX = Enum.TextXAlignment.Right,
		Size = UDim2.new(0.25, -S(16), 0, S(32)),
		Position = UDim2.new(0.75, 0, 1, -(bottom - S(4))),
		Parent = panel,
	})
	C.Text({
		Key = "UI_TRADE_HINT",
		TextSize = 11,
		Color = Theme.Muted,
		Wrapped = true,
		Size = UDim2.new(0.5, -S(24), 0, S(44)),
		Position = UDim2.new(0, S(16), 1, -S(56)),
		Parent = panel,
	})
	actionButton = C.Button({
		Key = "UI_TRADE_READY",
		Font = Theme.FontTitle,
		TextSize = 16,
		Size = UDim2.new(0.5, -S(24), 0, S(44)),
		Position = UDim2.new(1, -S(16), 1, -S(12)),
		AnchorPoint = Vector2.new(1, 1),
		Color = Theme.Accent,
		TextColor = Theme.Bg,
		OnClick = onAction,
		Parent = panel,
	})
end

-- своя подсказка «Обмен» над собственным персонажем не нужна
local function hideOwnPrompt(character: Model)
	local root = character:WaitForChild("HumanoidRootPart", 10)
	local prompt = root and root:WaitForChild("TradePrompt", 10)
	if prompt and prompt:IsA("ProximityPrompt") then
		prompt.Enabled = false
	end
end

function TradeController:Start()
	build()
	goldBox.PlaceholderText = Localization.Get("UI_TRADE_GOLD_LINE", { gold = 0 })
	Localization.Changed:Connect(function()
		goldBox.PlaceholderText = Localization.Get("UI_TRADE_GOLD_LINE", { gold = 0 })
		render()
	end)

	UIController:RegisterPanel("Trade", {
		Frame = panel,
		OnOpen = function()
			render()
		end,
		OnClose = function()
			-- окно закрыл сам игрок → отменить обмен на сервере
			local s = state
			if s and not closingByServer then
				Net.Event("TradeCancel"):FireServer(s.Id)
			end
			state = nil
		end,
	})

	Net.Event("TradeInvite").OnClientEvent:Connect(showInvite)
	Net.Event("TradeState").OnClientEvent:Connect(function(payload: { [string]: any })
		if payload.Phase == "open" or payload.Phase == "confirm" then
			state = payload
			confirmDeadline = os.clock() + (payload.ConfirmIn or 0)
			hideInvite()
			if UIController:GetOpenPanel() ~= "Trade" then
				UIController:OpenPanel("Trade")
			else
				render()
			end
		else
			if UIController:GetOpenPanel() == "Trade" then
				closingByServer = true
				UIController:ClosePanel()
				closingByServer = false
			end
			state = nil
		end
	end)

	DataController.Changed:Connect(function(key: string)
		if state and (key == "Weapons" or key == "Shop" or key == "EquippedWeapon") then
			render()
		end
	end)

	-- обратный отсчёт до «Подтвердить»
	local acc = 0
	RunService.Heartbeat:Connect(function(dt)
		acc += dt
		if acc >= 0.2 and state and state.Phase == "confirm" then
			acc = 0
			refreshButton()
		end
	end)

	player.CharacterAdded:Connect(hideOwnPrompt)
	if player.Character then
		task.spawn(hideOwnPrompt, player.Character)
	end
end

return TradeController
