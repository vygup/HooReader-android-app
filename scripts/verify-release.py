#!/usr/bin/env python3
"""Проверяет реальный release APK только на временном read-only эмуляторе."""
import os
import hashlib
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
adb = os.environ.get('ADB', 'adb')
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
if not serial.startswith('emulator-'):
    raise SystemExit('Release smoke разрешён только для временного Android emulator.')
processes = subprocess.check_output(['ps', '-ax', '-o', 'command='], text=True).splitlines()
qemu = [line for line in processes if '/qemu-system-' in line and '-avd' in line]
if len(qemu) != 1 or '-read-only' not in qemu[0]:
    raise SystemExit('Запустите ровно один Android emulator с -read-only; постоянные данные не затрагиваются.')


def run(*args):
    return subprocess.run([adb, '-s', serial, *args], capture_output=True, text=True, check=True).stdout


output = root / 'app/build/release/1.0.0'
apk = output / 'HooReader-1.0.0.apk'
run('wait-for-device')
if run('shell', 'getprop', 'sys.boot_completed').strip() != '1':
    raise SystemExit('Дождитесь завершения загрузки read-only emulator.')
# Changes affect only the temporary writable overlay. APK has a distinct release signer.
if 'package:com.hooreader' in run('shell', 'pm', 'list', 'packages', 'com.hooreader').splitlines():
    run('uninstall', 'com.hooreader')
run('install', '-r', str(apk))
run('install', '-r', str(root / 'release-smoke/build/outputs/apk/debug/release-smoke-debug.apk'))
run('install', '-r', str(root / 'release-smoke/build/outputs/apk/androidTest/debug/release-smoke-debug-androidTest.apk'))
for name, target in [('structured.epub', 'hooreader-release-epub.epub'),
                     ('windows-1251.fb2', 'hooreader-release-fb2.fb2')]:
    run('push', str(root / 'app/src/androidTest/assets/books' / name), '/sdcard/Download/' + target)
run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
run('shell', 'wm', 'dismiss-keyguard')
result = run('shell', 'am', 'instrument', '-w', '-e', 'class',
             'com.hooreader.releasesmoke.ReleaseSmokeTest', '-e', 'releaseSmoke', 'true',
             '-e', 'disposableReleaseEmulator', 'true',
             'com.hooreader.releasesmoke.test/androidx.test.runner.AndroidJUnitRunner')
(output / 'release-smoke.txt').write_text(result)
for name in ('release-smoke.xml', 'release-smoke.png'):
    captured = subprocess.run([adb, '-s', serial, 'exec-out', 'run-as', 'com.hooreader.releasesmoke',
                               'cat', 'files/' + name], capture_output=True, check=True)
    (output / name).write_bytes(captured.stdout)
print(result)
if 'OK (1 test)' not in result or 'FAILURES' in result or 'INSTRUMENTATION_FAILED' in result:
    raise SystemExit('Release smoke failed; см. release-smoke.txt.')
for name, target in [('structured.epub', 'hooreader-release-epub.epub'),
                     ('windows-1251.fb2', 'hooreader-release-fb2.fb2')]:
    expected = hashlib.sha256((root / 'app/src/androidTest/assets/books' / name).read_bytes()).hexdigest()
    actual = run('shell', 'sha256sum', '/sdcard/Download/' + target).split()[0]
    if actual != expected:
        raise SystemExit('Выбранный исходный файл изменён после удаления записи: ' + target)
print('Подписанный non-debuggable release APK прошёл внешний smoke-тест.')
