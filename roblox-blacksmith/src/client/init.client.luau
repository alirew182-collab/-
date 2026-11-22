--!strict
--[[
	ТОЧКА ВХОДА КЛИЕНТА.
	Клиент только рисует и шлёт запросы; вся игровая логика — на сервере.
	Порядок: :Init() у всех → :Start() у всех.
]]

local Controllers = script:WaitForChild("Controllers")

local ORDER = {
	"DataController",
	"UIController",
	"LocalizationController",
	"SystemWindowController",
	"HudController",
	"QuestController",
	"ForgeController",
	"InventoryController",
	"MerchantController",
	"CombatController",
	"GateController",
	"FxController",
}

local controllers: { [string]: any } = {}
for _, name in ORDER do
	controllers[name] = require(Controllers:WaitForChild(name) :: ModuleScript)
end

for _, name in ORDER do
	local controller = controllers[name]
	if type(controller.Init) == "function" then
		controller:Init()
	end
end

for _, name in ORDER do
	local controller = controllers[name]
	if type(controller.Start) == "function" then
		local ok, err = pcall(function()
			controller:Start()
		end)
		if not ok then
			warn(`[Client] {name}:Start() — {err}`)
		end
	end
end
