package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/28 10:39
 * @Company:
 */
public class LockServiceImpl implements ILock {


    private String name;

    private StringRedisTemplate stringRedisTemplate;

    /**
     * key前缀
     */
    public static final String KEY_PREFIX = "lock:";
    /**
     * ID前缀
     */
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * 加载Lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public LockServiceImpl(String name, StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String id = ID_PREFIX + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, id, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        // 执行lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
