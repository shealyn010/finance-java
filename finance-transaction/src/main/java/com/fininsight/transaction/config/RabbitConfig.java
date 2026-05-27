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

    @Bean
    public Queue aiAnalyzeQueue() {
        return new Queue(AI_ANALYZE_QUEUE, true); // durable
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
