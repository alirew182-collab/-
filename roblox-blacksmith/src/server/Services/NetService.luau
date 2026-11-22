--!strict
--[[
	Обёртка над Remote: rate-limit (token bucket на игрока и Remote), pcall вокруг обработчиков,
	единый формат ответа для RemoteFunction.
	Все сервисы подписываются на Remote ТОЛЬКО через NetService.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Net = require(ReplicatedStorage.Shared.Net)

local NetService = {}

type Bucket = { tokens: number, last: number }
local buckets: { [Player]: { [string]: Bucket } } = {}
local warnedAt: { [Player]: number } = {}

local function allow(player: Player, name: string): boolean
	local limit = Config.Network.Limits[name] or Config.Network.Default
	local perPlayer = buckets[player]
	if not perPlayer then
		perPlayer = {}
		buckets[player] = perPlayer
	end
	local now = os.clock()
	local bucket = perPlayer[name]
	if not bucket then
		bucket = { tokens = limit.Burst, last = now }
		perPlayer[name] = bucket
	end
	bucket.tokens = math.min(limit.Burst, bucket.tokens + (now - bucket.last) * limit.PerSecond)
	bucket.last = now
	if bucket.tokens < 1 then
		if Config.Debug.LogSuspicious and (warnedAt[player] or 0) + 5 < now then
			warnedAt[player] = now
			warn(`[Net] rate limit: {player.Name} → {name}`)
		end
		return false
	end
	bucket.tokens -= 1
	return true
end

function NetService:Init()
	Net.Setup()
	Players.PlayerRemoving:Connect(function(player)
		buckets[player] = nil
		warnedAt[player] = nil
	end)
end

-- Клиент → сервер (RemoteEvent)
function NetService:OnEvent(name: string, handler: (player: Player, ...any) -> ())
	Net.Event(name).OnServerEvent:Connect(function(player: Player, ...: any)
		if not allow(player, name) then
			return
		end
		local ok, err = pcall(handler, player, ...)
		if not ok then
			warn(`[Net] {name} ({player.Name}): {err}`)
		end
	end)
end

-- Клиент → сервер с ответом (RemoteFunction). handler обязан вернуть { ok = ..., ... }
function NetService:OnInvoke(name: string, handler: (player: Player, ...any) -> { [string]: any })
	Net.Function(name).OnServerInvoke = function(player: Player, ...: any)
		if not allow(player, name) then
			return { ok = false, err = "ERR_RATE_LIMIT" }
		end
		local ok, result = pcall(handler, player, ...)
		if not ok then
			warn(`[Net] {name} ({player.Name}): {result}`)
			return { ok = false, err = "ERR_SERVER" }
		end
		if type(result) ~= "table" then
			return { ok = false, err = "ERR_SERVER" }
		end
		return result
	end
end

function NetService:Fire(player: Player, name: string, ...: any)
	Net.Event(name):FireClient(player, ...)
end

function NetService:FireAll(name: string, ...: any)
	Net.Event(name):FireAllClients(...)
end

function NetService:FireList(players: { Player }, name: string, ...: any)
	local remote = Net.Event(name)
	for _, player in players do
		if player.Parent == Players then
			remote:FireClient(player, ...)
		end
	end
end

-- Косметика (цифры урона, искры): ненадёжный канал, всем в радиусе
function NetService:FxNear(position: Vector3, radius: number, payload: { [string]: any })
	local remote = Net.UnreliableEvent("Fx")
	for _, player in Players:GetPlayers() do
		local character = player.Character
		local root = character and character:FindFirstChild("HumanoidRootPart") :: BasePart?
		if root and (root.Position - position).Magnitude <= radius then
			remote:FireClient(player, payload)
		end
	end
end

function NetService:FxTo(player: Player, payload: { [string]: any })
	Net.UnreliableEvent("Fx"):FireClient(player, payload)
end

return NetService
