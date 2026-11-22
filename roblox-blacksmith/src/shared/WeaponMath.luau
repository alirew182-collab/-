--!strict
-- Формулы оружия, редкостей, рангов: одинаковые на клиенте (превью, шансы) и сервере (истина).

local Config = require(script.Parent.Config)

local WeaponMath = {}

type WeaponLike = { Type: string, Ore: string, Quality: number, Rarity: string, Traits: { string }? }

function WeaponMath.QualityMult(quality: number): number
	local f = Config.Forging
	local q = math.clamp(quality, 0, 100) / 100
	return f.QualityMultMin + (f.QualityMultMax - f.QualityMultMin) * q
end

function WeaponMath.HasTrait(weapon: WeaponLike?, traitId: string): boolean
	return weapon ~= nil and weapon.Traits ~= nil and table.find(weapon.Traits, traitId) ~= nil
end

-- Произведение поля черт (DamageMult / CooldownMult)
local function traitProduct(weapon: WeaponLike, field: string): number
	local mult = 1
	if weapon.Traits then
		for _, id in weapon.Traits do
			local t = Config.Traits[id]
			local v = t and (t :: any)[field]
			if type(v) == "number" then
				mult *= v
			end
		end
	end
	return mult
end

function WeaponMath.Damage(weapon: WeaponLike): number
	local wt = Config.Weapons[weapon.Type]
	local ore = Config.Ores[weapon.Ore]
	if not wt or not ore then
		return 1
	end
	local rarity = Config.Rarities[weapon.Rarity]
	local rarityMult = if rarity then rarity.DamageMult else 1
	local raw = wt.BaseDamage * ore.PowerMult * WeaponMath.QualityMult(weapon.Quality) * rarityMult
		* traitProduct(weapon, "DamageMult")
	return math.max(1, math.floor(raw + 0.5))
end

function WeaponMath.Cooldown(weapon: WeaponLike?): number
	if not weapon then
		return Config.Combat.Fists.AttackCooldown
	end
	local wt = Config.Weapons[weapon.Type]
	if not wt then
		return Config.Combat.Fists.AttackCooldown
	end
	return wt.AttackCooldown * traitProduct(weapon, "CooldownMult")
end

function WeaponMath.SellPrice(weapon: WeaponLike): number
	local wt = Config.Weapons[weapon.Type]
	local ore = Config.Ores[weapon.Ore]
	if not wt or not ore then
		return 1
	end
	local rarity = Config.Rarities[weapon.Rarity]
	local rarityMult = if rarity then rarity.SellMult else 1
	local raw = ore.SellPrice
		* wt.OreCost
		* wt.SellMult
		* Config.Economy.WeaponSellBonus
		* WeaponMath.QualityMult(weapon.Quality)
		* rarityMult
	return math.max(1, math.floor(raw + 0.5))
end

-- Превью урона до ковки (Common, без черт)
function WeaponMath.Preview(weaponType: string, oreId: string, quality: number): number
	return WeaponMath.Damage({ Type = weaponType, Ore = oreId, Quality = quality, Rarity = "Common" })
end

-- ===== Редкости =====

function WeaponMath.SortedRarities(): { any }
	local list = {}
	for _, r in Config.Rarities do
		table.insert(list, r)
	end
	table.sort(list, function(a, b)
		return a.Order < b.Order
	end)
	return list
end

-- Шансы редкостей: { { Id, Chance (0..1) } } по возрастанию редкости
function WeaponMath.RarityOdds(quality: number, oreId: string): { { Id: string, Chance: number } }
	local ore = Config.Ores[oreId]
	local luck = 1 + math.clamp(quality, 0, 100) / 100 * Config.Forging.RarityLuckPerQuality + (if ore then ore.RarityLuck else 0)
	local list = WeaponMath.SortedRarities()
	local total = 0
	local weights = {}
	for i, r in list do
		local w = r.Weight * luck ^ (r.Order - 1)
		weights[i] = w
		total += w
	end
	local odds = {}
	for i, r in list do
		table.insert(odds, { Id = r.Id, Chance = weights[i] / total })
	end
	return odds
end

function WeaponMath.RarityAtLeast(rarityId: string, minId: string): boolean
	local r = Config.Rarities[rarityId]
	local m = Config.Rarities[minId]
	return r ~= nil and m ~= nil and r.Order >= m.Order
end

-- ===== Уровни и ранги =====

function WeaponMath.XpToNext(level: number): number
	local l = Config.Leveling
	return math.floor(l.XpBase * level ^ l.XpExponent)
end

function WeaponMath.RankFor(level: number): any
	local best = Config.Ranks[1]
	for _, rank in Config.Ranks do
		if level >= rank.MinLevel then
			best = rank
		end
	end
	return best
end

function WeaponMath.RankById(id: string): any
	for _, rank in Config.Ranks do
		if rank.Id == id then
			return rank
		end
	end
	return Config.Ranks[1]
end

function WeaponMath.LevelDamageMult(level: number): number
	return (1 + (level - 1) * Config.Leveling.DamagePerLevel) * (1 + WeaponMath.RankFor(level).DamageBonus)
end

function WeaponMath.MaxHealth(level: number): number
	local l = Config.Leveling
	return l.BaseHealth + (level - 1) * l.HealthPerLevel
end

return WeaponMath
