# FinInsight 架构设计

## 项目定位

从 Go 版 finance-microservice 重构为 Java Spring Cloud 版本，新增企业工单模块 + AI Agent 对话查询。

## 微服务拆分

```
Vue3 Web → Gateway:8080
              ├── user-service:8081       注册/登录/Token
              ├── transaction-service:8082 工单CRUD/AI分析/Agent对话
              └── (Python) cleaner:8090    数据清洗服务(保留)
              └── (Python) ai-classifier:8091 AI分类服务(保留)
```

## 工单表设计

work_order 表新增字段：
- service_type: 安装/维修/巡检/保养/定制（覆盖中山灯饰/五金/家电行业）
- location: 服务地点（支持按镇区分析：古镇/小榄/火炬...）
- ai_category: AI分析标签（高利润/常规/亏损/重点客户）
- profit_rate: 利润率自动计算

## AI Agent 能力

### 1. 工单AI分析 (WorkOrderAiService)
- 调 DeepSeek 分析工单 → 输出利润分级 + 异常检测 + 经营建议
- 降级策略：API不可用时规则兜底

### 2. Agent对话查询 (AgentChatService)
- 自然语言查询工单数据："古镇上个月维修利润多少？"
- 支持同步 + SSE流式输出
- 自动注入用户数据上下文

## 与 Go 版对比

| | Go 版 | Java 版 |
|---|------|------|
| 工单模块 | ❌ | ✅ 新增 |
| Agent对话 | ❌ | ✅ 新增 |
| 框架 | Go-Zero | Spring Boot 3.2 |
| 服务发现 | Etcd | 直连(后续上Nacos) |
| Python服务 | 保留 | 保留 |
