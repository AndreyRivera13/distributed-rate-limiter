--[[
  Sliding Window Counter — atomic Redis Lua script.

  KEYS[1]  current window key   e.g. "sw:ip:1.2.3.4:1716300060"
  KEYS[2]  previous window key  e.g. "sw:ip:1.2.3.4:1716300000"

  ARGV[1]  now_ms     current timestamp in milliseconds
  ARGV[2]  window_ms  window size in milliseconds
  ARGV[3]  limit      max requests allowed per window

  Returns {allowed, estimated_count, limit, reset_epoch_ms}

  Formula:
    elapsed   = now_ms % window_ms
    weight    = 1 - (elapsed / window_ms)
    estimated = floor(prev * weight) + curr
--]]

local now       = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local limit     = tonumber(ARGV[3])

local curr = tonumber(redis.call('GET', KEYS[1])) or 0
local prev = tonumber(redis.call('GET', KEYS[2])) or 0

local elapsed   = now % window_ms
local weight    = 1 - (elapsed / window_ms)
local estimated = math.floor(prev * weight) + curr
local reset_ms  = now - elapsed + window_ms

if estimated >= limit then
    return {0, estimated, limit, reset_ms}
end

redis.call('INCR', KEYS[1])
redis.call('PEXPIRE', KEYS[1], window_ms * 2)
return {1, estimated + 1, limit, reset_ms}
