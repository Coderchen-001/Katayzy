# update_weights.ps1 - Weight update entry for Katayzy (PLACEHOLDER)
#
# Status: 官网权重更新正在布置中，本脚本当前只做本地权重检查与说明。
#
# 未来实现（官网就绪后启用）：
#   1. 从官网权重目录抓取最新模型列表（借鉴 KataGoAutoSetupHelper 的
#      parseOfficialWeights：https://katagotraining.org/networks/ 的 HTML 表格，
#      解析 model name / Elo / release date / sha256）。
#   2. 与本地 engines\katago-trt\*.bin.gz 比对，提示哪些可更新。
#   3. 下载新权重（断点续传 + SHA-256 校验），校验通过后替换本地文件。
#   4. 若对应 .cfg 缺失，自动调用 build_engines.ps1 -Only <model> 重建。
#   5. 全程显示下载进度，失败可重试。

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $rootDir) { $rootDir = "." }
$engineDir = Join-Path $rootDir "engines\katago-trt"

# 官网权重目录（预留，官网布置中）
# $NETWORKS_URL = "https://katagotraining.org/networks/"

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Katayzy 权重更新" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $engineDir)) {
    Write-Host "未找到引擎目录 engines\katago-trt，请确认整合包完整。" -ForegroundColor Red
    exit 1
}

Write-Host "本地已安装的权重模型：" -ForegroundColor Yellow
Get-ChildItem (Join-Path $engineDir "*.bin.gz") -ErrorAction SilentlyContinue | ForEach-Object {
    $sizeMb = [math]::Round($_.Length / 1MB, 1)
    Write-Host ("  - {0}  ({1} MB)" -f $_.Name, $sizeMb)
}

Write-Host ""
Write-Host "官网权重更新功能正在布置中，敬请期待。" -ForegroundColor Green
Write-Host "（届时将从此处检查并更新到最新 KataGo 官方权重）" -ForegroundColor DarkGray
Write-Host ""
exit 0
