package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import net.sf.jsqlparser.expression.StringValue;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private ShopMapper mapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 缓存穿透
    @Override
    public Shop getByCacheId(Long id) {
        String key = CACHE_SHOP_KEY + id;

        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("shopFilter");
        // 步骤1：检查布隆过滤器
        if (!bloomFilter.contains(String.valueOf(id))) {
            // 直接拦截不存在的数据
            return null;
        }

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            if ("null".equals(shopJson)) {
                return null;
            }
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        Shop shop = this.getById(id);
        if (shop != null) {
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } else {
            stringRedisTemplate.opsForValue().set(key, "null", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }

        return shop;
    }

    // 缓存击穿（互斥锁）
    @Override
    public Result getBySetNxId(Long id) {
        String key = CACHE_SHOP_KEY + id;
        Boolean flag = stringRedisTemplate.hasKey(key);

        if (flag) {
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(shopJson)) {
                return Result.fail("店铺不存在！");
            }
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }

        try {
            boolean isLock = tryLock(id);
            if (!isLock) {
                Thread.sleep(50);
                return getBySetNxId(id);
            }

            // 双重检测，避免有别的线程已经更新缓存，但此线程不知道缓存已经更新了
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            if (Objects.nonNull(shopJson)) {
                return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
            }

            // 从数据库查询数据，存到缓存中
            Shop shop = this.getById(id);
            if (Objects.nonNull(shop)) {
                String jsonStr = JSONUtil.toJsonStr(shop);
                stringRedisTemplate.opsForValue().set(key, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);

                return Result.ok(shop);
            }

            // 如果不存在，就缓存一个空对象
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(id);
        }

    }

    private Boolean tryLock(Long id) {
        return stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + id, "缓存互斥锁", LOCK_SHOP_TTL, TimeUnit.MINUTES);
    }

    private void releaseLock(Long id) {
        stringRedisTemplate.delete(LOCK_SHOP_KEY + id);
    }

    @Override
    public String updateShop(Shop shop) {
        this.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return "更新成功！";
    }

    @Override
    public List<String> getAllId() {
        return mapper.getAllId();
    }
}
