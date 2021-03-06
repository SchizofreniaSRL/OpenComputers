local deadline = 0
local function checkDeadline()
  if computer.realTime() > deadline then
    debug.sethook(coroutine.running(), checkDeadline, "", 1)
    error("too long without yielding", 0)
  end
end

-------------------------------------------------------------------------------

local function invoke(direct, ...)
  local result
  if direct then
    result = table.pack(component.invoke(...))
    if result.n == 0 then -- limit for direct calls reached
      result = nil
    end
  end
  if not result then
    local args = table.pack(...) -- for access in closure
    result = select(1, coroutine.yield(function()
      return table.pack(component.invoke(table.unpack(args, 1, args.n)))
    end))
  end
  if not result[1] then -- error that should be re-thrown.
    error(result[2], 0)
  else -- success or already processed error.
    return table.unpack(result, 2, result.n)
  end
end

-------------------------------------------------------------------------------

local function checkArg(n, have, ...)
  have = type(have)
  local function check(want, ...)
    if not want then
      return false
    else
      return have == want or check(...)
    end
  end
  if not check(...) then
    local msg = string.format("bad argument #%d (%s expected, got %s)",
                              n, table.concat({...}, " or "), have)
    error(msg, 3)
  end
end

-------------------------------------------------------------------------------

--[[ This is the global environment we make available to userland programs. ]]
-- You'll notice that we do a lot of wrapping of native functions and adding
-- parameter checks in those wrappers. This is to avoid errors from the host
-- side that would push error objects - which are userdata and cannot be
-- persisted.
local sandbox
sandbox = {
  assert = assert,
  dofile = nil, -- in lib/base.lua
  error = error,
  _G = nil, -- see below
  getmetatable = function(t)
    if type(t) == "string" then return nil end
    return getmetatable(t)
  end,
  ipairs = ipairs,
  load = function(ld, source, mode, env)
    assert((mode or "t") == "t", "unsupported mode")
    return load(ld, source, "t", env or sandbox)
  end,
  loadfile = nil, -- in lib/base.lua
  next = next,
  pairs = pairs,
  pcall = pcall,
  print = nil, -- in lib/base.lua
  rawequal = rawequal,
  rawget = rawget,
  rawlen = rawlen,
  rawset = rawset,
  select = select,
  setmetatable = setmetatable,
  tonumber = tonumber,
  tostring = tostring,
  type = type,
  _VERSION = "Lua 5.2",
  xpcall = xpcall,

  coroutine = {
    create = coroutine.create,
    resume = function(co, ...) -- custom resume part for bubbling sysyields
      checkArg(1, co, "thread")
      local args = table.pack(...)
      while true do -- for consecutive sysyields
        debug.sethook(co, checkDeadline, "", 10000)
        local result = table.pack(
          coroutine.resume(co, table.unpack(args, 1, args.n)))
        debug.sethook(co) -- avoid gc issues
        checkDeadline()
        if result[1] then -- success: (true, sysval?, ...?)
          if coroutine.status(co) == "dead" then -- return: (true, ...)
            return true, table.unpack(result, 2, result.n)
          elseif result[2] ~= nil then -- yield: (true, sysval)
            args = table.pack(coroutine.yield(result[2]))
          else -- yield: (true, nil, ...)
            return true, table.unpack(result, 3, result.n)
          end
        else -- error: result = (false, string)
          return false, result[2]
        end
      end
    end,
    running = coroutine.running,
    status = coroutine.status,
    wrap = function(f) -- for bubbling coroutine.resume
      local co = coroutine.create(f)
      return function(...)
        local result = table.pack(sandbox.coroutine.resume(co, ...))
        if result[1] then
          return table.unpack(result, 2, result.n)
        else
          error(result[2], 0)
        end
      end
    end,
    yield = function(...) -- custom yield part for bubbling sysyields
      return coroutine.yield(nil, ...)
    end
  },

  string = {
    byte = string.byte,
    char = string.char,
    dump = string.dump,
    find = string.find,
    format = string.format,
    gmatch = string.gmatch,
    gsub = string.gsub,
    len = string.len,
    lower = string.lower,
    match = string.match,
    rep = string.rep,
    reverse = string.reverse,
    sub = string.sub,
    upper = string.upper
  },

  table = {
    concat = table.concat,
    insert = table.insert,
    pack = table.pack,
    remove = table.remove,
    sort = table.sort,
    unpack = table.unpack
  },

  math = {
    abs = math.abs,
    acos = math.acos,
    asin = math.asin,
    atan = math.atan,
    atan2 = math.atan2,
    ceil = math.ceil,
    cos = math.cos,
    cosh = math.cosh,
    deg = math.deg,
    exp = math.exp,
    floor = math.floor,
    fmod = math.fmod,
    frexp = math.frexp,
    huge = math.huge,
    ldexp = math.ldexp,
    log = math.log,
    max = math.max,
    min = math.min,
    modf = math.modf,
    pi = math.pi,
    pow = math.pow,
    rad = math.rad,
    random = math.random,
    randomseed = function(seed)
      checkArg(1, seed, "number")
      math.randomseed(seed)
    end,
    sin = math.sin,
    sinh = math.sinh,
    sqrt = math.sqrt,
    tan = math.tan,
    tanh = math.tanh
  },

  bit32 = {
    arshift = bit32.arshift,
    band = bit32.band,
    bnot = bit32.bnot,
    bor = bit32.bor,
    btest = bit32.btest,
    bxor = bit32.bxor,
    extract = bit32.extract,
    replace = bit32.replace,
    lrotate = bit32.lrotate,
    lshift = bit32.lshift,
    rrotate = bit32.rrotate,
    rshift = bit32.rshift
  },

  io = nil, -- in lib/io.lua

  os = {
    clock = os.clock,
    date = function(format, time)
      checkArg(1, format, "string", "nil")
      checkArg(2, time, "number", "nil")
      return os.date(format, time)
    end,
    difftime = function(t2, t1)
      return t2 - t1
    end,
    execute = nil, -- in lib/os.lua
    exit = nil, -- in lib/os.lua
    remove = nil, -- in lib/os.lua
    rename = nil, -- in lib/os.lua
    time = os.time,
    tmpname = nil, -- in lib/os.lua
  },

-------------------------------------------------------------------------------
-- Start of non-standard stuff.

  computer = {
    isRobot = computer.isRobot,
    address = computer.address,
    romAddress = computer.romAddress,
    tmpAddress = computer.tmpAddress,
    freeMemory = computer.freeMemory,
    totalMemory = computer.totalMemory,
    uptime = computer.uptime,
    energy = computer.energy,
    maxEnergy = computer.maxEnergy,

    users = computer.users,
    addUser = function(name)
      checkArg(1, name, "string")
      return computer.addUser(name)
    end,
    removeUser = function(name)
      checkArg(1, name, "string")
      return computer.removeUser(name)
    end,

    shutdown = function(reboot)
      coroutine.yield(reboot ~= nil and reboot ~= false)
    end,
    pushSignal = function(name, ...)
      checkArg(1, name, "string")
      local args = table.pack(...)
      for i = 1, args.n do
        checkArg(i + 1, args[i], "nil", "boolean", "string", "number")
      end
      return computer.pushSignal(name, ...)
    end,
    pullSignal = function(timeout)
      local deadline = computer.uptime() +
        (type(timeout) == "number" and timeout or math.huge)
      repeat
        local signal = table.pack(coroutine.yield(deadline - computer.uptime()))
        if signal.n > 0 then
          return table.unpack(signal, 1, signal.n)
        end
      until computer.uptime() >= deadline
    end
  },

  component = {
    invoke = function(address, method, ...)
      checkArg(1, address, "string")
      checkArg(2, method, "string")
      return invoke(false, address, method, ...)
    end,
    list = function(filter)
      checkArg(1, filter, "string", "nil")
      local list = component.list(filter)
      local key = nil
      return function()
        key = next(list, key)
        if key then
          return key, list[key]
        end
      end
    end,
    proxy = function(address)
      checkArg(1, address, "string")
      local type, reason = component.type(address)
      if not type then
        return nil, reason
      end
      local proxy = {address = address, type = type}
      local methods, reason = component.methods(address)
      if not methods then
        return nil, reason
      end
      for method, direct in pairs(methods) do
        proxy[method] = function(...)
          return invoke(direct, address, method, ...)
        end
      end
      return proxy
    end,
    type = function(address)
      checkArg(1, address, "string")
      return component.type(address)
    end
  },

  unicode = {
    char = unicode.char,
    len = unicode.len,
    lower = unicode.lower,
    reverse = unicode.reverse,
    sub = unicode.sub,
    upper = unicode.upper
  },

  checkArg = checkArg
}
sandbox._G = sandbox

-------------------------------------------------------------------------------

local function main()
  local args
  local function bootstrap()
    -- Minimalistic hard-coded pure async proxy for our ROM.
    local rom = {}
    function rom.invoke(method, ...)
      return invoke(true, computer.romAddress(), method, ...)
    end
    function rom.open(file) return rom.invoke("open", file) end
    function rom.read(handle) return rom.invoke("read", handle, math.huge) end
    function rom.close(handle) return rom.invoke("close", handle) end
    function rom.libs(file) return ipairs(rom.invoke("list", "lib")) end
    function rom.isDirectory(path) return rom.invoke("isDirectory", path) end

    -- Custom low-level dofile implementation reading from our ROM.
    local function dofile(file)
      local handle, reason = rom.open(file)
      if not handle then
        error(reason)
      end
      if handle then
        local buffer = ""
        repeat
          local data, reason = rom.read(handle)
          if not data and reason then
            error(reason)
          end
          buffer = buffer .. (data or "")
        until not data
        rom.close(handle)
        local program, reason = load(buffer, "=" .. file, "t", sandbox)
        if program then
          local result = table.pack(pcall(program))
          if result[1] then
            return table.unpack(result, 2, result.n)
          else
            error(result[2])
          end
        else
          error(reason)
        end
      end
    end

    local init = {}
    for _, lib in rom.libs() do
      local path = "lib/" .. lib
      if not rom.isDirectory(path) then
        local install = dofile(path)
        if type(install) == "function" then
          table.insert(init, install)
        end
      end
    end

    for _, install in ipairs(init) do
      install()
    end

    -- Yield once to get a memory baseline.
    coroutine.yield()

    return coroutine.create(function() dofile("/init.lua") end)
  end
  local co, args = bootstrap(), {n=0}
  while true do
    deadline = computer.realTime() + timeout -- timeout global is set by host
    debug.sethook(co, checkDeadline, "", 10000)
    local result = table.pack(coroutine.resume(co, table.unpack(args, 1, args.n)))
    if not result[1] then
      error(tostring(result[2]), 0)
    elseif coroutine.status(co) == "dead" then
      error("computer stopped unexpectedly", 0)
    else
      args = table.pack(coroutine.yield(result[2])) -- system yielded value
    end
  end
end

-- JNLua converts the coroutine to a string immediately, so we can't get the
-- traceback later. Because of that we have to do the error handling here.
return pcall(main)