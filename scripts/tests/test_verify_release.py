"""Validación de entregas sin compilar, firmar ni conectarse a GitHub."""

import unittest

from scripts.verify_release import verify_release


class VerifyReleaseTest(unittest.TestCase):
    def setUp(self):
        self.metadata = {
            "variantName": "release",
            "applicationId": "com.lyape",
            "elements": [{
                "versionName": "0.0.1", "versionCode": 1,
                "outputFile": "app-release.apk", "filters": [],
            }],
        }
        self.badging = (
            "package: name='com.lyape' versionCode='1' versionName='0.0.1'\n"
            "sdkVersion:'26'\n"
        )

    def test_accepts_matching_release_tag_and_apk(self):
        self.assertEqual("0.0.1", verify_release("v0.0.1", self.metadata, self.badging))

    def test_rejects_branch_or_malformed_tag(self):
        for tag in ("main", "0.0.1", "v0.0.1-beta", "v0.0.1/other", "v00.0.1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                verify_release(tag, self.metadata, self.badging)

    def test_rejects_tag_version_mismatch(self):
        with self.assertRaises(ValueError):
            verify_release("v0.0.2", self.metadata, self.badging)

    def test_rejects_debug_variant_or_debuggable_apk(self):
        with self.assertRaises(ValueError):
            verify_release("v0.0.1", self.metadata, self.badging + "application-debuggable\n")
        self.metadata["variantName"] = "debug"
        with self.assertRaises(ValueError):
            verify_release("v0.0.1", self.metadata, self.badging)

    def test_rejects_wrong_package_in_metadata_or_apk(self):
        with self.assertRaises(ValueError):
            verify_release("v0.0.1", self.metadata, self.badging.replace("com.lyape", "other"))
        self.metadata["applicationId"] = "other"
        with self.assertRaises(ValueError):
            verify_release("v0.0.1", self.metadata, self.badging)

    def test_rejects_missing_or_multiple_apks(self):
        for elements in ([], self.metadata["elements"] * 2):
            with self.subTest(elements=elements), self.assertRaises(ValueError):
                verify_release("v0.0.1", {**self.metadata, "elements": elements}, self.badging)

    def test_rejects_invalid_code_or_non_universal_apk(self):
        for field, value in (("versionCode", 0), ("versionCode", True),
                             ("outputFile", "../app-release.apk"),
                             ("outputFile", "app-release-unsigned.apk"),
                             ("filters", [{"filterType": "ABI", "value": "x86_64"}])):
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                element = {**self.metadata["elements"][0], field: value}
                verify_release("v0.0.1", {**self.metadata, "elements": [element]}, self.badging)

    def test_rejects_apk_version_code_or_name_mismatch(self):
        for badging in (self.badging.replace("versionCode='1'", "versionCode='2'"),
                        self.badging.replace("versionName='0.0.1'", "versionName='0.0.2'"), ""):
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                verify_release("v0.0.1", self.metadata, badging)


if __name__ == "__main__":
    unittest.main()
