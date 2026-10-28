"""Contratos del paquete de pruebas de Play, sin Android ni red."""

from pathlib import Path
import unittest
from scripts.verify_play_bundle import verify_manifest
from scripts.prepare_play_assets import artifacts
import struct

ROOT = Path(__file__).resolve().parents[2]
GRADLE = 'versionName = "0.0.2"\nversionCode = 2\n'
MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
package="com.lyape" android:versionName="0.0.2" android:versionCode="2">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
<application android:allowBackup="false" android:debuggable="false"/>
</manifest>'''


class PlayPreparationTest(unittest.TestCase):
    def test_valid_manifest(self):
        self.assertIn("0.0.2", verify_manifest(MANIFEST, GRADLE))

    def test_rejects_debug_backup_old_target_and_wrong_identity(self):
        for old, new in [('debuggable="false"', 'debuggable="true"'),
                         ('allowBackup="false"', 'allowBackup="true"'),
                         ('targetSdkVersion="36"', 'targetSdkVersion="35"'),
                         ('minSdkVersion="26"', 'minSdkVersion="27"'),
                         ('package="com.lyape"', 'package="com.other"'),
                         ('versionCode="2"', 'versionCode="1"'),
                         ('versionName="0.0.2"', 'versionName="0.0.1"')]:
            with self.subTest(change=new), self.assertRaises(ValueError):
                verify_manifest(MANIFEST.replace(old, new), GRADLE)

    def test_rejects_new_sensitive_permissions(self):
        for permission in ("INTERNET", "READ_SMS", "RECORD_AUDIO", "READ_CONTACTS"):
            xml = MANIFEST.replace("<uses-sdk", f'<uses-permission android:name="android.permission.{permission}"/><uses-sdk')
            with self.subTest(permission=permission), self.assertRaises(ValueError):
                verify_manifest(xml, GRADLE)

    def test_play_job_is_opt_in_branch_only_and_never_publishes(self):
        workflow = (ROOT / ".github/workflows/android-quality.yml").read_text()
        job = workflow.split("  play_bundle:\n", 1)[1].split("  delivery:\n", 1)[0]
        self.assertIn("needs: [unit, android]", job)
        self.assertIn("github.event_name == 'workflow_dispatch'", job)
        self.assertIn("inputs.prepare_play_bundle", job)
        self.assertIn("github.ref == 'refs/heads/main'", job)
        self.assertIn("contents: read", job)
        self.assertNotIn("contents: write", job)
        self.assertNotIn("gh release", job)
        self.assertNotIn("google-play", job)
        self.assertIn("-Plyape.requireReleaseSigning=true", job)
        self.assertIn("jarsigner -J-Duser.language=en -verify -strict", job)
        self.assertIn("sha256sum --check", job)
        self.assertIn("if: always()", job)
        self.assertIn("!inputs.prepare_play_bundle", workflow.split("  delivery:\n", 1)[1])

    def test_api_36_matches_project_sdk_and_ci(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn("compileSdk = 36", gradle)
        self.assertIn("targetSdk = 36", gradle)
        self.assertIn("platforms;android-36", (ROOT / "android-sdk.packages").read_text())
        self.assertIn("api: [26, 35, 36]", (ROOT / ".github/workflows/android-quality.yml").read_text())

    def test_public_policy_and_graphics_match_app_sources(self):
        for name, expected in artifacts().items():
            self.assertEqual(expected, (ROOT / name).read_text(encoding="utf-8"), name)
        for name, size in (("icon", (512, 512)), ("feature", (1024, 500))):
            content = (ROOT / f"play/graphics/{name}.png").read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", content[:8])
            self.assertEqual(size, struct.unpack(">II", content[16:24]))


if __name__ == "__main__":
    unittest.main()
