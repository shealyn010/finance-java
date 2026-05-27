package com.fininsight.transaction.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fininsight.common.exception.BizException;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private final WorkOrderMapper workOrderMapper;

    public WorkOrder create(WorkOrder order) {
        order.setOrderId(IdUtil.fastSimpleUUID());
        // 自动计算
        BigDecimal totalCost = BigDecimal.ZERO
                .add(order.getLaborCost() != null ? order.getLaborCost() : BigDecimal.ZERO)
                .add(order.getMaterialCost() != null ? order.getMaterialCost() : BigDecimal.ZERO)
                .add(order.getOtherCost() != null ? order.getOtherCost() : BigDecimal.ZERO);
        order.setTotalCost(totalCost);

        BigDecimal revenue = order.getTotalRevenue() != null ? order.getTotalRevenue() : BigDecimal.ZERO;
        order.setProfit(revenue.subtract(totalCost));

        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            order.setProfitRate(order.getProfit()
                    .divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        } else {
            order.setProfitRate(BigDecimal.ZERO);
        }

        order.setStatus("pending");
        order.setAiCategory("未分类");
        order.setAiAnalyzed(0);
        order.setCreatedAt(LocalDateTime.now());
        workOrderMapper.insert(order);
        return order;
    }

    public Page<WorkOrder> listByUser(Long userId, int page, int size, String serviceType) {
        LambdaQueryWrapper<WorkOrder> qw = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getUserId, userId)
                .eq(serviceType != null && !serviceType.isEmpty(), WorkOrder::getServiceType, serviceType)
                .orderByDesc(WorkOrder::getOrderTime);
        return workOrderMapper.selectPage(new Page<>(page, size), qw);
    }

    public List<Map<String, Object>> profitByLocation(Long userId, String start, String end) {
        return workOrderMapper.profitByLocation(userId, start, end);
    }

    public List<Map<String, Object>> statsByServiceType(Long userId) {
        return workOrderMapper.statsByServiceType(userId);
    }

    public WorkOrder updateStatus(String orderId, String status) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) throw new BizException(404, "工单不存在");
        order.setStatus(status);
        workOrderMapper.updateById(order);
        return order;
    }
}
