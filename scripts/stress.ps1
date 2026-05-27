# FinInsight 高并发压测 - Windows PowerShell
# 用法: .\stress.ps1 -Count 1000000 -Threads 200
param($Count=500000, $Threads=100)

$Host = "192.168.245.129"
$Url = "http://${Host}:8082/api/work-order"
$PerThread = [math]::Floor($Count / $Threads)
$Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$Success = 0
$Fail = 0
$Lock = [object]::new()

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  FinInsight 全速压测" -ForegroundColor Cyan
Write-Host "  目标: $Count 条 | 线程: $Threads" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$ScriptBlock = {
    param($id, $n, $url)
    $types = @("安装","维修","巡检","保养","定制")
    $locs = @("中山市古镇","中山市小榄","中山市东区","中山市石岐","中山市南头","中山市火炬开发区","中山市三乡","中山市板芙","中山市港口","中山市西区")
    $techs = @("张工","李工","陈工","王工","刘工","赵工","周工","黄工")
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    $s = 0; $f = 0
    for ($i = 0; $i -lt $n; $i++) {
        $rev = Get-Random -Min 500 -Max 20000
        $body = @{
            userId=5; customer="压测$id-$i"
            serviceType=$types[(Get-Random -Min 0 -Max 5)]
            totalRevenue=$rev; laborCost=[math]::Floor($rev*0.2)
            materialCost=[math]::Floor($rev*0.5); otherCost=[math]::Floor($rev*0.05)
            location=$locs[(Get-Random -Min 0 -Max 10)]
            technician=$techs[(Get-Random -Min 0 -Max 8)]
            orderTime="2026-$((Get-Random -Min 1 -Max 6).ToString('00'))-$((Get-Random -Min 1 -Max 28).ToString('00'))T10:00:00"
        } | ConvertTo-Json
        try {
            $r = $client.PostAsync($url, [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, "application/json")).Result
            if ($r.IsSuccessStatusCode) { $s++ } else { $f++ }
        } catch { $f++ }
    }
    Write-Host "[$id] 完成: 成功=$s 失败=$f"
    return @{s=$s; f=$f}
}

$Tasks = @()
for ($i = 0; $i -lt $Threads; $i++) {
    $Tasks += [System.Threading.Tasks.Task]::Run({ & $ScriptBlock -id $args[0] -n $args[1] -url $args[2] }, @($i, $PerThread, $Url))
}

Write-Host "等待所有线程完成..."
[Threading.Tasks.Task]::WaitAll($Tasks)

foreach ($t in $Tasks) {
    $r = $t.Result
    $Success += $r.s
    $Fail += $r.f
}

$Stopwatch.Stop()
$Elapsed = [math]::Round($Stopwatch.Elapsed.TotalSeconds, 1)

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  耗时: ${Elapsed}s | 成功: $Success | 失败: $Fail" -ForegroundColor Green
Write-Host "  吞吐: $([math]::Round($Success/$Elapsed)) 条/秒" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
