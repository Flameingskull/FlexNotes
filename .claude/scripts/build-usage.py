#!/usr/bin/env python3
"""What a build session cost in Claude usage, per agent and per kind of work.

Reads the session's transcript (and its sub-agents') from Claude Code's project folder, which holds the token counts
of every API call. Nothing is sent anywhere. Standard library only; run it with `python -I`.

  python -I .claude/scripts/build-usage.py                  # the most recent session that ran a build command
  python -I .claude/scripts/build-usage.py 46d8482a         # a session, by the start of its id
  python -I .claude/scripts/build-usage.py 46d8482a --label 1.0.204

Weighted tokens = input + 0.1 x cache read + 1.25 x cache write + 5 x output (the formula /safe-build budgets with).
A re-cache is a call, not an agent's first, that wrote more than 80% of a context over 40K back into the cache: the
agent was woken after its cache had expired (about 5 minutes for a sub-agent, an hour for the main session). Its extra
cost is 1.15 x what it wrote, the difference from reading it warm.

Prints at most 15 lines and writes the full table to nimbalyst-local/usage/<session>[-<label>].md (local only).
"""
import collections
import datetime
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BUILD_COMMANDS = ("new-build", "safe-build", "slice-build")
JOBS = ("triage", "design", "review", "gate", "fix", "clerk")


def project_dir():
    slug = re.sub(r"[^A-Za-z0-9]", "-", ROOT)
    return os.path.join(os.path.expanduser("~"), ".claude", "projects", slug)


def rows_of(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                row = json.loads(line)
            except ValueError:
                continue
            if isinstance(row, dict):
                yield row


def when(row):
    try:
        return datetime.datetime.fromisoformat(row.get("timestamp").replace("Z", "+00:00"))
    except (AttributeError, ValueError):
        return None


def kind_of(tools):
    """What a main-session call was for, from the first tool it used."""
    if not tools:
        return "replies"
    name, given = tools[0]
    if name in ("Bash", "PowerShell"):
        command = given.get("command", "") if isinstance(given, dict) else ""
        for pattern, kind in (
            (r"local-coder\.ps1|run-orders\.ps1", "coder runs"),
            (r"gh run|watch-ci\.ps1", "CI watch"),
            (r"gh issue|backlog\.py", "issues"),
            (r"\bgit ", "git"),
        ):
            if re.search(pattern, command):
                return kind
        return "shell"
    if name in ("Agent", "SendMessage", "Task"):
        return "agents"
    if "tracker" in name:
        return "tracker"
    if name in ("Read", "Grep", "Glob"):
        return "reading"
    if name in ("Edit", "Write"):
        return "writing"
    return "other tools"


def calls_of(path):
    """One entry per API call: time, input, cache read, cache write, output, and the tools it used."""
    order, calls = [], {}
    for row in rows_of(path):
        message = row.get("message")
        if row.get("type") != "assistant" or not isinstance(message, dict) or message.get("model") == "<synthetic>":
            continue
        key = message.get("id")
        if key not in calls:
            usage = message.get("usage") or {}
            calls[key] = [
                when(row),
                usage.get("input_tokens") or 0,
                usage.get("cache_read_input_tokens") or 0,
                usage.get("cache_creation_input_tokens") or 0,
                usage.get("output_tokens") or 0,
                [],
            ]
            order.append(key)
        for block in message.get("content") or []:
            if isinstance(block, dict) and block.get("type") == "tool_use":
                calls[key][5].append((block.get("name", ""), block.get("input") or {}))
    return [calls[key] for key in order]


def weighted(call):
    return call[1] + 0.1 * call[2] + 1.25 * call[3] + 5 * call[4]


def measure(path, by_kind=None):
    calls = calls_of(path)
    total = {"calls": len(calls), "peak": 0, "weighted": 0.0, "recaches": 0, "extra": 0.0, "first": 0}
    for index, call in enumerate(calls):
        context = call[1] + call[2] + call[3]
        cost = weighted(call)
        total["weighted"] += cost
        total["peak"] = max(total["peak"], context)
        if index == 0:
            total["first"] = context
        elif call[3] > 0.8 * context and context > 40000:
            total["recaches"] += 1
            total["extra"] += 1.15 * call[3]
        if by_kind is not None:
            entry = by_kind[kind_of(call[5])]
            entry[0] += 1
            entry[1] += cost
    return total


def ran_a_build(path):
    """True when the owner typed a build command in this session (not when one is merely quoted in a tool result)."""
    typed = re.compile(r"^\s*(<command-message>.*?</command-message>\s*)?<command-name>/?(%s)<" % "|".join(BUILD_COMMANDS), re.S)
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if "<command-name>" not in line:
                continue
            try:
                row = json.loads(line)
            except ValueError:
                continue
            message = row.get("message") if isinstance(row, dict) else None
            if row.get("type") != "user" or not isinstance(message, dict):
                continue
            content = message.get("content")
            texts = [content] if isinstance(content, str) else [
                b.get("text", "") for b in content or [] if isinstance(b, dict) and b.get("type") == "text"
            ]
            if any(typed.search(text) for text in texts):
                return True
    return False


def find_session(folder, wanted):
    paths = sorted(glob.glob(os.path.join(folder, "*.jsonl")), key=os.path.getmtime, reverse=True)
    if wanted:
        hits = [p for p in paths if os.path.basename(p).startswith(wanted)]
        if len(hits) != 1:
            raise SystemExit(f"build-usage: {len(hits)} sessions start with {wanted} in {folder}")
        return hits[0]
    for path in paths:
        if ran_a_build(path):
            return path
    raise SystemExit(f"build-usage: no session in {folder} ran a build command")


def job_of(meta):
    words = re.split(r"[\s:,]+", (meta.get("description") or "").strip().lower())
    return words[0] if words and words[0] in JOBS else "(no job named)"


def millions(value):
    return f"{value / 1e6:.1f}M"


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    label = sys.argv[sys.argv.index("--label") + 1] if "--label" in sys.argv else ""
    if label and label in args:
        args.remove(label)
    session_path = find_session(project_dir(), args[0] if args else "")
    session = os.path.basename(session_path)[:-6]

    by_kind = collections.defaultdict(lambda: [0, 0.0])
    main_total = measure(session_path, by_kind)
    agents = []
    for path in sorted(glob.glob(os.path.join(session_path[:-6], "subagents", "*.jsonl"))):
        try:
            with open(path[:-6] + ".meta.json", encoding="utf-8") as f:
                meta = json.load(f)
        except (OSError, ValueError):
            meta = {}
        agents.append((meta, measure(path)))

    everything = [main_total] + [total for _, total in agents]
    grand = sum(t["weighted"] for t in everything) or 1.0
    recaches = sum(t["recaches"] for t in everything)
    extra = sum(t["extra"] for t in everything)
    sub_recaches = sum(t["recaches"] for _, t in agents)

    by_job = collections.defaultdict(lambda: [0, 0, 0.0, 0])
    for meta, total in agents:
        entry = by_job[(job_of(meta), meta.get("agentType") or "?")]
        entry[0] += 1
        entry[1] += total["calls"]
        entry[2] += total["weighted"]
        entry[3] += total["recaches"]

    out = [
        f"# Build usage: session {session[:8]}{' (' + label + ')' if label else ''}",
        "",
        "Weighted = input + 0.1 x cache read + 1.25 x cache write + 5 x output.",
        "",
        f"- Total: {millions(grand)} weighted",
        f"- Main session: {millions(main_total['weighted'])}, {main_total['calls']} calls, "
        f"peak context {main_total['peak'] // 1000}K, {main_total['recaches']} re-caches",
        f"- Sub-agents: {len(agents)}, {millions(grand - main_total['weighted'])}, {sub_recaches} re-caches",
        f"- Re-caches: {recaches}, costing {millions(extra)} more than warm reads ({extra / grand * 100:.0f}%)",
        "",
        "## Main session, by what each call was for",
        "",
        "| Work | Calls | Weighted |",
        "| --- | --- | --- |",
    ]
    for kind, (count, cost) in sorted(by_kind.items(), key=lambda item: -item[1][1]):
        out.append(f"| {kind} | {count} | {millions(cost)} |")
    out += ["", "## Sub-agents, by job", "", "| Job | Agent | Launches | Calls | Weighted | Re-caches |", "| --- | --- | --- | --- | --- | --- |"]
    for (job, agent), (launches, calls, cost, again) in sorted(by_job.items(), key=lambda item: -item[1][2]):
        out.append(f"| {job} | {agent} | {launches} | {calls} | {millions(cost)} | {again} |")
    out += ["", "## Every sub-agent", "", "| Agent | Description | Calls | Start | Peak | Weighted | Re-caches |", "| --- | --- | --- | --- | --- | --- | --- |"]
    for meta, total in sorted(agents, key=lambda item: -item[1]["weighted"]):
        description = (meta.get("description") or "").replace("|", "/")[:60]
        out.append(
            f"| {meta.get('agentType') or '?'} | {description} | {total['calls']} | {total['first'] // 1000}K | "
            f"{total['peak'] // 1000}K | {millions(total['weighted'])} | {total['recaches']} |"
        )

    folder = os.path.join(ROOT, "nimbalyst-local", "usage")
    os.makedirs(folder, exist_ok=True)
    report = os.path.join(folder, f"{session[:8]}{'-' + label if label else ''}.md")
    with open(report, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")

    print("\n".join(line for line in out[4:8]))
    top = sorted(by_kind.items(), key=lambda item: -item[1][1])[:4]
    print("- Main session went on: " + ", ".join(f"{kind} {millions(cost)} ({count})" for kind, (count, cost) in top))
    jobs = sorted(by_job.items(), key=lambda item: -item[1][2])[:4]
    if jobs:
        print("- Sub-agents went on: " + ", ".join(f"{job} {agent} {millions(cost)}" for (job, agent), (_, _, cost, _) in jobs))
    print(f"- Full table: {os.path.relpath(report, ROOT).replace(os.sep, '/')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
