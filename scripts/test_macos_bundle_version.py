#!/usr/bin/env python3

from __future__ import annotations

import unittest

from macos_bundle_version import derive_bundle_versions


class MacosBundleVersionTest(unittest.TestCase):
    def test_release_tag_maps_to_unique_apple_version(self) -> None:
        versions = derive_bundle_versions(
            "next-2026-07-26.1", date_tag="2026-07-26"
        )
        self.assertEqual("2607.26.1", versions.build)
        self.assertEqual("2607.26.1", versions.short)

    def test_same_day_release_serial_is_preserved(self) -> None:
        first = derive_bundle_versions("next-2026-07-26.1")
        second = derive_bundle_versions("next-2026-07-26.2")
        self.assertEqual("2607.26.1", first.build)
        self.assertEqual("2607.26.2", second.build)

    def test_dev_build_uses_normalized_fallback(self) -> None:
        versions = derive_bundle_versions("next-dev", fallback="1.2")
        self.assertEqual("1.2.0", versions.build)

    def test_date_mismatch_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            derive_bundle_versions(
                "next-2026-07-26.1", date_tag="2026-07-25"
            )

    def test_out_of_range_serial_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "between 1 and 99"):
            derive_bundle_versions("next-2026-07-26.100")

    def test_invalid_calendar_date_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "Invalid release date"):
            derive_bundle_versions("next-2026-02-30.1")

    def test_non_numeric_release_tag_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "next-YYYY-MM-DD"):
            derive_bundle_versions("release-2026-07-26")


if __name__ == "__main__":
    unittest.main()
