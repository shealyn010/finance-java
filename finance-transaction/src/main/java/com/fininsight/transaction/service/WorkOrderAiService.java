package com.fininsight.transaction.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fininsight.transaction.entity.WorkOrder;
import com.fininsight.transaction.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 工单 AI 分析服务
 * <p>
 * 调用 DeepSeek API 对工单进行智能分析：
 * 1. 利润分级（高利润/常规/亏损/重点客户）
 * 2. 异常检测（异常工时/异常材料费）
 * 3. 经营建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderAiService {
    private final WorkOrderMapper workOrderMapper;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_TEMPLATE = """
            你是一个企业工单分析专家。分析以下工单数据，返回 JSON（不要其他文字）：
            {
              "category": "高利润/常规/亏损",
              "anomalies": ["异常项"],
              "suggestion": "经营建议（50字以内）"
            }

            分析规则：
            - 利润率 > 40%: 高利润
            - 利润率 10%-40%: 常规
            - 利润率 < 10% 或亏损: 亏损
            - 材料费占比 > 70%: 标记为材料成本异常
            - 工时超过8小时: 标记为工时异常

            工单数据：""";

    /** 对单条工单进行 AI 分析 */
    public void analyzeOrder(String orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) return;
        // 纯规则分类（零Token，Ollama可选）
        double rate = order.getProfitRate() != null ? order.getProfitRate().doubleValue() : 0;
        String cat = rate > 40 ? "高利润" : rate < 10 ? "亏损" : "常规";
        order.setAiCategory(cat);
        order.setAiAnalyzed(1);
        workOrderMapper.updateById(order);
        log.info("[AI分析] {}: {}", orderId.substring(0,12), cat);
    }

    public void _analyzeOrder_old(String orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) return;

        try {
            String prompt = PROMPT_TEMPLATE + String.format(
                    "服务类型: %s, 收入: %s, 人工费: %s, 材料费: %s, 工时: %s, 利润率: %s%%",
                    order.getServiceType(), order.getTotalRevenue(),
                    order.getLaborCost(), order.getMaterialCost(),
                    order.getLaborHours(), order.getProfitRate());

            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(Map.of(
                            "model", "deepseek-chat",
                            "messages", List.of(
                                    Map.of("role", "user", "content", prompt)
                            ),
                            "max_tokens", 300,
                            "temperature", 0.3
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```", "").trim();

            JsonNode analysis = objectMapper.readTree(content);
            order.setAiCategory(analysis.path("category").asText("未分类"));
            order.setAiAnalyzed(1);
            workOrderMapper.updateById(order);

            log.info("[AI分析] 工单 {}: category={}", orderId, order.getAiCategory());
        } catch (Exception e) {
            log.error("[AI分析] 工单 {} 失败: {}", orderId, e.getMessage());
            // 降级：规则兜底
            fallbackAnalyze(order);
        }
    }

    /** 规则兜底分类 */
    private void fallbackAnalyze(WorkOrder order) {
        BigDecimal rate = order.getProfitRate();
        if (rate == null) return;
        if (rate.compareTo(new BigDecimal("40")) > 0) order.setAiCategory("高利润");
        else if (rate.compareTo(new BigDecimal("10")) > 0) order.setAiCategory("常规");
        else order.setAiCategory("亏损");
        order.setAiAnalyzed(1);
        workOrderMapper.updateById(order);
    }
}
