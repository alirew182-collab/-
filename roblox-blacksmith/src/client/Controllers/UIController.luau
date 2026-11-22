--!strict
--[[
	Корневой ScreenGui, слои, окна (одно открытое за раз), тосты, обработка ответов сервера.
	Окно регистрируется так:
		UIController:RegisterPanel("Forge", { Frame = frame, OnOpen = fn(payload), OnClose = fn() })
]]

local ContextActionService = game:GetService("ContextActionService")
local GuiService = game:GetService("GuiService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local Signal = require(ReplicatedStorage.Shared.Signal)
local C = require(script.Parent.Parent.UI.Components)
local Sound = require(script.Parent.Parent.UI.Sound)
local Theme = require(script.Parent.Parent.UI.Theme)
local InputController = require(script.Parent.InputController)

local S = Theme.S

local UIController = {}

UIController.Gui = nil :: any
UIController.Layers = {} :: { [string]: Frame }
UIController.PanelChanged = Signal.new() -- (name or nil)

type Panel = { Frame: GuiObject, OnOpen: ((any) -> ())?, OnClose: (() -> ())? }
local panels: { [string]: Panel } = {}
local openName: string? = nil

local toastContainer: Frame
local hitFlash: Frame
local toastCounter = 0

function UIController:Init()
	-- дождаться реального размера экрана
	local camera = workspace.CurrentCamera
	for _ = 1, 50 do
		if camera and camera.ViewportSize.X > 10 then
			break
		end
		task.wait(0.05)
		camera = workspace.CurrentCamera
	end
	Theme.UpdateScale()

	local playerGui = Players.LocalPlayer:WaitForChild("PlayerGui")
	local gui = Instance.new("ScreenGui")
	gui.Name = "SystemBlacksmith"
	gui.ResetOnSpawn = false
	gui.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
	gui.ScreenInsets = Enum.ScreenInsets.DeviceSafeInsets
	gui.Parent = playerGui
	UIController.Gui = gui

	for i, name in { "Hud", "Panels", "Overlay", "Toasts" } do
		local layer = Instance.new("Frame")
		layer.Name = name
		layer.Size = UDim2.fromScale(1, 1)
		layer.BackgroundTransparency = 1
		layer.ZIndex = i * 10
		layer.Parent = gui
		UIController.Layers[name] = layer
	end

	-- тосты внизу по центру
	toastContainer = C.Make("Frame", {
		Name = "ToastList",
		Size = UDim2.new(0, S(360), 0, S(200)),
		Position = UDim2.new(0.5, 0, 1, -S(120)),
		AnchorPoint = Vector2.new(0.5, 1),
		BackgroundTransparency = 1,
		Parent = UIController.Layers.Toasts,
	})
	C.List(toastContainer, Enum.FillDirection.Vertical, 4, Enum.HorizontalAlignment.Center, Enum.VerticalAlignment.Bottom)
	C.Adaptive(toastContainer)

	-- красная вспышка по краям экрана при получении урона
	hitFlash = C.Make("Frame", {
		Name = "HitFlash",
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = Theme.Danger,
		BackgroundTransparency = 1,
		Parent = UIController.Layers.Overlay,
	})
	C.Make("UIGradient", {
		Transparency = NumberSequence.new({
			NumberSequenceKeypoint.new(0, 0.2),
			NumberSequenceKeypoint.new(0.25, 1),
			NumberSequenceKeypoint.new(0.75, 1),
			NumberSequenceKeypoint.new(1, 0.2),
		}),
		Parent = hitFlash,
	})
end

-- Геймпад: выделить первую кнопку открытого окна (кроме крестика)
local function focusPanel(frame: GuiObject)
	if not InputController:IsGamepad() then
		return
	end
	local best: GuiObject? = nil
	for _, d in frame:GetDescendants() do
		if d:IsA("GuiButton") and d.Visible and d.Name ~= "Close" and d.Selectable then
			best = d
			break
		end
	end
	GuiService.SelectedObject = best
end

local function watchViewport()
	local connection: RBXScriptConnection? = nil
	local function hook()
		if connection then
			connection:Disconnect()
		end
		local camera = workspace.CurrentCamera
		if camera then
			connection = camera:GetPropertyChangedSignal("ViewportSize"):Connect(function()
				C.SetAdaptiveFactor(Theme.ComputeScale() / Theme.Scale)
			end)
			C.SetAdaptiveFactor(Theme.ComputeScale() / Theme.Scale)
		end
	end
	workspace:GetPropertyChangedSignal("CurrentCamera"):Connect(hook)
	hook()
end

function UIController:Start()
	watchViewport()
	InputController.Changed:Connect(function(mode: string)
		if openName and mode == "Gamepad" then
			focusPanel(panels[openName].Frame)
		elseif mode ~= "Gamepad" then
			GuiService.SelectedObject = nil
		end
	end)
	Net.Event("Toast").OnClientEvent:Connect(function(payload: { [string]: any })
		UIController:Toast(payload.Key, payload.Args, payload.Color)
	end)
	Net.Event("OpenPanel").OnClientEvent:Connect(function(payload: { [string]: any })
		UIController:OpenPanel(payload.Panel, payload)
	end)
end

-- ===== Окна =====

function UIController:RegisterPanel(name: string, panel: Panel)
	panels[name] = panel
	panel.Frame.Visible = false
	panel.Frame.Parent = UIController.Layers.Panels
	C.Adaptive(panel.Frame)
end

function UIController:OpenPanel(name: string, payload: any?)
	local panel = panels[name]
	if not panel then
		return
	end
	if openName and openName ~= name then
		UIController:ClosePanel()
	end
	openName = name
	panel.Frame.Visible = true
	if panel.OnOpen then
		panel.OnOpen(payload)
	end
	-- геймпад: B закрывает окно, фокус на первую кнопку
	ContextActionService:BindActionAtPriority("UIClosePanel", function(_, state)
		if state == Enum.UserInputState.Begin then
			UIController:ClosePanel()
		end
		return Enum.ContextActionResult.Sink
	end, false, 2500, Enum.KeyCode.ButtonB)
	focusPanel(panel.Frame)
	UIController.PanelChanged:Fire(name)
end

function UIController:ClosePanel()
	local name = openName
	if not name then
		return
	end
	openName = nil
	ContextActionService:UnbindAction("UIClosePanel")
	GuiService.SelectedObject = nil
	local panel = panels[name]
	panel.Frame.Visible = false
	if panel.OnClose then
		panel.OnClose()
	end
	UIController.PanelChanged:Fire(nil)
end

function UIController:TogglePanel(name: string)
	if openName == name then
		UIController:ClosePanel()
	else
		UIController:OpenPanel(name)
	end
end

function UIController:GetOpenPanel(): string?
	return openName
end

-- ===== Тосты =====

local TOAST_COLORS: { [string]: Color3 } = {
	error = Theme.Danger,
	ore = Theme.Gold,
	gold = Theme.Gold,
	success = Theme.Success,
}

function UIController:Toast(key: string, args: { [string]: any }?, color: string?)
	if not toastContainer then
		return
	end
	local text = Localization.Get(key, args)
	toastCounter += 1
	local toast = C.Make("TextLabel", {
		LayoutOrder = toastCounter,
		Size = UDim2.new(0, S(340), 0, S(32)),
		BackgroundColor3 = Theme.Bg,
		BackgroundTransparency = 0.25,
		Font = Theme.FontBold,
		TextSize = S(16),
		TextColor3 = (color and TOAST_COLORS[color]) or Theme.Text,
		TextWrapped = true,
		Text = text,
		Parent = toastContainer,
	})
	C.Corner(toast, 6)
	if color == "error" then
		Sound.Play("SystemWarning", 0.3)
	end
	-- не больше 5 тостов одновременно
	local list = {}
	for _, child in toastContainer:GetChildren() do
		if child:IsA("TextLabel") then
			table.insert(list, child)
		end
	end
	if #list > 5 then
		list[1]:Destroy()
	end
	task.delay(2.2, function()
		if toast.Parent then
			local tween = TweenService:Create(toast, TweenInfo.new(0.35), { TextTransparency = 1, BackgroundTransparency = 1 })
			tween:Play()
			tween.Completed:Wait()
			toast:Destroy()
		end
	end)
end

-- Ответ RemoteFunction: показать ошибку тостом. Возвращает ok.
function UIController:Handle(result: any): boolean
	if type(result) ~= "table" then
		UIController:Toast("ERR_SERVER", nil, "error")
		return false
	end
	if not result.ok then
		UIController:Toast(result.err or "ERR_SERVER", result.args, "error")
		return false
	end
	return true
end

-- Безопасный вызов RemoteFunction
function UIController:Invoke(name: string, ...: any): any
	local remote = Net.Function(name)
	local args = table.pack(...)
	local ok, result = pcall(function()
		return remote:InvokeServer(table.unpack(args, 1, args.n))
	end)
	if not ok then
		return { ok = false, err = "ERR_SERVER" }
	end
	return result
end

function UIController:FlashHit(strength: number)
	hitFlash.BackgroundTransparency = math.clamp(1 - strength, 0.35, 1)
	TweenService:Create(hitFlash, TweenInfo.new(0.4), { BackgroundTransparency = 1 }):Play()
end

return UIController
