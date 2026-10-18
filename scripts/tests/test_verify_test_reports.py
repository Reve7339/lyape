"""Regresión de la puerta de calidad; no necesita paquetes externos."""

import contextlib
import io
from pathlib import Path
import tempfile
import unittest

from scripts.verify_test_reports import QualityError, main, verify_reports


class VerifyTestReportsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.required = {"acceptance.ExampleTest": {"bdd01Payment"}}

    def report(self, content, name="TEST-example.xml"):
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_counts_executed_tests_and_required_scenarios(self):
        path = self.report('''<testsuite tests="2" failures="0" errors="0">
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"/>
          <testcase classname="domain.ExampleTest" name="amount"/>
        </testsuite>''')
        result = verify_reports([path], self.required)
        self.assertEqual(2, result.tests)
        self.assertEqual(1, result.scenarios)

    def test_rejects_missing_reports(self):
        with self.assertRaises(QualityError):
            verify_reports([], self.required)

    def test_rejects_empty_suite(self):
        path = self.report('<testsuite tests="0"/>')
        with self.assertRaises(QualityError):
            verify_reports([path], {})

    def test_rejects_failure_or_error_in_any_test(self):
        for child in ("failure", "error"):
            with self.subTest(child=child):
                path = self.report(f'''<testsuite>
                  <testcase classname="domain.ExampleTest" name="amount"><{child}/></testcase>
                </testsuite>''')
                with self.assertRaises(QualityError):
                    verify_reports([path], {})

    def test_rejects_suite_level_failure_even_without_failure_node(self):
        path = self.report('''<testsuite failures="1">
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"/>
        </testsuite>''')
        with self.assertRaises(QualityError):
            verify_reports([path], self.required)

    def test_rejects_missing_required_scenario(self):
        path = self.report('''<testsuite>
          <testcase classname="domain.ExampleTest" name="amount"/>
        </testsuite>''')
        with self.assertRaisesRegex(QualityError, "bdd01Payment"):
            verify_reports([path], self.required)

    def test_rejects_skipped_required_scenario(self):
        path = self.report('''<testsuite>
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"><skipped/></testcase>
        </testsuite>''')
        with self.assertRaises(QualityError):
            verify_reports([path], self.required)

    def test_same_method_in_other_class_does_not_replace_required_scenario(self):
        path = self.report('''<testsuite>
          <testcase classname="other.ExampleTest" name="bdd01Payment"/>
        </testsuite>''')
        with self.assertRaises(QualityError):
            verify_reports([path], self.required)

    def test_requires_ui_scenarios_in_addition_to_business_scenarios(self):
        path = self.report('''<testsuite>
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"/>
        </testsuite>''')
        required = {**self.required, "ui.JourneyTest": {"createPayment"}}
        with self.assertRaisesRegex(QualityError, "createPayment"):
            verify_reports([path], required)

    def test_rejects_malformed_xml(self):
        path = self.report("not xml")
        with self.assertRaises(QualityError):
            verify_reports([path], self.required)

    def test_supports_android_testsuites_wrapper(self):
        path = self.report('''<testsuites><testsuite>
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"/>
        </testsuite></testsuites>''')
        self.assertEqual(1, verify_reports([path], self.required).tests)

    def test_does_not_count_skipped_tests_as_executed(self):
        path = self.report('''<testsuite>
          <testcase classname="acceptance.ExampleTest" name="bdd01Payment"/>
          <testcase classname="domain.ExampleTest" name="later"><skipped/></testcase>
        </testsuite>''')
        result = verify_reports([path], self.required)
        self.assertEqual(1, result.tests)
        self.assertEqual(1, result.skipped)

    def test_command_fails_when_report_directory_has_no_xml(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, main(["unit", "--reports", str(self.root)]))


if __name__ == "__main__":
    unittest.main()
