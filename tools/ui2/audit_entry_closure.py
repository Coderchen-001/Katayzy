"""Classify label-audit records and close the entry-bearing audit.

Buckets:
- mapped: label/key exists in feature-inventory;
- container: JMenu/JFontMenu label maps to a container row; path is checked;
- dynamic: menu item is built in a loop/conditional and cannot be statically matched;
- non-entry: dialog copy, tooltip, error text, config key;
- comment: commented-out code;
- omission: a real entry that still has no inventory row.

Gate 1 closure is “every entry-bearing record lands in one bucket”, not “every GUI string is a
feature”.
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
LOOP_RE = re.compile(r"\b(for|while)\s*\(")
CONTAINER_RE = re.compile(r"new\s+JFontMenu\s*\(")


def is_dynamic(lines: list[str], line_index: int, label: str, key: str) -> bool:
    text = f"{label} {key}".lower()
    if any(token in text for token in ("engine", "detailedbar", "detailed_bar")):
        return True
    window = lines[max(0, line_index - 100) : line_index + 1]
    if any(LOOP_RE.search(line) for line in window):
        return True
    line = lines[line_index] if 0 <= line_index < len(lines) else ""
    return "engine[" in line or "getEngineData" in line


def main() -> None:
    features = list(csv.DictReader(FEATURES.open(encoding="utf-8")))
    labels = {(r.get("label") or "").strip() for r in features if r.get("label")}
    notes = " ".join((r.get("notes") or "") for r in features)
    by_label: dict[str, dict[str, str]] = {}
    for feature in features:
        by_label.setdefault((feature.get("label") or "").strip(), feature)

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
        label = (record.get("label") or "").strip()
        key = (record.get("key") or "").strip()
        feature_id = ""
        path_status = ""

        if stripped.startswith("//") or stripped.startswith("*"):
            disposition = "comment"
        elif CONTAINER_RE.search(line) or "new JMenu(" in line:
            feature = by_label.get(label)
            if feature:
                disposition = "container"
                feature_id = feature.get("id", "")
                feature_path = (feature.get("path") or "")
                clean_label = re.sub(r"\([^)]*\)\s*$", "", label).strip()
                path_status = (
                    "PASS"
                    if clean_label and clean_label in feature_path
                    else "CHECK"
                )
            else:
                disposition = "omission"
        elif ENTRY_RE.search(line):
            if label and (label in labels or key and key in notes):
                disposition = "mapped"
            elif is_dynamic(lines, line_number - 1, label, key):
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
                "feature_id": feature_id,
                "path_status": path_status,
            }
        )

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "source", "line", "kind", "key", "label",
                "disposition", "feature_id", "path_status",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)

    counts: dict[str, int] = {}
    for row in rows:
        counts[row["disposition"]] = counts.get(row["disposition"], 0) + 1
    omissions = [r for r in rows if r["disposition"] == "omission"]
    dynamic = [r for r in rows if r["disposition"] == "dynamic"]
    container_fail = [
        r for r in rows if r["disposition"] == "container" and r["path_status"] != "PASS"
    ]

    with OUT_MD.open("w", encoding="utf-8") as f:
        f.write("# Entry Audit Closure\n\n")
        f.write("Scope: Menu.java, BottomToolbar.java, RightClickMenu*.java.\n\n")
        f.write(
            "Buckets: mapped / container / dynamic / non-entry / comment / omission. "
            "Every entry-bearing record must land in one bucket.\n\n"
        )
        f.write("| Disposition | Count |\n|---|---:|\n")
        for key_name, count in sorted(counts.items()):
            f.write(f"| {key_name} | {count} |\n")

        f.write("\n## Container path check\n\n")
        f.write(f"Container rows checked: {sum(1 for r in rows if r['disposition'] == 'container')}; "
                f"path failures: {len(container_fail)}.\n\n")
        if container_fail:
            f.write("| Source | Line | Label | Feature ID |\n|---|---:|---|---|\n")
            for r in container_fail:
                f.write(f"| {r['source']} | {r['line']} | {r['label']} | {r['feature_id']} |\n")

        f.write("\n## Dynamic entries\n\n")
        if dynamic:
            f.write("| Source | Line | Key/label |\n|---|---:|---|\n")
            for r in dynamic:
                f.write(f"| {r['source']} | {r['line']} | `{r['key'] or r['label']}` |\n")
        else:
            f.write("None.\n")

        f.write("\n## Omission entry records\n\n")
        if omissions:
            f.write("| Source | Line | Key/label |\n|---|---:|---|\n")
            for r in omissions:
                f.write(f"| {r['source']} | {r['line']} | `{r['key'] or r['label']}` |\n")
        else:
            f.write("None.\n")

    print(
        f"wrote {OUT_CSV} rows={len(rows)} counts={counts} "
        f"omissions={len(omissions)} dynamic={len(dynamic)} container_fail={len(container_fail)}"
    )


if __name__ == "__main__":
    main()
