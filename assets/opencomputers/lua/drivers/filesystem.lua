local mtab = {children={}}

local function segments(path)
  path = path:gsub("\\", "/")
  repeat local n; path, n = path:gsub("//", "/") until n == 0
  local parts = {}
  for part in path:gmatch("[^/]+") do
    table.insert(parts, part)
  end
  local i = 1
  while i <= #parts do
    if parts[i] == "." then
      table.remove(parts, i)
    elseif parts[i] == ".." then
      table.remove(parts, i)
      i = i - 1
      if i > 0 then
        table.remove(parts, i)
      else
        i = 1
      end
    else
      i = i + 1
    end
  end
  return parts
end

local function findNode(path, create)
  checkArg(1, path, "string")
  local parts = segments(path)
  local node = mtab
  for i = 1, #parts do
    if not node.children[parts[i]] then
      if create then
        node.children[parts[i]] = {children={}, parent=node}
      else
        return node, table.concat(parts, "/", i)
      end
    end
    node = node.children[parts[i]]
  end
  return node
end

local function removeEmptyNodes(node)
  while node and node.parent and not node.fs and not next(node.children) do
    for k, c in pairs(node.parent.children) do
      if c == node then
        node.parent.children[k] = nil
        break
      end
    end
    node = node.parent
  end
end

-------------------------------------------------------------------------------

driver.filesystem = {}

function driver.filesystem.canonical(path)
  return table.concat(segments(path), "/")
end

function driver.filesystem.concat(pathA, pathB)
  return driver.filesystem.canonical(pathA .. "/" .. pathB)
end

function driver.filesystem.name(path)
  local parts = segments(path)
  return parts[#parts]
end

function driver.filesystem.mount(fs, path)
  if fs and path then
    checkArg(1, fs, "string")
    local node = findNode(path, true)
    if node.fs then
      return nil, "another filesystem is already mounted here"
    end
    node.fs = fs
  else
    local function path(node)
      local result = "/"
      while node and node.parent do
        for name, child in pairs(node.parent.children) do
          if child == node then
            result = "/" .. name .. result
            break
          end
        end
        node = node.parent
      end
      return result
    end
    local queue = {mtab}
    return function()
      if #queue == 0 then
        return nil
      else
        while true do
          local node = table.remove(queue)
          for _, child in pairs(node.children) do
            table.insert(queue, child)
          end
          if node.fs then
            return node.fs, path(node)
          end
        end
      end
    end
  end
end

function driver.filesystem.umount(fsOrPath)
  local node, rest = findNode(fsOrPath)
  if not rest and node.fs then
    node.fs = nil
    removeEmptyNodes(node)
    return true
  else
    local queue = {mtab}
    for fs, path in driver.filesystem.mount() do
      if fs == fsOrPath then
        local node = findNode(path)
        node.fs = nil
        removeEmptyNodes(node)
        return true
      end
    end
  end
end

-------------------------------------------------------------------------------

function driver.filesystem.label(fs, label)
  if type(label) == "string" then
    return send(fs, "fs.label=", label)
  end
  return send(fs, "fs.label")
end

-------------------------------------------------------------------------------

function driver.filesystem.spaceTotal(path)
  local node, rest = findNode(path)
  if node.fs then
    return send(node.fs, "fs.spaceTotal")
  else
    return nil, "no such device"
  end
end

function driver.filesystem.spaceUsed(path)
  local node, rest = findNode(path)
  if node.fs then
    return send(node.fs, "fs.spaceUsed")
  else
    return nil, "no such device"
  end
end

-------------------------------------------------------------------------------

function driver.filesystem.exists(path)
  local node, rest = findNode(path)
  if not rest then -- virtual directory
    return true
  end
  if node.fs then
    return send(node.fs, "fs.exists", rest)
  end
end

function driver.filesystem.size(path)
  local node, rest = findNode(path)
  if node.fs and rest then
    return send(node.fs, "fs.size", rest)
  end
  return 0 -- no such file or directory or it's a virtual directory
end

function driver.filesystem.isDirectory(path)
  local node, rest = findNode(path)
  if node.fs and rest then
    return send(node.fs, "fs.isDirectory", rest)
  else
    return not rest or rest:ulen() == 0
  end
end

function driver.filesystem.lastModified(path)
  local node, rest = findNode(path)
  if node.fs and rest then
    return send(node.fs, "fs.lastModified", rest)
  end
  return 0 -- no such file or directory or it's a virtual directory
end

function driver.filesystem.dir(path)
  local node, rest = findNode(path)
  if not node.fs and rest then
    return nil, "no such file or directory"
  end
  local result
  if node.fs then
    result = table.pack(send(node.fs, "fs.dir", rest or ""))
    if not result[1] and result[2] then
      return nil, result[2]
    end
  else
    result = {}
  end
  if not rest then
    for k, _ in pairs(node.children) do
      table.insert(result, k .. "/")
    end
  end
  table.sort(result)
  local i = 0
  return function()
    i = i + 1
    return result[i]
  end
end

-------------------------------------------------------------------------------

function driver.filesystem.makeDirectory(path)
  local node, rest = findNode(path)
  if node.fs and rest then
    return send(node.fs, "fs.makeDirectory", rest)
  end
  return nil, "cannot create a directory in a virtual directory"
end

function driver.filesystem.remove(path)
  local node, rest = findNode(path)
  if node.fs and rest then
    return send(node.fs, "fs.remove", rest)
  end
  return nil, "no such non-virtual directory"
end

function driver.filesystem.rename(oldPath, newPath)
  local oldNode, oldRest = findNode(oldPath)
  local newNode, newRest = findNode(newPath)
  if oldNode.fs and oldRest and newNode.fs and newRest then
    if oldNode.fs == newNode.fs then
      return send(oldNode.fs, "fs.rename", oldRest, newRest)
    else
      local result, reason = driver.filesystem.copy(oldPath, newPath)
      if result then
        return driver.filesystem.remove(oldPath)
      else
        return nil, reason
      end
    end
  end
  return nil, "trying to read from or write to virtual directory"
end

function driver.filesystem.copy(fromPath, toPath)
  local input, reason = io.open(fromPath, "rb")
  if not input then
    error(reason)
  end
  local output, reason = io.open(toPath, "wb")
  if not output then
    input:close()
    error(reason)
  end
  repeat
    local buffer, reason = input:read(1024)
    if not buffer and reason then
      error(reason)
    elseif buffer then
      local result, reason = output:write(buffer)
      if not result then
        input:close()
        output:close()
        error(reason)
      end
    end
  until not buffer
  input:close()
  output:close()
  return true
end

-------------------------------------------------------------------------------

local file = {}

function file:close()
  if self.handle then
    self:flush()
    return self.stream:close()
  end
end

function file:flush()
  if not self.handle then
    return nil, "file is closed"
  end

  local result, reason = self.stream:write(self.buffer)
  if result then
    self.buffer = ""
  else
    if reason then
      return nil, reason
    else
      return nil, "bad file descriptor"
    end
  end

  return self
end

function file:lines(...)
  local args = table.pack(...)
  return function()
    local result = table.pack(self:read(table.unpack(args, 1, args.n)))
    if not result[1] and result[2] then
      error(result[2])
    end
    return table.unpack(result, 1, result.n)
  end
end

function file:read(...)
  if not self.handle then
    return nil, "file is closed"
  end

  local function readChunk()
    local result, reason = self.stream:read(self.bufferSize)
    if result then
      self.buffer = self.buffer .. result
      return self
    else -- error or eof
      return nil, reason
    end
  end

  local function readBytesOrChars(n)
    local len, sub
    if self.mode == "r" then
      len = string.ulen
      sub = string.usub
    else
      assert(self.mode == "rb")
      len = rawlen
      sub = string.sub
    end
    local buffer = ""
    repeat
      if len(self.buffer) == 0 then
        local result, reason = readChunk()
        if not result then
          if reason then
            return nil, reason
          else -- eof
            return #buffer > 0 and buffer or nil
          end
        end
      end
      local left = n - len(buffer)
      buffer = buffer .. sub(self.buffer, 1, left)
      self.buffer = sub(self.buffer, left + 1)
    until len(buffer) == n
    return buffer
  end

  local function readLine(chop)
    local start = 1
    while true do
      local l = self.buffer:find("\n", start, true)
      if l then
        local result = self.buffer:sub(1, l + (chop and -1 or 0))
        self.buffer = self.buffer:sub(l + 1)
        return result
      else
        start = #self.buffer
        local result, reason = readChunk()
        if not result then
          if reason then
            return nil, reason
          else -- eof
            local result = #self.buffer > 0 and self.buffer or nil
            self.buffer = ""
            return result
          end
        end
      end
    end
  end

  local function readAll()
    repeat
      local result, reason = readChunk()
      if not result and reason then
        return nil, reason
      end
    until not result -- eof
    local result = self.buffer
    self.buffer = ""
    return result
  end

  local function read(n, format)
    if type(format) == "number" then
      return readBytesOrChars(format)
    else
      if type(format) ~= "string" or format:usub(1, 1) ~= "*" then
        error("bad argument #" .. n .. " (invalid option)")
      end
      format = format:usub(2, 2)
      if format == "n" then
        --[[ TODO ]]
        error("not implemented")
      elseif format == "l" then
        return readLine(true)
      elseif format == "L" then
        return readLine(false)
      elseif format == "a" then
        return readAll()
      else
        error("bad argument #" .. n .. " (invalid format)")
      end
    end
  end

  local results = {}
  local formats = table.pack(...)
  if formats.n == 0 then
    return readLine(true)
  end
  for i = 1, formats.n do
    local result, reason = read(i, formats[i])
    if result then
      results[i] = result
    elseif reason then
      return nil, reason
    end
  end
  return table.unpack(results, 1, formats.n)
end

function file:seek(whence, offset)
  if not self.handle then
    return nil, "file is closed"
  end

  whence = tostring(whence or "cur")
  assert(whence == "set" or whence == "cur" or whence == "end",
    "bad argument #1 (set, cur or end expected, got " .. whence .. ")")
  offset = offset or 0
  checkArg(2, offset, "number")
  assert(math.floor(offset) == offset, "bad argument #2 (not an integer)")

  if whence == "cur" then
    offset = offset - #self.buffer
  end
  local result, reason = self.stream:seek(whence, offset)
  if result then
    self.buffer = ""
    return result
  else
    return nil, reason
  end
end

function file:setvbuf(mode, size)
  if not self.handle then
    return nil, "file is closed"
  end

  mode = mode or self.bufferMode
  size = size or self.bufferSize

  assert(mode == "no" or mode == "full" or mode == "line",
    "bad argument #1 (no, full or line expected, got " .. tostring(mode) .. ")")
  assert(mode == "no" or type(size) == "number",
    "bad argument #2 (number expected, got " .. type(size) .. ")")

  self.bufferMode = mode
  self.bufferSize = size

  return self.bufferMode, self.bufferSize
end

function file:write(...)
  if not self.handle then
    return nil, "file is closed"
  end

  local args = table.pack(...)
  for i = 1, args.n do
    if type(args[i]) == "number" then
      args[i] = tostring(args[i])
    end
    checkArg(i, args[i], "string")
  end

  for i = 1, args.n do
    local arg = args[i]
    local result, reason

    if self.bufferMode == "full" then
      if self.bufferSize - #self.buffer < #arg then
        result, reason = self:flush()
        if not result then
          return nil, reason
        end
      end
      if #arg > self.bufferSize then
        result, reason = self.stream:write(arg)
      else
        self.buffer = self.buffer .. arg
        result = self
      end

    elseif self.bufferMode == "line" then
      local l
      repeat
        local idx = arg:find("\n", (l or 0) + 1, true)
        if idx then
          l = idx
        end
      until not idx
      if l or #arg > self.bufferSize then
        result, reason = self:flush()
        if not result then
          return nil, reason
        end
      end
      if l then
        result, reason = self.stream:write(arg:sub(1, l))
        if not result then
          return nil, reason
        end
        arg = arg:sub(l + 1)
      end
      if #arg > self.bufferSize then
        result, reason = self.stream:write(arg)
      else
        self.buffer = self.buffer .. arg
        result = self
      end

    else -- no
      result, reason = self.stream:write(arg)
    end

    if not result then
      return nil, reason
    end
  end

  return self
end

-------------------------------------------------------------------------------

function file.new(fs, handle, mode, stream, nogc)
  local result = {
    fs = fs,
    handle = handle,
    mode = mode,
    buffer = "",
    bufferSize = math.min(8 * 1024, os.totalMemory() / 8),
    bufferMode = "full"
  }
  result.stream = setmetatable({file = result}, {__index=stream})

  local metatable = {
    __index = file,
    __metatable = "file"
  }
  if not nogc then
    metatable.__gc = function(self)
      -- file.close does a syscall, which yields, and that's not possible in
      -- the __gc metamethod. So we start a timer to do the yield/cleanup.
      if type(event) == "table" and type(event.timer) == "function" then
        event.timer(0, function()
          self:close()
        end)
      end
    end
  end
  return setmetatable(result, metatable)
end

-------------------------------------------------------------------------------

local fileStream = {}

function fileStream:close()
  send(self.file.fs, "fs.close", self.file.handle)
  self.file.handle = nil
end

function fileStream:read(n)
  return send(self.file.fs, "fs.read", self.file.handle, n)
end

function fileStream:seek(whence, offset)
  return send(self.file.fs, "fs.seek", self.file.handle, whence, offset)
end

function fileStream:write(str)
  return send(self.file.fs, "fs.write", self.file.handle, str)
end

-------------------------------------------------------------------------------

function driver.filesystem.open(path, mode)
  mode = tostring(mode or "r")
  checkArg(2, mode, "string")
  assert(({r=true, rb=true, w=true, wb=true, a=true, ab=true})[mode],
    "bad argument #2 (r[b], w[b] or a[b] expected, got " .. mode .. ")")

  local node, rest = findNode(path)
  if not node.fs or not rest then
    return nil, "file not found"
  end

  local handle, reason = send(node.fs, "fs.open", rest, mode)
  if not handle then
    return nil, reason
  end

  return file.new(node.fs, handle, mode, fileStream)
end

function driver.filesystem.type(object)
  if type(object) == "table" then
    if getmetatable(object) == "file" then
      if object.handle then
        return "file"
      else
        return "closed file"
      end
    end
  end
  return nil
end

-------------------------------------------------------------------------------

io = {}

-------------------------------------------------------------------------------

local stdinStream = {}
local stdinHistory = {}

function stdinStream:close()
  return nil, "cannot close standard file"
end

function stdinStream:read(n)
  local result = term.read(stdinHistory)
  while #stdinHistory > 10 do
    table.remove(stdinHistory, 1)
  end
  return result
end

function stdinStream:seek(whence, offset)
  return nil, "bad file descriptor"
end

function stdinStream:write(str)
  return nil, "bad file descriptor"
end

local stdoutStream = {}

function stdoutStream:close()
  return nil, "cannot close standard file"
end

function stdoutStream:read(n)
  return nil, "bad file descriptor"
end

function stdoutStream:seek(whence, offset)
  return nil, "bad file descriptor"
end

function stdoutStream:write(str)
  term.write(str, true)
  return self
end

io.stdin = file.new(nil, "stdin", "r", stdinStream, true)
io.stdout = file.new(nil, "stdout", "w", stdoutStream, true)
io.stderr = io.stdout

io.stdout:setvbuf("no")

-------------------------------------------------------------------------------

local input, output = io.stdin, io.stdout

-------------------------------------------------------------------------------

function io.close(file)
  return (file or io.output()):close()
end

function io.flush()
  return io.output():flush()
end

function io.input(file)
  if file then
    if type(file) == "string" then
      local result, reason = io.open(file)
      if not result then
        error(reason)
      end
      input = result
    elseif io.type(file) then
      input = file
    else
      error("bad argument #1 (string or file expected, got " .. type(file) .. ")")
    end
  end
  return input
end

function io.lines(filename, ...)
  if filename then
    local result, reason = io.open(filename)
    if not result then
      error(reason)
    end
    local args = table.pack(...)
    return function()
      local result = table.pack(file:read(table.unpack(args, 1, args.n)))
      if not result[1] then
        if result[2] then
          error(result[2])
        else -- eof
          file:close()
          return nil
        end
      end
      return table.unpack(result, 1, result.n)
    end
  else
    return io.input():lines()
  end
end

io.open = driver.filesystem.open

function io.output(file)
  if file then
    if type(file) == "string" then
      local result, reason = io.open(file, "w")
      if not result then
        error(reason)
      end
      output = result
    elseif io.type(file) then
      output = file
    else
      error("bad argument #1 (string or file expected, got " .. type(file) .. ")")
    end
  end
  return output
end

-- TODO io.popen = function(prog, mode) end

function io.read(...)
  return io.input():read(...)
end

function io.tmpfile()
  local name = os.tmpname()
  if name then
    return io.open(name, "a")
  end
end

io.type = driver.filesystem.type

function io.write(...)
  return io.output():write(...)
end

function print(...)
  local args = table.pack(...)
  io.stdout:setvbuf("line")
  for i = 1, args.n do
    local arg = tostring(args[i])
    if i > 1 then
      arg = "\t" .. arg
    end
    io.stdout:write(arg)
  end
  io.stdout:write("\n")
  io.stdout:setvbuf("no")
  io.stdout:flush()
end

-------------------------------------------------------------------------------

os.remove = driver.filesystem.remove
os.rename = driver.filesystem.rename

function os.tmpname()
  if driver.filesystem.exists("tmp") then
    for i = 1, 10 do
      local name = "tmp/" .. math.random(1, 0x7FFFFFFF)
      if not driver.filesystem.exists(name) then
        return name
      end
    end
  end
end

-------------------------------------------------------------------------------

function loadfile(filename, env)
  local file, reason = io.open(filename)
  if not file then
    return nil, reason
  end
  local source, reason = file:read("*a")
  file:close()
  if not source then
    return nil, reason
  end
  return load(source, "=" .. filename, env)
end

function dofile(filename)
  local program, reason = loadfile(filename)
  if not program then
    return error(reason, 0)
  end
  return program()
end