package com.fininsight.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Agent 路由器 — LLM语义分析 → 路由到下游专业Agent
 * 不是关键词匹配，是让LLM理解问题含义后自主决策
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRouter {
    private final WebClient webClient;
    private final ObjectMapper om = new ObjectMapper();

    public record RouteDecision(String target, String reasoning, String entities) {}

    private static final String ROUTER_PROMPT = """
        你是Agent路由器。分析用户问题，决定交给哪个专业Agent处理。

        可选目标Agent:
        - rag: 问知识/概念/原理/怎么排查/是什么/规格参数/故障原因 (不需要查数据库)
        - sql: 问具体数据/统计/利润/库存数量/收入/多少人/多少单 (需要查数据库)
        - advice: 求建议/怎么优化/怎么提升
        - chat: 闲聊/问候

        返回JSON(不要其他文字):
        {"target":"rag","reasoning":"用户在问轴承的用途,属于知识类问题"}
        """;

    @SuppressWarnings("unchecked")
    public RouteDecision route(String question) {
        try {
            Map<String, Object> resp = webClient.post().uri("/v1/chat/completions")
                .bodyValue(Map.of(
                    "model", "deepseek-chat", "max_tokens", 150, "temperature", 0,
                    "messages", List.of(
                        Map.of("role", "system", "content", ROUTER_PROMPT),
                        Map.of("role", "user", "content", question)
                    )
                ))
                .retrieve().bodyToMono(Map.class).block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            String json = cleanJson(((String) ((Map) choices.get(0).get("message")).get("content")));
            var node = om.readTree(json);

            return new RouteDecision(
                node.get("target").asText(),
                node.has("reasoning") ? node.get("reasoning").asText() : "",
                ""
            );
        } catch (Exception e) {
            log.warn("[Router] LLM路由失败,降级为sql: {}", e.getMessage());
            return new RouteDecision("sql", "路由失败降级", "");
        }
    }

    private String cleanJson(String s) {
        return s.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
    }
}
