#!/usr/bin/env python3
"""Checks that the audits can't quietly skip code or leave a finding with nowhere to go.

Every Kotlin source file must be in exactly one area of docs/audit/areas.txt, and each glob in code-areas.txt must
match a file. --ledgers checks the function ledgers (docs/audit/<area>.md) and --code-ledgers the code ledgers
(docs/audit/code/<area>.md): each must list every file of its area as read in full, and every finding must name its
issue. Standard library only.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "app", "src", "main", "java", "com", "fitlens", "companion")
AUDIT = os.path.join(ROOT, "docs", "audit")
FUNCTION_FINDINGS = {"fails", "gap"}
CODE_FINDINGS = {"defect", "risk", "improve"}


def sources():
    found = set()
    for folder, _, names in os.walk(SOURCE):
        for name in names:
            if name.endswith(".kt"):
                found.add(os.path.relpath(os.path.join(folder, name), SOURCE).replace(os.sep, "/"))
    return found


def read_areas(name):
    result, current = {}, None
    with open(os.path.join(AUDIT, name), encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            match = re.fullmatch(r"\[(.+)\]", line)
            if match:
                current = match.group(1)
                result[current] = []
            elif current is None:
                raise SystemExit(f"{name}: {line} comes before any [area]")
            else:
                result[current].append(line)
    return result


def expand(patterns, problems, area):
    files = []
    for pattern in patterns:
        hits = glob.glob(os.path.join(ROOT, pattern), recursive=True)
        if not hits:
            problems.append(f"{area}: {pattern} matches no file")
        files += [os.path.relpath(h, ROOT).replace(os.sep, "/") for h in hits]
    return sorted(set(files))


def ledger(path):
    """The ledger's rows as lists of cells, or None if it doesn't exist."""
    if not os.path.exists(path):
        return None
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.lstrip().startswith("|"):
                rows.append([c.strip().strip("`") for c in line.strip().strip("|").split("|")])
    return rows


def check_ledger(area, path, files, findings, problems):
    rows = ledger(path)
    shown = os.path.relpath(path, ROOT).replace(os.sep, "/")
    if rows is None:
        problems.append(f"{area}: no ledger at {shown}")
        return
    read = {r[0]: r[1] for r in rows if len(r) == 2}
    for name in files:
        state = read.get(name)
        if state is None:
            problems.append(f"{area}: ledger doesn't list {name}")
        elif not state.lower().startswith("yes"):
            problems.append(f"{area}: {name} not read in full ({state})")
    for row in rows:
        if len(row) >= 6 and row[3].lower() in findings and not re.search(r"#\d+", row[-1]):
            problems.append(f"{area}: \"{row[0]}\" is {row[3]} with no issue")


def main():
    problems = []
    existing = sources()
    table = read_areas("areas.txt")
    extra = read_areas("code-areas.txt")
    seen = {}
    for area, files in table.items():
        for name in files:
            if name in seen:
                problems.append(f"{name} is in both {seen[name]} and {area}")
            seen[name] = area
            if name not in existing:
                problems.append(f"{name} ({area}) doesn't exist: remove it from areas.txt")
    for name in sorted(existing - set(seen)):
        problems.append(f"{name} isn't in any audit area: add it to docs/audit/areas.txt")
    expanded = {area: expand(patterns, problems, area) for area, patterns in extra.items()}

    if "--ledgers" in sys.argv:
        for area, files in table.items():
            check_ledger(area, os.path.join(AUDIT, f"{area}.md"), files, FUNCTION_FINDINGS, problems)
    if "--code-ledgers" in sys.argv:
        for area, files in table.items():
            check_ledger(area, os.path.join(AUDIT, "code", f"{area}.md"), files, CODE_FINDINGS, problems)
        for area, files in expanded.items():
            check_ledger(area, os.path.join(AUDIT, "code", f"{area}.md"), files, CODE_FINDINGS, problems)

    for problem in problems:
        print(f"audit coverage: {problem}")
    print(
        f"audit coverage: {len(existing)} source files, {len(table)} areas, "
        f"{sum(len(f) for f in expanded.values())} test and build files, {len(problems)} problem(s)"
    )
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
