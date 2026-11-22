--!strict
--[[
	Стиль «Системы»: тёмно-синие окна, неоновая голубая обводка.
	S(n) — размер в пикселях, масштабированный под экран (телефон ≈ 0.9, ПК до 1.6).
	Интерфейс спроектирован под экран телефона в альбомной ориентации (~640×380 точек).
]]

local Theme = {
	Scale = 1,

	Bg = Color3.fromRGB(6, 12, 28),
	Panel = Color3.fromRGB(10, 22, 46),
	PanelLight = Color3.fromRGB(18, 36, 70),
	Accent = Color3.fromRGB(70, 195, 255),
	AccentDim = Color3.fromRGB(35, 95, 150),
	Text = Color3.fromRGB(228, 242, 255),
	TextDim = Color3.fromRGB(140, 168, 200),
	Gold = Color3.fromRGB(255, 205, 80),
	Danger = Color3.fromRGB(255, 80, 95),
	Success = Color3.fromRGB(90, 230, 140),
	Disabled = Color3.fromRGB(70, 80, 100),

	Font = Enum.Font.GothamMedium,
	FontBold = Enum.Font.GothamBold,
	FontTitle = Enum.Font.GothamBlack,
}

function Theme.UpdateScale()
	local camera = workspace.CurrentCamera
	local size = if camera then camera.ViewportSize else Vector2.new(1280, 720)
	if size.X < 10 or size.Y < 10 then
		size = Vector2.new(1280, 720)
	end
	Theme.Scale = math.clamp(math.min(size.X / 640, size.Y / 380), 0.75, 1.6)
end

function Theme.S(n: number): number
	return math.floor(n * Theme.Scale + 0.5)
end

return Theme
