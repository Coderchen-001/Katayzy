#!/usr/bin/env python3

from pathlib import Path
import tempfile
import unittest

from audit_katago_binary_version import BinaryVersionAuditError, audit_binary


class AuditKataGoBinaryVersionTest(unittest.TestCase):
    def write_fixture(self, payload: bytes) -> Path:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        binary = Path(temp_dir.name) / "katago.exe"
        binary.write_bytes(payload)
        return binary

    def test_accepts_expected_marker(self) -> None:
        binary = self.write_fixture(b"PE fixture\0KataGo v1.17.1\0")
        audit_binary(binary, "1.17.1", ["1.17.0"])

    def test_rejects_missing_expected_marker(self) -> None:
        binary = self.write_fixture(b"PE fixture without a version")
        with self.assertRaises(BinaryVersionAuditError):
            audit_binary(binary, "1.17.1", ["1.17.0"])

    def test_rejects_stale_marker(self) -> None:
        binary = self.write_fixture(b"KataGo v1.17.1\0KataGo v1.17.0\0")
        with self.assertRaises(BinaryVersionAuditError):
            audit_binary(binary, "1.17.1", ["1.17.0"])


if __name__ == "__main__":
    unittest.main()
