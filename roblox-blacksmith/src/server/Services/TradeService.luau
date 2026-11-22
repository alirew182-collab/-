--!strict
--[[
	ОБМЕН МЕЖДУ ИГРОКАМИ.
	1. Подойди к игроку → подсказка «Обмен» → ему приходит приглашение (20 с).
	2. Оба кладут оружие и золото. Каждый видит настоящие характеристики предметов другого.
	3. «Готов» у обоих → через ConfirmDelaySeconds можно нажать «Подтвердить обмен».
	4. Подтвердили оба → сервер ещё раз всё проверяет и меняет предметы одним блоком без yield.

	Защита от скама:
	• любое изменение предложения увеличивает Version и сбрасывает «Готов»/«Подтвердить» у обоих;
	• подтверждение принимается только для той Version, которую видел игрок;
	• задержка перед подтверждением не даёт подменить предмет в последнюю секунду.
	Защита от дюпа: передача внутри одного потока, Id предметов сохраняются, оба профиля
	сохраняются сразу после сделки, запись в журнал (MarketLog).
]]

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Char = require(script.Parent.Parent.Util.Char)
local Guards = require(script.Parent.Parent.Util.Guards)
local MarketLog = require(script.Parent.Parent.Util.MarketLog)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local DataService = require(script.Parent.DataService)
local InventoryService = require(script.Parent.InventoryService)
local NetService = require(script.Parent.NetService)
local SystemService = require(script.Parent.SystemService)

local TradeService = {}

local TR = Config.Trading.Trade

type Offer = { Weapons: { string }, Gold: number }
type Session = {
	Id: string,
	A: Player,
	B: Player,
	Offers: { [Player]: Offer },
	Ready: { [Player]: boolean },
	Confirmed: { [Player]: boolean },
	Version: number,
	Phase: string, -- open | confirm
	ConfirmAt: number,
}

local sessions: { [Player]: Session } = {}
local pending: { [Player]: { From: Player, ExpiresAt: number } } = {} -- ключ — кого пригласили

local function other(s: Session, player: Player): Player
	return if s.A == player then s.B else s.A
end

local function sideView(s: Session, player: Player): { [string]: any }
	local data = DataService:Get(player)
	local offer = s.Offers[player]
	local weapons = {}
	if data then
		for _, id in offer.Weapons do
			local w = data.Weapons[id]
			if w then
				table.insert(weapons, w)
			end
		end
	end
	return {
		Weapons = weapons,
		Gold = offer.Gold,
		Ready = s.Ready[player] == true,
		Confirmed = s.Confirmed[player] == true,
	}
end

local function push(s: Session)
	local now = os.clock()
	for _, player in { s.A, s.B } do
		local partner = other(s, player)
		NetService:Fire(player, "TradeState", {
			Id = s.Id,
			Phase = s.Phase,
			Version = s.Version,
			PartnerName = partner.DisplayName,
			ConfirmIn = math.max(0, s.ConfirmAt - now),
			You = sideView(s, player),
			Them = sideView(s, partner),
		})
	end
end

local function finish(s: Session, phase: string, toastKey: string?)
	for _, player in { s.A, s.B } do
		if sessions[player] == s then
			sessions[player] = nil
		end
		if player.Parent == Players then
			NetService:Fire(player, "TradeState", { Id = s.Id, Phase = phase })
			if toastKey then
				SystemService:Toast(player, toastKey, nil, if phase == "done" then "success" else "error")
			end
		end
	end
end

local function resetConfirmations(s: Session)
	s.Version += 1
	s.Ready = {}
	s.Confirmed = {}
	s.Phase = "open"
	s.ConfirmAt = 0
end

-- ===== Приглашение =====

local function request(from: Player, to: Player)
	if from == to or not DataService:IsLoaded(from) or not DataService:IsLoaded(to) then
		return
	end
	for _, player in { from, to } do
		local data = DataService:Get(player)
		if data.Level < TR.MinLevel then
			SystemService:Error(from, "ERR_LEVEL_LOW", { level = TR.MinLevel })
			return
		end
	end
	if sessions[from] or sessions[to] then
		SystemService:Error(from, "ERR_TRADE_BUSY")
		return
	end
	pending[to] = { From = from, ExpiresAt = os.clock() + TR.RequestTimeoutSeconds }
	NetService:Fire(to, "TradeInvite", {
		FromUserId = from.UserId,
		FromName = from.DisplayName,
		Timeout = TR.RequestTimeoutSeconds,
	})
	SystemService:Toast(from, "TOAST_TRADE_SENT", { name = to.DisplayName })
end

local function respond(to: Player, fromUserId: any, accept: any)
	if not Guards.Integer(fromUserId, 1, 1e15) or not Guards.Boolean(accept) then
		return
	end
	local invite = pending[to]
	if not invite or invite.From.UserId ~= fromUserId then
		return
	end
	pending[to] = nil
	local from = invite.From
	if os.clock() > invite.ExpiresAt or from.Parent ~= Players then
		SystemService:Error(to, "ERR_TRADE_EXPIRED")
		return
	end
	if not accept then
		SystemService:Toast(from, "TOAST_TRADE_DECLINED", { name = to.DisplayName }, "error")
		return
	end
	if sessions[from] or sessions[to] or not DataService:IsLoaded(from) or not DataService:IsLoaded(to) then
		SystemService:Error(to, "ERR_TRADE_BUSY")
		return
	end
	local s: Session = {
		Id = HttpService:GenerateGUID(false),
		A = from,
		B = to,
		Offers = { [from] = { Weapons = {}, Gold = 0 }, [to] = { Weapons = {}, Gold = 0 } },
		Ready = {},
		Confirmed = {},
		Version = 1,
		Phase = "open",
		ConfirmAt = 0,
	}
	sessions[from] = s
	sessions[to] = s
	push(s)
end

-- ===== Предложение и подтверждения =====

local function sessionFor(player: Player, sessionId: any): Session?
	local s = sessions[player]
	if s and s.Id == sessionId then
		return s
	end
	return nil
end

local function setOffer(player: Player, sessionId: any, ids: any, gold: any)
	local s = sessionFor(player, sessionId)
	local data = DataService:Get(player)
	if not s or not data then
		return { ok = false, err = "ERR_TRADE_EXPIRED" }
	end
	if not Guards.StringArray(ids, TR.MaxItems, 64) then
		return { ok = false, err = "ERR_TOO_MANY_ITEMS", args = { max = TR.MaxItems } }
	end
	if not Guards.Integer(gold, 0, TR.MaxGold) or gold > data.Gold then
		return { ok = false, err = "ERR_NOT_ENOUGH_GOLD" }
	end
	local unique: { string } = {}
	local seen: { [string]: boolean } = {}
	for _, id in ids do
		if not seen[id] then
			seen[id] = true
			if not InventoryService:CanTransfer(player, id) then
				return { ok = false, err = if InventoryService:IsListed(player, id) then "ERR_LISTED" else "ERR_UNKNOWN_ITEM" }
			end
			table.insert(unique, id)
		end
	end
	s.Offers[player] = { Weapons = unique, Gold = gold }
	resetConfirmations(s)
	push(s)
	return { ok = true }
end

local function execute(s: Session)
	local A, B = s.A, s.B
	local dataA, dataB = DataService:Get(A), DataService:Get(B)
	local offerA, offerB = s.Offers[A], s.Offers[B]
	local valid = dataA ~= nil and dataB ~= nil
	if valid then
		for _, side in { { A, offerA, B }, { B, offerB, A } } do
			local giver, offer, receiver = side[1], side[2], side[3]
			local receiverData = DataService:Get(receiver)
			for _, id in offer.Weapons do
				if not InventoryService:CanTransfer(giver, id) or receiverData.Weapons[id] ~= nil then
					valid = false
				end
			end
			if DataService:Get(giver).Gold < offer.Gold then
				valid = false
			end
		end
		local countA = InventoryService:Count(A) - #offerA.Weapons + #offerB.Weapons
		local countB = InventoryService:Count(B) - #offerB.Weapons + #offerA.Weapons
		if countA > InventoryService:Capacity(A) or countB > InventoryService:Capacity(B) then
			valid = false
		end
	end
	if not valid then
		finish(s, "cancelled", "ERR_TRADE_FAILED")
		return
	end

	-- ===== перенос одним блоком, без yield =====
	local movedA, movedB = {}, {}
	for _, id in offerA.Weapons do
		local w = InventoryService:TakeForTransfer(A, id)
		if w then
			InventoryService:GiveTransferred(B, w)
			table.insert(movedA, MarketLog.Weapon(w))
		end
	end
	for _, id in offerB.Weapons do
		local w = InventoryService:TakeForTransfer(B, id)
		if w then
			InventoryService:GiveTransferred(A, w)
			table.insert(movedB, MarketLog.Weapon(w))
		end
	end
	dataA.Gold += offerB.Gold - offerA.Gold
	dataB.Gold += offerA.Gold - offerB.Gold
	dataA.Stats.Trades = (dataA.Stats.Trades or 0) + 1
	dataB.Stats.Trades = (dataB.Stats.Trades or 0) + 1
	-- ============================================

	for _, player in { A, B } do
		DataService:Replicate(player, "Gold", "Weapons", "EquippedWeapon", "Shop", "Stats")
	end
	MarketLog.Write("trade", {
		TradeId = s.Id,
		A = A.UserId,
		B = B.UserId,
		AGave = movedA,
		BGave = movedB,
		AGold = offerA.Gold,
		BGold = offerB.Gold,
	})
	finish(s, "done", "TOAST_TRADE_DONE")
	task.spawn(DataService.SaveNow, DataService, A)
	task.spawn(DataService.SaveNow, DataService, B)
end

local function ready(player: Player, sessionId: any, version: any, flag: any)
	local s = sessionFor(player, sessionId)
	if not s or not Guards.Boolean(flag) then
		return
	end
	if version ~= s.Version then
		push(s) -- клиент видел старое предложение — показать актуальное
		return
	end
	s.Ready[player] = flag
	s.Confirmed = {}
	if s.Ready[s.A] and s.Ready[s.B] then
		s.Phase = "confirm"
		s.ConfirmAt = os.clock() + TR.ConfirmDelaySeconds
	else
		s.Phase = "open"
		s.ConfirmAt = 0
	end
	push(s)
end

local function confirm(player: Player, sessionId: any, version: any)
	local s = sessionFor(player, sessionId)
	if not s then
		return
	end
	if s.Phase ~= "confirm" or version ~= s.Version or os.clock() < s.ConfirmAt then
		SystemService:Error(player, "ERR_TRADE_CHANGED")
		push(s)
		return
	end
	s.Confirmed[player] = true
	if s.Confirmed[s.A] and s.Confirmed[s.B] then
		execute(s)
	else
		push(s)
	end
end

local function cancel(player: Player, sessionId: any)
	local s = sessionFor(player, sessionId)
	if s then
		finish(s, "cancelled", "TOAST_TRADE_CANCELLED")
	end
end

-- ===== Подсказка «Обмен» над каждым игроком =====

local function addPrompt(player: Player, character: Model)
	local root = character:WaitForChild("HumanoidRootPart", 10)
	if not root or not root:IsA("BasePart") then
		return
	end
	local prompt = Prompts.Create(root, "PROMPT_TRADE_PLAYER", { Distance = TR.PromptDistance, HoldDuration = 0.25, KeyCode = Enum.KeyCode.T })
	prompt.Name = "TradePrompt"
	prompt.ObjectText = player.DisplayName
	prompt.Triggered:Connect(function(requester: Player)
		if requester ~= player and Char.IsAlive(requester) then
			request(requester, player)
		end
	end)
end

function TradeService:Start()
	NetService:OnEvent("TradeRespond", respond)
	NetService:OnInvoke("TradeSetOffer", setOffer)
	NetService:OnEvent("TradeReady", ready)
	NetService:OnEvent("TradeConfirm", confirm)
	NetService:OnEvent("TradeCancel", cancel)

	local function hook(player: Player)
		player.CharacterAdded:Connect(function(character)
			addPrompt(player, character)
		end)
		if player.Character then
			task.spawn(addPrompt, player, player.Character)
		end
	end
	Players.PlayerAdded:Connect(hook)
	for _, player in Players:GetPlayers() do
		hook(player)
	end
	Players.PlayerRemoving:Connect(function(player)
		pending[player] = nil
		local s = sessions[player]
		if s then
			finish(s, "cancelled", "TOAST_TRADE_CANCELLED")
		end
	end)
end

return TradeService
