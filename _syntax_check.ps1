$Files = @(
  "check_env.ps1",
  "add_engine.ps1",
  "build_engines.ps1",
  "update_weights.ps1",
  "check_first_run.ps1"
)
foreach ($f in $Files) {
  $errs = $null
  [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw -Encoding UTF8 $f), [ref]$errs)
  if ($errs.Count -eq 0) { Write-Host "$f OK" } else { Write-Host "$f ERRORS"; $errs | ForEach-Object { Write-Host $_.Message } }
}
