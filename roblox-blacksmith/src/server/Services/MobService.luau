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
	Alert: BillboardGui, -- «!» над головой во время замаха
	BarFill: Frame,
	Burn: ParticleEmitter,
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
	Home: Vector3?,
	LeashRadius: number?,
	AggroRadius: number?,
	BurnUntil: number,
	BurnDps: number,
	BurnAcc: number,
	BurnAttacker: Player?,
}

export type SpawnOptions = {
	RunId: string,
	HealthMult: number?,
	DamageMult: number?,
	GetTargets: () -> { Player },
	Home: Vector3?, -- мобы открытого мира возвращаются сюда
	LeashRadius: number?,
	AggroRadius: number?,
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
	gui.Size = UDim2.new(if def.IsBoss then 8 else 4.5, 0, if def.IsBoss then 1.6 else 1.1, 0)
	gui.StudsOffsetWorldSpace = Vector3.new(0, def.Size.Y / 2 + 1.4, 0)
	gui.MaxDistance = 90
	gui.LightInfluence = 0
	gui.Adornee = root
	-- имя переводит клиент (LocalizationController): атрибуты LocKey + name + level
	local name = Instance.new("TextLabel")
	name.Name = "MobName"
	name.Size = UDim2.fromScale(1, 0.55)
	name.BackgroundTransparency = 1
	name.Font = Enum.Font.GothamBold
	name.TextScaled = true
	name.TextColor3 = if def.IsBoss then Color3.fromRGB(255, 120, 120) else Color3.new(1, 1, 1)
	name.TextStrokeTransparency = 0.3
	name.Text = def.Id
	name:SetAttribute("LocKey", "UI_MOB_NAME")
	name:SetAttribute("name", "@" .. def.NameKey)
	name:SetAttribute("level", def.Level or 1)
	name.Parent = gui
	local bg = Instance.new("Frame")
	bg.Name = "Bg"
	bg.Size = UDim2.fromScale(1, 0.38)
	bg.Position = UDim2.fromScale(0, 0.62)
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

	local root = model.PrimaryPart :: BasePart

	-- «!» над головой во время замаха (Highlight не используем: их максимум 31 на экране)
	local alert = Instance.new("BillboardGui")
	alert.Name = "Alert"
	alert.Size = UDim2.fromScale(2.2, 2.2)
	alert.StudsOffsetWorldSpace = Vector3.new(0, def.Size.Y / 2 + (if def.IsBoss then 4 else 3), 0)
	alert.AlwaysOnTop = true
	alert.LightInfluence = 0
	alert.MaxDistance = 80
	alert.Enabled = false
	local mark = Instance.new("TextLabel")
	mark.Size = UDim2.fromScale(1, 1)
	mark.BackgroundTransparency = 1
	mark.Font = Enum.Font.GothamBlack
	mark.TextScaled = true
	mark.Text = "!"
	mark.TextColor3 = Color3.fromRGB(255, 60, 60)
	mark.TextStrokeTransparency = 0
	mark.Parent = alert
	alert.Parent = root

	local burn = Instance.new("ParticleEmitter")
	burn.Name = "Burn"
	burn.Enabled = false
	burn.Rate = 15
	burn.Lifetime = NumberRange.new(0.4, 0.8)
	burn.Speed = NumberRange.new(2, 5)
	burn.Color = ColorSequence.new(Color3.fromRGB(255, 160, 40), Color3.fromRGB(255, 50, 20))
	burn.Size = NumberSequence.new(0.8, 0)
	burn.LightEmission = 1
	burn.Parent = root

	addHealthBar(root, def)
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
	local alert = root:FindFirstChild("Alert") :: BillboardGui
	local burn = root:FindFirstChild("Burn") :: ParticleEmitter
	local bar = root:FindFirstChild("HealthBar") :: BillboardGui
	local fill = (bar:FindFirstChild("Bg") :: Frame):FindFirstChild("Fill") :: Frame

	local maxHealth = math.floor(def.MaxHealth * (options.HealthMult or 1))
	fill.Size = UDim2.fromScale(1, 1)
	alert.Enabled = false
	burn.Enabled = false
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
		Alert = alert,
		BarFill = fill,
		Burn = burn,
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
		Home = options.Home,
		LeashRadius = options.LeashRadius,
		AggroRadius = options.AggroRadius,
		BurnUntil = 0,
		BurnDps = 0,
		BurnAcc = 0,
		BurnAttacker = nil,
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
	mob.Alert.Enabled = false
	mob.Burn.Enabled = false
	mob.BurnUntil = 0
	mob.Model.Parent = poolFolder
	local list = pool[mob.Def.Id]
	if not list then
		list = {}
		pool[mob.Def.Id] = list
	end
	table.insert(list, mob.Model)
end

local function setBar(mob: Mob)
	mob.BarFill.Size = UDim2.fromScale(math.clamp(mob.Health / mob.MaxHealth, 0, 1), 1)
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
	setBar(mob)
	MobService.MobDamaged:Fire(mob, amount, attacker)
	if mob.Health > 0 then
		return false
	end

	mob.State = "dead"
	clearSlam(mob)
	mob.Alert.Enabled = false
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
	mob.Alert.Enabled = false
	clearSlam(mob)
	mob.Humanoid:MoveTo(mob.Root.Position)
end

-- Горение (черта оружия «Огонь»): dps урона в секунду в течение seconds
function MobService:ApplyBurn(mob: Mob, dps: number, seconds: number, attacker: Player?)
	if not MobService:IsAlive(mob) or dps <= 0 then
		return
	end
	mob.BurnDps = math.max(mob.BurnDps, dps)
	mob.BurnUntil = os.clock() + seconds
	mob.BurnAttacker = attacker
	mob.Burn.Enabled = true
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
	mob.Alert.Enabled = true
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
	mob.Alert.Enabled = false
	mob.State = "chase"
end

local function tickBurn(mob: Mob, now: number): boolean
	if mob.BurnUntil <= 0 then
		return false
	end
	if now >= mob.BurnUntil then
		mob.BurnUntil = 0
		mob.BurnDps = 0
		mob.BurnAcc = 0
		mob.Burn.Enabled = false
		return false
	end
	mob.BurnAcc += mob.BurnDps * Config.Combat.MobAiTickSeconds
	if mob.BurnAcc >= 1 then
		local damage = math.floor(mob.BurnAcc)
		mob.BurnAcc -= damage
		NetService:FxNear(mob.Root.Position, Config.Combat.DamageFxRadius, {
			Kind = "Burn",
			Position = mob.Root.Position + Vector3.new(0, mob.Def.Size.Y / 2, 0),
			Amount = damage,
		})
		return MobService:Damage(mob, damage, mob.BurnAttacker)
	end
	return false
end

local function tickMob(mob: Mob, now: number)
	local state = mob.State
	if state == "dead" then
		return
	end
	if tickBurn(mob, now) then
		return -- сгорел
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
		mob.Alert.Enabled = false
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
	if target and targetRoot then
		-- мобы открытого мира: не видят далёких и не уходят далеко от дома
		if mob.AggroRadius and dist > mob.AggroRadius then
			target, targetRoot = nil, nil
		elseif mob.Home and mob.LeashRadius and (targetRoot.Position - mob.Home).Magnitude > mob.LeashRadius then
			target, targetRoot = nil, nil
		end
	end
	if not target or not targetRoot then
		local home = mob.Home
		if home and (mob.Root.Position - home).Magnitude > 4 then
			mob.Humanoid:MoveTo(home)
		else
			mob.Humanoid:MoveTo(mob.Root.Position)
		end
		-- вне боя быстро восстанавливается (нельзя «кайтить» по чуть-чуть)
		if home and mob.Health < mob.MaxHealth then
			mob.Health = math.min(mob.MaxHealth, mob.Health + mob.MaxHealth * 0.08)
			setBar(mob)
		end
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
		mob.Alert.Enabled = true
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
