--!strict
--[[
	ИНВЕНТАРЬ: оружие (по уникальному GUID), надетое оружие и его модель в руке, кирки.
	Модель оружия: ServerStorage/WeaponModels/<Type> (Model или BasePart).
	В модели можно добавить Attachment "Grip" в главную часть — за него оружие берётся в руку.
]]

local HttpService = game:GetService("HttpService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local Types = require(ReplicatedStorage.Shared.Types)
local Guards = require(script.Parent.Parent.Util.Guards)
local DataService = require(script.Parent.DataService)
local NetService = require(script.Parent.NetService)
local TutorialService = require(script.Parent.TutorialService)

local InventoryService = {}

local VISUAL_NAME = "EquippedWeapon"

function InventoryService:Capacity(_player: Player): number
	return Config.Economy.InventoryCapacity -- Этап 3: x2 за геймпасс
end

function InventoryService:Count(player: Player): number
	local data = DataService:Get(player)
	if not data then
		return 0
	end
	local n = 0
	for _ in data.Weapons do
		n += 1
	end
	return n
end

function InventoryService:IsFull(player: Player): boolean
	return InventoryService:Count(player) >= InventoryService:Capacity(player)
end

function InventoryService:AddWeapon(
	player: Player,
	weaponType: string,
	oreId: string,
	quality: number,
	rarity: string
): Types.WeaponData?
	local data = DataService:Get(player)
	if not data or InventoryService:IsFull(player) then
		return nil
	end
	local weapon: Types.WeaponData = {
		Id = HttpService:GenerateGUID(false),
		Type = weaponType,
		Ore = oreId,
		Quality = math.clamp(math.floor(quality), 0, 100),
		Rarity = rarity,
		Traits = {},
		Xp = 0,
		CreatedAt = os.time(),
	}
	data.Weapons[weapon.Id] = weapon
	DataService:Replicate(player, "Weapons")
	return weapon
end

-- Удаляет оружие и возвращает его (или nil). Надетое снимается.
-- silent = true → не отправлять клиенту (для пакетной продажи; вызывающий сам сделает Replicate)
function InventoryService:RemoveWeapon(player: Player, weaponId: string, silent: boolean?): Types.WeaponData?
	local data = DataService:Get(player)
	if not data then
		return nil
	end
	local weapon = data.Weapons[weaponId]
	if not weapon then
		return nil
	end
	data.Weapons[weaponId] = nil
	if data.EquippedWeapon == weaponId then
		data.EquippedWeapon = ""
		InventoryService:RefreshVisual(player)
	end
	if not silent then
		DataService:Replicate(player, "Weapons", "EquippedWeapon")
	end
	return weapon
end

function InventoryService:GetEquipped(player: Player): Types.WeaponData?
	local data = DataService:Get(player)
	if not data or data.EquippedWeapon == "" then
		return nil
	end
	return data.Weapons[data.EquippedWeapon]
end

local function buildVisual(weapon: Types.WeaponData): (Instance?, BasePart?)
	local def = Config.Weapons[weapon.Type]
	if not def then
		return nil, nil
	end
	local folder = ServerStorage:FindFirstChild("WeaponModels")
	local template = folder and folder:FindFirstChild(weapon.Type)
	local root: Instance
	local primary: BasePart?

	if template and (template:IsA("Model") or template:IsA("BasePart")) then
		root = template:Clone()
		if root:IsA("BasePart") then
			primary = root
		else
			local model = root :: Model
			primary = model.PrimaryPart or model:FindFirstChildWhichIsA("BasePart", true)
		end
	else
		-- заглушка: цвет руды
		local ore = Config.Ores[weapon.Ore]
		local part = Instance.new("Part")
		part.Size = def.Size
		part.Material = Enum.Material.Metal
		part.Color = if ore then ore.Color else Color3.new(0.7, 0.7, 0.7)
		root = part
		primary = part
	end

	if not primary then
		root:Destroy()
		return nil, nil
	end

	local parts: { BasePart } = {}
	if root:IsA("BasePart") then
		table.insert(parts, root)
	end
	for _, d in root:GetDescendants() do
		if d:IsA("BasePart") then
			table.insert(parts, d)
		end
	end
	for _, part in parts do
		part.Anchored = false
		part.CanCollide = false
		part.CanQuery = false
		part.CanTouch = false
		part.Massless = true
		if part ~= primary then
			local weld = Instance.new("WeldConstraint")
			weld.Part0 = primary
			weld.Part1 = part
			weld.Parent = part
		end
	end
	root.Name = VISUAL_NAME
	return root, primary
end

function InventoryService:RefreshVisual(player: Player)
	local character = player.Character
	if not character then
		return
	end
	local old = character:FindFirstChild(VISUAL_NAME)
	if old then
		old:Destroy()
	end
	local weapon = InventoryService:GetEquipped(player)
	if not weapon then
		return
	end
	local hand = (character:FindFirstChild("RightHand") or character:FindFirstChild("Right Arm")) :: BasePart?
	if not hand then
		return
	end
	local visual, primary = buildVisual(weapon)
	if not visual or not primary then
		return
	end
	local weld = Instance.new("Weld")
	weld.Part0 = hand
	weld.Part1 = primary
	weld.C0 = CFrame.new(0, -hand.Size.Y / 2, 0)
	local grip = primary:FindFirstChild("Grip")
	if grip and grip:IsA("Attachment") then
		weld.C1 = grip.CFrame
	else
		-- клинок торчит вперёд из кулака
		weld.C1 = CFrame.new(0, 0, primary.Size.Z / 2 - 0.4)
	end
	weld.Parent = primary
	visual.Parent = character
end

function InventoryService:Equip(player: Player, weaponId: string): (boolean, string?)
	local data = DataService:Get(player)
	if not data then
		return false, "ERR_NOT_LOADED"
	end
	local weapon = data.Weapons[weaponId]
	if not weapon then
		return false, "ERR_UNKNOWN_ITEM"
	end
	data.EquippedWeapon = weaponId
	DataService:Replicate(player, "EquippedWeapon")
	InventoryService:RefreshVisual(player)
	TutorialService:Record(player, "WeaponEquipped")
	return true, nil
end

function InventoryService:Start()
	NetService:OnInvoke("EquipWeapon", function(player: Player, weaponId: any)
		if not Guards.String(weaponId, 64) then
			return { ok = false, err = "ERR_BAD_REQUEST" }
		end
		local ok, err = InventoryService:Equip(player, weaponId)
		return { ok = ok, err = err }
	end)

	NetService:OnInvoke("EquipPickaxe", function(player: Player, pickaxeId: any)
		local data = DataService:Get(player)
		if not data then
			return { ok = false, err = "ERR_NOT_LOADED" }
		end
		if not Guards.String(pickaxeId, 32) or not Config.Pickaxes[pickaxeId] then
			return { ok = false, err = "ERR_BAD_REQUEST" }
		end
		if not data.OwnedPickaxes[pickaxeId] then
			return { ok = false, err = "ERR_NOT_OWNED" }
		end
		data.Pickaxe = pickaxeId
		DataService:Replicate(player, "Pickaxe")
		return { ok = true }
	end)

	DataService.Loaded:Connect(function(player: Player)
		player.CharacterAdded:Connect(function(character)
			character:WaitForChild("RightHand", 5)
			InventoryService:RefreshVisual(player)
		end)
		if player.Character then
			InventoryService:RefreshVisual(player)
		end
	end)
end

return InventoryService
