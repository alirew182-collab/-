--!strict
--[[
	БОЙ ИГРОКА. Клиент шлёт только «я ударил» / «я держу блок».
	Сервер сам решает: кулдаун оружия, кого задело (дистанция + угол перед игроком), сколько урона.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local WeaponMath = require(ReplicatedStorage.Shared.WeaponMath)
local Char = require(script.Parent.Parent.Util.Char)
local Guards = require(script.Parent.Parent.Util.Guards)
local CombatState = require(script.Parent.CombatState)
local DataService = require(script.Parent.DataService)
local InventoryService = require(script.Parent.InventoryService)
local LevelService = require(script.Parent.LevelService)
local MobService = require(script.Parent.MobService)
local NetService = require(script.Parent.NetService)

local CombatService = {}

local lastAttack: { [Player]: number } = {}
local rng = Random.new()

-- Сетевой допуск: клиентский кулдаун и серверный могут разойтись на пинг
local COOLDOWN_TOLERANCE = 0.85

local function attack(player: Player)
	if not DataService:IsLoaded(player) or not Char.IsAlive(player) then
		return
	end
	local root = Char.Root(player) :: BasePart

	local weapon = InventoryService:GetEquipped(player)
	local def = if weapon then Config.Weapons[weapon.Type] else nil
	local stats = def or Config.Combat.Fists
	local now = os.clock()
	if (lastAttack[player] or 0) + stats.AttackCooldown * COOLDOWN_TOLERANCE > now then
		return
	end
	lastAttack[player] = now

	local baseDamage = if weapon then WeaponMath.Damage(weapon) else Config.Combat.Fists.Damage
	local look = root.CFrame.LookVector
	local flatLook = Vector3.new(look.X, 0, look.Z).Unit

	local candidates = MobService:GetMobsInRadius(root.Position, stats.Range)
	table.sort(candidates, function(a, b)
		return (a.Root.Position - root.Position).Magnitude < (b.Root.Position - root.Position).Magnitude
	end)

	local hits = 0
	for _, mob in candidates do
		if hits >= stats.MaxTargets then
			break
		end
		local offset = mob.Root.Position - root.Position
		local flat = Vector3.new(offset.X, 0, offset.Z)
		-- вплотную — попадаем всегда, иначе проверяем угол
		local inArc = flat.Magnitude < 2 or flatLook:Dot(flat.Unit) >= stats.Arc
		if inArc then
			hits += 1
			local damage =
				math.max(1, math.floor(baseDamage * LevelService:DamageMult(player) * rng:NextNumber(0.9, 1.1) + 0.5))
			MobService:Damage(mob, damage, player)
			NetService:FxNear(mob.Root.Position, Config.Combat.DamageFxRadius, {
				Kind = "Damage",
				Position = mob.Root.Position + Vector3.new(0, mob.Def.Size.Y / 2, 0),
				Amount = damage,
				ByUserId = player.UserId,
			})
		end
	end
end

function CombatService:Start()
	NetService:OnEvent("CombatAttack", attack)

	NetService:OnEvent("CombatBlock", function(player: Player, blocking: any)
		if not Guards.Boolean(blocking) then
			return
		end
		if blocking and not Char.IsAlive(player) then
			return
		end
		CombatState.SetBlocking(player, blocking)
	end)

	MobService.PlayerParried:Connect(function(player: Player)
		-- надпись «ПАРИРОВАНИЕ!» показывает клиент по Fx (быстрее, чем окно Системы)
		DataService:AddStat(player, "Parries", 1)
	end)

	Players.PlayerRemoving:Connect(function(player)
		lastAttack[player] = nil
	end)
end

return CombatService
