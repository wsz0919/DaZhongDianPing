package com.hmdp.service;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/28 10:26
 * @Company:
 */
public interface ILock {

    boolean tryLock(long timeoutSec);

    void unLock();

}
