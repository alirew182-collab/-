--!strict
--[[
	ТИП УПРАВЛЕНИЯ: Touch (телефон/планшет) | KeyboardMouse (ПК) | Gamepad (консоль, геймпад на ПК).
	Меняется на лету по последнему вводу: подключил геймпад к ПК — интерфейс сразу перестроится.
	Интерфейс по нему решает: показывать ли большие сенсорные кнопки, какие подсказки клавиш писать,
	включать ли навигацию геймпадом по окнам.
]]

local GuiService = game:GetService("GuiService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local UserInputService = game:GetService("UserInputService")

local Signal = require(ReplicatedStorage.Shared.Signal)
local C = require(script.Parent.Parent.UI.Components)

local InputController = {}

InputController.Mode = "KeyboardMouse"
InputController.Changed = Signal.new() -- (mode)

local function modeFor(t: Enum.UserInputType): string?
	if t == Enum.UserInputType.Touch then
		return "Touch"
	elseif string.sub(t.Name, 1, 7) == "Gamepad" then
		return "Gamepad"
	elseif
		t == Enum.UserInputType.Keyboard
		or t == Enum.UserInputType.MouseButton1
		or t == Enum.UserInputType.MouseButton2
		or t == Enum.UserInputType.MouseMovement
		or t == Enum.UserInputType.MouseWheel
	then
		return "KeyboardMouse"
	end
	return nil
end

local function setMode(mode: string)
	if mode == InputController.Mode then
		return
	end
	InputController.Mode = mode
	C.SetInputMode(mode)
	InputController.Changed:Fire(mode)
end

function InputController:Init()
	local initial = modeFor(UserInputService:GetLastInputType())
	if not initial then
		if GuiService:IsTenFootInterface() then
			initial = "Gamepad" -- консоль / телевизор
		elseif UserInputService.TouchEnabled and not UserInputService.KeyboardEnabled then
			initial = "Touch"
		elseif UserInputService.GamepadEnabled and not UserInputService.KeyboardEnabled then
			initial = "Gamepad"
		else
			initial = "KeyboardMouse"
		end
	end
	InputController.Mode = initial :: string
	C.SetInputMode(InputController.Mode)
end

function InputController:Start()
	UserInputService.LastInputTypeChanged:Connect(function(t)
		local mode = modeFor(t)
		if mode then
			setMode(mode)
		end
	end)
end

function InputController:IsTouch(): boolean
	return InputController.Mode == "Touch"
end

function InputController:IsGamepad(): boolean
	return InputController.Mode == "Gamepad"
end

return InputController
