---
name: new-build
description: Owner-triggered FlexNotes build. The bug-manager and feature-manager agents triage the GitHub bug and feature lists, the chosen items are implemented, and a new signed release is published. Run it only when the owner types /new-build.
disable-model-invocation: true
argument-hint: "[auto | issue numbers, e.g. 12 15 | resume]"
---

# /new-build: backlog to release

The owner triggers this by hand. It builds everything chosen, until it's done or the owner stops it. Arguments:
`$ARGUMENTS`
- empty: triage, show the plan, and ask the owner once which items to include;
- `auto`: build every item the agents mark `ready`, without asking;
- issue numbers (e.g. `12 15`): build exactly those issues, without asking;
- `resume`: carry on from the newest `state.md` (`build-core`, "The build folder and its state").

**First read `.claude/skills/build-core/SKILL.md` in full.** It holds the owner imperatives, the rules, the jobs and
their briefs, and every step. This file only says what `/new-build` does at each one.

| Core step | In `/new-build` |
| --- | --- |
| 1 Preflight | As written |
| 2 Triage | As written |
| 3 Choose | No arguments: one multi-select question listing the recommended items (each with its size and a summary), "Everything recommended" first. `auto` or issue numbers: no question |
| 4 Implement | As written, every chosen item |
| 5 Review | As written, on the whole build (`--base origin/main`) |
| 6 Gate | `build-audit` with `new`, once, on the whole build. If a full `/function-audit` is due (rule 8), run `/function-audit all` first |
| 7 Notes and commit | As written |
| 8 Check branch | Once, before the push. Up to 3 attempts across steps 8 and 9, then stop and report the errors |
| 9 Push and watch | As written |
| 10 Close out | As written |
| 11 Report | As written |

**Checkpoints** (rewrite `state.md`): after the choice, after each item's review is accepted, after the gate, after
the push.

**Budget:** none added. If a usage limit interrupts the build, it carries on from `state.md` with `/new-build resume`,
in a fresh session if more than an hour has passed.
