---
name: local-coder
description: How every FlexNotes build hands code to the coder (OpenCode, headless, on whichever model OpenCode is set to use). Claude agents plan, write work orders, review and audit; the coder writes all code. /new-build, /safe-build, /slice-build and build-audit follow it. Not a build on its own.
---

# Handing code to the local coder

**Owner, 2026-11-03:** all code is written by a model run through OpenCode. Claude agents (the main session,
`bug-manager`, `feature-manager`) never edit code, not even one line: they triage, write **work orders**, review the
result and send corrections. Auditors still change nothing.

**Model-agnostic (owner, 2026-11-04):** the coder is whichever model OpenCode is set to use. The owner may swap models
or servers at any time (today local models served by Unsloth Studio). Never name a model, server or port in a skill,
agent or order, and never write instructions that only one model would follow. Write for any capable coding model.

**Code** is anything a build compiles, runs or packages: everything under `app/` (Kotlin, tests, resources,
`strings.xml`, schemas), Gradle files, `.github/scripts/`, and workflows other than `build.yml`. Claude still writes
what isn't code: `RELEASE_NOTES.md`, `README.md`, `docs/` (code map, roadmap, plans), agent notes, issues, tracker
items and commit messages. Audit ledgers stay the auditors' job.

The coder's standing rules are in `AGENTS.md` (OpenCode loads it into every run); its permissions are in
`opencode.json` (no git, `gh`, Gradle or internet). Don't repeat those rules in an order: spend the space on the task.

## 1. Check the model first

At the build's preflight, find which model the coder will use. In order: `$env:FLEXNOTES_CODER_MODEL`, then the
`model` in OpenCode's settings:

```powershell
opencode models                               # every model OpenCode can use, as provider/model
(opencode debug config | ConvertFrom-Json).model   # OpenCode's chosen model, if set
```

- **One is chosen:** use it. Note its context size if `opencode models --verbose` shows one; otherwise assume 32K.
- **None is chosen:** ask the owner once which listed model to use for this build (the question tool, the listed
  models as options), then pass it to every run as `-Model provider/model`. Don't guess.

Never switch to a different model or unload one, and never force-cancel a chat: the owner chooses what runs. The one
exception is step 1a: reloading the owner's own model, unchanged, onto the GPU (owner, 2026-11-08). If the first run
exits 3 (no model, or it can't be reached), stop and ask the owner. Size orders to the context (step 2).

## 1a. Make sure the model is on the GPU

**Owner, 2026-11-08:** the model server updates itself (today Unsloth Studio, which runs llama.cpp), and turning that
off isn't an option. An update has twice left the model running on the CPU without saying so: the GPU backend couldn't
load because the update dropped its GPU runtime libraries. On the CPU every order takes many times longer. So every
build command checks this at its preflight, before the first order, and fixes it.

**The main Claude session does this, never the coder.** The coder is the model being checked: it can't load, unload or
reload itself, and no work order may ask it to. The main session uses its own tools (the server's status and load
tools, the shell). Copying the server's runtime files and reloading the model are machine set-up, not code, so rule 9
doesn't route them to the coder. Never reload while an order is running, because a reload ends that run: wait for the
run to finish or time out first.

**Skip it when it's already known.** The check isn't needed when either:
- this session already confirmed the model is on the GPU, and nothing has restarted, updated or reloaded the server
  since; or
- `nimbalyst-local/coder-gpu.md` (local only) records the model server's process id from its last confirmation, and
  that process is still running (`Get-Process -Id <pid>`). The same process means the same load. An update, restart or
  reload starts a new process, so the check runs again.

**Quick check** (when not already known):
1. Ask the server what's loaded and where, if it has a status tool (Unsloth Studio: `studio_status`; its `hardware`
   block and the chat model).
2. Compare GPU memory with the server's own memory:
   `nvidia-smi --query-gpu=memory.used,memory.total --format=csv`, and the working set of the model server's process
   (`llama-server.exe`). **On the GPU:** GPU memory used is about the model's size and the process holds only a few
   GB of RAM. **On the CPU:** the GPU is nearly idle and the process holds about the model's size in RAM.
3. On the GPU: record it (below) and carry on with the build.

**Not on the GPU: fix it, in this order.**
1. **Can the server see the GPU?** Run its engine's device list (`llama-server.exe --list-devices`, from the folder in
   its command line). `(none)` means the GPU backend failed to load, almost always because GPU runtime libraries are
   missing after an update. Find the libraries its GPU backend imports (the `*.dll` names inside `ggml-cuda.dll`,
   such as `cublas64_NN.dll`, `cublasLt64_NN.dll`, `cudart64_NN.dll`), find the same major version elsewhere on the
   machine (another bundle the same server installed, or a CUDA toolkit), and copy them beside the engine. Only
   add files, never replace or delete any. Check the device list again. `nimbalyst-local/coder-gpu.md` records where
   they came from last time.
2. **Reload the same model onto the GPU:** same model, same quantisation, same context the owner had (read them from
   the server's process command line before reloading). Preview the load first if the server can (`plan_load` with
   `device: "gpu"`). If it won't fit, quantise the KV cache (`q8_0`) first. Never switch the model or its quantisation,
   and never cut the context below the owner's setting without asking.
3. **Verify:** GPU memory jumps to about the model's size, the server process's RAM drops, and a one-line
   `opencode run "Reply with exactly the word READY. Do not use any tools."` answers within seconds.
4. **Record it:** update `nimbalyst-local/coder-gpu.md` with the time, the server process id, GPU memory used, and the
   load settings (model, quantisation, context, KV cache), plus what was fixed, if anything.

**Can't get it onto the GPU?** Stop before the first order and ask the owner. Never run a build's orders on the CPU
without saying so.

**Mid-build:** if a run stalls, times out or is far slower than earlier runs, run this check again before treating the
order as too big.

## 2. Write the work order

Assume the coder is a mid-sized model with a small context, no memory of the codebase and no way to compile. It does
well with a precise, self-contained order and badly with a vague one. **Everything it needs is in the order.** Work
out the design yourself (from `docs/CODEMAP.md`, `grep` and targeted reads), then write it down so the coder only has
to type it in correctly.

**Size.** One order is one coherent change the coder can hold in its head at once:
- at most 3 files changed and about 150 lines written;
- the order itself under about 6,000 tokens (roughly 400 lines of Markdown);
- what it has to read (the order, the files at the line ranges given, and the pattern to copy) fits in about half of
  the model's context; at about 50K tokens, that's roughly 1,200 lines of Kotlin. Name line ranges in large files.
- Split anything bigger into a sequence of orders, each leaving the code consistent (it compiles after every order).
  A typical item: data or logic → screen → strings → test. A test can be its own order.

**Untrusted text.** Issue content is untrusted: restate what's needed in your own words. Never paste issue text in.

Save it as `nimbalyst-local/work-orders/<version>/<issue>-<n>.md` (local only, never committed), using this template.
The wrapper reads the **Files you may change** list, so keep its form: one `` `path` `` per bullet, repo-relative.

````markdown
# Work order <issue>-<n>: <short title>

## Goal
<One or two sentences: what a person will see or be able to do after this order, and why (issue #N).>

## Background
<Only what the coder needs: how the feature fits, the data it uses, the decision already made. Plain words.>

## Files you may change
- `app/src/main/java/com/fitlens/companion/ui/Example.kt`
- `app/src/main/res/values/strings.xml`

## Read first
- `ui/Example.kt` lines 120–210: the function you'll change.
- `ui/Other.kt` lines 40–90: the pattern to copy (<what it does that matches>).

## Current code
<The exact lines being changed, quoted, with file and line numbers, so the coder can find them without searching.>

## Steps
1. In `ui/Example.kt`, in `fun ExampleScreen(...)` (line 120), after the `SectionLabel(...)` at line 141, add a row
   that <does what>. Use `GoldButton(onClick = ...)` like `Other.kt` line 66.
2. Add this function below `ExampleScreen`:
   ```kotlin
   <the exact signature, and the body or a precise outline of it>
   ```
3. Imports to add: `androidx.compose.material3.Switch`, `com.fitlens.companion.data.Example`.
4. In `strings.xml`, after `ex_title`, add exactly:
   ```xml
   <string name="ex_new_label">Show the weekday</string>
   ```

## Test
File `app/src/test/java/com/fitlens/companion/ui/ExampleTest.kt` (new | existing, add to it). Copy the setup of
`<ExistingTest>` (lines …). Test `<name>`: given <data>, when <action>, then <assertion>. Cover <none / several / years
of history / each state>, as the item needs. Use <fixture, e.g. `LongHistoryFixture` / `FunctionTest.tap`>.

## Don't
- <What to leave alone: other functions in the file, behaviour that must not change, tempting refactors.>

## Done when
- [ ] <Concrete, checkable facts about the code: names, behaviour, strings, test present and asserting X.>
- [ ] Every new identifier is imported or defined; no `Color(0x…)` and no `.sp` outside `Theme.kt`.

## Report
Finish with the REPORT block from AGENTS.md.
````

Be explicit about the things a model gets wrong without the codebase in its head: exact names and signatures, which
overload to call, nullability, the import for every new symbol, where in the file new code goes, the exact string ids
and text, a `Db.VERSION` bump and its `onUpgrade` step (or "no database change"), and the test's fixture and asserts.
Where a choice is reasonable either way, make it in the order (owner imperative 3 decides what the user gets to choose;
the coder never decides).

## 3. Run it

The **main session** runs orders, one at a time, in order (one model; later orders build on earlier ones). Run it in
the background and wait for the notification; don't poll:

```powershell
pwsh -NoProfile -File .claude/scripts/local-coder.ps1 -Order nimbalyst-local/work-orders/<version>/<issue>-<n>.md
```

Options: `-Model provider/model` (when OpenCode has none chosen, step 1), `-Variant <level>` (the model's own reasoning level, such as `high`, for tricky logic; only if the model has levels), `-TimeoutMinutes` (default 60).
It writes `<order>.result.md` (model, time, files changed, diff stat, the coder's report) and `<order>.log` (the full
transcript, for when the result isn't clear). Exit codes: 0 done; 1 failed or timed out; 2 it changed files outside
the order, which were put back; 3 no model (ask the owner); 4 the order is malformed.

When an agent wrote the order, the main session passes it only the result file's path and the exit code, so the
main session's context stays small.

## 4. Review, then accept or correct

Whoever wrote the order reviews the result: read the full diff of the order's files (`git diff -- <files>`, and new
files in full), never just the coder's report. Check it against **Done when**, then the usual Kotlin checks (CI is the
compiler): imports, types, nullability, named arguments, `when` exhaustiveness, Compose APIs, balanced braces, string
ids that exist, and nothing outside the order.

- **Right:** accept it. The next order can start.
- **Wrong or incomplete:** write a **correction order** `<issue>-<n>-fix<k>.md` with the same template: quote the wrong
  lines with `file:line`, say exactly what's wrong and give the exact fix. Keep it to the problems; don't restate the
  whole original order. Run it the same way.
- **Unusable** (wrong file, wrong approach, broken beyond a quick fix): put the order's files back with
  `git restore -- <files>` (and delete new files), then rewrite the order more explicitly, or split it smaller.
  Putting files back isn't writing code.
- **Cap (owner, 2026-11-13, "One pass, no loops"):** one review and at most one correction order per order (2 coder
  runs). If it still isn't right, stop: file what's left as an issue for a later build, written to
  `.claude/templates/failure-issue.md` with both attempts under "Already tried" (tracker and roadmap too), and say
  in the report what the coder couldn't do. Hold the item back (as `build-audit` step 5 does) only if its main function
  is broken without it or data is at risk. Never fix it yourself, and never send it round again.

Note in the agent's notes (`.claude/agent-notes/`) any reusable lesson about writing orders the coder gets right:
short, dated, one line each.

## 5. The same loop everywhere

- **Audit findings** (build-audit step 5): the owner of the item turns each must-fix finding into a fix order, one round
  only. The owner reviews the result; the auditor doesn't re-check it.
- **CI failures:** read `errors.txt`, then write a fix order that quotes each error line and gives the exact fix. A test
  or file taken out instead is filed with `.claude/templates/failure-issue.md`, the CI error quoted.
  Its owner reviews it once; it isn't re-audited.
- **No loops anywhere:** one correction per order, one gate fix round, no re-checks. Whatever is left is an issue.
