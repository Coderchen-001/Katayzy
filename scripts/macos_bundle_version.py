#!/usr/bin/env python3
"""Derive an Apple-compatible bundle version from a LizzieYzy release tag."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from datetime import date


RELEASE_TAG_PATTERN = re.compile(
    r"^next-(?P<year>\d{4})-(?P<month>\d{2})-(?P<day>\d{2})\.(?P<serial>\d+)$"
)
NUMERIC_VERSION_PATTERN = re.compile(r"^\d+(?:\.\d+){0,2}$")


@dataclass(frozen=True)
class BundleVersions:
    build: str
    short: str


def _normalize_fallback(value: str) -> str:
    if not NUMERIC_VERSION_PATTERN.fullmatch(value):
        raise ValueError(
            "Fallback app version must contain one to three numeric components."
        )
    components = [int(component) for component in value.split(".")]
    components.extend([0] * (3 - len(components)))
    major, minor, patch = components
    if major <= 0 or major > 9999 or minor > 99 or patch > 99:
        raise ValueError("Fallback app version is outside Apple's supported range.")
    return f"{major}.{minor}.{patch}"


def derive_bundle_versions(
    release_tag: str, date_tag: str = "", fallback: str = "1.0.0"
) -> BundleVersions:
    if not release_tag or release_tag == "next-dev":
        normalized = _normalize_fallback(fallback)
        return BundleVersions(build=normalized, short=normalized)

    match = RELEASE_TAG_PATTERN.fullmatch(release_tag)
    if not match:
        raise ValueError(
            "macOS release tag must use the form next-YYYY-MM-DD.N."
        )

    year = int(match.group("year"))
    month = int(match.group("month"))
    day = int(match.group("day"))
    serial = int(match.group("serial"))
    parsed_date = f"{year:04d}-{month:02d}-{day:02d}"

    if date_tag and parsed_date != date_tag:
        raise ValueError(
            f"Release tag date {parsed_date} does not match date tag {date_tag}."
        )
    try:
        date(year, month, day)
    except ValueError as error:
        raise ValueError(f"Invalid release date: {parsed_date}.") from error
    if not 1 <= serial <= 99:
        raise ValueError("Release serial must be between 1 and 99.")

    # Apple's legacy validation limits the three numeric components to 4/2/2
    # digits. YYMM.DD.N is chronological and distinguishes same-day releases.
    bundle_version = f"{year % 100:02d}{month:02d}.{day}.{serial}"
    return BundleVersions(build=bundle_version, short=bundle_version)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release-tag", default="next-dev")
    parser.add_argument("--date-tag", default="")
    parser.add_argument("--fallback", default="1.0.0")
    parser.add_argument("--field", choices=("build", "short", "json"), default="json")
    args = parser.parse_args()

    try:
        versions = derive_bundle_versions(
            release_tag=args.release_tag,
            date_tag=args.date_tag,
            fallback=args.fallback,
        )
    except ValueError as error:
        parser.error(str(error))

    if args.field == "json":
        print(json.dumps(asdict(versions), sort_keys=True))
    else:
        print(getattr(versions, args.field))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
