--!strict
--[[
	Локализатор. Используется на клиенте (весь текст интерфейса) и на сервере
	(только чтобы проверить, что язык допустим).

	Localization.Get("UI_LEVEL", { level = 5 }) → "Lv. 5" / "Ур. 5"
	Аргумент вида "@ORE_COPPER" переводится как ключ, "@WEAPON:Sword:Copper" — как название оружия.
]]

local Signal = require(script.Parent.Signal)
local Strings = require(script.Strings)

local Localization = {}

Localization.Supported = { "en", "ru" }
Localization.Changed = Signal.new()

local current = "en"

function Localization.IsSupported(lang: string): boolean
	return table.find(Localization.Supported, lang) ~= nil
end

-- "ru-ru" → "ru"; языки СНГ без своей таблицы тоже получают RU
function Localization.Detect(localeId: string): string
	local prefix = string.lower(string.sub(localeId, 1, 2))
	if prefix == "ru" or prefix == "uk" or prefix == "be" or prefix == "kk" then
		return "ru"
	end
	return "en"
end

function Localization.GetLanguage(): string
	return current
end

function Localization.SetLanguage(lang: string)
	if not Localization.IsSupported(lang) or lang == current then
		return
	end
	current = lang
	Localization.Changed:Fire(lang)
end

local function raw(key: string): string
	local tbl = (Strings :: any)[current]
	local value = tbl and tbl[key]
	if value == nil then
		value = Strings.en[key]
	end
	return if value ~= nil then value else key
end

function Localization.Get(key: string, args: { [string]: any }?): string
	local text = raw(key)
	if args then
		text = string.gsub(text, "{(%w+)}", function(name: string): string?
			local v = args[name]
			if v == nil then
				return nil -- оставить {name} как есть
			end
			if type(v) == "string" and string.sub(v, 1, 1) == "@" then
				local ref = string.sub(v, 2)
				-- "@WEAPON:Sword:Copper" → название оружия на языке игрока
				local wType, wOre = string.match(ref, "^WEAPON:(%w+):(%w+)$")
				if wType and wOre then
					return Localization.WeaponName(wType, wOre)
				end
				return raw(ref)
			end
			if type(v) == "number" then
				if v == math.floor(v) then
					return tostring(v)
				end
				return string.format("%.1f", v)
			end
			return tostring(v)
		end)
	end
	return text
end

-- Название оружия: "Copper Sword" / "Меч · медь"
function Localization.WeaponName(weaponType: string, oreId: string): string
	return Localization.Get("WEAPON_NAME", {
		type = "@WPN_" .. string.upper(weaponType),
		mat = "@MAT_" .. string.upper(oreId),
	})
end

return Localization
