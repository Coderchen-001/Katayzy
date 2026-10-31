"""Classify label-audit records as entry / non-entry and check inventory closure.

Gate 1 closure is about entry-bearing records, not every dialog string in the audit.
"""

from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "docs/ui2/menu-label-audit.csv"
FEATURES = ROOT / "docs/ui2/feature-inventory.csv"
OUT_CSV = ROOT / "docs/ui2/audit-closure.csv"
OUT_MD = ROOT / "docs/ui2/audit-closure.md"
ALLOWED = {"Menu.java", "BottomToolbar.java", "RightClickMenu.java", "RightClickMenu2.java"}
ENTRY_RE = re.compile(r"JFontMenu|JFontMenuItem|JFontCheckBoxMenuItem|JFontRadioButtonMenuItem|JMenu")


def main() -> None:
    features = list(csv.DictReader(FEATURES.open(encoding="utf-8")))
    labels = {(r.get("label") or "").strip() for r in features if r.get("label")}
    notes = " ".join((r.get("notes") or "") for r in features)
    audit = list(csv.DictReader(AUDIT.open(encoding="utf-8")))
    rows = []
    for record in audit:
        source = record["source"]
        if source not in ALLOWED:
            continue
        path = ROOT / "src/main/java/featurecat/lizzie/gui" / source
        if not path.exists():
            continue
        line_number = int(record["line"])
        lines = path.read_text(encoding="utf-8").splitlines()
        line = lines[line_number - 1] if 0 < line_number <= len(lines) else ""
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            disposition = "comment"
        elif "new JFontMenu" in line or "new JMenu" in line:
            disposition = "container"
        elif ENTRY_RE.search(line):
            label = (record.get("label") or "").strip()
            key = (record.get("key") or "").strip()
            if label and (label in labels or key and key in notes):
                disposition = "mapped"
            elif "for (" in line or "while (" in line:
                disposition = "dynamic"
            else:
                disposition = "omission"
        else:
            disposition = "non-entry"
        rows.append(
            {
                "source": source,
                "line": str(line_number),
                "kind": record.get("kind", ""),
                "key": record.get("key", ""),
                "label": record.get("label", ""),
                "disposition": disposition,
            }
        )
    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=["source", "line", "kind", "key", "label", "disposition"]
        )
        writer.writeheader()
        writer.writerows(rows)
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["disposition"]] = counts.get(row["disposition"], 0) + 1
    unmatched = [r for r in rows if r["disposition"] == "omission"]
    with OUT_MD.open("w", encoding="utf-8") as f:
        f.write("# Entry Audit Closure\n\n")
        f.write("Scope: Menu.java, BottomToolbar.java, RightClickMenu*.java.\n\n")
        f.write("Buckets: mapped / container / dynamic / non-entry / comment / omission. "
                "Every entry-bearing record must land in one bucket.\n\n")
        f.write("| Disposition | Count |\n|---|---:|\n")
        for key, count in sorted(counts.items()):
            f.write(f"| {key} | {count} |\n")
        f.write("\n## Omission entry records\n\n")
        if unmatched:
            f.write("| Source | Line | Key/label |\n|---|---:|---|\n")
            for r in unmatched:
                f.write(f"| {r['source']} | {r['line']} | `{r['key'] or r['label']}` |\n")
        else:
            f.write("None.\n")
    print(f"wrote {OUT_CSV} rows={len(rows)} counts={counts} unmapped={len(unmatched)}")


if __name__ == "__main__":
    main()
