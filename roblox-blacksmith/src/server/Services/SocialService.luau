--!strict
--[[
	СОЦИАЛЬНЫЕ БОНУСЫ К ОПЫТУ: друзья на сервере и участие в группе Roblox.
	Итоговый бонус кладётся в атрибут игрока XpBonus (0.15 = +15%) — его показывает HUD.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)

local SocialService = {}

local S = Config.Social
local inGroup: { [Player]: boolean } = {}
local friends: { [Player]: { [Player]: boolean } } = {}

local function update(player: Player)
	if player.Parent ~= Players then
		return
	end
	local count = 0
	for other in friends[player] or {} do
		if other.Parent == Players then
			count += 1
		end
	end
	local bonus = math.min(S.MaxFriendXpBonus, count * S.FriendXpBonusPerFriend)
	if inGroup[player] then
		bonus += S.GroupXpBonus
	end
	player:SetAttribute("XpBonus", bonus)
end

local function checkGroup(player: Player)
	if S.GroupId <= 0 then
		return
	end
	local ok, result = pcall(function()
		return player:IsInGroup(S.GroupId)
	end)
	inGroup[player] = ok and result == true
end

local function checkFriends(player: Player)
	friends[player] = friends[player] or {}
	for _, other in Players:GetPlayers() do
		if other ~= player then
			local ok, isFriend = pcall(function()
				return player:IsFriendsWith(other.UserId)
			end)
			if ok and isFriend then
				friends[player][other] = true
				friends[other] = friends[other] or {}
				friends[other][player] = true
				update(other)
			end
		end
	end
end

function SocialService:XpMult(player: Player): number
	local bonus = player:GetAttribute("XpBonus")
	return 1 + (if type(bonus) == "number" then bonus else 0)
end

function SocialService:Start()
	local function onAdded(player: Player)
		task.spawn(function()
			checkGroup(player)
			checkFriends(player)
			update(player)
		end)
	end
	Players.PlayerAdded:Connect(onAdded)
	for _, player in Players:GetPlayers() do
		onAdded(player)
	end
	Players.PlayerRemoving:Connect(function(player)
		local list = friends[player]
		friends[player] = nil
		inGroup[player] = nil
		if list then
			for other in list do
				if friends[other] then
					friends[other][player] = nil
					update(other)
				end
			end
		end
	end)
end

return SocialService
