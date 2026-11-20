<#
.SYNOPSIS
  Runs an item's work orders through the coder, one at a time, and stops the item at the first one that isn't clean.

.DESCRIPTION
  A queue around .claude/scripts/local-coder.ps1, which still runs each order exactly as before. One background run and
  one notification per item (or per stage), instead of one relay per order. Nothing here writes or changes code.

  For each order it saves the order's own patch as <order>.patch (the change that order made to its listed files,
  even when an earlier order already changed the same file), then decides whether the item goes on. An item STOPS at
  the first order with any of:
    - a non-zero exit from local-coder.ps1 (1 failed or timed out, 2 changed files outside the order, 4 malformed);
    - a coder report whose Status isn't `done`, or with no report at all;
    - a "no" or an unticked box in the report's checklist;
    - no change to any listed file.
  Later orders of a stopped item don't run: they build on the one that failed. In a queue of several items, the next
  item still runs unless its orders list a file the stopped item's orders list. Exit 3 (no model) stops everything.

  Writes <item>.run.md beside the orders: one line per order, each coder report without its transcript, and the
  patches to review. Prints at most about 15 lines.

  Exit codes: 0 every order ran clean; 1 an item stopped; 3 no usable model (ask the owner); 4 nothing to run.

.EXAMPLE
  pwsh -NoProfile -File .claude/scripts/run-orders.ps1 -Dir nimbalyst-local/work-orders/1.0.205 -Item 381
  pwsh -NoProfile -File .claude/scripts/run-orders.ps1 -Orders "nimbalyst-local/work-orders/1.0.205/381-2-fix1.md"
  pwsh -NoProfile -File .claude/scripts/run-orders.ps1 -Queue nimbalyst-local/work-orders/1.0.205/stage-1.queue

  A queue file lists order paths, one per line, in the order they run; a line "# item <name>" starts each item.
#>
[CmdletBinding()]
param(
    [string] $Dir,
    [string] $Item,
    [string] $Orders,
    [string] $Queue,
    [string] $Model,
    [string] $Variant,
    [int] $TimeoutMinutes = 60,
    [string] $Coder
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8   # git and gh write UTF-8; without this, a dash in a diff is garbled
$repo = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repo
if (-not $Coder) { $Coder = Join-Path $repo '.claude\scripts\local-coder.ps1' }

# What to run: a list of items, each a name and its order paths.
$items = [System.Collections.Generic.List[object]]::new()
function Add-Item([string] $name, [string[]] $paths) { if ($paths.Count -gt 0) { $items.Add([pscustomobject]@{ Name = $name; Orders = @($paths) }) } }
if ($Queue) {
    $name = $null; $paths = @()
    foreach ($line in Get-Content -LiteralPath $Queue) {
        $line = $line.Trim()
        if ($line -match '^#\s*item\s+(.+)$') { if ($name) { Add-Item $name $paths }; $name = $Matches[1].Trim(); $paths = @(); continue }
        if (-not $line -or $line.StartsWith('#')) { continue }
        if (-not $name) { $name = ([IO.Path]::GetFileNameWithoutExtension($line) -split '-')[0] }
        $paths += $line
    }
    if ($name) { Add-Item $name $paths }
    $outDir = Split-Path (Resolve-Path -LiteralPath $Queue).Path
} elseif ($Orders) {
    $paths = @($Orders -split '[,;]\s*|\s+(?=\S+\.md)' | Where-Object { $_ })
    $name = if ($Item) { $Item } else { ([IO.Path]::GetFileNameWithoutExtension($paths[0]) -split '-')[0] }
    Add-Item $name $paths
    $outDir = Split-Path (Resolve-Path -LiteralPath $paths[0] -ErrorAction SilentlyContinue)?.Path
} elseif ($Dir -and $Item) {
    # <item>-<n>.md in number order. Correction and CI orders (<item>-<n>-fix1.md) are run by name with -Orders.
    $paths = @(Get-ChildItem -LiteralPath $Dir -Filter "$Item-*.md" | Where-Object { $_.Name -match "^$([regex]::Escape($Item))-(\d+)\.md$" } |
        Sort-Object { [int]($_.Name -replace "^$([regex]::Escape($Item))-(\d+)\.md$", '$1') } | ForEach-Object { $_.FullName })
    Add-Item $Item $paths
    $outDir = (Resolve-Path -LiteralPath $Dir).Path
}
if ($items.Count -eq 0 -or -not $outDir) { Write-Output 'RUN-ORDERS: nothing to run. Give -Dir and -Item, -Orders, or -Queue.'; exit 4 }

function Get-Allowed([string] $orderPath) {
    $allowed = @(); $inList = $false
    foreach ($line in (Get-Content -LiteralPath $orderPath -ErrorAction SilentlyContinue)) {
        if ($line -match '^##\s+Files you may change') { $inList = $true; continue }
        if ($inList -and $line -match '^##\s') { break }
        if ($inList -and $line -match '^\s*-\s+`([^`]+)`') { $allowed += ($Matches[1] -replace '\\', '/') }
    }
    $allowed
}

function Get-Report([string] $resultPath) {
    # The REPORT block alone: local-coder.ps1 keeps the whole transcript after it.
    if (-not (Test-Path -LiteralPath $resultPath)) { return '' }
    $text = Get-Content -LiteralPath $resultPath -Raw
    $at = $text.LastIndexOf("## The coder's report")
    if ($at -lt 0) { return '' }
    $block = $text.Substring($at)
    $start = [regex]::Match($block, '(?m)^\s*REPORT\s*$')   # a line of its own, not "no REPORT block"
    if (-not $start.Success) { return '' }
    $block = $block.Substring($start.Index)
    foreach ($end in @('--- stderr ---', '```')) {
        $cut = $block.IndexOf($end)
        if ($cut -ge 0) { $block = $block.Substring(0, $cut) }
    }
    $block.Trim()
}

$temp = Join-Path ([IO.Path]::GetTempPath()) "run-orders-$([guid]::NewGuid())"
New-Item -ItemType Directory $temp | Out-Null
$empty = Join-Path $temp 'empty'
Set-Content -LiteralPath $empty '' -NoNewline
$stoppedFiles = @{}      # file -> the stopped item that listed it
$summary = @()
$exitCode = 0
$noModel = $false

try {
    # Not $item: that is the -Item parameter (names ignore case), and it is typed as a string.
    foreach ($unit in $items) {
        $itemFiles = @($unit.Orders | ForEach-Object { Get-Allowed $_ } | Select-Object -Unique)
        $clash = @($itemFiles | Where-Object { $stoppedFiles.ContainsKey($_) } | Select-Object -First 1)
        if ($noModel) { $summary += "$($unit.Name): NOT RUN (no model)"; continue }
        if ($clash) {
            $summary += "$($unit.Name): SKIPPED. It shares $($clash[0]) with stopped item $($stoppedFiles[$clash[0]])."
            $itemFiles | ForEach-Object { $stoppedFiles[$_] = $unit.Name }
            $exitCode = 1
            continue
        }
        $run = @("# Run: item $($unit.Name)", '', "$(Get-Date -Format 'yyyy-MM-dd HH:mm'). Orders in the order they ran. Review each against its own patch.", '')
        $ran = 0; $stopReason = $null; $patches = @()
        foreach ($order in $unit.Orders) {
            $orderName = [IO.Path]::GetFileNameWithoutExtension($order)
            if ($stopReason) { $run += "- ``$orderName``: not run (the item stopped)"; continue }
            if (-not (Test-Path -LiteralPath $order)) { $stopReason = "$orderName is missing"; $run += "- ``$orderName``: MISSING"; continue }
            $orderPath = (Resolve-Path -LiteralPath $order).Path
            $orderDir = Split-Path $orderPath
            $allowed = @(Get-Allowed $orderPath)

            # The listed files as they are now, so this order's own patch can be cut out afterwards.
            $beforeDir = Join-Path $temp "$($unit.Name)-$orderName"
            New-Item -ItemType Directory $beforeDir -Force | Out-Null
            $before = @{}
            for ($i = 0; $i -lt $allowed.Count; $i++) {
                if (Test-Path -LiteralPath $allowed[$i] -PathType Leaf) {
                    $copy = Join-Path $beforeDir "$i"
                    Copy-Item -LiteralPath $allowed[$i] $copy
                    $before[$allowed[$i]] = $copy
                }
            }

            # The coder gets an empty file as its input. Left to inherit this script's, OpenCode waits on it for more
            # of the message and the order never starts (found on the first trial: a 10-minute timeout, nothing sent).
            $coderArgs = @('-NoProfile', '-File', "`"$Coder`"", '-Order', "`"$orderPath`"", '-TimeoutMinutes', "$TimeoutMinutes")
            if ($Model) { $coderArgs += @('-Model', "`"$Model`"") }
            if ($Variant) { $coderArgs += @('-Variant', "`"$Variant`"") }
            $runLog = Join-Path $orderDir "$orderName.run.log"
            $started = Get-Date
            $proc = Start-Process -FilePath 'pwsh' -NoNewWindow -PassThru -ArgumentList $coderArgs `
                -RedirectStandardInput $empty -RedirectStandardOutput $runLog -RedirectStandardError "$runLog.err"
            $null = $proc.Handle   # keeps ExitCode readable after the process ends
            $proc.WaitForExit()
            $code = $proc.ExitCode
            $errText = Get-Content -LiteralPath "$runLog.err" -Raw -ErrorAction SilentlyContinue
            if ($errText) { Add-Content -LiteralPath $runLog "`n--- stderr ---`n$errText" }
            Remove-Item -LiteralPath "$runLog.err" -ErrorAction SilentlyContinue
            $minutes = [math]::Round(((Get-Date) - $started).TotalMinutes, 1)
            $ran++

            # This order's own patch.
            $patch = @()
            foreach ($file in $allowed) {
                $exists = Test-Path -LiteralPath $file -PathType Leaf
                $old = if ($before.ContainsKey($file)) { $before[$file] } else { $empty }
                if (-not $exists -and -not $before.ContainsKey($file)) { continue }
                if ($exists -and $before.ContainsKey($file) -and (Get-FileHash -LiteralPath $file).Hash -eq (Get-FileHash -LiteralPath $old).Hash) { continue }
                $new = if ($exists) { $file } else { $empty }
                $diff = @(git -c core.autocrlf=false -c core.safecrlf=false diff --no-index --no-color -- $old $new 2>$null)
                $fromLabel = if ($before.ContainsKey($file)) { "a/$file" } else { '/dev/null' }
                $toLabel = if ($exists) { "b/$file" } else { '/dev/null' }
                $patch += "diff --git a/$file b/$file"
                $patch += "--- $fromLabel"
                $patch += "+++ $toLabel"
                $patch += @($diff | Where-Object { $_ -notmatch '^(diff --git|index |--- |\+\+\+ |new file mode|deleted file mode)' })
            }
            $patchPath = Join-Path $orderDir "$orderName.patch"
            if ($patch.Count -gt 0) { Set-Content -LiteralPath $patchPath $patch } else { Remove-Item -LiteralPath $patchPath -ErrorAction SilentlyContinue }
            $patchShown = [IO.Path]::GetRelativePath($repo, $patchPath) -replace '\\', '/'
            $patchNote = if ($patch.Count -gt 0) { 'patch `{0}` ({1} lines)' -f $patchShown, $patch.Count } else { 'no patch' }

            $report = Get-Report (Join-Path $orderDir "$orderName.result.md")
            $status = if ($report -match '(?m)^Status:\s*(.+)$') { $Matches[1].Trim() } else { '' }
            $checklist = if ($report -match '(?s)Checklist:(.*?)(?:\r?\nProblems:|\z)') { $Matches[1] } else { '' }
            # A line refuses when its box is unticked or its answer is "no". The answer is what follows the line's last
            # colon, with any (...) aside removed first, so "has no X left: yes (no such functions found)" is a yes.
            # A line with no colon refuses only when it starts or ends with "no".
            $refused = @($checklist -split "`n" | Where-Object {
                $line = $_ -replace '\([^)]*\)', ''
                if ($line -match '^\s*-?\s*\[ \]') { $true }
                elseif ($line -match '^\s*-?\s*\[[xX]\]') { $false }   # a ticked box is a yes, whatever its words say
                elseif ($line -match ':\s*([^:]*)$') { $Matches[1].Trim() -match '(?i)^no\b' }
                else { $line -match '(?i)(^\W*no\b|\bno[,.]?\s*$)' }
            })
            $why = switch ($code) {
                0 { $null }
                1 { 'exit 1: the coder failed or timed out' }
                2 { 'exit 2: it changed files outside the order (put back)' }
                3 { 'exit 3: no usable model' }
                4 { 'exit 4: the order is malformed' }
                default { "exit $code" }
            }
            if (-not $why) {
                if (-not $report) { $why = 'no REPORT block' }
                elseif ($status -notmatch '^done\b') { $why = "the coder's Status is `"$status`"" }
                elseif ($refused.Count -gt 0) { $why = "the checklist has a no: $($refused[0].Trim())" }
                elseif ($patch.Count -eq 0) { $why = 'it changed nothing' }
            }
            $run += "- ``$orderName``: $(if ($why) { 'STOPPED, ' + $why } else { 'clean' }); $minutes min; $patchNote"
            if ($report) { $run += ''; $run += '  ```'; $run += @($report -split "`r?`n" | ForEach-Object { "  $_" }); $run += '  ```'; $run += '' }
            if ($patch.Count -gt 0) { $patches += $patchShown }
            if ($why) { $stopReason = "$orderName, $why" }
            if ($code -eq 3) { $noModel = $true }
        }
        $runPath = Join-Path $outDir "$($unit.Name).run.md"
        Set-Content -LiteralPath $runPath $run
        $runShown = (Resolve-Path -LiteralPath $runPath -Relative) -replace '\\', '/' -replace '^\./', ''
        if ($stopReason) {
            $exitCode = 1
            $itemFiles | ForEach-Object { $stoppedFiles[$_] = $unit.Name }
            $summary += "$($unit.Name): STOPPED at $stopReason. $ran of $($unit.Orders.Count) orders ran; $($patches.Count) patches saved. $runShown"
        } else {
            $summary += "$($unit.Name): clean, $ran of $($unit.Orders.Count) orders, $($patches.Count) patches. $runShown"
        }
    }
} finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}

if ($noModel) { $exitCode = 3 }
$verdict = switch ($exitCode) { 0 { 'ALL CLEAN' } 3 { 'NO MODEL: ask the owner' } default { 'STOPPED' } }
Write-Output "RUN-ORDERS: $verdict. $($items.Count) item(s)."
$summary | Select-Object -First 12 | ForEach-Object { Write-Output "  $_" }
if ($summary.Count -gt 12) { Write-Output "  ... and $($summary.Count - 12) more (see each <item>.run.md)" }
exit $exitCode
