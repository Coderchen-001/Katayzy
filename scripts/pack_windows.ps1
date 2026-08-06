# pack_windows.ps1 - One-shot Windows packaging for Katayzy (CUDA 12.8 + TensorRT 10.9)
#
# Pipeline: mvn package (shade jar) -> jpackage app-image -> inject version/heap opts
#           -> assemble versioned bundle under $OutputRoot\Katayzy-<version>
#
# Parameters:
#   -Version      version string, e.g. next-2026-08-02.1 (default: next-<today>.N, N auto-increments)
#   -BaseBundle   source bundle for engines/jcef/readboard/scripts etc. (default: $OutputRoot\Katayzy)
#   -OutputRoot   parent dir for the versioned bundle (default: D:\全新重构项目-卡塔狗桌面版\成品)
#   -SkipCompile  reuse existing target jar without recompiling
#   -AppVersion   jpackage file version (default: 2.6.20901)

param(
  [string]$Version = "",
  [string]$BaseBundle = "",
  [string]$OutputRoot = "D:\全新重构项目-卡塔狗桌面版\成品",
  [switch]$SkipCompile,
  [string]$AppVersion = "2.6.20901"
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path          # scripts\
$repoDir = Split-Path -Parent $rootDir                              # repo root
Set-Location $repoDir

# ---------------------------------------------------------------- version
if ($Version -eq "") {
  $today = Get-Date -Format "yyyy-MM-dd"
  $seq = 1
  while (Test-Path (Join-Path $OutputRoot ("Katayzy-next-" + $today + "." + $seq))) { $seq++ }
  $Version = "next-" + $today + "." + $seq
}
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  Katayzy Windows 打包 (版本 $Version)" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

$jarPath = Join-Path $repoDir "target\lizzie-yzy2.5.3-shaded.jar"
$progDir = Join-Path $repoDir "dist\windows\Katayzy"
$bundleDir = Join-Path $OutputRoot ("Katayzy-" + $Version)

# ---------------------------------------------------------------- 1. compile
if (-not $SkipCompile) {
  Write-Host "[1/6] 编译 jar (mvn package)..." -ForegroundColor Yellow
  cmd /c ".tools\build.cmd -DskipTests package"
  if ($LASTEXITCODE -ne 0) { Write-Host "编译失败" -ForegroundColor Red; exit 1 }
} else {
  Write-Host "[1/6] 跳过编译（-SkipCompile）" -ForegroundColor Yellow
}
if (-not (Test-Path $jarPath)) { Write-Host "未找到 $jarPath" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- 2. brand check
Write-Host "[2/6] 校验 jar 品牌 (Katayzy)..." -ForegroundColor Yellow
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
$entry = $zip.Entries | Where-Object { $_.FullName -eq "featurecat/lizzie/Lizzie.class" }
if (-not $entry) { $zip.Dispose(); Write-Host "jar 缺少 Lizzie.class" -ForegroundColor Red; exit 1 }
$ms = New-Object System.IO.MemoryStream
$s = $entry.Open(); $s.CopyTo($ms); $s.Close()
$bytes = $ms.ToArray(); $ms.Close(); $zip.Dispose()
$text = [System.Text.Encoding]::ASCII.GetString($bytes)
if (-not $text.Contains("Katayzy")) { Write-Host "jar 内无 Katayzy 品牌，疑似旧包，中止" -ForegroundColor Red; exit 1 }
Write-Host "  品牌校验通过" -ForegroundColor Green

# ---------------------------------------------------------------- 3. jpackage
Write-Host "[3/6] jpackage app-image..." -ForegroundColor Yellow
$inputDir = Join-Path $repoDir "dist\input"
if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item $jarPath (Join-Path $inputDir "lizzie-yzy2.5.3-shaded.jar") -Force
if (Test-Path $progDir) { Remove-Item $progDir -Recurse -Force }
& (Join-Path $repoDir ".tools\jdk-17\bin\jpackage.exe") `
    --type app-image --name Katayzy --app-version $AppVersion `
    --input $inputDir --main-jar lizzie-yzy2.5.3-shaded.jar `
    --main-class featurecat.lizzie.Lizzie `
    --icon (Join-Path $repoDir "packaging\icons\app-icon.ico") `
    --dest (Join-Path $repoDir "dist\windows")
if ($LASTEXITCODE -ne 0) { Write-Host "jpackage 失败" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- 4. inject version/heap opts
Write-Host "[4/6] 注入版本与堆参数..." -ForegroundColor Yellow
$cfgPath = Join-Path $progDir "app\Katayzy.cfg"
$cfgText = [System.IO.File]::ReadAllText($cfgPath, [System.Text.Encoding]::UTF8)
$add = "java-options=-Dlizzie.next.version=$Version`r`n" +
       "java-options=-XX:InitialRAMPercentage=1.0`r`n" +
       "java-options=-XX:MaxRAMPercentage=40.0`r`n" +
       "java-options=-Xshare:auto`r`n"
if ($cfgText -notmatch "-Dlizzie.next.version") {
  [System.IO.File]::WriteAllText($cfgPath, $cfgText.TrimEnd() + "`r`n" + $add, (New-Object System.Text.UTF8Encoding $false))
}

# ---------------------------------------------------------------- 5. assemble bundle
Write-Host "[5/6] 组装整合包 $bundleDir ..." -ForegroundColor Yellow
if (Test-Path $bundleDir) { Remove-Item $bundleDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null
if ($BaseBundle -eq "") { $BaseBundle = Join-Path $OutputRoot "Katayzy" }
if (-not (Test-Path $BaseBundle)) { Write-Host "基准包不存在: $BaseBundle" -ForegroundColor Red; exit 1 }

robocopy $progDir $bundleDir /E /NFL /NDL /NJH /NJS /NP | Out-Null
foreach ($d in @("engines","clockHelper","user-data","save","human-sl-models")) {
  robocopy (Join-Path $BaseBundle $d) (Join-Path $bundleDir $d) /E /NFL /NDL /NJH /NJS /NP | Out-Null
}
robocopy (Join-Path $BaseBundle "app\jcef-bundle") (Join-Path $bundleDir "app\jcef-bundle") /E /NFL /NDL /NJH /NJS /NP | Out-Null
robocopy (Join-Path $BaseBundle "app\readboard") (Join-Path $bundleDir "app\readboard") /E /NFL /NDL /NJH /NJS /NP | Out-Null
foreach ($f in @(".lizzie-portable","使用说明.txt","启动器.bat","check_env.ps1","check_first_run.ps1","add_engine.ps1","build_engines.ps1","update_weights.ps1","app\PROJECT_INFO.txt","570.65")) {
  Copy-Item (Join-Path $BaseBundle $f) (Join-Path $bundleDir $f) -Recurse -Force
}

# ---------------------------------------------------------------- 6. pre-provision b10c384 companion (out-of-box)
Write-Host "[6/6] 预置 b10c384 伴生进程（开箱即用）..." -ForegroundColor Yellow
$engDir = Join-Path $bundleDir "engines\katago-trt"
$cfgPath = Join-Path $engDir "analysis.cfg"
$cfgTemplate = Join-Path $engDir "analysis_example.cfg"
$bundleConfigTxt = Join-Path $bundleDir "user-data\config.txt"
$rel = "engines\katago-trt"

if (Test-Path $cfgTemplate) {
  if (-not (Test-Path $cfgPath)) { Copy-Item $cfgTemplate $cfgPath }
  $content = [System.IO.File]::ReadAllText($cfgPath, [System.Text.Encoding]::UTF8)
  $content = $content -replace '(?m)^\s*#?\s*maxVisits\s*=\s*[^\r\n]*\r?$', 'maxVisits = 100'
  $content = $content -replace '(?m)^\s*maxTime\s*=\s*[^\r\n]*\r?$', '# maxTime = 60'
  $content = $content -replace '(?m)^\s*#?\s*trtDeviceToUse\s*=\s*[^\r\n]*\r?$', 'trtDeviceToUse = 0'
  [System.IO.File]::WriteAllText($cfgPath, $content, (New-Object System.Text.UTF8Encoding $false))
  Write-Host "  [OK] analysis.cfg 特调（maxVisits=100, 无 maxTime, trtDeviceToUse=0）" -ForegroundColor Green
} else {
  Write-Host "  警告：缺少 analysis_example.cfg，跳过 analysis.cfg 预置" -ForegroundColor Yellow
}

if (Test-Path $bundleConfigTxt) {
  try {
    $json = Get-Content $bundleConfigTxt -Raw -Encoding UTF8 | ConvertFrom-Json
    function Set-BundleField($obj, $name, $value) {
      if ($null -ne $obj.PSObject.Properties[$name]) { $obj.$name = $value }
      else { $obj | Add-Member -NotePropertyName $name -NotePropertyValue $value -Force }
    }
    $analysisCmd = '"' + $rel + '\katago.exe" analysis -model "' + $rel + '\b10c384h6nbttflrs.bin.gz" -config "' + $rel + '\analysis.cfg" -quit-without-waiting'
    Set-BundleField $json.ui 'analysis-engine-command' $analysisCmd
    Set-BundleField $json.ui 'analysis-engine-command-customized' $true
    Set-BundleField $json.ui 'first-load-katago' $false
    Set-BundleField $json.ui 'chk-kata-engine-threads' $false
    Set-BundleField $json.ui 'autoload-kata-engine-threads' $false
    Set-BundleField $json.ui 'txt-kata-engine-threads' ""
    [System.IO.File]::WriteAllText($bundleConfigTxt, ($json | ConvertTo-Json -Depth 50), (New-Object System.Text.UTF8Encoding $false))
    Write-Host "  [OK] config.txt 出厂字段已写入（analysis-engine-command = b10c384 伴生）" -ForegroundColor Green
  } catch {
    Write-Host "  警告：config.txt 解析失败，跳过出厂字段预置：$($_.Exception.Message)" -ForegroundColor Yellow
  }
} else {
  Write-Host "  警告：未找到 $bundleConfigTxt，跳过 config.txt 预置" -ForegroundColor Yellow
}
# ---------------------------------------------------------------- summary
$fileCount = (Get-ChildItem $bundleDir -Recurse -File | Measure-Object).Count
$sizeMb = [math]::Round(((Get-ChildItem $bundleDir -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB), 0)
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  打包完成: $bundleDir" -ForegroundColor Green
Write-Host "  版本: $Version ($fileCount 文件, $sizeMb MB)" -ForegroundColor Green
Write-Host "  exe:  $bundleDir\Katayzy.exe" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
exit 0
