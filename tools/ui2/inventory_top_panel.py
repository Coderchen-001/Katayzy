"""Extract the real topPanel widget mounts.

The menu bar is only the 12 JMenu entries. Top-bar controls such as komi, player stones and
analysis toggles are added to `Lizzie.frame.topPanel` from Menu.java; they are a separate surface.

Line comments are stripped before matching and multi-line `add(...)` calls are balanced, so
commented-out additions and split arguments are handled correctly.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MENU = ROOT / "src/main/java/featurecat/lizzie/gui/Menu.java"
OUT = ROOT / "docs/ui2/top-panel-inventory.csv"
ADD = "Lizzie.frame.topPanel.add("
NESTED_ADD = re.compile(r"Lizzie\.frame\.topPanel\.(leftArea|centerArea|rightArea)\.add\(\s*(\w+)\s*\)")
LABEL_KEY_RE = re.compile(r'getString\(\s*"([^"]+)"')


def strip_line_comment(line: str) -> str:
    in_string = False
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\" and in_string:
            escaped = True
            continue
        if char == '"':
            in_string = not in_string
        elif not in_string and char == "/" and index + 1 < len(line) and line[index + 1] == "/":
            return line[:index]
    return line


def extract_calls(lines: list[str]) -> list[tuple[int, str]]:
    calls: list[tuple[int, str]] = []
    index = 0
    while index < len(lines):
        clean = strip_line_comment(lines[index])
        position = clean.find(ADD)
        if position < 0:
            index += 1
            continue
        start_line = index + 1
        text = clean[position:]
        depth = text.count("(") - text.count(")")
        while depth > 0 and index + 1 < len(lines):
            index += 1
            next_line = strip_line_comment(lines[index])
            text += " " + next_line
            depth += next_line.count("(") - next_line.count(")")
        argument = text[len(ADD) :].strip()
        calls.append((start_line, argument))
        index += 1
    return calls


def main() -> None:
    lines = MENU.read_text(encoding="utf-8").splitlines()
    rows = []
    for line_number, argument in extract_calls(lines):
        if argument.startswith("new JFontLabel"):
            match = LABEL_KEY_RE.search(argument)
            variable = match.group(1).split(".")[-1] if match else "label"
            variable = variable.replace("-", "")
        else:
            variable = re.match(r"(\w+)", argument).group(1)
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
        notes = "由 Menu.java 的 topPanel.add 提取；不是菜单栏成员"
        if variable == "lblLimit":
            notes += "；条件可见：showTimeControlInMenu || showPlayoutControlInMenu"
        if variable in {"black", "white", "blackwhite", "playPass", "clearSelect", "chkShowWinrate"}:
            notes += "；另有 toolPanel 变体挂载 L9278-9287"
        rows.append(
            {
                "id": f"TP-{len(rows) + 1:03d}",
                "variable": variable,
                "source_line": str(line_number),
                "group": group,
                "surface": "LizzieFrame.topPanel",
                "status": "待分类",
                "notes": notes,
            }
        )
    direct_vars = {row["variable"] for row in rows}
    for line_number, line in enumerate(lines, 1):
        match = NESTED_ADD.search(strip_line_comment(line))
        if not match:
            continue
        area, variable = match.group(1), match.group(2)
        if variable in direct_vars:
            continue
        notes = f"嵌套在 topPanel.{area}；由 Menu.java 提取；不是菜单栏成员"
        if variable == "btnRankMark":
            notes += "；点击弹出 rankMarkPopup，复用 addRankMarkMenu L8472 子树"
        rows.append(
            {
                "id": f"TP-{len(rows) + 1:03d}",
                "variable": variable,
                "source_line": str(line_number),
                "group": "nested-controls",
                "surface": f"LizzieFrame.topPanel.{area}",
                "status": "待分类",
                "notes": notes,
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
