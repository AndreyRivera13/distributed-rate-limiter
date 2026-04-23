--[[
  Token Bucket — atomic Redis Lua script.

  KEYS[1]  bucket key  e.g. "tb:ip:1.2.3.4"

  ARGV[1]  now_ms        current timestamp in milliseconds
  ARGV[2]  capacity      max tokens (burst size)
  ARGV[3]  rate_per_ms   tokens added per millisecond  (e.g. 0.01 = 10 req/s)
  ARGV[4]  ttl_ms        key TTL in milliseconds

  Returns {allowed, tokens_remaining, capacity, reset_epoch_ms}
--]]

local now      = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local rate     = tonumber(ARGV[3])
local ttl      = tonumber(ARGV[4])

local bucket      = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
local tokens      = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or now

local refilled = math.min(capacity, tokens + (now - last_refill) * rate)
local reset_ms = now + math.ceil((capacity - refilled) / rate)

if refilled < 1 then
    redis.call('HSET', KEYS[1], 'tokens', refilled, 'last_refill', now)
    redis.call('PEXPIRE', KEYS[1], ttl)
    return {0, 0, capacity, reset_ms}
end

local new_tokens = refilled - 1
redis.call('HSET', KEYS[1], 'tokens', new_tokens, 'last_refill', now)
redis.call('PEXPIRE', KEYS[1], ttl)
return {1, math.floor(new_tokens), capacity, reset_ms}
