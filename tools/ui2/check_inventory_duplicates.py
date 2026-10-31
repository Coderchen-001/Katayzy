"""Mechanical duplicate checks for docs/ui2/feature-inventory.csv.

A duplicate is:
- two rows with the same ID;
- two rows with the same normalized path + action (same UI entry);
- empty required fields.

Different paths may legitimately share an Action (menu + toolbar); that is not a duplicate.
"""

from __future__ import annotations

import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "docs/ui2/feature-inventory.csv"
REQUIRED = ("id", "entry_type", "path", "label", "action", "state_matrix")


def main() -> int:
    rows = list(csv.DictReader(CSV_PATH.open(encoding="utf-8")))
    errors: list[str] = []

    ids = Counter(r.get("id", "") for r in rows)
    for key, count in ids.items():
        if not key:
            errors.append("empty id")
        elif count > 1:
            errors.append(f"duplicate id: {key} x{count}")

    by_path_action: dict[tuple[str, str], list[str]] = defaultdict(list)
    for r in rows:
        by_path_action[(r.get("path", "").strip(), r.get("action", "").strip())].append(r.get("id", ""))
    for (path, action), ids_for_key in by_path_action.items():
        if path and action and len(ids_for_key) > 1:
            errors.append(f"duplicate path+action: {path} / {action} -> {ids_for_key}")

    for index, r in enumerate(rows, 2):
        for field in REQUIRED:
            if not r.get(field, "").strip():
                errors.append(f"line {index}: empty {field} ({r.get('id', '?')})")

    if errors:
        print("FAIL")
        for error in errors:
            print("-", error)
        return 1
    print(f"OK rows={len(rows)} unique_ids={len(ids)} unique_path_action={len(by_path_action)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
