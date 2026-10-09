# Корпус импорта и обновление — T060

Дата: 2026-10-07 (корпус), 2026-10-09 (обновление). Стенд: Pixel_10 API 37,
arm64-v8a emulator. **PASS** в проверенных сценариях.

[Raw JSON корпуса](evidence/import-corpus-v2.json): 26 файлов, 19 READY,
3 CORRUPT, 2 EMPTY, 1 DRM, 1 UNSUPPORTED_FORMAT; ожидаемые статусы совпали.
86 открытий глав: каждая глава каждой READY книги открыта в VERTICAL и PAGINATED,
с проверкой текста, геометрии страниц и глобального номера. Прогон выполнен
в авиарежиме, прежний режим восстановлен. Удаляются только собственные записи теста.

[Подписанное обновление](evidence/upgrade/summary.json): v1.0.0 → v2.0.0 через
`adb install -r`, без uninstall/pm clear между версиями. Проверены две записи EPUB/FB2,
восстановление второй главы, DARK и 150%, новые defaults VERTICAL/подтверждение выхода;
оба режима читаются в авиарежиме. Room schema v1 побайтово совпадает с tag v1.0.0.
Успешное обновление Android Package Manager подтверждает совместимость подписи.
Точный UTF-16 offset длинного абзаца проверен отдельно в T059; upgrade UI подтверждает главу.

[Seed/verify logs, screenshots/XML](evidence/upgrade/) сохранены. Перед seed очищалась
только тестовая установка во временном read-only overlay; постоянные данные AVD не изменены.

Воспроизведение после сборки debug/external runner и подписанных APK обеих версий:

```sh
scripts/verify-import-corpus.sh /tmp/import-corpus-v2.json
# Только на одном временном emulator -read-only:
python3 scripts/verify-reader-upgrade.py
```

Результат не закрывает аппаратную производительность, usability или API 26.
