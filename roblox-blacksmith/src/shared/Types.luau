--!strict
-- Общие типы данных клиента и сервера.

export type WeaponData = {
	Id: string,
	Type: string, -- ключ Config.Weapons
	Ore: string, -- ключ Config.Ores
	Quality: number, -- 0..100
	Rarity: string, -- ключ Config.Rarities
	Traits: { string }, -- Этап 2
	Xp: number, -- опыт оружия (пробуждение — Этап 3)
	CreatedAt: number,
}

export type TutorialData = {
	Step: number,
	Progress: number,
	Done: boolean,
	Awakened: boolean,
}

export type PlayerData = {
	SchemaVersion: number,
	Gold: number,
	Level: number,
	Xp: number,
	Rank: string,
	Ores: { [string]: number },
	Weapons: { [string]: WeaponData },
	EquippedWeapon: string, -- "" = ничего
	Pickaxe: string,
	OwnedPickaxes: { [string]: boolean },
	Tutorial: TutorialData,
	Stats: { [string]: number },
	Settings: { Language: string },
	ProcessedReceipts: { [string]: boolean },
	FirstJoinAt: number,
	LastJoinAt: number,
}

export type NotifyPayload = {
	Kind: string, -- Awakening | Quest | QuestDone | LevelUp | Reward | Info | Warning | Boss | GateCleared | GateFailed | Forge
	Title: string, -- ключ локализации
	Body: string?, -- ключ локализации
	Args: { [string]: any }?, -- подстановки {name}; значения-ключи с префиксом "@" тоже переводятся
}

export type Response = {
	ok: boolean,
	err: string?,
	[string]: any,
}

return {}
