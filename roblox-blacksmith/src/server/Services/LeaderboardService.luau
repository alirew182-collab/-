--!strict
--[[
	ГЛОБАЛЬНЫЕ ЛИДЕРБОРДЫ (OrderedDataStore): уровень, сильнейшее оружие, зачищенные Врата.
	Доски — Part с тегом "Leaderboard" и атрибутом Board = "Level" | "Damage" | "Gates".
	Без доступа к API (Studio без «Enable API Services») просто показывает «Ждём данные».
]]

local CollectionService = game:GetService("CollectionService")
local DataStoreService = game:GetService("DataStoreService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.Shared.Config)
local DataService = require(script.Parent.DataService)

local LeaderboardService = {}

type Board = { Id: string, TitleKey: string, Store: OrderedDataStore?, Value: (data: any) -> number, Format: (v: number) -> string }

local BOARDS: { Board } = {
	{
		Id = "Level",
		TitleKey = "LB_LEVEL",
		Store = nil,
		-- уровень * 1e6 + опыт: сортировка и по уровню, и по прогрессу внутри уровня
		Value = function(data)
			return data.Level * 1000000 + math.min(data.Xp, 999999)
		end,
		Format = function(v)
			return "Lv. " .. tostring(v // 1000000)
		end,
	},
	{
		Id = "Damage",
		TitleKey = "LB_DAMAGE",
		Store = nil,
		Value = function(data)
			return data.Stats.BestDamage or 0
		end,
		Format = function(v)
			return tostring(v)
		end,
	},
	{
		Id = "Gates",
		TitleKey = "LB_GATES",
		Store = nil,
		Value = function(data)
			return data.Stats.GatesCleared or 0
		end,
		Format = function(v)
			return tostring(v)
		end,
	},
}

local REFRESH_SECONDS = 120
local SAVE_SECONDS = 300
local nameCache: { [number]: string } = {}
local available = true

local function nameOf(userId: number): string
	if nameCache[userId] then
		return nameCache[userId]
	end
	local ok, name = pcall(function()
		return Players:GetNameFromUserIdAsync(userId)
	end)
	local result = if ok and name then name else "#" .. userId
	nameCache[userId] = result
	return result
end

local function buildSurface(part: BasePart, board: Board): { TextLabel }
	local old = part:FindFirstChild("BoardGui")
	if old then
		old:Destroy()
	end
	local gui = Instance.new("SurfaceGui")
	gui.Name = "BoardGui"
	gui.Face = Enum.NormalId.Front
	gui.SizingMode = Enum.SurfaceGuiSizingMode.PixelsPerStud
	gui.PixelsPerStud = 40
	gui.LightInfluence = 0
	gui.MaxDistance = 120

	local title = Instance.new("TextLabel")
	title.Size = UDim2.fromScale(1, 0.13)
	title.BackgroundColor3 = Color3.fromRGB(30, 80, 140)
	title.Font = Enum.Font.GothamBlack
	title.TextScaled = true
	title.TextColor3 = Color3.new(1, 1, 1)
	title.Text = board.TitleKey
	title:SetAttribute("LocKey", board.TitleKey) -- переводит клиент
	title.Parent = gui

	local rows = {}
	for i = 1, 10 do
		local row = Instance.new("TextLabel")
		row.Size = UDim2.fromScale(0.94, 0.08)
		row.Position = UDim2.fromScale(0.03, 0.15 + (i - 1) * 0.085)
		row.BackgroundTransparency = 1
		row.Font = if i <= 3 then Enum.Font.GothamBold else Enum.Font.GothamMedium
		row.TextScaled = true
		row.TextXAlignment = Enum.TextXAlignment.Left
		row.TextColor3 = if i == 1
			then Color3.fromRGB(255, 215, 80)
			elseif i == 2 then Color3.fromRGB(220, 225, 235)
			elseif i == 3 then Color3.fromRGB(230, 150, 90)
			else Color3.fromRGB(200, 215, 235)
		row.Text = if i == 1 then "LB_EMPTY" else ""
		if i == 1 then
			row:SetAttribute("LocKey", "LB_EMPTY")
		end
		row.Parent = gui
		table.insert(rows, row)
	end
	gui.Parent = part
	return rows
end

local function boardParts(id: string): { BasePart }
	local list = {}
	for _, inst in CollectionService:GetTagged(Config.Tags.Leaderboard) do
		if inst:IsA("BasePart") and inst:GetAttribute("Board") == id then
			table.insert(list, inst)
		end
	end
	return list
end

local function save(player: Player, data: any?)
	if not available then
		return
	end
	data = data or DataService:Get(player)
	if not data then
		return
	end
	for _, board in BOARDS do
		local store = board.Store
		if store then
			local value = math.floor(board.Value(data))
			if value > 0 then
				pcall(function()
					store:SetAsync(tostring(player.UserId), value)
				end)
			end
		end
	end
end

local function refresh()
	if not available then
		return
	end
	for _, board in BOARDS do
		local store = board.Store
		if store then
			local ok, pages = pcall(function()
				return store:GetSortedAsync(false, 10)
			end)
			if ok and pages then
				local entries = pages:GetCurrentPage()
				local lines = {}
				for i, entry in entries do
					local userId = tonumber(entry.key) or 0
					table.insert(lines, `{i}. {nameOf(userId)} — {board.Format(entry.value)}`)
				end
				for _, part in boardParts(board.Id) do
					local gui = part:FindFirstChild("BoardGui")
					if gui then
						local rows = {}
						for _, child in gui:GetChildren() do
							if child:IsA("TextLabel") and child.Position.Y.Scale > 0.14 then
								table.insert(rows, child)
							end
						end
						table.sort(rows, function(a, b)
							return a.Position.Y.Scale < b.Position.Y.Scale
						end)
						for i, row in rows do
							row:SetAttribute("LocKey", nil)
							row.Text = lines[i] or ""
						end
					end
				end
			end
		end
	end
end

function LeaderboardService:Start()
	for _, board in BOARDS do
		local ok, store = pcall(function()
			return DataStoreService:GetOrderedDataStore("LB_" .. board.Id)
		end)
		if ok then
			board.Store = store
		else
			available = false
		end
		for _, part in boardParts(board.Id) do
			buildSurface(part, board)
		end
	end
	CollectionService:GetInstanceAddedSignal(Config.Tags.Leaderboard):Connect(function(inst)
		for _, board in BOARDS do
			if inst:IsA("BasePart") and inst:GetAttribute("Board") == board.Id then
				buildSurface(inst, board)
			end
		end
	end)

	-- Releasing приходит до закрытия профиля, данные ещё доступны
	DataService.Releasing:Connect(save)
	task.spawn(function()
		local lastSave = os.clock()
		while true do
			refresh()
			task.wait(REFRESH_SECONDS)
			if os.clock() - lastSave >= SAVE_SECONDS then
				lastSave = os.clock()
				for _, player in Players:GetPlayers() do
					save(player)
				end
			end
		end
	end)
	game:BindToClose(function()
		for _, player in Players:GetPlayers() do
			save(player)
		end
	end)
end

return LeaderboardService
