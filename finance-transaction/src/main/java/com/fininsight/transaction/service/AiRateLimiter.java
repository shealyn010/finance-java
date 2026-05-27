package com.fininsight.transaction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI API 限流 + 熔断器
 * - 令牌桶：最多10个并发调用
 * - 熔断：连续失败5次 → 熔断30秒 → 半开探测 → 恢复或继续熔断
 */
@Slf4j
@Component
public class AiRateLimiter {

    // 信号量限流：最多10个并发
    private final Semaphore semaphore = new Semaphore(10);

    // 熔断状态
    private volatile boolean circuitOpen = false;
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicLong circuitOpenTime = new AtomicLong(0);
    private static final int FAIL_THRESHOLD = 5;
    private static final long COOLDOWN_MS = 30_000; // 30秒冷却

    /** 尝试获取调用许可 */
    public boolean tryAcquire() {
        if (circuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime.get() > COOLDOWN_MS) {
                // 半开探测
                log.info("[熔断器] 进入半开状态，尝试探测...");
                circuitOpen = false;
                failCount.set(0);
            } else {
                return false;
            }
        }
        return semaphore.tryAcquire();
    }

    /** 释放许可 */
    public void release() { semaphore.release(); }

    /** 记录成功 */
    public void onSuccess() { failCount.set(0); }

    /** 记录失败，触发熔断 */
    public void onFailure() {
        int fails = failCount.incrementAndGet();
        if (fails >= FAIL_THRESHOLD) {
            circuitOpen = true;
            circuitOpenTime.set(System.currentTimeMillis());
            log.warn("[熔断器] 连续失败{}次，熔断30秒", fails);
        }
    }

    /** 获取当前状态 */
    public String status() {
        return String.format("并发:%d/10, 熔断:%s, 连续失败:%d",
            semaphore.availablePermits(), circuitOpen ? "开" : "关", failCount.get());
    }
}
