"""Comprueba el lanzador sin ejecutar Gradle, emuladores ni reservar memoria."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "local-safe.sh"


class LocalSafeTest(unittest.TestCase):
    def run_script(self, *args, env=None):
        return subprocess.run(
            ["bash", str(SCRIPT), *args],
            text=True, capture_output=True, env=env, timeout=10,
        )

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.bin_dir = Path(self.directory.name)
        # Dobles: solo imprimen argumentos. No hablan con systemd ni ejecutan tareas.
        for name in ("systemd-run", "systemctl"):
            path = self.bin_dir / name
            path.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n', encoding="utf-8")
            path.chmod(0o755)
        self.env = {**os.environ, "PATH": f"{self.bin_dir}:{os.environ['PATH']}"}

    def test_help_does_not_launch_anything(self):
        result = self.run_script("--help")
        self.assertEqual(0, result.returncode)
        self.assertIn("4 GiB", result.stdout)

    def test_invalid_command_is_rejected(self):
        self.assertEqual(2, self.run_script("arbitrary", env=self.env).returncode)

    def test_gradle_requires_a_task(self):
        self.assertEqual(2, self.run_script("gradle", env=self.env).returncode)

    def test_emulator_requires_exactly_one_avd(self):
        for args in (("emulator",), ("emulator", "lyape34", "-memory", "8192")):
            with self.subTest(args=args):
                self.assertEqual(2, self.run_script(*args, env=self.env).returncode)

    def test_gradle_and_emulator_use_same_parent_slice(self):
        for args in (("gradle", ":app:assembleDebug"), ("emulator", "lyape34"), ("android-tests",)):
            with self.subTest(args=args):
                result = self.run_script(*args, env=self.env)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("--slice=lyape-local.slice\n", result.stdout)
                self.assertIn("--property=MemoryMax=4G\n", result.stdout)
                self.assertIn("--property=MemoryHigh=3G\n", result.stdout)
                self.assertIn("--property=MemorySwapMax=1G\n", result.stdout)
                self.assertIn(f"--unit=lyape-local-{args[0]}.scope\n", result.stdout)

    def test_status_does_not_start_a_scope(self):
        result = self.run_script("status", env=self.env)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("show\nlyape-local.slice\n", result.stdout)
        self.assertNotIn("--scope", result.stdout)

    def test_stop_only_targets_the_four_project_scopes(self):
        result = self.run_script("stop", env=self.env)
        self.assertEqual(0, result.returncode, result.stderr)
        lines = result.stdout.splitlines()
        self.assertEqual(4, lines.count("stop"))
        for name in ("gradle", "emulator", "check", "android-tests"):
            self.assertIn(f"lyape-local-{name}.scope", lines)
        self.assertNotIn("lyape-local.slice", lines)

    def test_no_fallback_if_systemd_run_fails(self):
        path = self.bin_dir / "systemd-run"
        path.write_text("#!/bin/sh\nexit 23\n", encoding="utf-8")
        result = self.run_script("gradle", ":app:assembleDebug", env=self.env)
        self.assertEqual(23, result.returncode)

    def test_direct_internal_invocation_cannot_bypass_group(self):
        # La suite se ejecuta fuera del scope de LYape, tanto localmente como en CI.
        result = self.run_script("--inside", "gradle", ":app:assembleDebug", env=self.env)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("grupo protegido", result.stderr)


if __name__ == "__main__":
    unittest.main()
