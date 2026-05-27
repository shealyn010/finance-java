package com.fininsight.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 对话查询服务
 * <p>
 * 用户用自然语言查询工单数据，Agent 调用内部服务获取数据后生成回答。
 * 例如: "古镇上个月维修工单利润怎么样？"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {
    private final WorkOrderMapper workOrderMapper;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /** 用户对话上下文（生产环境应使用 Redis） */
    private final Map<Long, StringBuilder> context = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            你是"小福"，企业工单AI分析助手。你可以：
            1. 查询工单利润、成本数据
            2. 按地点/服务类型分析经营状况
            3. 给出经营优化建议

            回答要求：简洁专业，使用具体数据，控制在200字内。""";

    /** 同步对话 */
    @SuppressWarnings("unchecked")
    public String chat(Long userId, String message) {
        try {
            // 1. 获取用户数据上下文
            String dataContext = buildDataContext(userId);

            // 2. 构建完整 prompt
            String userPrompt = dataContext + "\n\n用户问题：" + message;

            // 3. 调用 DeepSeek
            Map<String, Object> response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(Map.of(
                            "model", "deepseek-chat",
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", userPrompt)
                            ),
                            "max_tokens", 500,
                            "temperature", 0.5
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.getOrDefault("content", "AI 服务暂时不可用");

        } catch (Exception e) {
            log.error("[Agent对话] 失败: {}", e.getMessage());
            return "抱歉，我暂时无法处理您的查询，请稍后重试。";
        }
    }

    /** 流式对话 */
    @SuppressWarnings("unchecked")
    public Flux<String> chatStream(Long userId, String message) {
        String dataContext = buildDataContext(userId);
        String userPrompt = dataContext + "\n\n用户问题：" + message;

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(Map.of(
                        "model", "deepseek-chat",
                        "messages", List.of(
                                Map.of("role", "system", "content", SYSTEM_PROMPT),
                                Map.of("role", "user", "content", userPrompt)
                        ),
                        "max_tokens", 500,
                        "temperature", 0.5,
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(Map.class)
                .mapNotNull(chunk -> {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices == null || choices.isEmpty()) return null;
                    Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                    if (delta == null) return null;
                    Object content = delta.get("content");
                    return content != null ? content.toString() : null;
                })
                .filter(s -> !s.isEmpty());
    }

    /** 构建用户数据上下文 */
    private String buildDataContext(Long userId) {
        try {
            var stats = workOrderMapper.statsByServiceType(userId);
            if (stats.isEmpty()) return "暂无工单数据。";

            StringBuilder sb = new StringBuilder("当前用户工单数据概览：\n");
            for (var row : stats) {
                sb.append(String.format("- %s: %s单, 收入¥%s, 利润¥%s\n",
                        row.get("service_type"),
                        row.get("orders"),
                        row.get("revenue"),
                        row.get("profit")));
            }
            return sb.toString();
        } catch (Exception e) {
            return "暂无工单数据。";
        }
    }
}
