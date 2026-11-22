--!strict
--[[
	ДАННЫЕ ИГРОКА через ProfileStore (session locking → защита от дюпа между серверами).

	ProfileStore положи ModuleScript-ом в ServerStorage/ProfileStore (см. README).
	Если его нет — используется FallbackStore (данные НЕ сохраняются, только для теста).

	Правила, которые защищают от потери/дюпа:
	1. Все изменения данных — только на сервере и только через сервисы.
	2. Предмет сначала удаляется из одного места, потом добавляется в другое — в одном потоке без yield.
	3. Каждый предмет имеет уникальный GUID — продажа/надевание идут по Id.
	4. Если профиль открыт на другом сервере — сессия здесь закрывается и игрока кикает.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Localization = require(ReplicatedStorage.Shared.Localization)
local Signal = require(ReplicatedStorage.Shared.Signal)
local NetService = require(script.Parent.NetService)
local Template = require(script.Parent.Parent.Data.Template)
local Migrations = require(script.Parent.Parent.Data.Migrations)
local FallbackStore = require(script.Parent.Parent.Data.FallbackStore)

local DataService = {}

DataService.Loaded = Signal.new() -- (player, data)
DataService.Releasing = Signal.new() -- (player, data) — перед закрытием сессии

local profiles: { [Player]: any } = {}
local store: any = nil
DataService.UsingFallback = false

local function onPlayerAdded(player: Player)
	local profile = store:StartSessionAsync(`Player_{player.UserId}`, {
		Cancel = function()
			return player.Parent ~= Players
		end,
	})

	if profile == nil then
		-- Roblox не дал загрузить данные (или игрок вышел, пока грузилось)
		if player.Parent == Players then
			player:Kick("Could not load your data. Please rejoin. / Не удалось загрузить данные, перезайди.")
		end
		return
	end

	profile:AddUserId(player.UserId) -- GDPR
	profile:Reconcile()

	if not Migrations.Apply(profile.Data) then
		profile:EndSession()
		player:Kick("The game was updated. Please rejoin. / Игра обновилась, перезайди.")
		return
	end

	profile.OnSessionEnd:Connect(function()
		-- если мы сами закрыли сессию при выходе игрока, profiles[player] уже пуст
		local wasActive = profiles[player] == profile
		profiles[player] = nil
		if wasActive and player.Parent == Players then
			player:Kick("Your data was opened on another server. Please rejoin. / Данные открыты на другом сервере, перезайди.")
		end
	end)

	if player.Parent ~= Players then
		profile:EndSession()
		return
	end

	local data = profile.Data
	local now = os.time()
	if data.FirstJoinAt == 0 then
		data.FirstJoinAt = now
	end
	data.LastJoinAt = now

	profiles[player] = profile

	-- leaderstats (видно в списке игроков)
	local leaderstats = Instance.new("Folder")
	leaderstats.Name = "leaderstats"
	local level = Instance.new("IntValue")
	level.Name = "Level"
	level.Value = data.Level
	level.Parent = leaderstats
	local gold = Instance.new("IntValue")
	gold.Name = "Gold"
	gold.Value = data.Gold
	gold.Parent = leaderstats
	leaderstats.Parent = player

	player:SetAttribute("DataLoaded", true)
	NetService:Fire(player, "DataSync", data)
	DataService.Loaded:Fire(player, data)
end

local function onPlayerRemoving(player: Player)
	local profile = profiles[player]
	if profile then
		DataService.Releasing:Fire(player, profile.Data)
		profiles[player] = nil
		profile:EndSession()
	end
end

function DataService:Init()
	local module = ServerStorage:FindFirstChild("ProfileStore")
	if module and module:IsA("ModuleScript") then
		local ProfileStore = require(module) :: any
		store = ProfileStore.New(Config.Data.StoreName, Template)
	else
		DataService.UsingFallback = true
		warn(
			"[DataService] ServerStorage/ProfileStore не найден — данные НЕ сохраняются! "
				.. "Скачай ProfileStore (см. README) перед публикацией."
		)
		store = FallbackStore.New(Config.Data.StoreName, Template)
	end
end

function DataService:Start()
	NetService:OnInvoke("RequestData", function(player: Player)
		local profile = profiles[player]
		if not profile then
			return { ok = false, err = "ERR_NOT_LOADED" }
		end
		return { ok = true, data = profile.Data }
	end)

	NetService:OnEvent("SetLanguage", function(player: Player, lang: any)
		local data = DataService:Get(player)
		if data and type(lang) == "string" and Localization.IsSupported(lang) then
			data.Settings.Language = lang
		end
	end)

	-- настройки игрока (булевы, по белому списку)
	local SETTINGS = { Sfx = true, Music = true, DamageNumbers = true, LowGraphics = true }
	NetService:OnEvent("SetSetting", function(player: Player, key: any, value: any)
		local data = DataService:Get(player)
		if data and type(key) == "string" and SETTINGS[key] and type(value) == "boolean" then
			data.Settings[key] = value
		end
	end)

	Players.PlayerRemoving:Connect(onPlayerRemoving)
end

-- Вызывается загрузчиком ПОСЛЕ :Start() всех сервисов, чтобы никто не пропустил DataService.Loaded
function DataService:BeginLoading()
	Players.PlayerAdded:Connect(function(player)
		task.spawn(onPlayerAdded, player)
	end)
	for _, player in Players:GetPlayers() do
		task.spawn(onPlayerAdded, player)
	end
end

-- Данные игрока или nil, если ещё не загружены / уже выгружены
function DataService:Get(player: Player): any
	local profile = profiles[player]
	return if profile then profile.Data else nil
end

function DataService:IsLoaded(player: Player): boolean
	return profiles[player] ~= nil
end

-- Отправить клиенту изменившиеся ключи верхнего уровня
function DataService:Replicate(player: Player, ...: string)
	local data = DataService:Get(player)
	if not data then
		return
	end
	local patch = {}
	for _, key in { ... } do
		patch[key] = data[key]
	end
	NetService:Fire(player, "DataPatch", patch)

	local leaderstats = player:FindFirstChild("leaderstats")
	if leaderstats then
		local level = leaderstats:FindFirstChild("Level") :: IntValue?
		local gold = leaderstats:FindFirstChild("Gold") :: IntValue?
		if level then
			level.Value = data.Level
		end
		if gold then
			gold.Value = data.Gold
		end
	end
end

-- Немедленное сохранение (после обмена и покупки): сужает окно, в котором сбой сервера
-- мог бы сохранить одну сторону сделки без другой.
function DataService:SaveNow(player: Player)
	local profile = profiles[player]
	if profile and type(profile.Save) == "function" then
		pcall(function()
			profile:Save()
		end)
	end
end

function DataService:AddStat(player: Player, stat: string, amount: number)
	local data = DataService:Get(player)
	if data then
		data.Stats[stat] = (data.Stats[stat] or 0) + amount
		DataService:Replicate(player, "Stats")
	end
end

return DataService
