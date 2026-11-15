---
name: safe-build
description: Owner-triggered FlexNotes build that works exactly like /new-build (agent triage, owner's choice, agent implementation, review, signed release) but keeps the usage limits in mind. It sizes the build to the usage left, commits in checkpointed stages and stops cleanly instead of running until told to stop. Run it only when the owner types /safe-build.
disable-model-invocation: true
argument-hint: "[auto | issue numbers, e.g. 23 38 | resume]"
---

# /safe-build: /new-build within the usage limits

The owner triggers this by hand. It is **`/new-build` step for step**: same arguments, same triage, same choice, same
agents, same review, same release standard. The only difference is that it fits the build to the usage available,
checks the budget at every stage, and always finishes with a release (or a clean stop) before the limit. It aims for
the largest high-quality build the budget allows, not the smallest one.

Arguments: `$ARGUMENTS`
- empty: triage, show the plan, and ask the owner once which items to include;
- `auto`: build every item the agents mark `ready` that fits the budget, without asking;
- issue numbers (e.g. `12 15`): build exactly those issues, without asking (sliced into stages if they won't all fit);
- `resume`: carry on from the newest `state.md` (`build-core`, "The build folder and its state");
- anything else (e.g. `the roadmap`): a pointer to what to prefer. Plan from it, then carry on as with no arguments.

**First read `.claude/skills/build-core/SKILL.md` in full.** It holds the owner imperatives, the rules, the jobs and
their briefs, and every step. This file says what `/safe-build` changes, and its budget rules.

## Budget rules (what makes this different from `/new-build`)

- **Use the real figures.** Before sizing, read the last builds' reports in `nimbalyst-local/usage/` (written by
  `build-usage.py`): what a Design, a Review and a gate actually cost, per item of that size. Until the new workflow
  has a report of its own, the old ones are an upper bound: 21–33M weighted per build, with the two auditors about 5M.
  Weighted = input + 0.1 x cache read + 1.25 x cache write + 5 x output.
- **Size the build to the budget.** Plan the chosen items as **stages** in order of value: **core** (about two-thirds,
  what must ship) and **stretch** (built only if the core went smoothly and the budget is still healthy). Each stage is
  a complete, working piece, never a half-built feature behind a switch. A database migration or new dependency goes in
  core.
- **The coder costs time, not usage.** What a stage costs in usage is its Design and Review jobs, plus any corrections.
  Precise orders are the saving: a vague order costs a correction, which costs more than writing it well.
- **Size each stage with its gate.** A stage is implementation + audit gate + one fix round. The gate has a floor
  whatever the size of the change, so a small stage can cost more to gate than to build: group small items that touch
  the same audit areas into one stage. This lowers how much fits in a build, on purpose.
- **Checkpoint every stage.** After a stage is implemented, reviewed and through its gate, commit it locally with its
  `Fixes` / `Closes` / `Refs` lines, push it to the check branch, and write `state.md`. A committed stage has always
  passed and can ship as it is. Then decide whether the next stage still fits: a stretch stage starts only if the
  budget covers all three of its parts. If it doesn't, stop adding stages and go to release with what's committed.
- **Cut stages, never the gate.** A stage that can't be gated doesn't ship. Auditors are never downgraded.
- **Keep a reserve.** Hold about a quarter of the budget for the whole-build review, release notes, README and code
  map, and check-branch or CI fixes. Never spend the reserve on extra features.
- **Partly done issues** use `Refs #N` and get a progress comment. They stay open, with their tracker item open too.
- **One push to `main`.** Stages are committed locally and pushed only to the check branch. One push is one release.
- **A pause over an hour resumes in a fresh session** (`/clear`, then `/safe-build resume`). At a stage checkpoint
  with no pause, carry on in the same session.

## Steps

| Core step | In `/safe-build` |
| --- | --- |
| 1 Preflight | As written, and read the usage reports for sizing |
| 2 Triage | As written; the Triage jobs also give a rough size per item (files, lines, orders) |
| 3 Choose | Fit the recommendation to the budget, favouring stages and items that touch the same files. Lay it out as core and stretch stages, each with its issues, files and size, gate and one fix round included. No arguments: one multi-select question (size, stage and summary per item), "Everything recommended" first; if the owner chooses more than the budget allows, say which become stretch. `auto` or issue numbers: no question |
| 4 Implement | Stage by stage. One queue per item, or one per stage with `-Queue` |
| 5 Review | Per stage, against the stage's base ref, while it's fresh. Before the push, once more on the whole build (`--base origin/main`) only where stages touched the same files |
| 6 Gate | `build-audit` with `safe` and the stage's base ref, **once per stage, with fresh auditors each time** and its own folder (`gate-<stage>/`). If a full `/function-audit` is due (rule 8), run `/function-audit all` first |
| 7 Notes and commit | Each stage is committed as it passes its gate. Release notes, README and roadmap are one last commit, from the reserve |
| 8 Check branch | Each stage's commit is pushed to the check branch as it lands, with `watch-ci.ps1 -Check` in the background, so errors surface while the next stage is designed. The final commit is checked too. Up to 3 attempts across steps 8 and 9, or fewer if the reserve runs out; then stop and report the errors |
| 9 Push and watch | As written, once |
| 10 Close out | As written; partly done issues get their progress comment |
| 11 Report | As written, plus which stretch stages were built or skipped and why |

**Checkpoints** (rewrite `state.md`): after the choice and after each committed stage.
