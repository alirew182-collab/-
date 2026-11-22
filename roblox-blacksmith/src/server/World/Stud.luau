--!strict
--[[
	СТАД-СТИЛЬ: одна функция brick() для всех деталей мира (правила скилла Studify, RoClicks).

	• Материал всегда Plastic. Узор шипов на всех гранях задаётся по РОЛИ детали:
	    Universal   — большие заливки: земля, плиты, массивы холмов
	    StudsNoGrid — крыши, полы, шапки травы (в фолбэке без вариантов → Studs)
	    Weld        — конструкции: столбы, перила, рамы, ящики
	    Studs       — стены и акценты
	  Если в MaterialService есть варианты с этими именами — используются они (рендер сильнее),
	  иначе узор ставится через SurfaceType на каждую грань.
	• Neon — только акцент (свечение руды, горна, порталов, кристалла Системы), без узора.
	• Камни: узор Inlet только сверху, бока гладкие.
	• Детали меньше 0.5 во всех измерениях — чистый пластик без узора.
	• Защита от мерцания совпадающих граней: каждая деталь чуть больше заданного размера
	  (от 0.0002 до 0.02, по счётчику), поэтому у соседей грани никогда не совпадают в одной
	  плоскости, а стыки всегда «утоплены» друг в друга, а не разъезжаются щелью.
]]

local MaterialService = game:GetService("MaterialService")

local Stud = {}

local VARIANTS = { "Universal", "StudsNoGrid", "Weld", "Studs" }
local hasVariant: { [string]: boolean } = {}
for _, name in VARIANTS do
	hasVariant[name] = MaterialService:FindFirstChild(name, true) ~= nil
end
Stud.UsingVariants = hasVariant.Universal == true

local counter = 0

export type Options = {
	Pattern: string?, -- Universal | StudsNoGrid | Weld | Studs
	Class: string?, -- Part | WedgePart | CornerWedgePart
	Name: string?,
	Neon: boolean?,
	Rock: boolean?,
	Collide: boolean?,
	Transparency: number?,
	Shadow: boolean?,
	Shape: Enum.PartType?,
}

local function surfaceFor(pattern: string): Enum.SurfaceType
	if pattern == "Weld" then
		return Enum.SurfaceType.Weld
	elseif pattern == "Universal" then
		return Enum.SurfaceType.Universal
	end
	return Enum.SurfaceType.Studs -- Studs и StudsNoGrid
end

local function setAllSurfaces(p: BasePart, surface: Enum.SurfaceType)
	p.TopSurface = surface
	p.BottomSurface = surface
	p.FrontSurface = surface
	p.BackSurface = surface
	p.LeftSurface = surface
	p.RightSurface = surface
end

function Stud.brick(parent: Instance, size: Vector3, cframe: CFrame, color: Color3, options: Options?): BasePart
	local o: Options = options or {}
	local p = Instance.new(o.Class or "Part") :: BasePart
	if o.Shape and p:IsA("Part") then
		(p :: Part).Shape = o.Shape
	end
	counter += 1
	local eps = ((counter % 97) + 1) * 0.0002
	p.Size = size + Vector3.new(eps, eps, eps)
	p.CFrame = cframe
	p.Color = color
	p.Anchored = true
	p.CanCollide = if o.Collide == nil then true else o.Collide
	if o.Collide == false then
		p.CanQuery = false
		p.CanTouch = false
	end
	if o.Transparency then
		p.Transparency = o.Transparency
	end
	if o.Shadow == false then
		p.CastShadow = false
	end
	if o.Name then
		p.Name = o.Name
	end

	local tiny = size.X < 0.5 and size.Y < 0.5 and size.Z < 0.5
	if o.Neon then
		p.Material = Enum.Material.Neon
		setAllSurfaces(p, Enum.SurfaceType.Smooth)
	elseif tiny then
		p.Material = Enum.Material.Plastic
		setAllSurfaces(p, Enum.SurfaceType.Smooth)
	elseif o.Rock then
		p.Material = Enum.Material.Plastic
		setAllSurfaces(p, Enum.SurfaceType.Smooth)
		p.TopSurface = Enum.SurfaceType.Inlet
	else
		p.Material = Enum.Material.Plastic
		local pattern = o.Pattern or "Universal"
		if hasVariant[pattern] then
			p.MaterialVariant = pattern
			setAllSurfaces(p, Enum.SurfaceType.Smooth)
		else
			setAllSurfaces(p, surfaceFor(pattern))
			p.BottomSurface = Enum.SurfaceType.Inlet
		end
	end
	p.Parent = parent
	return p
end

function Stud.model(name: string, parent: Instance): Model
	local m = Instance.new("Model")
	m.Name = name
	m.Parent = parent
	return m
end

-- Палитра стад-стиля (пастель, «солнечный» свет). Земля — лососевая, камень — лавандовый.
Stud.Color = {
	Grass = Color3.fromRGB(126, 179, 60),
	GrassDark = Color3.fromRGB(84, 165, 59),
	GrassBright = Color3.fromRGB(113, 206, 42),
	Tuft = Color3.fromRGB(77, 150, 54),
	Dirt = Color3.fromRGB(207, 109, 76),
	DirtRose = Color3.fromRGB(218, 120, 110),
	DirtDeep = Color3.fromRGB(176, 92, 88),
	Coral = Color3.fromRGB(232, 126, 120),
	CoralDark = Color3.fromRGB(218, 110, 108),
	Sand = Color3.fromRGB(236, 208, 150),
	Peach = Color3.fromRGB(243, 180, 128),
	PeachDark = Color3.fromRGB(230, 160, 110),
	Stone = Color3.fromRGB(150, 158, 218),
	StoneDark = Color3.fromRGB(122, 128, 194),
	StoneLight = Color3.fromRGB(180, 186, 236),
	Wood = Color3.fromRGB(165, 105, 62),
	WoodDark = Color3.fromRGB(136, 83, 52),
	Water = Color3.fromRGB(64, 200, 240),
	Snow = Color3.fromRGB(236, 244, 255),
	Frost = Color3.fromRGB(190, 222, 250),
	Ice = Color3.fromRGB(160, 210, 250),
	Red = Color3.fromRGB(226, 72, 72),
	White = Color3.fromRGB(250, 248, 244),
	Gold = Color3.fromRGB(250, 196, 60),
	Blue = Color3.fromRGB(2, 106, 175),
	Roof = Color3.fromRGB(196, 72, 62),
	RoofDark = Color3.fromRGB(160, 56, 52),
	Iron = Color3.fromRGB(96, 100, 112),
	Ember = Color3.fromRGB(255, 136, 40),
	SystemBlue = Color3.fromRGB(88, 190, 255),
}

return Stud
