--!strict
--[[
	Единый список Remote-ов. Сервер создаёт их в ReplicatedStorage/Remotes,
	клиент ждёт. Имя, которого нет в списке, = ошибка (защита от опечаток).

	Соглашение для RemoteFunction: сервер всегда отвечает таблицей { ok = boolean, err = string?, ... }.
	err — ключ локализации (ERR_*), клиент покажет его тостом.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local FOLDER_NAME = "Remotes"

local Net = {}

-- RemoteEvent
Net.Events = {
	-- сервер → клиент
	DataSync = true, -- полный снимок данных игрока
	DataPatch = true, -- изменившиеся ключи верхнего уровня
	SystemNotify = true, -- окно Системы
	Toast = true, -- короткое сообщение внизу экрана
	OpenPanel = true, -- открыть окно (кузня, торговец)
	GateState = true, -- состояние группы/забега во Вратах
	-- клиент → сервер
	CombatAttack = true,
	CombatBlock = true,
	ForgeCancel = true,
	SetLanguage = true,
	GateStart = true,
	GateLeave = true,
}

-- UnreliableRemoteEvent (косметика, можно потерять)
Net.Unreliable = {
	Fx = true,
}

-- RemoteFunction (клиент → сервер с ответом)
Net.Functions = {
	RequestData = true,
	ForgeStart = true,
	ForgeSubmit = true,
	EquipWeapon = true,
	EquipPickaxe = true,
	SellWeapons = true,
	SellOres = true,
	BuyPickaxe = true,
}

local function getFolder(): Instance
	if RunService:IsServer() then
		local folder = ReplicatedStorage:FindFirstChild(FOLDER_NAME)
		if not folder then
			folder = Instance.new("Folder")
			folder.Name = FOLDER_NAME
			folder.Parent = ReplicatedStorage
		end
		return folder :: Instance
	end
	return ReplicatedStorage:WaitForChild(FOLDER_NAME)
end

local function getOrCreate(name: string, className: string): Instance
	local folder = getFolder()
	if RunService:IsServer() then
		local existing = folder:FindFirstChild(name)
		if existing then
			return existing
		end
		local remote = Instance.new(className)
		remote.Name = name
		remote.Parent = folder
		return remote
	end
	return folder:WaitForChild(name)
end

-- Сервер: создать все Remote заранее, чтобы клиент не ждал вечно
function Net.Setup()
	assert(RunService:IsServer(), "Net.Setup только на сервере")
	for name in Net.Events do
		getOrCreate(name, "RemoteEvent")
	end
	for name in Net.Unreliable do
		getOrCreate(name, "UnreliableRemoteEvent")
	end
	for name in Net.Functions do
		getOrCreate(name, "RemoteFunction")
	end
end

function Net.Event(name: string): RemoteEvent
	assert((Net.Events :: any)[name], `Неизвестный RemoteEvent: {name}`)
	return getOrCreate(name, "RemoteEvent") :: RemoteEvent
end

function Net.UnreliableEvent(name: string): UnreliableRemoteEvent
	assert((Net.Unreliable :: any)[name], `Неизвестный UnreliableRemoteEvent: {name}`)
	return getOrCreate(name, "UnreliableRemoteEvent") :: UnreliableRemoteEvent
end

function Net.Function(name: string): RemoteFunction
	assert((Net.Functions :: any)[name], `Неизвестная RemoteFunction: {name}`)
	return getOrCreate(name, "RemoteFunction") :: RemoteFunction
end

return Net
