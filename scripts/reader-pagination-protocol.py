#!/usr/bin/env python3
"""Профиль и пять повторов ранней пагинации; очищаются только собственные UUID теста."""
import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--allow-emulator', action='store_true', help='Диагностика; SC-004 не подтверждается')
parser.add_argument('--profile-only', action='store_true')
parser.add_argument('--case', help='Только сценарий с указанным id (для проверки harness)')
parser.add_argument('--corpus', type=Path, default=Path('/tmp/hooreader-v2-corpus/corpus'))
parser.add_argument('--output', type=Path, default=ROOT / 'specs/002-reader-appearance/evidence/pagination-probe.json')
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
ADB = os.environ.get('ADB', str(sdk / 'platform-tools/adb'))
serial = os.environ.get('ANDROID_SERIAL')


def command(*values, data=None):
    return subprocess.run(values, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout


def adb(*values, data=None):
    return command(ADB, '-s', serial, *values, data=data)


def shell(*values):
    return adb('shell', *values).decode().strip()


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(65536), b''):
            digest.update(chunk)
    return digest.hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def local_name(tag):
    return tag.rsplit('}', 1)[-1]


devices = command(ADB, 'devices').decode().splitlines()[1:]
devices = sorted(line.split()[0] for line in devices if line.strip().endswith('\tdevice'))
if not serial:
    physical_devices = [item for item in devices if not item.startswith('emulator-')]
    serial = next(iter(physical_devices or devices), None)
evidence = args.output.parent
profile_path = evidence / 'performance-profile.json'
if not serial:
    save(profile_path, {'status': 'NOT_VERIFIED_DEVICE', 'device': None,
                        'reason': 'Нет подключённого Android-устройства; аппаратный профиль не выбран.'})
    print('NOT_VERIFIED_DEVICE: устройства отсутствуют', flush=True)
    raise SystemExit(0)
physical = shell('getprop', 'ro.kernel.qemu') != '1' and not serial.startswith('emulator-')
if not (args.corpus / 'manifest.json').exists():
    command('python3', str(ROOT / 'scripts/generate-import-corpus.py'), '--large-output', str(args.corpus.parent))
corpus = []
for fmt in ('epub', 'fb2'):
    path = args.corpus / f'reader-20mb.{fmt}'
    assert path.stat().st_size >= 20_000_000
    if fmt == 'epub':
        with ZipFile(path) as archive:
            expanded = sum(item.file_size for item in archive.infolist())
            opf = ET.fromstring(archive.read('OPS/package.opf'))
            count = sum(local_name(item.tag) == 'itemref' for item in opf.iter())
        beginning = 0
    else:
        expanded = path.stat().st_size
        tree = ET.parse(path)
        body = next(item for item in tree.getroot() if local_name(item.tag) == 'body')
        count = sum(local_name(item.tag) == 'section' for item in body)
        beginning = 1  # первая непустая глава после синтетической пустой главы
    corpus.append({'format': fmt, 'sha256': sha(path), 'bytes': path.stat().st_size,
                   'expandedBytes': expanded, 'beginning': [beginning, 0, 0], 'late': [count - 1, 0, 0]})
cases = []
for book in corpus:
    for location in ('beginning', 'late'):
        for change, orientation, scale, system_font in (
                ('baseline', 'portrait', '1.0', '1.0'), ('scale', 'portrait', '2.0', '1.0'),
                ('orientation', 'landscape', '1.0', '1.0'), ('system_font', 'portrait', '1.0', '2.0')):
            for cache in ('cold', 'warm') + (('cold_source',) if change == 'baseline' else ()):
                cases.append({'id': f'{book["format"]}-{location}-{change}-{cache}',
                              'format': book['format'], 'anchor': book[location], 'cache': cache,
                              'orientation': orientation, 'readingScale': scale, 'systemFont': system_font,
                              'sha256': book['sha256']})
apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
test_apk = ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
profile = {
    'status': 'PROFILED' if physical else 'NOT_VERIFIED_DEVICE', 'physical': physical,
    'device': {'serial': serial, 'model': shell('getprop', 'ro.product.model'),
               'soc': shell('getprop', 'ro.soc.model') or None,
               'ram': shell('cat', '/proc/meminfo').splitlines()[0],
               'api': shell('getprop', 'ro.build.version.sdk'),
               'fingerprint': shell('getprop', 'ro.build.fingerprint'),
               'size': shell('wm', 'size'), 'density': shell('wm', 'density'),
               'display': shell('dumpsys', 'display'), 'battery': shell('dumpsys', 'battery'),
               'thermal': shell('dumpsys', 'thermalservice'),
               'peakRefreshRate': shell('settings', 'get', 'system', 'peak_refresh_rate'),
               'powerSave': shell('settings', 'get', 'global', 'low_power')},
    'build': {'type': 'debug', 'apkSha256': sha(apk), 'testApkSha256': sha(test_apk),
              'commit': command('git', '-C', str(ROOT), 'rev-parse', 'HEAD').decode().strip()},
    'generatorSha256': sha(ROOT / 'scripts/generate-import-corpus.py'),
    'corpus': corpus, 'scenarios': cases, 'runsPerScenario': 5,
    'theme': 'LIGHT', 'memorySamplingMs': 16,
    'timing': 'open -> source spool -> exact prefix -> source page draw -> next frame callback',
}
# Стабильная идентичность измерений не зависит от текущего заряда/динамического dumpsys.
identity = {k: v for k, v in profile.items() if k != 'device'}
identity['device'] = {k: v for k, v in profile['device'].items() if k not in ('display', 'battery', 'thermal')}
profile['profileId'] = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
if profile_path.exists():
    old = json.loads(profile_path.read_text())
    if old.get('profileId') and old['profileId'] != profile['profileId']:
        save(evidence / f'performance-profile-{old["profileId"]}.json', old)
save(profile_path, profile)
print(f'Профиль {profile["profileId"]}: {profile["status"]}', flush=True)
if args.profile_only or (not physical and not args.allow_emulator):
    raise SystemExit(0)
selected = [case for case in cases if not args.case or case['id'] == args.case]
if not selected:
    parser.error('Неизвестный --case')
adb('install', '-r', str(apk))
adb('install', '-r', str(test_apk))
shell('run-as', 'com.hooreader', 'mkdir', '-p', 'cache/pagination-probe')
for book in corpus:
    fmt = book['format']
    adb('exec-in', 'run-as', 'com.hooreader', 'sh', '-c',
        f'cat >cache/pagination-probe/reader-20mb.{fmt}', data=(args.corpus / f'reader-20mb.{fmt}').read_bytes())
def instrument(case, method):
    invocation = ['shell', 'am', 'instrument', '-w', '-e', 'class',
                  f'com.hooreader.acceptance.PaginationProbeTest#{method}']
    values = {'phase2Pagination': 'true', 'probeProfileId': profile['profileId'],
              'probePhysical': str(physical).lower(), 'probeCase': case['id'],
              'probeCache': case['cache'], 'probeFormat': case['format'],
              'probeChapter': str(case['anchor'][0]), 'probeScale': case['readingScale'],
              'probeSystemFont': case['systemFont'], 'probeOrientation': case['orientation'],
              'probeExpectedHash': case['sha256']}
    for name, value in values.items():
        invocation.extend(['-e', name, value])
    invocation.append('com.hooreader.test/androidx.test.runner.AndroidJUnitRunner')
    output = adb(*invocation).decode()
    (evidence / f'{case["id"]}-{method}-instrumentation.txt').write_text(output)
    if 'OK (1 test)' not in output:
        raise RuntimeError(f'Ошибка instrumentation: {case["id"]}/{method}; см. сохранённый журнал')


old_font = shell('settings', 'get', 'system', 'font_scale')
series = []
try:
    for case in selected:
        shell('settings', 'put', 'system', 'font_scale', case['systemFont'])
        instrument(case, 'measureFiveRuns')
        raw = json.loads(adb('exec-out', 'run-as', 'com.hooreader', 'cat',
                             'files/pagination-probe-series.json').decode())
        assert raw['profileId'] == profile['profileId'] and len(raw['runs']) == 5
        series.append(raw)
        save(args.output, {'profileId': profile['profileId'], 'status': profile['status'],
                           'expectedScenarios': len(selected), 'profileScenarioCount': len(cases),
                           'fullProfileCovered': len(series) == len(cases),
                           'completedScenarios': len(series), 'series': series})
        print(f'{case["id"]}: max {raw["maxMs"]:.1f} ms, {raw["status"]}', flush=True)
    cancel_case = next(case for case in cases if case['id'] == 'fb2-late-baseline-cold')
    shell('settings', 'put', 'system', 'font_scale', cancel_case['systemFont'])
    instrument(cancel_case, 'cancelObsoleteLayout')
    cancelled = json.loads(adb('exec-out', 'run-as', 'com.hooreader', 'cat',
                               'files/pagination-probe-cancellation.json').decode())
    report = json.loads(args.output.read_text())
    report['cancellation'] = cancelled
    save(args.output, report)
    print(f'Отмена старого LayoutKey: {cancelled["cancellationMs"]:.1f} ms', flush=True)

finally:
    if old_font == 'null':
        shell('settings', 'delete', 'system', 'font_scale')
    else:
        shell('settings', 'put', 'system', 'font_scale', old_font)
