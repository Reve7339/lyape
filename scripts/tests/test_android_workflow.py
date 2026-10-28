"""Contratos del SDK y la memoria de CI; no compila ni arranca emuladores.

Se comprueban los bloques de pasos del workflow actual; actionlint valida el YAML completo.
"""

from pathlib import Path
import re
import unittest


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/android-quality.yml"
SDK_ACTION = "android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407"
EMULATOR_ACTION = "reactivecircus/android-emulator-runner@1dcd0090116d15e7c562f8db72807de5e036a4ed"
PROJECT = WORKFLOW.parents[2]


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
        for name in ("unit", "android", "play_bundle", "delivery"):
            with self.subTest(job=name):
                job = self.job(name)
                self.assertIn(SDK_ACTION, job, "No asumir un SDK preinstalado en el runner")
                self.assertLess(job.index("actions/setup-java@"), job.index(SDK_ACTION))
                consumer = "android-emulator-runner@" if name == "android" else "./gradlew"
                self.assertLess(job.index(SDK_ACTION), job.index(consumer))
                self.assertNotIn("run: sdkmanager", job)

    def test_sdk_step_pins_tools_and_requests_all_build_packages(self):
        for name in ("unit", "android", "play_bundle", "delivery"):
            with self.subTest(job=name):
                steps = re.split(r"^      - ", self.job(name), flags=re.MULTILINE)
                sdk = next((s for s in steps if SDK_ACTION in s), "")
                self.assertIn("cmdline-tools-version: '12266719'", sdk)
                packages = re.search(r"packages: '([^']+)'", sdk)
                self.assertIsNotNone(packages)
                self.assertEqual(
                    {"platforms;android-36", "build-tools;35.0.0", "platform-tools"},
                    set(packages[1].split()),
                )

    def test_emulator_uses_verified_commit_not_annotated_tag_object(self):
        self.assertIn(EMULATOR_ACTION, self.job("android"))

    def test_ci_has_explicit_bounded_metaspace_budget(self):
        environment = re.search(r"^env:\n(.*?)(?=^\S|\Z)", self.workflow, re.MULTILINE | re.DOTALL)
        self.assertIsNotNone(environment, "El presupuesto extra pertenece solo al workflow")
        self.assertIn(
            "CI_GRADLE_JVM_ARGS: -Xmx1024m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8",
            environment[1],
        )

    def test_all_gradle_calls_use_ci_budget_without_reusable_daemons(self):
        for name in ("unit", "android", "play_bundle", "delivery"):
            commands = re.findall(r"^\s*(\./gradlew[^\n]*)$", self.job(name), re.MULTILINE)
            self.assertTrue(commands, f"Faltan invocaciones Gradle en {name}")
            for command in commands:
                with self.subTest(job=name, command=command):
                    self.assertIn('"-Dorg.gradle.jvmargs=$CI_GRADLE_JVM_ARGS"', command)
                    self.assertIn("--no-daemon", command)

    def test_unit_job_releases_classloaders_between_build_test_and_lint(self):
        commands = re.findall(r"^\s*(\./gradlew[^\n]*)$", self.job("unit"), re.MULTILINE)
        tasks = [re.findall(r":app:\w+", command) for command in commands]
        self.assertEqual([
            [":app:assembleDebug", ":app:assembleDebugAndroidTest"],
            [":app:testDebugUnitTest"],
            [":app:lintDebug"],
            [":app:assembleRelease"],
            [":app:testReleaseUnitTest"],
            [":app:lintRelease"],
        ], tasks)

    def test_ci_budget_does_not_raise_local_limits_or_parallelism(self):
        properties = (PROJECT / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn(
            "org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8",
            properties,
        )
        self.assertIn("org.gradle.workers.max=1", properties)
        self.assertIn("org.gradle.parallel=false", properties)
        self.assertIn("kotlin.compiler.execution.strategy=in-process", properties)
        launcher = (PROJECT / "scripts/local-safe.sh").read_text(encoding="utf-8")
        self.assertIn("MemoryMax=4G", launcher)
        self.assertIn("-XX:MaxMetaspaceSize=384m", launcher)


if __name__ == "__main__":
    unittest.main()
