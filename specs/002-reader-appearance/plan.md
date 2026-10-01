# План реализации: Второй релиз — внешний вид и режимы чтения

**Ветка Git**: `release/v2.0.0` | **Дата**: 2026-10-02 | **Спецификация**: [spec.md](spec.md)
**Активная функция Spec Kit**: `002-reader-appearance`
**Ввод**: `specs/002-reader-appearance/spec.md`

setup-plan возвращает логический BRANCH активной функции; фактическая Git-ветка проверена
отдельно и остаётся release/v2.0.0. План описывает дизайн, а не выполненную реализацию.

## Краткое описание

Второй релиз освобождает экран для книги. Первое касание показывает управление, второе скрывает;
навигационный жест скрывает панели и никогда не открывает их. Настройки предлагают вертикальный
и горизонтальный постраничный режим, применяются без «Готово». Указатели соответствуют режиму,
выход защищён общим переключателем приложения.

Сохраняется Compose и локальное чтение EPUB/FB2. Постоянный BookViewport отделён от overlay
панелей; машина состояний задаёт обработку menus/Back/exit. Производный source spool обеспечивает
оконное чтение соседних глав и произвольный доступ. Реальное измерение строк создаёт дисковый
page index, а долговечная позиция остаётся логическими координатами Room v1.
Системный шрифт до 200% входит в приёмку; TalkBack отложен ответом B.

## Технический контекст

**Язык и инструменты**: Kotlin 2.1.21, JVM 17, JDK 17–21, AGP 8.11.1, Gradle 8.14.3,
KSP 2.1.21-2.0.1; версии взяты из текущих build.gradle.kts.

**Основные зависимости**: Compose BOM 2025.08.01, foundation/UI/Material3, activity-compose 1.10.1,
Navigation Compose 2.9.3, Lifecycle 2.9.3, Coroutines 1.10.2, Room 2.7.2,
DataStore Preferences 1.1.7, Readium shared/streamer 3.1.2. Обновление зависимостей не требуется.

**Хранение**: существующая Room v1 для библиотеки/позиций; singleton reader_preferences с
аддитивными ключами; app-private files для исходных книг и производных spool/page indices.
Изменение схемы Room и destructive migration не нужны.

**Проверки**: JUnit 4.13.2, coroutine-test, Robolectric 4.15.1, Room testing;
AndroidJUnitRunner/Espresso/Compose UI testing, Android Lint и Detekt 1.23.8.
Реальные gestures, process-death, offline и измерения на Android device/emulator.

**Целевая платформа**: Android, minSdk 26, compileSdk/targetSdk 36.
Совместимость проверяется на API 26, нелинейное увеличение системного шрифта — на API 34+.

**Тип проекта**: один Android app module, без сервера или сетевого API.

**Цели времени**: SC-004: панели ≤0,3 секунды, содержание/настройки ≤2 касаний,
применение параметра ≤1 секунды. Для нового paginated layout требуется готовая страница
с точным номером; холодный пересчёт входит в измерение. Замеры первого релиза не подтвердили
общую плавность на физическом устройстве: [baseline](../001-offline-book-reader/performance-results.md).

**Ограничения**: офлайн; совместимость библиотеки, позиций и прежних preferences;
нет полной коллекции текста/всех layout в RAM; исходные blockIndex не меняются.
Показ панелей не меняет размеры страниц, точный номер не подменяется оценкой.
Системный шрифт 100–200%, обе темы/ориентации, масштаб чтения 0,75–2,0.
OPDS, новые форматы и TalkBack вне второго релиза.

**Объём**: один локальный пользователь; библиотека 100 книг как существующий baseline;
EPUB/FB2 и нагрузочный корпус 20 MB. Читалка, её menus, settings приложения, навигация,
слой производного содержимого и проверки. В RAM — окно до 128 блоков, текущая и две соседние
страницы и bounded LRU. Наибольший одиночный абзац требует отдельного измерения памяти.

## Проверка конституции

Проверка перед фазой 0 выполнена по [конституции](../../.specify/memory/constitution.md):
функция относится к чтению, сохраняет текущую Android/Compose архитектуру и локальные данные,
не добавляет сеть или секреты. Нарушений не выявлено.

| Принцип / правило | До исследования | После проектирования |
|---|---|---|
| I. Нативный Android | Пройден | Пройден: navigation, insets, lifecycle |
| II. Kotlin и Compose | Пройден | Пройден: Box, LazyColumn, HorizontalPager, Compose measurement |
| III. Чтение и восстановление | Пройден | Пройден: прежняя Room позиция, settings и flush/retry |
| IV. OPDS после offline MVP | Пройден: вне scope | Пройден: сетевых каталогов/контрактов нет |
| V. Секреты вне source control | Пройден | Пройден: app-private files, новых credentials/permissions нет |
| VI. Русская документация | Пройден | Пройден: пояснения артефактов по-русски |
| Спецификация до изменения поведения | Пройден | Пройден: plan не изменяет приложение или решения пользователя |
| Один commit на task | Соблюдён для этой фазы | Требование сохраняется для будущего tasks/implementation |
| Автотесты критической логики/хранения | Запланированы | Reducer, пагинация, settings queue, совместимость |
| RC на Android device/emulator | Запланирован | Обязательная будущая приёмка по quickstart |

Последние два пункта — обязательства реализации, а не выполненные тесты.
Custom UX-чеклист остаётся reviewer-owned; команда plan не оценивает и не меняет его отметки.
Навык plan не требует завершённого custom checklist для создания дизайна.

## Структура проекта

### Документация этой функции

```text
specs/002-reader-appearance/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/reader-ui.md
└── checklists/
    ├── requirements.md
    └── ux.md
```

tasks.md создаёт следующая команда `$speckit-tasks`. Результаты и evidence добавляются
после реализации; в фазе дизайна доказательства исполнения не создаются.

### Исходный код — существующие и предполагаемые точки изменения

```text
app/src/main/java/com/hooreader/
├── domain/model/
│   ├── ReaderPreferences.kt       # дополнить readingMode
│   ├── ReadingMode.kt             # новый enum
│   ├── AppPreferences.kt          # новая настройка выхода
│   └── ReadingPosition.kt         # сохранить формат координат
├── data/
│   ├── import/                    # orderedBlocks; прежние индексы
│   ├── local/
│   │   ├── ReaderPreferencesRepository.kt
│   │   └── BookContentIndexStore.kt # новый spool/index
│   └── repository/
│       └── ReaderContentRepository.kt # новые окна соседних глав
├── navigation/
│   ├── HooReaderNavHost.kt
│   ├── HooReaderRoutes.kt
│   └── ReaderDependencies.kt
└── ui/
    ├── library/                   # переход к app settings
    ├── settings/
    │   ├── ReaderSettingsSheet.kt
    │   ├── ReaderSettingsViewModel.kt
    │   └── AppSettingsScreen.kt    # новый экран
    └── reader/
        ├── ReaderScreen.kt
        ├── ReaderViewModel.kt
        ├── ContentBlockRenderer.kt
        ├── ReaderPositionResolver.kt
        ├── ReaderLifecycle.kt
        ├── ReadingPositionSaver.kt
        ├── ReaderChromeState.kt    # новый reducer
        ├── ReaderControls.kt       # новый overlay
        ├── ReadingIndicators.kt    # постоянные полосы
        └── pagination/             # новые layout policy, paginator, PageIndex
app/src/test/java/com/hooreader/     # reducer, source/index, settings, позиции
app/src/androidTest/                # UI, layout, lifecycle, performance
scripts/                           # расширение специальных проверок
docs/releases/                     # описание второго релиза после реализации
```

**Выбор структуры**: развивать один app module. Чистые модели/reducer находятся вне composable,
UI geometry — в ui/reader, поток и индекс источника — в data. Точный список файлов задаст tasks.
Отдельный framework или замена существующей системы координат не требуются.

## Фаза 0 — исследование

Завершена в [research.md](research.md). Два исследовательских агента рассмотрели
пагинацию/парсеры/позицию и UI/жесты/settings; выводы объединены с анализом текущего кода
и первичными Android/AndroidX источниками.

Приняты: spool одним проходом, общая измеренная типографика, точные page boundaries,
LayoutKey и generation cancellation, bounded windows, restoreAnchor, chrome/exit state machine,
singleton DataStore и последовательная очередь pending writes.
Невыбранных проектных решений, требующих ответа пользователя, не осталось.

## Фаза 1 — дизайн и контракты

Созданы [data-model.md](data-model.md), [contracts/reader-ui.md](contracts/reader-ui.md)
и [quickstart.md](quickstart.md). Проверка конституции после дизайна пройдена без исключений.

### Последовательность будущей реализации

1. Ранний срез source stream/spool → text measurement/page slices → real draw.
   Проверить Unicode/стили, крупнейший абзац, холодную позднюю главу и бюджет SC-004.
2. Модели ReadingMode/AppPreferences, совместимые ключи и последовательная очередь DataStore;
   проверки старых данных, default/fallback, быстрых intentions и ошибок нескольких полей.
3. Окна соседних глав, shared layout policy, логический offset, PageIndex,
   generation cancellation, восстановление и HorizontalPager.
4. Постоянный viewport, указатели, chrome reducer/gestures и адаптивные menus без «Готово».
5. AppSettingsScreen и единый requestExit → confirm → flush → navigate.
6. Сквозные регрессии, API 26/34+, 200%, process-death, offline, benchmark и 5 участников.
7. Подготовка релиза: versionName 2.0.0, versionCode выше установленного первого релиза,
   release notes и доказательства RC. Публикация не входит в эту команду.

Это порядок для будущего tasks.md, без task IDs и выполнения реализации.

### Прослеживаемость

| Требования | Компоненты | Проверка |
|---|---|---|
| FR-001–005 | chrome reducer, gesture scope, overlay | SC-001, SC-002, viewport regression |
| FR-006–011 | preferences, source windows, paginator, restoreAnchor | SC-003, SC-005, fragment invariants |
| FR-012–015 | PageIndex, indicators, chapter/progress | SC-002, SC-007 |
| FR-016–018 | содержание, settings queue, dismiss/retry | SC-004, SC-005 |
| FR-019–022 | AppSettings, exit state, lifecycle saver | SC-005, SC-006, process-death |
| FR-023 | sp/density, measured strips, adaptive actions | SC-008, SC-003 |

### Конкретизация переходных состояний

Дизайн определяет положение открытых панелей, Opening/PreparingPages и pending errors
в рамках пользовательских целей. PreparingPages не является готовым Reading.
Выход доступен; прежний anchor сохраняется, новый выбор отменяет устаревший расчёт.
Временный экран и приблизительный номер не дают права считать SC-004 пройденным.

### Риски и критерии реализации

| Риск | Действие |
|---|---|
| Точный номер поздней главы на новом LayoutKey требует prefix measurement | cold эксперимент; готовая страница и правильный номер за ≤1 секунды |
| Повторный разбор FB2 | orderedBlocks/spool одним проходом; измерять source passes |
| Разные переносы измерителя и renderer | общая policy/TextLayoutResult; непрерывность и Unicode |
| Крупнейший абзац создаёт пик памяти | измерить; при необходимости bounded fragmentation с исходными offsets |
| Поздний image decode меняет границы | image bounds/fallback до публикации index |
| Drag становится tap | touch-slop state на весь gesture, включая boundary/cancel |
| Потеря pending settings | последовательные intentions и учёт полей |
| Шрифт обрезает меню | реальная шкала API 34+, адаптивные actions, SC-008 |
| Устаревший job теряет anchor | flush/restoreAnchor и generation/LayoutKey validation |

Порог холодной пагинации пока не подтверждён. При превышении нужна оптимизация до релиза;
согласованная спецификация не ослабляется. Gate планирования пройден: решения выбраны,
нарушений конституции нет. Gate исполнения/приёмки остаётся до фактических проверок.

## Учёт сложности

Нарушений конституции нет. Производные spool/page index необходимы для точной глобальной
нумерации и небольшого окна книги. Room schema и исходные координаты остаются совместимыми;
новый сетевой слой или отдельный reader framework не вводятся.
