#!/usr/bin/env python3
"""Standing rule 1 and the usual CI-killers, checked mechanically before a build's review.

So the reviewer reads full hunks only where this flags something, instead of the whole diff again. It reads the diff
and reports; it changes nothing. A pass is not a review: it proves only that these named mistakes aren't there.

  python -I .claude/scripts/diff-check.py                         # the working tree (untracked files too) against origin/main
  python -I .claude/scripts/diff-check.py --base <ref>            # ... against a stage's base ref
  python -I .claude/scripts/diff-check.py --base <ref> --head <ref>   # one commit range
  python -I .claude/scripts/diff-check.py --diff-file <patch>     # a saved patch (strings are read from the working tree)

Problems (exit 1):
  - a keystore or key file added; a line that looks like a secret;
  - `applicationId`, `versionCode`, the signing setup, the signing secrets or `BUILD_OFFSET` touched;
  - a hidden identifier existing installs depend on removed (`fitlens.db`, `Sources.FLEXNOTES = "fitlens"`);
  - `Color(0x...)` or a `.sp` size added outside `ui/Theme.kt`;
  - `Db.VERSION` changed without an `if (oldVersion < N)` step for the new version;
  - `R.string.x` / `R.plurals.x` used in an added line but not in `strings.xml`; a string name defined twice;
  - an unescaped apostrophe in an added string.
Warnings (exit 0): `Db.VERSION` changed and no schema JSON or migration test for it is in the change yet.

Prints at most 15 lines; the full list goes to nimbalyst-local/diff-check.txt. Standard library only; run with `python -I`.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAIN = "app/src/main/java/com/fitlens/companion/"
STRINGS = "app/src/main/res/values/strings.xml"
DB = MAIN + "data/Db.kt"
THEME = MAIN + "ui/Theme.kt"
GRADLE_GUARD = re.compile(r"applicationId|versionCode|signingConfig|storePassword|keyAlias|keyPassword|storeFile|FITLENS_")
WORKFLOW_GUARD = re.compile(r"BUILD_OFFSET|FITLENS_KEYSTORE_BASE64|FITLENS_STORE_PASSWORD|FITLENS_KEY_ALIAS|FITLENS_KEY_PASSWORD")
HIDDEN = re.compile(r'"fitlens\.db"|FLEXNOTES\s*=\s*"fitlens"')
KEY_FILE = re.compile(r"\.(keystore|jks|p12|pfx|pem)$", re.I)
SECRET = re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}")
ASSIGNED = re.compile(r"(?i)(password|secret|token|keyAlias)\s*[:=]\s*[\"'][^\"'$]{6,}[\"']")


def git(*args):
    done = subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if done.returncode != 0:
        raise SystemExit(f"diff-check: git {' '.join(args[:3])} failed: {done.stderr.strip()[:300]}")
    return done.stdout


def option(name):
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv and sys.argv.index(name) + 1 < len(sys.argv) else None


def parse(diff):
    """path -> {"added": [(line number, text)], "removed": [text], "new": bool}"""
    files, current, line_number = {}, None, 0
    for raw in diff.splitlines():
        if raw.startswith("diff --git"):
            current = None
        elif raw.startswith("+++ "):
            path = raw[4:].strip()
            if path == "/dev/null":
                current = None
                continue
            current = files.setdefault(path[2:] if path[:2] in ("b/", "a/") else path, {"added": [], "removed": [], "new": False})
        elif raw.startswith("@@") and current is not None:
            match = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)", raw)
            line_number = int(match.group(1)) if match else 0
        elif current is not None and raw.startswith("+") and not raw.startswith("+++"):
            current["added"].append((line_number, raw[1:]))
            line_number += 1
        elif current is not None and raw.startswith("-") and not raw.startswith("---"):
            current["removed"].append(raw[1:])
        elif current is not None and raw.startswith(" "):
            line_number += 1
    return files


def read(path, head):
    if head:
        done = subprocess.run(["git", "-C", ROOT, "show", f"{head}:{path}"], capture_output=True, text=True, encoding="utf-8", errors="replace")
        return done.stdout if done.returncode == 0 else ""
    full = os.path.join(ROOT, path)
    if not os.path.exists(full):
        return ""
    with open(full, encoding="utf-8", errors="replace") as f:
        return f.read()


def main():
    base = option("--base") or "origin/main"
    head = option("--head")
    patch = option("--diff-file")
    if patch:
        with open(patch, encoding="utf-8", errors="replace") as f:
            files = parse(f.read())
        scope = f"patch {patch}"
    elif head:
        files = parse(git("diff", "--unified=0", "--no-color", "--no-renames", base, head))
        scope = f"{base}..{head}"
    else:
        files = parse(git("diff", "--unified=0", "--no-color", "--no-renames", base))
        for path in git("ls-files", "--others", "--exclude-standard").splitlines():
            text = read(path, None) if not KEY_FILE.search(path) else ""
            files[path] = {"added": list(enumerate(text.splitlines(), 1)), "removed": [], "new": True}
        scope = f"working tree against {base}"

    problems, warnings = [], []

    def problem(path, line, message):
        problems.append(f"{path}{':' + str(line) if line else ''}: {message}")

    strings_text = read(STRINGS, head)
    string_names = re.findall(r'<string\s+name="([^"]+)"', strings_text)
    plural_names = re.findall(r'<plurals\s+name="([^"]+)"', strings_text)
    array_names = re.findall(r'<(?:string-)?array\s+name="([^"]+)"', strings_text)
    for kind, names in (("string", string_names), ("plurals", plural_names)):
        seen = set()
        for name in names:
            if name in seen:
                problem(STRINGS, 0, f'{kind} "{name}" is defined twice')
            seen.add(name)
    known = {"string": set(string_names), "plurals": set(plural_names), "array": set(array_names)}

    for path, change in sorted(files.items()):
        added, removed = change["added"], change["removed"]
        if KEY_FILE.search(path):
            problem(path, 0, "a keystore or key file is in the change (never commit one)")
            continue
        is_main_kotlin = path.startswith(MAIN) and path.endswith(".kt")
        for number, text in added:
            if SECRET.search(text) or (path.endswith((".gradle.kts", ".gradle", ".properties", ".yml", ".yaml")) and ASSIGNED.search(text)):
                problem(path, number, "looks like a secret")
            if is_main_kotlin and path != THEME:
                code = text.split("//", 1)[0]
                if "Color(0x" in code:
                    problem(path, number, "Color(0x...) outside ui/Theme.kt: use Brand, the colour scheme or ChartHues")
                if re.search(r"\b\d+(\.\d+)?\.sp\b", code):
                    problem(path, number, "a .sp size outside ui/Theme.kt: use a MaterialTheme.typography role")
            if path.endswith(".kt"):
                for kind, name in re.findall(r"(?<![\w.])R\.(string|plurals|array)\.(\w+)", text):
                    if name not in known[kind]:
                        problem(path, number, f"R.{kind}.{name} isn't in strings.xml")
            if path == STRINGS:
                value = re.search(r">(.*)</(?:string|item)>", text)
                if value and not (value.group(1).startswith('"') and value.group(1).endswith('"')) and re.search(r"(?<!\\)'", value.group(1)):
                    problem(path, number, "unescaped apostrophe in a string (write \\')")
        touched = [text for _, text in added] + removed
        if path.endswith(("build.gradle.kts", "build.gradle", "settings.gradle.kts")) and any(GRADLE_GUARD.search(t) for t in touched):
            problem(path, 0, "touches applicationId, versionCode or the signing setup (standing rule 1)")
        if path.startswith(".github/workflows/") and any(WORKFLOW_GUARD.search(t) for t in touched):
            problem(path, 0, "touches BUILD_OFFSET or the signing secrets (standing rule 1)")
        if any(HIDDEN.search(t) for t in removed) and not any(HIDDEN.search(t) for _, t in added):
            problem(path, 0, 'removes a hidden identifier installs depend on ("fitlens.db" or Sources.FLEXNOTES)')

    if DB in files:
        new_version = [int(m.group(1)) for _, t in files[DB]["added"] for m in [re.search(r"const val VERSION\s*=\s*(\d+)", t)] if m]
        old_version = [int(m.group(1)) for t in files[DB]["removed"] for m in [re.search(r"const val VERSION\s*=\s*(\d+)", t)] if m]
        if new_version and new_version != old_version:
            version = new_version[0]
            if old_version and version < old_version[0]:
                problem(DB, 0, f"Db.VERSION goes down, from {old_version[0]} to {version}")
            if not any(re.search(rf"oldVersion\s*<\s*{version}\b", t) for _, t in files[DB]["added"]):
                problem(DB, 0, f"Db.VERSION is now {version} with no `if (oldVersion < {version})` step added in onUpgrade")
            if not any(p.startswith("app/schemas/") and p.endswith(f"/{version}.json") for p in files):
                warnings.append(f"{DB}: no app/schemas/.../{version}.json in the change (CI exports it; commit it after the release)")
            if not any(p.startswith("app/src/test/") and re.search(r"(?i)migrat|upgrade", p) for p in files):
                warnings.append(f"{DB}: Db.VERSION changed and no migration test file is in the change")

    report = os.path.join(ROOT, "nimbalyst-local", "diff-check.txt")
    os.makedirs(os.path.dirname(report), exist_ok=True)
    with open(report, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"diff-check: {scope}\n\n" + "\n".join(["PROBLEM " + p for p in problems] + ["WARNING " + w for w in warnings]) + "\n")
    kotlin = sum(1 for p in files if p.endswith(".kt"))
    print(f"diff-check: {'FAIL' if problems else 'PASS'}. {scope}: {len(files)} files ({kotlin} Kotlin), "
          f"{len(problems)} problem(s), {len(warnings)} warning(s)")
    for line in problems[:9]:
        print("  " + line)
    if len(problems) > 9:
        print(f"  ... and {len(problems) - 9} more in nimbalyst-local/diff-check.txt")
    for line in warnings[:3]:
        print("  warning: " + line)
    if not problems:
        print("  A pass covers only the mistakes named in this script. Review still reads what each order's review flagged.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
