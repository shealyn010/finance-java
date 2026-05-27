package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import com.fininsight.transaction.service.AgentChatService;
import com.fininsight.transaction.service.ChunkingEngine;
import com.fininsight.transaction.service.RagService;
import com.fininsight.transaction.service.SqlAgentService;
import com.fininsight.transaction.service.WorkOrderAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentChatService agentChatService;
    private final WorkOrderAiService workOrderAiService;
    private final SqlAgentService sqlAgentService;
    private final RagService ragService;
    private final ChunkingEngine chunkingEngine;

    public AgentController(AgentChatService agentChatService,
                           WorkOrderAiService workOrderAiService,
                           SqlAgentService sqlAgentService,
                           RagService ragService,
                           ChunkingEngine chunkingEngine) {
        this.agentChatService = agentChatService;
        this.workOrderAiService = workOrderAiService;
        this.sqlAgentService = sqlAgentService;
        this.ragService = ragService;
        this.chunkingEngine = chunkingEngine;
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

    /** 双引擎智能查询: RAG(知识) + SQL(数据) */
    @PostMapping("/query")
    public R<Map<String, Object>> smartQuery(@RequestParam(name="userId") Long userId,
                                              @RequestParam(name="message") String message) {
        // 关键词判断走RAG还是SQL
        boolean isKnowledge = message.contains("怎么") || message.contains("如何") ||
            message.contains("排查") || message.contains("维修") && !message.contains("利润") ||
            message.contains("SLA") || message.contains("故障");
        if (isKnowledge) {
            String doc = ragService.search(message);
            if (doc != null) {
                Map<String, Object> r = new HashMap<>();
                r.put("reply", "根据维修知识库：\n\n" + doc);
                r.put("role", "assistant");
                r.put("dataContext", "来源: 本地维修知识库(RAG)");
                r.put("engine", "rag");
                return R.ok(r);
            }
        }
        // 默认走SQL
        Map<String, Object> result = sqlAgentService.query(userId, message);
        result.put("engine", "sql");
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
