package com.fininsight.transaction.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高并发数据生成器
 * 多线程批量插入10W条工单，模拟高并发写入场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {
    private final WorkOrderMapper workOrderMapper;

    private static final String[] CUSTOMERS = {
        "古镇灯饰厂","小榄五金店","火炬电子厂","石岐家电维修","南头五金厂",
        "东区科技有限公司","西区电脑城","开发区智能设备","三乡制冷设备","板芙包装材料"
    };
    private static final String[] TYPES = {"安装","维修","巡检","保养","定制"};
    private static final String[] LOCATIONS = {
        "中山市古镇","中山市小榄","中山市火炬开发区","中山市石岐","中山市南头",
        "中山市东区","中山市西区","中山市三乡","中山市板芙","中山市港口"
    };
    private static final String[] TECHNICIANS = {"张工","李工","陈工","王工","刘工","赵工","周工","黄工"};

    /**
     * 多线程批量生成工单
     * @param totalCount 总数（如100000）
     * @param threads 并发线程数（如10）
     */
    public String generateBatch(int totalCount, int threads, Long userId) {
        long start = System.currentTimeMillis();
        AtomicInteger counter = new AtomicInteger(0);
        int perThread = totalCount / threads;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                List<WorkOrder> batch = new ArrayList<>(500);
                Random r = new Random(threadId * 1000);
                for (int i = 0; i < perThread; i++) {
                    batch.add(generateOne(userId, r));
                    if (batch.size() >= 500) {
                        flush(batch, counter);
                        batch.clear();
                    }
                }
                flush(batch, counter);
            }, pool));
        }

        // 等所有线程完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        pool.shutdown();
        try { pool.awaitTermination(10, TimeUnit.SECONDS); } catch (Exception ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        long count = workOrderMapper.selectCount(
            new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getUserId, userId));
        return String.format("生成完成: %d条, 耗时%dms, 写入速度%.0f条/秒",
            count, elapsed, count * 1000.0 / elapsed);
    }

    private WorkOrder generateOne(Long userId, Random r) {
        WorkOrder o = new WorkOrder();
        o.setOrderId(IdUtil.fastSimpleUUID());
        o.setUserId(userId);
        o.setCustomer(CUSTOMERS[r.nextInt(CUSTOMERS.length)]);
        o.setServiceType(TYPES[r.nextInt(TYPES.length)]);

        double laborH = 0.5 + r.nextDouble() * 15; // 0.5-15.5h
        double laborC = laborH * (50 + r.nextDouble() * 150); // 50-200/h
        double materialC = r.nextDouble() * 5000;
        double otherC = r.nextDouble() * 500;
        double revenue = (laborC + materialC + otherC) * (0.8 + r.nextDouble() * 0.6); // 0.8-1.4倍成本

        o.setLaborHours(BigDecimal.valueOf(laborH).setScale(1, RoundingMode.HALF_UP));
        o.setLaborCost(BigDecimal.valueOf(laborC).setScale(2, RoundingMode.HALF_UP));
        o.setMaterialCost(BigDecimal.valueOf(materialC).setScale(2, RoundingMode.HALF_UP));
        o.setOtherCost(BigDecimal.valueOf(otherC).setScale(2, RoundingMode.HALF_UP));
        o.setTotalRevenue(BigDecimal.valueOf(revenue).setScale(2, RoundingMode.HALF_UP));

        BigDecimal totalCost = o.getLaborCost().add(o.getMaterialCost()).add(o.getOtherCost());
        o.setTotalCost(totalCost);
        o.setProfit(o.getTotalRevenue().subtract(totalCost));
        if (o.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0) {
            o.setProfitRate(o.getProfit().divide(o.getTotalRevenue(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)));
        } else {
            o.setProfitRate(BigDecimal.ZERO);
        }

        o.setLocation(LOCATIONS[r.nextInt(LOCATIONS.length)]);
        o.setTechnician(TECHNICIANS[r.nextInt(TECHNICIANS.length)]);

        // 随机日期：2026年1-5月
        int month = 1 + r.nextInt(5);
        int day = 1 + r.nextInt(28);
        int hour = 8 + r.nextInt(12);
        o.setOrderTime(LocalDateTime.of(2026, month, day, hour, r.nextInt(60)));
        o.setStatus("completed");
        o.setAiCategory("未分类");
        o.setAiAnalyzed(0);
        o.setCreatedAt(LocalDateTime.now());
        return o;
    }

    private void flush(List<WorkOrder> batch, AtomicInteger counter) {
        if (batch.isEmpty()) return;
        try {
            workOrderMapper.insert(batch);
            counter.addAndGet(batch.size());
            if (counter.get() % 10000 == 0) {
                log.info("已插入 {} 条...", counter.get());
            }
        } catch (Exception e) {
            // 单条失败回退逐条插入
            for (WorkOrder o : batch) {
                try { workOrderMapper.insert(o); } catch (Exception ignored) {}
            }
        }
    }
}
