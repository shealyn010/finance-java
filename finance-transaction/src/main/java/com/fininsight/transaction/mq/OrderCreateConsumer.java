package com.fininsight.transaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.config.RabbitConfig;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.service.WorkOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 工单创建 MQ 消费者 — 削峰填谷 + 手动ACK防丢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {
    private final WorkOrderService workOrderService;
    private final ObjectMapper om = new ObjectMapper();

    @RabbitListener(queues = RabbitConfig.ORDER_CREATE_QUEUE, concurrency = "5-10",
                    ackMode = "MANUAL")
    public void onMessage(String json, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            WorkOrder order = om.readValue(json, WorkOrder.class);
            workOrderService.createDirect(order);
            channel.basicAck(tag, false);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一键冲突 = 重复消息，直接ACK不重试
            log.info("[MQ-Order] 重复消息(orderId已存在), 幂等跳过: {}", e.getMessage());
            try { channel.basicAck(tag, false); } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("[MQ-Order] 写入失败, 消息重回队列: {}", e.getMessage());
            try { channel.basicNack(tag, false, true); } catch (Exception ignored) {}
        }
    }
}
