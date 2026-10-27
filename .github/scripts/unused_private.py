#!/usr/bin/env python3
"""Fail when a private Kotlin declaration is never used (#260).

Private scope is the file, so a private fun / val / var / class / object / interface / typealias whose name
appears as a whole word only once in its own file is dead code. Standard library only, no Gradle plugin.
Usage: python3 -I .github/scripts/unused_private.py [root, default app/src]
Declarations with @Preview in the three lines above are skipped. Prints `path:line: kind name`, one per finding
(each prefixed `error: unused private` so CI's errors.txt picks it up), and exits 1 if there are any.
"""
import pathlib
import re
import sys

MODS = r"(?:(?:inline|const|lateinit|data|enum|sealed|abstract|open|tailrec|suspend|operator|infix|external|fun|value|annotation|inner|override)\s+)*"
DECL = re.compile(
    r"^\s*(?:@[\w.]+(?:\([^)]*\))?\s+)*private\s+" + MODS
    + r"(fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?`?(\w+)`?"
)


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "app/src")
    found = 0
    checked = 0
    for f in sorted(root.rglob("*.kt")):
        raw = f.read_text(encoding="utf-8")
        lines = raw.splitlines()
        code = raw
        for i, line in enumerate(lines):
            m = DECL.match(line)
            if not m:
                continue
            kind, name = m.group(1), m.group(2)
            if "@Preview" in "\n".join(lines[max(0, i - 3):i + 1]):
                continue
            checked += 1
            uses = len(re.findall(r"(?<![\w])" + re.escape(name) + r"(?![\w])", code))
            if uses <= 1:
                found += 1
                print(f"error: unused private {f.as_posix()}:{i + 1}: {kind} {name}")
    print(f"{found} unused private declaration(s) among {checked} checked")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
