# Корпус импорта и открытия — T044

Дата: 2026-10-01. Корпус v1: 17 допустимых файлов + 7 отрицательных контрольных файлов.
Состав зафиксирован в [manifest.json](../../app/src/androidTest/assets/books/corpus/manifest.json)
с byte size и SHA-256 каждого файла. SHA-256 самого manifest:
`a18dda4ca72be5712d30d8818bc0f8b9bbf3d859e72c8d6fadb676c2165f2b51`.
[Индивидуальные результаты Android-прогона](evidence/import-corpus-raw.json).

## Состав и граница вывода

Для текущей фазы выбран воспроизводимый синтетический baseline на основе существующих fixtures;
использовано разрешение пользователя выполнять задачи без уточнений. Тексты созданы для HooReader,
сторонних произведений/DRM-ключей нет. Это фиксированный проектный corpus v1, а не внешне
утверждённая репрезентативная выборка коммерческих/публичных книг. Отдельного согласования
представительности с reviewer не было; 100% здесь не доказывает 99% на произвольных книгах.

Покрытие: EPUB 2/NCX и EPUB 3/nav, FB2 UTF-8, Windows-1251, UTF-16 LE с BOM, UTF-16 BE с BOM,
кириллица/латиница/диакритика/типографская пунктуация, fallback metadata, главы и вложенные sections,
bold/italic/lists, PNG/SVG и повреждённые media, игнорирование активных элементов/внешних image URI.
Font-obfuscation fixture проверяет принятие стандартного IDPF marker; synthetic font placeholder
не является валидным шрифтом и не отображается. Корпус проверяет поддерживаемый parser profile,
без внешнего EPUBCheck/FB2 schema validation.

Пустые/оборванные файлы, AES DRM marker, PDF и XXE — отдельные отрицательные controls;
они не включаются в знаменатель успешного открытия допустимых книг. SVG/нечитаемое изображение
в допустимой книге может дать заглушку: незатронутый текст должен оставаться доступным.

## Метод и результат

Pixel_10 AVD, Android 17 / API 37, debug APK, тот же стенд, что в
[quickstart-results.md](quickstart-results.md). `ImportCorpusTest` для каждого файла проверяет
manifest size/hash, запускает настоящий `BookImportService` (копия/SHA-256/Readium или FB2/Room).
Для каждого READY файла создаётся ReaderViewModel и настоящий Compose ReaderScreen; открывается
каждая глава, проверяются reader list, непустой block и успешное сохранение позиции.
Для отказов проверяются точный enum причины и отсутствие изменений библиотеки.
База in-memory изолирована; app-local копии создаются настоящим storage и удаляются после проверки.

- Успешный импорт **и** открытие всех глав: **17/17 = 100%**.
- Открытые главы: **34/34**.
- Ожидаемый отказ отрицательных controls: **7/7 = 100%**.
- Неожиданные отказы/дубликаты: **0**. Instrumentation: `OK (1 test)`.
- Порог SC-003 ≥99% выполнен на corpus v1; представительность и физическое устройство
  отдельно не подтверждены.

| Файл | Ожидание | Фактический результат | Открытые главы | Статус |
|---|---|---|---:|---|
| `structured.epub` | READY | READY | 2 | PASS |
| `no-author-cover.epub` | READY | READY | 2 | PASS |
| `structured.fb2` | READY | READY | 2 | PASS |
| `windows-1251.fb2` | READY | READY | 2 | PASS |
| `missing-metadata.fb2` | READY | READY | 2 | PASS |
| `corpus/utf8.fb2` | READY | READY | 2 | PASS |
| `corpus/utf16le.fb2` | READY | READY | 2 | PASS |
| `corpus/utf16be.fb2` | READY | READY | 2 | PASS |
| `corpus/cp1251.fb2` | READY | READY | 2 | PASS |
| `corpus/no-metadata.fb2` | READY | READY | 2 | PASS |
| `corpus/nested.fb2` | READY | READY | 2 | PASS |
| `corpus/broken-image.fb2` | READY | READY | 2 | PASS |
| `corpus/epub2.epub` | READY | READY | 2 | PASS |
| `corpus/epub3.epub` | READY | READY | 2 | PASS |
| `corpus/broken-image.epub` | READY | READY | 2 | PASS |
| `corpus/active-external.epub` | READY | READY | 2 | PASS |
| `corpus/font-obfuscation.epub` | READY | READY | 2 | PASS |
| `empty.epub` | EMPTY | EMPTY | — | PASS |
| `empty.fb2` | EMPTY | EMPTY | — | PASS |
| `corrupt.epub` | CORRUPT | CORRUPT | — | PASS |
| `corrupt.fb2` | CORRUPT | CORRUPT | — | PASS |
| `drm-marker.epub` | DRM | DRM | — | PASS |
| `unsupported.pdf` | UNSUPPORTED_FORMAT | UNSUPPORTED_FORMAT | — | PASS |
| `corpus/xxe.fb2` | CORRUPT | CORRUPT | — | PASS |

## Воспроизведение

```sh
python3 scripts/generate-import-corpus.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
ADB="$ANDROID_HOME/platform-tools/adb" ANDROID_SERIAL=emulator-5554 \
  scripts/verify-import-corpus.sh /tmp/hooreader-import-corpus.json
```

Генератор использует только Python standard library, фиксирует порядок ZIP entries и timestamp;
`mimetype` первым entry без сжатия. Existing baseline fixtures остаются без изменений.
Повторная генерация должна оставить Git diff пустым. Для расширения corpus меняются manifest
и ожидаемый CORPUS_FILES теста; результаты и denominator пересчитываются после полного Android-прогона.
