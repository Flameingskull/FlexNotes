---
name: function-audit
description: Owner-triggered function audit of FlexNotes. One function-auditor agent per area reads every source file in full, follows every function to its result against the data matrix and the owner's imperatives, and records it in the docs/audit ledgers. It changes no app code; it ends by putting every action needed to resolve its findings into GitHub issues, the tracker and the roadmap. Run it when the owner types /function-audit or asks for an audit of working order; builds run it as /function-audit changed.
argument-hint: "[all | changed | area names, e.g. medications analysis]"
---

# /function-audit: does every function work?

Arguments: `$ARGUMENTS`
- empty or `all`: every area in `docs/audit/areas.txt`;
- `changed`: the areas holding files changed since the last audit, as a build runs it;
- area names: those areas.

An audit answers one question: does each function work, the way a person expects, with any data? The method is in
`.claude/agents/function-auditor.md` and `docs/audit/README.md`, the same every run, so that a result means the same
thing each time. Any Claude skill, plugin or agent type that helps may be used, without asking.

**An audit finds and records. It fixes nothing (owner, 2026-10-30).** No app code, test, resource or build file changes
during an audit, not even a one-line fix. Fixes are built later, from the issues it files, through `/safe-build` or
`/new-build`. The audit isn't finished until step 5 is done.

## Rules that make it reliable

- **Coverage is counted, not assumed.** `python .github/scripts/audit_coverage.py --ledgers` must pass at the end: every
  source file is in exactly one area, each area's ledger lists it as read in full, and every `Fails` and `Gap` row names
  its issue.
- **Budget shrinks what's audited, never how.** If the usage won't cover everything, audit fewer areas properly.
  Whatever wasn't done is reported as not done. A partial audit is never reported as an audit.
- **Say what's known.** Only a test that drives a function is proof. `Read OK` is not proof, and the report says how
  many functions rest on reading alone.
- **Audit blind, then calibrate.** Auditors don't see open issues. Afterwards, check whether they independently found
  the defects known at the time (recent owner reports, open bugs): what they missed shows what the method can't see, and
  the method is changed for that.

## Steps

1. **Coverage.** Run the coverage script without flags. Add any new source file to `docs/audit/areas.txt` first.
2. **Audit.** Launch one `function-auditor` per chosen area, in parallel, with its area name and file list. Give it the
   full model it needs, and don't give it the backlog. Each writes `docs/audit/<area>.md` and returns its findings.
3. **Check the auditors.** Have a fresh agent, also changing no code, re-check a sample of `Read OK` rows in the riskiest
   functions (medication records, data writes, imports, backups, figures people act on), plus every `Fails` that looks
   surprising. A wrong `Read OK` means that area is redone.
4. **Calibrate.** Compare the findings with the known defects and record the catch rate in `docs/audit/README.md`.
   For any miss, say why and change the method.
5. **Populate the backlog (required).** This session does it once all areas are in, so a defect shared across areas
   gets one issue:
   - Match each `Fails` and `Gap` against existing issues (`gh issue list --state all`).
   - **File an issue for each new one, holding every action needed to resolve it:** what a person sees, steps, wrong
     and expected result, `file:line`, the sibling places to sweep, the test that must drive it, and acceptance criteria.
     Label `bug` or `enhancement`, `area:`, a priority, and `ready`. Group rows only when one fix resolves them all.
   - **Import each into the tracker** (`tracker_import`, external id `Flameingskull/FlexNotes#N`), and set its status,
     priority, `area` and `githubIssue` as `CLAUDE.md` maps them. Add it to the tracker plan item's `dependsOn` (tracker
     ids, keeping the existing ones) and update its `progress`.
   - **Add each to the roadmap** (`docs/plans/2026-10-24-roadmap.md`): the Issue map, a build stage in the Builds table
     (serious and medication or data-loss defects early, grouped by the files they touch), and the Status block's
     "Left" and "Next", so a plain `/safe-build` picks them up.
   - Write each issue number into its ledger rows, and put functions that matter most and rest on reading alone on the
     function-test issue, so they end up `Proved`.
   - Run the tracker parity check from `CLAUDE.md`, then `audit_coverage.py --ledgers`. Both must pass.
   - After the first full run, add `--ledgers` to the coverage step in `.github/workflows/build.yml` and `pr.yml` as an
     edit for the next build, so a new file can't escape it, and set "Last full" in `CLAUDE.md` rule 8.
6. **Report.** Give the owner the coverage, the results by count, the catch rate, the serious findings in plain words,
   what was filed and which build stages it went to, and what remains unproved. Commit the ledgers and roadmap changes
   locally through the commit proposal, without pushing, because a push to `main` makes a release.
