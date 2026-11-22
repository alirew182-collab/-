--!strict
-- Проверка аргументов, пришедших от клиента. Клиенту НЕ доверяем ничего.

local Guards = {}

function Guards.String(v: any, maxLen: number?): boolean
	return type(v) == "string" and #v > 0 and #v <= (maxLen or 64)
end

function Guards.Number(v: any, min: number?, max: number?): boolean
	if type(v) ~= "number" or v ~= v or v == math.huge or v == -math.huge then
		return false
	end
	if min and v < min then
		return false
	end
	if max and v > max then
		return false
	end
	return true
end

function Guards.Integer(v: any, min: number?, max: number?): boolean
	return Guards.Number(v, min, max) and math.floor(v) == v
end

function Guards.Boolean(v: any): boolean
	return type(v) == "boolean"
end

-- Строгий массив: ключи 1..n без дыр и лишних полей
local function isArray(v: any, maxCount: number): boolean
	if type(v) ~= "table" then
		return false
	end
	local count = 0
	for k in v do
		if type(k) ~= "number" then
			return false
		end
		count += 1
		if count > maxCount then
			return false
		end
	end
	return count == #v
end

function Guards.StringArray(v: any, maxCount: number, maxLen: number?): boolean
	if not isArray(v, maxCount) then
		return false
	end
	for _, item in v do
		if not Guards.String(item, maxLen) then
			return false
		end
	end
	return true
end

function Guards.NumberArray(v: any, maxCount: number, min: number?, max: number?): boolean
	if not isArray(v, maxCount) then
		return false
	end
	for _, item in v do
		if not Guards.Number(item, min, max) then
			return false
		end
	end
	return true
end

return Guards
