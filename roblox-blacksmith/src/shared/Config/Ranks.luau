--!strict
--[[
	РАНГИ ОХОТНИКА-КУЗНЕЦА. Ранг растёт с уровнем и даёт постоянный бонус к урону.
	Испытание Системы (бой с боссом за ранг) — следующий этап; пороги останутся теми же.
]]

export type RankDef = {
	Id: string,
	Short: string, -- буква на значке
	NameKey: string,
	MinLevel: number,
	DamageBonus: number,
}

local Ranks: { RankDef } = {
	{ Id = "F", Short = "F", NameKey = "RANK_F", MinLevel = 1, DamageBonus = 0 },
	{ Id = "E", Short = "E", NameKey = "RANK_E", MinLevel = 5, DamageBonus = 0.04 },
	{ Id = "D", Short = "D", NameKey = "RANK_D", MinLevel = 10, DamageBonus = 0.08 },
	{ Id = "C", Short = "C", NameKey = "RANK_C", MinLevel = 16, DamageBonus = 0.13 },
	{ Id = "B", Short = "B", NameKey = "RANK_B", MinLevel = 24, DamageBonus = 0.19 },
	{ Id = "A", Short = "A", NameKey = "RANK_A", MinLevel = 32, DamageBonus = 0.26 },
	{ Id = "S", Short = "S", NameKey = "RANK_S", MinLevel = 42, DamageBonus = 0.35 },
	{ Id = "National", Short = "N", NameKey = "RANK_NATIONAL", MinLevel = 55, DamageBonus = 0.45 },
	{ Id = "Unknown", Short = "?", NameKey = "RANK_UNKNOWN", MinLevel = 75, DamageBonus = 0.6 },
}

return Ranks
