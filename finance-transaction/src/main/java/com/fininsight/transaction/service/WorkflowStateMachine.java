package com.fininsight.transaction.service;

import com.fininsight.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工单生命周期状态机
 * <pre>
 * pending ──assign──> assigned ──accept──> arrived ──start──> in_progress
 *                                                               │
 *                                               ┌───────────────┼──────────────┐
 *                                               ▼               ▼              ▼
 *                                         parts_needed      completed       closed
 *                                               │               │
 *                                               └──resume──> in_progress
 *                                                               │
 *                                                               ▼
 *                                                           completed ──confirm──> confirmed ──settle──> settled
 * </pre>
 */
@Component
public class WorkflowStateMachine {

    private static final Map<String, Set<String>> ALLOWED = new HashMap<>();
    static {
        ALLOWED.put("pending",      Set.of("assigned", "closed"));
        ALLOWED.put("assigned",     Set.of("accepted", "closed", "pending"));
        ALLOWED.put("accepted",     Set.of("arrived", "closed"));
        ALLOWED.put("arrived",      Set.of("in_progress", "closed"));
        ALLOWED.put("in_progress",  Set.of("completed", "parts_needed", "closed"));
        ALLOWED.put("parts_needed", Set.of("in_progress", "closed"));
        ALLOWED.put("completed",    Set.of("confirmed", "closed"));
        ALLOWED.put("confirmed",    Set.of("settled", "closed"));
        ALLOWED.put("settled",      Set.of());
        ALLOWED.put("closed",       Set.of("reopen"));
        ALLOWED.put("reopen",       Set.of("in_progress", "completed"));
    }

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("pending",      "待分派"),
        Map.entry("assigned",     "已分派"),
        Map.entry("accepted",     "技术员已接单"),
        Map.entry("arrived",      "已到场"),
        Map.entry("in_progress",  "维修中"),
        Map.entry("parts_needed", "待配件"),
        Map.entry("completed",    "已完成"),
        Map.entry("confirmed",    "客户已确认"),
        Map.entry("settled",      "已结算"),
        Map.entry("closed",       "已关单")
    );

    /** 校验状态流转是否合法 */
    public void validate(String from, String to) {
        if (from == null || to == null) throw new BizException(400, "状态不能为空");
        Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BizException(400, String.format(
                "状态流转不合法: [%s] → [%s]，允许: %s", from, to, allowed));
        }
    }

    /** 获取状态中文标签 */
    public String label(String status) {
        return LABELS.getOrDefault(status, status);
    }

    /** 获取所有允许的下一状态 */
    public Set<String> nextStates(String current) {
        return ALLOWED.getOrDefault(current, Set.of());
    }

    /** 模拟业务：创建工单时选择初始状态 */
    public String initialStatus(String serviceType) {
        // 定制/巡检类工单可以直接分派到技术员
        if ("定制".equals(serviceType) || "巡检".equals(serviceType)) {
            return "assigned";
        }
        return "pending";
    }
}
