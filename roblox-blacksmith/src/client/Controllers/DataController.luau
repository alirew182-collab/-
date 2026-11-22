--!strict
--[[
	Копия данных игрока на клиенте (только для отображения — истина на сервере).
	DataController.Changed:Connect(function(key) ... end) — key = изменившийся ключ или "*" (полная синхронизация).
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Net = require(ReplicatedStorage.Shared.Net)
local Signal = require(ReplicatedStorage.Shared.Signal)

local DataController = {}

DataController.Data = nil :: any
DataController.Changed = Signal.new()
DataController.Ready = Signal.new()

local function setFull(data: any)
	local first = DataController.Data == nil
	DataController.Data = data
	DataController.Changed:Fire("*")
	if first then
		DataController.Ready:Fire(data)
	end
end

function DataController:Get(): any
	return DataController.Data
end

function DataController:WaitForData(): any
	if DataController.Data then
		return DataController.Data
	end
	return DataController.Ready:Wait()
end

function DataController:Start()
	Net.Event("DataSync").OnClientEvent:Connect(setFull)
	Net.Event("DataPatch").OnClientEvent:Connect(function(patch: { [string]: any })
		local data = DataController.Data
		if not data then
			return
		end
		for key, value in patch do
			data[key] = value
		end
		for key in patch do
			DataController.Changed:Fire(key)
		end
	end)

	-- на случай, если DataSync пришёл раньше, чем мы подписались
	task.spawn(function()
		local remote = Net.Function("RequestData")
		for _ = 1, 60 do
			if DataController.Data then
				return
			end
			local ok, result = pcall(function()
				return remote:InvokeServer()
			end)
			if ok and type(result) == "table" and result.ok and result.data then
				if not DataController.Data then
					setFull(result.data)
				end
				return
			end
			task.wait(2)
		end
	end)
end

return DataController
