"""Cross-check feature-inventory dialog references against ui-surface-inventory.

This is a review aid, not a proof: dialog labels are often descriptive ("文件选择器",
"新建对局对话框"), so unmatched rows are marked for manual review rather than declared missing.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATURES = ROOT / "docs/ui2/feature-inventory.csv"
SURFACES = ROOT / "docs/ui2/ui-surface-inventory.csv"
OUT = ROOT / "docs/ui2/surface-coverage.md"
GENERIC = {"", "—", "-", "无", "文件选择器", "确认框", "浏览器", "输入框", "对话框"}


def main() -> None:
    features = list(csv.DictReader(FEATURES.open(encoding="utf-8")))
    surfaces = list(csv.DictReader(SURFACES.open(encoding="utf-8")))
    by_class = {row["class"]: row for row in surfaces}
    unmatched = []
    matched = []
    for row in features:
        dialog = (row.get("dialog") or "").strip()
        if dialog in GENERIC:
            continue
        tokens = [t.strip() for t in re.split(r"[/,，、;；()（）\s]+", dialog) if t.strip()]
        hits = []
        for token in tokens:
            if token in by_class:
                hits.append(token)
            else:
                for cls in by_class:
                    if token and (token in cls or cls.removesuffix(".java") in token):
                        hits.append(cls)
        hits = sorted(set(hits))
        record = (row["id"], dialog, row["path"], hits)
        if hits:
            matched.append(record)
        else:
            unmatched.append(record)
    with OUT.open("w", encoding="utf-8") as f:
        f.write("# Surface Coverage\n\n")
        f.write(f"- feature dialog references: {len(matched) + len(unmatched)}\n")
        f.write(f"- matched to surface classes: {len(matched)}\n")
        f.write(f"- needs manual mapping: {len(unmatched)}\n\n")
        f.write("## Needs manual mapping\n\n")
        f.write("Descriptive dialog names may be valid even when they do not match a class name.\n\n")
        f.write("| Feature ID | Dialog reference | Menu path |\n|---|---|---|\n")
        for fid, dialog, path, _ in unmatched:
            f.write(f"| {fid} | {dialog} | {path} |\n")
        f.write("\n## Matched references\n\n")
        f.write("| Feature ID | Dialog reference | Surface classes |\n|---|---|---|\n")
        for fid, dialog, _path, hits in matched:
            f.write(f"| {fid} | {dialog} | {', '.join(hits)} |\n")
    print(f"wrote {OUT} matched={len(matched)} unmatched={len(unmatched)}")


if __name__ == "__main__":
    main()
