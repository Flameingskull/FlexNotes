# Keeping plans

The procedure behind "Plans" in `CLAUDE.md`, moved here on 2026-11-14 so it loads only when a plan is being made or
updated. `CLAUDE.md` keeps the rules; this file is how to carry them out.

Every multi-build plan is kept in **two places**, so nothing in it slips:
- **The plan file, in git:** `docs/plans/YYYY-MM-DD-<name>.md`. It starts with a `> **Status**` block: what's done,
  what's left (by GitHub issue number), the next command and its tracker item.
- **A Nimbalyst tracker item of type `plan`** with the same title, its description pointing at the file, `progress`
  set, and `dependsOn` = the tracker ids (`import_...`) of every issue the plan still needs.

Rules:
- **At the start of every session, and before any build,** read every plan in `docs/plans/` and the open `plan` items
  in the tracker. Continue an unfinished plan unless the owner asks for something else.
- **A new plan** (from plan mode, chat or a skill) is saved to `docs/plans/` straight away, even if it was first
  written to `nimbalyst-local/plans/`; every requirement in it becomes a GitHub issue (or is mapped to an existing
  one), each issue is imported into the tracker, and the plan item is created with them as `dependsOn`. A stray copy
  left in `nimbalyst-local/plans/` is replaced by a one-line pointer to the file in `docs/plans/`.
- **After every build that advances a plan,** update its Status block, the plan item's `progress` and description,
  and drop shipped issues from what's left. Commit the plan file with the build.
- **A plan is finished** only when every issue it depends on has shipped: then set the Status block to `Done` and the
  plan item to `completed`. Never mark it done while an item is still open; move anything deferred into a new issue
  and keep it in `dependsOn`.

## Where finished plans go

A plan whose Status block says `Done` (or `Superseded`) moves to `docs/plans/archive/`, unchanged. The roadmap never
moves, but what it has shipped does: when a stage ships, its Builds row, its Issue map rows and its spec move, word for
word, to `docs/plans/archive/2026-10-24-roadmap-history.md`, and the roadmap's Status block is rewritten to at most 15
lines: the last green release, the next stage, what's left, what only the owner can do, and the next command. It
holds no build history.
