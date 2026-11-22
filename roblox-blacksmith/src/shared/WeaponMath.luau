--!strict
-- Формулы оружия: одинаковые на клиенте (превью) и сервере (истина).

local Config = require(script.Parent.Config)

local WeaponMath = {}

function WeaponMath.QualityMult(quality: number): number
	local f = Config.Forging
	local q = math.clamp(quality, 0, 100) / 100
	return f.QualityMultMin + (f.QualityMultMax - f.QualityMultMin) * q
end

function WeaponMath.Damage(weapon: { Type: string, Ore: string, Quality: number, Rarity: string }): number
	local wt = Config.Weapons[weapon.Type]
	local ore = Config.Ores[weapon.Ore]
	if not wt or not ore then
		return 1
	end
	local rarity = Config.Rarities[weapon.Rarity]
	local rarityMult = if rarity then rarity.DamageMult else 1
	return math.max(1, math.floor(wt.BaseDamage * ore.PowerMult * WeaponMath.QualityMult(weapon.Quality) * rarityMult + 0.5))
end

function WeaponMath.SellPrice(weapon: { Type: string, Ore: string, Quality: number, Rarity: string }): number
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

-- Превью урона до ковки (средний результат при заданном качестве)
function WeaponMath.Preview(weaponType: string, oreId: string, quality: number): number
	return WeaponMath.Damage({ Type = weaponType, Ore = oreId, Quality = quality, Rarity = "Common" })
end

function WeaponMath.XpToNext(level: number): number
	local l = Config.Leveling
	return math.floor(l.XpBase * level ^ l.XpExponent)
end

function WeaponMath.LevelDamageMult(level: number): number
	return 1 + (level - 1) * Config.Leveling.DamagePerLevel
end

function WeaponMath.MaxHealth(level: number): number
	local l = Config.Leveling
	return l.BaseHealth + (level - 1) * l.HealthPerLevel
end

return WeaponMath
