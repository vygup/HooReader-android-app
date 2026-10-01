#!/usr/bin/env python3
"""Создаёт локальный ключ первого релиза. Никогда не заменяет существующие файлы."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parents[1]
signing = root / '.signing'
keystore = signing / 'hooreader-release.p12'
password_file = signing / 'password'
if keystore.exists() or password_file.exists():
    raise SystemExit('Ключ или файл пароля уже существует; повторная генерация запрещена.')
signing.mkdir(mode=0o700, exist_ok=True)
signing.chmod(0o700)
password = secrets.token_urlsafe(48)
fd = os.open(password_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as output:
    output.write(password + '\n')
result = subprocess.run([
    'keytool', '-genkeypair', '-alias', 'hooreader', '-keyalg', 'RSA', '-keysize', '3072',
    '-sigalg', 'SHA256withRSA', '-validity', '10000', '-dname', 'CN=HooReader release',
    '-storetype', 'PKCS12', '-keystore', str(keystore),
    '-storepass:file', str(password_file), '-keypass:file', str(password_file),
], capture_output=True, text=True)
if result.returncode:
    # Keytool output is intentionally withheld; do not risk logging credential data.
    raise SystemExit('Не удалось создать ключ. Локальные файлы сохранены для диагностики; пароль не выводится.')
keystore.chmod(0o600)
print('Ключ создан в .signing/hooreader-release.p12; пароль — .signing/password (оба вне Git).')
