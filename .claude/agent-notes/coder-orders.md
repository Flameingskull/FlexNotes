# Writing work orders the coder gets right

Shared by everyone who writes or reviews orders (`bug-manager`, `feature-manager`, the main session in `/slice-build`).
The method and the size rule are in `.claude/skills/local-coder/SKILL.md`; this file is what earlier orders taught.
One dated line each; delete lines that stop being true. Keep it under about 150 lines. The repository is public: no
personal data, secrets or `FIL.n` keys.

## Size and shape

- 2026-11-06: orders that make the coder read a big file (`Charts.kt`, 900 lines; `strings.xml`, 3,900) overflow its
  context and are cancelled at the time limit. 34 orders of one quoted find-and-replace each, or one whole small test
  file, finished in under 30 seconds apiece. A stall or timeout means "split further", not "investigate".
- 2026-11-06: generate a run of similar orders with a Python script that takes (id, file, find, replace), so the exact
  text is typed once. Write the script and the orders with the Write tool: shell heredocs here drop backslashes and
  break on apostrophes and backticks.
- 2026-11-06: a test file longer than one order is created by the first order with a `// END OF TESTS` marker line
  before the closing brace; each later order appends by replacing the marker with its test plus the marker.
- 2026-11-12: a changed signature and its call sites are separate orders (signature, then each call), so the code only
  compiles again after the last of them. Say so in the design, and queue them together.
- 2026-11-13: two items that edit the same function (`FitSheet` for #334 and #331) run one after the other, never
  interleaved: the second item's "Find exactly" text is written against the first item's result.

## What the coder gets wrong unless the order says it

- 2026-11-05: a rule described in words comes back with parameters swapped or a comparison inverted. Give the exact
  function body, and the test as code.
- 2026-11-05: it writes `assertTrue { x }` (a lambda: doesn't compile) and passes a `Boolean?` to `assertTrue`. Say to
  use `assertEquals(true, x)`.
- 2026-11-06: it leaves out the import for an annotation or class it uses (`Application`, `RunWith`). Quote every
  import line in the order.
- 2026-11-06: in tests it calls `@Composable` functions outside a composition. Give the whole test, and say one
  `openScreen` per test.
- 2026-11-14: asked to add one line above an existing one, it re-indented the neighbouring lines by a space. Put the
  neighbours in "Find exactly" and "Replace with exactly", so the indent is given, not inferred.
- Source files mix CRLF and LF line endings. Tell the coder to keep each line's ending and give new lines their
  neighbours' ending; a patch that shows every line changed is a line-ending slip, not a rewrite.
- A string that starts or ends with a space, or holds two spaces together, needs double quotes round the whole text
  in `strings.xml`; apostrophes are `\'`. Give the line exactly as it must be stored.

## What to check in review (CI is the compiler)

- Brace counts and imports: a run once deleted three closing braces and an import in a file it was only adding to.
- A new `internal` type must not appear in a public function's signature (a compile error): look at every new
  parameter and return type.
- A helper made `@Composable` breaks any plain function that calls it: grep every caller.
- Test sources compile with main: read a test's imports like main code. A check run is green only if both compile.
- A new Room DAO parameter with no default breaks every existing caller, tests included (`addSet`, 1.0.172).
- After an exit 2 (a file outside the order changed), read the order's patch before re-running: the coder's own
  change may already be there.

## Scope (1.0.205)

- Name what an order must not touch: an order whose find is in one function rewrote two others (332-3), so the
  next order's find was gone and the coder improvised.
- End a find block on a line that differs in kind from the run it sits in: a removal ending inside a run of imports
  removed the rest of the run too (332-46-fix1). Or list in "Done when" the lines after it that must remain.
- A renaming order says which other uses later orders replace, or the coder renames them all and later finds miss.
- An append says the lines above its marker stay unchanged; an append at the end of a test file may replace the last
  test instead of adding after it (332-gate12): name that test in "Don't".
- In a test order, name every line of the test body in "Done when": the coder drops lines (332-70).
- A test written with full names (`android.app.Notification`) gets shortened: say "keep the full names" or give the
  import in the same order (332-gate18).
- Status "blocked" when the find isn't there: otherwise the coder edits something nearby instead.

## Running

- 2026-11-12: nothing in the repository may change while an order runs. The wrapper puts back any other file that
  changed (a roadmap edit was lost this way) and fails the order with exit 2.
- 2026-11-14: OpenCode waits on its standard input for more of the message. Started from inside another shell's
  pipeline it never sends a request and times out with an empty log. `run-orders.ps1` gives each run an empty input;
  run a single order from the PowerShell tool directly.
