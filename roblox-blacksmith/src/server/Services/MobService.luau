--!strict
--[[
	МОБЫ: спавн из пула объектов, простой ИИ на сервере (догнать → замах → удар),
	у боссов — удар по площади с красным кругом-предупреждением.
	Здоровье мобов считаем сами (Humanoid только для ходьбы) — так дешевле и нет «смерти» Humanoid.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local ServerStorage = game:GetService("ServerStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Signal = require(ReplicatedStorage.Shared.Signal)
local Char = require(script.Parent.Parent.Util.Char)
local CombatState = require(script.Parent.CombatState)
local NetService = require(script.Parent.NetService)

local MobService = {}

MobService.MobDied = Signal.new() -- (mob, killer: Player?)
MobService.MobDamaged = Signal.new() -- (mob, amount, attacker: Player?)
MobService.PlayerParried = Signal.new() -- (player, mob)

export type Mob = {
	Id: number,
	Def: any,
	Model: Model,
	Root: BasePart,
	Humanoid: Humanoid,
	Highlight: Highlight,
	BarFill: Frame,
	Health: number,
	MaxHealth: number,
	DamageMult: number,
	RunId: string,
	GetTargets: () -> { Player },
	State: string, -- chase | windup | slam | stunned | dead
	Target: Player?,
	ActionEndsAt: number,
	NextAttackAt: number,
	NextSlamAt: number,
	StunUntil: number,
	SlamPosition: Vector3?,
	SlamMarker: BasePart?,
}

export type SpawnOptions = {
	RunId: string,
	HealthMult: number?,
	DamageMult: number?,
	GetTargets: () -> { Player },
}

local active: { [Mob]: boolean } = {}
local pool: { [string]: { Model } } = {}
local nextId = 0

local mobsFolder: Folder
local poolFolder: Folder

-- ===== Сборка модели =====

local function addHealthBar(root: BasePart, def: any): Frame
	local gui = Instance.new("BillboardGui")
	gui.Name = "HealthBar"
	gui.Size = UDim2.new(if def.IsBoss then 7 else 3.5, 0, if def.IsBoss then 0.6 else 0.4, 0)
	gui.StudsOffsetWorldSpace = Vector3.new(0, def.Size.Y / 2 + 1.2, 0)
	gui.MaxDistance = 90
	gui.LightInfluence = 0
	gui.Adornee = root
	local bg = Instance.new("Frame")
	bg.Size = UDim2.fromScale(1, 1)
	bg.BackgroundColor3 = Color3.fromRGB(20, 20, 30)
	bg.BorderSizePixel = 0
	bg.Parent = gui
	local fill = Instance.new("Frame")
	fill.Name = "Fill"
	fill.Size = UDim2.fromScale(1, 1)
	fill.BackgroundColor3 = if def.IsBoss then Color3.fromRGB(255, 70, 70) else Color3.fromRGB(230, 90, 60)
	fill.BorderSizePixel = 0
	fill.Parent = bg
	gui.Parent = root
	return fill
end

local function buildModel(def: any): Model
	local folder = ServerStorage:FindFirstChild("Mobs")
	local template = folder and folder:FindFirstChild(def.Id)
	if
		template
		and template:IsA("Model")
		and template:FindFirstChildOfClass("Humanoid")
		and template:FindFirstChild("HumanoidRootPart")
	then
		local model = template:Clone()
		model.PrimaryPart = model:FindFirstChild("HumanoidRootPart") :: BasePart
		return model
	end

	-- заглушка: блок с «глазами»
	local model = Instance.new("Model")
	model.Name = def.Id
	local root = Instance.new("Part")
	root.Name = "HumanoidRootPart"
	root.Size = def.Size
	root.Color = def.Color
	root.Material = Enum.Material.SmoothPlastic
	root.CanCollide = false
	root.Parent = model

	local eye = Instance.new("Part")
	eye.Name = "Eyes"
	eye.Size = Vector3.new(def.Size.X * 0.6, math.max(0.3, def.Size.Y * 0.08), 0.1)
	eye.Color = Color3.fromRGB(255, 170, 60)
	eye.Material = Enum.Material.Neon
	eye.CanCollide = false
	eye.CanQuery = false
	eye.Massless = true
	eye.CFrame = root.CFrame * CFrame.new(0, def.Size.Y * 0.3, -def.Size.Z / 2 - 0.05)
	local weld = Instance.new("WeldConstraint")
	weld.Part0 = root
	weld.Part1 = eye
	weld.Parent = eye
	eye.Parent = model

	local humanoid = Instance.new("Humanoid")
	humanoid.RigType = Enum.HumanoidRigType.R15
	humanoid.HipHeight = 0.05
	humanoid.Parent = model

	model.PrimaryPart = root
	return model
end

local function prepareModel(def: any): Model
	local model = buildModel(def)
	local humanoid = model:FindFirstChildOfClass("Humanoid") :: Humanoid
	humanoid.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
	humanoid.HealthDisplayType = Enum.HumanoidHealthDisplayType.AlwaysOff
	humanoid.BreakJointsOnDeath = false
	humanoid.RequiresNeck = false
	humanoid:SetStateEnabled(Enum.HumanoidStateType.Dead, false)
	humanoid:SetStateEnabled(Enum.HumanoidStateType.FallingDown, false)
	humanoid:SetStateEnabled(Enum.HumanoidStateType.Ragdoll, false)
	humanoid:SetStateEnabled(Enum.HumanoidStateType.Seated, false)

	local highlight = Instance.new("Highlight")
	highlight.Name = "WindupHighlight"
	highlight.FillColor = Color3.fromRGB(255, 40, 40)
	highlight.OutlineColor = Color3.fromRGB(255, 220, 220)
	highlight.FillTransparency = 0.45
	highlight.Enabled = false
	highlight.Parent = model

	addHealthBar(model.PrimaryPart :: BasePart, def)
	return model
end

-- ===== Публичное API =====

function MobService:Spawn(mobId: string, cframe: CFrame, options: SpawnOptions): Mob?
	local def = Config.Mobs[mobId]
	if not def then
		warn(`[Mobs] неизвестный моб {mobId}`)
		return nil
	end

	local list = pool[mobId]
	local model: Model
	if list and #list > 0 then
		model = table.remove(list) :: Model
	else
		model = prepareModel(def)
	end

	local root = model.PrimaryPart :: BasePart
	local humanoid = model:FindFirstChildOfClass("Humanoid") :: Humanoid
	local highlight = model:FindFirstChild("WindupHighlight") :: Highlight
	local bar = root:FindFirstChild("HealthBar") :: BillboardGui
	local fill = (bar:FindFirstChildOfClass("Frame") :: Frame):FindFirstChild("Fill") :: Frame

	local maxHealth = math.floor(def.MaxHealth * (options.HealthMult or 1))
	fill.Size = UDim2.fromScale(1, 1)
	highlight.Enabled = false
	humanoid.WalkSpeed = def.WalkSpeed

	model:PivotTo(cframe)
	model.Parent = mobsFolder
	if root:CanSetNetworkOwnership() then
		root:SetNetworkOwner(nil) -- физикой мобов владеет сервер
	end

	nextId += 1
	local now = os.clock()
	local mob: Mob = {
		Id = nextId,
		Def = def,
		Model = model,
		Root = root,
		Humanoid = humanoid,
		Highlight = highlight,
		BarFill = fill,
		Health = maxHealth,
		MaxHealth = maxHealth,
		DamageMult = options.DamageMult or 1,
		RunId = options.RunId,
		GetTargets = options.GetTargets,
		State = "chase",
		Target = nil,
		ActionEndsAt = 0,
		NextAttackAt = now + 1, -- секунда на «появление»
		NextSlamAt = now + 4,
		StunUntil = 0,
		SlamPosition = nil,
		SlamMarker = nil,
	}
	active[mob] = true
	return mob
end

local function clearSlam(mob: Mob)
	if mob.SlamMarker then
		mob.SlamMarker:Destroy()
		mob.SlamMarker = nil
	end
	mob.SlamPosition = nil
end

local function release(mob: Mob)
	active[mob] = nil
	clearSlam(mob)
	mob.Highlight.Enabled = false
	mob.Model.Parent = poolFolder
	local list = pool[mob.Def.Id]
	if not list then
		list = {}
		pool[mob.Def.Id] = list
	end
	table.insert(list, mob.Model)
end

function MobService:IsAlive(mob: Mob): boolean
	return active[mob] == true and mob.State ~= "dead"
end

-- true, если моб умер от этого удара
function MobService:Damage(mob: Mob, amount: number, attacker: Player?): boolean
	if not MobService:IsAlive(mob) then
		return false
	end
	mob.Health = math.max(0, mob.Health - amount)
	mob.BarFill.Size = UDim2.fromScale(mob.Health / mob.MaxHealth, 1)
	MobService.MobDamaged:Fire(mob, amount, attacker)
	if mob.Health > 0 then
		return false
	end

	mob.State = "dead"
	clearSlam(mob)
	mob.Highlight.Enabled = false
	mob.Humanoid:MoveTo(mob.Root.Position)
	NetService:FxNear(mob.Root.Position, Config.Combat.DamageFxRadius, {
		Kind = "MobDeath",
		Position = mob.Root.Position,
		Boss = mob.Def.IsBoss,
	})
	MobService.MobDied:Fire(mob, attacker)
	task.delay(0.4, function()
		if active[mob] then
			release(mob)
		end
	end)
	return true
end

function MobService:Stun(mob: Mob, seconds: number)
	if not MobService:IsAlive(mob) then
		return
	end
	mob.State = "stunned"
	mob.StunUntil = os.clock() + seconds
	mob.Highlight.Enabled = false
	clearSlam(mob)
	mob.Humanoid:MoveTo(mob.Root.Position)
end

-- Живые мобы в радиусе (с учётом их размера)
function MobService:GetMobsInRadius(position: Vector3, radius: number): { Mob }
	local result = {}
	for mob in active do
		if mob.State ~= "dead" then
			local offset = mob.Root.Position - position
			local flat = Vector3.new(offset.X, 0, offset.Z).Magnitude
			local half = math.max(mob.Def.Size.X, mob.Def.Size.Z) / 2
			if flat - half <= radius and math.abs(offset.Y) < 12 + mob.Def.Size.Y then
				table.insert(result, mob)
			end
		end
	end
	return result
end

function MobService:DespawnRun(runId: string)
	local list = {}
	for mob in active do
		if mob.RunId == runId then
			table.insert(list, mob)
		end
	end
	for _, mob in list do
		mob.State = "dead"
		release(mob)
	end
end

-- ===== ИИ =====

local function hitPlayer(mob: Mob, player: Player, rawDamage: number)
	local humanoid = Char.Humanoid(player)
	local root = Char.Root(player)
	if not humanoid or not root or humanoid.Health <= 0 then
		return
	end
	local damage = math.floor(rawDamage * mob.DamageMult + 0.5)
	local final, result = CombatState.Resolve(player, damage)
	if result == "parry" then
		MobService:Stun(mob, Config.Combat.ParryStunSeconds)
		MobService.PlayerParried:Fire(player, mob)
	else
		humanoid:TakeDamage(final)
	end
	NetService:FxNear(root.Position, Config.Combat.DamageFxRadius, {
		Kind = "PlayerHit",
		Position = root.Position,
		Amount = final,
		Result = result,
		UserId = player.UserId,
	})
end

local function nearestTarget(mob: Mob): (Player?, BasePart?, number)
	local best: Player? = nil
	local bestRoot: BasePart? = nil
	local bestDist = math.huge
	for _, player in mob.GetTargets() do
		if Char.IsAlive(player) then
			local root = Char.Root(player) :: BasePart
			local d = (root.Position - mob.Root.Position).Magnitude
			if d < bestDist then
				best, bestRoot, bestDist = player, root, d
			end
		end
	end
	return best, bestRoot, bestDist
end

local function face(mob: Mob, position: Vector3)
	local from = mob.Root.Position
	local flat = Vector3.new(position.X, from.Y, position.Z)
	if (flat - from).Magnitude > 0.1 then
		mob.Root.CFrame = CFrame.lookAt(from, flat)
	end
end

local raycastParams = RaycastParams.new()
raycastParams.FilterType = Enum.RaycastFilterType.Exclude

local function startSlam(mob: Mob, targetRoot: BasePart, now: number)
	local def = mob.Def
	raycastParams.FilterDescendantsInstances = { mobsFolder, targetRoot.Parent :: Instance }
	local hit = workspace:Raycast(targetRoot.Position, Vector3.new(0, -25, 0), raycastParams)
	local ground = if hit then hit.Position else targetRoot.Position - Vector3.new(0, 3, 0)

	mob.State = "slam"
	mob.ActionEndsAt = now + def.SlamWindup
	mob.NextSlamAt = now + def.SlamCooldown
	mob.SlamPosition = ground
	mob.Highlight.Enabled = true
	mob.Humanoid:MoveTo(mob.Root.Position)
	face(mob, ground)

	local marker = Instance.new("Part")
	marker.Name = "SlamWarning"
	marker.Shape = Enum.PartType.Cylinder
	marker.Size = Vector3.new(0.2, def.SlamRadius * 2, def.SlamRadius * 2)
	marker.CFrame = CFrame.new(ground + Vector3.new(0, 0.12, 0)) * CFrame.Angles(0, 0, math.rad(90))
	marker.Anchored = true
	marker.CanCollide = false
	marker.CanQuery = false
	marker.CanTouch = false
	marker.Material = Enum.Material.Neon
	marker.Color = Color3.fromRGB(255, 40, 40)
	marker.Transparency = 0.55
	marker.Parent = mobsFolder
	mob.SlamMarker = marker
end

local function resolveSlam(mob: Mob)
	local center = mob.SlamPosition
	if center then
		for _, player in mob.GetTargets() do
			local root = Char.Root(player)
			if root and Char.IsAlive(player) then
				local offset = root.Position - center
				if Vector3.new(offset.X, 0, offset.Z).Magnitude <= mob.Def.SlamRadius and math.abs(offset.Y) < 8 then
					hitPlayer(mob, player, mob.Def.SlamDamage)
				end
			end
		end
		NetService:FxNear(center, Config.Combat.DamageFxRadius, { Kind = "Slam", Position = center, Radius = mob.Def.SlamRadius })
	end
	clearSlam(mob)
	mob.Highlight.Enabled = false
	mob.State = "chase"
end

local function tickMob(mob: Mob, now: number)
	local state = mob.State
	if state == "dead" then
		return
	end
	if state == "stunned" then
		if now < mob.StunUntil then
			return
		end
		mob.State = "chase"
	elseif state == "windup" then
		if now < mob.ActionEndsAt then
			return
		end
		mob.Highlight.Enabled = false
		mob.State = "chase"
		local target = mob.Target
		local targetRoot = target and Char.Root(target)
		if target and targetRoot and (targetRoot.Position - mob.Root.Position).Magnitude <= mob.Def.AttackRange * 1.4 then
			hitPlayer(mob, target, mob.Def.Damage)
		end
		return
	elseif state == "slam" then
		if now >= mob.ActionEndsAt then
			resolveSlam(mob)
		end
		return
	end

	-- погоня
	local target, targetRoot, dist = nearestTarget(mob)
	if not target or not targetRoot then
		mob.Humanoid:MoveTo(mob.Root.Position)
		return
	end
	local def = mob.Def
	local half = math.max(def.Size.X, def.Size.Z) / 2

	if def.IsBoss and def.SlamDamage and now >= mob.NextSlamAt and dist <= def.SlamTriggerRange + half then
		startSlam(mob, targetRoot, now)
	elseif dist <= def.AttackRange + half and now >= mob.NextAttackAt then
		mob.State = "windup"
		mob.Target = target
		mob.ActionEndsAt = now + def.WindupSeconds
		mob.NextAttackAt = now + def.AttackCooldown
		mob.Highlight.Enabled = true
		mob.Humanoid:MoveTo(mob.Root.Position)
		face(mob, targetRoot.Position)
	elseif dist > def.AttackRange * 0.6 + half then
		mob.Humanoid:MoveTo(targetRoot.Position)
	else
		mob.Humanoid:MoveTo(mob.Root.Position)
		face(mob, targetRoot.Position)
	end
end

function MobService:Init()
	mobsFolder = Instance.new("Folder")
	mobsFolder.Name = "Mobs"
	mobsFolder.Parent = workspace
	poolFolder = Instance.new("Folder")
	poolFolder.Name = "MobPool"
	poolFolder.Parent = ServerStorage
end

function MobService:Start()
	local accumulator = 0
	RunService.Heartbeat:Connect(function(dt)
		accumulator += dt
		if accumulator < Config.Combat.MobAiTickSeconds then
			return
		end
		accumulator = 0
		local now = os.clock()
		for mob in active do
			local ok, err = pcall(tickMob, mob, now)
			if not ok then
				warn(`[Mobs] ошибка ИИ {mob.Def.Id}: {err}`)
				mob.State = "dead"
				release(mob)
			end
		end
	end)
end

return MobService
