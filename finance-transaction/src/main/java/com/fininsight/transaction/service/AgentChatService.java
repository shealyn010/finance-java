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

    /** 用户对话上下文（生产环境应使用 Redis）；StringBuffer 线程安全 */
    private final Map<Long, StringBuffer> context = new ConcurrentHashMap<>();

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
            // 1. 意图分类（关键词强制定路由）
            String intent = classifyIntent(message);
            if (message.contains("哪个月") || message.contains("亏损") || message.contains("月份")
                || message.contains("材料费") || message.contains("成本") || message.contains("占比")
                || message.contains("库存")) {
                intent = "sql_query";
            }

            // 2. 根据意图走不同数据获取策略
            String dataContext = switch (intent) {
                case "specific_query" -> buildTargetedContext(userId, message);  // 时间+类型精准查
                case "advice" -> buildAdviceContext(userId);                     // 近12月趋势+建议
                default -> buildOverviewContext(userId);                          // 总览兜底
            };

            // 3. 构建 prompt（建议类加分析指令）
            String sysPrompt = intent.equals("advice")
                ? SYSTEM_PROMPT + "\n请基于近12个月趋势数据给出具体可操作的建议，引用数据支撑。"
                : SYSTEM_PROMPT;

            // 4. 调用 DeepSeek
            String userPrompt = dataContext + "\n\n用户问题：" + message;
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
        String dataContext = buildOverviewContext(userId);
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

    /** 意图分类 */
    @SuppressWarnings("unchecked")
    private String classifyIntent(String question) {
        try {
            String prompt = "判断用户意图，只返回一个词: sql_query(问具体数据/数量/统计/利润/库存) / knowledge(问知识/概念/怎么排查/是什么/干啥用的/规格参数) / advice(求建议/优化) / overview(其他)";
            Map<String, Object> resp = webClient.post().uri("/v1/chat/completions")
                .bodyValue(Map.of("model","deepseek-chat","max_tokens",20,"temperature",0,
                    "messages",List.of(Map.of("role","user","content",prompt+"\n\n问题:"+question))))
                .retrieve().bodyToMono(Map.class).block();
            List<Map<String,Object>> choices = (List<Map<String,Object>>) resp.get("choices");
            String intent = (String)((Map<String,Object>)choices.get(0).get("message")).get("content");
            return intent != null ? intent.trim().toLowerCase() : "overview";
        } catch(Exception e) { return "overview"; }
    }

    /** 总览上下文 */
    private String buildOverviewContext(Long userId) {
        var ov = workOrderMapper.overview(userId);
        return String.format("总览: %s单, 收入¥%s, 利润¥%s, 利润率%s%%, 亏损%s单",
            ov.get("total"), ov.get("revenue"), ov.get("profit"), ov.get("avg_rate"), ov.get("loss_count"));
    }

    /** 建议上下文：近12月趋势 */
    private String buildAdviceContext(Long userId) {
        StringBuffer sb = new StringBuffer();
        var ov = workOrderMapper.overview(userId);
        sb.append(String.format("## 总览\n%s单 | 利润¥%s | 利润率%s%%\n\n", ov.get("total"), ov.get("profit"), ov.get("avg_rate")));
        var monthly = workOrderMapper.monthlyStats(userId);
        sb.append("## 近12月趋势\n| 月份 | 工单 | 利润 | 利润率 | 亏损 |\n|------|------|------|--------|------|\n");
        int c = 0;
        for (var m : monthly) { if (c++ >= 12) break;
            sb.append(String.format("| %s | %s | ¥%s | %s%% | %s |\n", m.get("month"), m.get("orders"), m.get("profit"), m.get("avg_rate"), m.get("loss_orders")));
        }
        var yt = workOrderMapper.yearlyByType(userId);
        sb.append("\n## 年度×类型\n");
        for (var r : yt) { sb.append(String.format("%s %s: %s单, 利润¥%s, %s%%\n", r.get("year"), r.get("service_type"), r.get("orders"), r.get("profit"), r.get("avg_rate"))); }
        return sb.toString();
    }

    private static final java.util.regex.Pattern YEAR_PAT = java.util.regex.Pattern.compile("(\\d{4})\\s*年");
    private static final java.util.regex.Pattern MONTH_PAT = java.util.regex.Pattern.compile("(\\d{1,2})\\s*月");
    private static final java.util.regex.Pattern TYPE_PAT = java.util.regex.Pattern.compile("(维修|安装|巡检|保养|定制)");
    private static final java.util.regex.Pattern LOC_PAT = java.util.regex.Pattern.compile("(古镇|小榄|石岐|东区|西区|南头|火炬|三乡|板芙|港口|开发区)");

    private String resolveYear(String q) {
        var m = YEAR_PAT.matcher(q);
        if (m.find()) return m.group(1);
        // 相对时间
        int thisYear = java.time.LocalDate.now().getYear();
        if (q.contains("去年") || q.contains("上一年")) return String.valueOf(thisYear - 1);
        if (q.contains("今年") || q.contains("本年")) return String.valueOf(thisYear);
        if (q.contains("前年")) return String.valueOf(thisYear - 2);
        if (q.contains("明年")) return String.valueOf(thisYear + 1);
        return null;
    }

    /** 精准查询：正则提取实体 + 定向SQL */
    private String buildTargetedContext(Long userId, String question) {
        try {
            var mm = MONTH_PAT.matcher(question);
            var tm = TYPE_PAT.matcher(question);
            var lm = LOC_PAT.matcher(question);
            String year = resolveYear(question);
            String month = mm.find() ? String.format("%02d", Integer.parseInt(mm.group(1))) : null;
            String svcType = tm.find() ? tm.group(1) : null;
            String loc = lm.find() ? lm.group(1) : null;
            // 亏损/利润类问题必须有月度数据
            boolean needMonthly = question.contains("亏损") || question.contains("哪个月") || question.contains("月份");

            StringBuffer sb = new StringBuffer();

            // 年度×类型精准匹配
            if (year != null || svcType != null) {
                var yt = workOrderMapper.yearlyByType(userId);
                sb.append("## 年度×类型匹配\n| 年份 | 类型 | 单数 | 利润 | 利润率 |\n|------|------|------|------|--------|\n");
                int matched = 0;
                for (var r : yt) {
                    String ry = (String) r.get("year");
                    String rt = (String) r.get("service_type");
                    if (year != null && !ry.equals(year)) continue;
                    if (svcType != null && !rt.contains(svcType)) continue;
                    sb.append(String.format("| %s | %s | %s | ¥%s | %s%% |\n",
                        ry, rt, r.get("orders"), r.get("profit"), r.get("avg_rate")));
                    matched++;
                }
                if (matched == 0) sb.append("(无匹配)\n");
                sb.append("\n");
            }

            // 材料费/成本类问题
            if (question.contains("材料费") || question.contains("成本") || question.contains("占比")) {
                var mr = year != null ? workOrderMapper.materialRatioByYearType(userId)
                                     : workOrderMapper.materialRatioByType(userId);
                sb.append("## 材料费占比\n| 年份 | 类型 | 单数 | 材料费 | 收入 | 占比 |\n|------|------|------|--------|------|------|\n");
                for (var r : mr) {
                    String ry = r.get("year") != null ? (String) r.get("year") : (year != null ? year : "");
                    sb.append(String.format("| %s | %s | %s | ¥%s | ¥%s | %s%% |\n",
                        ry, r.get("service_type"), r.get("orders"),
                        r.get("total_material"), r.get("revenue"), r.get("material_ratio")));
                }
                sb.append("\n");
            }

            // 月度明细（年份指定 或 亏损/月份类问题）
            if (year != null || needMonthly) {
                var monthly = workOrderMapper.monthlyStats(userId);
                String label = year != null ? year + "年" : "";
                sb.append(label + "月度明细:\n");
                int mc = 0;
                for (var m : monthly) {
                    String mth = (String) m.get("month");
                    boolean show = year != null ? mth.startsWith(year) : (mc++ < 12);
                    if (show) {
                        sb.append(String.format("%s: %s单, 利润¥%s, 亏损%s\n",
                            mth, m.get("orders"), m.get("profit"), m.get("loss_orders")));
                    }
                }
            }

            // 年度×类型交叉
            var yt = workOrderMapper.yearlyByType(userId);
            if (year != null || svcType != null) {
                sb.append("\n匹配的年度×类型数据:\n");
                for (var r : yt) {
                    String ry = (String) r.get("year");
                    String rt = (String) r.get("service_type");
                    boolean match = true;
                    if (year != null && !ry.equals(year)) match = false;
                    if (svcType != null && !rt.contains(svcType)) match = false;
                    if (match) {
                        sb.append(String.format("%s %s: %s单, 利润¥%s, 利润率%s%%\n",
                            ry, rt, r.get("orders"), r.get("profit"), r.get("avg_rate")));
                    }
                }
            }

            // 年度汇总
            if (year == null) {
                var yearly = workOrderMapper.yearlyStats(userId);
                sb.append("\n年度汇总:\n");
                for (var y : yearly) {
                    sb.append(String.format("%s: %s单, 利润¥%s, 利润率%s%%\n",
                        y.get("year"), y.get("orders"), y.get("profit"), y.get("avg_rate")));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            // 降级：返回概览
            try {
                var ov = workOrderMapper.overview(userId);
                return String.format("总览: %s单, 利润¥%s", ov.get("total"), ov.get("profit"));
            } catch (Exception ex) { return "暂无数据"; }
        }
    }
}
