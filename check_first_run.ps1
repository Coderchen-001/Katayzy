# check_first_run.ps1 - Detect whether Katayzy has never been configured.
# Exit code: 0 = first run (no engine in config.txt AND no build cfg under engines\katago-trt)
#            1 = already configured
$ErrorActionPreference = "Continue"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $rootDir) { $rootDir = "." }
$configTxt = Join-Path $rootDir "user-data\config.txt"
$engineDir = Join-Path $rootDir "engines\katago-trt"

# b10c384 现在用 analysis.cfg（模板特调），不再 genconfig 生成 b10c384.cfg
$hasCfg = $false
$targetCfgs = @("analysis.cfg", "b10c512.cfg", "b11c768.cfg")
foreach ($c in $targetCfgs) {
    if (Test-Path (Join-Path $engineDir $c)) { $hasCfg = $true; break }
}

$hasEngine = $false
if (Test-Path $configTxt) {
    try {
        $json = Get-Content $configTxt -Raw -Encoding UTF8 | ConvertFrom-Json
        $list = @($json.leelaz.'engine-settings-list')
        $hasEngine = $list.Count -gt 0
    } catch {
        $hasEngine = $false
    }
}

if (-not $hasEngine -and -not $hasCfg) {
    exit 0
}
exit 1
