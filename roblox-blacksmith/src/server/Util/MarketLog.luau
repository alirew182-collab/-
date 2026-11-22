--!strict
--[[
	ЖУРНАЛ СДЕЛОК (обмены и покупки в лавках).
	Каждая запись — в Output сервера и в DataStore Config.Trading.LogStoreName (ключ = Id сделки),
	чтобы при жалобе на скам или дюп можно было найти, кто кому что отдал.
	Посмотреть записи: Studio → View → Data Stores, или DataStoreService в командной строке.
]]

local DataStoreService = game:GetService("DataStoreService")
local HttpService = game:GetService("HttpService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)

local MarketLog = {}

local store: DataStore? = nil
local ok = pcall(function()
	store = DataStoreService:GetDataStore(Config.Trading.LogStoreName)
end)
if not ok then
	store = nil -- Studio без доступа к API: пишем только в Output
end

-- kind: "trade" | "shop" | "offline"
function MarketLog.Write(kind: string, record: { [string]: any }): string
	local id = HttpService:GenerateGUID(false)
	record.Kind = kind
	record.Time = os.time()
	record.Id = id
	local okJson, json = pcall(function()
		return HttpService:JSONEncode(record)
	end)
	print(`[Market] {if okJson then json else kind}`)
	local s = store
	if s then
		task.spawn(function()
			pcall(function()
				s:SetAsync(id, record)
			end)
		end)
	end
	return id
end

-- Краткое описание оружия для журнала
function MarketLog.Weapon(weapon: any): { [string]: any }
	return {
		Id = weapon.Id,
		Type = weapon.Type,
		Ore = weapon.Ore,
		Rarity = weapon.Rarity,
		Quality = weapon.Quality,
	}
end

return MarketLog
