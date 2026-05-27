#!/bin/bash
# FinInsight API 接口测试 + 边界测试
BASE="http://localhost:8082/api"
PASS=0
FAIL=0

check() {
    local desc="$1" expected="$2" actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo "  ✅ $desc"
        PASS=$((PASS+1))
    else
        echo "  ❌ $desc (expected: $expected)"
        echo "     got: $(echo $actual | head -1)"
        FAIL=$((FAIL+1))
    fi
}

echo "========== 正常流程 =========="

# 创建工单
r=$(curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d '{
  "userId":1,"customer":"测试客户","serviceType":"维修","serviceDesc":"空调维修",
  "laborHours":3,"laborCost":300,"materialCost":500,"totalRevenue":1500,
  "location":"中山市东区","technician":"测试工","orderTime":"2026-05-27T10:00:00"
}')
check "创建工单" '"code":200' "$r"
orderId=$(echo "$r" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['orderId'])" 2>/dev/null)
echo "     orderId=$orderId"

# 查询列表
r=$(curl -s "$BASE/work-order?userId=1&page=1&size=5")
check "查询工单列表" '"code":200' "$r"

# 按类型统计
r=$(curl -s "$BASE/work-order/stats-by-type?userId=1")
check "按类型统计" '"code":200' "$r"

# 按地点统计
r=$(curl -s "$BASE/work-order/profit-by-location?userId=1&start=2026-01-01&end=2026-12-31")
check "按地点统计利润" '"code":200' "$r"

echo ""
echo "========== 边界测试 =========="

# 负数收入
r=$(curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d '
  {"userId":1,"customer":"测试","serviceType":"维修","totalRevenue":-100}
')
check "拒绝负数收入" "400" "$r"

# 空客户名
r=$(curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d '
  {"userId":1,"customer":"","serviceType":"维修","totalRevenue":100}
')
check "拒绝空客户名" "400" "$r"

# 负数材料费
r=$(curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d '
  {"userId":1,"customer":"测试","serviceType":"维修","totalRevenue":100,"materialCost":-50}
')
check "拒绝负数材料费" "400" "$r"

# 缺失必填字段
r=$(curl -s -X POST "$BASE/work-order" -H "Content-Type: application/json" -d '
  {"userId":1,"serviceType":"维修"}
')
check "拒绝缺失客户名" "400" "$r"

echo ""
echo "========== 结果: $PASS 通过 / $((PASS+FAIL)) 总计 =========="
