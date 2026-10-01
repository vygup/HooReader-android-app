# Сквозная проверка quickstart — T042

Дата: 2026-10-01. Устройство: Pixel_10 AVD, Android 17 / API 37, arm64, 16 KB pages,
1080×2424, 60 Hz; host Apple M3, GPU host. Сборка debug, minSdk 26 / targetSdk 36.
Основание: [quickstart.md](quickstart.md). Устройство API 26 и физический телефон в этом прогоне
не проверялись. Результаты нельзя переносить на все устройства без дополнительного прогона.

## Запуск

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:detekt :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
ADB="$ANDROID_HOME/platform-tools/adb" ANDROID_SERIAL=emulator-5554 scripts/verify-reader-process-death.sh
```

Итог общего instrumentation XML: 23 теста, 21 пройден, 2 штатно пропущены (seed/verify
process-death требуют отдельных процессов), ошибок/падений нет. Оба отдельных process-death
метода затем прошли: `OK (1 test)` для seed и verify. Unit: по 56 тестов debug/release,
0 failures/errors/skips. Detekt: 0 нарушений; Lint: 0 ошибок, 25 предупреждений.
Первый запуск нового теста выявил неверный возвращаемый тип JUnit; исправлен явным `Unit`,
после чего полный запуск успешен.

## Покрытие сценариев

| Сценарий quickstart | Выполнение и наблюдение | Итог |
|---|---|---|
| Основной 1–2: EPUB через File Picker, metadata и 0% | Помимо Espresso Intents выполнен настоящий `ACTION_OPEN_DOCUMENT`: Download → `phase7-structured.epub`. Reader открыл кириллицу/диакритику; возврат показал title, author, placeholder (fixture имеет SVG cover), ≈0%. Осмотрен screenshot reader. | PASS |
| Основной 3–4: поздняя глава и перезапуск | `ReadingPositionRestoreTest` для EPUB/FB2; независимые repository/parser/database/VM, удалён оригинал. `ProcessDeathAcceptanceTest`: seed, `am force-stop`, verify с новым PID. Глава 1, блок 1, characterOffset 4 совпали точно. | PASS |
| Основной 5: авиарежим | Process-death verify проверяет airplane_mode_on=1, оба локальных текста доступны, flush позиции успешен; скрипт восстанавливает предыдущий авиарежим. | PASS |
| Форматы 1: объявленная кодировка FB2 | `ImportBookFlowTest` выбрал Windows-1251 FB2, reader доступен; parser tests проверяют корректную кириллицу. UTF-8 FB2 проходит restore/process-death. | PASS |
| Форматы 2: содержание/font/theme/orientation | `ReaderNavigationTest`: оба формата, начало главы, блок 24 после поворота и system fontScale. `ReaderSettingsTest`: размер 150%, обе темы, recreation и повторное открытие, тот же блок; pixels/system bars проверены. Process-death дополнительно подтверждает DARK/1.5 в новом процессе. | PASS |
| Форматы 3: разметка и повреждённое image | `ContentBlockRendererTest` проверяет перекрывающиеся bold/italic spans, marker списка, PNG и corrupt/unreadable image с доступным следующим абзацем. Parser tests покрывают headings и damaged XHTML. SVG fixture в настоящем reader даёт безопасную заглушку. | PASS |
| Форматы 4: PDF/empty/corrupt/DRM | Новый UI test проходит unsupported.pdf, empty.epub, corrupt.epub, corrupt.fb2, drm-marker.epub. Для каждого проверены русское сообщение и неизменный набор bookId; затем допустимая книга импортируется. Empty FB2 отдельно проверен прежним UI test. | PASS |
| Форматы 5: duplicate/delete/source | `ImportBookFlowTest`: duplicate сохраняет record/position; отмена и подтверждение удаления, cascade и исходные bytes. Restore/process-death удаляют original до чтения app-local copy. Настоящий picker-прогон завершён UI-удалением: библиотека пуста, исходный Download EPUB остаётся (2286 bytes). | PASS |
| Порог: открытие 20 MB и плавность 100 книг | `LibraryScreenTest` уже проверяет доступность сотой карточки. Количественные замеры и оценка порогов выделены в T043: [performance-results.md](performance-results.md). | См. T043; функциональный PASS не подтверждает SC-004/005. |

## Границы проверки

SC-001/002/006 подтверждены для перечисленных воспроизводимых сценариев; это не статистическая
гарантия всех пользовательских книг. Test corpus и доля открытия — отдельный отчёт T044.
Настоящий системный picker проверен для EPUB; автоматические FB2/error-сценарии подставляют
результат OpenDocument через Espresso, затем читают bytes реальным ContentResolver.
TalkBack, физическое устройство среднего класса и Android API 26 остаются за границей этого прогона.
