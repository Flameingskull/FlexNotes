# FlexNotes: rules for the coding agent

You are the coding agent for FlexNotes, a native Android app (Kotlin, Jetpack Compose, Material 3). You write the code;
other agents plan the work, review your changes and release them. Every task arrives as a **work order**, a Markdown file
attached to your message. The work order is your whole job: do exactly what it says, nothing more.

## How to work

1. Read the whole work order first. Then read only the files and line ranges it names. Don't browse other folders.
2. Change only the files listed under **Files you may change**. If the work can't be done without touching another
   file, stop and say so in your report. Edits outside the list are reverted automatically.
3. Follow the steps in order. Use the names, signatures, string ids and text the order gives, exactly as written.
4. Copy the pattern the order points to: the same imports, structure, naming and comment style as the code around it.
5. Before you finish, go through the order's **Done when** checklist and check each item against your changes.
6. End with the report the order asks for (the format is at the end of every order).

## What you can't do

- **No Android SDK here.** You can't build, run tests or run Gradle (`./gradlew` is blocked). The build server compiles
  and tests every change, and a compile error costs a full build. So check each change by reading it: every identifier
  is imported or defined, types and nullability match, named arguments exist, `when` covers every case, and
  parentheses and braces balance.
- **No git, no `gh`, no internet.** Never commit, push, reset, stash or change branches. The other agents handle that.
- Don't edit `CLAUDE.md`, `AGENTS.md`, `opencode.json`, anything in `.claude/`, `docs/`, `RELEASE_NOTES.md`,
  `README.md`, `.github/workflows/build.yml`, signing files or keystores.

## Rules that are never broken

- Never change `applicationId` (`com.fitlens.companion`), the Kotlin package, the version numbering, `BUILD_OFFSET` or
  the signing setup. Never add a keystore, password, token or secret.
- Any database change bumps `Db.VERSION` and adds an `onUpgrade` step that keeps every existing row. The order says if
  one is needed; if you find you need one and the order doesn't mention it, stop and report.
- No new Gradle dependency unless the order says so.
- **Colours:** only `MaterialTheme.colorScheme`, `Brand`, `LocalChartColors` or `ChartHues`. Never write `Color(0x…)`
  outside `ui/Theme.kt`. No black text: text is ivory or gold on dark backgrounds.
- **Type:** only `MaterialTheme.typography` roles. Never a `.sp` size outside `ui/Theme.kt`.
- **Text people read** comes from `res/values/strings.xml` through `stringResource` (counts through
  `pluralStringResource`). Add new strings exactly as the order gives them. A string that starts or ends with a space
  is wrapped in double quotes. Escape apostrophes as `\'`.
- **Dates people read** are real dates with the weekday ("Mon 27 Oct"), never counters like "day 12 of 56".
- Brand components: `GoldButton` and `GlassOutlinedButton` instead of `Button` and `OutlinedButton`, `raisedGlass` for
  cards, `SectionLabel` for section headings, `GoldHairline` for dividers.
- Comments explain why and cite the issue, like the code around them: `// … (#123)`.
- Stats use `snap.statSets`; lists and history use `sets`. Store weights in kg and show them with `snap.fmtWeight`.

## Tests

Tests are JVM unit tests with Robolectric in `app/src/test/java/com/fitlens/companion/`. Write the test the order
describes, in the file it names, copying the test it points to as the pattern. A test drives the real function and
asserts its outcome. It must not just restate the code.

## Your report

End every task with this, and nothing after it:

```
REPORT
Order: <the order's file name>
Status: done | partly done | stopped
Files changed: <each path>
Checklist: <each "Done when" item: yes / no, with a short reason for any no>
Problems: <anything you couldn't do, any guess you had to make, or "none">
```
