package com.fininsight.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> chat(Long userId, String message) {
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
            String reply = (String) msg.getOrDefault("content", "AI 服务暂时不可用");

            // 返回 reply + AI 基于的原始数据上下文(供前端校验)
            Map<String, Object> result = new HashMap<>();
            result.put("reply", reply);
            result.put("role", "assistant");
            result.put("dataContext", dataContext);
            return result;

        } catch (Exception e) {
            log.error("[Agent对话] 失败: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("reply", "抱歉，我暂时无法处理您的查询。");
            fallback.put("role", "assistant");
            fallback.put("dataContext", "暂无数据");
            return fallback;
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

    /** 构建用户数据上下文——多维度喂给LLM */
    private String buildDataContext(Long userId) {
        try {
            StringBuilder sb = new StringBuilder();
            Map<String, Object> ov = workOrderMapper.overview(userId);

            sb.append(String.format("## 用户工单总览\n总工单: %s单 | 总收入: ¥%s | 总利润: ¥%s | 平均利润率: %s%% | 亏损单: %s单 | 高利润单: %s单\n\n",
                ov.get("total"), ov.get("revenue"), ov.get("profit"),
                ov.get("avg_rate"), ov.get("loss_count"), ov.get("high_profit_count")));

            // 按月统计（最近12个月）
            var monthly = workOrderMapper.monthlyStats(userId);
            if (!monthly.isEmpty()) {
                sb.append("## 最近12个月利润明细\n| 月份 | 工单数 | 收入 | 利润 | 利润率 | 亏损单 |\n|------|--------|------|------|--------|--------|\n");
                int count = 0;
                for (var m : monthly) {
                    if (count++ >= 12) break;
                    sb.append(String.format("| %s | %s | ¥%s | ¥%s | %s%% | %s |\n",
                        m.get("month"), m.get("orders"), m.get("revenue"),
                        m.get("profit"), m.get("avg_rate"), m.get("loss_orders")));
                }
            }

            // 按类型统计
            var byType = workOrderMapper.statsByServiceType(userId);
            if (!byType.isEmpty()) {
                sb.append("\n## 按服务类型统计\n| 类型 | 工单数 | 收入 | 利润 |\n|------|--------|------|------|\n");
                for (var t : byType) {
                    sb.append(String.format("| %s | %s | ¥%s | ¥%s |\n",
                        t.get("service_type"), t.get("orders"), t.get("revenue"), t.get("profit")));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "暂无工单数据。";
        }
    }
}
