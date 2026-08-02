# add_engine.ps1 - Append a KataGo engine entry into Katayzy config.txt
# Uses RELATIVE paths (portable / USB stick friendly). Katayzy resolves them
# against its own working directory (see CommandLaunchHelper in source).
# Parameters:
#   -EngineName   display name, e.g. "KataGo-b10c384"
#   -WeightFile   model file name, e.g. "b10c384h6nbttflrs.bin.gz"
#   -ConfigFile   generated cfg file name, e.g. "b10c384.cfg"
#   -SetDefault   make this engine the default (first install)

param(
  [Parameter(Mandatory = $true)][string]$EngineName,
  [Parameter(Mandatory = $true)][string]$WeightFile,
  [Parameter(Mandatory = $true)][string]$ConfigFile,
  [switch]$SetDefault
)

$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $rootDir) { $rootDir = "." }
$configPath = Join-Path $rootDir "user-data\config.txt"
$relPrefix = "engines\katago-trt"

if (-not (Test-Path $configPath)) {
    Write-Host "ERROR: 未找到配置文件 $configPath" -ForegroundColor Red
    exit 1
}

# Build command line with relative paths (JSON-escaped backslashes and quotes)
$command = '"' + $relPrefix + '\katago.exe" gtp -model "' + $relPrefix + '\' + $WeightFile + '" -config "' + $relPrefix + '\' + $ConfigFile + '"'

# Read existing config
$json = Get-Content $configPath -Raw -Encoding UTF8 | ConvertFrom-Json

# Duplicate check
$list = $json.leelaz.'engine-settings-list'
$dup = $list | Where-Object { $_.name -eq $EngineName }
if ($dup) {
    Write-Host ("引擎 '{0}' 已存在，跳过添加。" -f $EngineName) -ForegroundColor Yellow
    exit 0
}

# Build new entry
$entry = [ordered]@{
    ip            = ""
    initialCommand = ""
    userName      = ""
    preload       = $false
    command       = $command
    komi          = 7.5
    isDefault     = $false
    password      = ""
    port          = ""
    name          = $EngineName
    width         = 19
    useJavaSSH    = $false
    useKeyGen     = $false
    keyGenPath    = ""
    height        = 19
}

if ($SetDefault) {
    foreach ($e in $list) { $e.isDefault = $false }
    $entry.isDefault = $true
}

$list += $entry
$json.leelaz.'engine-settings-list' = $list

# Update ui.default-engine index
$ui = $json.ui
if ($SetDefault) {
    $ui.'default-engine' = $list.Count - 1
} elseif ($null -eq $ui.'default-engine' -or [int]$ui.'default-engine' -lt 0) {
    $ui.'default-engine' = 0
}

# Write back as UTF-8 without BOM
$out = $json | ConvertTo-Json -Depth 50
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($configPath, $out, $utf8NoBom)

Write-Host ("引擎 '{0}' 已添加到 Katayzy。" -f $EngineName) -ForegroundColor Green
Write-Host "  command: $command"
if ($SetDefault) {
    Write-Host "  已设为默认引擎。" -ForegroundColor Green
}
exit 0
