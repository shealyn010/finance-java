package com.fininsight.transaction.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fininsight.common.result.R;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-order")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService workOrderService;

    @PostMapping
    public R<WorkOrder> create(@RequestBody WorkOrder order) {
        return R.ok(workOrderService.create(order));
    }

    @GetMapping
    public R<Page<WorkOrder>> list(
            @RequestParam(name="userId") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String serviceType) {
        return R.ok(workOrderService.listByUser(userId, page, size, serviceType));
    }

    @GetMapping("/profit-by-location")
    public R<List<Map<String, Object>>> profitByLocation(
            @RequestParam(name="userId") Long userId,
            @RequestParam(name="start") String start,
            @RequestParam(name="end") String end) {
        return R.ok(workOrderService.profitByLocation(userId, start, end));
    }

    @GetMapping("/stats-by-type")
    public R<List<Map<String, Object>>> statsByType(@RequestParam(name="userId") Long userId) {
        return R.ok(workOrderService.statsByServiceType(userId));
    }

    @PutMapping("/{orderId}/status")
    public R<WorkOrder> updateStatus(@PathVariable("orderId") String orderId, @RequestParam(name="status") String status) {
        return R.ok(workOrderService.updateStatus(orderId, status));
    }
}
