# The Nimbalyst tracker: field mapping and the parity check

The procedure behind "Nimbalyst tracker" in `CLAUDE.md`, moved here on 2026-11-14 so it loads only for whoever is
doing the tracker work (in a build, the Clerk job, from the worklist `backlog.py` writes). GitHub issues are the source
of truth; the tracker is a read-model plus a dependency graph.

Every open issue has exactly one tracker item, imported through the `github-issues` importer so it carries an
`origin` back-link (`tracker_import`, provider `github-issues`, external id `Flameingskull/FlexNotes#N`; items imported before the 1.0.124 rename carry `Flameingskull/FitLens#N`,
so check for an existing item by issue number before importing again). Re-importing
an issue is safe: it returns the existing item instead of duplicating it.

Field mapping, kept in step by whichever agent changed the labels:

| Tracker field | From the issue |
| --- | --- |
| type | `bug` label becomes `bug`, anything else becomes `feature` |
| status | `ready`, `needs-info` or `triaged` as labelled, otherwise `needs-triage` |
| priority | the `priority: high\|medium\|low` label |
| `area` | every `area: X` label |
| `githubIssue` | the issue number |
| tags | the `epic` and `accessibility` labels |
| `dependsOn` | each `Depends on #N` line in the body, counting open issues only |

Two traps worth remembering:

- `dependsOn` takes tracker item **ids** (`import_...`), not `FIL.n` keys. A key is accepted and stored, but never
  resolves, so the item silently looks unblocked.
- `FIL.n` keys are local to this machine and are **not** issue keys. Never put one in a commit message, an issue, a
  release note or anything else another person reads. Refer to work by its GitHub number.

Keeping it in step: after changing an issue's labels, make its item agree; after filing an issue, import it; when an
issue closes, set the item to `shipped` (features) or `done` (bugs). `tracker_ready` then answers "what can we start
now", ranked by how much each item unblocks.

**Parity rule (owner, 2026-10-22): the tracker and GitHub issues must always match.** Every open issue has exactly
one tracker item, and every open tracker bug or feature has an open issue: nothing lost, nothing duplicated. Each build
skill (`/new-build`, `/safe-build`, `/slice-build`) runs the parity check at its start and again before its push.

**Parity means content, not numbers (owner, 2026-11-13).** Issues are edited directly on GitHub (labels, priority, titles,
bodies, `Depends on` lines, closures), so matching issue numbers or counts proves nothing. Never call the two in parity
from a count or a list of numbers. The check, kept cheap by looking only at what changed:
1. Read the last check's time from `nimbalyst-local/parity-last.txt` (local only; if missing, check every open issue).
2. Fetch every issue updated since then, open or closed:
   `gh issue list --state all --limit 300 --search "updated:>=<time>" --json number,title,state,labels,body,updatedAt`.
   Also list open issue numbers and open tracker `bug`/`feature` items, to catch anything missing or tracker-only.
3. For each fetched issue, compare its item field by field with the mapping table above: type, status, priority, `area`,
   tags, title, `githubIssue`, and `dependsOn` from its `Depends on #N` lines (tracker ids, open issues only). Update
   every field that differs. A closed issue sets its item to `shipped` (feature) or `done` (bug).
4. Import any open issue without an item (match by issue number, either repo name), archive duplicates (keep the item
   whose origin is `Flameingskull/FlexNotes#N`), and file an issue (rule 5) for any tracker-only bug or feature.
5. An edited body or acceptance criteria on an issue in the build, or one the roadmap maps, goes to the owning agent
   for re-triage before it's worked on: the build works from the issue as it is now.
6. Write the check's start time to `nimbalyst-local/parity-last.txt`, and report what was compared: how many issues
   were updated, how many items changed, and which.

## In a build: the script and the Clerk

`python -I .claude/scripts/backlog.py --out <build folder>` does steps 1 and 2 with no Claude usage, and writes:
- `backlog.md`: every open issue on one line, with its roadmap stage and whether it was edited since the last check;
- `clerk-worklist.md`: for each changed issue, the fields its item must have (step 3), new issues to import and closed
  ones to close (step 4), the tracker-only check, and the command that records the check (step 6).

A **Clerk** job (`.claude/skills/build-core/SKILL.md`, "Jobs") carries the worklist out and returns a checklist. Step 5
stays with the main session: `backlog.py` prints which issues changed, and one that is in the build goes back to its
owner before it is worked on. Parity is still by content: the worklist is the comparison, and a count is never proof.

## Traps

- `tracker_update`'s `tags` replaces the list: pass the item's existing `epic` / `accessibility` tags back, or they are
  wiped. `labels` behaves the same way.
- A bug item's `area` is an array (`["ui"]`); set a feature's the same way, as an array of the label suffixes.
- A newly imported item starts as `to-do` with no `githubIssue` or `area`: set its status and both fields straight
  after importing (`fields`).
- `tracker_import` needs the full external id, `Flameingskull/FlexNotes#N`; a bare number fails.
- Listing the whole tracker with `full: true` returns 50 to 130KB. Query one issue at a time by `githubIssue`.
