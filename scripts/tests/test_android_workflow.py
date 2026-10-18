"""Contrato de preparación del SDK; no descarga herramientas ni arranca emuladores.

Se comprueban los bloques de pasos del workflow actual; actionlint valida el YAML completo.
"""

from pathlib import Path
import re
import unittest


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/android-quality.yml"
SDK_ACTION = "android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407"
EMULATOR_ACTION = "reactivecircus/android-emulator-runner@1dcd0090116d15e7c562f8db72807de5e036a4ed"


class AndroidWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")

    def job(self, name):
        match = re.search(
            rf"^  {name}:\n(.*?)(?=^  [\w-]+:\n|\Z)",
            self.workflow, re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(match, f"Falta el job {name}")
        return match[1]

    def test_every_job_prepares_sdk_before_android_commands(self):
        for name in ("unit", "android", "delivery"):
            with self.subTest(job=name):
                job = self.job(name)
                self.assertIn(SDK_ACTION, job, "No asumir un SDK preinstalado en el runner")
                self.assertLess(job.index("actions/setup-java@"), job.index(SDK_ACTION))
                consumer = "android-emulator-runner@" if name == "android" else "./gradlew"
                self.assertLess(job.index(SDK_ACTION), job.index(consumer))
                self.assertNotIn("run: sdkmanager", job)

    def test_sdk_step_pins_tools_and_requests_all_build_packages(self):
        for name in ("unit", "android", "delivery"):
            with self.subTest(job=name):
                steps = re.split(r"^      - ", self.job(name), flags=re.MULTILINE)
                sdk = next((s for s in steps if SDK_ACTION in s), "")
                self.assertIn("cmdline-tools-version: '12266719'", sdk)
                packages = re.search(r"packages: '([^']+)'", sdk)
                self.assertIsNotNone(packages)
                self.assertEqual(
                    {"platforms;android-35", "build-tools;35.0.0", "platform-tools"},
                    set(packages[1].split()),
                )

    def test_emulator_uses_verified_commit_not_annotated_tag_object(self):
        self.assertIn(EMULATOR_ACTION, self.job("android"))


if __name__ == "__main__":
    unittest.main()
