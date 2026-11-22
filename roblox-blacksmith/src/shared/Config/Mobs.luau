--!strict
--[[
	МОБЫ И БОССЫ.
	Модель: ServerStorage/Mobs/<Id> — Model с Humanoid и HumanoidRootPart.
	Если модели нет — сервер соберёт блок-заглушку размером Size и цветом Color.
]]

export type MobDef = {
	Id: string,
	NameKey: string,
	IsBoss: boolean,
	MaxHealth: number,
	Damage: number,
	AttackRange: number,
	AttackCooldown: number,
	WindupSeconds: number, -- «замах» перед ударом: окно для блока/парирования
	WalkSpeed: number,
	XpReward: number,
	GoldReward: number,
	Size: Vector3,
	Color: Color3,
	-- только боссы
	SlamDamage: number?,
	SlamRadius: number?,
	SlamWindup: number?,
	SlamCooldown: number?,
	SlamTriggerRange: number?,
}

local Mobs: { [string]: MobDef } = {
	Ashling = {
		Id = "Ashling",
		NameKey = "MOB_ASHLING",
		IsBoss = false,
		MaxHealth = 40,
		Damage = 6,
		AttackRange = 5,
		AttackCooldown = 1.6,
		WindupSeconds = 0.5,
		WalkSpeed = 12,
		XpReward = 8,
		GoldReward = 3,
		Size = Vector3.new(2, 3, 2),
		Color = Color3.fromRGB(90, 70, 70),
	},
	CinderHound = {
		Id = "CinderHound",
		NameKey = "MOB_CINDERHOUND",
		IsBoss = false,
		MaxHealth = 70,
		Damage = 9,
		AttackRange = 5.5,
		AttackCooldown = 1.4,
		WindupSeconds = 0.45,
		WalkSpeed = 15,
		XpReward = 14,
		GoldReward = 5,
		Size = Vector3.new(2.5, 2.5, 4),
		Color = Color3.fromRGB(150, 60, 30),
	},
	CinderWarden = {
		Id = "CinderWarden",
		NameKey = "MOB_CINDERWARDEN",
		IsBoss = true,
		MaxHealth = 900,
		Damage = 14,
		AttackRange = 8,
		AttackCooldown = 2.2,
		WindupSeconds = 0.7,
		WalkSpeed = 10,
		XpReward = 120,
		GoldReward = 40,
		Size = Vector3.new(6, 9, 5),
		Color = Color3.fromRGB(60, 30, 25),
		SlamDamage = 28,
		SlamRadius = 13,
		SlamWindup = 1.3,
		SlamCooldown = 8,
		SlamTriggerRange = 18,
	},
}

return Mobs
