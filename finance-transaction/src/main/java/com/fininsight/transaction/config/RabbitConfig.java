package com.fininsight.transaction.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String AI_ANALYZE_QUEUE = "ai.analyze";
    public static final String ORDER_CREATE_QUEUE = "order.create";

    @Bean
    public Queue aiAnalyzeQueue() {
        return new Queue(AI_ANALYZE_QUEUE, true);
    }

    @Bean
    public Queue orderCreateQueue() {
        return new Queue(ORDER_CREATE_QUEUE, true);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate t = new RabbitTemplate(factory);
        t.setMessageConverter(new Jackson2JsonMessageConverter());
        t.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) System.err.println("[MQ] 消息未确认: " + cause);
        });
        return t;
    }
}
