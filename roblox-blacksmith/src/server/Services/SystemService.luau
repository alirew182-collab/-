--!strict
--[[
	«СИСТЕМА» — окна в стиле маньхуа. Сервер решает, ЧТО показать (ключи локализации),
	клиент — КАК (анимация, звук, язык).

	Kind: Awakening | Quest | QuestDone | LevelUp | Reward | Info | Warning | Boss |
	      GateCleared | GateFailed | Forge
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Types = require(ReplicatedStorage.Shared.Types)
local NetService = require(script.Parent.NetService)

local SystemService = {}

local function payload(kind: string, title: string, body: string?, args: { [string]: any }?): Types.NotifyPayload
	return { Kind = kind, Title = title, Body = body, Args = args }
end

function SystemService:Notify(player: Player, kind: string, title: string, body: string?, args: { [string]: any }?)
	NetService:Fire(player, "SystemNotify", payload(kind, title, body, args))
end

function SystemService:NotifyList(players: { Player }, kind: string, title: string, body: string?, args: { [string]: any }?)
	NetService:FireList(players, "SystemNotify", payload(kind, title, body, args))
end

-- Объявление на весь сервер (открытие Врат; в Этапе 2 — ковка Mythic+)
function SystemService:Announce(kind: string, title: string, body: string?, args: { [string]: any }?)
	NetService:FireAll("SystemNotify", payload(kind, title, body, args))
end

-- Короткое сообщение внизу экрана. key — ключ локализации.
function SystemService:Toast(player: Player, key: string, args: { [string]: any }?, color: string?)
	NetService:Fire(player, "Toast", { Key = key, Args = args, Color = color })
end

function SystemService:ToastList(players: { Player }, key: string, args: { [string]: any }?, color: string?)
	NetService:FireList(players, "Toast", { Key = key, Args = args, Color = color })
end

function SystemService:Error(player: Player, key: string, args: { [string]: any }?)
	SystemService:Toast(player, key, args, "error")
end

return SystemService
