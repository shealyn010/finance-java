package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.service.AgentChatService;
import com.fininsight.transaction.service.WorkOrderAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentChatService agentChatService;
    private final WorkOrderAiService workOrderAiService;

    /** 对话查询（同步） */
    @PostMapping("/chat")
    public R<Map<String, String>> chat(@RequestParam Long userId,
                                        @RequestParam String message) {
        String reply = agentChatService.chat(userId, message);
        return R.ok(Map.of("reply", reply, "role", "assistant"));
    }

    /** 对话查询（流式 SSE） */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam Long userId,
                                    @RequestParam String message) {
        return agentChatService.chatStream(userId, message);
    }

    /** 对工单进行 AI 分析 */
    @PostMapping("/analyze/{orderId}")
    public R<String> analyze(@PathVariable String orderId) {
        workOrderAiService.analyzeOrder(orderId);
        return R.ok("分析完成");
    }
}
