--!strict
--[[
	ЕЖЕДНЕВНОЕ: награда за вход с серией (7-дневный цикл) и 3 задания Системы на день.
	Невыполненные вчера задания → шуточный «штраф» Системы (без настоящего наказания).
	Прогресс приходит через ProgressService:Record.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local DataService = require(script.Parent.DataService)
local EconomyService = require(script.Parent.EconomyService)
local LevelService = require(script.Parent.LevelService)
local SystemService = require(script.Parent.SystemService)

local DailyService = {}

local D = Config.Daily
local rng = Random.new()

local function today(): number
	return os.time() // 86400
end

local function questDef(id: string): any
	for _, q in D.QuestPool do
		if q.Id == id then
			return q
		end
	end
	return nil
end

local function rollQuests(): { any }
	local pool = table.clone(D.QuestPool)
	local list = {}
	for _ = 1, math.min(D.QuestCount, #pool) do
		local q = table.remove(pool, rng:NextInteger(1, #pool))
		table.insert(list, { Id = q.Id, Progress = 0, Done = false })
	end
	return list
end

local function refresh(player: Player, data: any, announceDelay: number)
	local day = today()
	local daily = data.Daily
	local changed = false

	-- награда за вход
	if daily.LastClaimDay < day then
		daily.Streak = if daily.LastClaimDay == day - 1 then daily.Streak + 1 else 1
		daily.LastClaimDay = day
		changed = true
		local reward = D.Rewards[((daily.Streak - 1) % #D.Rewards) + 1]
		task.delay(announceDelay, function()
			if not DataService:IsLoaded(player) then
				return
			end
			EconomyService:AddGold(player, reward.Gold)
			if reward.Ores then
				for oreId, count in reward.Ores do
					EconomyService:AddOre(player, oreId, count)
					SystemService:Toast(player, "TOAST_ORE", { count = count, ore = "@" .. Config.Ores[oreId].NameKey }, "ore")
				end
			end
			SystemService:Notify(player, "Daily", "SYS_DAILY_TITLE", "SYS_DAILY_BODY", { day = daily.Streak, gold = reward.Gold })
		end)
	end

	-- задания дня
	if daily.QuestDay ~= day then
		local missed = false
		if daily.QuestDay > 0 then
			for _, q in daily.Quests do
				if not q.Done then
					missed = true
				end
			end
		end
		daily.QuestDay = day
		daily.Quests = rollQuests()
		changed = true
		task.delay(announceDelay + 1, function()
			if not DataService:IsLoaded(player) then
				return
			end
			if missed then
				SystemService:Notify(player, "Penalty", "SYS_PENALTY_TITLE", "SYS_PENALTY_BODY")
			end
			SystemService:Notify(player, "Quest", "SYS_DAILYQ_NEW_TITLE", "SYS_DAILYQ_NEW_BODY")
		end)
	end

	if changed then
		DataService:Replicate(player, "Daily")
	end
end

function DailyService:Record(player: Player, event: string, amount: number)
	local data = DataService:Get(player)
	if not data then
		return
	end
	local changed = false
	for _, q in data.Daily.Quests do
		local def = questDef(q.Id)
		if def and not q.Done and def.Event == event then
			q.Progress = math.min(def.Count, q.Progress + amount)
			changed = true
			if q.Progress >= def.Count then
				q.Done = true
				EconomyService:AddGold(player, def.RewardGold)
				LevelService:AddXp(player, def.RewardXp)
				SystemService:Notify(player, "QuestDone", def.TitleKey, "SYS_REWARD_LINE", {
					count = def.Count,
					gold = def.RewardGold,
					xp = def.RewardXp,
				})
			end
		end
	end
	if changed then
		DataService:Replicate(player, "Daily")
	end
end

function DailyService:Start()
	DataService.Loaded:Connect(function(player: Player, data: any)
		-- новичку сначала показываем пробуждение и обучение
		refresh(player, data, if data.Tutorial.Done then 3 else 12)
	end)

	-- смена дня во время игры (полночь UTC)
	task.spawn(function()
		while true do
			task.wait(60)
			for _, player in Players:GetPlayers() do
				local data = DataService:Get(player)
				if data then
					refresh(player, data, 0)
				end
			end
		end
	end)
end

return DailyService
