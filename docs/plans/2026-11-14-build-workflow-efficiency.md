# Build workflow: same workflows, far fewer Claude tokens

> **Status (2026-11-20): In progress.** Owner-approved 2026-11-14. GitHub issue #388; tracker plan item of the same
> title. Committed locally; it rides with the next build's push (D4).
> Done (steps 1–5 of "Order of work"): the seven scripts in `.claude/scripts/`, each checked as "Verification" says;
> `build-core` with `/new-build`, `/safe-build` and `/slice-build` as step tables over it; the `local-coder` split
> (`preflight.md`) with the one size rule; the gate from a pack with fresh auditors; a Jobs section in all four agent
> files; the code map as an index plus `docs/codemap/`; the roadmap's Status block at 15 lines with its history in
> `docs/plans/archive/`; `docs/process/tracker.md` and `plans.md`; agent notes pruned, with `coder-orders.md`.
> Short of target: `CLAUDE.md` is 27KB, not about 20KB (what's left is rules and owner decisions, which stay);
> `feature-manager` notes are 175 lines, not about 150.
> Live test, `/safe-build` 1.0.205 (2026-11-15 to 11-20, awaiting CI): it ran on the new workflow through five usage
> limits; the owner added `/continue` for those. Fixed on the way: `run-orders.ps1`'s checklist check. Seen: the coder
> often omits its REPORT block (the queue stops on a correct order), and agents' file writes in the gate are refused.
> Left (step 6): a `/slice-build` live test; `build-usage.py` compared with the table below; the owner's plugin step.
> Next: the owner runs `/slice-build`. #388 closes when both test builds have run on the new workflow.

## Context

The owner asked for practical changes to the build commands, agents and workflows that save Claude usage **only where
that also reduces errors, miscommunication and failed delivery**, for **every build command in its own scope**
(`/new-build`, `/safe-build`, `/slice-build`, and the `build-audit` gate and `local-coder` loop they share). The agents'
purposes stay: managers own their lists, design, write work orders and review; auditors check blind and fix nothing;
the local coder writes all code.

### What the transcripts show

Measured from the last four build sessions' transcripts (all `/safe-build`; no `/new-build` or `/slice-build` has run
since the gate and coder arrived, so those two are covered by changing the pieces all three share). "Weighted" is the
formula already in `/safe-build`: input + 0.1 x cache read + 1.25 x cache write + 5 x output.

| Build | Total weighted | Main session | Main calls / peak context | Lost to re-caching |
| --- | --- | --- | --- | --- |
| 1.0.199 | 21.1M | 8.7M | 215 / 469K | 5.1M (24%) |
| 1.0.200–201 | 33.2M | 16.0M | 363 / 540K | 10.6M (32%) |
| 1.0.202 | 26.0M | 14.2M | 332 / 595K | 4.7M (18%) |
| 1.0.204 | 21.8M | 8.4M | 245 / 407K | 4.8M (22%) |

For scale: builds before the gate and the coder (to 2026-10-29) cost 1–7M each.

1. **Waking an idle agent re-pays its whole context.** A sub-agent's cache lasts about 5 minutes, the main session's
   about an hour. Every one of the 70 sub-agent wakes after a 6-minute-plus gap was a full re-cache at 1.25x, and so
   was every main-session resume after 75 minutes (19 of 19). The skills say "continue the same agents with
   `SendMessage` so they keep what they've read": with managers at 300–500K, that is the most expensive instruction in
   the workflow. A fresh agent starts at 48K.
2. **Cost is calls x context, and both grow together.** Managers made 80–150 calls each while growing to 300–500K. The
   main session made 245–420 calls while growing to 400–600K, mostly mechanical: 38–42 coder runs relayed one by one,
   27–58 `SendMessage` relays, 8–37 `gh run` polls at peak context, 25–50 tracker calls.
3. **Every agent starts at 48K (main 63–66K) before reading anything:** `CLAUDE.md` (30KB), the skill list (many
   plugins unrelated to an Android app), tool schemas. Every call re-reads it.
4. **Large documents are read whole or in big parts each build:** the roadmap (70KB, of which the Status block alone
   is 26KB of build history), `feature-manager` notes (34KB), the code map (141KB).
5. **Drift between copies causes errors:** the three build skills repeat about 60% of their text; the README cadence
   numbers live in four files; the order-size rule in `local-coder` (3 files, 150 lines) contradicts the owner's later
   rule (2026-11-06: one edit in one file, about 2,500 characters), which lives only in memory, where a fresh agent
   never sees it.
6. **CI failures are found on `main`,** where each one burns a release number and a fix loop at peak context.

## What does not change

- The four agents, their ownership and their rules; the blind audit; "auditors fix nothing"; "one pass, no loops";
  the gate is never cut or downgraded; the coder writes all code, one order at a time; every owner imperative.
- `local-coder.ps1` and the single-order path stay as they are (the queue below wraps it).
- What each command is for: `/new-build` builds everything chosen until done; `/safe-build` is the same flow sized to
  the usage in gated, committed stages; `/slice-build` ships one small slice with no backlog agents.

## Decisions taken as defaults (say if any should differ)

| # | Decision | Default in this plan |
| --- | --- | --- |
| D1 | Who writes new scripts in `.claude/scripts/` | **Claude**, as with `local-coder.ps1`: the coder is denied `.claude/`, and nothing there is compiled or packaged. Rule 9 gets one line saying so. If no: they move to a folder the coder may edit and are built from work orders. |
| D2 | How an item's orders run | **Queued per item, reviewed once per item**, each order still judged against its own diff. The coder still runs one order at a time. If no: keep review after every order; the other savings still apply. |
| D3 | Fresh session from a saved state file | **Required after any pause over an hour** (the owner types `/clear`, then `/<build> resume`). At stage or item checkpoints the build carries on in the same session unless an automatic hand-off to a new session proves to work (tested in step 6). |
| D4 | Tracking | One plan file in `docs/plans/`, one GitHub issue, one tracker plan item. Commits stay local and ride with the next build, since every push to `main` is a release. |

## The changes

### A. Agents do one job, then end. Hand-offs live in files. (largest saving, est. 25–35%)

Replace "continue with `SendMessage`" with **jobs**. Same agent types, same ownership; each launch does one job from a
fixed brief template and ends. Everything the next job needs is written to the build folder
(`nimbalyst-local/work-orders/<version>/`), so nothing is lost to a limit, a crash or a re-read.

| Job | Agent | Reads | Writes |
| --- | --- | --- | --- |
| Triage | `bug-manager`, `feature-manager` (in parallel, `model: sonnet`) | `backlog.md` (B4), open roadmap stages | `triage-<list>.md`: the prioritised table, with roadmap stage and number of orders |
| Design | the owning manager, one per item (items sharing a file go to one job) | the issue as it is now, its area of the code map, its notes | the orders, and `<issue>.design.md`: decisions, files and lines, pattern, traps, the proving test, `Fixes` line, release-note lines, phone test tips |
| Review | the owning manager, fresh | `<issue>.design.md`, the orders, each order's patch (B1) | `<issue>.review.md`: a verdict per order, at most one correction order per order, or a filled `failure-issue.md` draft |
| Gate | `function-auditor`, `code-auditor`, fresh for every gate | `gate/pack.md` (B5) only | ledger rows, findings |
| Fix | the owning manager, fresh | its findings, `<issue>.design.md` | fix orders; then a Review job, once |
| Clerk | `general-purpose`, small model | a worklist (B4) | tracker parity, imports and close-out; a done/failed checklist |

Rules that go with it:
- Every job returns at most 12 lines plus file paths. The main session never reads a manager's diffs or pastes reports.
- `SendMessage` only for an immediate follow-up (a missing field), within 4 minutes of the agent's last output.
- Fresh auditors for every `/safe-build` stage (reversing "the same auditors continue"): cheaper, and more independent.
- **Build state:** the main session writes `state.md` at each checkpoint (command and arguments, version, base ref,
  coder model and context, each item's status, `Fixes` lines, release-note lines, issues filed, next step). Each build
  command gains a `resume` argument that continues from it. This is also what stops a build recording work as done
  before it is (the 1.0.188–190 problem).

Error it prevents: reviews judged against what the author remembers instead of what the order says; context lost at a
limit; briefs that forget a field.

### B. Scripts for the mechanical loops (est. 10–15%; zero-token work)

All in `.claude/scripts/`, each printing at most about 15 lines and writing detail to a file.

1. **`run-orders.ps1`** – runs an item's orders in sequence through the existing `local-coder.ps1`, saves each order's
   own patch (`<order>.patch`), and **stops the item** at the first non-zero exit, stray edit, `Status` other than
   `done`, a "no" in the checklist, or an order that changed nothing. One background run and one notification per item
   (or per stage, skipping only the stopped item when file lists don't overlap), instead of one relay per order.
2. **`watch-ci.ps1`** – waits for the run in the background, then prints one verdict: on failure `status.txt` and
   `errors.txt` from `ci-logs`; on success the release, its four assets and the issues closed. Replaces 8–37 polls.
3. **`coder-preflight.ps1`** – model chosen and listed, server process id against `nimbalyst-local/coder-gpu.md`,
   GPU memory against process memory; prints `GPU OK (known)`, `GPU OK (checked)`, `ON CPU` or `NO MODEL`. Only
   `ON CPU` opens the manual fix steps.
4. **`backlog.py`** – one `gh` fetch of issues updated since `parity-last.txt` plus the open list; writes `backlog.md`
   (number, title, labels, priority, areas, `Depends on`, roadmap stage, "edited since last build") and the expected
   tracker fields per changed issue as the Clerk's worklist. Parity stays by content; the tracker is queried only for
   the issues listed, not dumped whole (50–126KB each time today).
5. **`gate-pack.ps1`** – writes `gate/pack.md`: version, base, changed and untracked files with their audit area, each
   item's title and acceptance criteria, the diff, and the existing ledger rows for those files. Both auditors start
   from the same pack, and it can't contain the coder's report, so the blind rule holds by construction.
6. **`diff-check.py`** – rule 1 and CI-killers checked mechanically before review: keystores and secrets,
   `applicationId`, signing, `BUILD_OFFSET`, `Color(0x`, stray `.sp`, `Db.VERSION` changed without a migration and
   schema, every `R.string.*` used exists, duplicate string names. The main session then reads full hunks only where
   this flags or where items overlap in a file, instead of the whole diff a fourth time.
7. **`build-usage.py`** – the measurement used above, per agent and phase, written to `nimbalyst-local/usage/` and
   quoted in the build report. `/safe-build`'s budget rules then cite the last builds' real figures.

### C. Compile on a pull request before `main` (fewer failed releases)

Standard in all three commands: push the build's commits to `check/<version>`, open a draft PR, let `pr.yml` compile
and run every test, and push `main` only when it's green; then close the PR and delete the branch. `pr.yml` doesn't
advance the release run number, so a failed check costs no version (1.0.203 was lost this way). In `/safe-build`, each
stage's local commit is pushed to the check branch as it lands, so errors surface while the next stage is designed.

### D. Slimmer always-loaded documents (est. 5–10%)

- **Roadmap:** Status block rewritten each build to at most 15 lines (last green release, next stage, what's left,
  owner-only items, next command). Build history, shipped Issue map rows and shipped specs move verbatim to
  `docs/plans/archive/`. Finished plans move there too.
- **Agent notes:** capped at about 150 lines each; order-writing lessons move to one shared
  `.claude/agent-notes/coder-orders.md` read by Design jobs; stale lines pruned.
- **Code map:** `docs/CODEMAP.md` becomes a short index (data flow, where things go) with the existing sections split
  as they stand into `docs/codemap/` (`data.md`, `ui.md`, `other.md`, `conventions.md`). Agents open one part.
- **`CLAUDE.md`:** every standing rule, owner decision and product direction stays. Step-by-step procedures only the
  main session needs move to `docs/process/` behind one-line pointers: the tracker field mapping and parity steps,
  the plan-keeping rules, "Making a new build". Target about 20KB.
- **Auditors:** `CLAUDE.md` is already in every agent's context, so "read `CLAUDE.md` first" becomes "it's loaded;
  don't re-read it".
- **Plugins (owner step):** run `/context` once; switch off, for this project, plugins no build uses (SEO, Qt, Figma,
  marketing, product-management, productivity, PDF). Keep `design`, `code-review`, `security-review`, `dataviz`.
  Estimated up to a fifth of every agent's 48K start; confirm with `/context` before and after.

### E. One build core, three thin commands (drift, not tokens)

- New `.claude/skills/build-core/SKILL.md` (not user-invocable): owner imperatives, preflight, the jobs and brief
  templates, the order loop, review, check branch, release notes, commit, watch, close-out, report.
- `/new-build`, `/safe-build`, `/slice-build` keep their arguments, their scope and budget rules, and a step table
  saying "core step N as written" or "core step N, with this change".
- Cadence numbers (README refresh, full audit due) live only in `CLAUDE.md` rules 7 and 8; skills point there.
- `local-coder`: `SKILL.md` keeps what Design and Review jobs need (template, size, review); the model and GPU check
  moves to `preflight.md`, read by the main session only. **The order-size rule becomes the owner's 2026-11-06 rule.**

## How each command gets it, in its own scope

| | `/new-build` | `/safe-build` | `/slice-build` |
| --- | --- | --- | --- |
| Scope (unchanged) | Everything chosen, until done | Same flow, sized to usage; core and stretch stages | One item or slice; about 6 files, 400 lines |
| Agents | Triage, Design, Review, Fix jobs; Clerk | Same | **Still no backlog agents.** Main designs, writes at most 3 orders and reviews. Clerk only for the tracker worklist |
| Orders | Queue per item | Queue per item, or per stage | One queue for the slice |
| Gate | One gate, fresh auditors, from the pack | One per stage, fresh auditors each time | One gate (its one agent exception, as now) |
| Checkpoint and `resume` | After the choice, each accepted item, the gate, the push | After the choice and each committed stage | After the choice, the review, the gate |
| Check branch | Once, before the push | Each stage commit | Once; a failed check uses one of its two CI attempts |
| Budget | None added | Budget rules kept, figures from `build-usage.py` | Size cap kept |
| Shared scripts | All | All | All except the stage queue |

The gate (`build-audit`) and the full audits keep their method. `/function-audit` and `/code-audit` change only where
they share a piece: the Clerk worklist for their filing step, and "don't re-read `CLAUDE.md`".

## Files

- **New:** `.claude/skills/build-core/SKILL.md`; `.claude/skills/local-coder/preflight.md`; the seven scripts in
  `.claude/scripts/`; `docs/process/tracker.md`, `docs/process/plans.md`; `docs/codemap/*.md`;
  `docs/plans/archive/`; `.claude/agent-notes/coder-orders.md`.
- **Edited:** `.claude/skills/{new-build,safe-build,slice-build,build-audit,local-coder}/SKILL.md`;
  `.claude/agents/{bug-manager,feature-manager,function-auditor,code-auditor}.md` (a Jobs section each; no change of
  purpose); `.claude/skills/{function-audit,code-audit}/SKILL.md` (two lines each); `CLAUDE.md`; `docs/CODEMAP.md`;
  `docs/plans/2026-10-24-roadmap.md`; both agent notes files; the memory entries this supersedes.
- **Untouched:** `local-coder.ps1`, `AGENTS.md`, `opencode.json`, `build.yml`, `pr.yml`, everything under `app/`.

## Order of work

1. Save this plan as `docs/plans/2026-11-14-build-workflow-efficiency.md` with a Status block; leave a pointer here;
   file the one issue; create the tracker plan item.
2. Scripts B1–B7 (D1), each checked as below.
3. `build-core`, the three thin commands, the `local-coder` split, `build-audit`, the four agent files (A, C, E).
4. Document diet (D), with `audit_coverage.py` run after the moves.
5. Commit locally through the commit proposal. No push.
6. The next `/slice-build` is the live test (smallest scope), then `/safe-build`. Test the automatic hand-off to a new
   session there (D3). Compare `build-usage.py` with the table above and adjust.

## Verification

- `build-usage.py` on session `46d8482a` reproduces 21.8M weighted and 21 re-caches.
- `watch-ci.ps1` against the finished 1.0.204 run reports success, four assets and the issues closed.
- `backlog.py` lists every open issue once, and its worklist matches a hand check of three recently edited issues.
- `gate-pack.ps1` over `8a66dc5..6547a21` (1.0.204) lists the files the gate was given then, with no coder report.
- `diff-check.py` over the same range passes; a scratch diff with `Color(0xFF000000)` and a missing string id fails.
- `run-orders.ps1` stops on a deliberately malformed second order (exit 4) and leaves the first order's patch saved.
- `coder-preflight.ps1` prints one of its four verdicts and agrees with `studio_status`.
- `python .github/scripts/audit_coverage.py` still reports 0 problems.
- Searches: no skill or agent still says "continue the same agents"; the cadence versions appear only in `CLAUDE.md`;
  the order-size rule appears once.
- First real builds: no sub-agent re-caches in `build-usage.py`, main-session calls roughly halved, and the build's
  report states what each item's proof is, exactly as now.

## Not proposed

- Cheaper models for auditors, a smaller gate, or skipping reviews: the owner's rules forbid them and the data doesn't
  require them.
- Applying exact-text orders by script instead of the coder: rule 9 gives all code to the coder.
- A ledger-merge script for gate rows: only if the first usage report shows auditors still spend heavily on ledgers.
