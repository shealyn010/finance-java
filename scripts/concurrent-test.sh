#!/bin/bash
# 高并发API压测：模拟多用户同时创建工单
# 每个请求走完整流程：校验→写库→异步AI→MQ

BASE="http://localhost:8082/api"
CONCURRENT=20        # 并发数
REQUESTS_PER=200      # 每线程请求数
TOTAL=$((CONCURRENT * REQUESTS_PER))

echo "=== 高并发API压测 ==="
echo "并发线程: $CONCURRENT"
echo "每线程请求: $REQUESTS_PER"
echo "总请求: $TOTAL"
echo ""

# 准备 JSON 模板
BODY='{"userId":1,"customer":"压测客户","serviceType":"维修","totalRevenue":2000,"laborCost":300,"materialCost":500,"otherCost":100,"location":"中山市东区","technician":"压测工","orderTime":"2026-05-27T10:00:00"}'

START=$(date +%s%N)

# 多进程并发
for i in $(seq 1 $CONCURRENT); do
  (
    for j in $(seq 1 $REQUESTS_PER); do
      curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d "$BODY" > /dev/null
    done
  ) &
done

# 等待所有后台进程
wait

END=$(date +%s%N)
ELAPSED=$(( ($END - $START) / 1000000 ))

# 统计结果
SUCCESS=$(export MYSQL_HOME="$HOME/opt/mysql8" && $MYSQL_HOME/bin/mysql -u root -p123456 fininsight -N -e "SELECT COUNT(*) FROM work_order WHERE technician='压测工'" 2>/dev/null)

echo "=== 结果 ==="
echo "总耗时: ${ELAPSED}ms"
echo "成功写入: $SUCCESS 条 (本次压测新增)"
echo "吞吐量: $(( SUCCESS * 1000 / ELAPSED )) 条/秒 (走API全流程)"
echo ""
echo "=== 限流器状态 ==="
curl -s "$BASE/bench/limiter-status"
echo ""
echo "=== AI分析(前5条) ==="
export MYSQL_HOME="$HOME/opt/mysql8"
$MYSQL_HOME/bin/mysql -u root -p123456 fininsight -N -e "SELECT ai_category, COUNT(*) FROM work_order WHERE technician='压测工' GROUP BY ai_category" 2>/dev/null
