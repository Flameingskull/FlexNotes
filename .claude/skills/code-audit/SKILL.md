---
name: code-audit
description: Owner-triggered code audit of FlexNotes. One code-auditor agent per area reads every source, test and build file in full and checks correctness, data safety, concurrency, lifecycle, error handling, security, performance, structure, tests and release safety, recording it in the docs/audit/code ledgers. It changes no code; it ends by putting every action needed to resolve its findings into GitHub issues, the tracker and the roadmap. Run it only when the owner types /code-audit.
disable-model-invocation: true
argument-hint: "[all | area names, e.g. medications build-release]"
---

# /code-audit: is the code sound?

The owner triggers this by hand. Arguments: `$ARGUMENTS`
- empty or `all`: every area in `docs/audit/areas.txt` and `docs/audit/code-areas.txt`;
- area names: those areas.

`/function-audit` asks whether each function works for a person. This asks whether the code underneath is correct, safe
and maintainable, including what no person can see yet: a race, a lost write on failure, a leak, a restore that trusts
its file, a gate that can't fail. The method is in `.claude/agents/code-auditor.md` and `docs/audit/README.md`, the same
every run. Any Claude skill, plugin or agent type that helps may be used, without asking, as long as it changes nothing.

**An audit finds and records. It fixes nothing (owner, 2026-10-30).** No code, test, resource or build file changes
during an audit, not even a one-line fix. Don't run a tool in a mode that applies changes (`simplify`,
`code-review --fix`). Fixes are built later, from the issues it files. The audit isn't finished until step 5 is done.

## Rules that make it reliable

- **Coverage is counted, not assumed.** `python .github/scripts/audit_coverage.py --code-ledgers` must pass at the end:
  every source, test and build file is in an area, each area's ledger lists it as read in full, and every `Defect`,
  `Risk` and `Improve` row names its issue.
- **Budget shrinks what's audited, never how.** If the usage won't cover everything, audit fewer areas properly.
  Whatever wasn't done is reported as not done. A partial audit is never reported as an audit.
- **Say what's known.** `Verified` needs a test that would fail if the code broke. `Read OK` is not proof.
- **Audit blind, then calibrate.** Auditors don't see open issues. Afterwards, check whether they independently found
  the code defects known at the time.

## Steps

1. **Coverage.** Run the coverage script without flags. Add any new source file to `docs/audit/areas.txt`.
2. **Audit.** Launch one `code-auditor` per chosen area, in parallel, with its area name and file list. Give it the full
   model it needs, and don't give it the backlog. Each writes `docs/audit/code/<area>.md` and returns its findings.
3. **Check the auditors.** Have a fresh agent, also changing no code, re-check a sample of `Read OK` rows in the riskiest
   code (migrations, restores, imports, medication records, anything that deletes) plus every surprising `Defect`. A wrong
   `Read OK` means that area is redone.
4. **Calibrate.** Compare the findings with the known defects and record the catch rate in `docs/audit/README.md`.
   For any miss, say why and change the method.
5. **Populate the backlog (required).** This session does it once all areas are in, so a defect shared across areas
   gets one issue:
   - Match each `Defect`, `Risk` and `Improve` against existing issues (`gh issue list --state all`).
   - **File an issue for each new one, holding every action needed to resolve it:** what goes wrong and when, `file:line`,
     the sibling places to sweep, the proposed change, the test that must fail before it and pass after, and acceptance
     criteria. Label `bug` for a defect and `enhancement` for the others, `area:`, a priority, and `ready`. Group rows only
     when one change resolves them all.
   - **Import each into the tracker** (`tracker_import`, external id `Flameingskull/FlexNotes#N`), and set its status,
     priority, `area` and `githubIssue` as `CLAUDE.md` maps them. Add it to the tracker plan item's `dependsOn` (tracker
     ids, keeping the existing ones) and update its `progress`.
   - **Add each to the roadmap** (`docs/plans/2026-10-24-roadmap.md`): the Issue map, a build stage in the Builds table
     (data-loss, security and medication defects early, grouped by the files they touch), and the Status block's "Left"
     and "Next", so a plain `/safe-build` picks them up.
   - Write each issue number into its ledger rows.
   - Run the tracker parity check from `CLAUDE.md`, then `audit_coverage.py --code-ledgers`. Both must pass.
   - After the first full run, add `--code-ledgers` to the coverage step in `.github/workflows/build.yml` and `pr.yml`
     as an edit for the next build, so a new file can't escape it.
6. **Report.** Give the owner the coverage, the results by count, the catch rate, the serious findings in plain words,
   what was filed and which build stages it went to, and what wasn't reached. Commit the ledgers and roadmap changes
   locally through the commit proposal, without pushing, because a push to `main` makes a release.
