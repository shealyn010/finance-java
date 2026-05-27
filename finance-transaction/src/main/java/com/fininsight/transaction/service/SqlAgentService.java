package com.fininsight.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
public class SqlAgentService {
    private final JdbcTemplate jdbc;
    private final WebClient webClient;
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String SCHEMA = """
        表名: work_order
        字段: order_id(VARCHAR), user_id(BIGINT), customer(VARCHAR), service_type(VARCHAR),
              service_desc(VARCHAR), labor_hours(DECIMAL), labor_cost(DECIMAL), material_cost(DECIMAL),
              other_cost(DECIMAL), total_revenue(DECIMAL), total_cost(DECIMAL), profit(DECIMAL),
              profit_rate(DECIMAL), location(VARCHAR), technician(VARCHAR),
              order_time(DATETIME), status(VARCHAR), ai_category(VARCHAR)
        说明: service_type取值: 安装/维修/巡检/保养/定制
              location包含: 中山市+镇区名(如中山市古镇)
              order_time覆盖2024-01~2026-05
              profit_rate是百分比值, profit=total_revenue-total_cost""";

    public SqlAgentService(JdbcTemplate jdbc, WebClient webClient) {
        this.jdbc = jdbc;
        this.webClient = webClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> query(Long userId, String question) {
        try {
            // 1. LLM生成SQL
            String sqlPrompt = String.format("""
                你是SQL专家。根据以下表结构生成一条只读SELECT语句回答用户问题。
                规则：只返回SQL，不要解释；只允许SELECT；WHERE条件必须包含user_id=%d；
                查询结果限制50行。

                %s

                问题：%s""", userId, SCHEMA, question);

            Map<String, Object> resp = webClient.post().uri("/v1/chat/completions")
                .bodyValue(Map.of("model","deepseek-chat","max_tokens",300,"temperature",0,
                    "messages",List.of(Map.of("role","user","content",sqlPrompt))))
                .retrieve().bodyToMono(Map.class).block();

            List<Map<String,Object>> choices = (List<Map<String,Object>>) resp.get("choices");
            String sql = ((String)((Map)choices.get(0).get("message")).get("content"))
                .replaceAll("```sql\\s*","").replaceAll("```","").trim();

            log.info("[SQL-Agent] 生成SQL: {}", sql.substring(0, Math.min(200, sql.length())));

            // 2. 执行查询（失败自动修复重试1次）
            List<Map<String, Object>> results;
            try {
                results = jdbc.queryForList(sql);
            } catch (Exception sqlEx) {
                log.warn("[SQL-Agent] SQL执行失败, 让LLM修复: {}", sqlEx.getMessage());
                String fixPrompt = String.format("SQL报错: %s\n请修复以下SQL(只返回修复后的SQL):\n%s",
                    sqlEx.getMessage(), sql);
                Map<String, Object> fixResp = webClient.post().uri("/v1/chat/completions")
                    .bodyValue(Map.of("model","deepseek-chat","max_tokens",300,"temperature",0,
                        "messages",List.of(Map.of("role","user","content",fixPrompt))))
                    .retrieve().bodyToMono(Map.class).block();
                List<Map<String,Object>> fixChoices = (List<Map<String,Object>>) fixResp.get("choices");
                sql = ((String)((Map)fixChoices.get(0).get("message")).get("content"))
                    .replaceAll("```sql\\s*","").replaceAll("```","").trim();
                results = jdbc.queryForList(sql);
            }

            // 3. 让LLM解读结果
            String answerPrompt = String.format("""
                用户问题: %s
                查询结果(前20行): %s
                请用简洁中文回答，引用具体数据。100字以内。""",
                question, om.writeValueAsString(results.subList(0, Math.min(20, results.size()))));

            Map<String, Object> resp2 = webClient.post().uri("/v1/chat/completions")
                .bodyValue(Map.of("model","deepseek-chat","max_tokens",300,"temperature",0.3,
                    "messages",List.of(Map.of("role","user","content",answerPrompt))))
                .retrieve().bodyToMono(Map.class).block();

            List<Map<String,Object>> choices2 = (List<Map<String,Object>>) resp2.get("choices");
            String answer = (String)((Map)choices2.get(0).get("message")).get("content");

            // 4. 返回
            Map<String, Object> result = new HashMap<>();
            result.put("reply", answer);
            result.put("role", "assistant");
            result.put("dataContext", "SQL: " + sql + "\n\n结果(" + results.size() + "行):\n"
                + om.writeValueAsString(results.subList(0, Math.min(10, results.size()))));
            result.put("sql", sql);
            return result;

        } catch (Exception e) {
            log.error("[SQL-Agent] 失败: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("reply", "抱歉，查询失败: " + e.getMessage());
            fallback.put("role", "assistant");
            fallback.put("dataContext", "SQL生成或执行出错");
            return fallback;
        }
    }
}
