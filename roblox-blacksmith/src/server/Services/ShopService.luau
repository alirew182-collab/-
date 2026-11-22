--!strict
--[[
	ЛАВКИ ИГРОКОВ.
	• Каждому игроку на сервере — свой прилавок (тег ShopStall) в Торговом ряду.
	• Владелец выставляет оружие с ценой (откуда угодно: Инвентарь → «Моя лавка»).
	• Покупатель подходит к прилавку → «Лавка» → покупает. Сервер проверяет дистанцию,
	  что цена не изменилась, место в инвентаре и золото, затем переносит оружие (тот же Id)
	  и золото одним блоком без yield, сохраняет оба профиля и пишет запись в журнал.
	• Пока владелец не в игре, лавка продаёт NPC-покупателям (ограниченно, Config.Trading.Shop.Offline).
	Выставленное оружие нельзя надеть, продать торговцу или отдать в обмен.
]]

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local Guards = require(script.Parent.Parent.Util.Guards)
local MarketLog = require(script.Parent.Parent.Util.MarketLog)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local Proximity = require(script.Parent.Parent.Util.Proximity)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local InventoryService = require(script.Parent.InventoryService)
local NetService = require(script.Parent.NetService)
local SystemService = require(script.Parent.SystemService)

local ShopService = {}

local SH = Config.Trading.Shop
local rng = Random.new()

local stallOwner: { [Model]: Player } = {}
local playerStall: { [Player]: Model } = {}

-- ===== Прилавки =====

local function stalls(): { Model }
	local list = {}
	for _, inst in CollectionService:GetTagged(Config.Tags.ShopStall) do
		if inst:IsA("Model") and inst:IsDescendantOf(workspace) then
			table.insert(list, inst)
		end
	end
	table.sort(list, function(a, b)
		return (a:GetAttribute("Slot") or 0) < (b:GetAttribute("Slot") or 0)
	end)
	return list
end

local function ownerLabel(stall: Model): TextLabel?
	return stall:FindFirstChild("Owner", true) :: TextLabel?
end

local function listingsOf(data: any): { { Weapon: any, Price: number } }
	local list = {}
	for id, price in data.Shop.Listings do
		local weapon = data.Weapons[id]
		if weapon then
			table.insert(list, { Weapon = weapon, Price = price })
		end
	end
	table.sort(list, function(a, b)
		return a.Price > b.Price
	end)
	return list
end

-- Витрина: 3 самых дорогих предмета на подставках
local function refreshDisplay(stall: Model)
	local old = stall:FindFirstChild("Display")
	if old then
		old:Destroy()
	end
	local owner = stallOwner[stall]
	local label = ownerLabel(stall)
	if label then
		if owner then
			label:SetAttribute("LocKey", nil)
			label.Text = owner.DisplayName
		else
			label.Text = "UI_STALL_FREE"
			label:SetAttribute("LocKey", "UI_STALL_FREE")
		end
	end
	local data = owner and DataService:Get(owner)
	if not data then
		return
	end
	local folder = Instance.new("Folder")
	folder.Name = "Display"
	for i, entry in listingsOf(data) do
		if i > 3 then
			break
		end
		local pedestal = stall:FindFirstChild("Pedestal" .. i, true) :: BasePart?
		local visual, primary = InventoryService:BuildWeaponVisual(entry.Weapon)
		if pedestal and visual and primary then
			primary.Anchored = true
			-- клинок вертикально над подставкой
			primary.CFrame = pedestal.CFrame
				* CFrame.new(0, pedestal.Size.Y / 2 + primary.Size.Z / 2 + 0.2, 0)
				* CFrame.Angles(math.rad(90), 0, 0)
			visual.Parent = folder
		elseif visual then
			visual:Destroy()
		end
	end
	folder.Parent = stall
end

local function assignStall(player: Player)
	if playerStall[player] then
		return
	end
	for _, stall in stalls() do
		if not stallOwner[stall] then
			stallOwner[stall] = player
			playerStall[player] = stall
			player:SetAttribute("HasStall", true) -- клиент показывает подсказку, если прилавка нет
			refreshDisplay(stall)
			return
		end
	end
end

local function releaseStall(player: Player)
	local stall = playerStall[player]
	if stall then
		playerStall[player] = nil
		stallOwner[stall] = nil
		player:SetAttribute("HasStall", nil)
		refreshDisplay(stall)
	end
end

local function refreshPlayerStall(player: Player)
	local stall = playerStall[player]
	if stall then
		refreshDisplay(stall)
	end
end

local function browsePayload(owner: Player): { [string]: any }
	local data = DataService:Get(owner)
	local listings = {}
	if data then
		for _, entry in listingsOf(data) do
			table.insert(listings, { Weapon = entry.Weapon, Price = entry.Price })
		end
	end
	return {
		Panel = "Shop",
		Mode = "browse",
		OwnerUserId = owner.UserId,
		OwnerName = owner.DisplayName,
		Listings = listings,
	}
end

local function onStallTriggered(player: Player, stall: Model)
	if not DataService:IsLoaded(player) then
		return
	end
	local owner = stallOwner[stall]
	if owner == player then
		NetService:Fire(player, "OpenPanel", { Panel = "Shop", Mode = "manage" })
	elseif owner then
		NetService:Fire(player, "OpenPanel", browsePayload(owner))
	else
		SystemService:Toast(player, "UI_STALL_FREE")
	end
end

local function setupStall(stall: Instance)
	if not stall:IsA("Model") then
		return
	end
	local anchor = stall.PrimaryPart
	if not anchor or anchor:FindFirstChild("GamePrompt") then
		return
	end
	local prompt = Prompts.Create(anchor, "PROMPT_SHOP", { Distance = 12 })
	prompt.Triggered:Connect(function(player: Player)
		onStallTriggered(player, stall)
	end)
end

-- ===== Выставление =====

local function listWeapon(player: Player, weaponId: any, price: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(weaponId, 64) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	if not Guards.Integer(price, SH.MinPrice, SH.MaxPrice) then
		return { ok = false, err = "ERR_PRICE", args = { min = SH.MinPrice, max = SH.MaxPrice } }
	end
	if not data.Weapons[weaponId] then
		return { ok = false, err = "ERR_UNKNOWN_ITEM" }
	end
	if data.EquippedWeapon == weaponId then
		return { ok = false, err = "ERR_EQUIPPED" }
	end
	local count = 0
	for _ in data.Shop.Listings do
		count += 1
	end
	if not data.Shop.Listings[weaponId] and count >= SH.MaxListings then
		return { ok = false, err = "ERR_SHOP_FULL", args = { max = SH.MaxListings } }
	end
	data.Shop.Listings[weaponId] = price
	DataService:Replicate(player, "Shop")
	refreshPlayerStall(player)
	return { ok = true }
end

local function unlistWeapon(player: Player, weaponId: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(weaponId, 64) or not data.Shop.Listings[weaponId] then
		return { ok = false, err = "ERR_UNKNOWN_ITEM" }
	end
	data.Shop.Listings[weaponId] = nil
	DataService:Replicate(player, "Shop")
	refreshPlayerStall(player)
	return { ok = true }
end

-- ===== Покупка у игрока на этом сервере =====

local function buy(player: Player, ownerUserId: any, weaponId: any, expectedPrice: any)
	local buyerData = DataService:Get(player)
	if not buyerData then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.Integer(ownerUserId, 1, 1e15) or not Guards.String(weaponId, 64) or not Guards.Integer(expectedPrice, 0, 1e9) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local owner = Players:GetPlayerByUserId(ownerUserId)
	local ownerData = owner and DataService:Get(owner)
	if not owner or not ownerData then
		return { ok = false, err = "ERR_OWNER_OFFLINE" }
	end
	if owner == player then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local stall = playerStall[owner]
	local anchor = stall and stall.PrimaryPart
	if not anchor or Proximity.DistanceTo(player, anchor.Position) > SH.InteractDistance then
		return { ok = false, err = "ERR_TOO_FAR" }
	end
	-- цена должна совпадать с той, что видел покупатель: продавец не подменит её в последний момент
	local price = ownerData.Shop.Listings[weaponId]
	local weapon = ownerData.Weapons[weaponId]
	if not price or not weapon or price ~= expectedPrice then
		return { ok = false, err = "ERR_SHOP_GONE" }
	end
	if InventoryService:IsFull(player) then
		return { ok = false, err = "ERR_INVENTORY_FULL" }
	end
	if buyerData.Gold < price then
		return { ok = false, err = "ERR_NOT_ENOUGH_GOLD" }
	end
	if buyerData.Weapons[weaponId] then
		return { ok = false, err = "ERR_SHOP_GONE" } -- такой Id уже есть: защита от дюпа
	end

	-- ===== перенос одним блоком, без yield =====
	local received = EconomyService:TransferGold(player, owner, price, SH.TaxRate)
	if not received then
		return { ok = false, err = "ERR_NOT_ENOUGH_GOLD" }
	end
	local taken = InventoryService:TakeForTransfer(owner, weaponId) :: any
	InventoryService:GiveTransferred(player, taken)
	ownerData.Shop.Earned += received
	ownerData.Stats.ShopSales = (ownerData.Stats.ShopSales or 0) + 1
	-- ============================================

	DataService:Replicate(player, "Gold", "Weapons")
	DataService:Replicate(owner, "Gold", "Weapons", "EquippedWeapon", "Shop", "Stats")
	refreshPlayerStall(owner)

	MarketLog.Write("shop", {
		Seller = owner.UserId,
		Buyer = player.UserId,
		Price = price,
		Received = received,
		Weapon = MarketLog.Weapon(taken),
	})
	SystemService:Notify(owner, "Shop", "SYS_SHOP_SOLD_TITLE", "SYS_SHOP_SOLD_BODY", {
		weapon = `@WEAPON:{taken.Type}:{taken.Ore}`,
		buyer = player.DisplayName,
		price = price,
		gold = received,
	})
	task.spawn(DataService.SaveNow, DataService, player)
	task.spawn(DataService.SaveNow, DataService, owner)
	return { ok = true, weapon = taken, price = price }
end

local function fetch(_player: Player, ownerUserId: any)
	if not Guards.Integer(ownerUserId, 1, 1e15) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local owner = Players:GetPlayerByUserId(ownerUserId)
	if not owner or not DataService:IsLoaded(owner) then
		return { ok = false, err = "ERR_OWNER_OFFLINE" }
	end
	local payload = browsePayload(owner)
	payload.ok = true
	return payload
end

-- ===== Офлайн-продажи =====

local function processOffline(player: Player, data: any)
	local O = SH.Offline
	local last = data.Shop.LastOnlineAt
	local away = os.time() - last
	if last <= 0 or away < O.MinAwaySeconds then
		return
	end
	local hours = math.min(away / 3600, O.MaxHours)
	local sales = math.min(O.MaxSales, math.floor(hours * O.SalesPerHour))
	local sold, gold = 0, 0
	for _ = 1, sales do
		local ids = {}
		for id in data.Shop.Listings do
			if data.Weapons[id] then
				table.insert(ids, id)
			end
		end
		if #ids == 0 then
			break
		end
		local id = ids[rng:NextInteger(1, #ids)]
		local weapon = data.Weapons[id]
		local cap = math.floor(WeaponMath.SellPrice(weapon) * O.MaxPriceMult)
		local price = math.min(data.Shop.Listings[id], cap)
		local received = price - math.floor(price * SH.TaxRate + 0.5)
		InventoryService:RemoveWeapon(player, id, true)
		data.Gold += received
		data.Shop.Earned += received
		data.Stats.ShopSales = (data.Stats.ShopSales or 0) + 1
		sold += 1
		gold += received
		MarketLog.Write("offline", { Seller = player.UserId, Price = price, Received = received, Weapon = MarketLog.Weapon(weapon) })
	end
	if sold > 0 then
		DataService:Replicate(player, "Gold", "Weapons", "EquippedWeapon", "Shop", "Stats")
		task.delay(4, function()
			if DataService:IsLoaded(player) then
				SystemService:Notify(player, "Shop", "SYS_SHOP_OFFLINE_TITLE", "SYS_SHOP_OFFLINE_BODY", { count = sold, gold = gold })
			end
		end)
	end
end

function ShopService:Start()
	NetService:OnInvoke("ShopList", listWeapon)
	NetService:OnInvoke("ShopUnlist", unlistWeapon)
	NetService:OnInvoke("ShopBuy", buy)
	NetService:OnInvoke("ShopFetch", fetch)

	for _, stall in CollectionService:GetTagged(Config.Tags.ShopStall) do
		setupStall(stall)
	end
	CollectionService:GetInstanceAddedSignal(Config.Tags.ShopStall):Connect(setupStall)

	DataService.Loaded:Connect(function(player: Player, data: any)
		processOffline(player, data)
		data.Shop.LastOnlineAt = os.time()
		assignStall(player)
	end)
	DataService.Releasing:Connect(function(player: Player, data: any)
		data.Shop.LastOnlineAt = os.time()
		releaseStall(player)
	end)
	Players.PlayerRemoving:Connect(releaseStall)

	-- отметка «был в игре» на случай падения сервера
	task.spawn(function()
		while true do
			task.wait(60)
			for _, player in Players:GetPlayers() do
				local data = DataService:Get(player)
				if data then
					data.Shop.LastOnlineAt = os.time()
				end
			end
		end
	end)
end

function ShopService:HasStall(player: Player): boolean
	return playerStall[player] ~= nil
end

return ShopService
