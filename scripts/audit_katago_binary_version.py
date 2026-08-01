#!/usr/bin/env python3
"""Audit embedded KataGo version markers without loading GPU driver DLLs."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys


class BinaryVersionAuditError(RuntimeError):
    pass


def audit_binary(path: Path, expected_version: str, rejected_versions: list[str]) -> None:
    if not path.is_file():
        raise BinaryVersionAuditError(f"KataGo binary not found: {path}")
    data = path.read_bytes()
    expected_marker = f"KataGo v{expected_version}".encode("ascii")
    if expected_marker not in data:
        raise BinaryVersionAuditError(
            f"{path} does not contain the expected marker {expected_marker.decode('ascii')}"
        )
    for version in rejected_versions:
        rejected_marker = f"KataGo v{version}".encode("ascii")
        if rejected_marker in data:
            raise BinaryVersionAuditError(
                f"{path} contains rejected marker {rejected_marker.decode('ascii')}"
            )
    print(f"{path}: embedded KataGo v{expected_version} marker verified")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--reject-version", action="append", default=[])
    parser.add_argument("binaries", nargs="+", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    for binary in args.binaries:
        audit_binary(binary, args.expected_version, args.reject_version)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, BinaryVersionAuditError) as exc:
        print(f"KataGo binary version audit failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
