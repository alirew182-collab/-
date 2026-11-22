--!strict
-- Состояние блока/парирования игроков. Используется MobService (входящий урон) и CombatService.

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Char = require(script.Parent.Parent.Util.Char)

local CombatState = {}

type State = { Blocking: boolean, BlockStartedAt: number, LastToggle: number }
local states: { [Player]: State } = {}

local function get(player: Player): State
	local s = states[player]
	if not s then
		s = { Blocking = false, BlockStartedAt = 0, LastToggle = 0 }
		states[player] = s
	end
	return s
end

function CombatState.SetBlocking(player: Player, blocking: boolean)
	local s = get(player)
	if s.Blocking == blocking then
		return
	end
	local now = os.clock()
	if blocking and now - s.LastToggle < Config.Combat.BlockToggleCooldown then
		return -- спам блоком ради окна парирования не пройдёт
	end
	s.LastToggle = now
	s.Blocking = blocking
	if blocking then
		s.BlockStartedAt = now
	end
	local humanoid = Char.Humanoid(player)
	if humanoid then
		humanoid.WalkSpeed = if blocking then Config.Combat.BlockWalkSpeed else Config.Combat.DefaultWalkSpeed
	end
end

function CombatState.IsBlocking(player: Player): boolean
	return get(player).Blocking
end

-- Итоговый урон по игроку и результат: "hit" | "block" | "parry"
function CombatState.Resolve(player: Player, damage: number): (number, string)
	local s = get(player)
	if not s.Blocking then
		return damage, "hit"
	end
	if os.clock() - s.BlockStartedAt <= Config.Combat.ParryWindowSeconds then
		return 0, "parry"
	end
	return math.floor(damage * (1 - Config.Combat.BlockDamageReduction) + 0.5), "block"
end

function CombatState:Start()
	Players.PlayerAdded:Connect(function(player)
		player.CharacterAdded:Connect(function()
			states[player] = nil -- новый персонаж — без блока
		end)
	end)
	for _, player in Players:GetPlayers() do
		player.CharacterAdded:Connect(function()
			states[player] = nil
		end)
	end
	Players.PlayerRemoving:Connect(function(player)
		states[player] = nil
	end)
end

return CombatState
