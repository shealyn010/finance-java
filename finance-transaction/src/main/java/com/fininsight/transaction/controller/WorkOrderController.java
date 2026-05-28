package com.fininsight.transaction.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fininsight.common.result.R;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import com.fininsight.transaction.service.WorkOrderService;
import org.springframework.web.bind.annotation.*;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-order")
public class WorkOrderController {
    private final WorkOrderService workOrderService;
    private final WorkOrderMapper workOrderMapper;
    private final WorkflowStateMachine stateMachine;

    public WorkOrderController(WorkOrderService workOrderService, WorkOrderMapper workOrderMapper,
                               WorkflowStateMachine stateMachine) {
        this.workOrderService = workOrderService;
        this.workOrderMapper = workOrderMapper;
        this.stateMachine = stateMachine;
    }

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

    @Cacheable(value = "report", key = "'typeStats_' + #userId", unless = "#result.data.isEmpty()")
    @GetMapping("/stats-by-type")
    public R<List<Map<String, Object>>> statsByType(@RequestParam(name="userId") Long userId) {
        return R.ok(workOrderService.statsByServiceType(userId));
    }

    @PutMapping("/{orderId}/status")
    public R<WorkOrder> updateStatus(@PathVariable("orderId") String orderId, @RequestParam(name="status") String status) {
        return R.ok(workOrderService.updateStatus(orderId, status));
    }

    @Cacheable(value = "report", key = "'monthly_' + #userId", unless = "#result.data.isEmpty()")
    @GetMapping("/monthly-stats")
    public R<List<Map<String, Object>>> monthlyStats(@RequestParam(name="userId") Long userId) {
        // 优先走物化视图(毫秒级)，数据为空则回退到实时聚合
        var fast = workOrderMapper.monthlyStatsFast(userId);
        if (!fast.isEmpty()) return R.ok(fast);
        return R.ok(workOrderMapper.monthlyStats(userId));
    }
}
