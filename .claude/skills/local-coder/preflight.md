# Coder preflight: the model, and the GPU

For the **main session only**, at a build's preflight and when a run stalls. Agents writing or reviewing work orders
don't need this file.

```powershell
pwsh -NoProfile -File .claude/scripts/coder-preflight.ps1
```

| First line | Means | Do |
| --- | --- | --- |
| `GPU OK (known)` | The server process recorded at the last confirmation is still running: same load | Carry on |
| `GPU OK (checked)` | The server holds GPU memory and little RAM; recorded for next time | Carry on |
| `ON CPU` | The server is running, but the model isn't on the GPU, or that couldn't be confirmed | "Not on the GPU" below, then run it again |
| `NO MODEL` | No coding model is chosen, OpenCode doesn't list it, the server is stopped, or it didn't answer | Ask the owner. Don't guess |

It also prints the model as OpenCode names it and the context size from the server's command line: note both in
`state.md`. If no context is shown, assume 32K. It never loads, switches or unloads anything.

## The model

The coder uses, in order: `$env:FLEXNOTES_CODER_MODEL`, then the `model` in OpenCode's settings
(`opencode models` lists what it can use; `(opencode debug config | ConvertFrom-Json).model` is what's chosen).

- **None is chosen:** ask the owner once which listed model to use for this build (the question tool, the listed
  models as options), then pass it to every run as `-Model provider/model`.
- **Never switch to a different model or unload one, and never force-cancel a chat:** the owner chooses what runs. The
  one exception is below: reloading the owner's own model, unchanged, onto the GPU (owner, 2026-11-08).

## Why the GPU check exists

**Owner, 2026-11-08:** the model server updates itself, and turning that off isn't an option. An update has several
times left the model running on the CPU without saying so: the GPU backend couldn't load because the update dropped its
GPU runtime libraries. On the CPU every order takes many times longer. So every build checks at its preflight, before
the first order, and fixes it.

**The main session does this, never the coder.** The coder is the model being checked: it can't load, unload or reload
itself, and no work order may ask it to. Copying the server's runtime files and reloading the model are machine set-up,
not code, so rule 9 doesn't route them to the coder. Never reload while an order is running, because a reload ends that
run: wait for the run to finish or time out first.

**What "known" rests on.** `nimbalyst-local/coder-gpu.md` (local only) records the model server's process id from its
last confirmation. The same process means the same load; an update, restart or reload starts a new process, so the
check runs again. The script decides by memory, not by what a status tool says: on the GPU, about the model's size is
in GPU memory (`nvidia-smi`) and the server process holds little RAM; on the CPU it's the reverse. A server's own
status tool can report its hardware as "cpu" while its engine is on the GPU, so don't overrule the script with it.

## Not on the GPU: fix it, in this order

1. **Can the server see the GPU?** Run its engine's device list (for a llama.cpp server,
   `llama-server.exe --list-devices`, from the folder in its command line). `(none)` means the GPU backend failed to
   load, almost always because GPU runtime libraries are missing after an update. Find the libraries its GPU backend
   imports (the `*.dll` names inside `ggml-cuda.dll`, such as `cublas64_NN.dll`, `cublasLt64_NN.dll`,
   `cudart64_NN.dll`), find the same major version elsewhere on the machine (another bundle the same server installed,
   or a CUDA toolkit), and copy them beside the engine. Only add files, never replace or delete any. Check the device
   list again. `nimbalyst-local/coder-gpu.md` records where they came from last time.
2. **Reload the same model onto the GPU:** same model, same quantisation, same context the owner had (read them from
   the server's process command line before reloading). Preview the load first if the server can. If it won't fit,
   quantise the KV cache (`q8_0`) first. Never switch the model or its quantisation, and never cut the context below
   the owner's setting without asking.
3. **Verify:** run the script again. It must print `GPU OK (checked)` and an answer within seconds.
4. **Record what was fixed** in `nimbalyst-local/coder-gpu.md`: the load settings (model, quantisation, context, KV
   cache) and what was copied from where. The script records the time and process id itself.

**Can't get it onto the GPU?** Stop before the first order and ask the owner. Never run a build's orders on the CPU
without saying so.

**Mid-build:** if a run stalls, times out or is far slower than earlier runs, run the script again before treating the
order as too big.

## If an order never starts

OpenCode reads its standard input as more of the message. Started with an input that never ends (for example from a
script that was itself started through a pipe), it waits there: the log stays empty and the run times out without a
single request. `run-orders.ps1` gives each run an empty input for this reason. When running `local-coder.ps1` by
hand, do it from the PowerShell tool directly, not from inside another shell's pipeline.
