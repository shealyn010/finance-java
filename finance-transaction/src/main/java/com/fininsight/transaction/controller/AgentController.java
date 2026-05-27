package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.service.AgentChatService;
import com.fininsight.transaction.service.SqlAgentService;
import com.fininsight.transaction.service.WorkOrderAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentChatService agentChatService;
    private final WorkOrderAiService workOrderAiService;
    private final SqlAgentService sqlAgentService;

    public AgentController(AgentChatService agentChatService,
                           WorkOrderAiService workOrderAiService,
                           SqlAgentService sqlAgentService) {
        this.agentChatService = agentChatService;
        this.workOrderAiService = workOrderAiService;
        this.sqlAgentService = sqlAgentService;
    }

    /** 对话查询（同步） */
    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestParam(name="userId") Long userId,
                                        @RequestParam(name="message") String message) {
        return R.ok(agentChatService.chat(userId, message));
    }

    /** 对话查询（流式 SSE） */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam(name="userId") Long userId,
                                    @RequestParam(name="message") String message) {
        return agentChatService.chatStream(userId, message);
    }

    /** Text-to-SQL 智能查询 */
    @PostMapping("/sql-query")
    public R<Map<String, Object>> sqlQuery(@RequestParam(name="userId") Long userId,
                                            @RequestParam(name="message") String message) {
        return R.ok(sqlAgentService.query(userId, message));
    }

    /** 对工单进行 AI 分析 */
    @PostMapping("/analyze/{orderId}")
    public R<String> analyze(@PathVariable("orderId") String orderId) {
        workOrderAiService.analyzeOrder(orderId);
        return R.ok("分析完成");
    }
}
