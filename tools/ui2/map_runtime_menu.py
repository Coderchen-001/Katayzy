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
DUMP = ROOT / "docs/ui2/golden/samples/smoke-zh_CN.txt"
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
    by_id = {r["id"]: r for r in menu_rows}
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

    path_text = {r["path"]: norm_text(r["text"]) for r in runtime}

    def top_menu_text(path: str) -> str:
        parts = path.split("/")
        return path_text.get("/".join(parts[:2]), "")

    def choose_inventory(row: dict[str, str]) -> tuple[dict[str, str] | None, bool, str]:
        text = norm_text(row["text"])
        candidates = list(by_label.get(text, {}).values())
        evidence = "normalized label match"
        if not candidates:
            # Controlled prefix fallback for Chinese annotations such as
            # "经典配色（原版 lizzieyzy）". Never match a bare prefix without a parenthetical.
            for key, ids in by_label.items():
                suffix = text[len(key) :] if key else ""
                if key and text.startswith(key) and (
                    suffix.startswith(("(", "（"))
                    or re.fullmatch(r":\d+", suffix)
                    or (key.endswith(":") and suffix.isdigit())
                ):
                    candidates.extend(ids.values())
            if candidates:
                evidence = "parenthetical annotation prefix match"
        parent_path = row["path"].rsplit("/", 1)[0]
        if parent_path.endswith("/popup"):
            # The popup row has empty text; the real container is its parent.
            parent_path = parent_path.rsplit("/", 1)[0]
        parent_text = path_text.get(parent_path, "")
        exact = [
            c
            for c in candidates
            if norm_text(c["path"].split(">")[-2] if ">" in c["path"] else "") == parent_text
        ]
        if len({c["id"] for c in exact}) > 1:
            # Top/bottom toolbar both have a child named 自定义内容; use the grandparent text.
            grand_path = parent_path.rsplit("/", 1)[0]
            grand_text = path_text.get(grand_path, "")
            exact = [
                c
                for c in exact
                if len(c["path"].split(">")) >= 3
                and norm_text(c["path"].split(">")[-3]) == grand_text
            ]
        # Conflict is about the full candidate set, not the post-parent subset.
        conflict = len({c["id"] for c in candidates}) > 1
        if len({c["id"] for c in exact}) == 1:
            return exact[0], conflict, evidence
        unique = {c["id"]: c for c in candidates}
        if len(unique) == 1:
            return next(iter(unique.values())), False, evidence
        return None, conflict, evidence

    out = []
    counts = Counter()
    seen_inventory = set()
    container_rows = [r for r in runtime if r["class"] in {"JMenu", "JFontMenu"}]
    # Containers participate in the reverse check but not in the 340-leaf bucket accounting.
    for row in container_rows:
        chosen, _, _ = choose_inventory(row)
        top_segment = row["path"].split("/")[1] if "/" in row["path"] else ""
        if chosen is None and top_segment in {"JFontMenu#10", "JFontMenu#11"}:
            chosen = by_id.get("M-ENG-001" if top_segment == "JFontMenu#10" else "M-ENG-011")
        if chosen is not None:
            seen_inventory.add(chosen["id"])

    for row in leaves:
        composite = ""
        if not row["text"].strip():
            children = [
                r
                for r in runtime
                if r["path"].startswith(row["path"] + "/")
                and r["class"] in {"JFontLabel", "JFontButton"}
                and r["text"].strip()
            ]
            children.sort(key=lambda r: 0 if r["class"] == "JFontLabel" else 1)
            if children:
                row = dict(row)
                row["text"] = children[0]["text"]
                composite = f"composite child {children[0]['class']}"
        chosen, conflict, evidence = choose_inventory(row)
        if composite:
            evidence = composite + ("; " + evidence if evidence else "")
        top_segment = row["path"].split("/")[1] if "/" in row["path"] else ""
        if chosen is None and row["class"] == "JFontMenu" and top_segment in {"JFontMenu#10", "JFontMenu#11"}:
            chosen = by_id.get("M-ENG-001" if top_segment == "JFontMenu#10" else "M-ENG-011")
            evidence = "engine top-level position map"
        if chosen is not None:
            # Even hidden/dynamic rows count as inventory-seen; otherwise the reverse check lies.
            seen_inventory.add(chosen["id"])
        top_text = top_menu_text(row["path"])
        top_segment = row["path"].split("/")[1] if "/" in row["path"] else ""
        if top_text == "未加载引擎" or top_segment in {"JFontMenu#10", "JFontMenu#11"}:
            bucket = "engine-dynamic"
        elif row["visible"].lower() == "false":
            bucket = "config-hidden"
        elif chosen is not None:
            bucket = "matched"
        else:
            bucket = "unmatched"
        if conflict:
            evidence = "conflict: multiple inventory candidates"
        if bucket == "engine-dynamic" and row["visible"].lower() == "false":
            evidence = (evidence + "; " if evidence else "") + "engine subtree hidden"
        counts[bucket] += 1
        out.append(
            {
                "path": row["path"],
                "top_pos": top_segment,
                "top_menu": top_text or top_menu(row["path"]),
                "class": row["class"],
                "text": row["text"],
                "visible": row["visible"],
                "enabled": row["enabled"],
                "bucket": bucket,
                "inventory_id": chosen["id"] if chosen is not None else "",
                "evidence": evidence,
            }
        )

    claimed = Counter(r["inventory_id"] for r in out if r["inventory_id"])
    for row in out:
        if row["inventory_id"] and claimed[row["inventory_id"]] > 1:
            row["evidence"] = (
                row["evidence"] + "; " if row["evidence"] else ""
            ) + f"mirror/duplicate claim x{claimed[row['inventory_id']]}"

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

    engine_expected = {"M-ENG-004", "M-ENG-007", "M-ENG-009", "M-ENG-010"}
    missing = [
        r["id"]
        for r in menu_rows
        if r["id"] not in seen_inventory
        and not r.get("path_exempt")
        and r.get("migration_status") != "no-entry"
    ]
    exempt_missing = [
        r["id"]
        for r in menu_rows
        if r["id"] not in seen_inventory
        and r.get("path_exempt")
        and r["id"] not in engine_expected
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
        f.write("\n## Exempt but unmatched inventory rows\n\n")
        f.write("These are not silently dropped: they are exempt from the path gate but must still be\n")
        f.write("reviewed for runtime coverage.\n\n")
        for pid in exempt_missing:
            f.write(f"- `{pid}`\n")
        f.write("\n## Gap candidates (unmatched runtime rows)\n\n")
        f.write("Split by whether the inventory has any label candidate at all. A no-candidate row is a\n")
        f.write("likely omission; a candidate-unassigned row is a path/alias problem.\n\n")
        for row in out:
            if row["bucket"] != "unmatched":
                continue
            candidates = by_label.get(norm_text(row["text"]), {})
            group = "candidate unassigned" if candidates else "no inventory candidate"
            f.write(f"- [{group}] `{row['path']}`  text={row['text']!r}\n")
        f.write("\n## Unmatched runtime rows\n\n")
        for row in out:
            if row["bucket"] == "unmatched":
                f.write(f"- `{row['path']}`  text={row['text']!r}\n")
    print(f"structural={structural} leaves={leaf_total} separators={len(separators)} popups={len(popups)}")
    print(dict(counts))
    print(f"unmatched={counts['unmatched']} inventory_missing={len(missing)} exempt_unmatched={len(exempt_missing)}")


if __name__ == "__main__":
    main()
