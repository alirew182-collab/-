--!strict
--[[
	ТОРГОВЕЦ (NPC). Part/Model с тегом "Merchant".
	Продажа оружия и руды, покупка кирок. Каждое действие проверяет, что игрок рядом с торговцем.
]]

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local Guards = require(script.Parent.Parent.Util.Guards)
local Proximity = require(script.Parent.Parent.Util.Proximity)
local Prompts = require(script.Parent.Parent.Util.Prompts)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local InventoryService = require(script.Parent.InventoryService)
local NetService = require(script.Parent.NetService)
local TutorialService = require(script.Parent.TutorialService)

local MerchantService = {}

local function nearMerchant(player: Player): boolean
	return Proximity.NearTagged(player, Config.Tags.Merchant, Config.Economy.InteractMaxDistance) ~= nil
end

local function sellWeapons(player: Player, ids: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.StringArray(ids, Config.Economy.MaxSellBatch, 64) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	if not nearMerchant(player) then
		return { ok = false, err = "ERR_TOO_FAR" }
	end

	local total, count = 0, 0
	local seen: { [string]: boolean } = {}
	for _, id in ids do
		if not seen[id] then
			seen[id] = true
			if id == data.EquippedWeapon then
				continue -- надетое не продаём
			end
			-- сначала удаляем, потом платим: никакого дюпа даже при ошибке ниже
			local weapon = InventoryService:RemoveWeapon(player, id, true)
			if weapon then
				total += WeaponMath.SellPrice(weapon)
				count += 1
			end
		end
	end
	if count == 0 then
		return { ok = false, err = if seen[data.EquippedWeapon] then "ERR_EQUIPPED" else "ERR_UNKNOWN_ITEM" }
	end
	DataService:Replicate(player, "Weapons", "EquippedWeapon")
	EconomyService:AddGold(player, total)
	return { ok = true, gold = total, count = count }
end

local function sellOres(player: Player, oreId: any, count: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(oreId, 32) or not Config.Ores[oreId] or not Guards.Integer(count, 1, 1e6) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	if not nearMerchant(player) then
		return { ok = false, err = "ERR_TOO_FAR" }
	end
	if not EconomyService:TrySpendOre(player, oreId, count) then
		return { ok = false, err = "ERR_NOT_ENOUGH_ORE" }
	end
	local gold = Config.Ores[oreId].SellPrice * count
	EconomyService:AddGold(player, gold)
	return { ok = true, gold = gold, count = count }
end

local function buyPickaxe(player: Player, pickaxeId: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(pickaxeId, 32) then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	local def = Config.Pickaxes[pickaxeId]
	if not def then
		return { ok = false, err = "ERR_BAD_REQUEST" }
	end
	if not nearMerchant(player) then
		return { ok = false, err = "ERR_TOO_FAR" }
	end
	if data.OwnedPickaxes[pickaxeId] then
		return { ok = false, err = "ERR_ALREADY_OWNED" }
	end
	if data.Level < def.RequiredLevel then
		return { ok = false, err = "ERR_LEVEL_LOW", args = { level = def.RequiredLevel } }
	end
	if not EconomyService:TrySpendGold(player, def.Price) then
		return { ok = false, err = "ERR_NOT_ENOUGH_GOLD" }
	end
	data.OwnedPickaxes[pickaxeId] = true
	data.Pickaxe = pickaxeId
	DataService:Replicate(player, "OwnedPickaxes", "Pickaxe")
	return { ok = true }
end

local function setupMerchant(instance: Instance)
	local anchor = Proximity.AnchorPart(instance)
	if not anchor or anchor:FindFirstChild("GamePrompt") then
		return
	end
	local prompt = Prompts.Create(anchor, "PROMPT_TRADE", { Distance = 12 })
	prompt.Triggered:Connect(function(player: Player)
		if DataService:IsLoaded(player) then
			NetService:Fire(player, "OpenPanel", { Panel = "Merchant" })
			TutorialService:Record(player, "MerchantOpened")
		end
	end)
end

function MerchantService:Start()
	NetService:OnInvoke("SellWeapons", sellWeapons)
	NetService:OnInvoke("SellOres", sellOres)
	NetService:OnInvoke("BuyPickaxe", buyPickaxe)

	for _, instance in CollectionService:GetTagged(Config.Tags.Merchant) do
		setupMerchant(instance)
	end
	CollectionService:GetInstanceAddedSignal(Config.Tags.Merchant):Connect(setupMerchant)
end

return MerchantService
