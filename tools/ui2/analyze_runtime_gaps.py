#!/usr/bin/env python3
"""Cluster unmatched runtime rows so toolbar mirrors are补册 once, not per layout variant."""

from __future__ import annotations

import csv
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV = ROOT / "docs/ui2/runtime-mapping.csv"
OUT = ROOT / "docs/ui2/runtime-gap-clusters.md"


def norm(text: str) -> str:
    text = re.sub(r"\s*[\(\[（【]\s*[A-Za-z0-9+\-\[\]\s]*\s*[\)\]）】]\s*$", "", text or "")
    return text.replace(" ", "").replace("/", "")


def main() -> None:
    rows = [r for r in csv.DictReader(CSV.open(encoding="utf-8-sig")) if r["bucket"] == "unmatched"]
    clusters: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        parent = row["path"].rsplit("/", 2)[-2] if "/" in row["path"] else ""
        clusters[(row["top_pos"], norm(row["text"]))].append(row)
    ordered = sorted(clusters.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    with OUT.open("w", encoding="utf-8") as f:
        f.write("# Runtime Gap Clusters (generated)\n\n")
        f.write(f"Unmatched rows: **{len(rows)}**; clusters: **{len(ordered)}**.\n\n")
        f.write("| Count | Top | Text | Example path |\n|---:|---|---|---|\n")
        for (top, text), items in ordered:
            f.write(f"| {len(items)} | {top} | {text} | `{items[0]['path']}` |\n")
    print(f"unmatched={len(rows)} clusters={len(ordered)}")
    for (top, text), items in ordered[:30]:
        print(f"{len(items):2d}  {top:12s} {text}")


if __name__ == "__main__":
    main()
