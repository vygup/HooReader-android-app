#!/usr/bin/env python3
"""Обновляет настоящий подписанный v1 APK до v2 без очистки данных между версиями."""
import hashlib
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get('ADB', 'adb')
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
if not SERIAL.startswith('emulator-'):
    raise SystemExit('Upgrade acceptance разрешён только на временном read-only emulator.')
processes = subprocess.check_output(['ps', '-ax', '-o', 'command='], text=True).splitlines()
qemu = [line for line in processes if '/qemu-system-' in line and '-avd' in line]
if len(qemu) != 1 or '-read-only' not in qemu[0]:
    raise SystemExit('Запустите ровно один emulator с -read-only; пользовательская установка не затрагивается.')
output = ROOT / 'specs/002-reader-appearance/evidence/upgrade'
output.mkdir(parents=True, exist_ok=True)
apks = [ROOT / f'app/build/release/{version}/HooReader-{version}.apk' for version in ('1.0.0', '2.0.0')]
if not all(apk.is_file() for apk in apks):
    raise SystemExit('Нужны подписанные APK 1.0.0 и 2.0.0; сначала выполните scripts/build-release.py.')
schema_path = 'app/schemas/com.hooreader.data.local.HooReaderDatabase/1.json'
old_schema = subprocess.check_output(['git', '-C', str(ROOT), 'show', 'v1.0.0:' + schema_path])
schema = (ROOT / schema_path).read_bytes()
if schema != old_schema:
    raise SystemExit('Room schema изменилась; проверка обновления требует явной миграции.')


def adb(*parts):
    return subprocess.check_output([ADB, '-s', SERIAL, *parts])


def phase(name):
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 'com.hooreader.releasesmoke.ReleaseUpgradeTest#' + ('seedUpgrade' if name == 'seed' else 'verifyUpgrade'),
                 '-e', 'upgradePhase', name, '-e', 'disposableReleaseEmulator', 'true',
                 'com.hooreader.releasesmoke.test/androidx.test.runner.AndroidJUnitRunner').decode()
    (output / f'{name}-instrumentation.txt').write_text(result)
    print(result)
    if 'OK (1 test)' not in result or 'FAILURES' in result:
        raise RuntimeError('Upgrade phase failed: ' + name)


if adb('shell', 'getprop', 'sys.boot_completed').strip() != b'1':
    raise SystemExit('Дождитесь загрузки emulator.')
old_airplane = adb('shell', 'settings', 'get', 'global', 'airplane_mode_on').strip()
try:
    # This uninstall affects only the disposable overlay BEFORE seeding v1.
    packages = adb('shell', 'pm', 'list', 'packages', 'com.hooreader').decode().splitlines()
    if 'package:com.hooreader' in packages:
        adb('uninstall', 'com.hooreader')
    adb('install', str(apks[0]))
    for apk in ('release-smoke/build/outputs/apk/debug/release-smoke-debug.apk',
                'release-smoke/build/outputs/apk/androidTest/debug/release-smoke-debug-androidTest.apk'):
        adb('install', '-r', str(ROOT / apk))
    for fixture, target in [('structured.epub', 'hooreader-release-epub.epub'),
                            ('windows-1251.fb2', 'hooreader-release-fb2.fb2')]:
        adb('push', str(ROOT / 'app/src/androidTest/assets/books' / fixture), '/sdcard/Download/' + target)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    phase('seed')
    adb('shell', 'am', 'force-stop', 'com.hooreader')
    installation = adb('install', '-r', str(apks[1])).decode()
    (output / 'install-update.txt').write_text(installation)
    # No uninstall/pm clear occurs between v1 and v2. Package manager also verifies the signer.
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    phase('verify')
    for name in ('seed', 'epub', 'fb2', 'verify'):
        for suffix in ('png', 'xml'):
            filename = f'upgrade-{name}.{suffix}'
            (output / filename).write_bytes(adb('exec-out', 'run-as', 'com.hooreader.releasesmoke',
                                               'cat', 'files/' + filename))
    result = {'status': 'PASS', 'fromVersion': '1.0.0', 'toVersion': '2.0.0', 'dataClearedBetweenVersions': False,
              'airplaneMode': True, 'formats': ['EPUB', 'FB2'], 'modes': ['VERTICAL', 'PAGINATED'],
              'theme': 'DARK', 'fontScale': 1.5, 'defaultMode': 'VERTICAL', 'defaultConfirmReaderExit': True,
              'roomSchemaVersion': 1, 'roomSchemaUnchanged': True,
              'roomSchemaSha256': hashlib.sha256(schema).hexdigest(),
              'position': 'Room позиция v1 восстанавливает вторую главу; точный UTF-16 offset проверяется T059',
              'apkSha256': {apk.name: hashlib.sha256(apk.read_bytes()).hexdigest() for apk in apks},
              'releaseAccepted': False}
    (output / 'summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
finally:
    if old_airplane != b'1':
        adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
