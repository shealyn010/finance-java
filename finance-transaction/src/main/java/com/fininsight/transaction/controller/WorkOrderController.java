package com.fininsight.transaction.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fininsight.common.result.R;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import com.fininsight.transaction.service.WorkOrderService;
import com.fininsight.transaction.service.WorkflowStateMachine;
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
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String status) {
        return R.ok(workOrderService.listByUser(userId, page, size, serviceType, status));
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
        // 状态机校验流转合法性
        var order = workOrderService.getById(orderId);
        if (order == null) throw new com.fininsight.common.exception.BizException(404, "工单不存在");
        stateMachine.validate(order.getStatus(), status);
        // 关单7天内可撤销(回到completed)
        if ("completed".equals(status) && "closed".equals(order.getStatus())
            && order.getOrderTime() != null
            && java.time.Duration.between(order.getOrderTime(), java.time.LocalDateTime.now()).toDays() > 7) {
            throw new com.fininsight.common.exception.BizException(400, "关单超过7天不可撤销");
        }
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
