--!strict
--[[
	Создание ProximityPrompt. Текст ставится по-английски, а ключи локализации
	кладутся в атрибуты ActionKey/ObjectKey — клиент сам переводит подсказку на свой язык.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Localization = require(ReplicatedStorage.Shared.Localization)

local Prompts = {}

export type PromptOptions = {
	ObjectKey: string?,
	Distance: number?,
	HoldDuration: number?,
	KeyCode: Enum.KeyCode?,
}

function Prompts.Create(parent: Instance, actionKey: string, options: PromptOptions?): ProximityPrompt
	local opts: PromptOptions = options or {}
	local prompt = Instance.new("ProximityPrompt")
	prompt.Name = "GamePrompt"
	prompt.ActionText = Localization.Get(actionKey)
	prompt:SetAttribute("ActionKey", actionKey)
	if opts.ObjectKey then
		prompt.ObjectText = Localization.Get(opts.ObjectKey)
		prompt:SetAttribute("ObjectKey", opts.ObjectKey)
	end
	prompt.HoldDuration = opts.HoldDuration or 0
	prompt.MaxActivationDistance = opts.Distance or 10
	prompt.RequiresLineOfSight = false
	prompt.KeyboardKeyCode = opts.KeyCode or Enum.KeyCode.E
	prompt.Parent = parent
	return prompt
end

return Prompts
