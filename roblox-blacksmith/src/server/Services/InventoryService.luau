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
local ProgressService = require(script.Parent.ProgressService)

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
	rarity: string,
	traits: { string }?
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
		Traits = traits or {},
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
	data.Shop.Listings[weaponId] = nil
	if data.EquippedWeapon == weaponId then
		data.EquippedWeapon = ""
		InventoryService:RefreshVisual(player)
	end
	if not silent then
		DataService:Replicate(player, "Weapons", "EquippedWeapon")
	end
	return weapon
end

function InventoryService:IsListed(player: Player, weaponId: string): boolean
	local data = DataService:Get(player)
	return data ~= nil and data.Shop.Listings[weaponId] ~= nil
end

-- Можно ли отдать оружие другому игроку (обмен): своё, не надето, не в лавке
function InventoryService:CanTransfer(player: Player, weaponId: string): boolean
	local data = DataService:Get(player)
	return data ~= nil
		and data.Weapons[weaponId] ~= nil
		and data.EquippedWeapon ~= weaponId
		and data.Shop.Listings[weaponId] == nil
end

-- Передача между игроками: оружие переходит с тем же Id.
-- Вызывается только из TradeService/ShopService внутри одного блока без yield.
function InventoryService:TakeForTransfer(player: Player, weaponId: string): Types.WeaponData?
	local data = DataService:Get(player)
	if not data then
		return nil
	end
	local weapon = data.Weapons[weaponId]
	if not weapon then
		return nil
	end
	data.Weapons[weaponId] = nil
	data.Shop.Listings[weaponId] = nil
	if data.EquippedWeapon == weaponId then
		data.EquippedWeapon = ""
		InventoryService:RefreshVisual(player)
	end
	return weapon
end

function InventoryService:GiveTransferred(player: Player, weapon: Types.WeaponData): boolean
	local data = DataService:Get(player)
	if not data or data.Weapons[weapon.Id] ~= nil then
		-- тот же Id уже есть у получателя: это был бы дюп
		warn(`[Inventory] отказ: {player.Name} уже владеет {weapon.Id}`)
		return false
	end
	data.Weapons[weapon.Id] = table.clone(weapon)
	return true
end

function InventoryService:GetEquipped(player: Player): Types.WeaponData?
	local data = DataService:Get(player)
	if not data or data.EquippedWeapon == "" then
		return nil
	end
	return data.Weapons[data.EquippedWeapon]
end

local buildVisual: (weapon: Types.WeaponData) -> (Instance?, BasePart?)

-- Модель оружия для витрины лавки (без сварки с рукой)
function InventoryService:BuildWeaponVisual(weapon: Types.WeaponData): (Instance?, BasePart?)
	return buildVisual(weapon)
end

function buildVisual(weapon: Types.WeaponData): (Instance?, BasePart?)
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
		part.Material = Enum.Material.Plastic -- стад-стиль: пластик с узором Weld
		part.TopSurface = Enum.SurfaceType.Weld
		part.BottomSurface = Enum.SurfaceType.Weld
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

	-- аура редкого оружия (Epic+): частицы цвета редкости, у Divine ещё и свет
	local rarity = Config.Rarities[weapon.Rarity]
	if rarity and rarity.Order >= 3 then
		local aura = Instance.new("ParticleEmitter")
		aura.Name = "RarityAura"
		aura.Color = ColorSequence.new(rarity.Color)
		aura.LightEmission = 1
		aura.Rate = 4 + rarity.Order * 3
		aura.Lifetime = NumberRange.new(0.4, 0.8)
		aura.Speed = NumberRange.new(0.5, 1.5)
		aura.Size = NumberSequence.new(0.25, 0)
		aura.SpreadAngle = Vector2.new(180, 180)
		aura.Parent = primary
		if rarity.Order >= 6 then
			local glow = Instance.new("PointLight")
			glow.Color = rarity.Color
			glow.Range = 8
			glow.Brightness = 1.5
			glow.Parent = primary
		end
	end
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
	if data.Shop.Listings[weaponId] then
		return false, "ERR_LISTED"
	end
	data.EquippedWeapon = weaponId
	DataService:Replicate(player, "EquippedWeapon")
	InventoryService:RefreshVisual(player)
	ProgressService:Record(player, "WeaponEquipped")
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
