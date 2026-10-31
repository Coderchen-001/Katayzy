"""Inventory every visible Swing surface before UI2 rendering changes.

The feature inventory answers "what actions exist". This inventory answers "which screens and
custom paint sites must be reskinned" so no old-looking surface is left behind.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src/main/java/featurecat/lizzie/gui"
OUT = ROOT / "docs/ui2/ui-surface-inventory.csv"

PATTERNS = {
    "custom-paint": re.compile(r"\bvoid\s+paintComponent\s*\(|\bvoid\s+paint\s*\("),
    "dialog": re.compile(r"\bextends\s+JDialog\b|\bnew\s+JDialog\s*\("),
    "popup": re.compile(r"\bextends\s+JPopupMenu\b|\bnew\s+JPopupMenu\s*\("),
    "frame": re.compile(r"\bextends\s+JFrame\b|\bnew\s+JFrame\s*\("),
    "graphics2d": re.compile(r"\bGraphics2D\b"),
}


def main() -> None:
    rows = []
    for path in sorted(GUI.glob("*.java")):
        text = path.read_text(encoding="utf-8")
        kinds = []
        evidence = []
        for kind, pattern in PATTERNS.items():
            matches = list(pattern.finditer(text))
            if matches:
                kinds.append(kind)
                line = text[: matches[0].start()].count("\n") + 1
                evidence.append(f"{kind}@{line}")
        if not kinds:
            continue
        surface = next(
            (k for k in ("frame", "dialog", "popup", "custom-paint") if k in kinds),
            "panel",
        )
        rows.append(
            {
                "id": f"UI-{len(rows) + 1:03d}",
                "class": path.name,
                "surface_type": surface,
                "all_signals": "+".join(kinds),
                "evidence": ";".join(evidence),
                "replacement": "",
                "status": "未探查",
                "notes": "先确认实际可见状态，再决定 FlatLaf/MigLayout/保留渲染",
            }
        )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["id", "class", "surface_type", "all_signals", "evidence", "replacement", "status", "notes"],
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {OUT} rows={len(rows)}")


if __name__ == "__main__":
    main()
