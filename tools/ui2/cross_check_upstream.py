"""Compare menu-bearing resource keys between the UI2 tree and upstream 741c301.

This is a two-way check:
- ref-only: upstream has an entry that the current inventory may have lost;
- current-only: the current fork added a feature; mark it origin: katayzy-new.

It intentionally scans only menu-bearing files. Dialog copy and error text are not features.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

CURRENT = Path(r"D:\copy\全新重构项目-卡塔狗桌面版\修改源码（GitHub同步）\Katayzy")
REFERENCE = Path(r"D:\temp\lizzieyzy")
OUT = CURRENT / "docs/ui2/upstream-cross-check.csv"
FILES = ("Menu.java", "BottomToolbar.java", "RightClickMenu.java", "RightClickMenu2.java")
KEY_RE = re.compile(r'getString\("([^"]+)"\)')


def decode(value: str) -> str:
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), value)


def load_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if not path.exists():
        return result
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = decode(value.strip())
    return result


def scan(root: Path, props: dict[str, str]) -> dict[str, dict[str, str]]:
    found: dict[str, dict[str, str]] = {}
    gui = root / "src/main/java/featurecat/lizzie/gui"
    for name in FILES:
        path = gui / name
        if not path.exists():
            continue
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for match in KEY_RE.finditer(line):
                key = match.group(1)
                found.setdefault(
                    key,
                    {
                        "key": key,
                        "label": props.get(key, ""),
                        "source": f"{name}:{lineno}",
                    },
                )
    return found


def main() -> None:
    current_props = load_properties(
        CURRENT / "src/main/resources/l10n/DisplayStrings_zh_CN.properties"
    )
    ref_props = load_properties(
        REFERENCE / "src/main/resources/l10n/DisplayStrings_zh_CN.properties"
    )
    current = scan(CURRENT, current_props)
    reference = scan(REFERENCE, ref_props)
    keys = sorted(set(current) | set(reference))
    rows = []
    for key in keys:
        c = current.get(key)
        r = reference.get(key)
        status = "both" if c and r else ("current-only" if c else "ref-only")
        rows.append(
            {
                "key": key,
                "label": (c or r)["label"],
                "status": status,
                "current_source": c["source"] if c else "",
                "reference_source": r["source"] if r else "",
            }
        )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["key", "label", "status", "current_source", "reference_source"],
        )
        writer.writeheader()
        writer.writerows(rows)
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    print(f"wrote {OUT}")
    print(f"keys={len(rows)} counts={counts}")
    for status in ("ref-only", "current-only"):
        print(f"--- {status} sample ---")
        for row in [x for x in rows if x["status"] == status][:20]:
            print(row["key"], row["label"])


if __name__ == "__main__":
    main()
