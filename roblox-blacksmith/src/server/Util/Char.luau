--!strict
-- Быстрый доступ к персонажу игрока.

local Char = {}

function Char.Root(player: Player): BasePart?
	local character = player.Character
	if not character then
		return nil
	end
	return character:FindFirstChild("HumanoidRootPart") :: BasePart?
end

function Char.Humanoid(player: Player): Humanoid?
	local character = player.Character
	if not character then
		return nil
	end
	return character:FindFirstChildOfClass("Humanoid")
end

function Char.IsAlive(player: Player): boolean
	local humanoid = Char.Humanoid(player)
	return humanoid ~= nil and humanoid.Health > 0 and Char.Root(player) ~= nil
end

return Char
