package com.fininsight.transaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.config.RabbitConfig;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 工单创建 MQ 消费者 — 削峰填谷
 * 请求先进队列 → 消费者限速写入 MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {
    private final WorkOrderService workOrderService;
    private final ObjectMapper om = new ObjectMapper();

    @RabbitListener(queues = RabbitConfig.ORDER_CREATE_QUEUE, concurrency = "5-10")
    public void onMessage(String json) {
        try {
            WorkOrder order = om.readValue(json, WorkOrder.class);
            workOrderService.createDirect(order); // 跳过MQ,直接写库
        } catch (Exception e) {
            log.error("[MQ-Order] 写入失败: {}", e.getMessage());
        }
    }
}
