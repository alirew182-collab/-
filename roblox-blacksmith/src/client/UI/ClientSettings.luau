--!strict
-- Текущие настройки игрока на клиенте (звук, музыка, цифры урона, слабая графика).
-- Берутся из профиля, меняются в Меню → Настройки.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Signal = require(ReplicatedStorage.Shared.Signal)

local ClientSettings = {
	Sfx = true,
	Music = true,
	DamageNumbers = true,
	LowGraphics = false,
	Changed = Signal.new(), -- (key, value)
}

function ClientSettings.Set(key: string, value: boolean)
	if (ClientSettings :: any)[key] == value then
		return
	end;
	(ClientSettings :: any)[key] = value
	ClientSettings.Changed:Fire(key, value)
end

return ClientSettings
