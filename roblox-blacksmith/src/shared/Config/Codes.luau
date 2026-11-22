--!strict
--[[
	КОДЫ для раздачи в соцсетях. Ключ — сам код (ЗАГЛАВНЫМИ буквами).
	Expires: 0 = бессрочно, иначе unix-время окончания (https://www.unixtimestamp.com).
	Каждый игрок может активировать код один раз.
]]

export type CodeDef = {
	Gold: number?,
	Xp: number?,
	Ores: { [string]: number }?,
	Expires: number,
}

local Codes: { [string]: CodeDef } = {
	RELEASE = { Gold = 250, Xp = 100, Ores = { Copper = 10 }, Expires = 0 },
	SYSTEM = { Gold = 150, Ores = { Iron = 5 }, Expires = 0 },
	FORGEMASTER = { Gold = 500, Expires = 0 },
}

return Codes
