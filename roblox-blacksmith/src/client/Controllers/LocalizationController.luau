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

function LocalizationController:Init()
	-- до загрузки данных — язык аккаунта, чтобы первый экран был понятен
	Localization.SetLanguage(Localization.Detect(Players.LocalPlayer.LocaleId))
end

function LocalizationController:Start()
	ProximityPromptService.PromptShown:Connect(function(prompt)
		localizePrompt(prompt)
	end)

	task.spawn(function()
		local data = DataController:WaitForData()
		local saved = data.Settings and data.Settings.Language
		if type(saved) == "string" and Localization.IsSupported(saved) then
			Localization.SetLanguage(saved)
		end
	end)
end

function LocalizationController:Toggle()
	local list = Localization.Supported
	local index = table.find(list, Localization.GetLanguage()) or 1
	local nextLang = list[(index % #list) + 1]
	Localization.SetLanguage(nextLang)
	Net.Event("SetLanguage"):FireServer(nextLang)
	UIController:Toast("TOAST_LANG")
end

return LocalizationController
