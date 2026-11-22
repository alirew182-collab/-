--!strict
--[[
	ЗАГЛУШКА ProfileStore — ТОЛЬКО ДЛЯ ТЕСТА В STUDIO, ДАННЫЕ НЕ СОХРАНЯЮТСЯ.
	Используется, если в ServerStorage нет модуля ProfileStore.
	Повторяет нужную нам часть API ProfileStore: New / StartSessionAsync / profile.Data /
	Reconcile / AddUserId / EndSession / IsActive / OnSessionEnd.
]]

local Signal = require(game:GetService("ReplicatedStorage").Shared.Signal)

local FallbackStore = {}

local function deepCopy<T>(value: T): T
	if type(value) ~= "table" then
		return value
	end
	local copy = {}
	for k, v in value :: any do
		copy[deepCopy(k)] = deepCopy(v)
	end
	return copy :: any
end

local function reconcile(target: any, template: any)
	for k, v in template do
		if type(k) == "string" then
			if target[k] == nil then
				target[k] = deepCopy(v)
			elseif type(target[k]) == "table" and type(v) == "table" then
				reconcile(target[k], v)
			end
		end
	end
end

function FallbackStore.New(_name: string, template: any)
	local store = {}

	function store:StartSessionAsync(_key: string, _params: any): any
		local active = true
		local onEnd = Signal.new()
		local profile = {
			Data = deepCopy(template),
			OnSessionEnd = onEnd,
		}
		function profile:Reconcile()
			reconcile(profile.Data, template)
		end
		function profile:AddUserId(_userId: number) end
		function profile:IsActive(): boolean
			return active
		end
		function profile:EndSession()
			if active then
				active = false
				onEnd:Fire()
			end
		end
		return profile
	end

	return store
end

return FallbackStore
