@echo off
setlocal enabledelayedexpansion
echo Stress Test: 100 x 50 = 5000 requests
set COUNT=0
for /l %%t in (1,1,100) do (
  start /b cmd /c "for /l %%i in (1,1,50) do curl -s -X POST http://192.168.245.129:8082/api/work-order -H "Content-Type: application/json" -d "{\"userId\":5,\"customer\":\"P%%t-%%i\",\"serviceType\":\"wei xiu\",\"totalRevenue\":5000,\"laborCost\":500,\"materialCost\":2000,\"otherCost\":200,\"location\":\"GuZhen\",\"technician\":\"Zhang\",\"orderTime\":\"2026-05-27T10:00:00\"}" > nul 2>&1"
)
echo Done launching 100 threads.
echo Check http://192.168.245.129:8082/api/health/jvm
pause
