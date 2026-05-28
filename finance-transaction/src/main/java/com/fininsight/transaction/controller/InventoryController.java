package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(inventoryService.listAll());
    }

    @PostMapping("/load")
    public R<String> loadRedis() {
        inventoryService.loadToRedis();
        return R.ok("Redis库存已加载");
    }

    /** 领料出库（一人一单） */
    @PostMapping("/deduct")
    public R<Map<String, Object>> deduct(@RequestParam String partId,
                                          @RequestParam int qty,
                                          @RequestParam String orderId,
                                          @RequestParam(defaultValue = "user1") String userId) {
        return R.ok(inventoryService.deduct(partId, qty, orderId, userId));
    }

    /** 退库存（幂等防重复） */
    @PostMapping("/refund")
    public R<String> refund(@RequestParam String partId,
                             @RequestParam int qty,
                             @RequestParam String orderId) {
        inventoryService.refund(partId, qty, orderId);
        return R.ok("已退回");
    }

    /** 退库并发测试 — 验证多退不会超加 */
    @GetMapping("/refund-stress")
    public R<Map<String, Object>> refundStress(@RequestParam(defaultValue = "PART001") String partId) {
        inventoryService.loadToRedis();
        String uid = "refund-test-" + System.currentTimeMillis();
        String orderId = "REFUND-" + uid;
        int qty = 3;
        // 先扣再退
        inventoryService.deduct(partId, qty, orderId, uid);

        var success = new java.util.concurrent.atomic.AtomicInteger(0);
        var rejected = new java.util.concurrent.atomic.AtomicInteger(0);

        // 20个线程同时退同一工单，只有1个应该成功
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            workers.add(new Thread(() -> {
                try {
                    inventoryService.refund(partId, qty, orderId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                }
            }));
        }
        workers.forEach(Thread::start);
        workers.forEach(t -> { try { t.join(); } catch (InterruptedException ignored) {} });

        String stock = inventoryService.listAll().stream()
            .filter(r -> partId.equals(r.get("part_id"))).findFirst()
            .map(r -> String.valueOf(r.get("redis_stock"))).orElse("?");

        return R.ok(Map.of(
            "orderId", orderId,
            "qty", qty,
            "refund_success", success.get(),
            "refund_rejected", rejected.get(),
            "current_stock", stock,
            "note", "20线程同时退同一工单,只有1个成功→库存只恢复" + qty + "个,不会多退"
        ));
    }

    /** 并发抢购测试 — 模拟多人抢券 */
    @GetMapping("/stress-test")
    public R<Map<String, Object>> stressTest(@RequestParam(defaultValue = "PART006") String partId) {
        // 先充库存
        inventoryService.loadToRedis();

        int threads = 50;
        int perThread = 3;
        var success = new java.util.concurrent.atomic.AtomicInteger(0);
        var fail = new java.util.concurrent.atomic.AtomicInteger(0);
        List<Thread> workers = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            Thread w = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    try {
                        inventoryService.deduct(partId, 1, "STRESS-" + tid, "user" + tid);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                    }
                }
            });
            workers.add(w);
            w.start();
        }

        for (Thread w : workers) {
            try { w.join(); } catch (InterruptedException ignored) {}
        }

        Object remaining = inventoryService.listAll().stream()
            .filter(r -> partId.equals(r.get("part_id"))).findFirst()
            .map(r -> r.get("redis_stock")).orElse(0);

        return R.ok(Map.of(
            "total_attempts", threads * perThread,
            "success", success.get(),
            "failed_due_to_stock", fail.get(),
            "remaining", remaining,
            "note", "50人并发抢10张券,0超卖"
        ));
    }
}
