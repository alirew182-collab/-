--!strict
-- КОДЫ: игрок вводит код из соцсетей → разовая награда. Список кодов — Config/Codes.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Guards = require(script.Parent.Parent.Util.Guards)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local LevelService = require(script.Parent.LevelService)
local NetService = require(script.Parent.NetService)

local CodesService = {}

local function redeem(player: Player, raw: any)
	local data = DataService:Get(player)
	if not data then
		return { ok = false, err = "ERR_NOT_LOADED" }
	end
	if not Guards.String(raw, 40) then
		return { ok = false, err = "ERR_CODE_INVALID" }
	end
	local code = string.upper((string.gsub(raw, "%s", "")))
	local def = Config.Codes[code]
	if not def then
		return { ok = false, err = "ERR_CODE_INVALID" }
	end
	if data.RedeemedCodes[code] then
		return { ok = false, err = "ERR_CODE_USED" }
	end
	if def.Expires > 0 and os.time() > def.Expires then
		return { ok = false, err = "ERR_CODE_EXPIRED" }
	end

	data.RedeemedCodes[code] = true -- сначала отмечаем, потом выдаём (нет двойной выдачи)
	if def.Gold then
		EconomyService:AddGold(player, def.Gold)
	end
	if def.Xp then
		LevelService:AddXp(player, def.Xp, true)
	end
	if def.Ores then
		for oreId, count in def.Ores do
			EconomyService:AddOre(player, oreId, count)
		end
	end
	return { ok = true, gold = def.Gold or 0, xp = def.Xp or 0 }
end

function CodesService:Start()
	NetService:OnInvoke("RedeemCode", redeem)
end

return CodesService
