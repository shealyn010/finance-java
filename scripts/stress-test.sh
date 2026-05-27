#!/bin/bash
# 高并发压测脚本 - 丢到Windows上跑
# 用法: bash stress-test.sh <总条数> <线程数> <间隔ms>
# 示例: bash stress-test.sh 1000000 200 5000

COUNT=${1:-1000000}
THREADS=${2:-200}
DELAY_MS=${3:-5000}
HOST=${4:-192.168.245.129}
BATCH=$((COUNT / THREADS))

echo "========================================="
echo "  FinInsight 高并发压测"
echo "  目标: $COUNT 条 | 线程: $THREADS | 间隔: ${DELAY_MS}ms"
echo "  每线程: $BATCH 条"
echo "========================================="

START=$(date +%s%N)

for t in $(seq 1 $THREADS); do
  (
    for i in $(seq 1 $BATCH); do
      R=$((RANDOM % 10))
      CUSTOMER="压测客户$t-$i"
      LOCATION="中山市$(echo 古镇 小榄 石岐 东区 南头 火炬 三乡 板芙 港口 | cut -d' ' -f$((R+1)))"
      TYPE=$(echo 安装 维修 巡检 保养 定制 | cut -d' ' -f$((RANDOM % 5 + 1)))
      TECH=$(echo 张工 李工 陈工 王工 刘工 | cut -d' ' -f$((RANDOM % 5 + 1)))
      REV=$((500 + RANDOM % 20000))
      LABOR=$((REV * 20 / 100))
      MAT=$((REV * 50 / 100))
      OTHER=$((REV * 5 / 100))

      curl -s -X POST "http://${HOST}:8082/api/work-order" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":5,\"customer\":\"$CUSTOMER\",\"serviceType\":\"$TYPE\",\"totalRevenue\":$REV,\"laborCost\":$LABOR,\"materialCost\":$MAT,\"otherCost\":$OTHER,\"location\":\"$LOCATION\",\"technician\":\"$TECH\",\"orderTime\":\"2026-$(printf '%02d' $((1+RANDOM%5)))-$(printf '%02d' $((1+RANDOM%28)))T$(printf '%02d' $((8+RANDOM%12))):00:00\"}" \
        > /dev/null 2>&1
    done
    echo "[$t/$THREADS] 线程完成: $BATCH 条"
  ) &
  # 间隔启动，模拟真实流量
  sleep $(echo "scale=3; $DELAY_MS/1000/$THREADS" | bc)
done

wait
END=$(date +%s%N)
ELAPSED=$(( ($END - $START) / 1000000 ))

echo ""
echo "========================================="
echo "  压测完成: ${ELAPSED}ms"
echo "  预期写入: $COUNT 条"
echo "========================================="
