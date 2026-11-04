<#
.SYNOPSIS
  Runs one work order through the coder: OpenCode, headless, on whichever model OpenCode is set to use.

.DESCRIPTION
  All FlexNotes code is written by the coder (owner, 2026-11-03), and the coder is model-agnostic (owner, 2026-11-04):
  this script knows nothing about any model or server. Claude writes a work order (.claude/skills/local-coder/SKILL.md),
  and this runs it and reports what changed.
  - The model, in order: -Model, $env:FLEXNOTES_CODER_MODEL, then the `model` set in OpenCode's own settings. It never
    falls back to OpenCode's built-in default, and never loads, switches or unloads a model. No model, a model OpenCode
    doesn't list, or one that can't be reached stops the run (exit 3), so the owner can choose.
  - Only the files listed under "## Files you may change" may change. Any other file the coder changed is put back
    exactly as it was before the run (exit 2), unless -KeepStray.
  - One order at a time: a second run while one is going stops at once.
  - Writes <order>.log (the coder's transcript) and <order>.result.md (what changed) beside the order.

  Exit codes: 0 ran, changed only listed files; 1 the coder failed, timed out or another run is going; 2 it changed files
  outside the order (put back); 3 no usable model (ask the owner); 4 bad order.

.EXAMPLE
  pwsh -NoProfile -File .claude/scripts/local-coder.ps1 -Order nimbalyst-local/work-orders/1.0.200/312-1.md
  pwsh -NoProfile -File .claude/scripts/local-coder.ps1 -Order <order> -Model provider/model -Variant high
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Order,
    [string] $Model = $env:FLEXNOTES_CODER_MODEL,
    [string] $Variant,
    [int] $TimeoutMinutes = 60,
    [switch] $KeepStray
)

$ErrorActionPreference = 'Stop'
$repo = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repo

function Stop-Order([int] $code, [string] $message) {
    Write-Output "LOCAL-CODER: $message"
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

# The model: whatever OpenCode is set to use. Never OpenCode's built-in fallback.
$opencode = (Get-Command opencode -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1)?.Source
if (-not $opencode) { Stop-Order 3 'OpenCode is not installed (npm i -g opencode-ai).' }
$env:OPENCODE_DISABLE_CLAUDE_CODE = '1'   # AGENTS.md, not CLAUDE.md: the coder's context may be small
$env:OPENCODE_DISABLE_AUTOUPDATE = '1'
$env:NO_COLOR = '1'
$listed = @(& $opencode models 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^\S+/\S+$' })
if (-not $Model) {
    try { $Model = ((& $opencode debug config 2>$null | Out-String) | ConvertFrom-Json).model } catch { }
}
if (-not $Model) {
    Stop-Order 3 ("no coding model chosen. Set `"model`" in OpenCode's settings, or pass -Model / FLEXNOTES_CODER_MODEL. " +
        "OpenCode lists: $(if ($listed) { $listed -join ', ' } else { 'nothing' })")
}
if ($listed -notcontains $Model) {
    Stop-Order 3 "OpenCode doesn't list the model $Model. It lists: $(if ($listed) { $listed -join ', ' } else { 'nothing' })"
}

# One order at a time.
$lock = Join-Path $repo '.git\local-coder.lock'
if (Test-Path -LiteralPath $lock) {
    $other = Get-Content -LiteralPath $lock -ErrorAction SilentlyContinue
    if ($other -and (Get-Process -Id $other -ErrorAction SilentlyContinue)) { Stop-Order 1 "another work order is running (pid $other)." }
}
Set-Content -LiteralPath $lock $PID

function Get-DirtyFiles {
    git status --porcelain=v1 -uall --no-renames | ForEach-Object { $_.Substring(3).Trim('"') }
}

$stash = Join-Path ([IO.Path]::GetTempPath()) "local-coder-$([guid]::NewGuid())"
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
    $runArgs = @('run', "`"$message`"", '--model', $Model, '--dir', "`"$repo`"", '--title', "`"work order $name`"", '--file', "`"$orderPath`"")
    if ($Variant) { $runArgs += @('--variant', $Variant) }
    # OpenCode's own log shows provider errors that it otherwise retries silently.
    $ocLog = Join-Path $HOME '.local\share\opencode\log\opencode.log'
    $ocLogStart = if (Test-Path -LiteralPath $ocLog) { (Get-Item -LiteralPath $ocLog).Length } else { 0 }
    $started = Get-Date
    Write-Output "LOCAL-CODER: running $name on $Model$(if ($Variant) { " ($Variant)" })"
    $p = Start-Process -FilePath $opencode -NoNewWindow -PassThru -ArgumentList $runArgs `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $null = $p.Handle   # keeps ExitCode readable after the process ends

    $status = 'finished'
    $modelError = $null
    $deadline = $started.AddMinutes($TimeoutMinutes)
    while (-not $p.WaitForExit(5000)) {
        if ((Get-Date) -gt $deadline) { $status = "timed out after $TimeoutMinutes min"; break }
        if (Test-Path -LiteralPath $ocLog) {
            $fs = [IO.File]::Open($ocLog, 'Open', 'Read', 'ReadWrite')
            try {
                if ($fs.Length -gt $ocLogStart) {
                    $fs.Seek($ocLogStart, 'Begin') | Out-Null
                    $new = (New-Object IO.StreamReader($fs)).ReadToEnd()
                    $errors = @([regex]::Matches($new, 'message="stream error".*?error\.error="([^"]{0,200})'))
                    if ($errors.Count -ge 3) { $modelError = $errors[-1].Groups[1].Value; break }
                }
            } finally { $fs.Dispose() }
        }
    }
    if (-not $p.HasExited) { taskkill /PID $p.Id /T /F | Out-Null; $p.WaitForExit(10000) | Out-Null }
    if ($modelError) { $status = "stopped: the model can't be reached ($modelError)" }
    elseif ($status -eq 'finished' -and $p.ExitCode -ne 0) { $status = "failed (exit $($p.ExitCode))" }
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
        "- Model: $Model$(if ($Variant) { " ($Variant)" })"
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
    Write-Output "LOCAL-CODER: result written to $resultPath"

    if ($modelError) { $exitCode = 3 }
    elseif ($status -ne 'finished') { $exitCode = 1 }
    elseif ($stray.Count -gt 0) { $exitCode = 2 }
} finally {
    Remove-Item -LiteralPath $stash -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
}
exit $exitCode
