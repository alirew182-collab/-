--!strict
--[[
	ГЕНЕРАТОР МИРА (Terrain + постройки из Part).
	Строится один раз при старте сервера, если в карте нет своих объектов с тегом OreNode.
	Детерминированный (Config.World.Seed) — мир одинаковый на всех серверах.

	Что строится:
	  • Угольный Оплот (город): площадь, фонтан с Кристаллом Системы, кузня, лавка торговца,
	    доски лидеров, фонари, деревья, точка появления.
	  • Медные холмы, Карьер Железный Клык, Грот лунного серебра — жилы руды + мобы.
	  • Равнина Врат — точки появления порталов с каменными кругами.
	  • Дороги, озеро, горы по краю карты и невидимые стены.
	Деталей ~400 — нормально для слабых телефонов. Все модели — со своими тегами,
	так что любую можно заменить своей моделью с тем же тегом.
]]

local CollectionService = game:GetService("CollectionService")
local Lighting = game:GetService("Lighting")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)

local W = Config.World
local T = Config.Tags

local WorldBuilder = {}

WorldBuilder.Built = false -- true, если мир построен кодом (зоны совпадают с Config.World)

local terrain = workspace.Terrain
local rng = Random.new(W.Seed)
local root: Folder

local M = Enum.Material

-- ===== Помощники =====

local rayParams = RaycastParams.new()
rayParams.FilterType = Enum.RaycastFilterType.Include
rayParams.FilterDescendantsInstances = { terrain }

local function groundAt(x: number, z: number): number
	local hit = workspace:Raycast(Vector3.new(x, 250, z), Vector3.new(0, -500, 0), rayParams)
	return if hit then hit.Position.Y else 0
end

local function groundPos(x: number, z: number): Vector3
	return Vector3.new(x, groundAt(x, z), z)
end

local function part(parent: Instance, props: { [string]: any }): Part
	local p = Instance.new("Part")
	p.Anchored = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	-- порядок важен: форма → размер → остальное → позиция
	if props.Shape then
		p.Shape = props.Shape
	end
	if props.Size then
		p.Size = props.Size
	end
	for key, value in props do
		if key ~= "Shape" and key ~= "Size" and key ~= "CFrame" then
			(p :: any)[key] = value
		end
	end
	if props.CFrame then
		p.CFrame = props.CFrame
	end
	p.Parent = parent
	return p
end

local function model(name: string, parent: Instance): Model
	local m = Instance.new("Model")
	m.Name = name
	m.Parent = parent
	return m
end

local function light(parent: BasePart, color: Color3, range: number, brightness: number)
	local l = Instance.new("PointLight")
	l.Color = color
	l.Range = range
	l.Brightness = brightness
	l.Shadows = false
	l.Parent = parent
end

local function icon(parent: BasePart, text: string, height: number)
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(70, 70)
	gui.StudsOffsetWorldSpace = Vector3.new(0, height, 0)
	gui.MaxDistance = 140
	gui.LightInfluence = 0
	gui.AlwaysOnTop = false
	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.TextScaled = true
	label.Font = Enum.Font.GothamBlack
	label.Text = text
	label.TextColor3 = Color3.new(1, 1, 1)
	label.Parent = gui
	gui.Parent = parent
end

local function randomInZone(zone: any, minR: number, maxR: number): (number, number)
	local angle = rng:NextNumber(0, math.pi * 2)
	local r = rng:NextNumber(minR, maxR)
	return zone.Center.X + math.cos(angle) * r, zone.Center.Z + math.sin(angle) * r
end

-- ===== Terrain =====

local function buildTerrain()
	local size = W.Size
	local Z = W.Zones

	-- основа: камень снизу, трава сверху (верх земли = Y 0)
	terrain:FillBlock(CFrame.new(0, -26, 0), Vector3.new(size, 12, size), M.Rock)
	terrain:FillBlock(CFrame.new(0, -10, 0), Vector3.new(size, 20, size), M.Grass)

	-- дороги из города в каждую зону
	for _, zone in Z do
		if zone.Id ~= "Town" then
			local from = Vector3.new(0, -1, 0)
			local to = Vector3.new(zone.Center.X, -1, zone.Center.Z)
			local length = (to - from).Magnitude
			local mid = (from + to) / 2
			terrain:FillBlock(CFrame.lookAt(mid, to), Vector3.new(10, 2, length), M.Ground)
		end
	end

	-- город: мощёная площадь
	terrain:FillCylinder(CFrame.new(0, -1, 0), 2, 40, M.Cobblestone)

	-- Медные холмы: холмы и проплешины земли
	local copper = Z.CopperHills
	for _ = 1, 9 do
		local x, z = randomInZone(copper, 25, copper.Radius * 0.95)
		local r = rng:NextNumber(14, 26)
		terrain:FillBall(Vector3.new(x, -r * 0.6, z), r, M.Grass)
	end
	for _ = 1, 6 do
		local x, z = randomInZone(copper, 0, copper.Radius * 0.8)
		terrain:FillCylinder(CFrame.new(x, -1, z), 2.2, rng:NextNumber(8, 16), M.Ground)
	end

	-- Карьер: сланцевый пол, скальное кольцо с проходом к городу, каменные столбы
	local quarry = Z.IronQuarry
	terrain:FillCylinder(CFrame.new(quarry.Center.X, -1, quarry.Center.Z), 2.2, quarry.Radius * 0.85, M.Slate)
	local toTown = math.atan2(-quarry.Center.Z, -quarry.Center.X)
	for i = 0, 23 do
		local angle = i / 24 * math.pi * 2
		local diff = math.abs(math.atan2(math.sin(angle - toTown), math.cos(angle - toTown)))
		if diff > 0.35 then -- проход в сторону города
			local r = rng:NextNumber(11, 17)
			local dist = quarry.Radius * 0.85 + 4
			terrain:FillBall(
				Vector3.new(quarry.Center.X + math.cos(angle) * dist, -2, quarry.Center.Z + math.sin(angle) * dist),
				r,
				M.Rock
			)
		end
	end
	for _ = 1, 6 do
		local x, z = randomInZone(quarry, 20, quarry.Radius * 0.65)
		terrain:FillBall(Vector3.new(x, -3, z), rng:NextNumber(5, 9), M.Basalt)
	end

	-- Грот: снег, ледяные глыбы
	local grotto = Z.MoonGrotto
	terrain:FillCylinder(CFrame.new(grotto.Center.X, -1, grotto.Center.Z), 2.2, grotto.Radius * 0.9, M.Snow)
	for _ = 1, 10 do
		local x, z = randomInZone(grotto, 25, grotto.Radius * 0.95)
		local r = rng:NextNumber(6, 13)
		terrain:FillBall(Vector3.new(x, -r * 0.3, z), r, M.Glacier)
	end

	-- Равнина Врат: густая трава
	local plains = Z.GatePlains
	terrain:FillCylinder(CFrame.new(plains.Center.X, -1, plains.Center.Z), 2.2, plains.Radius * 0.9, M.LeafyGrass)

	-- озеро между городом, гротом и равниной
	local lake = Vector3.new(130, 0, 125)
	terrain:FillCylinder(CFrame.new(lake.X, -1, lake.Z), 2.2, 38, M.Sand)
	terrain:FillCylinder(CFrame.new(lake.X, -5, lake.Z), 10, 31, M.Air)
	terrain:FillCylinder(CFrame.new(lake.X, -6.5, lake.Z), 7, 31, M.Water)

	-- горы по краю карты
	local half = size / 2
	local step = 34
	local edge = -half
	while edge <= half do
		for _, pos in {
			Vector3.new(edge, 0, -half),
			Vector3.new(edge, 0, half),
			Vector3.new(-half, 0, edge),
			Vector3.new(half, 0, edge),
		} do
			local r = rng:NextNumber(32, 46)
			terrain:FillBall(Vector3.new(pos.X, rng:NextNumber(0, 8), pos.Z), r, M.Rock)
			if rng:NextNumber() < 0.5 then
				terrain:FillBall(Vector3.new(pos.X, r * 0.75, pos.Z), r * 0.35, M.Snow)
			end
		end
		edge += step
	end
end

-- ===== Природа =====

local function tree(parent: Instance, position: Vector3, scale: number, leafColor: Color3)
	local m = model("Tree", parent)
	local height = 9 * scale
	part(m, {
		Name = "Trunk",
		Size = Vector3.new(1.6 * scale, height, 1.6 * scale),
		CFrame = CFrame.new(position + Vector3.new(0, height / 2 - 0.5, 0)),
		Material = M.Wood,
		Color = Color3.fromRGB(105, 70, 45),
	})
	part(m, {
		Name = "Leaves",
		Shape = Enum.PartType.Ball,
		Size = Vector3.one * (10 * scale),
		CFrame = CFrame.new(position + Vector3.new(0, height + 2 * scale, 0)),
		Material = M.Grass,
		Color = leafColor,
		CastShadow = true,
	})
end

local function crystal(parent: Instance, position: Vector3, height: number, color: Color3)
	part(parent, {
		Name = "Crystal",
		Size = Vector3.new(height * 0.35, height, height * 0.35),
		CFrame = CFrame.new(position + Vector3.new(0, height * 0.4, 0))
			* CFrame.Angles(rng:NextNumber(-0.3, 0.3), rng:NextNumber(0, math.pi), rng:NextNumber(-0.3, 0.3)),
		Material = M.Neon,
		Color = color,
		CanCollide = false,
		CastShadow = false,
	})
end

-- ===== Жила руды =====

local function oreNode(parent: Instance, position: Vector3, oreId: string)
	local ore = Config.Ores[oreId]
	local m = model(oreId .. "Node", parent)
	local size = rng:NextNumber(4.5, 6)
	local rock = part(m, {
		Name = "Rock",
		Shape = Enum.PartType.Ball,
		Size = Vector3.one * size,
		CFrame = CFrame.new(position + Vector3.new(0, size * 0.3, 0)),
		Material = M.Slate,
		Color = Color3.fromRGB(92, 88, 86),
	})
	for _ = 1, 3 do
		local offset = Vector3.new(rng:NextNumber(-1, 1), rng:NextNumber(0.2, 1), rng:NextNumber(-1, 1)).Unit
			* (size * 0.42)
		part(m, {
			Name = "Vein",
			Size = Vector3.new(rng:NextNumber(0.8, 1.4), rng:NextNumber(1.6, 2.6), rng:NextNumber(0.8, 1.4)),
			CFrame = CFrame.new(rock.Position + offset)
				* CFrame.Angles(rng:NextNumber(0, 6), rng:NextNumber(0, 6), rng:NextNumber(0, 6)),
			Material = if oreId == "Moonsilver" then M.Neon elseif oreId == "Iron" then M.Metal else M.Foil,
			Color = ore.Color,
			CanCollide = false,
		})
	end
	m.PrimaryPart = rock
	m:SetAttribute("OreId", oreId)
	m:SetAttribute("AutoStyle", false)
	CollectionService:AddTag(m, T.OreNode)
end

-- ===== Город =====

local function buildSmithy(parent: Instance, origin: Vector3)
	local m = model("Smithy", parent)
	local wood = Color3.fromRGB(120, 82, 52)
	part(m, { Name = "Floor", Size = Vector3.new(26, 1, 20), CFrame = CFrame.new(origin + Vector3.new(0, 0.5, 0)), Material = M.WoodPlanks, Color = wood })
	for _, offset in { Vector3.new(-12, 0, -9), Vector3.new(12, 0, -9), Vector3.new(-12, 0, 9), Vector3.new(12, 0, 9) } do
		part(m, { Name = "Post", Size = Vector3.new(1.2, 13, 1.2), CFrame = CFrame.new(origin + offset + Vector3.new(0, 7, 0)), Material = M.Wood, Color = wood })
	end
	local roofColor = Color3.fromRGB(110, 40, 35)
	part(m, { Name = "Roof", Size = Vector3.new(28, 1, 12.5), CFrame = CFrame.new(origin + Vector3.new(0, 15.5, -4.6)) * CFrame.Angles(math.rad(25), 0, 0), Material = M.Slate, Color = roofColor })
	part(m, { Name = "Roof", Size = Vector3.new(28, 1, 12.5), CFrame = CFrame.new(origin + Vector3.new(0, 15.5, 4.6)) * CFrame.Angles(math.rad(-25), 0, 0), Material = M.Slate, Color = roofColor })
	part(m, { Name = "BackWall", Size = Vector3.new(26, 9, 1), CFrame = CFrame.new(origin + Vector3.new(0, 5.5, -9.5)), Material = M.Brick, Color = Color3.fromRGB(140, 90, 70) })

	-- горн
	part(m, { Name = "Furnace", Size = Vector3.new(6, 7, 5), CFrame = CFrame.new(origin + Vector3.new(-8, 4.5, -6)), Material = M.Brick, Color = Color3.fromRGB(95, 60, 50) })
	local fire = part(m, { Name = "FurnaceFire", Size = Vector3.new(3, 2.4, 0.3), CFrame = CFrame.new(origin + Vector3.new(-8, 3, -3.4)), Material = M.Neon, Color = Color3.fromRGB(255, 120, 30), CanCollide = false })
	light(fire, Color3.fromRGB(255, 140, 60), 18, 2.5)
	local emitter = Instance.new("ParticleEmitter")
	emitter.Rate = 12
	emitter.Lifetime = NumberRange.new(0.5, 1)
	emitter.Speed = NumberRange.new(2, 4)
	emitter.Color = ColorSequence.new(Color3.fromRGB(255, 170, 60), Color3.fromRGB(255, 60, 20))
	emitter.Size = NumberSequence.new(0.6, 0)
	emitter.LightEmission = 1
	emitter.Parent = fire
	part(m, { Name = "Chimney", Size = Vector3.new(2.5, 12, 2.5), CFrame = CFrame.new(origin + Vector3.new(-8, 13, -7)), Material = M.Brick, Color = Color3.fromRGB(95, 60, 50) })

	-- наковальня — кузня по тегу Forge
	local anvil = model("Anvil", m)
	local metal = Color3.fromRGB(55, 58, 64)
	part(anvil, { Name = "Base", Size = Vector3.new(2, 2.2, 1.6), CFrame = CFrame.new(origin + Vector3.new(2, 2.1, 0)), Material = M.Metal, Color = metal })
	local top = part(anvil, { Name = "Top", Size = Vector3.new(4, 1, 1.8), CFrame = CFrame.new(origin + Vector3.new(2, 3.7, 0)), Material = M.Metal, Color = metal })
	part(anvil, { Name = "Horn", Size = Vector3.new(1.4, 0.7, 1.1), CFrame = CFrame.new(origin + Vector3.new(4.6, 3.7, 0)), Material = M.Metal, Color = metal })
	anvil.PrimaryPart = top
	anvil.ModelStreamingMode = Enum.ModelStreamingMode.Persistent
	CollectionService:AddTag(anvil, T.Forge)
	icon(top, "⚒", 6)

	-- бочка для закалки
	part(m, { Name = "Barrel", Shape = Enum.PartType.Cylinder, Size = Vector3.new(3, 3, 3), CFrame = CFrame.new(origin + Vector3.new(8, 2.5, 4)) * CFrame.Angles(0, 0, math.rad(90)), Material = M.Wood, Color = wood })
end

local function buildMerchant(parent: Instance, origin: Vector3)
	local m = model("MerchantStall", parent)
	local wood = Color3.fromRGB(140, 100, 60)
	part(m, { Name = "Counter", Size = Vector3.new(12, 3, 3), CFrame = CFrame.new(origin + Vector3.new(0, 1.5, 4)), Material = M.WoodPlanks, Color = wood })
	for _, x in { -6, 6 } do
		for _, z in { -2, 5 } do
			part(m, { Name = "Post", Size = Vector3.new(0.8, 9, 0.8), CFrame = CFrame.new(origin + Vector3.new(x, 4.5, z)), Material = M.Wood, Color = wood })
		end
	end
	for i = 0, 3 do
		part(m, {
			Name = "Awning",
			Size = Vector3.new(3.5, 0.4, 9),
			CFrame = CFrame.new(origin + Vector3.new(-5.25 + i * 3.5, 9.3, 1.5)) * CFrame.Angles(math.rad(-12), 0, 0),
			Material = M.Fabric,
			Color = if i % 2 == 0 then Color3.fromRGB(200, 60, 60) else Color3.fromRGB(240, 230, 210),
		})
	end
	for i = 1, 3 do
		part(m, { Name = "Crate", Size = Vector3.one * 2.2, CFrame = CFrame.new(origin + Vector3.new(-4 + i * 2.6, 1.1, -1)) * CFrame.Angles(0, rng:NextNumber(-0.3, 0.3), 0), Material = M.WoodPlanks, Color = Color3.fromRGB(170, 125, 75) })
	end

	-- NPC торговца
	local npc = model("Merchant", m)
	local body = part(npc, { Name = "Body", Size = Vector3.new(2.6, 3.2, 1.5), CFrame = CFrame.new(origin + Vector3.new(0, 4.6, 1.5)), Material = M.Fabric, Color = Color3.fromRGB(70, 90, 150) })
	part(npc, { Name = "Legs", Size = Vector3.new(2.2, 3, 1.3), CFrame = CFrame.new(origin + Vector3.new(0, 1.5, 1.5)), Material = M.Fabric, Color = Color3.fromRGB(60, 50, 45) })
	part(npc, { Name = "Head", Shape = Enum.PartType.Ball, Size = Vector3.one * 2, CFrame = CFrame.new(origin + Vector3.new(0, 7.2, 1.5)), Material = M.SmoothPlastic, Color = Color3.fromRGB(240, 200, 160) })
	part(npc, { Name = "Hat", Shape = Enum.PartType.Cylinder, Size = Vector3.new(0.6, 3, 3), CFrame = CFrame.new(origin + Vector3.new(0, 8.3, 1.5)) * CFrame.Angles(0, 0, math.rad(90)), Material = M.Fabric, Color = Color3.fromRGB(150, 40, 40) })
	npc.PrimaryPart = body
	npc.ModelStreamingMode = Enum.ModelStreamingMode.Persistent
	CollectionService:AddTag(npc, T.Merchant)
	icon(body, "💰", 6)
end

local function buildLeaderboards(parent: Instance, origin: Vector3)
	local m = model("Leaderboards", parent)
	for i, board in { "Level", "Damage", "Gates" } do
		local pos = origin + Vector3.new((i - 2) * 15, 7, (i - 2) * 6)
		local p = part(m, {
			Name = board .. "Board",
			Size = Vector3.new(12, 13, 0.8),
			-- лицевая сторона смотрит на центр площади
			CFrame = CFrame.lookAt(pos, Vector3.new(0, pos.Y, 0)),
			Material = M.Slate,
			Color = Color3.fromRGB(25, 35, 60),
		})
		p:SetAttribute("Board", board)
		CollectionService:AddTag(p, T.Leaderboard)
		part(m, { Name = "Leg", Size = Vector3.new(1, 1, 1), CFrame = CFrame.new(pos - Vector3.new(0, 7, 0)), Material = M.Slate, Color = Color3.fromRGB(60, 60, 70) })
	end
	m.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
end

local function buildTown(parent: Instance)
	local town = model("Emberhold", parent)
	local accent = Color3.fromRGB(70, 195, 255)

	-- фонтан и Кристалл Системы
	part(town, { Name = "FountainBase", Shape = Enum.PartType.Cylinder, Size = Vector3.new(2, 22, 22), CFrame = CFrame.new(0, 1, 0) * CFrame.Angles(0, 0, math.rad(90)), Material = M.Slate, Color = Color3.fromRGB(150, 150, 160) })
	part(town, { Name = "FountainWater", Shape = Enum.PartType.Cylinder, Size = Vector3.new(0.3, 19, 19), CFrame = CFrame.new(0, 2.05, 0) * CFrame.Angles(0, 0, math.rad(90)), Material = M.Glass, Color = Color3.fromRGB(60, 140, 220), Transparency = 0.35, CanCollide = false })
	part(town, { Name = "Pillar", Size = Vector3.new(3, 8, 3), CFrame = CFrame.new(0, 6, 0), Material = M.Marble, Color = Color3.fromRGB(220, 225, 235) })
	local core = part(town, {
		Name = "SystemCrystal",
		Size = Vector3.new(3.4, 6, 3.4),
		CFrame = CFrame.new(0, 14, 0) * CFrame.Angles(0, math.rad(45), 0),
		Material = M.Neon,
		Color = accent,
		CanCollide = false,
	})
	light(core, accent, 32, 2)
	CollectionService:AddTag(core, T.Spin)

	-- точка появления
	local spawn = Instance.new("SpawnLocation")
	spawn.Name = "TownSpawn"
	spawn.Size = Vector3.new(10, 1, 10)
	spawn.CFrame = CFrame.new(0, 0.5, 26)
	spawn.Anchored = true
	spawn.Material = M.Slate
	spawn.Color = Color3.fromRGB(60, 120, 180)
	spawn.Duration = 0
	spawn.Neutral = true
	spawn.TopSurface = Enum.SurfaceType.Smooth
	spawn.Parent = town

	buildSmithy(town, Vector3.new(-32, 0, -20))
	buildMerchant(town, Vector3.new(32, 0, -20))
	buildLeaderboards(town, Vector3.new(34, 0, -54)) -- между дорогами, не перекрывает путь

	-- фонари по кругу площади
	for i = 0, 7 do
		local angle = i / 8 * math.pi * 2 + math.pi / 8
		local pos = Vector3.new(math.cos(angle) * 37, 0, math.sin(angle) * 37)
		part(town, { Name = "LampPost", Size = Vector3.new(0.6, 9, 0.6), CFrame = CFrame.new(pos + Vector3.new(0, 4.5, 0)), Material = M.Metal, Color = Color3.fromRGB(40, 40, 45) })
		local bulb = part(town, { Name = "Lamp", Shape = Enum.PartType.Ball, Size = Vector3.one * 1.6, CFrame = CFrame.new(pos + Vector3.new(0, 9.4, 0)), Material = M.Neon, Color = Color3.fromRGB(255, 215, 150), CanCollide = false })
		light(bulb, Color3.fromRGB(255, 200, 140), 20, 1.2)
	end

	-- деревья вокруг города
	for i = 0, 13 do
		local angle = i / 14 * math.pi * 2 + rng:NextNumber(-0.15, 0.15)
		local r = rng:NextNumber(48, 66)
		local x, z = math.cos(angle) * r, math.sin(angle) * r
		-- не ставить на дороги
		local onRoad = false
		for _, zone in W.Zones do
			if zone.Id ~= "Town" then
				local dir = Vector3.new(zone.Center.X, 0, zone.Center.Z).Unit
				local p = Vector3.new(x, 0, z)
				local proj = p:Dot(dir)
				if proj > 0 and (p - dir * proj).Magnitude < 9 then
					onRoad = true
				end
			end
		end
		if not onRoad then
			tree(town, groundPos(x, z), rng:NextNumber(0.9, 1.3), Color3.fromRGB(70, 140, 60))
		end
	end
end

-- ===== Зоны =====

local function placeOres(parent: Instance)
	for _, entry in W.OreNodes do
		local zone = W.Zones[entry.Zone]
		local placed: { Vector3 } = {}
		local minR = if zone.Id == "Town" then 44 else 12
		local maxR = if zone.Id == "Town" then 58 else zone.Radius * 0.75
		local tries = 0
		while #placed < entry.Count and tries < entry.Count * 20 do
			tries += 1
			local x, z = randomInZone(zone, minR, maxR)
			local pos = groundPos(x, z)
			local ok = pos.Y < 6 -- не на вершинах холмов
			for _, other in placed do
				if (other - pos).Magnitude < 9 then
					ok = false
					break
				end
			end
			if ok then
				table.insert(placed, pos)
				oreNode(parent, pos, entry.OreId)
			end
		end
	end
end

local function buildZones(parent: Instance)
	local Z = W.Zones

	-- деревья на холмах
	local hills = model("CopperHills", parent)
	for _ = 1, 12 do
		local x, z = randomInZone(Z.CopperHills, 10, Z.CopperHills.Radius)
		tree(hills, groundPos(x, z), rng:NextNumber(0.8, 1.3), Color3.fromRGB(95, 150, 55))
	end

	-- кристаллы в гроте
	local grotto = model("MoonGrotto", parent)
	for _ = 1, 14 do
		local x, z = randomInZone(Z.MoonGrotto, 10, Z.MoonGrotto.Radius * 0.9)
		crystal(grotto, groundPos(x, z), rng:NextNumber(4, 9), Color3.fromRGB(150, 210, 255))
	end
	for _ = 1, 3 do
		local x, z = randomInZone(Z.MoonGrotto, 0, 40)
		local p = part(grotto, { Name = "Glow", Size = Vector3.one, CFrame = CFrame.new(groundPos(x, z) + Vector3.new(0, 4, 0)), Transparency = 1, CanCollide = false, CanQuery = false })
		light(p, Color3.fromRGB(150, 200, 255), 40, 1.5)
	end

	-- карьер: брошенные вагонетки и опоры
	local quarry = model("IronQuarry", parent)
	for _ = 1, 5 do
		local x, z = randomInZone(Z.IronQuarry, 15, Z.IronQuarry.Radius * 0.6)
		local pos = groundPos(x, z)
		part(quarry, { Name = "Beam", Size = Vector3.new(1, 10, 1), CFrame = CFrame.new(pos + Vector3.new(0, 5, 0)), Material = M.Wood, Color = Color3.fromRGB(100, 75, 50) })
		part(quarry, { Name = "Cart", Size = Vector3.new(4, 2.5, 3), CFrame = CFrame.new(pos + Vector3.new(3, 1.25, 0)) * CFrame.Angles(0, rng:NextNumber(0, 3), 0), Material = M.Metal, Color = Color3.fromRGB(80, 70, 60) })
	end

	-- Равнина Врат: каменные круги = точки появления порталов
	local plains = model("GatePlains", parent)
	local c = Z.GatePlains.Center
	for _, offset in { Vector3.new(-38, 0, -30), Vector3.new(38, 0, -30), Vector3.new(-38, 0, 32), Vector3.new(38, 0, 32) } do
		local pos = groundPos(c.X + offset.X, c.Z + offset.Z)
		part(plains, {
			Name = "Circle",
			Shape = Enum.PartType.Cylinder,
			Size = Vector3.new(0.4, 16, 16),
			CFrame = CFrame.new(pos + Vector3.new(0, 0.1, 0)) * CFrame.Angles(0, 0, math.rad(90)),
			Material = M.Slate,
			Color = Color3.fromRGB(90, 95, 110),
		})
		for i = 0, 3 do
			local a = i / 4 * math.pi * 2
			part(plains, { Name = "Pillar", Size = Vector3.new(1.6, 7, 1.6), CFrame = CFrame.new(pos + Vector3.new(math.cos(a) * 9.5, 3.5, math.sin(a) * 9.5)), Material = M.Slate, Color = Color3.fromRGB(110, 115, 130) })
		end
		local spawnPoint = part(plains, {
			Name = "GateSpawn",
			Size = Vector3.new(6, 1, 6),
			CFrame = CFrame.new(pos + Vector3.new(0, 0.5, 0)),
			Transparency = 1,
			CanCollide = false,
			CanQuery = false,
		})
		CollectionService:AddTag(spawnPoint, T.GateSpawn)
	end
end

local function buildBounds(parent: Instance)
	local half = W.Size / 2 - 8
	for _, data in {
		{ Vector3.new(0, 100, -half), Vector3.new(W.Size, 260, 2) },
		{ Vector3.new(0, 100, half), Vector3.new(W.Size, 260, 2) },
		{ Vector3.new(-half, 100, 0), Vector3.new(2, 260, W.Size) },
		{ Vector3.new(half, 100, 0), Vector3.new(2, 260, W.Size) },
	} do
		part(parent, { Name = "Bound", Size = data[2], CFrame = CFrame.new(data[1]), Transparency = 1, CanQuery = false })
	end
end

local function setupLighting()
	local L = W.Lighting
	Lighting.ClockTime = L.ClockTime
	Lighting.Brightness = L.Brightness
	Lighting.GlobalShadows = true
	Lighting.OutdoorAmbient = Color3.fromRGB(135, 135, 155)
	Lighting.EnvironmentDiffuseScale = 0.5
	Lighting.EnvironmentSpecularScale = 0.5
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
	ensure("Atmosphere", { Density = L.AtmosphereDensity, Offset = 0.15, Color = Color3.fromRGB(200, 205, 225), Decay = Color3.fromRGB(105, 112, 130), Glare = 0.2, Haze = 1.2 })
	ensure("BloomEffect", { Intensity = 0.5, Size = 24, Threshold = 1.6 })
	ensure("ColorCorrectionEffect", { Saturation = 0.12, Contrast = 0.05 })
	ensure("SunRaysEffect", { Intensity = 0.04, Spread = 0.8 })
	terrain.WaterColor = Color3.fromRGB(40, 120, 170)
	terrain.WaterTransparency = 0.6
	terrain.WaterWaveSize = 0.1
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

	buildTerrain()
	task.wait() -- не упереться в лимит времени выполнения скрипта
	buildTown(root)
	buildZones(root)
	placeOres(root)
	buildBounds(root)
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
	print(`[World] мир построен за {string.format("%.1f", os.clock() - started)} с`)
end

-- Точка на земле (для мобов): используется WorldMobService
function WorldBuilder.GroundAt(x: number, z: number): number
	return groundAt(x, z)
end

return WorldBuilder
