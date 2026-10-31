#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bucket the runtime menu dump against feature-inventory.csv.

The dump is produced by:
  Katayzy.exe --ui2-dump=docs/ui2/golden/... --ui2-smoke
Only the ModelJMenuBar subtree is mapped here; the content tree is a separate surface.

Outputs docs/ui2/runtime-mapping.csv and docs/ui2/runtime-mapping.md.
"""

from __future__ import annotations

import csv
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/ui2"))
from verify_paths import norm  # noqa: E402

INV = ROOT / "docs/ui2/feature-inventory.csv"
DUMP = ROOT / "docs/ui2/golden/smoke-zh_CN.txt"
OUT_CSV = ROOT / "docs/ui2/runtime-mapping.csv"
OUT_MD = ROOT / "docs/ui2/runtime-mapping.md"

LINE_RE = re.compile(
    r"^\s*(?P<path>\S+) \| (?P<class>\w+) \| text=\"(?P<text>(?:\\.|[^\"\\])*)\""
    r" \| mnemonic=(?P<mnemonic>[^|]*) \| accelerator=(?P<accel>[^|]*)"
    r" \| selected=(?P<selected>[^|]*) \| visible=(?P<visible>\w+)"
    r" \| enabled=(?P<enabled>\w+)\s*$"
)
LEAF_CLASSES = {"JFontMenuItem", "JFontCheckBoxMenuItem", "JRadioButtonMenuItem",
                "InfoMenuItem", "JFontLabel", "JFontButton"}
SEPARATOR_CLASSES = {"ToolbarSeparator", "JPopupMenu"}
KEY_SUFFIX = re.compile(r"\s*[\(\[（【]\s*[A-Za-z0-9+\-\[\]\s]*\s*[\)\]）】]\s*$")


def unquote(value: str) -> str:
    return (
        value.replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
    )


def parse_dump() -> list[dict[str, str]]:
    rows = []
    for line in DUMP.read_text(encoding="utf-8").splitlines():
        m = LINE_RE.match(line)
        if not m:
            continue
        row = m.groupdict()
        row["text"] = unquote(row["text"])
        if row["path"].startswith("ModelJMenuBar"):
            rows.append(row)
    return rows


def top_menu(path: str) -> str:
    parts = path.split("/")
    return parts[1] if len(parts) > 1 else ""


def norm_text(value: str) -> str:
    return norm(KEY_SUFFIX.sub("", value or ""))


def main() -> None:
    inventory = [r for r in csv.DictReader(INV.open(encoding="utf-8-sig"))]
    menu_rows = [r for r in inventory if r["entry_type"] == "menu"]
    by_label: dict[str, dict[str, dict[str, str]]] = {}
    for row in menu_rows:
        for key in {norm_text(row["label"]), norm_text(row["path"].split(">")[-1])}:
            by_label.setdefault(key, {})[row["id"]] = row

    runtime = parse_dump()
    classes = Counter(r["class"] for r in runtime)
    top_menus = [r for r in runtime if r["path"].count("/") == 1]
    popups = [r for r in runtime if r["class"] == "JPopupMenu"]
    separators = [r for r in runtime if r["class"] in {"Separator", "ToolbarSeparator"}]
    leaves = [r for r in runtime if r["class"] in LEAF_CLASSES]

    out = []
    counts = Counter()
    seen_inventory = set()
    for row in leaves:
        text = norm_text(row["text"])
        candidates = list(by_label.get(text, {}).values())
        # Duplicate labels are disambiguated only by exact parent path text.
        parent_text = norm_text(row["path"].split("/")[-3]) if row["path"].count("/") >= 3 else ""
        exact = [c for c in candidates if norm_text(c["path"].split(">")[-2] if ">" in c["path"] else "") == parent_text]
        chosen = exact[0] if len(exact) == 1 else (candidates[0] if len(candidates) == 1 else None)
        if row["visible"].lower() == "false":
            bucket = "config-hidden"
        elif "引擎" in row["path"] or "未加载引擎" in row["text"] or "引擎2" in row["path"]:
            bucket = "engine-dynamic"
        elif chosen is not None:
            bucket = "matched"
            seen_inventory.add(chosen["id"])
        else:
            bucket = "unmatched"
        counts[bucket] += 1
        out.append(
            {
                "path": row["path"],
                "top_menu": top_menu(row["path"]),
                "class": row["class"],
                "text": row["text"],
                "visible": row["visible"],
                "enabled": row["enabled"],
                "bucket": bucket,
                "inventory_id": chosen["id"] if chosen is not None else "",
                "evidence": "normalized label match" if chosen is not None else "",
            }
        )

    # Structural glue is counted but never treated as a feature.
    counts["separator"] = len(separators)
    counts["popup-glue"] = len(popups)
    # 437 = 340 leaves + 47 separators + 50 popup rows. JMenu container rows and the root
    # Menu row are structural wrappers, not part of the leaf accounting.
    leaf_total = len(leaves)
    structural = leaf_total + len(separators) + len(popups)
    containers = sum(1 for r in runtime if r["class"] in {"JMenu", "JFontMenu", "Menu"})
    if structural != 437:
        raise SystemExit(f"structural mismatch: expected 437, got {structural}")

    missing = [
        r["id"]
        for r in menu_rows
        if r["id"] not in seen_inventory
        and not r.get("path_exempt")
        and r.get("migration_status") != "no-entry"
    ]
    with OUT_CSV.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
        w.writeheader()
        w.writerows(out)
    with OUT_MD.open("w", encoding="utf-8") as f:
        f.write("# Runtime Menu Mapping (generated)\n\n")
        f.write(f"Structural rows: **{structural} = {leaf_total} leaves + {len(separators)} separators + {len(popups)} popups**\n\n")
        f.write("| Bucket | Count |\n|---|---:|\n")
        for key in ["matched", "toolbar-mirror", "engine-dynamic", "config-hidden", "unmatched", "separator", "popup-glue"]:
            f.write(f"| {key} | {counts[key]} |\n")
        f.write(f"\nLeaf total: **{leaf_total}**; five feature buckets sum: **{sum(counts[k] for k in ['matched','toolbar-mirror','engine-dynamic','config-hidden','unmatched'])}**\n")
        f.write("\n## Unmapped inventory rows\n\n")
        for pid in missing:
            f.write(f"- `{pid}`\n")
        f.write("\n## Unmatched runtime rows\n\n")
        for row in out:
            if row["bucket"] == "unmatched":
                f.write(f"- `{row['path']}`  text={row['text']!r}\n")
    print(f"structural={structural} leaves={leaf_total} separators={len(separators)} popups={len(popups)}")
    print(dict(counts))
    print(f"unmatched={counts['unmatched']} inventory_missing={len(missing)}")


if __name__ == "__main__":
    main()
