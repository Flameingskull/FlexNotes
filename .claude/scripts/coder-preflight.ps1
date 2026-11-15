<#
.SYNOPSIS
  A build's coder check in one call: is a coding model chosen, and is it on the GPU?

.DESCRIPTION
  Prints one verdict on its first line:
    GPU OK (known)    the model server process recorded at the last confirmation is still running: same load.
    GPU OK (checked)  the server process holds GPU memory and little RAM. Recorded for next time.
    ON CPU            the server is running but the model isn't on the GPU, or that couldn't be confirmed.
                      Follow .claude/skills/local-coder/preflight.md, "Not on the GPU".
    NO MODEL          OpenCode has no coding model chosen, doesn't list it, or its server isn't running. Ask the owner.
  It changes nothing about any model: it never loads, switches or unloads one. It knows no model or server by name:
  the server's process name comes from nimbalyst-local/coder-gpu.md (the last confirmation) or -ServerProcess.

  Exit codes: 0 GPU OK; 1 ON CPU; 3 NO MODEL.

.EXAMPLE
  pwsh -NoProfile -File .claude/scripts/coder-preflight.ps1
  pwsh -NoProfile -File .claude/scripts/coder-preflight.ps1 -NoPing -ServerProcess llama-server
#>
[CmdletBinding()]
param(
    [string] $Model = $env:FLEXNOTES_CODER_MODEL,
    [string] $ServerProcess,
    [switch] $NoPing,
    [int] $PingSeconds = 120
)

$ErrorActionPreference = 'Stop'
$repo = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repo
$record = Join-Path $repo 'nimbalyst-local\coder-gpu.md'

function Stop-Check([int] $code, [string] $verdict, [string[]] $lines) {
    Write-Output "CODER-PREFLIGHT: $verdict"
    $lines | Where-Object { $_ } | ForEach-Object { Write-Output $_ }
    exit $code
}

# 1. The model OpenCode will use.
$opencode = (Get-Command opencode -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1)?.Source
if (-not $opencode) { Stop-Check 3 'NO MODEL' @('OpenCode is not installed (npm i -g opencode-ai).') }
$env:OPENCODE_DISABLE_AUTOUPDATE = '1'
$env:NO_COLOR = '1'
$listed = @(& $opencode models 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\S+/\S+$' })
$from = if ($Model) { 'given' } else { "OpenCode's setting" }
if (-not $Model) {
    try { $Model = ((& $opencode debug config 2>$null | Out-String) | ConvertFrom-Json).model } catch { }
}
if (-not $Model) { Stop-Check 3 'NO MODEL' @('No coding model is chosen in OpenCode. Ask the owner which to use:', ($listed -join ', ')) }
if ($listed -notcontains $Model) { Stop-Check 3 'NO MODEL' @("OpenCode doesn't list $Model. It lists:", ($listed -join ', ')) }
$modelLine = "Model: $Model ($from)"

# 2. The server process: the one recorded last time, if it's still running.
$recordText = if (Test-Path -LiteralPath $record) { Get-Content -LiteralPath $record -Raw } else { '' }
$knownPid = if ($recordText -match 'PID \*\*(\d+)\*\*') { [int]$Matches[1] } else { 0 }
if (-not $ServerProcess -and $recordText -match 'Model server process: `([^`]+)`') { $ServerProcess = $Matches[1] }
$ServerProcess = $ServerProcess -replace '\.exe$', ''
$server = if ($knownPid) { Get-Process -Id $knownPid -ErrorAction SilentlyContinue } else { $null }
$known = [bool]$server
if (-not $server -and $ServerProcess) {
    $server = Get-Process -Name $ServerProcess -ErrorAction SilentlyContinue | Sort-Object WorkingSet64 -Descending | Select-Object -First 1
}
if (-not $server) {
    $why = if ($ServerProcess) { "No $ServerProcess process is running: the model server is stopped." }
           else { 'No model server is recorded in nimbalyst-local/coder-gpu.md. Pass -ServerProcess <name>.' }
    Stop-Check 3 'NO MODEL' @($modelLine, $why)
}
$ramGb = [math]::Round($server.WorkingSet64 / 1GB, 1)
$context = ''
try {
    $commandLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($server.Id)").CommandLine
    if ($commandLine -match '(?:-c|--ctx-size)\s+(\d+)') { $context = "; context $($Matches[1])" }
} catch { }
$modelLine += $context

# 3. GPU memory against the server's own memory.
$gpuLine = 'GPU: nvidia-smi not found, so the GPU could not be checked.'
$onGpu = $false
$smi = (Get-Command nvidia-smi -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1)?.Source
if ($smi) {
    $memory = (& $smi --query-gpu=memory.used,memory.total --format=csv,noheader,nounits | Select-Object -First 1) -split ',\s*'
    $usedGb = [math]::Round([int]$memory[0] / 1024, 1)
    $totalGb = [math]::Round([int]$memory[1] / 1024, 1)
    $gpuPids = @(& $smi --query-compute-apps=pid --format=csv,noheader,nounits | ForEach-Object { "$_".Trim() })
    $holdsGpu = $gpuPids -contains "$($server.Id)"
    # On the GPU the model's size is in GPU memory and the process keeps little RAM; on the CPU it's the reverse.
    $onGpu = $holdsGpu -and $usedGb -ge 2 -and $ramGb -lt $usedGb
    $gpuLine = "Server: $($server.ProcessName) pid $($server.Id), $ramGb GB RAM; GPU $usedGb of $totalGb GB used$(if (-not $holdsGpu) { '; the server holds no GPU memory' })"
}
if (-not $onGpu -and -not $known) {
    Stop-Check 1 'ON CPU' @($modelLine, $gpuLine, 'Fix: .claude/skills/local-coder/preflight.md, "Not on the GPU". Never run orders on the CPU without saying so.')
}
if ($known -and $smi -and -not $onGpu) {
    # Same process as the last confirmation, yet the numbers disagree: trust the numbers.
    Stop-Check 1 'ON CPU' @($modelLine, $gpuLine, 'The recorded process is running but no longer looks GPU-loaded. Fix: .claude/skills/local-coder/preflight.md.')
}

# 4. Does it answer? Skipped while an order is running, so nothing else is on the model.
$pingLine = ''
$lock = Join-Path $repo '.git\local-coder.lock'
$busy = (Test-Path -LiteralPath $lock) -and (Get-Process -Id (Get-Content -LiteralPath $lock) -ErrorAction SilentlyContinue)
if (-not $NoPing -and -not $busy) {
    $out = [IO.Path]::GetTempFileName()
    $started = Get-Date
    $p = Start-Process -FilePath $opencode -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError "$out.err" `
        -ArgumentList @('run', '"Reply with exactly the word READY. Do not use any tools."', '--model', $Model)
    $done = $p.WaitForExit($PingSeconds * 1000)
    if (-not $done) { taskkill /PID $p.Id /T /F | Out-Null }
    $seconds = [math]::Round(((Get-Date) - $started).TotalSeconds)
    $answer = (Get-Content -LiteralPath $out -Raw -ErrorAction SilentlyContinue)
    Remove-Item -LiteralPath $out, "$out.err" -ErrorAction SilentlyContinue
    if ($done -and $answer -match 'READY') { $pingLine = "Answered READY in $seconds s" }
    else { Stop-Check 3 'NO MODEL' @($modelLine, $gpuLine, "The model didn't answer a one-line request within $PingSeconds s. Ask the owner.") }
} elseif ($busy) { $pingLine = 'Not pinged: a work order is running.' }

# 5. Record a fresh confirmation, so the next check is "known".
if (-not $known) {
    $stamp = "- When: $(Get-Date -Format 'yyyy-MM-dd HH:mm') (local), by coder-preflight.ps1"
    $proc = "- Model server process: ``$($server.ProcessName).exe``, PID **$($server.Id)**"
    if ($recordText -match '(?m)^- When:.*$' -and $recordText -match '(?m)^- Model server process:.*$') {
        $recordText = [regex]::Replace($recordText, '(?m)^- When:.*$', $stamp, 1)
        $recordText = [regex]::Replace($recordText, '(?m)^- Model server process:.*$', $proc, 1)
    } else {
        $recordText = "# Coder on the GPU: last confirmation (local only, never committed)`n`n## Last confirmed`n`n$stamp`n$proc`n`n" + $recordText
    }
    Set-Content -LiteralPath $record $recordText.TrimEnd()
}
Stop-Check 0 "GPU OK ($(if ($known) { 'known' } else { 'checked' }))" @($modelLine, $gpuLine, $pingLine)
