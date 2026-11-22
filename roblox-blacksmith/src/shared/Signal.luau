--!strict
-- Минимальный Signal без BindableEvent (быстрее и не копирует таблицы).

export type Connection = {
	Disconnect: (self: Connection) -> (),
}

export type Signal = {
	Connect: (self: Signal, fn: (...any) -> ()) -> Connection,
	Once: (self: Signal, fn: (...any) -> ()) -> Connection,
	Fire: (self: Signal, ...any) -> (),
	Wait: (self: Signal) -> ...any,
}

local Signal = {}

function Signal.new(): Signal
	local handlers: { [{}]: (...any) -> () } = {}
	local self = {}

	function self:Connect(fn: (...any) -> ()): Connection
		local key = {}
		handlers[key] = fn
		local connection = {}
		function connection:Disconnect()
			handlers[key] = nil
		end
		return connection :: any
	end

	function self:Once(fn: (...any) -> ()): Connection
		local connection: Connection
		connection = self:Connect(function(...)
			connection:Disconnect()
			fn(...)
		end)
		return connection
	end

	function self:Fire(...: any)
		-- копия списка: обработчик может (от)подписаться во время вызова
		local list = {}
		for _, fn in handlers do
			table.insert(list, fn)
		end
		for _, fn in list do
			task.spawn(fn, ...)
		end
	end

	function self:Wait(): ...any
		local thread = coroutine.running()
		self:Once(function(...)
			task.spawn(thread, ...)
		end)
		return coroutine.yield()
	end

	return self :: any
end

return Signal
