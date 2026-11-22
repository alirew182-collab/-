--!strict
--[[
	ГЕНЕРАТОР МИРА В СТАД-СТИЛЕ (правила скилла Studify, RoClicks).
	Всё из деталей Plastic с узором шипов, без Terrain и без гладких материалов.
	Строится один раз при старте сервера, если в карте нет своих объектов с тегом OreNode.
	Детерминированный (Config.World.Seed): одинаковый мир на всех серверах.

	Раскладка (не менялась с прошлых этапов, это наш согласованный блок-аут):
	  Угольный Оплот (0, 0)        — площадь, фонтан с Кристаллом Системы, кузня, лавка, доски, Торговый ряд
	  Медные холмы (-200, 20)      — холмы из лососевой земли с травяными шапками, деревья, медь
	  Карьер Железный Клык (10,-210) — лавандовый камень, каменное кольцо с проходом, вагонетки, железо
	  Грот лунного серебра (210,-10) — зимний рескин: снег, морозные ели, ледяные кристаллы, лунное серебро
	  Равнина Врат (0, 210)        — каменные площадки для порталов
	  Озеро (130, 125), дорожки из плиток, кольцо плато по краю карты.

	Контракты с игровыми сервисами (теги и имена) те же, что раньше: Forge, Merchant, Leaderboard,
	GateSpawn, OreNode (+OreId), ShopStall (+Slot, Counter, Pedestal1..3, Owner), Spin.
	Все детали мира — пустые для игроков визуально, кроме того, что в папке Ground (по ней ходят).
]]

local CollectionService = game:GetService("CollectionService")
local Lighting = game:GetService("Lighting")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Stud = require(script.Parent.Stud)

local W = Config.World
local T = Config.Tags
local C = Stud.Color
local brick = Stud.brick
local model = Stud.model

local WorldBuilder = {}

WorldBuilder.Built = false -- true, если мир построен кодом (зоны совпадают с Config.World)

local rng = Random.new(W.Seed)
local root: Folder
local ground: Folder -- всё, по чему ходят (для GroundAt)

local function deg(d: number): number
	return math.rad(d)
end

local function yaw15(): number
	return deg(15 * rng:NextInteger(0, 23))
end

-- ===== Земля =====

local rayParams = RaycastParams.new()
rayParams.FilterType = Enum.RaycastFilterType.Include

local function groundAt(x: number, z: number): number
	local hit = workspace:Raycast(Vector3.new(x, 250, z), Vector3.new(0, -500, 0), rayParams)
	return if hit then hit.Position.Y else 0
end

local function groundPos(x: number, z: number): Vector3
	return Vector3.new(x, groundAt(x, z), z)
end

-- Круг из горизонтальных полос: ступенчатый контур вместо гладкого (стиль «всё террасами»)
local function disc(parent: Instance, cx: number, cz: number, radius: number, bottom: number, thick: number, color: Color3, options: Stud.Options?)
	local strips = math.max(6, math.floor(radius / 6))
	local band = 2 * radius / strips
	for i = 0, strips - 1 do
		local zc = -radius + band * (i + 0.5)
		local half = math.floor(math.sqrt(math.max(0, radius * radius - zc * zc)) / 2) * 2
		if half >= 2 then
			brick(parent, Vector3.new(half * 2, thick, band), CFrame.new(cx, bottom + thick / 2, cz + zc), color, options)
		end
	end
end

local function inDisc(x: number, z: number, cx: number, cz: number, r: number): boolean
	return (x - cx) ^ 2 + (z - cz) ^ 2 <= r * r
end

-- ===== Торговый ряд: координаты =====

local MARKET_X0 = 48
local MARKET_STEP = 14
local MARKET_ROW_A = 44 -- прилавки смотрят на +Z
local MARKET_ROW_B = 76 -- прилавки смотрят на −Z
local PLAZA_R = 30

local function inMarket(x: number, z: number): boolean
	return x > MARKET_X0 - 12 and x < MARKET_X0 + MARKET_STEP * 8 + 4 and z > MARKET_ROW_A - 10 and z < MARKET_ROW_B + 10
end

local function nearRoad(x: number, z: number, width: number): boolean
	for _, zone in W.Zones do
		if zone.Id ~= "Town" then
			local dir = Vector3.new(zone.Center.X, 0, zone.Center.Z)
			local len = dir.Magnitude
			local unit = dir.Unit
			local point = Vector3.new(x, 0, z)
			local t = math.clamp(point:Dot(unit), 0, len)
			if (point - unit * t).Magnitude < width then
				return true
			end
		end
	end
	return false
end

-- Занятые места: разные объекты не должны пересекаться (дерево не растёт сквозь кузню)
local reserved: { { X: number, Z: number, R: number } } = {}

local function reserve(x: number, z: number, r: number)
	table.insert(reserved, { X = x, Z = z, R = r })
end

local function free(x: number, z: number, r: number): boolean
	for _, o in reserved do
		if (o.X - x) ^ 2 + (o.Z - z) ^ 2 < (o.R + r) ^ 2 then
			return false
		end
	end
	return true
end

-- свободно и не на дороге/рынке; при успехе место занимается
local function claim(x: number, z: number, r: number, roadGap: number): boolean
	if not free(x, z, r) or inMarket(x, z) or nearRoad(x, z, roadGap) then
		return false
	end
	reserve(x, z, r)
	return true
end

local function randomInZone(zone: any, minR: number, maxR: number): (number, number)
	local angle = rng:NextNumber(0, math.pi * 2)
	local r = rng:NextNumber(minR, maxR)
	return zone.Center.X + math.cos(angle) * r, zone.Center.Z + math.sin(angle) * r
end

-- ===== Мелкие помощники =====

local function light(parent: BasePart, color: Color3, range: number, brightness: number)
	local l = Instance.new("PointLight")
	l.Color = color
	l.Range = range
	l.Brightness = brightness
	l.Shadows = false
	l.Parent = parent
end

-- Подпись над зданием. Текст переводит клиент по атрибуту LocKey.
local function sign(parent: BasePart, locKey: string, height: number)
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(180, 36)
	gui.StudsOffsetWorldSpace = Vector3.new(0, height, 0)
	gui.MaxDistance = 140
	gui.LightInfluence = 0
	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.TextScaled = true
	label.Font = Enum.Font.GothamBold
	label.Text = locKey
	label:SetAttribute("LocKey", locKey)
	label.TextColor3 = C.White
	label.TextStrokeColor3 = Color3.fromRGB(40, 30, 30)
	label.TextStrokeTransparency = 0.2
	label.Parent = gui
	gui.Parent = parent
end

-- Ориентация «лицом» (локальная −Z) к точке, с шагом 15°
local function facing(pos: Vector3, target: Vector3): CFrame
	local d = target - pos
	local yaw = math.atan2(-d.X, -d.Z)
	yaw = math.rad(math.floor(math.deg(yaw) / 15 + 0.5) * 15)
	return CFrame.new(pos) * CFrame.Angles(0, yaw, 0)
end

-- ===== Природа =====

-- Пучок травы: 3-4 травинки веером с наклоном 15°
local function tuft(parent: Instance, pos: Vector3)
	local m = model("Tuft", parent)
	local n = rng:NextInteger(3, 4)
	local base = yaw15()
	for i = 1, n do
		local h = rng:NextNumber(0.8, 1.5)
		local lean = if i % 2 == 0 then 15 else -15
		brick(
			m,
			Vector3.new(0.3, h, 0.3),
			CFrame.new(pos) * CFrame.Angles(0, base + deg(i * 60), 0) * CFrame.Angles(deg(lean), 0, 0) * CFrame.new(0, h / 2, 0),
			C.Tuft,
			{ Pattern = "Studs", Collide = false, Shadow = false }
		)
	end
end

-- Плоский цветок «плюсом»: серединка и 4 лепестка
local function flower(parent: Instance, pos: Vector3, petal: Color3)
	local m = model("Flower", parent)
	local y = pos.Y + 0.15
	brick(m, Vector3.new(0.6, 0.3, 0.6), CFrame.new(pos.X, y + 0.05, pos.Z), C.Gold, { Collide = false, Shadow = false })
	for _, o in { Vector3.new(0.6, 0, 0), Vector3.new(-0.6, 0, 0), Vector3.new(0, 0, 0.6), Vector3.new(0, 0, -0.6) } do
		brick(m, Vector3.new(0.6, 0.3, 0.6), CFrame.new(pos.X + o.X, y, pos.Z + o.Z), petal, { Collide = false, Shadow = false })
	end
end

local FLOWER_COLORS = { C.Red, C.White, Color3.fromRGB(240, 120, 200), Color3.fromRGB(140, 120, 255) }

local function scatter(parent: Instance, zone: any, minR: number, maxR: number, tufts: number, flowers: number)
	for _ = 1, tufts do
		local x, z = randomInZone(zone, minR, maxR)
		if not nearRoad(x, z, 6) and not inMarket(x, z) and free(x, z, 1) then
			tuft(parent, groundPos(x, z))
		end
	end
	for _ = 1, flowers do
		local x, z = randomInZone(zone, minR, maxR)
		if not nearRoad(x, z, 6) and not inMarket(x, z) and free(x, z, 1) then
			flower(parent, groundPos(x, z), FLOWER_COLORS[rng:NextInteger(1, #FLOWER_COLORS)])
		end
	end
end

-- Дерево-«блоб» (воксельная сфера на высоком коралловом стволе, корни растут из ствола)
local function blobTree(parent: Instance, pos: Vector3, scale: number, leaf: Color3, leaf2: Color3)
	local m = model("Tree", parent)
	local trunkH = 9 * scale
	local segH = trunkH / 4
	local tw = 2.8 * scale
	for i = 0, 3 do
		local off = if i % 2 == 0 then 0.2 else -0.2
		brick(
			m,
			Vector3.new(tw, segH + 0.3, tw),
			CFrame.new(pos + Vector3.new(off, segH * (i + 0.5), -off)),
			if i % 2 == 0 then C.Coral else C.CoralDark,
			{ Pattern = "Weld" }
		)
	end
	-- корни: из боковины ствола на высоте ~2 вниз под 45°, с «носком» на земле
	for i = 1, 3 do
		local a = deg(120 * i + 30)
		local dir = Vector3.new(math.cos(a), 0, math.sin(a))
		local start = pos + dir * (tw / 2) + Vector3.new(0, 2, 0)
		local toe = pos + dir * (tw / 2 + 1.8) + Vector3.new(0, 0.3, 0)
		local mid = (start + toe) / 2
		brick(m, Vector3.new(1, 1, (start - toe).Magnitude + 0.4), CFrame.lookAt(mid, toe), C.CoralDark, { Pattern = "Weld", Collide = false })
		brick(m, Vector3.new(1.4, 0.6, 1.4), CFrame.new(toe), C.Coral, { Pattern = "Weld", Collide = false })
	end
	-- крона: 6 ярусов по округлому профилю + выступы
	local maxW = 10 * scale
	local profile = { 0.55, 0.85, 1.0, 0.95, 0.75, 0.45 }
	local y0 = pos.Y + trunkH - 1
	for i, k in profile do
		local w = maxW * k
		local cy = y0 + (i - 0.5) * 1.6 * scale
		local jx, jz = rng:NextNumber(-0.8, 0.8), rng:NextNumber(-0.8, 0.8)
		brick(m, Vector3.new(w, 1.6 * scale, w), CFrame.new(pos.X + jx, cy, pos.Z + jz), leaf, { Pattern = "StudsNoGrid", Collide = false })
		if i >= 2 and i <= 5 then
			for _ = 1, 2 do
				local a = rng:NextNumber(0, math.pi * 2)
				local b = rng:NextNumber(1.6, 2.8) * scale
				local r = w / 2
				brick(
					m,
					Vector3.new(b, b * 0.8, b),
					CFrame.new(pos.X + jx + math.cos(a) * r, cy, pos.Z + jz + math.sin(a) * r),
					if rng:NextNumber() < 0.4 then leaf2 else leaf,
					{ Pattern = "StudsNoGrid", Collide = false }
				)
			end
		end
	end
	-- ветка: три звена, каждое поднимается круче и уходит в низ кроны
	local p = pos + Vector3.new(tw / 2, trunkH * 0.6, 0)
	local dir = Vector3.new(1, 0.4, 0.2).Unit
	for i = 1, 3 do
		local nextP = p + dir * (1.8 * scale)
		brick(
			m,
			Vector3.new(0.9 - i * 0.15, 0.9 - i * 0.15, (nextP - p).Magnitude + 0.3),
			CFrame.lookAt((p + nextP) / 2, nextP),
			if i % 2 == 0 then C.Coral else C.CoralDark,
			{ Pattern = "Weld", Collide = false }
		)
		p = nextP
		dir = (dir + Vector3.new(0, 0.45, 0)).Unit
	end
	m.PrimaryPart = m:FindFirstChildWhichIsA("BasePart")
end

-- Ярусная ель (зимний рескин): ярусы 8,5,7,4,5,2 по центру ствола, снежные шапки
local function pineTree(parent: Instance, pos: Vector3, scale: number)
	local m = model("FrostPine", parent)
	local trunkH = 3 * scale
	brick(m, Vector3.new(1.6 * scale, trunkH, 1.6 * scale), CFrame.new(pos + Vector3.new(0, trunkH / 2, 0)), C.WoodDark, { Pattern = "Weld" })
	local widths = { 8, 5, 7, 4, 5, 2 }
	local y = pos.Y + trunkH
	for i, w in widths do
		local ww = w * scale
		local h = 1.4 * scale
		brick(m, Vector3.new(ww, h, ww), CFrame.new(pos.X, y + h / 2, pos.Z), if i % 2 == 0 then C.Frost else C.Ice, { Pattern = "StudsNoGrid", Collide = i == 1 })
		brick(m, Vector3.new(ww * 0.8, 0.3, ww * 0.8), CFrame.new(pos.X, y + h + 0.15, pos.Z), C.Snow, { Pattern = "StudsNoGrid", Collide = false })
		y += h
	end
end

-- Камень: 2-3 плиты с разным поворотом на 15°, узор Inlet только сверху
local function rock(parent: Instance, pos: Vector3, scale: number, tones: { Color3 }): Model
	local m = model("Rock", parent)
	local n = rng:NextInteger(2, 3)
	local y = pos.Y
	for i = 1, n do
		local w = rng:NextNumber(2.6, 4.2) * scale * (1 - (i - 1) * 0.25)
		local h = rng:NextNumber(1.2, 2) * scale
		local d = rng:NextNumber(2.4, 3.8) * scale * (1 - (i - 1) * 0.25)
		brick(
			m,
			Vector3.new(w, h, d),
			CFrame.new(pos.X + rng:NextNumber(-0.4, 0.4), y + h / 2, pos.Z + rng:NextNumber(-0.4, 0.4)) * CFrame.Angles(0, yaw15(), 0),
			tones[rng:NextInteger(1, #tones)],
			{ Rock = true }
		)
		y += h * 0.85
	end
	m.PrimaryPart = m:FindFirstChildWhichIsA("BasePart")
	return m
end

-- Холм: 2-4 перекрывающихся блока земли с травяными шапками (растёт из земли, а не лежит на ней)
local function hill(parent: Instance, pos: Vector3)
	local m = model("Hill", parent)
	local n = rng:NextInteger(2, 4)
	for i = 1, n do
		local w = rng:NextNumber(12, 26)
		local d = rng:NextNumber(10, 22)
		local h = rng:NextNumber(3, 8) * (if i == 1 then 1.2 else 0.8)
		local yaw = deg(15 * rng:NextInteger(0, 2))
		local c = CFrame.new(pos + Vector3.new(rng:NextNumber(-8, 8), 0, rng:NextNumber(-8, 8))) * CFrame.Angles(0, yaw, 0)
		brick(m, Vector3.new(w, h, d), c * CFrame.new(0, h / 2, 0), if i % 2 == 0 then C.DirtRose else C.Dirt)
		brick(m, Vector3.new(w + 2, 1.6, d + 2), c * CFrame.new(0, h + 0.8, 0), C.Grass, { Pattern = "StudsNoGrid" })
	end
	m.Parent = ground
end

-- Плато края карты: 2-3 уменьшающихся яруса, у каждого травяная шапка и «нос»
local function plateau(parent: Instance, center: CFrame, length: number, depth: number, totalH: number, darkLower: boolean): number
	local m = model("Plateau", parent)
	local tiers = if totalH >= 22 then 3 else 2
	local y = 0
	local l, d = length, depth
	for i = 1, tiers do
		local h = totalH / tiers
		local off = CFrame.new(rng:NextNumber(-l * 0.08, l * 0.08), 0, rng:NextNumber(-d * 0.1, d * 0.1))
		local c = center * off
		local tone = if i == 1 and darkLower then C.DirtDeep elseif i % 2 == 0 then C.DirtRose else C.Dirt
		brick(m, Vector3.new(l, h, d), c * CFrame.new(0, y + h / 2, 0), tone)
		brick(m, Vector3.new(l + 2, 1.4, d + 2), c * CFrame.new(0, y + h + 0.7, 0), C.Grass, { Pattern = "StudsNoGrid" })
		-- нос: небольшой выступ, ломающий прямой край
		brick(m, Vector3.new(4, h * 0.6, 3), c * CFrame.new(rng:NextNumber(-l / 3, l / 3), y + h * 0.3, -d / 2 - 1.4), tone)
		y += h
		l *= rng:NextNumber(0.6, 0.75)
		d *= rng:NextNumber(0.6, 0.75)
	end
	m.Parent = ground
	return y + 1.4
end

-- ===== Жила руды =====

local function oreNode(parent: Instance, position: Vector3, oreId: string)
	local ore = Config.Ores[oreId]
	local tones = if oreId == "Moonsilver" then { C.StoneLight, C.Frost } else { C.Stone, C.StoneDark }
	local m = rock(parent, position, 1.3, tones)
	m.Name = oreId .. "Node"
	local main = m.PrimaryPart :: BasePart
	for i = 1, 3 do
		local a = deg(120 * i + rng:NextInteger(0, 2) * 15)
		local r = main.Size.X / 2
		brick(
			m,
			Vector3.new(1.2, rng:NextNumber(1.4, 2.2), 1.2),
			CFrame.new(main.Position + Vector3.new(math.cos(a) * r, rng:NextNumber(0, 1), math.sin(a) * r)) * CFrame.Angles(0, a, deg(15)),
			ore.Color,
			{ Neon = oreId == "Moonsilver", Pattern = "Studs", Collide = false }
		)
	end
	m:SetAttribute("OreId", oreId)
	m:SetAttribute("AutoStyle", false)
	CollectionService:AddTag(m, T.OreNode)
end

-- ===== Город =====

local function lamp(parent: Instance, pos: Vector3)
	local m = model("Lamp", parent)
	brick(m, Vector3.new(1.6, 0.6, 1.6), CFrame.new(pos + Vector3.new(0, 0.3, 0)), C.StoneDark)
	brick(m, Vector3.new(0.8, 8, 0.8), CFrame.new(pos + Vector3.new(0, 4.6, 0)), C.WoodDark, { Pattern = "Weld" })
	brick(m, Vector3.new(1.4, 1.6, 1.4), CFrame.new(pos + Vector3.new(0, 9.4, 0)), C.Iron, { Pattern = "Weld" })
	local core = brick(m, Vector3.new(1, 1, 1.5), CFrame.new(pos + Vector3.new(0, 9.4, 0)), Color3.fromRGB(255, 214, 150), { Neon = true, Collide = false })
	brick(m, Vector3.new(1.8, 0.4, 1.8), CFrame.new(pos + Vector3.new(0, 10.4, 0)), C.Iron, { Pattern = "Weld" })
	light(core, Color3.fromRGB(255, 200, 140), 18, 1.2)
end

local function crate(parent: Instance, cf: CFrame, size: number)
	local m = model("Crate", parent)
	brick(m, Vector3.new(size, size, size), cf, C.Gold, { Pattern = "Weld" })
	local h = size / 2
	for _, x in { -h, h } do
		for _, z in { -h, h } do
			brick(m, Vector3.new(0.4, size + 0.1, 0.4), cf * CFrame.new(x, 0, z), C.WoodDark, { Pattern = "Weld" })
		end
	end
	brick(m, Vector3.new(size + 0.1, 0.4, size + 0.1), cf * CFrame.new(0, h, 0), C.WoodDark, { Pattern = "Weld" })
end

local function buildSmithy(parent: Instance, pos: Vector3)
	local m = model("Smithy", parent)
	local b = facing(pos, Vector3.new(0, pos.Y, 0))
	local function at(x: number, y: number, z: number): CFrame
		return b * CFrame.new(x, y, z)
	end
	-- основание и ступень
	brick(m, Vector3.new(24, 1, 18), at(0, 0.5, 0), C.StoneDark, { Pattern = "StudsNoGrid" })
	brick(m, Vector3.new(10, 0.5, 2), at(0, 0.25, -10), C.Stone)
	-- задняя стена с поясом и окном-нишей
	brick(m, Vector3.new(24, 11, 1), at(0, 6.5, 8.5), C.Dirt, { Pattern = "Studs" })
	brick(m, Vector3.new(24.4, 0.6, 1.4), at(0, 5, 8.5), C.DirtDeep, { Pattern = "Weld" })
	-- боковые полустены
	for _, x in { -11.5, 11.5 } do
		brick(m, Vector3.new(1, 4, 16), at(x, 3, 0.5), C.Dirt, { Pattern = "Studs" })
		brick(m, Vector3.new(1.4, 0.6, 16.4), at(x, 5.3, 0.5), C.DirtDeep, { Pattern = "Weld" })
	end
	-- столбы и балка фасада
	for _, x in { -11.6, 11.6 } do
		for _, z in { -8.6, 8.6 } do
			brick(m, Vector3.new(1.2, 12, 1.2), at(x, 7, z), C.Wood, { Pattern = "Weld" })
		end
	end
	brick(m, Vector3.new(24.6, 1, 1), at(0, 12.6, -8.6), C.WoodDark, { Pattern = "Weld" })
	-- ступенчатая крыша
	for i = 1, 5 do
		local w = 27 - (i - 1) * 3
		local d = 18 - (i - 1) * 3
		brick(m, Vector3.new(w, 1.2, d), at(0, 13.7 + (i - 1) * 1.2, 0), if i % 2 == 0 then C.RoofDark else C.Roof, { Pattern = "StudsNoGrid" })
	end
	-- труба за задней стеной, тлеющая сверху
	brick(m, Vector3.new(3, 22, 3), at(-7, 11, 10.5), C.DirtDeep, { Pattern = "Studs" })
	brick(m, Vector3.new(3.6, 0.6, 3.6), at(-7, 22.3, 10.5), C.StoneDark, { Pattern = "Weld" })
	local ember = brick(m, Vector3.new(2, 0.3, 2), at(-7, 22.7, 10.5), C.Ember, { Neon = true, Collide = false })
	local smoke = Instance.new("ParticleEmitter")
	smoke.Rate = 4
	smoke.Lifetime = NumberRange.new(2, 3)
	smoke.Speed = NumberRange.new(2, 3)
	smoke.Size = NumberSequence.new(1.5, 4)
	smoke.Transparency = NumberSequence.new(0.4, 1)
	smoke.Color = ColorSequence.new(Color3.fromRGB(120, 120, 130))
	smoke.Parent = ember
	-- горн: главный «светящийся» момент сцены
	brick(m, Vector3.new(7, 1, 5), at(-7, 1.5, 5.5), C.StoneDark)
	brick(m, Vector3.new(6, 5, 4), at(-7, 4.5, 5.5), C.RoofDark, { Pattern = "Studs" })
	brick(m, Vector3.new(6.6, 0.8, 4.6), at(-7, 7.4, 5.5), C.StoneDark, { Pattern = "Weld" })
	local fire = brick(m, Vector3.new(3, 2, 0.6), at(-7, 3.6, 3.4), C.Ember, { Neon = true, Collide = false })
	light(fire, Color3.fromRGB(255, 140, 60), 20, 2.5)
	local sparks = Instance.new("ParticleEmitter")
	sparks.Rate = 10
	sparks.Lifetime = NumberRange.new(0.5, 1)
	sparks.Speed = NumberRange.new(2, 4)
	sparks.Color = ColorSequence.new(Color3.fromRGB(255, 190, 80), Color3.fromRGB(255, 80, 20))
	sparks.Size = NumberSequence.new(0.4, 0)
	sparks.LightEmission = 1
	sparks.Parent = fire
	-- наковальня (тег Forge)
	local anvil = model("Anvil", m)
	brick(anvil, Vector3.new(3, 0.6, 2.2), at(2, 1.3, -1), C.Iron, { Pattern = "Weld" })
	brick(anvil, Vector3.new(1.6, 1.4, 1.2), at(2, 2.3, -1), C.Iron, { Pattern = "Weld" })
	local top = brick(anvil, Vector3.new(4, 0.8, 1.8), at(2, 3.4, -1), C.Iron, { Pattern = "Weld", Name = "Top" })
	brick(anvil, Vector3.new(1.2, 0.6, 1.2), at(4.6, 3.4, -1), C.Iron, { Pattern = "Weld" })
	brick(anvil, Vector3.new(0.8, 0.4, 0.8), at(5.6, 3.4, -1), C.Iron)
	anvil.PrimaryPart = top
	anvil.ModelStreamingMode = Enum.ModelStreamingMode.Persistent
	CollectionService:AddTag(anvil, T.Forge)
	sign(top, "UI_FORGE_TITLE", 6)
	-- бочка для закалки
	brick(m, Vector3.new(3, 2, 3), at(7, 2, 2), C.Wood, { Pattern = "Weld" })
	brick(m, Vector3.new(3.4, 0.4, 3.4), at(7, 2.6, 2), C.WoodDark, { Pattern = "Weld" })
	brick(m, Vector3.new(2.4, 0.2, 2.4), at(7, 3.05, 2), C.Water, { Collide = false })
	-- стойка с инструментами на задней стене
	brick(m, Vector3.new(7, 3, 0.4), at(5, 7, 7.8), C.Wood, { Pattern = "Weld" })
	for i = 1, 3 do
		local x = 2.6 + i * 1.6
		brick(m, Vector3.new(0.3, 2.2, 0.3), at(x, 6.6, 7.4), C.WoodDark)
		brick(m, Vector3.new(1, 0.6, 0.6), at(x, 7.8, 7.4), C.Iron, { Pattern = "Weld" })
	end
	-- изобретение: вагонетка с рассыпанной медью у входа
	local cart = model("OreCart", m)
	brick(cart, Vector3.new(4, 2, 3), at(-9, 2, -12), C.Iron, { Pattern = "Weld" })
	for _, x in { -1.4, 1.4 } do
		brick(cart, Vector3.new(0.6, 1, 3.4), at(-9 + x, 0.6, -12), C.WoodDark, { Pattern = "Weld" })
	end
	for i = 1, 6 do
		local cx = -9 + rng:NextNumber(-1.4, 1.4)
		local cz = -12 + rng:NextNumber(-1, 1)
		brick(cart, Vector3.new(0.8, 0.8, 0.8), at(cx, 3.2, cz) * CFrame.Angles(0, deg(15 * i), 0), Config.Ores.Copper.Color, { Collide = false })
	end
	for i = 1, 3 do
		brick(cart, Vector3.new(0.7, 0.5, 0.7), at(-6.5 + i * 0.9, 0.25, -13.2 + i * 0.4) * CFrame.Angles(0, deg(30 * i), 0), Config.Ores.Copper.Color, { Collide = false })
	end
end

local function buildMerchant(parent: Instance, pos: Vector3)
	local m = model("MerchantStall", parent)
	local b = facing(pos, Vector3.new(0, pos.Y, 0))
	local function at(x: number, y: number, z: number): CFrame
		return b * CFrame.new(x, y, z)
	end
	brick(m, Vector3.new(14, 0.6, 10), at(0, 0.3, 0), C.Wood, { Pattern = "StudsNoGrid" })
	for _, x in { -6.4, 6.4 } do
		for _, z in { -4.4, 4.4 } do
			brick(m, Vector3.new(0.9, 9, 0.9), at(x, 5.1, z), C.WoodDark, { Pattern = "Weld" })
		end
	end
	-- прилавок
	brick(m, Vector3.new(12, 3, 2), at(0, 2.1, -3.6), C.Wood, { Pattern = "Studs" })
	brick(m, Vector3.new(12.6, 0.4, 2.6), at(0, 3.8, -3.6), C.WoodDark, { Pattern = "Weld" })
	-- полки с товаром
	brick(m, Vector3.new(12, 6, 0.8), at(0, 3.6, 4), C.WoodDark, { Pattern = "Weld" })
	for row = 0, 1 do
		brick(m, Vector3.new(11, 0.4, 1.6), at(0, 2.6 + row * 2.4, 3.2), C.Wood, { Pattern = "Weld" })
		for i = 0, 4 do
			local color = ({ C.Red, C.Blue, C.Gold, Config.Ores.Iron.Color, Config.Ores.Copper.Color })[i + 1]
			brick(m, Vector3.new(1, 1.2, 1), at(-4 + i * 2, 3.4 + row * 2.4, 3.2), color, { Collide = false })
		end
	end
	-- полосатый навес и бахрома
	for i = 0, 6 do
		local x = -6 + i * 2
		local color = if i % 2 == 0 then C.Red else C.White
		brick(m, Vector3.new(2, 0.4, 11), at(x, 9.8, 0) * CFrame.Angles(deg(-15), 0, 0), color, { Pattern = "StudsNoGrid" })
		brick(m, Vector3.new(2, 1, 0.3), at(x, 8.6, -5.4), color, { Pattern = "Studs" })
	end
	-- продавец (тег Merchant): кубическая фигура, без шаров
	local npc = model("Merchant", m)
	brick(npc, Vector3.new(2, 3, 1.2), at(0, 2.1, 0.6), C.Blue, { Pattern = "Studs" })
	local body = brick(npc, Vector3.new(2.6, 3, 1.4), at(0, 5.1, 0.6), C.White, { Pattern = "Studs", Name = "Body" })
	brick(npc, Vector3.new(2.2, 2.4, 0.3), at(0, 4.8, -0.2), C.Gold, { Pattern = "Studs" })
	for _, x in { -1.8, 1.8 } do
		brick(npc, Vector3.new(0.9, 2.8, 0.9), at(x, 5, 0.6), C.White, { Pattern = "Studs" })
	end
	brick(npc, Vector3.new(2, 2, 2), at(0, 7.6, 0.6), Color3.fromRGB(240, 200, 160), { Pattern = "Studs" })
	brick(npc, Vector3.new(2.6, 0.4, 2.6), at(0, 8.8, 0.6), C.Red, { Pattern = "Weld" })
	brick(npc, Vector3.new(1.6, 1, 1.6), at(0, 9.5, 0.6), C.Red, { Pattern = "Weld" })
	npc.PrimaryPart = body
	npc.ModelStreamingMode = Enum.ModelStreamingMode.Persistent
	CollectionService:AddTag(npc, T.Merchant)
	sign(body, "UI_MERCHANT_TITLE", 6)
	-- ящики и бочка перед лавкой
	crate(m, at(-8.6, 1.1, -2), 2.2)
	crate(m, at(-8.6, 3.3, -2) * CFrame.Angles(0, deg(15), 0), 1.6)
	brick(m, Vector3.new(2.2, 2.6, 2.2), at(8.6, 1.3, -2), C.Wood, { Pattern = "Weld" })
	for _, y in { 0.6, 2 } do
		brick(m, Vector3.new(2.5, 0.3, 2.5), at(8.6, y, -2), C.Iron, { Pattern = "Weld" })
	end
end

local function buildLeaderboards(parent: Instance, origin: Vector3)
	local m = model("Leaderboards", parent)
	for i, board in { "Level", "Damage", "Gates" } do
		local pos = origin + Vector3.new((i - 2) * 15, 0, (i - 2) * 6)
		local b = facing(pos, Vector3.new(0, 0, 0))
		brick(m, Vector3.new(14, 0.6, 3), b * CFrame.new(0, 0.3, 0), C.StoneDark)
		for _, x in { -6.6, 6.6 } do
			brick(m, Vector3.new(1, 14, 1), b * CFrame.new(x, 7.6, 0), C.WoodDark, { Pattern = "Weld" })
		end
		local p = brick(m, Vector3.new(12, 12, 0.8), b * CFrame.new(0, 7.6, 0), Color3.fromRGB(38, 36, 33), { Pattern = "Studs", Name = board .. "Board" })
		brick(m, Vector3.new(15, 0.6, 2.4), b * CFrame.new(0, 14.9, 0), C.Roof, { Pattern = "StudsNoGrid" })
		brick(m, Vector3.new(12, 0.6, 1.6), b * CFrame.new(0, 15.5, 0), C.RoofDark, { Pattern = "StudsNoGrid" })
		p:SetAttribute("Board", board)
		CollectionService:AddTag(p, T.Leaderboard)
	end
	m.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
end

local function buildTown(parent: Instance)
	local town = model("Emberhold", parent)
	local floor = model("Plaza", ground)
	disc(floor, 0, 0, PLAZA_R, 0, 0.4, C.StoneLight, { Pattern = "Universal" })
	disc(floor, 0, 0, PLAZA_R - 6, 0, 0.5, C.Stone, { Pattern = "Universal" })
	local top = 0.5

	-- фонтан: ступенчатая квадратная чаша, вода, колонна, Кристалл Системы
	local f = model("Fountain", town)
	brick(f, Vector3.new(18, 0.6, 18), CFrame.new(0, top + 0.3, 0), C.StoneDark, { Pattern = "StudsNoGrid" })
	for _, side in { { 0, 8.2, 18, 1.6 }, { 0, -8.2, 18, 1.6 }, { 8.2, 0, 1.6, 15 }, { -8.2, 0, 1.6, 15 } } do
		brick(f, Vector3.new(side[3], 1.6, side[4]), CFrame.new(side[1], top + 1.4, side[2]), C.Stone, { Pattern = "Weld" })
	end
	brick(f, Vector3.new(14.9, 0.8, 14.9), CFrame.new(0, top + 1.2, 0), C.Water, { Transparency = 0.25, Collide = false })
	brick(f, Vector3.new(4, 2, 4), CFrame.new(0, top + 1.6, 0), C.StoneLight, { Pattern = "Weld" })
	brick(f, Vector3.new(3, 4, 3), CFrame.new(0, top + 4.6, 0), C.Stone, { Pattern = "Studs" })
	brick(f, Vector3.new(4, 0.6, 4), CFrame.new(0, top + 6.9, 0), C.StoneDark, { Pattern = "Weld" })
	local core = brick(f, Vector3.new(3.4, 6, 3.4), CFrame.new(0, top + 12, 0) * CFrame.Angles(0, deg(45), 0), C.SystemBlue, { Neon = true, Collide = false, Name = "SystemCrystal" })
	light(core, C.SystemBlue, 32, 2)
	CollectionService:AddTag(core, T.Spin)

	-- точка появления
	-- площадка утоплена в мощение: без щели над полом
	local spawnPad = brick(town, Vector3.new(10, 1.2, 10), CFrame.new(0, 0.6, 26), C.Stone, { Class = "SpawnLocation", Name = "TownSpawn", Pattern = "StudsNoGrid" }) :: any
	spawnPad.Duration = 0
	spawnPad.Neutral = true
	for _, side in { { 0, 5.3, 10.6, 0.6 }, { 0, -5.3, 10.6, 0.6 }, { 5.3, 0, 0.6, 10 }, { -5.3, 0, 0.6, 10 } } do
		brick(town, Vector3.new(side[3], 0.4, side[4]), CFrame.new(side[1], 1.4, 26 + side[2]), C.Gold, { Pattern = "Weld" })
	end

	reserve(0, 0, PLAZA_R)
	reserve(-40, -26, 17)
	reserve(40, -26, 11)
	reserve(34, -54, 24)
	buildSmithy(town, Vector3.new(-40, 0, -26))
	buildMerchant(town, Vector3.new(40, 0, -26))
	buildLeaderboards(town, Vector3.new(34, 0, -54))

	-- фонари вокруг площади (на траве, за краем мощения)
	for i = 0, 7 do
		local a = i / 8 * math.pi * 2 + math.pi / 8
		local x, z = math.cos(a) * (PLAZA_R + 4), math.sin(a) * (PLAZA_R + 4)
		if claim(x, z, 2, 5) then
			lamp(town, Vector3.new(x, 0, z))
		end
	end

	-- деревья кольцом вокруг города (немного и крупные)
	local zone = W.Zones.Town
	for i = 0, 9 do
		local a = i / 10 * math.pi * 2 + rng:NextNumber(-0.12, 0.12)
		local r = rng:NextNumber(50, 64)
		local x, z = math.cos(a) * r, math.sin(a) * r
		if claim(x, z, 7, 9) then
			blobTree(town, groundPos(x, z), rng:NextNumber(0.9, 1.15), C.Grass, C.GrassBright)
		end
	end
	scatter(town, zone, PLAZA_R + 2, 66, 30, 14)
end

-- ===== Торговый ряд =====

local STRIPES = {
	{ C.Red, C.White },
	{ C.Blue, C.White },
	{ C.Gold, C.White },
	{ C.Coral, C.White },
}

-- Прилавок игрока: тег ShopStall, атрибут Slot. ShopService назначает владельца,
-- пишет имя на вывеске (StallSign → Owner) и ставит до 3 предметов на подставки Pedestal1..3.
local function buildStall(parent: Instance, origin: Vector3, yaw: number, slot: number)
	local m = model("Stall" .. slot, parent)
	local look = CFrame.new(origin) * CFrame.Angles(0, yaw, 0)
	local function at(x: number, y: number, z: number): CFrame
		return look * CFrame.new(x, y, z)
	end
	local stripe = STRIPES[(slot - 1) % #STRIPES + 1]
	brick(m, Vector3.new(12, 0.6, 9), at(0, 0.3, 0), C.Wood, { Pattern = "StudsNoGrid" })
	brick(m, Vector3.new(12, 8, 0.6), at(0, 4.6, 4.2), C.WoodDark, { Pattern = "Weld" })
	for _, x in { -5.6, 5.6 } do
		brick(m, Vector3.new(0.8, 8.4, 0.8), at(x, 4.8, -4.2), C.WoodDark, { Pattern = "Weld" })
	end
	for i = 0, 5 do
		brick(m, Vector3.new(2.2, 0.4, 10), at(-5.5 + i * 2.2, 9.2, 0) * CFrame.Angles(deg(15), 0, 0), stripe[(i % 2) + 1], { Pattern = "StudsNoGrid" })
		brick(m, Vector3.new(2.2, 0.8, 0.3), at(-5.5 + i * 2.2, 8.2, -5), stripe[(i % 2) + 1], { Pattern = "Studs" })
	end
	local counter = brick(m, Vector3.new(10, 3, 2.4), at(0, 2.1, -2.8), C.Wood, { Pattern = "Studs", Name = "Counter" })
	brick(m, Vector3.new(10.6, 0.3, 3), at(0, 3.75, -2.8), C.WoodDark, { Pattern = "Weld" })
	for i = 1, 3 do
		brick(m, Vector3.new(1.6, 0.4, 1.6), at((i - 2) * 3.2, 4.1, -2.8), C.StoneDark, { Name = "Pedestal" .. i })
	end
	local signPart = brick(m, Vector3.new(1, 1, 1), at(0, 7.6, -4.4), C.White, { Transparency = 1, Collide = false, Name = "StallSign" })
	local gui = Instance.new("BillboardGui")
	gui.Name = "OwnerGui"
	gui.Size = UDim2.fromOffset(200, 32)
	gui.MaxDistance = 80
	gui.LightInfluence = 0
	local label = Instance.new("TextLabel")
	label.Name = "Owner"
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.TextScaled = true
	label.Font = Enum.Font.GothamBold
	label.TextColor3 = C.White
	label.TextStrokeColor3 = Color3.fromRGB(40, 30, 30)
	label.TextStrokeTransparency = 0.2
	label.Text = "UI_STALL_FREE"
	label:SetAttribute("LocKey", "UI_STALL_FREE")
	label.Parent = gui
	gui.Parent = signPart

	m.PrimaryPart = counter
	m:SetAttribute("Slot", slot)
	m.ModelStreamingMode = Enum.ModelStreamingMode.Persistent
	CollectionService:AddTag(m, T.ShopStall)
end

local function buildMarket(parent: Instance)
	local market = model("MarketRow", parent)
	-- улица: шахматка из плиток 8×8 (стиль «плиточные поля», края ступенчатые сами собой)
	local street = model("MarketStreet", ground)
	for ix = 0, 13 do
		for iz = 0, 4 do
			local x = MARKET_X0 - 6 + ix * 8 + 4
			local z = MARKET_ROW_A - 4 + iz * 8 + 4
			brick(street, Vector3.new(8, 0.3, 8), CFrame.new(x, 0.15, z), if (ix + iz) % 2 == 0 then C.Peach else C.PeachDark, { Pattern = "Universal" })
		end
	end
	local perRow = math.ceil(W.MarketStalls / 2)
	local slot = 0
	for row = 1, 2 do
		for i = 0, perRow - 1 do
			slot += 1
			if slot > W.MarketStalls then
				break
			end
			local x = MARKET_X0 + i * MARKET_STEP
			local z = if row == 1 then MARKET_ROW_A else MARKET_ROW_B
			buildStall(market, Vector3.new(x, 0.3, z), if row == 1 then math.pi else 0, slot)
		end
	end
	for _, x in { MARKET_X0 - 9, MARKET_X0 + MARKET_STEP * 7 + 9 } do
		lamp(market, Vector3.new(x, 0.3, 60))
	end
end

-- ===== Дорожки: поля плиток 4×4 вдоль линий =====

local function buildPaths()
	local paths = model("Paths", ground)
	local used: { [string]: boolean } = {}
	local function blocked(x: number, z: number): boolean
		if inDisc(x, z, 0, 0, PLAZA_R - 1) or inMarket(x, z) then
			return true
		end
		local Z = W.Zones
		return inDisc(x, z, Z.IronQuarry.Center.X, Z.IronQuarry.Center.Z, 80)
			or inDisc(x, z, Z.MoonGrotto.Center.X, Z.MoonGrotto.Center.Z, 80)
	end
	local function road(from: Vector3, to: Vector3, width: number)
		local len = (to - from).Magnitude
		local dir = (to - from).Unit
		local side = Vector3.new(-dir.Z, 0, dir.X)
		for t = 0, len, 2 do
			for s = -width / 2, width / 2, 2 do
				local sample = from + dir * t + side * s
				local cx, cz = math.floor(sample.X / 4), math.floor(sample.Z / 4)
				local key = cx .. ":" .. cz
				local x, z = cx * 4 + 2, cz * 4 + 2
				if not used[key] and not blocked(x, z) then
					used[key] = true
					brick(paths, Vector3.new(4, 0.3, 4), CFrame.new(x, 0.15, z), if (cx + cz) % 2 == 0 then C.Sand else C.Peach)
				end
			end
		end
	end
	for _, zone in W.Zones do
		if zone.Id ~= "Town" and zone.Id ~= "Market" then
			local to = Vector3.new(zone.Center.X, 0, zone.Center.Z)
			road(to.Unit * (PLAZA_R - 2), to - to.Unit * (zone.Radius * 0.55), 8)
		end
	end
	road(Vector3.new(20, 0, 22), Vector3.new(MARKET_X0 - 8, 0, 60), 8)
end

-- ===== Зоны =====

local function buildCopperHills(parent: Instance)
	local zone = W.Zones.CopperHills
	local m = model("CopperHills", parent)
	for _ = 1, 7 do
		local x, z = randomInZone(zone, 30, zone.Radius * 0.95)
		if claim(x, z, 22, 22) then
			hill(m, Vector3.new(x, 0, z))
		end
	end
	-- вытоптанные земляные пятна
	local patches = model("DirtPatches", ground)
	for _ = 1, 5 do
		local x, z = randomInZone(zone, 0, zone.Radius * 0.7)
		for k = 1, 3 do
			brick(patches, Vector3.new(rng:NextNumber(6, 12), 0.2, rng:NextNumber(5, 10)), CFrame.new(x + k * 2, 0.1, z + rng:NextNumber(-2, 2)), C.Dirt)
		end
	end
	for _ = 1, 8 do
		local x, z = randomInZone(zone, 15, zone.Radius * 0.9)
		local pos = groundPos(x, z)
		if pos.Y < 1 and claim(x, z, 7, 8) then
			blobTree(m, pos, rng:NextNumber(0.85, 1.2), if rng:NextNumber() < 0.5 then C.Grass else C.GrassBright, C.GrassDark)
		end
	end
	for _ = 1, 6 do
		local x, z = randomInZone(zone, 10, zone.Radius * 0.9)
		if claim(x, z, 3, 6) then
			rock(m, groundPos(x, z), rng:NextNumber(0.8, 1.2), { C.Dirt, C.DirtRose })
		end
	end
	scatter(m, zone, 5, zone.Radius * 0.95, 40, 16)
end

local function buildQuarry(parent: Instance)
	local zone = W.Zones.IronQuarry
	local m = model("IronQuarry", parent)
	local c = zone.Center
	disc(model("QuarryFloor", ground), c.X, c.Z, zone.Radius * 0.85, 0, 0.4, C.Stone)
	-- каменное кольцо из масс с проходом в сторону города
	local walls = model("QuarryWalls", parent)
	local toTown = math.atan2(-c.Z, -c.X)
	local n = 16
	for i = 0, n - 1 do
		local a = i / n * math.pi * 2
		local diff = math.abs(math.atan2(math.sin(a - toTown), math.cos(a - toTown)))
		if diff > 0.4 then
			local r = zone.Radius * 0.85 + 6
			local pos = Vector3.new(c.X + math.cos(a) * r, 0, c.Z + math.sin(a) * r)
			local cf = facing(pos, Vector3.new(c.X, 0, c.Z))
			local h = ({ 9, 13, 7, 11, 15, 8 })[i % 6 + 1]
			local mass = model("RockMass", walls)
			local y = 0
			local len, dep = rng:NextNumber(22, 30), rng:NextNumber(10, 14)
			for t = 1, 2 do
				local th = h / 2
				brick(mass, Vector3.new(len, th, dep), cf * CFrame.new(rng:NextNumber(-1, 1), y + th / 2, 0), if t == 1 then C.StoneDark else C.Stone)
				brick(mass, Vector3.new(len + 1, 0.6, dep + 1), cf * CFrame.new(0, y + th + 0.3, 0), C.StoneLight, { Pattern = "StudsNoGrid" })
				y += th
				len *= 0.7
				dep *= 0.75
			end
			mass.Parent = ground
		end
	end
	-- каменные столбы внутри
	for _ = 1, 5 do
		local x, z = randomInZone(zone, 25, zone.Radius * 0.6)
		if not claim(x, z, 5, 6) then
			continue
		end
		local s = model("Spire", ground)
		local y = 0.4
		for k = 1, 3 do
			local w = 6 - k * 1.4
			local h = rng:NextNumber(3, 5)
			brick(s, Vector3.new(w, h, w), CFrame.new(x, y + h / 2, z) * CFrame.Angles(0, deg(15 * k), 0), if k % 2 == 0 then C.Stone else C.StoneDark)
			y += h
		end
	end
	-- опоры шахты с фонарями и вагонетки
	for _ = 1, 4 do
		local x, z = randomInZone(zone, 18, zone.Radius * 0.55)
		if not claim(x, z, 9, 6) then
			continue
		end
		local pos = groundPos(x, z)
		local cf = CFrame.new(pos) * CFrame.Angles(0, yaw15(), 0)
		local f = model("MineFrame", m)
		for _, dx in { -3, 3 } do
			brick(f, Vector3.new(1, 9, 1), cf * CFrame.new(dx, 4.5, 0), C.Wood, { Pattern = "Weld" })
		end
		brick(f, Vector3.new(7.6, 1, 1.2), cf * CFrame.new(0, 9.4, 0), C.WoodDark, { Pattern = "Weld" })
		local lanternCore = brick(f, Vector3.new(0.8, 1, 0.8), cf * CFrame.new(0, 8.3, 0), Color3.fromRGB(255, 214, 150), { Neon = true, Collide = false })
		light(lanternCore, Color3.fromRGB(255, 200, 140), 16, 1)
		local cart = model("Cart", m)
		local cc = cf * CFrame.new(0, 0, 5)
		brick(cart, Vector3.new(4, 2, 3), cc * CFrame.new(0, 1.6, 0), C.Iron, { Pattern = "Weld" })
		for _, dx in { -1.4, 1.4 } do
			brick(cart, Vector3.new(0.6, 0.8, 3.4), cc * CFrame.new(dx, 0.5, 0), C.WoodDark, { Pattern = "Weld" })
		end
		for k = 1, 4 do
			brick(cart, Vector3.new(0.8, 0.8, 0.8), cc * CFrame.new(rng:NextNumber(-1.2, 1.2), 2.9, rng:NextNumber(-0.8, 0.8)) * CFrame.Angles(0, deg(15 * k), 0), Config.Ores.Iron.Color, { Collide = false })
		end
	end
	for _ = 1, 8 do
		local x, z = randomInZone(zone, 10, zone.Radius * 0.75)
		if claim(x, z, 3, 6) then
			rock(m, groundPos(x, z), rng:NextNumber(0.7, 1.1), { C.Stone, C.StoneDark, C.StoneLight })
		end
	end
end

local function buildGrotto(parent: Instance)
	local zone = W.Zones.MoonGrotto
	local m = model("MoonGrotto", parent)
	local c = zone.Center
	disc(model("SnowFloor", ground), c.X, c.Z, zone.Radius * 0.85, 0, 0.4, C.Snow)
	for _ = 1, 10 do
		local x, z = randomInZone(zone, 20, zone.Radius * 0.9)
		local pos = groundPos(x, z)
		if pos.Y < 1 and claim(x, z, 5, 6) then
			pineTree(m, pos, rng:NextNumber(0.9, 1.3))
		end
	end
	-- ледяные кристаллы: ступенчатые столбики с наклоном 15°
	for _ = 1, 12 do
		local x, z = randomInZone(zone, 12, zone.Radius * 0.85)
		if not claim(x, z, 2.5, 6) then
			continue
		end
		local pos = groundPos(x, z)
		local cl = model("IceCrystal", m)
		local base = CFrame.new(pos) * CFrame.Angles(0, yaw15(), deg(15 * rng:NextInteger(-1, 1)))
		local y = 0
		for k = 1, 3 do
			local w = 2.4 - k * 0.5
			local h = rng:NextNumber(2, 3.6)
			brick(cl, Vector3.new(w, h, w), base * CFrame.new(0, y + h / 2, 0), if k % 2 == 0 then C.Ice else C.Frost, { Pattern = "Studs", Collide = k == 1 })
			y += h
		end
	end
	for _ = 1, 6 do
		local x, z = randomInZone(zone, 10, zone.Radius * 0.8)
		if claim(x, z, 3, 6) then
			rock(m, groundPos(x, z), rng:NextNumber(0.8, 1.2), { C.Coral, C.CoralDark })
		end
	end
	for _ = 1, 3 do
		local x, z = randomInZone(zone, 0, 40)
		local p = brick(m, Vector3.new(1, 1, 1), CFrame.new(x, 5, z), C.White, { Transparency = 1, Collide = false })
		light(p, Color3.fromRGB(150, 200, 255), 40, 1.4)
	end
end

local function buildGatePlains(parent: Instance)
	local zone = W.Zones.GatePlains
	local m = model("GatePlains", parent)
	local c = zone.Center
	local pads = model("GatePads", ground)
	for _, offset in { Vector3.new(-38, 0, -30), Vector3.new(38, 0, -30), Vector3.new(-38, 0, 32), Vector3.new(38, 0, 32) } do
		local x, z = c.X + offset.X, c.Z + offset.Z
		reserve(x, z, 12)
		brick(pads, Vector3.new(16, 0.6, 16), CFrame.new(x, 0.3, z), C.StoneDark, { Pattern = "StudsNoGrid" })
		brick(pads, Vector3.new(12, 0.4, 12), CFrame.new(x, 0.8, z), C.Stone, { Pattern = "StudsNoGrid" })
		-- тонкая светящаяся кромка: площадка важна для игры
		for _, s in { { 0, 6.1, 12.4, 0.3 }, { 0, -6.1, 12.4, 0.3 }, { 6.1, 0, 0.3, 12 }, { -6.1, 0, 0.3, 12 } } do
			brick(pads, Vector3.new(s[3], 0.2, s[4]), CFrame.new(x + s[1], 1.05, z + s[2]), C.SystemBlue, { Neon = true, Collide = false })
		end
		for _, px in { -7, 7 } do
			for _, pz in { -7, 7 } do
				brick(m, Vector3.new(1.6, 6, 1.6), CFrame.new(x + px, 3.6, z + pz), C.StoneLight, { Pattern = "Weld" })
				brick(m, Vector3.new(2.2, 0.6, 2.2), CFrame.new(x + px, 6.9, z + pz), C.StoneDark, { Pattern = "Weld" })
			end
		end
		local spawnPoint = brick(m, Vector3.new(6, 1, 6), CFrame.new(x, 1.5, z), C.White, { Transparency = 1, Collide = false, Name = "GateSpawn" })
		CollectionService:AddTag(spawnPoint, T.GateSpawn)
	end
	for _ = 1, 5 do
		local x, z = randomInZone(zone, 60, zone.Radius * 0.95)
		if claim(x, z, 7, 8) then
			blobTree(m, groundPos(x, z), rng:NextNumber(0.9, 1.2), C.GrassBright, C.Grass)
		end
	end
	scatter(m, zone, 10, zone.Radius * 0.95, 30, 18)
end

local function buildLake(parent: Instance)
	local m = model("Lake", parent)
	local cx, cz = 130, 125
	reserve(cx, cz, 34)
	disc(model("LakeBed", ground), cx, cz, 32, 0, 0.2, C.Sand)
	-- одна прозрачная плита воды поверх дна (дно видно — значит глубоко)
	disc(m, cx, cz, 27, 0.2, 0.7, C.Water, { Transparency = 0.35, Collide = false })
	for i = 0, 15 do
		local a = i / 16 * math.pi * 2
		rock(m, Vector3.new(cx + math.cos(a) * 29, 0.2, cz + math.sin(a) * 29), 0.6, { C.Stone, C.StoneLight })
	end
	-- кувшинки, рогоз и мостки
	for i = 1, 5 do
		local a = rng:NextNumber(0, math.pi * 2)
		local r = rng:NextNumber(6, 20)
		local padPos = Vector3.new(cx + math.cos(a) * r, 0.95, cz + math.sin(a) * r)
		brick(m, Vector3.new(2, 0.1, 2), CFrame.new(padPos) * CFrame.Angles(0, deg(15 * i), 0), C.GrassDark, { Collide = false })
		if i == 2 then
			flower(m, padPos, Color3.fromRGB(240, 120, 200))
		end
	end
	for i = 1, 6 do
		local a = deg(200 + i * 8)
		local p = Vector3.new(cx + math.cos(a) * 25, 0.2, cz + math.sin(a) * 25)
		brick(m, Vector3.new(0.3, 2.4, 0.3), CFrame.new(p + Vector3.new(0, 1.2, 0)), C.Tuft, { Collide = false })
		brick(m, Vector3.new(0.5, 0.8, 0.5), CFrame.new(p + Vector3.new(0, 2.6, 0)), C.WoodDark, { Collide = false })
	end
	local dock = model("Dock", m)
	local dockCf = CFrame.new(cx - 28, 0, cz) * CFrame.Angles(0, deg(90), 0)
	for i = 0, 5 do
		brick(dock, Vector3.new(4, 0.4, 1.6), dockCf * CFrame.new(0, 1.4, -i * 1.8), if i % 2 == 0 then C.Wood else C.WoodDark, { Pattern = "Weld" })
	end
	for _, dx in { -2, 2 } do
		for _, dz in { 0, -5, -9 } do
			brick(dock, Vector3.new(0.6, 2, 0.6), dockCf * CFrame.new(dx, 0.6, dz), C.WoodDark, { Pattern = "Weld" })
		end
	end
end

-- ===== Плато по краю карты (барьер без щелей) =====

local function buildBorder(parent: Instance)
	local ring = model("Border", parent)
	local half = W.Size / 2
	local heights = { 16, 24, 19, 27, 17, 22, 26, 18 }
	local k = 0
	for side = 1, 4 do
		local pos = -half
		while pos < half do
			k += 1
			local length = rng:NextNumber(40, 75)
			local depth = rng:NextNumber(18, 34)
			local h = heights[k % #heights + 1]
			local along = pos + length / 2
			-- длинная ось вдоль края, глубина вглубь карты, масса «седлает» край
			local center: CFrame
			if side == 1 then
				center = CFrame.new(along, 0, -half + depth * 0.3) * CFrame.Angles(0, math.pi, 0)
			elseif side == 2 then
				center = CFrame.new(along, 0, half - depth * 0.3)
			elseif side == 3 then
				center = CFrame.new(-half + depth * 0.3, 0, along) * CFrame.Angles(0, -math.pi / 2, 0)
			else
				center = CFrame.new(half - depth * 0.3, 0, along) * CFrame.Angles(0, math.pi / 2, 0)
			end
			local topY = plateau(ring, center, length, depth, h, k % 2 == 0)
			if h >= 24 and k % 2 == 0 then
				local treePos = (center * CFrame.new(0, topY, 0)).Position
				blobTree(ring, treePos, 1, C.GrassBright, C.Grass)
			end
			pos += length * 0.8 -- перекрытие ~20%: щелей нет
		end
	end
	-- невидимые стены поверх: страховка от выхода за карту
	local inner = half - 8
	for _, data in {
		{ Vector3.new(0, 100, -inner), Vector3.new(W.Size, 260, 2) },
		{ Vector3.new(0, 100, inner), Vector3.new(W.Size, 260, 2) },
		{ Vector3.new(-inner, 100, 0), Vector3.new(2, 260, W.Size) },
		{ Vector3.new(inner, 100, 0), Vector3.new(2, 260, W.Size) },
	} do
		local p = Instance.new("Part")
		p.Name = "Bound"
		p.Size = data[2]
		p.CFrame = CFrame.new(data[1])
		p.Anchored = true
		p.Transparency = 1
		p.CanQuery = false
		p.Parent = ring
	end
end

local function placeOres(parent: Instance)
	local m = model("Ores", parent)
	for _, entry in W.OreNodes do
		local zone = W.Zones[entry.Zone]
		local placed: { Vector3 } = {}
		local minR = if zone.Id == "Town" then 44 else 12
		local maxR = if zone.Id == "Town" then 58 else zone.Radius * 0.75
		local tries = 0
		while #placed < entry.Count and tries < entry.Count * 30 do
			tries += 1
			local x, z = randomInZone(zone, minR, maxR)
			local pos = groundPos(x, z)
			if pos.Y < 1 and claim(x, z, 4.5, 6) then
				table.insert(placed, pos)
				oreNode(m, pos, entry.OreId)
			end
		end
	end
end

local function setupLighting()
	-- высокий ключ: солнечно, мягкие тени (стилизованный свет)
	pcall(function()
		Lighting.LightingStyle = Enum.LightingStyle.Soft
	end)
	Lighting.ClockTime = W.Lighting.ClockTime
	Lighting.Brightness = 3
	Lighting.GlobalShadows = true
	Lighting.Ambient = Color3.fromRGB(110, 110, 125)
	Lighting.OutdoorAmbient = Color3.fromRGB(160, 160, 180)
	Lighting.EnvironmentDiffuseScale = 0.6
	Lighting.EnvironmentSpecularScale = 0.2
	local function ensure(className: string, props: { [string]: any })
		if Lighting:FindFirstChildOfClass(className) then
			return
		end
		local inst = Instance.new(className)
		for k, v in props do
			(inst :: any)[k] = v
		end
		inst.Parent = Lighting
	end
	ensure("Atmosphere", { Density = 0.22, Offset = 0.1, Color = Color3.fromRGB(205, 225, 255), Decay = Color3.fromRGB(150, 180, 220), Glare = 0, Haze = 1 })
	ensure("BloomEffect", { Intensity = 0.4, Size = 20, Threshold = 1.8 })
	ensure("ColorCorrectionEffect", { Saturation = 0.18, Contrast = 0.04, Brightness = 0.02 })
	ensure("SunRaysEffect", { Intensity = 0.03, Spread = 0.6 })
end

-- Строить ли мир: только если в карте нет своих жил руды
function WorldBuilder.ShouldBuild(): boolean
	return W.AutoBuild and #CollectionService:GetTagged(T.OreNode) == 0
end

function WorldBuilder.Build()
	local started = os.clock()
	root = Instance.new("Folder")
	root.Name = "World"
	root.Parent = workspace
	ground = Instance.new("Folder")
	ground.Name = "Ground"
	ground.Parent = root
	rayParams.FilterDescendantsInstances = { ground }

	-- остров: травяная шапка над лососевой землёй
	brick(ground, Vector3.new(W.Size, 2, W.Size), CFrame.new(0, -1, 0), C.Grass, { Name = "GrassCap" })
	brick(ground, Vector3.new(W.Size, 10, W.Size), CFrame.new(0, -7, 0), C.DirtDeep, { Name = "DirtLayer" })
	local checker = W.CheckerTextureId
	if type(checker) == "string" and checker ~= "" then
		local tex = Instance.new("Texture")
		tex.Texture = checker
		tex.Face = Enum.NormalId.Top
		tex.StudsPerTileU = 8
		tex.StudsPerTileV = 8
		tex.Transparency = 0.8
		tex.Parent = ground:FindFirstChild("GrassCap")
	end

	buildTown(root)
	task.wait() -- не упереться в лимит времени выполнения скрипта
	buildMarket(root)
	buildPaths()
	buildCopperHills(root)
	buildQuarry(root)
	task.wait()
	buildGrotto(root)
	buildGatePlains(root)
	buildLake(root)
	buildBorder(root)
	placeOres(root)
	setupLighting()

	-- шаблонный Baseplate убираем в самом конце, чтобы никто не успел упасть в пустоту
	if W.RemoveDefaultBaseplate then
		for _, name in { "Baseplate", "SpawnLocation" } do
			local inst = workspace:FindFirstChild(name)
			if inst and inst:IsA("BasePart") then
				inst:Destroy()
			end
		end
	end

	WorldBuilder.Built = true
	local count = 0
	for _, d in root:GetDescendants() do
		if d:IsA("BasePart") then
			count += 1
		end
	end
	print(`[World] стад-мир построен за {string.format("%.1f", os.clock() - started)} с, деталей: {count}, варианты материалов: {Stud.UsingVariants}`)
end

-- Точка на земле (для мобов): используется WorldMobService
function WorldBuilder.GroundAt(x: number, z: number): number
	return groundAt(x, z)
end

return WorldBuilder
