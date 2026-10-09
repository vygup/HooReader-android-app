#!/usr/bin/env python3
"""Готовит публичный комплект выпуска из проверенных подписанных APK/AAB."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '2.0.0'
OUTPUT = ROOT / 'app/build/release' / VERSION
EVIDENCE = ROOT / 'specs/002-reader-appearance/evidence'


def read_json(path):
    return json.loads(path.read_text())


def sha256(data):
    return hashlib.sha256(data).hexdigest()


provenance = read_json(OUTPUT / 'build-provenance.json')
if provenance['versionName'] != VERSION or provenance['versionCode'] != 2:
    raise SystemExit('Неверная версия подписанного комплекта.')
if (provenance['workingTreeDirtyAtBuildStart'] or provenance['workingTreeDirtyAfterBuild'] or
        provenance['sourceCommit'] != provenance['sourceCommitAfterBuild']):
    raise SystemExit('Для комплекта выпуска нужна сборка из зафиксированного исходного состояния.')

payload = {name: (OUTPUT / name).read_bytes() for name in
           (f'HooReader-{VERSION}.apk', f'HooReader-{VERSION}.aab', 'SHA256SUMS.txt')}
hashes = {name: sha256(data) for name, data in payload.items() if name.endswith(('.apk', '.aab'))}
if hashes != provenance['artifactsSha256']:
    raise SystemExit('Артефакты изменились после проверки подписанной сборки.')
expected = ''.join(f'{hashes[name]}  {name}\n' for name in hashes)
if payload['SHA256SUMS.txt'].decode() != expected:
    raise SystemExit('SHA256SUMS не соответствует APK/AAB.')

smoke = read_json(EVIDENCE / 'release/summary.json')
upgrade = read_json(EVIDENCE / 'upgrade/summary.json')
apk_name = f'HooReader-{VERSION}.apk'
if (smoke['status'] != 'PASS' or smoke['artifacts'].get(apk_name) != hashes[apk_name] or
        upgrade['status'] != 'PASS' or upgrade['apkSha256'].get(apk_name) != hashes[apk_name]):
    raise SystemExit('Текущий APK требует release smoke и проверки обновления; старые hashes не совпали.')

acceptance = read_json(EVIDENCE / 'validation-summary.json')
payload['RELEASE_NOTES.md'] = (ROOT / f'docs/releases/{VERSION}.md').read_bytes()
payload['INSTALLATION.md'] = (
    '# Установка HooReader 2.0.0\n\n'
    'Требуется Android 8.0 (API 26) или новее. Для установки используется файл '
    '`HooReader-2.0.0.apk`. Откройте его на Android и подтвердите установку.\n\n'
    'Подпись совместима с 1.0.0: обновление устанавливается поверх приложения, '
    'сохраняя локальную библиотеку и настройки. Удалять приложение перед обновлением не требуется.\n\n'
    'AAB предназначен для доставки через магазин и не устанавливается открытием файла. '
    'Checksums APK/AAB находятся в SHA256SUMS.txt. Условия приёмки и известные ограничения '
    'записаны в RELEASE_NOTES.md и RELEASE_MANIFEST.json.\n'
).encode()
manifest = {
    'versionName': VERSION, 'versionCode': 2, 'applicationId': 'com.hooreader', 'minSdk': 26,
    'sourceCommit': provenance['sourceCommit'], 'buildFinishedAt': provenance['buildFinishedAt'],
    'certificateSha256': provenance['certificateSha256'],
    'releaseAccepted': acceptance['releaseAccepted'], 'remainingGates': acceptance['blockedBy'],
    'releaseExceptions': acceptance.get('releaseExceptions', []),
    'packageStatus': ('ACCEPTED_RELEASE_WITH_EXCEPTION' if acceptance.get('releaseExceptions') else
                      'ACCEPTED_RELEASE') if acceptance['releaseAccepted'] else 'RC_PENDING_ACCEPTANCE',
    'usabilityStatus': acceptance['SC']['SC-007']['status'],
    'smokeVerifiedApkSha256': hashes[apk_name], 'upgradeVerifiedApkSha256': hashes[apk_name],
    'smokeEvidenceDate': smoke['smokeAt'],
    'filesSha256': {name: sha256(data) for name, data in payload.items()},
}
payload['RELEASE_MANIFEST.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
archive = OUTPUT / f'HooReader-{VERSION}-release.zip'
temporary = archive.with_suffix('.zip.part')
with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as bundle:
    for name, data in payload.items():
        info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        info.external_attr = 0o100644 << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        bundle.writestr(info, data)
with zipfile.ZipFile(temporary) as bundle:
    if bundle.testzip() is not None or set(bundle.namelist()) != set(payload):
        raise SystemExit('Проверка архива не пройдена.')
    for name, data in payload.items():
        if bundle.read(name) != data:
            raise SystemExit('Содержимое архива повреждено: ' + name)
temporary.replace(archive)
(OUTPUT / 'RELEASE_MANIFEST.json').write_bytes(payload['RELEASE_MANIFEST.json'])
(OUTPUT / (archive.name + '.sha256')).write_text(f'{sha256(archive.read_bytes())}  {archive.name}\n')
print(f'{manifest["packageStatus"]}: {archive}')
print(f'SHA-256: {sha256(archive.read_bytes())}')
