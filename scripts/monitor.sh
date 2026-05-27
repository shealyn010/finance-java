#!/bin/bash
# 实时监控脚本 - 压测时开着看
HOST=${1:-localhost}
INTERVAL=${2:-2}

echo "=== FinInsight 实时监控 (${INTERVAL}s刷新) ==="
echo ""

while true; do
  clear
  echo "=== $(date '+%H:%M:%S') | FinInsight 实时监控 ==="
  echo ""

  # 1. 限流器状态
  echo "--- 限流器 ---"
  curl -s "http://${HOST}:8082/api/bench/limiter-status" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data','?'))" 2>/dev/null || echo "(无法获取)"

  echo ""
  echo "--- 数据库连接 ---"
  export MYSQL_HOME="$HOME/opt/mysql8"
  $MYSQL_HOME/bin/mysql -u root -p123456 fininsight -e "SHOW STATUS LIKE 'Threads_connected'; SHOW STATUS LIKE 'Threads_running'; SHOW STATUS LIKE 'Questions'; SHOW STATUS LIKE 'Slow_queries';" 2>/dev/null | grep -v "Variable_name"

  echo ""
  echo "--- 工单总量 ---"
  $MYSQL_HOME/bin/mysql -u root -p123456 fininsight -N -e "SELECT COUNT(*) FROM work_order;" 2>/dev/null

  echo ""
  echo "--- 最近错误 ---"
  curl -s "http://${HOST}:8082/api/logs/errors?lines=3" 2>/dev/null | python3 -c "
import sys,json
for l in json.load(sys.stdin).get('data',[]):
    print(l[:120])
" 2>/dev/null || echo "(无法获取)"

  echo ""
  echo "--- 系统资源 ---"
  free -h | grep Mem
  echo "CPU: $(top -bn1 | grep 'Cpu' | awk '{print $2}')%"

  sleep $INTERVAL
done
