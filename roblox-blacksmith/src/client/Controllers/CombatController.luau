--!strict
--[[
	БОЙ НА КЛИЕНТЕ: кнопки «Атака» и «Блок» (справа снизу, левее кнопки прыжка).
	Удержание «Атаки» = автоатака с кулдауном оружия. ПК: ЛКМ — атака, F — блок.
	Геймпад: R2 — атака, L2 — блок. Сервер всё перепроверяет.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")

local Config = require(ReplicatedStorage.Shared.Config)
local Net = require(ReplicatedStorage.Shared.Net)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local DataController = require(script.Parent.DataController)
local ForgeController = require(script.Parent.ForgeController)
local UIController = require(script.Parent.UIController)

local S = Theme.S
local player = Players.LocalPlayer

local CombatController = {}

local attackButton: TextButton
local blockButton: TextButton
local attackHeld = false
local blocking = false
local lastAttack = 0
local swingTrack: AnimationTrack? = nil

local function weaponStats(): any
	local data = DataController:Get()
	if data and data.EquippedWeapon ~= "" then
		local weapon = data.Weapons[data.EquippedWeapon]
		if weapon and Config.Weapons[weapon.Type] then
			return Config.Weapons[weapon.Type]
		end
	end
	return Config.Combat.Fists
end

local function isAlive(): boolean
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	return humanoid ~= nil and humanoid.Health > 0
end

local function loadSwing()
	swingTrack = nil
	local id = Config.Combat.SwingAnimationId
	if id == "" then
		return
	end
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	local animator = humanoid and humanoid:FindFirstChildOfClass("Animator")
	if animator then
		local animation = Instance.new("Animation")
		animation.AnimationId = id
		swingTrack = animator:LoadAnimation(animation)
	end
end

local function attack()
	if UIController:GetOpenPanel() ~= nil or ForgeController:IsBusy() or not isAlive() then
		return
	end
	local now = os.clock()
	if now - lastAttack < weaponStats().AttackCooldown then
		return
	end
	lastAttack = now
	Net.Event("CombatAttack"):FireServer()
	Sound.Play("Swing", 0.5, 0.95 + math.random() * 0.1)
	if swingTrack then
		swingTrack:Play(0.05)
	end
	attackButton.BackgroundTransparency = 0.4
	task.delay(0.08, function()
		attackButton.BackgroundTransparency = 0.05
	end)
end

local function setBlock(on: boolean)
	if on and not isAlive() then
		on = false
	end
	if on == blocking then
		return
	end
	blocking = on
	Net.Event("CombatBlock"):FireServer(on)
	blockButton.BackgroundColor3 = if on then Theme.Accent else Theme.Panel
	blockButton.TextColor3 = if on then Theme.Bg else Theme.Text
end

local function isPointer(input: InputObject): boolean
	return input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch
end

local function build()
	local cluster = C.Make("Frame", {
		Name = "CombatButtons",
		Size = UDim2.fromOffset(S(190), S(100)),
		Position = UDim2.new(1, -S(175), 1, -S(16)),
		AnchorPoint = Vector2.new(1, 1),
		BackgroundTransparency = 1,
		Parent = UIController.Layers.Hud,
	})
	attackButton = C.Button({
		Name = "Attack",
		Key = "UI_ATTACK",
		Font = Theme.FontTitle,
		TextSize = 16,
		Size = UDim2.fromOffset(S(96), S(96)),
		Position = UDim2.fromScale(1, 1),
		AnchorPoint = Vector2.new(1, 1),
		Color = Color3.fromRGB(150, 45, 45),
		Transparency = 0.05,
		Round = true,
		Silent = true,
		Parent = cluster,
	})
	blockButton = C.Button({
		Name = "Block",
		Key = "UI_BLOCK",
		Font = Theme.FontBold,
		TextSize = 14,
		Size = UDim2.fromOffset(S(70), S(70)),
		Position = UDim2.new(0, 0, 1, 0),
		AnchorPoint = Vector2.new(0, 1),
		Color = Theme.Panel,
		Transparency = 0.05,
		Round = true,
		Silent = true,
		Parent = cluster,
	})

	attackButton.InputBegan:Connect(function(input)
		if isPointer(input) then
			attackHeld = true
			attack()
		end
	end)
	attackButton.InputEnded:Connect(function(input)
		if isPointer(input) then
			attackHeld = false
		end
	end)
	blockButton.InputBegan:Connect(function(input)
		if isPointer(input) then
			setBlock(true)
		end
	end)
	blockButton.InputEnded:Connect(function(input)
		if isPointer(input) then
			setBlock(false)
		end
	end)
end

function CombatController:Start()
	build()

	UserInputService.InputBegan:Connect(function(input, processed)
		if processed then
			return
		end
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.KeyCode == Enum.KeyCode.ButtonR2 then
			attackHeld = true
			attack()
		elseif input.KeyCode == Enum.KeyCode.F or input.KeyCode == Enum.KeyCode.ButtonL2 then
			setBlock(true)
		end
	end)
	UserInputService.InputEnded:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.KeyCode == Enum.KeyCode.ButtonR2 then
			attackHeld = false
		elseif input.KeyCode == Enum.KeyCode.F or input.KeyCode == Enum.KeyCode.ButtonL2 then
			setBlock(false)
		end
	end)

	-- автоатака при удержании
	RunService.Heartbeat:Connect(function()
		if attackHeld then
			attack()
		end
	end)

	player.CharacterAdded:Connect(function(character)
		blocking = false
		attackHeld = false
		blockButton.BackgroundColor3 = Theme.Panel
		character:WaitForChild("Humanoid", 10)
		loadSwing()
	end)
	if player.Character then
		task.spawn(loadSwing)
	end
end

return CombatController
