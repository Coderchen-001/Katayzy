# build_engines.ps1 - Build KataGo engine configs for Katayzy (CUDA 12.8 + TensorRT 10.9)
#
# Behaviour:
#   1. Runs the environment gate (check_env.ps1).
#   2. b10c384 (companion analysis process): copies KataGo analysis_example.cfg -> analysis.cfg
#      and applies quick-analysis tuning (maxVisits=100, no maxTime cap, trtDeviceToUse=0).
#      No genconfig, no TensorRT engine build (cache builds on first analysis request).
#   3. b10c512 (default engine) / b11c768: run `katago genconfig` (TensorRT build ~3-8 min each).
#   4. b10c512 / b11c768 are added to config.txt (b10c512 as default); b10c384 stays hidden.
#   5. Writes command fields + disables GUI kata thread overrides:
#        ui.analysis-engine-command            = b10c384 analysis command (uses analysis.cfg)
#        ui.analysis-engine-command-customized = true
#        ui.estimate-command                   = b10c512 gtp command (Kata评估)
#        ui.first-load-katago / chk- / autoload-kata-engine-threads -> off (threads from cfg only)
#
# Parameters:
#   -Only <model>   build only one model, e.g. -Only b10c384 (skip the environment gate? no, gate still runs)

param(
  [string]$Only = ""
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $rootDir) { $rootDir = "." }
Set-Location $rootDir

$engineDir = Join-Path $rootDir "engines\katago-trt"
$katago    = Join-Path $engineDir "katago.exe"
$configTxt = Join-Path $rootDir "user-data\config.txt"
$relPrefix = "engines\katago-trt"

# ---------------------------------------------------------------- models
$models = @(
  @{ Id="b10c384"; Weight="b10c384h6nbttflrs.bin.gz";                Config="analysis.cfg";  AddEngine=$false; Default=$false; Note="伴生进程基础模型（打开棋谱自动分析，用 analysis.cfg 特调）" },
  @{ Id="b10c512"; Weight="b10c512h8nbt3tflrs-fson-silu-rsnh.bin.gz"; Config="b10c512.cfg"; AddEngine=$true;  Default=$true;  Note="中模型（默认引擎 / 棋力评估）" },
  @{ Id="b11c768"; Weight="b11c768h12nbt3tflrs-fson-silu.bin.gz";     Config="b11c768.cfg"; AddEngine=$true;  Default=$false; Note="大模型（棋力最强）" }
)

if ($Only -ne "") {
  $filtered = $models | Where-Object { $_.Id -eq $Only }
  if (-not $filtered) {
    Write-Host "错误：未知模型 '$Only'（可选: b10c384 / b10c512 / b11c768）" -ForegroundColor Red
    exit 1
  }
  $models = @($filtered)
}

# ---------------------------------------------------------------- gate
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Katayzy 引擎配置构建 (CUDA 12.8 + TensorRT 10.9)" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "[0/2] 正在检查显卡环境..." -ForegroundColor Yellow
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $rootDir "check_env.ps1")
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "环境检查未通过，请先处理显卡/驱动问题后再试。" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $katago)) {
    Write-Host "错误：未找到 $katago" -ForegroundColor Red
    Write-Host "请确认引擎文件位于 engines\katago-trt\ 目录。" -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------- prepare configs
Write-Host ""
Write-Host "[1/2] 准备引擎配置..." -ForegroundColor Yellow
Write-Host "      b10c384 使用 analysis 模板特调（不 genconfig，安装更快）；" -ForegroundColor DarkGray
Write-Host "      b10c512/b11c768 运行 genconfig（首次需构建 TensorRT 引擎，约 3~8 分钟/个，输出实时滚动）。" -ForegroundColor DarkGray
Write-Host "      请勿关闭窗口。" -ForegroundColor DarkGray

$answers = Join-Path $rootDir "_answers.tmp"
"chinese" | Out-File $answers -Encoding ASCII
for ($i = 0; $i -lt 9; $i++) { Add-Content $answers "" }

$failed = @()
foreach ($m in $models) {
  $weight = Join-Path $engineDir $m.Weight
  $cfg    = Join-Path $engineDir $m.Config

  Write-Host ""
  Write-Host "---------------------------------------------------" -ForegroundColor Cyan
  Write-Host ("  构建 {0}  ({1})" -f $m.Id, $m.Note) -ForegroundColor White

  if ($m.Id -eq "b10c384") {
    # b10c384 伴生进程：不 genconfig，复制 KataGo analysis 模板并特调
    $template = Join-Path $engineDir "analysis_example.cfg"
    if (-not (Test-Path $template)) {
      Write-Host "  错误：缺少 analysis 模板 $template" -ForegroundColor Red
      $failed += $m.Id
      continue
    }
    if (-not (Test-Path $weight)) {
      Write-Host "  错误：缺少权重文件 $($m.Weight)" -ForegroundColor Red
      $failed += $m.Id
      continue
    }
    # 清理废弃的旧 genconfig 产物（b10c384.cfg 已由 analysis.cfg 取代）
    $legacyCfg = Join-Path $engineDir "b10c384.cfg"
    if (Test-Path $legacyCfg) {
      Remove-Item $legacyCfg -Force
      Write-Host "  已删除废弃的 b10c384.cfg（由 analysis.cfg 取代）" -ForegroundColor DarkGray
    }
    if (Test-Path $cfg) {
      Write-Host "  已存在 $($m.Config)，重新应用特调（收敛到设计值）。" -ForegroundColor DarkGray
    } else {
      Copy-Item $template $cfg
      Write-Host "  已从模板生成 $($m.Config)（不运行 genconfig）。" -ForegroundColor DarkGray
    }
    # 特调：按 key 无条件重置（收敛到设计值，无论旧值/注释状态/模板版本）
    #   maxVisits -> 100（保险上限）；maxTime -> 保持注释（无时间上限，
    #   否则会截断整盘精析的 deep 500-visits 请求）；trtDeviceToUse -> 0
    $content = [System.IO.File]::ReadAllText($cfg, [System.Text.Encoding]::UTF8)
    # 正则尾部加 \r? 兼容 CRLF 行尾（模板为 CRLF，否则 $ 无法匹配 \r 前位置导致替换失效）
    $content = $content -replace '(?m)^\s*#?\s*maxVisits\s*=\s*[^\r\n]*\r?$', 'maxVisits = 100'
    # 将任何有效 maxTime 行恢复为注释（去掉时间上限，保留模板注释行不变）
    $content = $content -replace '(?m)^\s*maxTime\s*=\s*[^\r\n]*\r?$', '# maxTime = 60'
    $content = $content -replace '(?m)^\s*#?\s*trtDeviceToUse\s*=\s*[^\r\n]*\r?$', 'trtDeviceToUse = 0'
    [System.IO.File]::WriteAllText($cfg, $content, (New-Object System.Text.UTF8Encoding $false))
    Write-Host "  [OK] $($m.Config) 特调完成（maxVisits=100, 无 maxTime 上限, trtDeviceToUse=0）" -ForegroundColor Green
    continue
  }

  $cfgExists = Test-Path $cfg
  if ($cfgExists) {
    Write-Host "  已存在 $($m.Config)，跳过（如需重建请先删除该文件）。" -ForegroundColor DarkGray
  } else {
    if (-not (Test-Path $weight)) {
      Write-Host "  错误：缺少权重文件 $($m.Weight)" -ForegroundColor Red
      $failed += $m.Id
      continue
    }
    Write-Host "  运行: katago.exe genconfig -model $($m.Weight) -output $($m.Config)" -ForegroundColor DarkGray
    Write-Host "  （首次构建 TensorRT 引擎，约 3~8 分钟，请耐心等待）" -ForegroundColor DarkGray
    # cwd = $rootDir（上面 Set-Location），用相对路径与参考成品安装脚本一致
    cmd /c "`"$relPrefix\katago.exe`" genconfig -model `"$relPrefix\$($m.Weight)`" -output `"$relPrefix\$($m.Config)`" < `"$answers`""
    if ($LASTEXITCODE -ne 0) {
      Write-Host ("  [FAIL] genconfig 失败（错误码 {0}）。常见原因：TensorRT 引擎构建失败、显存不足或驱动过旧。" -f $LASTEXITCODE) -ForegroundColor Red
      $failed += $m.Id
      continue
    }
    Write-Host ("  [OK] 已生成 {0}" -f $m.Config) -ForegroundColor Green
  }
}

if (Test-Path $answers) { Remove-Item $answers -Force }

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host ("构建失败: {0}" -f ($failed -join ", ")) -ForegroundColor Red
    Write-Host "请检查驱动（>= 570.65）、显存与 GPU 占用后，用  启动器.bat 的「重建引擎配置」重试。" -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------- add engines + command fields
Write-Host ""
Write-Host "[2/2] 正在写入引擎列表与命令字段..." -ForegroundColor Yellow

foreach ($m in $models) {
  if (-not $m.AddEngine) { continue }
  $addArgs = @(
    "-EngineName", ("KataGo-" + $m.Id),
    "-WeightFile", $m.Weight,
    "-ConfigFile", $m.Config
  )
  if ($m.Default) { $addArgs += "-SetDefault" }
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $rootDir "add_engine.ps1") @addArgs
  if ($LASTEXITCODE -ne 0) {
    Write-Host ("添加引擎 {0} 失败" -f $m.Id) -ForegroundColor Red
    exit 1
  }
}

# ---- write analysis-engine-command (b10c384 companion) / estimate-command (b10c512) ----
function Set-JsonField($obj, $name, $value) {
  if ($null -ne $obj.PSObject.Properties[$name]) { $obj.$name = $value }
  else { $obj | Add-Member -NotePropertyName $name -NotePropertyValue $value -Force }
}
if (Test-Path $configTxt) {
  $json = Get-Content $configTxt -Raw -Encoding UTF8 | ConvertFrom-Json
  $b10c384 = $models | Where-Object { $_.Id -eq "b10c384" }
  $b10c512 = $models | Where-Object { $_.Id -eq "b10c512" }

  if ($b10c384) {
    $analysisCmd = '"' + $relPrefix + '\katago.exe" analysis -model "' + $relPrefix + '\' + $b10c384.Weight + '" -config "' + $relPrefix + '\' + $b10c384.Config + '" -quit-without-waiting'
    Set-JsonField $json.ui 'analysis-engine-command' $analysisCmd
    Set-JsonField $json.ui 'analysis-engine-command-customized' $true
    Write-Host ("  analysis-engine-command -> {0}" -f $analysisCmd) -ForegroundColor DarkGray
  }
  if ($b10c512) {
    $estimateCmd = '"' + $relPrefix + '\katago.exe" gtp -model "' + $relPrefix + '\' + $b10c512.Weight + '" -config "' + $relPrefix + '\' + $b10c512.Config + '"'
    Set-JsonField $json.ui 'estimate-command' $estimateCmd
    Write-Host ("  estimate-command        -> {0}" -f $estimateCmd) -ForegroundColor DarkGray
  }

  # 关闭 GUI 的 kata 线程自动干预：线程完全由 genconfig/analysis cfg 决定，
  # 避免多余的 kata-set-param numSearchThreads（与配置文件重复/冲突）
  Set-JsonField $json.ui 'first-load-katago' $false
  Set-JsonField $json.ui 'chk-kata-engine-threads' $false
  Set-JsonField $json.ui 'autoload-kata-engine-threads' $false
  Set-JsonField $json.ui 'txt-kata-engine-threads' ""
  Set-JsonField $json.ui 'enable-startup-benchmark' $false
  Write-Host "  已关闭 kata 线程自动干预（线程由各配置文件决定）" -ForegroundColor DarkGray

  $out = $json | ConvertTo-Json -Depth 50
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  [System.IO.File]::WriteAllText($configTxt, $out, $utf8NoBom)
} else {
  Write-Host "警告：未找到 $configTxt，跳过命令字段写入。请先运行一次 Katayzy 生成配置文件。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host "  引擎配置构建完成！" -ForegroundColor Green
Write-Host "  b10c384  -> 伴生进程专用（analysis.cfg 特调，不在引擎列表显示）" -ForegroundColor Green
Write-Host "  b10c512  -> 默认引擎（引擎列表）" -ForegroundColor Green
Write-Host "  b11c768  -> 引擎列表" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green
Write-Host "  提示：首次打开棋谱自动分析时，b10c384 需构建 TensorRT 缓存" -ForegroundColor Yellow
Write-Host "        （约 1~3 分钟，日志显示 Building TensorRT engine 属正常现象）。" -ForegroundColor Yellow
exit 0
