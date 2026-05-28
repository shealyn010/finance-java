package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.service.AgentChatService;
import com.fininsight.transaction.service.AgentRouter;
import com.fininsight.transaction.service.ChunkingEngine;
import com.fininsight.transaction.service.RagService;
import com.fininsight.transaction.service.SqlAgentService;
import com.fininsight.transaction.service.WorkOrderAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentChatService agentChatService;
    private final WorkOrderAiService workOrderAiService;
    private final SqlAgentService sqlAgentService;
    private final RagService ragService;
    private final ChunkingEngine chunkingEngine;

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

    private final AgentRouter agentRouter;

    public AgentController(AgentChatService agentChatService,
                           WorkOrderAiService workOrderAiService,
                           SqlAgentService sqlAgentService,
                           RagService ragService,
                           ChunkingEngine chunkingEngine,
                           AgentRouter agentRouter) {
        this.agentChatService = agentChatService;
        this.workOrderAiService = workOrderAiService;
        this.sqlAgentService = sqlAgentService;
        this.ragService = ragService;
        this.chunkingEngine = chunkingEngine;
        this.agentRouter = agentRouter;
    }

    /** 路由Agent: LLM语义分析→自动分发到RAG/SQL/Advice */
    @PostMapping("/query")
    public R<Map<String, Object>> smartQuery(@RequestParam(name="userId") Long userId,
                                              @RequestParam(name="message") String message,
                                              @RequestParam(defaultValue = "") String context) {
        // 带上对话历史做语义路由
        String fullMsg = context.isEmpty() ? message : context + "\n用户: " + message;
        var route = agentRouter.route(fullMsg);
        log.info("[Router] {} → {}", route.reasoning(), message.substring(0, Math.min(30, message.length())));

        Map<String, Object> result = new HashMap<>();
        String contextPrompt = context.isEmpty() ? "" : "对话历史:\n" + context + "\n\n当前问题: ";

        switch (route.target()) {
            case "rag" -> {
                String doc = ragService.search(message);
                result.put("reply", doc != null ? doc : "知识库中暂无相关内容。");
                result.put("dataContext", "RAG知识库: " + route.reasoning());
                result.put("engine", "rag");
            }
            case "advice" -> {
                result = sqlAgentService.query(userId, contextPrompt + message);
                result.put("reply", "📊 数据分析建议:\n\n" + result.get("reply"));
                result.put("engine", "advice");
            }
            default -> {
                result = sqlAgentService.query(userId, contextPrompt + message);
                result.put("engine", "sql");
            }
        }

        result.put("role", "assistant");
        result.put("route", route.target());
        result.put("route_reason", route.reasoning());
        return R.ok(result);
    }

    /** Text-to-SQL 智能查询 (兼容旧接口) */
    @PostMapping("/sql-query")
    public R<Map<String, Object>> sqlQuery(@RequestParam(name="userId") Long userId,
                                            @RequestParam(name="message") String message) {
        return R.ok(sqlAgentService.query(userId, message));
    }

    /** 切片策略对比（教学用） */
    @GetMapping("/chunking-demo")
    public R<Map<String, Object>> chunkingDemo() {
        String text;
        try {
            text = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("../data/knowledge/工业设备维修手册.md")));
        } catch (Exception e) {
            text = "## 测试\n这是测试文本。\n\n这是第二段，用于验证切片效果。\n## 第二部分\n更多内容。";
        }
        return R.ok(chunkingEngine.compare(text));
    }

    /** RAG 知识库主题列表 */
    @GetMapping("/knowledge-topics")
    public R<List<String>> knowledgeTopics() {
        return R.ok(ragService.topics());
    }

    /** 对工单进行 AI 分析 */
    @PostMapping("/analyze/{orderId}")
    public R<String> analyze(@PathVariable("orderId") String orderId) {
        workOrderAiService.analyzeOrder(orderId);
        return R.ok("分析完成");
    }
}
