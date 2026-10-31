"""Extract the real topPanel widget mounts.

The menu bar is only the 12 JMenu entries. Top-bar controls such as komi, player stones and
analysis toggles are added to `Lizzie.frame.topPanel` from Menu.java; they are a separate surface.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MENU = ROOT / "src/main/java/featurecat/lizzie/gui/Menu.java"
OUT = ROOT / "docs/ui2/top-panel-inventory.csv"
ADD_RE = re.compile(r"Lizzie\.frame\.topPanel\.add\(\s*(\w+)\s*\)")


def main() -> None:
    text = MENU.read_text(encoding="utf-8").splitlines()
    rows = []
    for line_number, line in enumerate(text, 1):
        match = ADD_RE.search(line)
        if not match:
            continue
        variable = match.group(1)
        if 7900 <= line_number <= 7960:
            group = "players/stop"
        elif 8300 <= line_number <= 8330:
            group = "select-allow-avoid"
        elif 8440 <= line_number <= 8460:
            group = "double-game"
        elif 8880 <= line_number <= 8960:
            group = "komi-pda-wrn-time"
        elif 9100 <= line_number <= 9180:
            group = "analysis-engine-controls"
        elif line_number == 6904:
            group = "byoyomi-time"
        elif line_number == 7569:
            group = "double-menu"
        else:
            group = "other"
        rows.append(
            {
                "id": f"TP-{len(rows) + 1:03d}",
                "variable": variable,
                "source_line": str(line_number),
                "group": group,
                "surface": "LizzieFrame.topPanel",
                "status": "待分类",
                "notes": "由 Menu.java 的 topPanel.add 提取；不是菜单栏成员",
            }
        )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f, fieldnames=["id", "variable", "source_line", "group", "surface", "status", "notes"]
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {OUT} rows={len(rows)}")


if __name__ == "__main__":
    main()
