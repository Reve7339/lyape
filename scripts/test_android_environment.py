#!/usr/bin/env python3
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import os
import android_environment
from android_environment import packages, properties_with_paths, install_packages, jdk_problems


class AndroidEnvironmentTests(unittest.TestCase):
    def make_jdk(self, root):
        (root / 'bin').mkdir(parents=True)
        for tool in ('java', 'javac', 'jar', 'jlink', 'jmod'):
            executable = root / 'bin' / tool
            executable.touch()
            executable.chmod(0o755)
        (root / 'jmods').mkdir()
        (root / 'jmods/java.base.jmod').touch()

    def test_complete_jdk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root)
            self.assertEqual(jdk_problems(root, root / 'bin/java'), [])

    def test_partial_ide_runtime_is_rejected_even_with_javac(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root)
            (root / 'bin/jlink').unlink()
            (root / 'bin/jmod').unlink()
            (root / 'jmods/java.base.jmod').unlink()
            problems = jdk_problems(root, root / 'bin/java')
            self.assertEqual(len(problems), 3)
            self.assertTrue(any('bin/jlink' in problem for problem in problems))

    def test_each_jdk_component_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root)
            for component in ('bin/java', 'bin/javac', 'bin/jar', 'bin/jlink',
                              'bin/jmod', 'jmods/java.base.jmod'):
                with self.subTest(component=component):
                    path = root / component
                    original_mode = path.stat().st_mode
                    path.unlink()
                    self.assertTrue(jdk_problems(root, root / 'bin/java'))
                    path.touch()
                    path.chmod(original_mode)

    def test_nonexecutable_jlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root)
            (root / 'bin/jlink').chmod(0o644)
            self.assertTrue(jdk_problems(root, root / 'bin/java'))

    def test_java_home_and_path_must_agree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root)
            self.assertTrue(jdk_problems(None, root / 'bin/java'))
            self.assertTrue(jdk_problems(root, None))
            self.assertTrue(jdk_problems(root, '/another/jdk/bin/java'))

    @patch('sys.argv', ['android_environment.py', '--install'])
    @patch('android_environment.install_packages')
    def test_incomplete_jdk_stops_before_sdk_install_or_configure(self, install):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mobile_app/android').mkdir(parents=True)
            (root / 'android-sdk.packages').write_text('platform-tools\n')
            target = root / 'mobile_app/android/local.properties'
            target.write_text('sdk.dir=unchanged\n')
            with patch.object(android_environment, 'ROOT', root), patch.dict(
                os.environ, {'ANDROID_HOME': str(root / 'sdk'),
                             'JAVA_HOME': str(root / 'partial-java'),
                             'GRADLE_USER_HOME': str(root / '.dev/gradle')}
            ):
                self.assertEqual(android_environment.main(), 1)
            install.assert_not_called()
            self.assertEqual(target.read_text(), 'sdk.dir=unchanged\n')

    def test_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'packages'
            path.write_text('# comment\nplatform-tools\nplatforms;android-36 # API\n')
            self.assertEqual(packages(path), ['platform-tools', 'platforms;android-36'])

    def test_invalid_package(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'packages'
            for value in ('../escape', 'platforms;/tmp', '--update', ''):
                path.write_text(value)
                with self.assertRaises(ValueError):
                    packages(path)

    def test_properties_preserve_other_settings(self):
        previous = '# local\nsdk.dir=/old\nflutter.sdk=/old-flutter\nflutter.buildMode=debug\n'
        result = properties_with_paths(previous, Path('/shared/sdk'), Path('/shared/flutter'))
        self.assertIn('flutter.buildMode=debug\n', result)
        self.assertIn('# local\n', result)
        self.assertNotIn('/old', result)
        self.assertEqual(result.count('sdk.dir='), 1)

    @patch('android_environment.subprocess.run')
    def test_install_passes_only_parsed_ids(self, run):
        install_packages(Path('/sdk/bin/sdkmanager'), Path('/sdk'),
                         ['platform-tools', 'platforms;android-36'])
        run.assert_called_once_with(['/sdk/bin/sdkmanager', '--sdk_root=/sdk',
                                     'platform-tools', 'platforms;android-36'], check=True)

    def test_properties_escape_and_idempotency(self):
        first = properties_with_paths('', Path('/shared/sdk space'), Path('/shared/flutter'))
        self.assertIn('sdk\\ space', first)
        self.assertEqual(first, properties_with_paths(first, Path('/shared/sdk space'), Path('/shared/flutter')))

    @patch('sys.argv', ['android_environment.py'])
    def test_shared_cache_is_rejected(self):
        with patch.dict(os.environ, {'ANDROID_HOME': '/shared/sdk',
                                     'GRADLE_USER_HOME': '/shared/cache'}):
            with self.assertRaises(SystemExit) as result:
                android_environment.main()
            self.assertEqual(result.exception.code, 2)

    @patch('sys.argv', ['android_environment.py'])
    def test_missing_components_are_not_reported_ready(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root / 'jdk')
            (root / 'mobile_app').mkdir()
            (root / 'android-sdk.packages').write_text('platforms;android-36\n')
            with patch.object(android_environment, 'ROOT', root), patch.dict(
                os.environ, {'ANDROID_HOME': str(root / 'sdk'),
                             'JAVA_HOME': str(root / 'jdk'),
                             'GRADLE_USER_HOME': str(root / '.dev/gradle')}
            ), patch('android_environment.shutil.which', return_value=str(root / 'jdk/bin/java')):
                self.assertEqual(android_environment.main(), 1)

    @patch('sys.argv', ['android_environment.py'])
    def test_complete_environment_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_jdk(root / 'jdk')
            (root / 'mobile_app').mkdir()
            (root / 'android-sdk.packages').write_text('platforms;android-36\n')
            platform = root / 'sdk/platforms/android-36'
            platform.mkdir(parents=True)
            (platform / 'source.properties').touch()
            with patch.object(android_environment, 'ROOT', root), patch.dict(
                os.environ, {'ANDROID_HOME': str(root / 'sdk'),
                             'JAVA_HOME': str(root / 'jdk'),
                             'GRADLE_USER_HOME': str(root / '.dev/gradle')}
            ), patch('android_environment.shutil.which', return_value=str(root / 'jdk/bin/java')):
                self.assertEqual(android_environment.main(), 0)


if __name__ == '__main__':
    unittest.main()
