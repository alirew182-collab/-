--!strict
-- СОЦИАЛЬНЫЕ БОНУСЫ: игра с друзьями и участие в группе дают больше опыта.

local Social = {
	GroupId = 0, -- ID твоей группы Roblox (0 = бонус за группу выключен)
	GroupXpBonus = 0.1, -- +10% опыта участникам группы
	FriendXpBonusPerFriend = 0.05, -- +5% за каждого друга на сервере
	MaxFriendXpBonus = 0.2,
}

return Social
