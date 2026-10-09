#!/usr/bin/env python3
"""Собирает подписанный релиз, проверяет подпись и формирует checksums."""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
signing = root / '.signing'
env = os.environ.copy()
names = ('HOOREADER_KEYSTORE_PATH', 'HOOREADER_KEYSTORE_PASSWORD', 'HOOREADER_KEY_ALIAS', 'HOOREADER_KEY_PASSWORD')
if not any(env.get(name) for name in names):
    password_file = signing / 'password'
    keystore = signing / 'hooreader-release.p12'
    if not (password_file.is_file() and keystore.is_file()):
        raise SystemExit('Нет подписи. Задайте HOOREADER_* variables или выполните scripts/create-release-key.py.')
    if keystore.stat().st_mode & 0o077 or password_file.stat().st_mode & 0o077:
        raise SystemExit('Ключ и пароль должны иметь права 600.')
    password = password_file.read_text().strip()
    env.update(HOOREADER_KEYSTORE_PATH=str(keystore), HOOREADER_KEYSTORE_PASSWORD=password,
               HOOREADER_KEY_ALIAS='hooreader', HOOREADER_KEY_PASSWORD=password)
if not all(env.get(name) for name in names):
    raise SystemExit('Неполная конфигурация HOOREADER_* signing variables.')
subprocess.run([str(root / 'gradlew'), ':app:check', ':app:assembleRelease', ':app:bundleRelease'],
               cwd=root, env=env, check=True)
metadata = json.loads((root / 'app/build/outputs/apk/release/output-metadata.json').read_text())
entry = metadata['elements'][0]
if metadata['applicationId'] != 'com.hooreader' or entry['versionName'] != '2.0.0' or entry['versionCode'] != 2:
    raise SystemExit('Release identity не соответствует 2.0.0 (2).')
sdk = env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
if not sdk:
    for line in (root / 'local.properties').read_text().splitlines():
        if line.startswith('sdk.dir='):
            sdk = line.split('=', 1)[1]
            break
if not sdk:
    raise SystemExit('Укажите ANDROID_HOME для проверки подписи.')
apksigner = Path(sdk) / 'build-tools/35.0.0/apksigner'
output = root / 'app/build/release/2.0.0'
output.mkdir(parents=True, exist_ok=True)
apk = output / 'HooReader-2.0.0.apk'
aab = output / 'HooReader-2.0.0.aab'
shutil.copy2(root / 'app/build/outputs/apk/release' / entry['outputFile'], apk)
shutil.copy2(root / 'app/build/outputs/bundle/release/app-release.aab', aab)
verified = subprocess.run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                          capture_output=True, text=True, check=True)
if 'Android Debug' in verified.stdout:
    raise SystemExit('Debug certificate запрещён для release.')
(output / 'apk-signature.txt').write_text(verified.stdout)
bundle_verified = subprocess.run(['jarsigner', '-verify', str(aab)],
                                 capture_output=True, text=True, check=True)
if 'jar verified.' not in bundle_verified.stdout or 'jar is unsigned' in bundle_verified.stdout:
    raise SystemExit('AAB signature verification failed.')
(output / 'aab-signature.txt').write_text(bundle_verified.stdout)
bundle_certificate = subprocess.run(['keytool', '-printcert', '-jarfile', str(aab)],
                                    capture_output=True, text=True, check=True).stdout
apk_digest = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', verified.stdout)
bundle_digest = re.search(r'SHA256: ([A-Fa-f0-9:]+)', bundle_certificate)
if not apk_digest or not bundle_digest or apk_digest[1] != bundle_digest[1].replace(':', '').lower():
    raise SystemExit('APK и AAB подписаны разными или нераспознанными сертификатами.')
(output / 'aab-certificate.txt').write_text(bundle_certificate)
checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in (apk, aab))
(output / 'SHA256SUMS.txt').write_text(checksums)
print(f'Готовые подписанные APK/AAB: {output}')
print(checksums, end='')
