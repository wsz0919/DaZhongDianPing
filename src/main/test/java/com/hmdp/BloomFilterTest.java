package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

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

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        //CountDownLatch 相当于 一个线程计数器，没执行完一个线程就调用countDown（）方法 -1 ，直到减为0，之后还会变成初始值。
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //  等待所有任务执行完毕
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }
}
