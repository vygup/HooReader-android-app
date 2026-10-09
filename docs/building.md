# Сборка HooReader

## Второй RC: фактические проверки

Локальный RC 2.0.0 (versionCode 2) собирается `python3 scripts/build-release.py`.
APK/AAB и SHA256SUMS находятся в `app/build/release/2.0.0/`; используется существующая
release-подпись. [Выпуск и временный стенд](releasing.md), [notes](releases/2.0.0.md).
Подписанное обновление проверяется отдельно `python3 scripts/verify-reader-upgrade.py`:
требуются локальные APK 1.0.0/2.0.0, собранный внешний release-smoke runner и один
`-read-only` emulator. Между установкой v1 и обновлением v2 библиотека не очищается.

На 2026-10-09 прошли unit/static/debug builds и по 48 UI tests на API 26 и API 37;
на каждом стенде 17 специальных opt-in tests в общем прогоне skipped. После
исправлений тестового ввода и screenshot повторены четыре затронутых теста
на API 37 — без ошибок. Отдельно пройдены:

- `scripts/verify-reader-font-scale.sh`: 96 сочетаний, 576 screenshots.
- `scripts/verify-reader-process-death.sh`: четыре сочетания режима/защиты выхода,
  длинный UTF-16 anchor и пересоздание отсутствующего производного кэша.
- `scripts/verify-import-corpus.sh /tmp/corpus.json`: 26 файлов, оба режима офлайн.
- `python3 scripts/verify-release.py`: внешний UI smoke подписанного release APK.

`scripts/measure-reader-performance.sh /tmp/performance.json` требует физического
стенда; без него сохраняет NOT_VERIFIED_DEVICE. Для проверки harness доступно
`--allow-emulator --ui-only /tmp/performance-diagnostic.json`: пять повторов каждого
UI-сценария, отдельный profileId. Полная серия 20 MB текущего RC не проведена;
прежний непройденный порог ≤1 секунды остаётся release blocker. T062/SC-007 пройдена
по подтверждению пользователя; индивидуальные наблюдения запрошены для отчёта.

Для API 26 установлен официальный Google APIs ARM64 image и создан отдельный
AVD `HooReader_API26` в стандартном каталоге `~/.android/avd/`. Воспроизведение:

```sh
"$ANDROID_HOME/emulator/emulator" -avd HooReader_API26 -no-snapshot -no-window -no-audio
# После загрузки, при одном подключённом тестовом стенде:
ANDROID_SERIAL=emulator-5554 ./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

На API 26–27 screenshot диалога сохраняется через UiAutomation, поскольку Compose
captureToImage не поддерживает эти окна до API 28. Все проверки диалога выполняются;
скриншот не заменяет их. [Итоги T063](../specs/002-reader-appearance/validation-results.md).

[Все результаты и ограничения](../specs/002-reader-appearance/quickstart-results.md) ·
[Машиночитаемая матрица](../specs/002-reader-appearance/evidence/validation-summary.json).
Старые разделы ниже сохраняют предыдущие этапы и не заменяют текущую приёмку.

## Окружение

- JDK 17–21 (для проверки проекта используется JDK 21).
- Android SDK: платформа API 36 и Build Tools 35.0.0.
- Android Studio либо командная строка с `ANDROID_HOME`, указывающим на SDK.
  Вместо переменной можно задать `sdk.dir` в локальном `local.properties`.

Gradle 8.14.3 загружается через Wrapper; SHA-256 дистрибутива зафиксирован в
`gradle/wrapper/gradle-wrapper.properties`. Устанавливать Gradle отдельно не требуется.
Сборка впервые требует доступа к Google Maven, Maven Central и Gradle Plugin Portal.

```sh
./gradlew help
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:testDebugUnitTest
```

При запуске из терминала `JAVA_HOME` должен указывать на совместимый JDK. В Android Studio
тот же JDK необходимо выбрать в настройках Gradle. Java/Kotlin bytecode приложения имеет
целевую версию 17; минимальный Android API — 26.

## Версии инструментов

Android Gradle Plugin 8.11.1 и Kotlin 2.1.21 закреплены в `build.gradle.kts`.
Compose compiler использует ту же версию, что и Kotlin.

Источники совместимости:

- [Android Gradle Plugin 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes).
- [Readium Kotlin Toolkit 3.1.2](https://github.com/readium/kotlin-toolkit/tree/3.1.2).
- [Gradle Wrapper](https://docs.gradle.org/8.14.3/userguide/gradle_wrapper.html).

## Зависимости и тестирование

Модуль подключает Compose BOM 2025.08.01, Navigation Compose 2.9.3, Lifecycle 2.9.3,
Room 2.7.2, DataStore Preferences 1.1.7, Coroutines 1.10.2 и Readium 3.1.2.
Для EPUB подключены только `readium-shared` и `readium-streamer`; UI использует Compose.
Для Readium включён core library desugaring с `desugar_jdk_libs` 2.1.5.

Room обрабатывается через KSP 2.1.21-2.0.1. Схемы будущей базы экспортируются в
`app/schemas/`; их нужно сохранять в Git для проверки миграций.

JUnit, coroutine-test, Room testing и Robolectric подключены для локальных тестов.
AndroidJUnitRunner, Espresso и Compose UI testing подключены для instrumentation tests.
Для запуска последних требуется устройство или эмулятор:

```sh
./gradlew :app:connectedDebugAndroidTest
```

В фазе 2 добавлены локальные тесты моделей, файлового хранилища, DataStore, загрузчика блоков
и Room-репозитория, а также UI-тесты навигации. Команда `assembleDebugAndroidTest` проверяет
сборку тестового APK; `connectedDebugAndroidTest` запускает UI-тесты на устройстве.

## Форматирование и статический анализ

Detekt 1.23.8 проверяет Kotlin-код приложения, локальных и instrumentation tests.
Правила `detekt-formatting` используют ktlint с Android style. Имена Compose-функций
с аннотацией `@Composable` могут начинаться с заглавной буквы.
Сгенерированный код не входит в список исходников для основной задачи `detekt`.
Настройки хранятся в `config/detekt/detekt.yml` и `.editorconfig`.

```sh
./gradlew :app:detekt
./gradlew :app:detekt --auto-correct
./gradlew :app:check
```

Обычная проверка не исправляет файлы автоматически; исправление выполняется только при
передаче `--auto-correct`. Задача `check` включает detekt и Android Lint; нарушения
останавливают проверку. Отчёты находятся в `app/build/reports/`.
При автоисправлении detekt может вернуть ошибку для уже исправленных нарушений:
после него нужно повторить обычную команду `detekt`.

- [Detekt Gradle Plugin](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/).

## Проверка фазы 1

На установленном Android SDK с API 36 и Build Tools 35.0.0, с JDK 21 выполнена команда:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest
```

Все задачи завершились успешно. Detekt не обнаружил нарушений. Проверено обнаружение
ошибок форматирования и их исправление на временном Kotlin-файле; после проверки файл удалён.
Android Lint завершился без ошибок, с 27 предупреждениями: доступны более новые версии
зависимостей и Gradle, отсутствует launcher icon, не заданы отдельные правила переноса данных
для Android 12+. В manifest резервное копирование отключено через `allowBackup="false"`.

Локальные тесты имеют статус `NO-SOURCE`: тестовые сценарии запланированы в следующих фазах.
Запуск на устройстве не проверен, поскольку подключённого устройства или запущенного эмулятора
не было. Эта проверка подтверждает настройку проекта, а не готовность функций чтения.

## Проверка фазы 2

Выполнены T005–T012: доменные модели, Room со схемой версии 1, app-specific файлы,
BookRepository, DataStore, контракт парсера с ограниченной загрузкой блоков, root navigation,
синтетические EPUB/FB2 и тесты репозитория.

На JDK 21, SDK API 36 и подключённом CPH2723 с Android 16 успешно выполнено:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

- 19 локальных тестов прошли в каждом из вариантов debug и release.
- Проверены уникальность хеша, каскадное удаление, ограничения позиции на уровне SQL,
  откат транзакции, сохранение после повторного открытия базы и защита от запоздалой записи позиции.
- Проверены сохранение настроек после повторного открытия DataStore, безопасное копирование
  файлов, отсутствие удаления оригинала и ограниченная загрузка блоков только выбранной главы.
- 2 UI-теста навигации прошли на телефоне: стартовая библиотека, передача bookId и возврат назад.
- Detekt не обнаружил нарушений; Android Lint завершился без ошибок с 27 предупреждениями.
- Структура ZIP/XML тестовых EPUB, кодировки FB2 и целостность встроенного PNG проверены отдельно.

При повторном запуске с выключенным экраном телефона UI-тесты не нашли активную Compose hierarchy;
после включения экрана оба теста прошли. Перед запуском instrumentation tests экран должен быть
включён, устройство разблокировано. Подключённые тесты могут удалить тестируемый APK при очистке;
для восстановления приложения используется `./gradlew :app:installDebug`.

Фаза 2 создаёт основание приложения: начальный экран теперь показывает пустую библиотеку.
На контрольной точке фазы 2 импорт EPUB/FB2, чтение и восстановление позиции через UI ещё не были реализованы.
Полные acceptance-сценарии из quickstart пока не выполнялись.

## Проверка фазы 3

На 2026-10-01 завершены T013–T021: импорт EPUB/FB2 через `OpenDocument`, собственная копия книги,
проверка DRM, SHA-256 для повторного импорта, текстовый reader и восстановление логической позиции.
Сохранение объединяет изменения за 400 мс и выполняется сразу при смене главы, возврате в библиотеку,
остановке экрана и `ProcessLifecycleOwner.ON_STOP`. Ошибку сохранения можно повторить через UI.
Reader держит окно не более 128 блоков; парсеры читают текст потоково, без загрузки всей книги в память.

Выполнены:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
ADB="$ANDROID_HOME/platform-tools/adb" scripts/verify-reader-process-death.sh
```

Результаты:

- По 36 unit-тестов в debug и release, без ошибок. Покрыты оба parser, Windows-1251, пустой и
  повреждённый файл, DRM, подмена расширения, незакрытый XML и XML entities, дубликаты, очистка
  частичного копирования, логические координаты, ограниченное окно блоков и сохранение позиции.
- Семь обычных instrumentation/UI-тестов прошли на Pixel_10 AVD, Android 17, API 37. Проверены
  навигация, импорт EPUB/FB2, повторный импорт, ошибка с последующим успешным импортом, пересоздание
  Activity и восстановление позиции после пересоздания reader/database и удаления оригинала.
- UI-тест импорта подставляет результат `OpenDocument` через Espresso Intents. Сам файл читается
  настоящим `ContentResolver` из тестового provider, который не экспортируется и входит только в
  debug APK. Внешний интерфейс приложения выбора файлов этим тестом не автоматизируется.
- Две фазы `ProcessDeathAcceptanceTest` прошли отдельно через скрипт: импорт обоих форматов и
  сохранение позиции; затем `am force-stop`, авиарежим и восстановление в новом PID. Проверены
  `chapterIndex = 1`, `blockIndex = 1`, `characterOffset = 4`, доступность текста и отсутствие
  оригиналов. Скрипт возвращает прежнее состояние авиарежима и удаляет созданные записи книг.
  В обычном `connectedDebugAndroidTest` эти два метода намеренно пропускаются: им нужен запуск
  в разных процессах.
- Detekt прошёл без нарушений. Android Lint: 0 ошибок, 24 предупреждения о версиях зависимостей,
  launcher icon и настройках backup. Debug APK и instrumentation APK собраны.

AndroidX Test обновлён до core/runner 1.7.0, JUnit extension 1.3.0 и Espresso 3.7.0:
эта версия исправляет несовместимый вызов `InputManager.getInstance` на новых Android.
Источник: [release notes Espresso 3.7.0](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

Граница фазы: библиотека пока содержит простой список названий и авторов; полноценные карточки,
обложки, прогресс и удаление относятся к фазе 4. Reader отображает базовый текст, заголовки и
безопасные заглушки; расширенные стили, изображения и содержание относятся к фазе 5. Настройки
оформления относятся к фазе 6. Пороговые замеры производительности и согласованный test corpus
остаются задачами фазы 7; текущие результаты не подтверждают SC-003–SC-005.

## Проверка фазы 4

На 2026-10-01 завершены T022–T027: библиотека с обложками и заглушками, запасными метаданными,
примерным прогрессом, отметкой «Прочитано» от 98%, сообщением о повторном импорте и подтверждением
удаления. Для каждой задачи создан отдельный commit.

`LibraryRepository` наблюдает книги и позиции одним Room-запросом с `LEFT JOIN`: изменения
прогресса появляются без повторного открытия библиотеки, а непрочитанная книга показывает 0%.
Запасные название файла и «Неизвестный автор» сохраняются при импорте; недоступная или нечитаемая
обложка отображается заглушкой. `LazyColumn` использует устойчивые ключи книг. Обложки декодируются
на `Dispatchers.IO` с уменьшением размера до 512 пикселей по каждой стороне.

Удаление из UI выполняется после подтверждения. Диалог сохраняется при пересоздании Activity;
отмена оставляет запись и файлы. Удаляется только app-specific каталог выбранной книги, затем
Room-запись с каскадным удалением глав и позиции. Если удаление файлов завершается ошибкой,
запись остаётся для повторной попытки. Исходный URI при удалении не используется.

Выполнены:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:detekt :app:connectedDebugAndroidTest
```

Результаты:

- По 46 unit-тестов в debug и release, без ошибок и пропусков. Новые тесты покрывают запасные
  метаданные EPUB/FB2, повторный импорт под другим именем, сохранение исходной записи и позиции,
  недоступную обложку, наблюдение за прогрессом, порог 98%, состояния библиотеки и удаление.
- 13 instrumentation/UI-тестов прошли на Pixel_10 AVD, Android 17, API 37. Среди новых сценариев:
  карточка с заглушкой и прогрессом, порог «Прочитано», пустая библиотека, прокрутка к сотой книге,
  повторный импорт без потери позиции, отмена удаления, пересоздание диалога и подтверждённое
  удаление локальной книги вместе с главами и позицией. Исходный файл проверен непосредственно
  в cache-каталоге тестового provider: его байты после удаления книги не изменились.
- Два метода `ProcessDeathAcceptanceTest` штатно пропущены в общем запуске: им нужны отдельные
  процессы и скрипт `verify-reader-process-death.sh`. Этот сценарий проверен в фазе 3;
  в фазе 4 скрипт повторно не запускался.
- Detekt прошёл без нарушений. Android Lint: 0 ошибок и 23 предупреждения о версиях зависимостей,
  launcher icon и настройках backup. Debug APK и instrumentation APK собраны.
- Первый запуск нового сквозного теста выявил отсутствие `missing-metadata.fb2` в разрешённых
  fixtures debug-provider. После добавления fixture полный набор instrumentation-тестов прошёл.

Библиотека из 100 карточек проверена UI-тестом; замеры кадров и времени открытия больших книг
остаются задачами фазы 7. Фазы 5–7 не выполнялись, reviewer-owned чеклисты не изменялись.

## Проверка фазы 5

На 2026-10-01 завершены T028–T034: потоковое преобразование структурированного EPUB XHTML и FB2,
вложенные bold/italic, заголовки, базовые списки, локальные изображения и содержание с переходом
к началу выбранной главы. Активные XHTML-элементы пропускаются, внешние ссылки на изображения
не открываются. Неизвестные текстовые элементы сохраняют читаемый текст; повреждённый XHTML
и недоступное изображение получают безопасную заглушку.

Reader восстанавливает ближайшие существующие главу и блок, ограничивает character offset длиной
текста и пересоздаёт viewport от логического блока при изменении ориентации или масштаба текста.
Начальное событие viewport не перезаписывает сохранённое смещение внутри того же блока. Парсеры
удерживают текущий блок, reader — окно до 128 блоков. Растровые изображения декодируются вне
main thread с ограничением разрешения до 1024 пикселей по каждой стороне; SVG остаётся заглушкой.

На JDK 21 и Pixel_10 AVD, Android 17, выполнены:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
ADB="$ANDROID_HOME/platform-tools/adb" scripts/verify-reader-process-death.sh
```

Результаты:

- По 52 unit-теста в debug и release, без ошибок. Новые сценарии проверяют координаты и
  частичную загрузку структурированных блоков, вложенные стили, списки, inline images,
  исключение активного XHTML, внешних и выходящих за архив ссылок, повреждённый фрагмент,
  вложенные FB2 section и ближайшую доступную логическую позицию.
- 19 обычных instrumentation/UI-тестов прошли на эмуляторе. Шесть новых проверяют переходы
  по содержанию обоих форматов, сохранение блока после изменения font scale, настоящего поворота
  и пересоздания Activity, перекрывающиеся bold/italic spans, маркер списка, декодирование PNG
  и продолжение чтения после повреждённых или нечитаемых изображений.
- Два метода ProcessDeathAcceptanceTest штатно пропущены в общем запуске, затем оба успешно
  выполнены отдельно через скрипт: EPUB/FB2 восстанавливают главу, блок и character offset
  после `am force-stop` в новом процессе и в авиарежиме. Скрипт вернул прежнее состояние авиарежима.
- Detekt прошёл без нарушений; Android Lint: 0 ошибок, 25 предупреждений. Debug APK и тестовый
  APK собраны. JDK 25 из текущей Android Studio не подходит этому Gradle; проверки выполнены
  на установленном JDK 21.

Задачи T028–T034 отмечены в tasks.md и оформлены отдельными commits. Фазы 6–7 не выполнялись;
экран сохранения настроек оформления, замеры производительности и согласованный test corpus
остаются в своих фазах. Reviewer-owned чеклисты не изменялись.


## Проверка фазы 7

На 2026-10-01 выполнены T040–T045 отдельными commits: security/UX review, расширенные
quickstart-сценарии, воспроизводимые performance measurements, фиксированный corpus v1 и README.
Reviewer-owned чеклисты не изменены; продолжение разрешено пользователем без уточнений.

Полный `check`/сборка/debug instrumentation прошли на Pixel_10 AVD, API 37. Итог UI XML:
25 tests, 22 passed, 3 штатно skipped (2 process-death phases и opt-in performance),
0 failures/errors. Все специальные режимы проверены отдельно. Локальные tests:
по 56 debug/release, без failures/errors/skips. Отдельный process-death acceptance подтвердил
позиции EPUB/FB2, тёмную тему/150% текста в новом PID при авиарежиме. Настоящий системный
File Picker проверен для EPUB; остальные picker-сценарии используют Espresso Intents.

Корпус: 17/17 допустимых файлов, 34/34 главы, 7/7 ожидаемых отказов. Opening 20 MB FB2:
1183–1332 ms. FrameMetrics: 0% кадров ≤16,7 ms, порог SC-005 не пройден на эмуляторе;
SC-004/005 на физическом устройстве среднего класса не подтверждены.

Подробности и исходные данные:
[quickstart](../specs/001-offline-book-reader/quickstart-results.md),
[performance](../specs/001-offline-book-reader/performance-results.md),
[corpus](../specs/001-offline-book-reader/import-corpus-results.md),
[security](../specs/001-offline-book-reader/security-review.md),
[UX](../specs/001-offline-book-reader/ux-review.md).
Результаты review содержат открытые замечания и не объявляют release acceptance пройденной.
