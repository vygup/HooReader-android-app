#!/usr/bin/env python3
"""Сквозной SC-004: отдельные UI samples и production pagination, без вымышленной приёмки."""
import argparse
import json
import os
import statistics
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', nargs='?', type=Path,
                    default=ROOT / 'specs/002-reader-appearance/evidence/performance-v2.json')
parser.add_argument('--allow-emulator', action='store_true', help='Только диагностика, не аппаратная приёмка')
parser.add_argument('--ui-only', action='store_true', help='Проверка UI harness; полная серия остаётся незавершённой')
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
adb_path = os.environ.get('ADB', str(sdk / 'platform-tools/adb'))
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
profile_path = ROOT / 'specs/002-reader-appearance/evidence/performance-profile.json'
profile = json.loads(profile_path.read_text())
args.output.parent.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    return subprocess.check_output([adb_path, '-s', serial, *parts])


def save(report):
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')


devices = subprocess.check_output([adb_path, 'devices'], text=True)
connected = any(line.split()[:2] == [serial, 'device'] for line in devices.splitlines())
physical = connected and not serial.startswith('emulator-') and adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1'
report = {'status': 'NOT_VERIFIED_DEVICE', 'profileId': profile.get('profileId'),
          'physical': physical, 'runsPerScenario': 5, 'ui': [], 'pagination': None,
          'releaseAccepted': False, 'reason': 'Физический стенд SC-004 недоступен.',
          'priorDiagnostics': 'pagination-production-optimized.json'}
save(report)
if not physical and not args.allow_emulator:
    print('NOT_VERIFIED_DEVICE: физический стенд недоступен; пять значений не выдуманы.')
    raise SystemExit(0)
if not connected:
    raise SystemExit('Выбранный Android-стенд не подключён.')

# Новый профиль/серия сохраняются отдельно; прежние доказательства остаются доступны.
directory = args.output.parent / (args.output.stem + '-series')
directory.mkdir(parents=True, exist_ok=True)
pagination = directory / 'pagination.json'
invocation = ['python3', str(ROOT / 'scripts/reader-pagination-protocol.py'), '--output', str(pagination)]
if args.allow_emulator:
    invocation += ['--allow-emulator']
if args.ui_only:
    invocation += ['--profile-only']
subprocess.run(invocation, check=True, env=dict(os.environ, ANDROID_SERIAL=serial, ADB=adb_path))
profile = json.loads((directory / 'performance-profile.json').read_text())
report.update(profileId=profile['profileId'], reason='Диагностическая серия' if not physical else None)
if pagination.exists() and not args.ui_only:
    report['pagination'] = json.loads(pagination.read_text())
save(report)
for apk in ('app/build/outputs/apk/debug/app-debug.apk',
            'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
    adb('install', '-r', str(ROOT / apk))
result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
             'com.hooreader.acceptance.ReaderPerformanceTest#measureV2PanelsMenusAndSettings',
             '-e', 'phase8Performance', 'true', '-e', 'probeProfileId', profile['profileId'],
             'com.hooreader.test/androidx.test.runner.AndroidJUnitRunner').decode()
(directory / 'ui-instrumentation.txt').write_text(result)
if 'OK (1 test)' not in result or 'FAILURES' in result:
    raise SystemExit('UI measurement failed; см. ui-instrumentation.txt')
raw = json.loads(adb('exec-out', 'run-as', 'com.hooreader', 'cat', 'files/performance-v2-ui.json'))
assert raw['profileId'] == profile['profileId']
for case in raw['cases']:
    for kind, samples in case['samples'].items():
        assert len(samples) == 5
        limit = 300 if kind == 'panels' else 1000 if kind in ('theme', 'fontScale', 'mode') else None
        report['ui'].append({'format': case['format'], 'mode': case['mode'], 'kind': kind,
                             'corpusSha256': case['corpusSha256'], 'runsMs': samples,
                             'minMs': min(samples), 'medianMs': statistics.median(samples), 'maxMs': max(samples),
                             'accessTaps': 2 if kind in ('contents', 'settings') else None,
                             'limitMs': limit, 'timingIsUpperBound': True,
                             'status': 'NOT_VERIFIED_DEVICE' if not physical else
                             'PASS' if limit is None or max(samples) <= limit else 'NOT_VERIFIED_TIMING'})
report['fullSeriesCovered'] = bool(report['pagination'] and report['pagination'].get('fullProfileCovered'))
report['status'] = 'NOT_VERIFIED_DEVICE' if not physical else 'NOT_VERIFIED_RELEASE'
save(report)
print(f'Серия сохранена: {args.output}; status={report["status"]}; releaseAccepted=false')
