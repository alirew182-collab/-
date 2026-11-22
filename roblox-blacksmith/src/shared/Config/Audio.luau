--!strict
--[[
	ЗВУКИ. Вставь сюда ID своих звуков из Creator Store ("rbxassetid://123...").
	Пустая строка = звук отключён (игра работает без звуков).
]]

local Audio: { [string]: string } = {
	SystemOpen = "", -- появление окна Системы
	SystemLevelUp = "",
	SystemQuestDone = "",
	SystemWarning = "",
	SystemBoss = "",
	MineHit = "",
	OreBreak = "",
	ForgePump = "",
	ForgePour = "",
	ForgeHammerPerfect = "",
	ForgeHammerHit = "",
	ForgeHammerMiss = "",
	ForgeDone = "",
	Swing = "",
	HitMob = "",
	Parry = "",
	Coins = "",
	UiClick = "",
}

return Audio
