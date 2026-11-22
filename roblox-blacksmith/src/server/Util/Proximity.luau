--!strict
-- Серверная проверка дистанции до объектов карты (кузня, торговец, жила).

local CollectionService = game:GetService("CollectionService")

local Char = require(script.Parent.Char)

local Proximity = {}

function Proximity.PositionOf(instance: Instance): Vector3?
	if instance:IsA("BasePart") then
		return instance.Position
	elseif instance:IsA("Model") then
		return instance:GetPivot().Position
	elseif instance:IsA("Attachment") then
		return instance.WorldPosition
	end
	return nil
end

function Proximity.DistanceTo(player: Player, position: Vector3): number
	local root = Char.Root(player)
	if not root then
		return math.huge
	end
	return (root.Position - position).Magnitude
end

-- Ближайший объект с тегом в пределах maxDistance (или nil)
function Proximity.NearTagged(player: Player, tag: string, maxDistance: number): Instance?
	local root = Char.Root(player)
	if not root then
		return nil
	end
	local best: Instance? = nil
	local bestDist = maxDistance
	for _, instance in CollectionService:GetTagged(tag) do
		if instance:IsDescendantOf(workspace) then
			local pos = Proximity.PositionOf(instance)
			if pos then
				local d = (root.Position - pos).Magnitude
				if d <= bestDist then
					best = instance
					bestDist = d
				end
			end
		end
	end
	return best
end

-- Куда повесить ProximityPrompt / Attachment для объекта
function Proximity.AnchorPart(instance: Instance): BasePart?
	if instance:IsA("BasePart") then
		return instance
	elseif instance:IsA("Model") then
		return instance.PrimaryPart or instance:FindFirstChildWhichIsA("BasePart", true)
	end
	return nil
end

return Proximity
