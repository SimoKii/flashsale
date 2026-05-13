-- KEYS[1] = stock:product:{id}
-- KEYS[2] = holders:product:{id}
-- ARGV[1] = userId

local score = redis.call('ZSCORE', KEYS[2], ARGV[1])
if score == false then
    return 0
end
redis.call('INCRBY', KEYS[1], 1)
redis.call('ZREM', KEYS[2], ARGV[1])
return 1
