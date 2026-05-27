package com.fininsight.transaction.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fininsight.common.exception.BizException;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Service
public class WorkOrderService {
    private final WorkOrderMapper workOrderMapper;
    private final RabbitTemplate rabbitTemplate;

    public WorkOrderService(WorkOrderMapper workOrderMapper,
                            RabbitTemplate rabbitTemplate) {
        this.workOrderMapper = workOrderMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** MQ异步创建（削峰） */
    public WorkOrder createAsync(WorkOrder order) {
        validate(order);
        order.setOrderId(IdUtil.fastSimpleUUID());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("pending");
        order.setAiCategory("未分类");
        order.setAiAnalyzed(0);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.ORDER_CREATE_QUEUE,
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(order));
        } catch (Exception e) {
            return createDirect(order); // MQ挂了降级直写
        }
        return order;
    }

    /** 直接写入（MQ消费者用） */
    public WorkOrder createDirect(WorkOrder order) {
        validate(order);
        order.setOrderId(order.getOrderId() != null ? order.getOrderId() : IdUtil.fastSimpleUUID());
        if (order.getCreatedAt() == null) order.setCreatedAt(LocalDateTime.now());
        if (order.getStatus() == null) order.setStatus("pending");
        if (order.getAiCategory() == null) order.setAiCategory("未分类");
        calcProfit(order);
        workOrderMapper.insert(order);
        return order;
    }

    public WorkOrder create(WorkOrder order) {
        validate(order);
        return createDirect(order);
    }

    private void validate(WorkOrder order) {
        // 参数校验
        if (order.getCustomer() == null || order.getCustomer().isBlank()) {
            throw new BizException(400, "客户名称不能为空");
        }
        if (order.getTotalRevenue() == null || order.getTotalRevenue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "工单收入必须为非负数");
        }
        if (order.getLaborCost() != null && order.getLaborCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "人工费不能为负数");
        }
        if (order.getMaterialCost() != null && order.getMaterialCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "材料费不能为负数");
        }
        if (order.getOtherCost() != null && order.getOtherCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(400, "其他费用不能为负数");
        }
    }

    private void calcProfit(WorkOrder order) {
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
