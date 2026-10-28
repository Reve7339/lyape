"""Recogida de capturas desde los resultados del host, sin adb ni emulador."""

import tempfile
import unittest
from pathlib import Path

from scripts.collect_play_screenshots import collect, NAMES


class CollectPlayScreenshotsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "outputs"
        self.destination = self.root / "delivery"

    def seed(self, device="emulator-5554 - 8.0.0"):
        folder = self.source / "debugAndroidTest" / "connected" / device / "play-screenshots"
        folder.mkdir(parents=True)
        for name in NAMES:
            (folder / name).write_bytes(b"\x89PNG\r\n\x1a\n" + b"fictional test data")
        return folder

    def test_collects_all_four_from_nested_device_path_with_spaces(self):
        folder = self.seed()
        collect(self.source, self.destination)
        self.assertEqual(set(NAMES), {p.name for p in self.destination.iterdir()})
        for name in NAMES:
            self.assertEqual((folder / name).read_bytes(), (self.destination / name).read_bytes())

    def test_missing_folder_fails_instead_of_publishing_empty_artifact(self):
        with self.assertRaises(ValueError):
            collect(self.source, self.destination)

    def test_missing_capture_fails_before_copying_anything(self):
        folder = self.seed()
        (folder / NAMES[-1]).unlink()
        with self.assertRaises(ValueError):
            collect(self.source, self.destination)
        self.assertFalse(self.destination.exists())

    def test_ambiguous_device_outputs_are_rejected(self):
        self.seed()
        self.seed("emulator-5556 - 16")
        with self.assertRaises(ValueError):
            collect(self.source, self.destination)

    def test_non_png_output_is_rejected(self):
        folder = self.seed()
        (folder / NAMES[0]).write_text("an error, not a screenshot")
        with self.assertRaises(ValueError):
            collect(self.source, self.destination)

