<#
.SYNOPSIS
  Runs one work order through the local coder: OpenCode on the model Unsloth Studio has loaded.

.DESCRIPTION
  All FlexNotes code is written by the local model (owner, 2026-11-03). Claude writes a work order
  (.claude/skills/qwen-code/SKILL.md), and this script runs it headlessly and reports what changed.
  - It never loads or switches a model. If Unsloth Studio isn't running or has no model loaded, it stops (exit 3), so
    the owner's own chats are never cut off.
  - Only the files listed under "## Files you may change" may change. Any other file the coder changed is put back
    exactly as it was before the run (exit 2), unless -KeepStray.
  - One order at a time: a second run while one is going stops at once.
  - Writes <order>.log (the coder's transcript) and <order>.result.md (what changed) beside the order.

  Exit codes: 0 ran, changed only listed files; 1 the coder failed, timed out or another run is going; 2 it changed files
  outside the order (put back); 3 no model to run on; 4 bad order.

.EXAMPLE
  pwsh -NoProfile -File .claude/scripts/qwen-code.ps1 -Order nimbalyst-local/work-orders/1.0.200/312-1.md
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Order,
    [ValidateSet('low', 'medium', 'high', 'xhigh')] [string] $Effort = 'medium',
    [int] $TimeoutMinutes = 60,
    [switch] $KeepStray
)

$ErrorActionPreference = 'Stop'
$repo = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repo

function Stop-Order([int] $code, [string] $message) {
    Write-Output "QWEN-CODE: $message"
    exit $code
}

# The order and the files it allows.
$orderPath = (Resolve-Path -LiteralPath $Order -ErrorAction SilentlyContinue)?.Path
if (-not $orderPath) { Stop-Order 4 "work order not found: $Order" }
$name = [IO.Path]::GetFileNameWithoutExtension($orderPath)
$allowed = @()
$inList = $false
foreach ($line in (Get-Content -LiteralPath $orderPath)) {
    if ($line -match '^##\s+Files you may change') { $inList = $true; continue }
    if ($inList -and $line -match '^##\s') { break }
    if ($inList -and $line -match '^\s*-\s+`([^`]+)`') { $allowed += ($Matches[1] -replace '\\', '/') }
}
if ($allowed.Count -eq 0) { Stop-Order 4 'the order has no "## Files you may change" list of `path` bullets' }

# The model: whatever Unsloth Studio has loaded. Never load or switch one here.
$unsloth = (Get-Command unsloth -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1)?.Source
if (-not $unsloth) { $unsloth = Join-Path $HOME '.unsloth\studio\bin\unsloth.exe' }
$opencode = (Get-Command opencode -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1)?.Source
if (-not $opencode) { Stop-Order 3 'OpenCode is not installed (npm i -g opencode-ai).' }
$base = 'http://127.0.0.1:8888'
$cfgPath = Join-Path $HOME '.unsloth\studio\auth\agents\opencode\opencode.json'

try { Invoke-RestMethod "$base/api/health" -TimeoutSec 5 | Out-Null }
catch { if (-not $_.Exception.Response) { Stop-Order 3 'Unsloth Studio is not running. Start it and load a model, then run the order again.' } }

function Get-LoadedModel {
    $headers = @{}
    if (Test-Path -LiteralPath $cfgPath) {
        $key = (Get-Content -LiteralPath $cfgPath -Raw | ConvertFrom-Json).provider.'unsloth-studio'.options.apiKey
        if ($key) { $headers.Authorization = "Bearer $key" }
    }
    try { $r = Invoke-RestMethod "$base/v1/models" -Headers $headers -TimeoutSec 10 }
    catch { return 'unauthorised' }
    $m = @($r.data | Where-Object { $_.loaded }) | Select-Object -First 1
    if ($m) { return $m } else { return 'none' }
}

$model = Get-LoadedModel
if ($model -is [string] -and $model -eq 'none') { Stop-Order 3 'Unsloth Studio has no model loaded. Load one in Studio, then run the order again.' }
# Refresh OpenCode's key and reasoning effort for the loaded model. --no-serve: never start a server.
$refresh = & $unsloth start opencode --reasoning-effort $Effort --no-serve --no-launch 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Stop-Order 3 "Unsloth couldn't prepare OpenCode: $($refresh.Trim())" }
$model = Get-LoadedModel
if ($model -is [string]) { Stop-Order 3 "Unsloth Studio has no model ready for OpenCode ($model)." }

$modelRef = "unsloth-studio/$($model.id)"
$env:OPENCODE_CONFIG = $cfgPath
$env:OPENCODE_CONFIG_CONTENT = @{
    model = $modelRef; small_model = $modelRef; enabled_providers = @('unsloth-studio'); disabled_providers = @()
} | ConvertTo-Json -Compress
$env:OPENCODE_DISABLE_CLAUDE_CODE = '1'   # AGENTS.md, not CLAUDE.md: the coder's context is small
$env:OPENCODE_DISABLE_AUTOUPDATE = '1'
$env:NO_COLOR = '1'

# One order at a time.
$lock = Join-Path $repo '.git\qwen-code.lock'
if (Test-Path -LiteralPath $lock) {
    $other = Get-Content -LiteralPath $lock -ErrorAction SilentlyContinue
    if ($other -and (Get-Process -Id $other -ErrorAction SilentlyContinue)) { Stop-Order 1 "another work order is running (pid $other)." }
}
Set-Content -LiteralPath $lock $PID

function Get-DirtyFiles {
    git status --porcelain=v1 -uall --no-renames | ForEach-Object { $_.Substring(3).Trim('"') }
}

$stash = Join-Path ([IO.Path]::GetTempPath()) "qwen-code-$([guid]::NewGuid())"
$exitCode = 0
try {
    # Remember every file that's already changed, so the coder's stray edits can be put back exactly.
    New-Item -ItemType Directory $stash | Out-Null
    $before = @{}
    foreach ($f in Get-DirtyFiles) {
        if (Test-Path -LiteralPath $f -PathType Leaf) {
            $before[$f] = (Get-FileHash -LiteralPath $f).Hash
            $copy = Join-Path $stash $f
            New-Item -ItemType Directory (Split-Path $copy) -Force | Out-Null
            Copy-Item -LiteralPath $f $copy
        } else { $before[$f] = 'missing' }
    }

    $dir = Split-Path $orderPath
    $log = Join-Path $dir "$name.log"
    $message = "Carry out the attached work order ($name) exactly, following AGENTS.md. Finish with the REPORT block."
    $started = Get-Date
    Write-Output "QWEN-CODE: running $name on $($model.id) $($model.quant) ($($model.context_length) tokens, effort $Effort)"
    $p = Start-Process -FilePath $opencode -NoNewWindow -PassThru `
        -ArgumentList @('run', "`"$message`"", '--dir', "`"$repo`"", '--title', "`"work order $name`"", '--file', "`"$orderPath`"") `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $null = $p.Handle   # keeps ExitCode readable after the process ends
    $status = 'finished'
    if (-not $p.WaitForExit($TimeoutMinutes * 60 * 1000)) {
        taskkill /PID $p.Id /T /F | Out-Null
        $status = "timed out after $TimeoutMinutes min"
    } elseif ($p.ExitCode -ne 0) { $status = "failed (exit $($p.ExitCode))" }
    $errText = Get-Content -LiteralPath "$log.err" -Raw -ErrorAction SilentlyContinue
    if ($errText) { Add-Content -LiteralPath $log "`n--- stderr ---`n$errText" }
    Remove-Item -LiteralPath "$log.err" -ErrorAction SilentlyContinue
    $logText = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
    if ($status -eq 'finished' -and $logText -match '(?m)^Error:') { $status = 'failed (the model or OpenCode reported an error)' }
    $minutes = [math]::Round(((Get-Date) - $started).TotalMinutes, 1)

    # What changed in this run, and what was outside the order.
    $now = @(Get-DirtyFiles)
    $changed = @()
    foreach ($f in (@($before.Keys) + $now | Sort-Object -Unique)) {
        $hash = if (Test-Path -LiteralPath $f -PathType Leaf) { (Get-FileHash -LiteralPath $f).Hash } else { 'missing' }
        if ($before.ContainsKey($f)) { if ($hash -ne $before[$f]) { $changed += $f } }
        elseif ($now -contains $f) { $changed += $f }
    }
    $stray = @($changed | Where-Object { $allowed -notcontains $_ })
    $kept = @($changed | Where-Object { $allowed -contains $_ })
    if ($stray.Count -gt 0 -and -not $KeepStray) {
        foreach ($f in $stray) {
            if ($before.ContainsKey($f)) {
                if ($before[$f] -eq 'missing') { Remove-Item -LiteralPath $f -Force -ErrorAction SilentlyContinue }
                else { Copy-Item -LiteralPath (Join-Path $stash $f) $f -Force }
            } else {
                git ls-files --error-unmatch -- $f *> $null
                if ($LASTEXITCODE -eq 0) { git restore --source=HEAD --staged --worktree -- $f }
                else { Remove-Item -LiteralPath $f -Force }
            }
        }
    }

    $report = 'none found: the coder did not finish with a REPORT block.'
    if ($logText) {
        $at = $logText.LastIndexOf('REPORT')
        if ($at -ge 0) { $report = $logText.Substring($at).Trim() }
    }
    $stat = if ($kept.Count -gt 0) { (git diff --stat -- $kept | Out-String).Trim() } else { '(no changes)' }
    $result = @(
        "# Result: $name"
        ''
        "- Model: $($model.id) $($model.quant), $($model.context_length) tokens, effort $Effort"
        "- Run: $status, $minutes min"
        "- Changed (in the order): $(if ($kept) { $kept -join ', ' } else { 'nothing' })"
        "- Outside the order: $(if ($stray) { ($stray -join ', ') + $(if ($KeepStray) { ' (kept)' } else { ' (put back)' }) } else { 'nothing' })"
        "- Transcript: $log"
        ''
        '## Diff stat (tracked files)'
        ''
        '```'
        $stat
        '```'
        ''
        "## The coder's report"
        ''
        '```'
        $report
        '```'
    ) -join "`n"
    $resultPath = Join-Path $dir "$name.result.md"
    Set-Content -LiteralPath $resultPath $result
    Write-Output $result
    Write-Output "QWEN-CODE: result written to $resultPath"

    if ($status -ne 'finished') { $exitCode = 1 }
    elseif ($stray.Count -gt 0) { $exitCode = 2 }
} finally {
    Remove-Item -LiteralPath $stash -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
}
exit $exitCode
