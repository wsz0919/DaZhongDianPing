package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/23 13:41
 * @Company:
 */
@SpringBootTest
public class BloomFilterTest {

    @Resource
    private RedissonClient redissonClient;

    @Test
    public void testBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("shopFilter");
        // 遍历 15~1000，查看是否有被误判为存在的 id
        for (int i = 14; i <= 1000; i++) {
            String id = String.valueOf(i);
            if (bloomFilter.contains(id)) {
                System.out.println("被误判为存在的 id：" + id);
            }
        }
    }
}
