# Audit ledgers

The ledgers record what's actually known, so "reviewed" always has a checkable meaning. Audits change no code, and
each ends by filing every finding as an issue, a tracker item and a roadmap stage.

- **Function audit**, `/function-audit` (method: `.claude/agents/function-auditor.md`): does each function work for a
  person? One ledger per area, `docs/audit/<area>.md`.
- **Code audit**, `/code-audit` (method: `.claude/agents/code-auditor.md`): is the code sound? One ledger per area,
  `docs/audit/code/<area>.md`, with two extra areas for tests and the build (`code-areas.txt`).

`areas.txt` lists which source file belongs to which area. `.github/scripts/audit_coverage.py` checks that every file
is in an area and read in full, and that every finding names its issue.

## Function audit

### Results

| Result | Means |
| --- | --- |
| `Proved` | A test drives the function and asserts the outcome. |
| `Read OK` | The whole path was read and looked right. Not run, so not proof. |
| `Fails` | It doesn't do what it should, with steps and the wrong result. |
| `Gap` | It breaks an owner imperative or instruction, or something asked for is missing. |
| `Not checked` | Not reached, or can't be judged without a device, and why. |

### Ledger format

```markdown
# Function audit: <area>

> Audited <YYYY-MM-DD> against <version>. Files read in full: <n> of <n>.
> Results: <n> Proved, <n> Read OK, <n> Fails, <n> Gap, <n> Not checked.

## Files

| File | Read in full |
| --- | --- |
| ui/Example.kt | Yes |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Copy workout to a date | Every set arrives on that date | WorkoutEditing.kt:120 | Read OK | Followed to Workouts.copyWorkout | |
```

A `File` row says `Yes` or `No (why)`, using the path relative to `app/src/main/java/com/fitlens/companion/`, with
forward slashes.

## Code audit

### Results

| Result | Means |
| --- | --- |
| `Verified` | A test exists that would fail if this broke. |
| `Read OK` | Read through with nothing wrong found. Not proof. |
| `Defect` | It's wrong, with the trigger and the bad result. |
| `Risk` | Not wrong today, but likely to fail under a named condition. |
| `Improve` | A more stable or maintainable way, with what it buys. |
| `Not checked` | Not reached, or needs a device, and why. |

### Ledger format

```markdown
# Code audit: <area>

> Audited <YYYY-MM-DD> against <version>. Files read in full: <n> of <n>.
> Results: <n> Verified, <n> Read OK, <n> Defect, <n> Risk, <n> Improve, <n> Not checked.

## Files

| File | Read in full |
| --- | --- |
| ui/Example.kt | Yes |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Workouts.copyWorkout | Data safety | Workouts.kt:120 | Read OK | One transaction, areas named | |
```

A `File` row uses the path from `areas.txt` or, for `tests` and `build-release`, from the repository root.

## Build gates

Every build runs a gate (`.claude/skills/build-audit/SKILL.md`) before it pushes: the same auditors and method, on only
the functions that build touched. So a ledger can hold partial rows:

- **Merge, never replace.** A gate adds or updates only the rows it checked, and keeps an existing `Yes` file row. A file
  it didn't read in full says `No (build gate: changed functions only)`. Only a full audit replaces a ledger.
- **Header.** Each gate adds `> Build gate <version>: <n> functions checked.`, and a ledger made by one starts with
  `> No full audit yet: rows from build gates only.` The `Results:` line counts every row in the ledger. A row in such a
  ledger says what was found about that function, and nothing about the rest of the area.
- **Independent.** Coders never write ledgers. Rows are written before CI has run the tests they name, and a failed run
  publishes nothing, so a green release is what confirms `Proved` and `Verified`.
- **Checks.** `audit_coverage.py --findings` runs in every build: each finding in any existing ledger names its issue, or
  is gone. It checks no file coverage, so partial ledgers pass it. They correctly keep failing `--ledgers` and
  `--code-ledgers` until a full audit replaces them.

## Calibration

Each audit records whether it independently found the defects known at the time, because an audit's only real measure
is what it catches.

| Audit | Known defects | Found blind | Missed, and why |
| --- | --- | --- | --- |
| 2026-10-30, first attempt, stopped by the owner | #327 to #330 | Not measured | All eight areas stopped at a usage limit with no ledger or finding recorded, so this was not an audit. Run it fresh. Ledgers need writing as an area goes, not only at the end, or a stop loses the work. |
