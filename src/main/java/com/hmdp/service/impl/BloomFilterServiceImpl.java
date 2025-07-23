package com.hmdp.service.impl;

import com.hmdp.service.IShopService;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

/**
 * Description
 * 布隆过滤器配置
 *
 * @Author wangshaozhe
 * @Date 2025/7/23 11:25
 * @Company:
 */
@Service
public class BloomFilterServiceImpl {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IShopService service;

    @PostConstruct
    public void initBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("shopFilter");
        bloomFilter.tryInit(1000L, 0.1);
        List<String> ids = service.getAllId();
        ids.forEach(bloomFilter::add);
    }

}
