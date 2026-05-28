# FinInsight 项目指南

## 启动
```bash
~/start-services.sh    # 一键启动全部
~/check-services.sh    # 健康检测
```

## 技术栈
- 后端: Java 21 + Spring Boot 3.2 + MyBatis-Plus
- 前端: Vue3 + Vite (端口5173)
- MySQL: root/123456 (端口3306)
- Redis: localhost:6379, 密码redis123
- RabbitMQ: guest/guest (端口5672)

## 关键目录
- finance-transaction/ — 核心业务(工单/库存/AI)
- finance-user/ — 用户认证
- docs/ — 面试文档+架构设计
- scripts/ — 压测/监控脚本

## AI Agent 架构
- AgentRouter: LLM语义路由→RAG/SQL
- SqlAgentService: Text-to-SQL自动生成
- RagService: 本地维修知识库检索
- InventoryService: Redis Lua秒杀+退库幂等
