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

## Контрольная точка Phase 2

T003–T013 завершены (`cfb4c88`). Полный check/build/Android прогон T013 подтверждает
координаты, spool corruption/rebuild, UTF-16 восстановление, EPUB/FB2 measured fragments,
atomic page records и отмену. Общий слой и ранний эксперимент готовы для интеграции US1/US2.
Производительность не объявлена принятой: физического стенда нет, диагностические cold/warm
значения превышают бюджет. Риски явно перенесены в T035/T061 согласно tasks.md.

T014: пять unit-контрактов chrome введены; компиляция успешна, пять assertions выявляют
отсутствие единого состояния chrome. Это ожидаемый RED до T016, не проверка компиляцией.
Переходы меню/drag/reopen и 20 toggles будут повторены после реализации. Следующая T015.

T015: четыре Android-сценария с настоящим touch input и ReaderDestination введены;
APK компилируется. RED: отсутствует reader_viewport/скрытый chrome. Проверки 20 toggles,
100 жестов и menu Back будут повторены после T019–T020. Следующая T016.

T016: чистый reducer chrome реализован; все пять T014 GREEN, detekt прошёл.
Состояние не содержит anchor/layout; EXIT_CONFIRMATION пока неактивен. Следующая T017.

T017: gesture observer реализован без consume и без controlsVisible в pointerInput key;
системный slop/longPressTimeout, достигнутый drag и multi-pointer/consumed suppression.
Android child-click/cancel/обычный tap GREEN, detekt/APK GREEN. Следующая T018.

T018: sibling overlay controls с FlowRow и собственными touch targets готов;
скрытый overlay будет отсутствовать в композиции. Detekt/debug build GREEN. Следующая T019.

T019: viewport постоянный, chrome принадлежит ReaderViewModel, controls — sibling.
В скрытом VERTICAL видны глава и процент; title/count/navigation скрыты.
79 debug unit tests, detekt/build GREEN; три Android-теста GREEN, включая все
20 jitter toggles с неизменными bounds/anchor и 100 drag/cancel/diagonal/boundary
жестов без открытия панелей. Следующая T020 — единый overlay NavHost.

T020: local reader-menu флаг NavHost удалён. Enum overlay ViewModel владеет обоими
sheets, dismiss/ChapterSelected скрывают panels; Opening/error сохраняют доступ к exit/settings.
Предыдущие UI сценарии обновлены реальным первым tap. 79 debug unit tests, build/lint/detekt прошли; release unit suite
был повторён полностью в T021 до 79 успешных тестов. Полный Android прогон выявил ошибку самого нового теста Back:
Activity dispatcher обходил окно BottomSheet. Исправлено на настоящий Espresso Back;
четыре ReaderScreenTest GREEN, включая быстрые два запроса меню в одном touch batch.
Общая Android серия повторяется в phase checkpoint T021. Следующая T021.

T021: T014/T015 и минимальный US1 путь прошли на API 37. Полный phase check:
79 debug + 79 release unit tests, detekt/lint/build/APK GREEN; Android 29 passed,
6 opt-in skipped, 0 failures (35 tests). Реальный Back теперь вводится KEYCODE_BACK,
чтобы не обходить modal window и не зависеть от Espresso root picker.
Пять panel timings отдельно выполнены opt-in через Gradle и повторены ADB для сохранения raw:
Gradle удаляет тестовую установку после connected-run, первый raw не сохранился; это
не серия приёмки. Сохранённая повторная серия: 147.322, 132.398, 146.392, 131.785,
129.935 ms, max 147.322 ms. Profile/corpus/APK hashes проверены; viewport/anchor неизменны.
Таймер до наблюдаемого drawn capture — диагностическая верхняя оценка, не физический
first-presented frame. NOT_VERIFIED_DEVICE; SC-004 аппаратно не принят.
[Итоги](quickstart-results.md). Следующая T022, Phase 4 US2.

## Контрольная точка Phase 3

T014–T021 завершены (`b4124ae`); итоговый полный check/build/Android и отдельные panel
измерения выполнены. US1 функционально подтверждён в вертикальном режиме: скрытие,
жесты, стабильный viewport, единый overlay, реальный Back. Аппаратный SC-004 имеет
NOT_VERIFIED_DEVICE; постраничный SC-001 ещё относится к Phase 4. Следующая задача T022.

T022: восемь targeted unit tests GREEN (3 native TextPaginator + 5 PageIndex), detekt GREEN.
Реальные line metrics сохраняют текст/стили/surrogates, длинный абзац, list indent, цельный
image ratio и fallback пустой главы; chapter pages нумеруются точным prefix.
Пустая глава корпуса расположена в середине, не первой; тест исправлен по фактическому fixture.
Source metadata отличает завершённую пустую главу от EOF. Изменения content/parser/schema/
viewport/insets/font samples/locale/direction/scale инвалидируют cache; старый prefix rebuild.
Продолжение production frontier/resume относится к T028. Следующая T023.

T023: четыре реальные destination/input Android-контракта введены, APK/detekt GREEN.
Все четыре RED по отсутствию reading_mode_paginated, не ошибке компиляции.
Fixtures содержат 139264 code points (~250 KB UTF-8), threshold проверяет >100000 UTF-16
chars; unit guard длинного paragraph усилен тем же корректным порогом.
Geometry assertions берут актуальный in-memory anchor из настоящего ViewModel, не
предыдущую debounce запись Room. Проверки pages/cancel/chapters/reopen/scale/rotation
повторяются в T034 после интеграции. Следующая T024.

T024: ReadingMode VERTICAL/PAGINATED и default VERTICAL в ReaderPreferences добавлены.
Прежние LIGHT/finite 0.75–2.0/default 1.0 сохранены; 84 debug unit tests, detekt,
build GREEN. Следующая T025 — долговечный preference и typed API.

T025: reading_mode сохраняется в прежнем singleton DataStore; отсутствующий/неизвестный
режим даёт VERTICAL. Добавлены old-file/fallback/restart/cross-field tests, typed settings
API и typed cleanup PagedReaderTest. 86 debug unit tests, detekt/build/test APK GREEN.
Существующая retry closure намеренно развивается в pending queue T045. Следующая T026.

T026: PageSlice связывает anchors с первым/последним source fragment, запрещает gaps/
повторы/overlap, validates whole-image descriptors и неотрицательный точный prefix.
LayoutKey явно включает четыре системных safeDrawing insets, метрики/font/locale/versions;
colors/chrome не добавлены. PageIndex writer/read сохраняют exact prefix equation.
87 unit tests, detekt/build/APK и два EPUB/FB2 Android measured-page tests GREEN.
Следующая T027 — bounded content/measurement и сложные fallback/batch cases.

T027: exact source-line packing сохраняется, добавлен единственный source layout LRU:
8 layouts и 512000 измеренных UTF-16 chars; внутренний TextMeasurer cache для source
отключён. Большой одиночный layout сверх cache budget не кешируется; synchronous whole
block measurement остаётся явно отмеченным риском T035. Source batches ≤128, отмена
проверяется до каждого блока и между страницами. Изображения цельные/пропорциональные,
metrics/fallback до packing, пустая глава имеет fallback страницу. Новый 300-block +
битый bitmap тест проходит без повторных text scans, offsets сохраняют исходную parser
fallback подпись (она не пустая). 88 unit tests, detekt/build/test APK и два
настоящих EPUB/FB2 Android page-layout tests GREEN. Следующая T028.

T028: PageIndexStore publishes prefix.json атомарным synced ATOMIC_MOVE после завершённых
chapter page/offset records; валидирует source count, layout, counts/sequence/EOF и hashes
завершённых глав при resume. PagePrefix отличает frontier от EOF, даёт точный binary mapping
global number→chapter и anchor→page. Cancellation сохраняет предыдущий prefix, удаляет
part; corruption rebuild, invalidation/eviction не затрагивают original/Room position.
MRU timestamp обновляется при reuse; два последних использованных layouts остаются.
3 store + 6 page-index targeted tests GREEN; полный check/build/test APK: 91 debug
и 91 release unit tests, lint/detekt GREEN. Следующая T029 — production renderer.

T029: production PagedContentRenderer переиспользуется debug probe; рисует whole-source
TextLayoutResult с исходными spans и clipping sourceTop, list marker/indent и sampled
локальные bitmap. Подготовка держит только fragments текущей страницы (≤128), source
layout LRU остаётся bounded. Отказ bitmap после packing заменяет содержимое фиксированной
области, не меняет PageSlice/anchors/номер. Native regression проверяет этот отказ и
source line geometry каждого подготовленного fragment EPUB/FB2. 92 debug unit tests,
detekt/build/test APK GREEN; два Android measured-and-drawn tests GREEN API 37.
Следующая T030 — HorizontalPager и абсолютный frontier mapping.

T030: HorizontalPager с PagerSnapDistance.atMost(1), settled-only callbacks и initial
restore suppression готов. Absolute zero-based indices не меняются при расширении
точного PagePrefix; один frontier slot существует только пока source EOF неизвестен.
В composition bounded current/adjacent cells, async IO failures retryable; observer
не открывает chrome после drag/cancel/boundary. Android реальный fast fling в обоих
направлениях, cancel, frontier→EOF и обе границы GREEN API 37; detekt/build/APK GREEN.
Следующая T031 — geometry generations и pending position revisions.

T031: ReaderViewModel владеет ReaderPaginationController и Reading/PreparingPages
pipeline с effective mode/scale, logicalPosition и generation. USER_PREFERENCE требует
flush, failed request держит прежний выбор и retry; SYSTEM_CONFIGURATION отменяет
старое вычисление и сразу использует anchor из памяти, не ждёт IO. Только актуальные
generation/LayoutKey публикуются; containing-page restore не переписывает anchor,
explicit settled navigation сохраняет page start. PositionSaver имеет pending revisions,
не отменяет активную запись новым debounce, flush догоняет latest revision; старый успех
не скрывает новую pending/error. Native tests rapid geometry/inner anchor/stale callbacks/
failed flush/system geometry/navigation/retry и deferred-write races GREEN. 96 debug
+96 release unit tests, check/build/lint/detekt/test APK GREEN. Подключение source и UI
environment предусмотрено T033; legacy opening пока сохраняется. Следующая T032.

T032: две radio options с contract tags рядом с масштабом, русские строки и
автоматический typed settings callback добавлены. DataStore reading_mode общий для всех
книг, прежние theme/scale не меняются. Android chooser selection→persist→новый repository
и обратный выбор GREEN API 37, detekt/build/test APK GREEN. JUnit test Unit signature
исправлена после initial validation error, итоговый реальный сценарий прошёл.
Подключение effective режима к renderer выполняется T033. Следующая T033.

T033: единственный source pipeline активирован во всех ReaderViewModel constructors;
ChapterBlockLoader из reader удалён. Dependencies поставляют content/page stores,
NavHost создаёт reader после persisted preferences и передаёт USER geometry apply/write
callback (включая deferred retry). Тема меняет только цвета. Vertical window использует
виртуальные fallback items пустых глав и ≤128 source records соседних глав; callback
принимает chapter+block+generation, layouts bounded 8. Restore baseline подавляет
перезапись исходного inner anchor до фактического scroll. Paged viewport фиксирует strips,
передаёт реальный Compose measurement environment и показывает только page number
актуального LayoutKey. Geometry/chrome не дублируются в NavHost.
14 targeted Android passed +1 opt-in skipped, полный Android 35 passed +6 opt-in skipped,
0 failures API 37; 96 debug +96 release unit tests, detekt/lint/check/build GREEN.
Unit empty-chapter assertion обновлена для соседнего окна и усилена навигацией в обе
стороны с сохранением. [Evidence](evidence/T033-check.txt). Следующая T034.

T034: T022/T023 и SC-003 повторены на production services/screen. 10 Android tests
GREEN: EPUB/FB2 real gestures/modes/scale/rotation/native page geometry и Room/database
reopen без внешнего original, включая UTF-16 offset 15000 внутри >100000-char paragraph.
ViewModel native regression с injected durable writer подтверждает failed USER flush
(прежний выбор), rapid system font 150→200% (новый LayoutKey/тот же anchor без IO wait),
explicit navigation после ошибки и retry последней revision. Дополнительный saver test
проверяет late failed flush и последующую навигацию; старые deferred-success tests GREEN.
98 debug +98 release unit tests, check/build/lint/detekt/test APK GREEN.
[Android](evidence/T034-android.xml), [checks](evidence/T034-check.txt). Следующая T035.

T035 в работе: debug instrumentation переведён на настоящий ReaderViewModel/ReaderScreen,
Room/source open, production LayoutKey/viewport и PageIndexStore. Debug observer только
отмечает точный prefix и draw текущей страницы; следующий frame callback — диагностическая
верхняя граница, не физический first-presented. Подготовка fixtures/Room до таймера,
source/page cache state проверяется перед каждым повтором. Cold/warm и cancellation
harness собраны с detekt; пробная серия EPUB cold: 5 повторов, max 1753.093 ms,
отмена прежнего LayoutKey 4.544 ms. Задача ещё не завершена, следующая полная серия T035.
По последнему указанию пользователя остановиться после T036; T037–T065 оставить на завтра.

T035 промежуточная оптимизация проверена: block cache использует value equality LayoutKey
без SHA-256/toString на каждом блоке; PageIndex directory/hash вычисляется один раз на owner.
PAGINATED не удерживает отдельное окно вертикальных блоков; при возврате в VERTICAL окно
загружается с актуального anchor. Native ViewModel regression подтверждает пустое window
в pages и восстановление ограниченного окна после retry. 98 debug +98 release unit tests,
check/build/lint/detekt и оба APK GREEN. [Checks](evidence/T035-check.txt).
Исходная полная series ещё идёт; установленный baseline APK не заменялся сборкой оптимизации.
После неё выполнить новую полную series оптимизированного APK, затем закрыть T035 и T036.

T035 завершена: production baseline и повтор после оптимизации — каждый 36×5=180
измерений; все raw значения, min/median/max, cache flags, SHA/profileId, passes/memory
сохранены раздельно. [Production отчёт](performance-results.md). Baseline max 7818.371 ms;
повтор max 55169.996 ms с сохранёнными FB2 выбросами. Причина последних задержек
не установлена, улучшение общей latency не заявляется. Prefix/native draw/anchors
корректны; cancellation 4.994 ms без obsolete/.part. 98 debug +98 release unit tests,
check/build/lint/detekt GREEN. SC-004 ≤1000 ms НЕ ПРОЙДЕН, физический стенд отсутствует
(NOT_VERIFIED_DEVICE); это release blocker, разрешённый отрицательный итог T035,
а не успешная аппаратная приёмка. Следующая T036, затем остановка по указанию пользователя.
