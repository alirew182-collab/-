--!strict
--[[
	ЕДИНАЯ ТОЧКА ДЛЯ ИГРОВЫХ СОБЫТИЙ. Сервисы сообщают сюда, что произошло,
	а ProgressService раздаёт это обучению и ежедневным заданиям (позже — достижениям, аналитике).

	События: OreMined, WeaponForged, QualityForge80, WeaponEquipped, MerchantOpened, ItemSold,
	         GateEntered, GateCleared, MobKilled, Parry
]]

local DailyService = require(script.Parent.DailyService)
local TutorialService = require(script.Parent.TutorialService)

local ProgressService = {}

function ProgressService:Record(player: Player, event: string, info: { OreId: string?, Count: number? }?)
	TutorialService:Record(player, event, info)
	DailyService:Record(player, event, (info and info.Count) or 1)
end

return ProgressService
