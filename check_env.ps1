# check_env.ps1 - Environment check for Katayzy (CUDA 12.8 + TensorRT 10.9)
# Checks: NVIDIA GPU present, RTX series, compute capability >= 7.5 (RTX 20+), driver >= 570.65
# Exit code: 0 = pass, 1 = fail

$ErrorActionPreference = "Continue"

# ---------- locate nvidia-smi ----------
$nvidiaSmi = $null
$cmd = Get-Command nvidia-smi -ErrorAction SilentlyContinue
if ($cmd) {
    $nvidiaSmi = $cmd.Source
} else {
    $sys32 = Join-Path $env:SystemRoot "System32\nvidia-smi.exe"
    if (Test-Path $sys32) { $nvidiaSmi = $sys32 }
}

if (-not $nvidiaSmi) {
    Write-Host ""
    Write-Host "ERROR: 未找到 nvidia-smi。未安装 NVIDIA 驱动或不在 PATH 中。" -ForegroundColor Red
    Write-Host "本整合包需要 NVIDIA RTX 20 系及以上显卡，驱动版本 >= 570.65。" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# ---------- query GPU info ----------
Write-Host "正在通过 nvidia-smi 检测显卡..." -ForegroundColor Cyan
$output = & $nvidiaSmi --query-gpu=name,compute_cap,driver_version --format=csv,noheader,nounits 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: nvidia-smi 执行失败:" -ForegroundColor Red
    $output | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
    exit 1
}

# ---------- parse and validate ----------
$foundNvidia = $false
$gpuCount = 0

foreach ($line in $output) {
    if (-not $line) { continue }
    $parts = $line -split ','
    if ($parts.Count -lt 3) { continue }
    $name = $parts[0].Trim()
    $cc = $parts[1].Trim()
    $driver = $parts[2].Trim()
    $gpuCount++

    Write-Host ""
    Write-Host ("GPU {0}: {1}" -f $gpuCount, $name) -ForegroundColor White
    Write-Host ("  计算能力 (Compute Capability): {0}" -f $cc)
    Write-Host ("  驱动版本 (Driver Version)     : {0}" -f $driver)

    # compute capability check: need >= 7.5
    $ccOk = $false
    try {
        $ccParts = $cc -split '\.'
        $ccRank = [int]$ccParts[0] * 10 + [int]$ccParts[1]
        if ($ccRank -ge 75) { $ccOk = $true }
    } catch { $ccOk = $false }

    # RTX series check (RTX 20 series or newer per user requirement)
    $isRtx = $name -match 'RTX'

    if (-not $isRtx) {
        Write-Host "  [FAIL] 不是 RTX 系列显卡。需要 RTX 20 系及以上。" -ForegroundColor Red
        continue
    }
    if (-not $ccOk) {
        Write-Host ("  [FAIL] 计算能力 {0} 低于 7.5（RTX 20 系最低要求）。" -f $cc) -ForegroundColor Red
        continue
    }

    # driver version check: need >= 570.65
    $driverOk = $false
    try {
        $dParts = $driver -split '\.'
        $dMajor = [int]$dParts[0]
        $dMinor = if ($dParts.Count -gt 1) { [int]$dParts[1] } else { 0 }
        if ($dMajor -gt 570 -or ($dMajor -eq 570 -and $dMinor -ge 65)) { $driverOk = $true }
    } catch { $driverOk = $false }

    if (-not $driverOk) {
        Write-Host ("  [FAIL] 驱动 {0} 低于 570.65（CUDA 12.8 最低要求）。请到 NVIDIA 官网更新驱动。" -f $driver) -ForegroundColor Red
        continue
    }

    Write-Host "  [PASS]" -ForegroundColor Green
    $foundNvidia = $true
}

Write-Host ""
if (-not $foundNvidia) {
    Write-Host "FAILED: 未找到满足要求的 NVIDIA RTX 显卡（>= RTX 20 系，驱动 >= 570.65）。" -ForegroundColor Red
    Write-Host "请到 NVIDIA 官网更新驱动: https://www.nvidia.com/Download/index.aspx" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host "PASS: 环境满足 Katayzy CUDA 12.8 + TensorRT 10.9 要求。" -ForegroundColor Green
Write-Host ""
exit 0
