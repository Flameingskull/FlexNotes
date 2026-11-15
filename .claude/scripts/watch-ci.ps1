<#
.SYNOPSIS
  Waits for one CI run and prints one verdict, so a build doesn't poll `gh run` at peak context.

.DESCRIPTION
  Run it in the background and wait for the notification.
  - Release run (the default): the build.yml run for a commit on main. On success it prints the release, its assets
    and whether the issues it lists closed. On failure it prints status.txt and the first errors from errors.txt on
    the ci-logs branch.
  - Check run (-Check): the pr.yml run for the head of a check branch's pull request. It compiles and runs every test
    without advancing the release number. On failure it prints the first error lines of the failed step.
  The full log is written to nimbalyst-local/ci/ (local only). Prints at most about 15 lines.

  Exit codes: 0 green; 1 the run failed; 2 no run found, or it didn't finish in time.

.EXAMPLE
  pwsh -NoProfile -File .claude/scripts/watch-ci.ps1                       # the release run for HEAD
  pwsh -NoProfile -File .claude/scripts/watch-ci.ps1 -Sha 6547a21          # a finished run reports straight away
  pwsh -NoProfile -File .claude/scripts/watch-ci.ps1 -Check                # the check run for HEAD
#>
[CmdletBinding()]
param(
    [string] $Sha,
    [switch] $Check,
    [int] $TimeoutMinutes = 60,
    [int] $FindMinutes = 3
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8   # git and gh write UTF-8; without this, a dash in a diff is garbled
$repo = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $repo
$workflow = if ($Check) { 'pr.yml' } else { 'build.yml' }
$kind = if ($Check) { 'check' } else { 'release' }
if (-not $Sha) { $Sha = 'HEAD' }
$full = (git rev-parse --verify --quiet "$Sha^{commit}" 2>$null)
if (-not $full) { Write-Output "WATCH-CI: unknown commit $Sha"; exit 2 }
$full = "$full".Trim()
$short = $full.Substring(0, 7)
$outDir = Join-Path $repo 'nimbalyst-local\ci'
New-Item -ItemType Directory $outDir -Force | Out-Null

# Find the run. A push takes a few seconds to start one.
$run = $null
$findUntil = (Get-Date).AddMinutes($FindMinutes)
do {
    $found = gh run list --workflow $workflow --commit $full --limit 1 --json databaseId,status,conclusion,number,url 2>$null | ConvertFrom-Json
    if ($found) { $run = @($found)[0]; break }
    Start-Sleep -Seconds 10
} while ((Get-Date) -lt $findUntil)
if (-not $run) { Write-Output "WATCH-CI: no $workflow run for $short after $FindMinutes min. Was it pushed?"; exit 2 }

# Wait for it, quietly.
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ($run.status -ne 'completed') {
    if ((Get-Date) -gt $deadline) { Write-Output "WATCH-CI: $kind run $($run.number) for $short still $($run.status) after $TimeoutMinutes min: $($run.url)"; exit 2 }
    Start-Sleep -Seconds 30
    $run = gh run view $run.databaseId --json databaseId,status,conclusion,number,url | ConvertFrom-Json
}

$logPath = Join-Path $outDir "$kind-$($run.number)-$short.log"
$shown = (Resolve-Path -LiteralPath $outDir -Relative) -replace '\\', '/'
if ($run.conclusion -eq 'success') {
    if ($Check) {
        Write-Output "WATCH-CI: GREEN. Check run $($run.number) for $short compiled and passed every test. Safe to push main."
        exit 0
    }
    $offset = [int]((Select-String -LiteralPath '.github/workflows/build.yml' -Pattern '^\s*BUILD_OFFSET:\s*(\d+)').Matches[0].Groups[1].Value)
    $build = $run.number + $offset
    $release = gh release view "build-$build" --json name,url,assets,body 2>$null | ConvertFrom-Json
    if (-not $release) { Write-Output "WATCH-CI: run $($run.number) is green but release build-$build isn't there: $($run.url)"; exit 1 }
    $assets = @($release.assets.name)
    $need = @('\.apk$', '-source\.zip$', '-source\.tar\.gz$', '^SHA256SUMS\.txt$')
    $missing = @($need | Where-Object { $pattern = $_; -not ($assets | Where-Object { $_ -match $pattern }) })
    Write-Output "WATCH-CI: GREEN. $($release.name) (run $($run.number), $short): $($release.url)"
    Write-Output "Assets ($($assets.Count)): $($assets -join ', ')$(if ($missing) { '. MISSING: ' + ($missing -join ', ') })"
    $section = [regex]::Match($release.body, '(?s)## Issues resolved\s*(.*?)(\r?\n## |\z)').Groups[1].Value
    $numbers = @([regex]::Matches($section, '(?m)^- #(\d+)') | ForEach-Object { $_.Groups[1].Value })
    if ($numbers.Count -eq 0) { Write-Output 'Issues resolved: none listed in the release post.' }
    else {
        $closed = @(); $open = @()
        foreach ($n in $numbers) {
            $state = (gh issue view $n --json state -q .state 2>$null)
            if ($state -eq 'CLOSED') { $closed += "#$n" } else { $open += "#$n" }
        }
        Write-Output "Issues closed: $(if ($closed) { $closed -join ' ' } else { 'none' })$(if ($open) { '. STILL OPEN: ' + ($open -join ' ') })"
    }
    Set-Content -LiteralPath $logPath $release.body
    Write-Output "Release post saved: $shown/$(Split-Path $logPath -Leaf)"
    if ($missing) { exit 1 }
    exit 0
}

# Failed (or cancelled).
Write-Output "WATCH-CI: $($run.conclusion.ToUpper()). $kind run $($run.number) for ${short}: $($run.url)"
$errors = @()
if (-not $Check) {
    git fetch --quiet origin ci-logs 2>$null
    $status = (git show origin/ci-logs:status.txt 2>$null | Out-String).Trim()
    if ($status -match "sha=$full") {
        Write-Output "status.txt: $status"
        $errors = @(git show origin/ci-logs:errors.txt 2>$null)
        Set-Content -LiteralPath $logPath (@($errors) + '' + '--- build-tail.log ---' + @(git show origin/ci-logs:build-tail.log 2>$null))
    } else {
        Write-Output "ci-logs is for another run ($status); reading the run's own log instead."
    }
}
if ($errors.Count -eq 0) {
    $failed = @(gh run view $run.databaseId --log-failed 2>$null)
    Set-Content -LiteralPath $logPath $failed
    # Lines with a colour code are the workflow echoing its own script, not output.
    $errors = @($failed | Where-Object { $_ -notmatch '(|\^\[)\[\d' -and $_ -match '(^|\s)e: |error:|FAILED|Exception|audit coverage: .*problem|unused private' } |
        ForEach-Object { ($_ -replace '^.*?\d{4}-\d\d-\d\dT[\d:.]+Z\s*', '').Trim() } | Select-Object -Unique)
}
$errors = @($errors | Where-Object { "$_".Trim() })
Write-Output "Errors ($($errors.Count)), first 8:"
$errors | Select-Object -First 8 | ForEach-Object { Write-Output ("  " + ("$_".Length -gt 300 ? "$_".Substring(0, 300) + '…' : "$_")) }
Write-Output "Full log: $shown/$(Split-Path $logPath -Leaf)"
exit 1
