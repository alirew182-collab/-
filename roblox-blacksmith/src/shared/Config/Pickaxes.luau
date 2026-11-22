--!strict
-- КИРКИ. Power — урон по жиле за удар, Tier — какие руды можно добывать.

export type PickaxeDef = {
	Id: string,
	NameKey: string,
	Order: number,
	Tier: number,
	Power: number,
	Price: number, -- 0 = выдаётся бесплатно
	RequiredLevel: number,
}

local Pickaxes: { [string]: PickaxeDef } = {
	Stone = { Id = "Stone", NameKey = "PICK_STONE", Order = 1, Tier = 1, Power = 1, Price = 0, RequiredLevel = 1 },
	Iron = { Id = "Iron", NameKey = "PICK_IRON", Order = 2, Tier = 2, Power = 2, Price = 150, RequiredLevel = 3 },
	Moonsilver = {
		Id = "Moonsilver",
		NameKey = "PICK_MOONSILVER",
		Order = 3,
		Tier = 3,
		Power = 3,
		Price = 900,
		RequiredLevel = 6,
	},
}

return Pickaxes
