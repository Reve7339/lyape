"""El lector de resultados no puede convertir una ejecución incompleta en verde."""

import unittest
import xml.etree.ElementTree as ET

from scripts.run_android_tests import parse_results


def transcript(status=0, total=1, ending="INSTRUMENTATION_CODE: -1", summary="OK (1 test)"):
    return f'''INSTRUMENTATION_STATUS: class=com.lyape.ui.ExampleTest
INSTRUMENTATION_STATUS: test=example
INSTRUMENTATION_STATUS: numtests={total}
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.lyape.ui.ExampleTest
INSTRUMENTATION_STATUS: test=example
INSTRUMENTATION_STATUS: numtests={total}
INSTRUMENTATION_STATUS: stack=Detalles con < & >
INSTRUMENTATION_STATUS_CODE: {status}
INSTRUMENTATION_RESULT: stream=
{summary}
{ending}
'''


class AndroidResultsTest(unittest.TestCase):
    def test_success_has_real_class_and_method(self):
        root = parse_results(transcript())
        self.assertEqual("1", root.get("tests"))
        case = root.find("testcase")
        self.assertEqual("com.lyape.ui.ExampleTest", case.get("classname"))
        self.assertEqual("example", case.get("name"))

    def test_failure_and_error_are_not_silenced(self):
        for status in (-1, -2):
            with self.subTest(status=status):
                root = parse_results(transcript(status=status, summary="FAILURES!!!"))
                self.assertEqual("1", root.get("failures"))
                self.assertIsNotNone(root.find("testcase/failure"))

    def test_ignored_and_assumption_remain_skipped(self):
        for status in (-3, -4):
            with self.subTest(status=status):
                root = parse_results(transcript(status=status))
                self.assertEqual("1", root.get("skipped"))
                self.assertIsNotNone(root.find("testcase/skipped"))

    def test_rejects_incomplete_run(self):
        with self.assertRaises(ValueError):
            parse_results(transcript(total=2))

    def test_rejects_crashed_runner(self):
        with self.assertRaises(ValueError):
            parse_results(transcript(ending="INSTRUMENTATION_CODE: 0"))

    def test_rejects_missing_success_summary(self):
        with self.assertRaises(ValueError):
            parse_results(transcript(summary="Process crashed"))

    def test_rejects_pass_without_start(self):
        text = transcript().split("INSTRUMENTATION_STATUS_CODE: 1\n", 1)[1]
        with self.assertRaises(ValueError):
            parse_results(text)

    def test_skipped_case_does_not_hide_crashed_summary(self):
        with self.assertRaises(ValueError):
            parse_results(transcript(status=-3, summary="Process crashed"))

    def test_rejects_zero_tests(self):
        with self.assertRaises(ValueError):
            parse_results("OK (0 tests)\nINSTRUMENTATION_CODE: -1\n")

    def test_rejects_duplicate_case(self):
        text = transcript().replace("numtests=1", "numtests=2")
        with self.assertRaises(ValueError):
            parse_results(text + text)

    def test_rejects_start_without_completion(self):
        with self.assertRaises(ValueError):
            parse_results(transcript().replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: 1"))

    def test_serializes_failure_text_safely(self):
        root = parse_results(transcript(status=-2, summary="FAILURES!!!"))
        restored = ET.fromstring(ET.tostring(root))
        self.assertIn("< & >", restored.find("testcase/failure").text)


if __name__ == "__main__":
    unittest.main()
