--!strict
-- Опыт, уровни, здоровье персонажа от уровня.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local DataService = require(script.Parent.DataService)
local SocialService = require(script.Parent.SocialService)
local SystemService = require(script.Parent.SystemService)

local LevelService = {}

local function applyStats(player: Player, heal: boolean)
	local data = DataService:Get(player)
	local character = player.Character
	if not data or not character then
		return
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if not humanoid then
		return
	end
	local maxHealth = WeaponMath.MaxHealth(data.Level)
	humanoid.MaxHealth = maxHealth
	if heal or humanoid.Health > maxHealth then
		humanoid.Health = maxHealth
	end
end

-- Ранг по уровню (Config/Ranks). notify = показать окно «Новый ранг».
local function checkRank(player: Player, data: any, notify: boolean)
	local rank = WeaponMath.RankFor(data.Level)
	if data.Rank == rank.Id then
		return
	end
	local old = WeaponMath.RankById(data.Rank)
	data.Rank = rank.Id
	DataService:Replicate(player, "Rank")
	if notify and rank.MinLevel > old.MinLevel then
		SystemService:Notify(player, "RankUp", "SYS_RANKUP_TITLE", "SYS_RANKUP_BODY", {
			rank = "@" .. rank.NameKey,
			bonus = math.floor(rank.DamageBonus * 100 + 0.5),
		})
	end
end

-- noBonus = true: не умножать на бонус друзей/группы (коды, фиксированные награды)
function LevelService:AddXp(player: Player, amount: number, noBonus: boolean?)
	local data = DataService:Get(player)
	if not data or amount <= 0 then
		return
	end
	if not noBonus then
		amount *= SocialService:XpMult(player)
	end
	data.Xp += math.floor(amount)

	local maxLevel = Config.Leveling.MaxLevel
	local gained = 0
	while data.Level < maxLevel do
		local need = WeaponMath.XpToNext(data.Level)
		if data.Xp < need then
			break
		end
		data.Xp -= need
		data.Level += 1
		gained += 1
	end
	if data.Level >= maxLevel then
		data.Xp = 0
	end

	DataService:Replicate(player, "Level", "Xp")

	if gained > 0 then
		applyStats(player, true)
		SystemService:Notify(player, "LevelUp", "SYS_LEVELUP_TITLE", "SYS_LEVELUP_BODY", { level = data.Level })
		checkRank(player, data, true)
	end
end

function LevelService:GetLevel(player: Player): number
	local data = DataService:Get(player)
	return if data then data.Level else 1
end

function LevelService:DamageMult(player: Player): number
	return WeaponMath.LevelDamageMult(LevelService:GetLevel(player))
end

function LevelService:Start()
	DataService.Loaded:Connect(function(player: Player, data: any)
		checkRank(player, data, false)
		player.CharacterAdded:Connect(function(character)
			character:WaitForChild("Humanoid", 10)
			applyStats(player, true)
		end)
		if player.Character then
			applyStats(player, true)
		end
	end)
end

return LevelService
