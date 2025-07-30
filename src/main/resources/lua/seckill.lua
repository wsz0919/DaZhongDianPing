-- 优惠券id
local voucherId = ARGV[1];
-- 用户id
local userId = ARGV[2];

local beginTime = tonumber(ARGV[3]);

local endTime = tonumber(ARGV[4]);

-- 库存的key
local stockKey = 'seckill:stock:' .. voucherId;
-- 订单key
local orderKey = 'seckill:order:' .. voucherId;

local currentTime = tonumber(redis.call('TIME')[1]);

-- 判断秒杀时间是否有效
if currentTime < beginTime then
    -- 秒杀未开始
    return 3;
end
if currentTime > endTime then
    -- 秒杀已结束
    return 4;
end

-- 判断库存是否充足 get stockKey > 0 ?
local stock = redis.call('GET', stockKey);
if (tonumber(stock) <= 0) then
    -- 库存不足，返回1
    return 1;
end

-- 库存充足，判断用户是否已经下过单 SISMEMBER orderKey userId
if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    -- 用户已下单，返回2
    return 2;
end

-- 库存充足，没有下过单，扣库存、下单
redis.call('INCRBY', stockKey, -1);
redis.call('SADD', orderKey, userId);
-- 返回0，标识下单成功
return 0;
