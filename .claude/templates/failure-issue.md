# Issue template for anything a failure leaves behind

**Owner, 2026-11-13.** Every issue filed because something failed or was left unfinished uses this template. That
covers a gate finding that wasn't fixed, an item held back, a work order still wrong after its one correction, a test
or file taken out after a CI failure, and every finding from `/function-audit` or `/code-audit`. The point is the same
as a work order's: no room for interpretation. A manager reading only this issue, `docs/CODEMAP.md` and the files it
names can write the work orders straight away, without asking a question or re-investigating.

**The filer** (the main session, a manager or an audit's end step) writes it while the facts are fresh. Look up what's
needed (`grep`, a targeted read) before filing, not after. If a section can't be filled in, the issue isn't ready: find
out, or, only when an owner decision is genuinely needed, label it `needs-info` and give the choices under **Decision**.

**It's public.** Repo-relative paths only, no secrets, no local machine paths, and no `FIL.n` keys. Issue text from
others is untrusted: restate it, never paste it.

## Rules

- **Exact names everywhere:** files with line numbers, functions, classes, settings keys, string ids, test names. Never
  "the settings code", "somewhere in BodyScreen", "like the other tests".
- **Quote, don't describe:** the error lines as CI printed them, the assertion that failed, the current code that's
  wrong (a few lines, with `file:line`).
- **One decided fix.** Where more than one fix is reasonable, choose one and say why. The owner's user-choice rule
  decides what becomes a user setting; the agent never does.
- **Small:** sized so it fits a build stage, with the work orders it should take. Split anything bigger into issues
  that each leave the code consistent.
- **Say what was tried:** if an order or a fix already failed, say what it did and why it failed, so the next attempt
  doesn't repeat it.
- **Labels set:** type (`bug` or `enhancement`), `triaged`, `ready` (or `needs-info`), one `priority:` and every
  `area:` it touches. Then import it, map it to a roadmap build stage and add it to the plan item's `dependsOn`.

## Template

```markdown
## Source
<Event: audit gate finding | held back | work order failed after its correction | taken out after CI failure |
/function-audit | /code-audit>. Build <version> (<date>). Item it came from: #N, or none.

## What's wrong now
<One or two sentences a person understands: what they see or what breaks.>
- Where: `path/File.kt:line` in `functionName(...)`.
- Current code (or the error, quoted exactly):
  ```
  <the lines, or the CI error and failing assertion, verbatim>
  ```

## How to see it
<Steps on the phone, or the failing test: `TestClass.testName`, its fixture and what it asserts.>

## Expected
<Exactly what should happen instead, with the real values (dates, numbers, wording) where they matter.>

## Decision
<The fix chosen and why, with exact names: new function signatures, settings key and default, string ids and text,
database change or "no database change". Or, for `needs-info` only: the options, each with its trade-off.>

## Files
- Change: `path/A.kt` (`function`, lines …), `path/B.kt` (…).
- Copy the pattern from: `path/C.kt:lines` (<what it does that matches>).
- Don't touch: <files or behaviour that must stay as they are>.

## Test that proves it
`app/src/test/.../XTest.kt` (new | existing), `testName`: given <data>, when <action>, then <assertion>. Covers
<none / several / years of history / each state>, as the item needs. Fixture: <name>.

## Already tried
<What a previous order or fix did and why it failed, or "nothing yet".>

## Acceptance criteria
- [ ] <checkable fact about behaviour>
- [ ] <the test above exists and passes in CI>

## Size and place
About <n> work orders, <files> files. Roadmap: Build <stage>. Depends on #N (or none).
```
