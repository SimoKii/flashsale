-- KEYS[1] = stock:product:{id}
-- KEYS[2] = holders:product:{id}
-- KEYS[3] = ticket_processed:product:{id}
-- ARGV[1] = ticketId
-- ARGV[2] = userId
-- ARGV[3] = now (epoch seconds)
-- 반환: 0=이미 처리됨, -1=매진, 양수=남은 재고

local alreadyProcessed = redis.call('SISMEMBER', KEYS[3], ARGV[1])
if alreadyProcessed == 1 then
    return 0
end

local remaining = tonumber(redis.call('GET', KEYS[1]))
if remaining == nil or remaining <= 0 then
    return -1
end

redis.call('DECRBY', KEYS[1], 1)
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
redis.call('SADD', KEYS[3], ARGV[1])
redis.call('EXPIRE', KEYS[3], 3600)

return remaining - 1
