package com.fininsight.transaction.mq;

import com.fininsight.transaction.config.RabbitConfig;
import com.fininsight.transaction.service.AiRateLimiter;
import com.fininsight.transaction.service.WorkOrderAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI分析MQ消费者
 * 从队列消费工单ID，用限流器控制并发调用DeepSeek API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalyzeConsumer {
    private final WorkOrderAiService aiService;
    private final AiRateLimiter rateLimiter;

    @RabbitListener(queues = RabbitConfig.AI_ANALYZE_QUEUE, concurrency = "3-5")
    public void onMessage(String orderId) {
        // 等待限流许可
        while (!rateLimiter.tryAcquire()) {
            try {
                log.info("[MQ] 限流等待... orderId={}", orderId);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try {
            aiService.analyzeOrder(orderId);
            rateLimiter.onSuccess();
        } catch (Exception e) {
            rateLimiter.onFailure();
            log.error("[MQ] AI分析失败 orderId={}: {}", orderId, e.getMessage());
        } finally {
            rateLimiter.release();
        }
    }
}
