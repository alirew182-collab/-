--!strict
--[[
	Направление: ночная кузня. Интерфейс — тихий тёплый графит (копоть, металл),
	главное действие — цвет углей. Холодный синий свет есть только у Системы:
	окно Системы и заголовок «[Система]» в трекере задания.
	Без неоновых обводок и градиентов: глубина от ступеней поверхности и тонких линий.

	S(n) — размер в пикселях, масштабированный под экран (телефон ≈ 0.9, ПК до 1.6).
	Базовые размеры — на сетке 4px. Интерфейс рассчитан на телефон в альбомной ориентации (~640×380 точек).
]]

local Theme = {
	Scale = 1,

	-- тёплая нейтральная шкала (поверхности и текст)
	Bg = Color3.fromRGB(18, 17, 15), -- самая глубокая поверхность
	Panel = Color3.fromRGB(28, 26, 24), -- окна и блоки
	PanelLight = Color3.fromRGB(38, 36, 33), -- поднятая: кнопки, строки, поля
	Text = Color3.fromRGB(239, 233, 222), -- основной
	TextDim = Color3.fromRGB(170, 163, 152), -- второстепенный
	Muted = Color3.fromRGB(138, 132, 122), -- подписи и мета
	HairlineTransparency = 0.9, -- линии = основной текст на 10%

	-- угли кузни: главное действие, выбор, таймер
	Accent = Color3.fromRGB(238, 137, 64),
	AccentDim = Color3.fromRGB(58, 44, 34), -- выбранная строка: поверхность с оттенком углей

	-- холодный свет Системы (только окно Системы и заголовок «[Система]»)
	System = Color3.fromRGB(88, 190, 255),
	SystemBg = Color3.fromRGB(10, 22, 40),

	-- смысловые цвета, приглушённые под шкалу
	Gold = Color3.fromRGB(232, 190, 90), -- золото как валюта
	Danger = Color3.fromRGB(229, 72, 77),
	Success = Color3.fromRGB(76, 195, 138),
	Disabled = Color3.fromRGB(34, 32, 30),

	-- веса 400 и 500; иерархия — размером и цветом, а не жирностью
	Font = Enum.Font.Gotham,
	FontBold = Enum.Font.GothamMedium,
	FontTitle = Enum.Font.GothamMedium,

	-- одна кривая для всей анимации (ease-out), без пружин
	Ease = Enum.EasingStyle.Quint,
}

-- Масштаб для текущего экрана (не меняет Theme.Scale)
function Theme.ComputeScale(): number
	local camera = workspace.CurrentCamera
	local size = if camera then camera.ViewportSize else Vector2.new(1280, 720)
	if size.X < 10 or size.Y < 10 then
		size = Vector2.new(1280, 720)
	end
	-- телефон в портретной ориентации: ориентируемся на ширину
	if size.Y > size.X then
		return math.clamp(size.X / 600, 0.6, 1.3) -- окна 540 точек должны влезть по ширине
	end
	return math.clamp(math.min(size.X / 640, size.Y / 380), 0.75, 1.6)
end

-- Масштаб, с которым строится интерфейс (вызывается один раз при запуске)
function Theme.UpdateScale()
	Theme.Scale = Theme.ComputeScale()
end

function Theme.S(n: number): number
	return math.floor(n * Theme.Scale + 0.5)
end

return Theme
