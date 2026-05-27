package com.fininsight.transaction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Value("${fininsight.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${fininsight.ai.api-key:}")
    private String apiKey;

    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
