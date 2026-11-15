---
name: slice-build
description: Owner-triggered FlexNotes build that ships exactly one small item or one complete slice of an issue, with no backlog agents (only the audit gate's read-only auditors) and a hard size cap. The single-slice mode that /safe-build used to be. Run it only when the owner types /slice-build.
disable-model-invocation: true
argument-hint: "[auto | one issue number, e.g. 23 | resume]"
---

# /slice-build: one slice, one small release

The owner triggers this by hand when they want the smallest safe release: one item, or one slice of an issue. For the
largest build that fits within the usage limits, use `/safe-build` instead. Arguments: `$ARGUMENTS`
- empty: pick the best-sized item, and ask the owner once to confirm it or name another;
- `auto`: pick it without asking;
- one issue number: build that issue. If it's too big for one safe build, build a slice of it (step 3);
- `resume`: carry on from the newest `state.md` (`build-core`, "The build folder and its state").

It has the same release standard as `/new-build` and `/safe-build`: an update that installs over the app, a signed
release with professionally written notes, and the brand look. It does less work to get there.

**First read `.claude/skills/build-core/SKILL.md` in full.** It holds the owner imperatives, the rules, the job briefs
and every step. This file says what `/slice-build` changes, and what keeps it small.

## What keeps it small

- **No backlog agents.** Don't launch `bug-manager` or `feature-manager`: the main session triages from the labels as
  they are, designs, writes the orders and reviews. The exceptions are the audit gate's read-only `function-auditor`
  and `code-auditor` (someone other than the author has to check) and the **Clerk** job for the tracker worklists. The
  coder isn't a Claude agent, so it doesn't count.
- **One item per build:** one issue, or one slice of an issue. A slice is a complete, working piece that users can see
  or that removes a real risk. It's never a half-built feature behind a switch.
- **Size cap.** About 6 files changed and 400 lines added, no new Gradle dependency, no new CI step, and no database
  schema change unless the item can't work without one. If the item needs more, slice it or choose another.
- **Leave room when choosing.** Choose at about 4 files and 250 lines, preferring items in one or two audit areas
  (`docs/audit/areas.txt`), so the driving test and a fix round still fit the cap.
- **Few orders.** At most 3 work orders, test included (`local-coder`, "Size"), plus at most one correction each.
  An item that needs more is sliced smaller.
- **One fix round.** If the gate's fix would break the cap, or the item's main function is still broken after it, the
  item is held back (`build-audit`, step 5); anything else left over is filed and the slice ships.
- **Read only what you need.** Start from `docs/CODEMAP.md`'s index, open the one part of `docs/codemap/` the item is
  in, then `grep` for the exact code and read only those parts.
- **Two CI attempts**, counting a failed check run as one. If the second fails too, stop and report the errors.
- **Leave a clean stopping point.** If the budget runs low before pushing, stop with the work committed locally (not
  pushed) and `state.md` written, and tell the owner what's left. Never push a change that hasn't been reviewed and
  gated.

## Steps

| Core step | In `/slice-build` |
| --- | --- |
| 1 Preflight | As written |
| 2 Triage | **No Triage jobs.** Use `backlog.md` as it is; don't re-triage. Mention any `needs-triage` issues in the report so the next `/new-build` handles them |
| 3 Choose | Prefer, in order: a `priority: high` bug; a `ready` item that unblocks the most others (`tracker_ready`); then any `ready` or `triaged` high-priority item. Read the chosen issue's body as it is now, then `grep` the code it touches to size it against the room left (about 4 files, 250 lines). Too big: pick a slice that closes one or more acceptance criteria and write the rest down as deferred; a slice uses `Refs #N` and gets a progress comment, and `Closes` / `Fixes` only when every criterion is met. No arguments: ask once, recommending the item or slice plus one or two alternatives with sizes. `auto` or a number: no question. The Clerk (parity) job runs as written |
| 4 Implement | **The main session is the Design and the Review job.** Write `<issue>.design.md` and the orders yourself (`local-coder` template and size), run them as one queue (`run-orders.ps1 -Dir <folder> -Item <issue>`), and review each order against its own patch, with at most one correction each. Put standing rule 1 in every order it applies to. File any significant gap you find (rule 5); don't fix it here |
| 5 Review | As written. Don't write in `docs/audit/`: the gate does that |
| 6 Gate | `build-audit` with `slice`, once. The main session writes the fix orders. A full `/function-audit` that's due (rule 8) is never run from here: say so in the report, so the owner can run it |
| 7 Notes and commit | As written. A due README refresh changes only the sections that are out of date since the last one |
| 8 Check branch | Once. A failed check uses one of the two CI attempts |
| 9 Push and watch | As written |
| 10 Close out | As written. For a slice, post the progress comment and leave the tracker item open |
| 11 Report | As written, plus any `needs-triage` issues waiting for the next `/new-build` |

**Checkpoints** (rewrite `state.md`): after the choice, after the review, after the gate.
