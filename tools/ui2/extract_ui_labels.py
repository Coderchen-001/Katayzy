"""Build a label audit for UI2.

The authoritative Chinese menu labels live in l10n/DisplayStrings_zh_CN.properties,
not in Java string literals. This script scans the UI code for resource keys and
hard-coded labels, resolves keys through the properties file, and writes a CSV
that can be used to validate docs/ui2/feature-inventory.csv.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "src/main/java/featurecat/lizzie"
PROPS = ROOT / "src/main/resources/l10n/DisplayStrings_zh_CN.properties"
OUT = ROOT / "docs/ui2/menu-label-audit.csv"

KEY_RE = re.compile(r'getString\("([^"]+)"\)')
HARD_RE = re.compile(
    r'new JFont(?:Menu|MenuItem|CheckBoxMenuItem|RadioButtonMenuItem)\("([^"]*)"\)'
)


def decode_properties(value: str) -> str:
    # Properties files store non-ASCII as \uXXXX. Decode only those escapes so
    # already-readable Chinese text is not mangled by unicode_escape.
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), value)


def load_properties() -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in PROPS.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = decode_properties(value.strip())
    return result


def source_files() -> list[Path]:
    files: list[Path] = []
    for pattern in ("gui/*.java", "*.java"):
        files.extend(JAVA_ROOT.glob(pattern))
    return sorted(set(files))


def main() -> None:
    labels = load_properties()
    rows: list[dict[str, str]] = []
    unresolved: set[str] = set()
    for path in source_files():
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for match in KEY_RE.finditer(line):
                key = match.group(1)
                label = labels.get(key, "")
                if not label:
                    unresolved.add(key)
                rows.append(
                    {
                        "source": path.name,
                        "line": str(lineno),
                        "kind": "resource-key",
                        "key": key,
                        "label": label,
                    }
                )
            for match in HARD_RE.finditer(line):
                rows.append(
                    {
                        "source": path.name,
                        "line": str(lineno),
                        "kind": "hard-coded",
                        "key": "",
                        "label": match.group(1),
                    }
                )

    rows.sort(key=lambda r: (r["key"] or r["label"], r["source"], int(r["line"])))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=["source", "line", "kind", "key", "label"]
        )
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {OUT}")
    print(f"rows={len(rows)} unresolved_keys={len(unresolved)}")
    if unresolved:
        print("unresolved:", ", ".join(sorted(unresolved)[:20]))


if __name__ == "__main__":
    main()
