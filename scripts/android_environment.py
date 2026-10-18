#!/usr/bin/env python3
"""Prepare/check declared Android tools; never build or access a device."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def packages(path):
    result = []
    for line in path.read_text().splitlines():
        value = line.split('#', 1)[0].strip()
        if not value:
            continue
        if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]*(?:;[a-zA-Z0-9][a-zA-Z0-9_.-]*)*', value):
            raise ValueError(f'Invalid Android package ID: {value!r}')
        result.append(value)
    if not result:
        raise ValueError('Empty Android package manifest')
    return result


def properties_with_paths(text, sdk, flutter=None):
    values = {'sdk.dir': str(sdk)}
    if flutter is not None:
        values['flutter.sdk'] = str(flutter)
    def escaped(value):
        return value.replace('\\', '\\\\').replace(':', '\\:').replace(' ', '\\ ')
    lines = [line for line in text.splitlines()
             if line.split('=', 1)[0].strip() not in values]
    lines.extend(f'{key}={escaped(value)}' for key, value in values.items())
    return '\n'.join(lines) + '\n'


def install_packages(manager, sdk, required):
    # sdkmanager's package_file parser does not ignore manifest comments.
    subprocess.run([str(manager), f'--sdk_root={sdk}', *required], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--configure', action='store_true')
    args = parser.parse_args()
    sdk_value = os.environ.get('ANDROID_HOME')
    if not sdk_value:
        parser.error('ANDROID_HOME is missing; run through mise exec / mise run')
    sdk = Path(sdk_value).resolve()
    mobile = ROOT / 'mobile_app'
    expected_cache = (ROOT / '.dev' / 'gradle' if mobile.is_dir()
                      else ROOT / '.buildenv' / 'gradle-home')
    cache = Path(os.environ.get('GRADLE_USER_HOME', '/missing')).resolve()
    if cache != expected_cache:
        parser.error('GRADLE_USER_HOME must be project-local; run through mise')
    manifest = ROOT / 'android-sdk.packages'
    required = packages(manifest)
    if args.install:
        manager = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager'
        if not manager.is_file():
            parser.error('Install Android command-line tools in ANDROID_HOME/cmdline-tools/latest first')
        install_packages(manager, sdk, required)
    if args.configure or args.install:
        flutter = None
        if mobile.is_dir():
            executable = shutil.which('flutter')
            if not executable:
                parser.error('Flutter is missing; run mise install')
            flutter = Path(executable).resolve().parents[1]
            target = mobile / 'android' / 'local.properties'
        else:
            target = ROOT / 'local.properties'
        previous = target.read_text() if target.exists() else ''
        target.write_text(properties_with_paths(previous, sdk, flutter))
    missing = [p for p in required
               if not (sdk.joinpath(*p.split(';')) / 'source.properties').is_file()]
    for command in ('java', 'adb'):
        executable = shutil.which(command)
        print(f'{command}: {executable or "MISSING"}')
        if not executable:
            missing.append(command)
    print(f'Android SDK: {sdk}')
    print(f'Gradle cache: {cache}')
    if missing:
        print('Missing: ' + ', '.join(missing), file=sys.stderr)
        return 1
    print('Environment ready (no build or device operation executed).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
