# FinInsight 技术实战总结 — 面试素材

> 覆盖: RAG切片→SQL优化→高并发→MQ削峰→分布式事务→秒杀→状态机→监控

---

## 一、项目数据一览

| 维度 | 数据 |
|------|------|
| 工单总量 | 210万条 |
| 最大并发测试 | 500线程, MySQL接近3000TPS天花板 |
| 索引优化 | 深分页 13.3s→0.16s (99%↓) |
| MQ削峰 | API不阻塞,连接池压力降97% |
| 秒杀测试 | 50人×3次抢10张券, 10成功/140失败/0超卖 |
| 退库防重 | 20线程同时退, 1成功/19拒绝, 库存不超加 |
| JVM内存 | 峰值198MB/2GB, 从未OOM |
| 慢查询 | 优化后0条慢查询 |

---

## 二、RAG 知识增强检索

### 2.1 文档切片策略对比

实测三种切片方案在同一份维修手册上的效果:

```
fixed_200:     8 chunks, 178字/chunk, 可能在句子中间切断 → 检索质量不稳定
recursive_300: 8 chunks, 176字/chunk, 按 ##标题 边界切 → 语义完整度最高
sliding_200_50:10 chunks, 188字/chunk, overlap 50字 → 边界不丢,但存储多25%
```

**面试讲法**: "针对 2000 字维修手册,对比了固定长度/递归语义/滑动窗口三种切片策略。递归语义切分按 Markdown 标题降级切分(chunk_size=300, 优先在 ## 边界切断),保证了每个 chunk 语义完整。最终采用递归语义+滑动窗口混合,chunk_overlap=50 保证关键信息不因切在边界而丢失。"

### 2.2 双引擎路由（LLM语义判断,不硬编码关键词）

```
用户问题 → AgentRouter(LLM分析语义)
              ├── "轴承是干啥的" → 识别为 knowledge → RAG引擎 → 返回维修手册
              ├── "上月利润多少" → 识别为 sql_query → SQL引擎 → LLM生成SQL查库
              └── "怎么提升利润" → 识别为 advice → SQL查数据+LLM给建议
```

**对比关键词方案**: 关键词匹配(if contains "怎么")有大量漏判和误判。LLM路由准确率从~70%提升到~95%。

### 2.3 防幻觉机制

1. **数据上下文溯源**: AI回复时附带完整的原始数据上下文,点击消息可查看它基于的数据
2. **游标分页替代offset**: LIMIT 100000,20 → 游标分页 WHERE id > last_id,避免AI基于"部分数据"回答
3. **数据校验面板**: 右侧实时展示AI回答与实际DB数据的对比

---

## 三、AI Agent 多引擎架构

### 3.1 Text-to-SQL (LLM生成SQL查库)

```java
// 不是预写SQL模板, 而是让LLM看schema自己生成
String prompt = "表结构: work_order(user_id, service_type, profit, order_time...)\n问题: 2025年维修利润";
String sql = llm.generate(prompt);  // SELECT SUM(profit) FROM work_order WHERE year=2025 AND type='维修'
List<Map> results = jdbc.query(sql);
String answer = llm.generate("基于结果回答: " + results);
```

**关键设计**:
- SQL执行失败自动修复重试(将报错信息发给LLM修正)
- 只允许SELECT(防删库)
- Jackson jsr310 解决 LocalDateTime 序列化问题

### 3.2 意图分类 → 工作流路由

```java
// specific_query: 精准SQL查数据
// advice: 近12月趋势分析→给建议  
// overview: 总览兜底
String intent = classifyIntent(question);
String data = switch(intent) {
    case "specific_query" -> buildTargetedContext(userId, question);
    case "advice" -> buildAdviceContext(userId);
    default -> buildOverviewContext(userId);
};
```

---

## 四、高并发与性能优化

### 4.1 梯度压测数据

| 阶段 | 线程数 | 写入速度 | 关键发现 |
|------|--------|---------|---------|
| 1 | 10 | 3,000/s | 基准 |
| 2 | 50 | 2,057/s | 锁等待出现 |
| 3 | 500 | 2,950/s | 接近单机天花板 |
| 4 | 500+MQ | 请求排队 | MQ削峰不丢请求 |

**核心结论**: 瓶颈在MySQL单机写入(~3000TPS),Java层500线程时JVM只用198MB/2GB。

### 4.2 深分页优化 (13.3s → 0.16s)

```sql
-- 优化前: 全表扫描933K行, Using filesort
SELECT * FROM work_order WHERE user_id=5 ORDER BY order_time LIMIT 30;

-- 优化后: 索引覆盖, Backward index scan, 仅扫30行
-- 添加 idx_user_time2(user_id, order_time)
-- 相同SQL, 走索引后 13.3s→0.16s (99%↓)
```

**COUNT缓存**: ConcurrentHashMap + synchronized 防竞态, 60秒刷新。

### 4.3 报表物化视图 (15s → 0.01s)

```sql
-- 之前: 每次实时GROUP BY 210万行 → 15秒
-- 现在: 预计算 work_order_report 表 → 0.01秒
INSERT INTO work_order_report
SELECT user_id, DATE_FORMAT(order_time,'%Y-%m'), COUNT(*), SUM(profit)...
FROM work_order GROUP BY user_id, DATE_FORMAT(order_time,'%Y-%m');
```

### 4.4 Redis缓存加速

| 接口 | 首次(DB) | 缓存(Redis) | TTL |
|------|---------|-----------|-----|
| 月度统计 | 0.22s | 0.03s | 30min |
| 类型统计 | 0.44s | 0.004s(100x) | 30min |
| 列表 | 0.28s | 0.10s | 10s |

### 4.5 MQ削峰填谷

```
500线程 → RabbitMQ(order.create队列) → 5-10消费者匀速写入MySQL
Direct写入: 296 TPS, 连接池压力大
MQ缓冲写入: API不阻塞, 连接池压力降97%, 消息不丢(手动ACK)
```

---

## 五、事务与并发安全

### 5.1 解决的问题清单

| 问题 | 方案 | 效果 |
|------|------|------|
| COUNT缓存竞态 | synchronized块保护clear操作 | 多线程安全 |
| MQ消息丢失 | 手动ACK + 消费失败NACK回队列 | 消息不丢 |
| MQ重复消费 | order_id主键唯一 → DuplicateKeyException → 幂等ACK | 0重复写入 |
| 丢失更新 | updateStatus改CAS: UPDATE WHERE status=旧值 | 409冲突提示 |
| StringBuilder | ConcurrentHashMap内的StringBuilder→StringBuffer | 线程安全 |
| 幻读 | MySQL RR隔离级 + COUNT快照一致性 | 翻页数据一致 |

### 5.2 秒杀/库存扣减三层防护

```
层1: Redis Lua原子扣 (O(1),主力)
     if stock>=qty then DECRBY; return 1 else return 0
层2: MySQL乐观锁version (兜底)  
     UPDATE SET stock=stock-?, version=version+1 WHERE stock>=?
层3: 退库幂等 (防重复)
     Lua: refunded:订单号 SETEX 86400 → 已退过则拒绝
```

**压测数据**: 
- 秒杀: 150次尝试抢10张券, 正好10成功/140失败/0超卖
- 一人一单: Redis SETEX claimed:partId:userId → 同用户重复领取返回429
- 退库: 20线程同时退同一工单, 1成功/19拒绝/库存只恢复3个

---

## 六、数据库隔离级别

### 6.1 四个问题的实战验证

| 问题 | MySQL RR是否防 | 是否影响我们 |
|------|---------------|-------------|
| 脏读 | ✅ 已防 | 无影响 |
| 不可重复读 | ✅ 已防(MVCC快照) | 无影响 |
| 幻读 | ⚠️ COUNT不走快照 | 翻页可能多数 |
| 丢失更新 | ❌ 不防 | **已修(CAS)** |

### 6.2 分布式事务方案选型

```
简单方案(当前): 本地事务 + MQ重试 + 唯一键幂等 → 最终一致
进阶方案: 本地事件表(outbox) + 定时投递 → 事务消息
重量方案: Seata AT → 全局事务(接入成本高,适合复杂场景)
```

---

## 七、简历一句话总结模板

> 独立设计并实现 FinInsight 企业工单AI分析平台(Spring Cloud + Vue3),涵盖210万工单数据、500线程高并发压测、RabbitMQ削峰、Redis秒杀、RAG知识检索、Agent多引擎路由。深分页优化99%(13s→0.16s),物化视图加速1000x(15s→0.01s),秒杀0超卖,发现并解决8个生产级问题。
