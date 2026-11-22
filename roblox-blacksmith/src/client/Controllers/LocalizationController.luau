--!strict
--[[
	Выбор языка: сохранённый в профиле → иначе язык аккаунта Roblox (RU для русскоязычных) → EN.
	Переводит ProximityPrompt по атрибутам ActionKey/ObjectKey, которые ставит сервер.
]]

local Players = game:GetService("Players")
local ProximityPromptService = game:GetService("ProximityPromptService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Localization = require(ReplicatedStorage.Shared.Localization)
local Net = require(ReplicatedStorage.Shared.Net)
local DataController = require(script.Parent.DataController)
local UIController = require(script.Parent.UIController)

local LocalizationController = {}

local function localizePrompt(prompt: ProximityPrompt)
	local action = prompt:GetAttribute("ActionKey")
	local object = prompt:GetAttribute("ObjectKey")
	if type(action) == "string" then
		prompt.ActionText = Localization.Get(action)
	end
	if type(object) == "string" then
		prompt.ObjectText = Localization.Get(object)
	end
end

-- ===== Тексты в мире (имена мобов, доски лидеров) =====
-- Сервер ставит на TextLabel атрибут LocKey, остальные атрибуты — аргументы ({name}, {level}).
local worldLabels: { [TextLabel]: boolean } = setmetatable({}, { __mode = "k" }) :: any

local function renderWorld(label: TextLabel)
	local key = label:GetAttribute("LocKey")
	if type(key) ~= "string" then
		return
	end
	local args = {}
	for name, value in label:GetAttributes() do
		if name ~= "LocKey" then
			args[name] = value
		end
	end
	label.Text = Localization.Get(key, args)
end

local function trackWorld(inst: Instance)
	if not inst:IsA("TextLabel") or worldLabels[inst] then
		return
	end
	worldLabels[inst] = true
	inst.AttributeChanged:Connect(function()
		renderWorld(inst)
	end)
	renderWorld(inst)
end

function LocalizationController:Init()
	-- до загрузки данных — язык аккаунта, чтобы первый экран был понятен
	Localization.SetLanguage(Localization.Detect(Players.LocalPlayer.LocaleId))
end

function LocalizationController:Start()
	ProximityPromptService.PromptShown:Connect(function(prompt)
		localizePrompt(prompt)
	end)

	workspace.DescendantAdded:Connect(trackWorld)
	for _, inst in workspace:GetDescendants() do
		trackWorld(inst)
	end
	Localization.Changed:Connect(function()
		for label in worldLabels do
			if label.Parent then
				renderWorld(label)
			end
		end
		-- подсказки «Добыть», «Ковать»… переводятся сразу, а не при следующем показе
		for _, inst in workspace:GetDescendants() do
			if inst:IsA("ProximityPrompt") then
				localizePrompt(inst)
			end
		end
	end)

	task.spawn(function()
		local data = DataController:WaitForData()
		local saved = data.Settings and data.Settings.Language
		if type(saved) == "string" and Localization.IsSupported(saved) then
			Localization.SetLanguage(saved)
		end
	end)
end

-- Язык на сервер уходит через секунду после последнего нажатия: при быстром переключении
-- сервер (лимит 2 запроса подряд) иначе мог сохранить не тот язык.
local toggleToken = 0

function LocalizationController:Toggle()
	local list = Localization.Supported
	local index = table.find(list, Localization.GetLanguage()) or 1
	local nextLang = list[(index % #list) + 1]
	Localization.SetLanguage(nextLang)
	UIController:Toast("TOAST_LANG")
	toggleToken += 1
	local token = toggleToken
	task.delay(1, function()
		if token == toggleToken then
			Net.Event("SetLanguage"):FireServer(Localization.GetLanguage())
		end
	end)
end

return LocalizationController
