--!strict
-- Звуки интерфейса и эффектов. ID задаются в Shared/Config/Audio (пусто = тишина).

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local SoundService = game:GetService("SoundService")

local Config = require(ReplicatedStorage.Shared.Config)

local Sound = {}

local cache: { [string]: Sound } = {}

function Sound.Play(key: string, volume: number?, pitch: number?)
	local id = Config.Audio[key]
	if not id or id == "" then
		return
	end
	local sound = cache[key]
	if not sound then
		sound = Instance.new("Sound")
		sound.Name = key
		sound.SoundId = id
		sound.Parent = SoundService
		cache[key] = sound
	end
	local s = sound :: Sound
	s.Volume = volume or 0.6
	s.PlaybackSpeed = pitch or 1
	SoundService:PlayLocalSound(s)
end

return Sound
