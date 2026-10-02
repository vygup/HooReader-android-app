# Ход реализации второго релиза

## Выполнено до T009

Фаза 1 завершена: воспроизводимые fixtures и исходные 56 debug/56 release unit tests,
build/lint/Detekt. [Исходный отчёт](implementation-baseline.md).

T003–T009: последовательный источник, spool/codec, соседние окна, локальные image bounds,
общая типографика и верхняя видимая строка. Проверки T009: 70 debug unit tests,
Detekt; 2 Android tests на Pixel_10 API 37 (реальный Compose layout и существующий reader).
[Журнал Android](evidence/validation/T009-android.txt).

## Существенные решения

- Существующий EPUB parser представляет пустую главу fallback-блоком, FB2 — нулём блоков.
  Поток сохраняет именно старые координаты и эту нормализацию; тест проверяет оба случая.
- Счётчик `sourcePassCount` измеряет текстовые обходы источника; открытие embedded media
  учитывается отдельно при измерениях, счётчик не выдаётся за число всех IO-операций.
- Spool использует length-prefixed UTF-8, а offsets стилей/позиции остаются UTF-16.
  Индекс содержит directory/checkpoints, текст хранится только на диске и в окне до 128 блоков.
- `.part` не считается готовым индексом; публикация — rename проверенного каталога.
  SHA-256, версии, количество записей, координаты и checkpoints проверяются до reuse.
- `ReaderContentRepository` подключён в dependencies. Перевод production reader на общий
  source window предусмотрен дальнейшими интеграционными задачами US2; сейчас прежний
  вертикальный loader сохранён до этого переключения.
- Локальные media копируются потоково и кешируются в производном каталоге; LRU image
  metrics ограничен 32 элементами. Отсутствующий ресурс получает fallback до измерения.
- Typography общая для measurement/draw: списки имеют отдельный маркер шириной 24dp,
  межблочный интервал 16dp; source offsets маркером не изменяются.
- Сохранение верхней видимой строки использует TextLayoutResult. Смещение между high/low
  surrogate нормализуется назад до начала пары. Room v1/формат ReadingPosition не изменены.

## Незавершённые доказательства

Физическое Android-устройство отсутствует: SC-004 пока NOT_VERIFIED_DEVICE.
API 26 AVD отсутствует; текущий API 37 годится для функциональной разработки.
Reviewer-owned UX checklist остаётся без изменений; исполнение разрешено пользователем.
T010 завершена: общий TextMeasurer, исходные линии/clipping, атомарные chapter page records,
точные prefix counts и binary-search anchor; два последних layout-кэша на книгу.
73 debug и 73 release unit tests; check/build/test APK прошли. После усиления закрытия
ресурсов при отмене повторены PageIndex unit tests/Detekt и два Android-теста EPUB/FB2.
[Build](evidence/validation/T010-gradle.txt), [Android](evidence/validation/T010-android.txt).
Android-тесты восстановили весь текст fixtures из page fragments без пропусков/дубликатов
и разрыва surrogate pairs, проверили geometry, новый старт главы и повторное чтение индекса.
В debug probe нижняя полоса номера измеряется отдельно; она входит в LayoutKey и не закрывает
содержимое. Замер ready frame относится к первому draw и следующему frame callback,
а не к завершению paginator callback. Сам прототип не является production reader.
T011 завершена: opt-in `PaginationProbeTest`, wrapper и Python driver задают 36 сценариев
(EPUB/FB2, начало/поздняя глава, cold/warm, сброс source spool, новый масштаб/ориентация,
реальный системный шрифт 200%). По пять повторов; исходные значения, min/median/max,
source passes, sampled PSS/heap/native, EOF/frontier и отмена начатого старого LayoutKey.
Настройка системного шрифта восстанавливается в finally; очищаются только производные
данные собственных UUID теста. Нагрузочные файлы не добавляются в assets/Git.

Smoke harness на Pixel_10 API 37: пять FB2 cold_source запусков (20 643 379 bytes),
max 2883,8 ms; отмена старого layout 3,3 ms, obsolete results и `.part` не опубликованы.
[Все значения](evidence/harness-smoke.json); [профиль](evidence/performance-profile.json).
`check`/APK build прошли; smoke не заменяет полную серию T012. Профиль создан до запуска,
содержит реальные APK SHA-256 и базовый commit, код T011 в тот момент был в рабочем дереве.
Физического устройства нет: NOT_VERIFIED_DEVICE, SC-004 не подтверждён.
T012 завершена: после исправления смешанного source состояния полностью повторены все
36 сценариев по пять раз (180 raw значений), flags source/page cache проверяются до таймера.
Неверная первая серия сохранена отдельно с INVALID_MIXED_SOURCE_STATE; значения не удалялись.
[Ранний отчёт](pagination-probe-results.md) и [raw JSON](evidence/pagination-probe.json)
содержат profileId, каждое значение и min/median/max; отдельно точный номер, draw frame,
память и отмена. Все Android assertions прошли; аппаратный SC-004 остаётся NOT_VERIFIED_DEVICE.
Следующая задача — T013: оптимизация выявленного warm reopen/IO риска и повторный срез.

## T013 — завершена

SHA-256 spool вычисляется при проверке length-prefixed записей одним файловым проходом.
Декодирование, координаты/checkpoints/EOF и digest по-прежнему обязательны; отдельный
тест валидной подмены текста требует пересоздания исходного spool. PageIndex пишет
на IO через bounded channel ёмкостью 1, сохраняя отмену/atomic publish; максимальное
число slices в writer pipeline — три. Текстовые source passes и media opens учитываются
раздельно, их сумма не является числом всех OS IO-вызовов.

Полный `check`, debug APK/test APK и `connectedDebugAndroidTest` прошли: 74 debug +
74 release unit tests; Android API 37: 25 passed, 5 opt-in skipped, 0 failures.
Устаревшее ожидаемое количество import corpus исправлено 24→26 после двух fixtures T001;
все 26 файлов по-прежнему проверяются по SHA, импорту и открытию каждой главы.
[Полный лог](evidence/validation/T013-gradle-android.txt). Завершены все 36×5 измерений
в `evidence/pagination-probe-optimized.json`; raw/cache flags/counts проверены.
[Повторный отчёт](pagination-probe-optimized-results.md) сохраняет все значения, включая
max 21715.550 ms. Две диагностические серии повторены отдельно, меньшие значения не
заменяют исходных выбросов. Отмена старого layout 15.802 ms, obsolete/staging отсутствуют.
Бюджет SC-004 открыт, NOT_VERIFIED_DEVICE; metadata reopen и крупнейший целый layout
сохраняются как риски T035/T061. Unicode и полный корпус Android прошли без регрессий.
Следующая задача — T014, начало Phase 3 US1.
