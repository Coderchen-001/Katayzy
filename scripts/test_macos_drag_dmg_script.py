#!/usr/bin/env python3

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class MacosDragDmgScriptTest(unittest.TestCase):
    def test_writable_image_creation_is_retried_with_diagnostics(self) -> None:
        script = (ROOT / "scripts/create_macos_drag_dmg.sh").read_text(encoding="utf-8")
        self.assertIn("create_writable_dmg()", script)
        self.assertIn("for attempt in 1 2 3", script)
        self.assertIn("Creating writable DMG (attempt $attempt/3)", script)
        self.assertIn("Unable to create the writable DMG after 3 attempts.", script)
        self.assertIn("\ncreate_writable_dmg\n", script)
        self.assertNotIn("hdiutil create \\\n  -quiet", script)

    def test_writable_image_attach_is_retried_with_diagnostics(self) -> None:
        script = (ROOT / "scripts/create_macos_drag_dmg.sh").read_text(encoding="utf-8")
        self.assertIn("attach_writable_dmg()", script)
        self.assertIn("Attaching writable DMG (attempt $attempt/3)", script)
        self.assertIn("Writable DMG attach attempt $attempt/3 failed:", script)
        self.assertIn("Unable to attach the writable DMG after 3 attempts.", script)
        self.assertIn('cat "$attach_log" >&2', script)
        self.assertIn("\nattach_writable_dmg\n", script)


if __name__ == "__main__":
    unittest.main()
