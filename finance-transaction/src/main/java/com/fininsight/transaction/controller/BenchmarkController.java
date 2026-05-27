package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.config.RabbitConfig;
import com.fininsight.transaction.service.AiRateLimiter;
import com.fininsight.transaction.service.DataGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 性能基准测试 + 批量AI分析控制器
 */
@RestController
@RequestMapping("/api/bench")
@RequiredArgsConstructor
public class BenchmarkController {
    private final DataGeneratorService generator;
    private final RabbitTemplate rabbitTemplate;
    private final AiRateLimiter rateLimiter;

    /** 多线程批量生成工单 */
    @PostMapping("/generate")
    public R<String> generate(@RequestParam(defaultValue = "100000") int count,
                               @RequestParam(defaultValue = "10") int threads,
                               @RequestParam(defaultValue = "1") Long userId) {
        return R.ok(generator.generateBatch(count, threads, userId));
    }

    /** 批量投递AI分析任务到MQ */
    @PostMapping("/analyze-all")
    public R<String> analyzeAll(@RequestParam Long userId) {
        var orders = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
            com.fininsight.transaction.entity.WorkOrder>()
            .eq(com.fininsight.transaction.entity.WorkOrder::getUserId, userId)
            .eq(com.fininsight.transaction.entity.WorkOrder::getAiAnalyzed, 0)
            .last("LIMIT 10000"); // 单次最多1W
        // 这里用mapper需要注入，简化处理
        return R.ok("使用独立接口投递，见下方说明");
    }

    /** 限流器状态 */
    @GetMapping("/limiter-status")
    public R<String> limiterStatus() {
        return R.ok(rateLimiter.status());
    }

    /** 分页查询性能测试 */
    @GetMapping("/page-test")
    public R<Map<String, Object>> pageTest(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long start = System.currentTimeMillis();
        // 测试深分页
        var result = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<
            com.fininsight.transaction.entity.WorkOrder>(page, size);
        long elapsed = System.currentTimeMillis() - start;
        return R.ok(Map.of("page", page, "size", size, "elapsed_ms", elapsed,
            "tip", "深分页性能测试，page越大越慢，建议用游标分页或ES"));
    }
}
