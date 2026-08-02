#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
FILES = [
    ROOT / "CHANGELOG.md",
    ROOT / "PACKAGING.md",
    ROOT / "ROADMAP.md",
]
FILES.extend(sorted((ROOT / "docs").glob("*.md")))

LINK_RE = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
IGNORE_PREFIXES = ("http://", "https://", "#", "mailto:")

missing = []
for file_path in FILES:
    text = file_path.read_text(encoding="utf-8")
    for raw_target in LINK_RE.findall(text):
        target = raw_target.split("#", 1)[0].strip()
        if not target or target.startswith(IGNORE_PREFIXES):
            continue
        resolved = (file_path.parent / target).resolve()
        if not resolved.exists():
            missing.append((file_path.relative_to(ROOT), raw_target))

if missing:
    for source, target in missing:
        print(f"MISSING LINK: {source} -> {target}")
    sys.exit(1)

print("All checked local markdown links exist.")
