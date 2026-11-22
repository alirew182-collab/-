--!strict
--[[
	ТЕСТОВАЯ КАРТА-ЗАГЛУШКА.
	Если в карте ещё нет ни одного объекта с тегами OreNode / Forge / Merchant / GateSpawn,
	сервер ставит простые блоки рядом со спавном, чтобы игру можно было проверить сразу по Play.
	Как только ты расставишь свои объекты с тегами — заглушка перестанет появляться.
	Каждый блок подписан, а теги/атрибуты на нём ровно такие, какие нужны твоим моделям.
]]

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)

local DevMap = {}

local folder: Folder

local function label(part: BasePart, text: string)
	local gui = Instance.new("BillboardGui")
	gui.Size = UDim2.fromOffset(200, 40)
	gui.StudsOffsetWorldSpace = Vector3.new(0, part.Size.Y / 2 + 1.5, 0)
	gui.MaxDistance = 60
	gui.LightInfluence = 0
	local t = Instance.new("TextLabel")
	t.Size = UDim2.fromScale(1, 1)
	t.BackgroundTransparency = 1
	t.Font = Enum.Font.GothamBold
	t.TextScaled = true
	t.TextColor3 = Color3.new(1, 1, 1)
	t.TextStrokeTransparency = 0.4
	t.Text = text
	t.Parent = gui
	gui.Parent = part
end

local function block(name: string, size: Vector3, position: Vector3, color: Color3, tag: string?, text: string?): Part
	local p = Instance.new("Part")
	p.Name = name
	p.Size = size
	p.Position = position
	p.Anchored = true
	p.Color = color
	p.Material = Enum.Material.Plastic
	p.TopSurface = Enum.SurfaceType.Studs
	if tag then
		CollectionService:AddTag(p, tag)
	end
	if text then
		label(p, text)
	end
	p.Parent = folder
	return p
end

local function hasAny(tag: string): boolean
	return #CollectionService:GetTagged(tag) > 0
end

local function hasFloor(): boolean
	for _, child in workspace:GetChildren() do
		if child:IsA("BasePart") and child.Size.X >= 100 and child.Size.Z >= 100 then
			return true
		end
	end
	return workspace:FindFirstChild("Baseplate") ~= nil
end

function DevMap.BuildIfEmpty()
	local T = Config.Tags
	local needOres = not hasAny(T.OreNode)
	local needForge = not hasAny(T.Forge)
	local needMerchant = not hasAny(T.Merchant)
	local needGates = not hasAny(T.GateSpawn)
	if not (needOres or needForge or needMerchant or needGates) then
		return
	end

	folder = Instance.new("Folder")
	folder.Name = "DevMap_Placeholder"
	folder.Parent = workspace
	warn("[DevMap] Карта пустая — ставлю тестовые заглушки (папка Workspace/DevMap_Placeholder)")

	if not hasFloor() then
		block("DevFloor", Vector3.new(400, 2, 400), Vector3.new(0, -1, 0), Color3.fromRGB(70, 90, 70))
	end

	if needForge then
		block("Anvil (tag Forge)", Vector3.new(4, 3, 2), Vector3.new(0, 1.5, -22), Color3.fromRGB(50, 50, 55), T.Forge, "Forge")
		block("Furnace", Vector3.new(6, 6, 4), Vector3.new(6, 3, -26), Color3.fromRGB(120, 50, 30))
	end

	if needMerchant then
		block("Merchant (tag Merchant)", Vector3.new(3, 6, 3), Vector3.new(24, 3, -10), Color3.fromRGB(230, 190, 60), T.Merchant, "Merchant")
	end

	if needOres then
		local layout = {
			{ Ore = "Copper", X = -24, Count = 6 },
			{ Ore = "Iron", X = -40, Count = 4 },
			{ Ore = "Moonsilver", X = -56, Count = 3 },
		}
		for _, row in layout do
			for i = 1, row.Count do
				local z = -12 + (i - 1) * 7
				local node = block(`{row.Ore}Node (tag OreNode)`, Vector3.new(4, 4, 4), Vector3.new(row.X, 2, z), Color3.new(1, 1, 1), nil, nil)
				node:SetAttribute("OreId", row.Ore)
				CollectionService:AddTag(node, T.OreNode)
			end
		end
	end

	if needGates then
		for i, pos in { Vector3.new(30, 0.5, 30), Vector3.new(-10, 0.5, 45) } do
			local spawnPoint = block(`GateSpawn{i} (tag GateSpawn)`, Vector3.new(6, 1, 6), pos, Color3.fromRGB(40, 60, 120), T.GateSpawn)
			spawnPoint.Transparency = 0.5
			spawnPoint.CanCollide = false
		end
	end
end

return DevMap
