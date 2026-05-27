package com.fininsight.transaction.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 分布式锁
 * 场景:
 * 1. 缓存击穿保护: 热点key过期时只让一个线程重建缓存
 * 2. 重复提交防护: 同一工单5秒内不允许重复创建
 * 3. 定时任务互斥: 多个实例同时跑定时任务时只执行一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {
    private final RedissonClient redisson;

    /** 带锁执行（获取锁失败直接返回） */
    public <T> T tryLock(String key, long waitSec, long holdSec, Supplier<T> task) {
        RLock lock = redisson.getLock("lock:" + key);
        try {
            if (lock.tryLock(waitSec, holdSec, TimeUnit.SECONDS)) {
                try {
                    return task.get();
                } finally {
                    lock.unlock();
                }
            }
            log.warn("[分布式锁] 获取锁失败: {}", key);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** 缓存击穿保护：只有拿到锁的线程去查DB重建缓存 */
    public <T> T cacheRebuild(String key, long holdSec, Supplier<T> dbQuery) {
        return tryLock("cache:" + key, 0, holdSec, dbQuery);
    }

    /** 防重复提交 */
    public boolean isDuplicate(String key, long windowSec) {
        RLock lock = redisson.getLock("dedup:" + key);
        try {
            return !lock.tryLock(0, windowSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true; // 中断当重复处理
        }
    }
}
