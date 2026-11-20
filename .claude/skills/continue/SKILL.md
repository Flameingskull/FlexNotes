---
name: continue
description: Owner-triggered. Picks a FlexNotes build back up after a usage limit stopped it and it couldn't carry on straight away when the limit reset. Puts the local coder back on the GPU (the model server often updates itself during the wait), finds where the build stopped, redoes any job the limit cut off, and carries on from there. Run it only when the owner types /continue.
disable-model-invocation: true
---

# /continue: carry on after a usage limit

The owner types this when a usage limit cut a build off and the work couldn't continue the moment the limit reset
(the owner's request). By then hours or days may have passed: the model server may have updated itself and put the
model on the CPU, background jobs may have died without writing their files, and issues may have been edited on
GitHub. This command checks each of these, then carries on with the build's own `resume`. It takes no input: when
the build stopped and what was running come from the build folder and git, never from the owner.

It works in the session that was cut off or in a fresh one. **Prefer a fresh one** (`/clear`, then `/continue`): after
a pause of more than an hour the old session's cache is gone, so every call re-pays the whole conversation
(`build-core`, "The build folder and its state"). Follow `CLAUDE.md` and `.claude/skills/build-core/SKILL.md`
throughout; this file only adds the checks a long pause needs.

## 1. Find the build

- The newest folder in `nimbalyst-local/work-orders/` whose `state.md` isn't finished (its "Next step" isn't step 11
  done). No unfinished `state.md`: say so, and stop. Don't start a new build: the owner
  chooses one with `/new-build`, `/safe-build` or `/slice-build`.
- Read `state.md` in full: the command (`/safe-build`, `/new-build` or `/slice-build`, and its arguments), the base
  ref, the items and their status, and "Next step".
- `git status` and `git log origin/main..HEAD`: the tree must match what `state.md` says (committed stages, files from
  an order that was running). Anything else uncommitted: stop and show the owner. `git fetch` and check whether
  `main` moved; if it did, say so before going on.

## 2. Put the coder back on the GPU

Always, however short the pause seemed. Updates during the wait are the norm, not the exception.

- `pwsh -NoProfile -File .claude/scripts/coder-preflight.ps1`.
- `GPU OK`: carry on. `NO MODEL`: ask the owner. `ON CPU`: follow `.claude/skills/local-coder/preflight.md`, "Not
  on the GPU". Two ways it has failed after an update (both in `nimbalyst-local/coder-gpu.md`):
  - the engine can't see the GPU (`--list-devices` shows `(none)`): its CUDA runtime files are missing; copy them
    as recorded, then reload;
  - the engine sees the GPU but the update's reload put the model on the CPU anyway, often at a different context:
    reload it.
- **`GPU OK` isn't the whole check.** An update can also reload the model on the GPU at a different context or with
  the default KV cache. Compare the context the preflight prints, and the `--cache-type-k` in the server's command
  line, with `coder-gpu.md`; if either differs, reload as below.
- The reload is always the owner's model, unchanged: the model, quantisation, context and KV cache recorded in
  `nimbalyst-local/coder-gpu.md` and `state.md`, never what the update chose. Run the preflight again; it must say
  `GPU OK (checked)`. Record what was fixed in `coder-gpu.md`, and the coder line in `state.md`.

## 3. Check what the limit cut off

A background job or coder run that was going when the limit hit may have finished, half finished, or left nothing.
Check each one's file in the build folder, never what the old session believed:

| Was running | Check | If it's missing or partial |
| --- | --- | --- |
| A Triage, Design, Review or Fix job | its file (`triage-*.md`, `<N>.design.md` and its orders, `<N>.review.md`, gate fix orders) is there and complete (every heading filled, every order its "Done when") | Launch it again as a **fresh** job from its brief in `build-core`, saying that a first attempt was cut off and to write its files as it goes. Never wake the old agent with `SendMessage`: its cache is long gone. Keep what a partial job left only if it's complete in itself |
| A coder queue (`run-orders.ps1`) | `<item>.run.md`, each order's `.patch` and `.result.md`. The coder is local, so the limit doesn't stop it: the queue has often finished | Finished: go on to its Review job. Stopped mid-order: look at that order's patch and the working tree before running it again, as after an exit 2 |
| A gate (`build-audit`) | its `gate-<stage>/` findings files and the ledger rows | Missing auditors are launched fresh from the pack, the same auditors and brief. Still one gate: a gate redone because it was cut off isn't a second audit |
| A Clerk job | its worklist's checkboxes, and whether `backlog.py --stamp` was recorded | Launch the Clerk again on the same worklist; it skips lines already done |
| A CI watch (`watch-ci.ps1`) | `gh run list` for the check branch or `main`, and the `ci-logs` branch | Read the run's result instead of watching again; a red run is handled as `build-core` step 8 or 9 says |

A job that was cut off and launched again doesn't count as a second pass under "One pass, no loops" (`CLAUDE.md`
rule 8): it's the same pass, finished.

## 4. Check what changed on GitHub

- `python -I .claude/scripts/backlog.py --out <folder>`. An issue in this build that was edited during the pause goes
  back to its owner's Design or Review job as it is now (parity by content, `docs/process/tracker.md`). New or edited
  issues outside the build wait for the next one, except a `priority: high` bug, which is told to the owner.
- If a release went out during the pause (`gh release list --limit 1`), this build's version moves up: say so and
  use the new number for the release notes and the check branch.

## 5. Carry on

- Rewrite `state.md`: the coder line, what was redone, and "Next step".
- Tell the owner in one or two lines: what was fixed (the GPU, any job redone), and the step the build continues at.
- Then carry on exactly as the build's own command does with `resume` (`/safe-build resume`, `/new-build resume` or
  `/slice-build resume`), from "Next step", with that command's budget rules. For `/safe-build`, size what's left
  against the usage now available: a stretch stage not yet started is re-checked against the budget, as at any stage
  checkpoint.
