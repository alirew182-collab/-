--!strict
--[[
	МИГРАЦИИ СХЕМЫ ДАННЫХ.
	Steps[N] переводит данные из версии N в версию N+1. Вызываются по цепочке при загрузке.
	Пример на будущее (v1 → v2: переименовать Gold в Coins):
		[1] = function(data)
			data.Coins = data.Gold
			data.Gold = nil
		end,
]]

local Config = require(game:GetService("ReplicatedStorage").Shared.Config)

local Migrations = {}

local Steps: { [number]: (data: any) -> () } = {
	-- пока пусто: текущая версия схемы = 1
}

-- false → данные новее, чем знает этот сервер (старый сервер после обновления). Не трогаем их.
function Migrations.Apply(data: any): boolean
	local target = Config.Data.SchemaVersion
	local version = tonumber(data.SchemaVersion) or 1
	if version > target then
		return false
	end
	while version < target do
		local step = Steps[version]
		if step then
			step(data)
		end
		version += 1
		data.SchemaVersion = version
	end
	return true
end

return Migrations
