--!strict
--[[
	ТОЧКА ВХОДА СЕРВЕРА.
	Порядок: все сервисы → :Init() (создают свои папки/Remote) → :Start() (подписки, циклы).
	Сервисы зависят друг от друга через require без циклов.
]]

local DevMap = require(script.Util.DevMap)
local WorldBuilder = require(script.World.WorldBuilder)

local ORDER = {
	"NetService",
	"DataService",
	"SystemService",
	"EconomyService",
	"LevelService",
	"SocialService",
	"TutorialService",
	"DailyService",
	"ProgressService",
	"InventoryService",
	"MiningService",
	"ForgeService",
	"MerchantService",
	"CombatState",
	"MobService",
	"CombatService",
	"GateService",
	"WorldMobService",
	"CodesService",
	"LeaderboardService",
}

-- карта: полный мир кодом (если нет своих объектов с тегами) или минимальные заглушки
if WorldBuilder.ShouldBuild() then
	WorldBuilder.Build()
else
	DevMap.BuildIfEmpty()
end

local services: { [string]: any } = {}
for _, name in ORDER do
	services[name] = require(script.Services:FindFirstChild(name) :: ModuleScript)
end

for _, name in ORDER do
	local service = services[name]
	if type(service.Init) == "function" then
		service:Init()
	end
end

for _, name in ORDER do
	local service = services[name]
	if type(service.Start) == "function" then
		local ok, err = pcall(function()
			service:Start()
		end)
		if not ok then
			warn(`[Server] {name}:Start() — {err}`)
		end
	end
end

-- игроков начинаем загружать только когда все сервисы подписались на события
services.DataService:BeginLoading()

print("[SystemBlacksmith] сервер запущен")
