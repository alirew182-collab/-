--!strict
-- Золото и руда. Единственное место, где они меняются.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local DataService = require(script.Parent.DataService)

local EconomyService = {}

function EconomyService:AddGold(player: Player, amount: number)
	local data = DataService:Get(player)
	if not data or amount <= 0 then
		return
	end
	amount = math.floor(amount)
	data.Gold += amount
	data.Stats.GoldEarned = (data.Stats.GoldEarned or 0) + amount
	DataService:Replicate(player, "Gold")
end

function EconomyService:TrySpendGold(player: Player, amount: number): boolean
	local data = DataService:Get(player)
	if not data or amount < 0 or data.Gold < amount then
		return false
	end
	data.Gold -= math.floor(amount)
	DataService:Replicate(player, "Gold")
	return true
end

-- Перевод золота между игроками (обмен, покупка в лавке). Не считается «заработком».
-- tax — доля, которая сгорает (комиссия рынка). Возвращает сумму, дошедшую получателю.
function EconomyService:TransferGold(from: Player, to: Player, amount: number, tax: number?): number?
	local a = DataService:Get(from)
	local b = DataService:Get(to)
	amount = math.floor(amount)
	if not a or not b or amount < 0 or a.Gold < amount then
		return nil
	end
	local received = amount - math.floor(amount * (tax or 0) + 0.5)
	a.Gold -= amount
	b.Gold += received
	return received
end

function EconomyService:GetOre(player: Player, oreId: string): number
	local data = DataService:Get(player)
	if not data then
		return 0
	end
	return data.Ores[oreId] or 0
end

function EconomyService:AddOre(player: Player, oreId: string, count: number)
	local data = DataService:Get(player)
	if not data or count <= 0 or not Config.Ores[oreId] then
		return
	end
	data.Ores[oreId] = (data.Ores[oreId] or 0) + math.floor(count)
	DataService:Replicate(player, "Ores")
end

function EconomyService:TrySpendOre(player: Player, oreId: string, count: number): boolean
	local data = DataService:Get(player)
	if not data or count <= 0 then
		return false
	end
	local have = data.Ores[oreId] or 0
	if have < count then
		return false
	end
	data.Ores[oreId] = have - count
	DataService:Replicate(player, "Ores")
	return true
end

return EconomyService
