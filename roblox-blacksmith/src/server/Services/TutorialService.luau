--!strict
--[[
	ОБУЧЕНИЕ = цепочка квестов Системы (Config.Tutorial).
	Цель: за первые ~60 секунд игрок добывает руду, куёт первое оружие и видит окно Системы.

	Другие сервисы просто сообщают о событиях:
		TutorialService:Record(player, "OreMined", { OreId = "Copper", Count = 2 })
]]

local AnalyticsService = game:GetService("AnalyticsService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local DataService = require(script.Parent.DataService)
local SystemService = require(script.Parent.SystemService)
local LevelService = require(script.Parent.LevelService)
local EconomyService = require(script.Parent.EconomyService)

local TutorialService = {}

local Steps = Config.Tutorial.Steps

local function announce(player: Player, stepIndex: number)
	local step = Steps[stepIndex]
	if not step then
		return
	end
	if step.RewardGold > 0 then
		SystemService:Notify(player, "Quest", step.TitleKey, "SYS_QUEST_REWARD", { xp = step.RewardXp, gold = step.RewardGold })
	else
		SystemService:Notify(player, "Quest", step.TitleKey, "SYS_QUEST_REWARD_XP", { xp = step.RewardXp })
	end
end

function TutorialService:Record(player: Player, event: string, info: { OreId: string?, Count: number? }?)
	local data = DataService:Get(player)
	if not data or data.Tutorial.Done then
		return
	end
	local tutorial = data.Tutorial
	local step = Steps[tutorial.Step]
	if not step or step.Event ~= event then
		return
	end
	if step.OreId and (not info or info.OreId ~= step.OreId) then
		return
	end

	tutorial.Progress += (info and info.Count) or 1
	if tutorial.Progress < step.Count then
		DataService:Replicate(player, "Tutorial")
		return
	end

	-- шаг выполнен → воронка обучения в Creator Hub (Analytics → Funnels)
	pcall(function()
		AnalyticsService:LogOnboardingFunnelStepEvent(player, tutorial.Step + 1, step.Id)
	end)
	tutorial.Step += 1
	tutorial.Progress = 0
	local finished = tutorial.Step > #Steps
	if finished then
		tutorial.Done = true
	end
	DataService:Replicate(player, "Tutorial")

	SystemService:Notify(
		player,
		"QuestDone",
		step.TitleKey,
		if step.RewardGold > 0 then "SYS_REWARD_LINE" else "SYS_REWARD_LINE_XP",
		{ xp = step.RewardXp, gold = step.RewardGold }
	)
	if step.RewardGold > 0 then
		EconomyService:AddGold(player, step.RewardGold)
	end
	if step.RewardXp > 0 then
		LevelService:AddXp(player, step.RewardXp)
	end

	if finished then
		SystemService:Notify(player, "Reward", "SYS_TUTORIAL_DONE_TITLE", "SYS_TUTORIAL_DONE_BODY")
	else
		announce(player, tutorial.Step)
	end
end

function TutorialService:Start()
	DataService.Loaded:Connect(function(player: Player, data: any)
		if data.Tutorial.Done then
			return
		end
		task.delay(2, function()
			if not DataService:IsLoaded(player) then
				return
			end
			if not data.Tutorial.Awakened then
				pcall(function()
					AnalyticsService:LogOnboardingFunnelStepEvent(player, 1, "Awakened")
				end)
				data.Tutorial.Awakened = true
				DataService:Replicate(player, "Tutorial")
				SystemService:Notify(player, "Awakening", "SYS_AWAKEN_TITLE", "SYS_AWAKEN_BODY")
			end
			announce(player, data.Tutorial.Step)
		end)
	end)
end

return TutorialService
