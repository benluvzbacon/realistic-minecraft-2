#!/usr/bin/env python3
"""Local syntax gate for the mod's Java sources.

The sandbox this project was developed in has no JDK (only a JRE), so Gradle cannot run
locally. This script parses every ``*.java`` file with ``javalang`` (pure Python) so that
obvious syntax errors are caught before pushing to GitHub Actions, which does the real
compilation and the JUnit run.

Usage:
    python3 tools/check_java_syntax.py            # checks src/
    python3 tools/check_java_syntax.py path ...   # checks given files/directories

Install the dependency with:  pip install javalang
"""
import sys
from pathlib import Path

try:
    import javalang
except ImportError:  # pragma: no cover - tooling only
    print("javalang is not installed; run: pip install javalang")
    sys.exit(2)

# Constructs used by this project that javalang (Java 8 grammar) cannot parse.
# Keep this list empty if possible: the project is deliberately written with a Java 8
# syntax surface so that this gate is a reliable signal.
KNOWN_LIMITATIONS = ()


def iter_java_files(targets):
    for target in targets:
        path = Path(target)
        if path.is_file() and path.suffix == ".java":
            yield path
        elif path.is_dir():
            yield from sorted(path.rglob("*.java"))


def main(argv):
    targets = argv[1:] or ["src"]
    files = list(iter_java_files(targets))
    if not files:
        print("no java files found for:", targets)
        return 1
    failures = 0
    for path in files:
        source = path.read_text(encoding="utf-8")
        try:
            javalang.parse.parse(source)
        except Exception as error:  # noqa: BLE001 - report everything
            message = str(error)
            if any(marker in message for marker in KNOWN_LIMITATIONS):
                print(f"SKIP  {path} ({message})")
                continue
            print(f"FAIL  {path}\n      {message}")
            failures += 1
        else:
            print(f"OK    {path}")
    print(f"\n{len(files)} file(s) checked, {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
