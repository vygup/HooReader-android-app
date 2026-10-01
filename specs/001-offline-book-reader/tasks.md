# Задачи: Офлайн-читалка книг

**Ввод**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [UI-контракт](contracts/reader-ui.md), [quickstart.md](quickstart.md).

**Правило commit**: Каждая задача ниже должна быть выполнена отдельным commit без несвязанных
изменений.

## Формат

- `[P]` — задача может выполняться параллельно после выполнения её зависимостей.
- `[US#]` — задача относится к соответствующей User Story.
- Каждая строка задачи содержит checkbox, ID, необходимые labels и точный путь.

## Фаза 1: Настройка

**Цель**: создать воспроизводимый Android-проект на Kotlin и Compose.

- [X] T001 Создать Gradle root-конфигурацию и модуль приложения в `settings.gradle.kts`, `build.gradle.kts` и `app/build.gradle.kts`.
- [X] T002 Настроить Android manifest, `minSdk 26` и базовую тему в `app/src/main/AndroidManifest.xml` и `app/src/main/java/com/hooreader/ui/theme/Theme.kt`.
- [X] T003 [P] Подключить Compose, Room, DataStore, Lifecycle, Coroutines, Readium и тестовые зависимости в `app/build.gradle.kts`.
- [X] T004 Настроить Kotlin formatting и static analysis в `config/detekt/detekt.yml` и `app/build.gradle.kts`.

## Фаза 2: Основание

**Цель**: создать общие модели, persistent storage и навигацию до реализации User Story.

- [X] T005 Создать доменные модели `Book`, `Chapter`, `ContentBlock`, `ReadingPosition` и `ReaderPreferences` в `app/src/main/java/com/hooreader/domain/model/`.
- [X] T006 Создать Room entities, DAO и database для Book, Chapter и ReadingPosition в `app/src/main/java/com/hooreader/data/local/` с ограничениями: `contentHash` уникален, `bookId` позиции уникален, координаты позиции неотрицательны, `progressPercent` в диапазоне 0–100.
- [X] T007 Создать app-specific file storage и `BookRepository` в `app/src/main/java/com/hooreader/data/repository/BookRepository.kt`.
- [X] T008 [P] Создать DataStore-backed `ReaderPreferencesRepository` в `app/src/main/java/com/hooreader/data/local/ReaderPreferencesRepository.kt`.
- [X] T009 Создать общий контракт parser и lazy chapter/block loading в `app/src/main/java/com/hooreader/data/import/BookParser.kt`.
- [X] T010 Создать root navigation и маршруты Library/Reader в `app/src/main/java/com/hooreader/navigation/HooReaderNavHost.kt`.
- [X] T011 [P] Добавить тестовые EPUB, FB2, DRM-marker, повреждённые и пустые fixtures в `app/src/androidTest/assets/books/`.
- [X] T012 [P] Добавить Room repository tests для уникального `contentHash`, каскадного удаления и координат позиции в `app/src/test/java/com/hooreader/data/repository/BookRepositoryTest.kt`.

**Контрольная точка**: общие данные, storage и навигация готовы; User Story можно реализовывать.

## Фаза 3: User Story 1 — Импорт и продолжение чтения книги (P1) 🎯 MVP

**Цель**: импортировать EPUB/FB2, читать офлайн и восстановить позицию в пределах одного абзаца.

**Независимый тест**: импортировать допустимую книгу, остановиться в известном абзаце, завершить
process, открыть книгу в авиарежиме и восстановить позицию.

- [X] T013 [P] [US1] Написать unit tests валидации формата, пустого файла, DRM и ошибок parsing в `app/src/test/java/com/hooreader/data/import/BookImportValidatorTest.kt`.
- [X] T014 [P] [US1] Написать instrumentation test импорта, process recreation и восстановления позиции в `app/src/androidTest/java/com/hooreader/reader/ReadingPositionRestoreTest.kt`.
- [X] T015 [US1] Реализовать EPUB importer на Readium с извлечением metadata, cover и глав в `app/src/main/java/com/hooreader/data/import/EpubBookParser.kt`.
- [X] T016 [US1] Реализовать потоковый FB2 importer с учётом XML-кодировки в `app/src/main/java/com/hooreader/data/import/Fb2BookParser.kt`.
- [X] T017 [US1] Реализовать проверку DRM, копирование app-local файла, SHA-256 duplicate identity и rollback неполного импорта в `app/src/main/java/com/hooreader/data/import/BookImportService.kt`.
- [X] T018 [US1] Реализовать `ReaderViewModel` с logical position `chapterIndex`, `blockIndex`, `characterOffset` и `progressPercent` в `app/src/main/java/com/hooreader/ui/reader/ReaderViewModel.kt`.
- [X] T019 [US1] Реализовать вертикальный reader для текущей главы и безопасные fallback blocks в `app/src/main/java/com/hooreader/ui/reader/ReaderScreen.kt`.
- [X] T020 [US1] Добавить debounced сохранение позиции, смену главы и обработку `ProcessLifecycleOwner.ON_STOP` в `app/src/main/java/com/hooreader/ui/reader/ReadingPositionSaver.kt`.
- [X] T021 [US1] Связать импорт, открытие ReaderScreen и recoverable import errors в `app/src/main/java/com/hooreader/ui/library/ImportBookLauncher.kt` и `app/src/main/java/com/hooreader/navigation/HooReaderNavHost.kt`.

**Контрольная точка**: EPUB/FB2 без DRM импортируются, сохраняются локально, читаются офлайн и
возвращаются к логической позиции.

## Фаза 4: User Story 2 — Просмотр и управление локальной библиотекой (P1)

**Цель**: показать библиотеку с fallback metadata и безопасно управлять записями.

**Независимый тест**: импортировать книги с неполными metadata, создать 100 записей, повторно
импортировать книгу и удалить одну запись без удаления исходного файла.

- [X] T022 [P] [US2] Написать repository tests fallback title/author/cover и duplicate result в `app/src/test/java/com/hooreader/data/repository/LibraryRepositoryTest.kt`.
- [X] T023 [P] [US2] Написать Compose UI test карточки с progress и placeholder в `app/src/androidTest/java/com/hooreader/library/LibraryScreenTest.kt`.
- [X] T024 [US2] Реализовать query библиотеки, fallback metadata, «Прочитано» от 98% и удаление только app-local данных в `app/src/main/java/com/hooreader/data/repository/LibraryRepository.kt`.
- [X] T025 [US2] Реализовать `LibraryViewModel` для состояний `empty`, `content`, `importing` и `error` в `app/src/main/java/com/hooreader/ui/library/LibraryViewModel.kt`.
- [X] T026 [US2] Реализовать lazy library, BookCard и empty state в `app/src/main/java/com/hooreader/ui/library/LibraryScreen.kt` и `app/src/main/java/com/hooreader/ui/library/BookCard.kt`.
- [X] T027 [US2] Реализовать сообщение о повторном импорте и диалог подтверждения удаления в `app/src/main/java/com/hooreader/ui/library/LibraryActions.kt`.

**Контрольная точка**: библиотека удобна при 100 книгах, показывает fallback/progress и не создаёт
незаметных дубликатов.

## Фаза 5: User Story 3 — Навигация и чтение структурированного содержимого (P2)

**Цель**: читать структурированный текст, переходить по содержанию и сохранять позицию при изменении layout.

**Независимый тест**: открыть EPUB и FB2 с главами, стилями, списками и изображениями; перейти по
содержанию; изменить ориентацию и размер текста; остаться у того же блока.

- [X] T028 [P] [US3] Написать parser tests для headings, bold/italic, lists, images и повреждённого block в `app/src/test/java/com/hooreader/data/import/StructuredContentParserTest.kt`.
- [X] T029 [P] [US3] Написать Compose UI test перехода по содержанию и сохранения позиции при layout change в `app/src/androidTest/java/com/hooreader/reader/ReaderNavigationTest.kt`.
- [X] T030 [US3] Реализовать преобразование EPUB XHTML в ContentBlock без выполнения активного содержимого в `app/src/main/java/com/hooreader/data/import/EpubContentMapper.kt`.
- [X] T031 [US3] Реализовать преобразование FB2 section/paragraph/style/image в ContentBlock в `app/src/main/java/com/hooreader/data/import/Fb2ContentMapper.kt`.
- [X] T032 [US3] Реализовать список глав и переход к началу главы в `app/src/main/java/com/hooreader/ui/reader/TableOfContentsSheet.kt`.
- [X] T033 [US3] Реализовать Compose blocks для paragraph, heading, bold/italic, list, image и fallback в `app/src/main/java/com/hooreader/ui/reader/ContentBlockRenderer.kt`.
- [X] T034 [US3] Реализовать восстановление ближайшего существующего блока после поворота или смены font scale в `app/src/main/java/com/hooreader/ui/reader/ReaderPositionResolver.kt`.

**Контрольная точка**: chapter navigation и поддерживаемая разметка работают, а повреждённый content
не закрывает читалку.

## Фаза 6: User Story 4 — Настройка внешнего вида (P3)

**Цель**: сохранить светлую/тёмную тему и размер текста без сброса позиции.

**Независимый тест**: изменить тему и font scale, перезапустить app и открыть книгу около прежнего
логического блока.

- [X] T035 [P] [US4] Написать DataStore tests сохранения темы и font scale в `app/src/test/java/com/hooreader/data/local/ReaderPreferencesRepositoryTest.kt`.
- [ ] T036 [P] [US4] Написать Compose instrumentation test сохранения темы и позиции в `app/src/androidTest/java/com/hooreader/settings/ReaderSettingsTest.kt`.
- [ ] T037 [US4] Реализовать `ReaderSettingsViewModel` и ограниченный font scale в `app/src/main/java/com/hooreader/ui/settings/ReaderSettingsViewModel.kt`.
- [ ] T038 [US4] Реализовать chooser темы и размера текста в `app/src/main/java/com/hooreader/ui/settings/ReaderSettingsSheet.kt`.
- [ ] T039 [US4] Подключить DataStore theme к app theme в `app/src/main/java/com/hooreader/ui/theme/HooReaderTheme.kt`.

**Контрольная точка**: выбранные тема и размер текста сохраняются, а логическая позиция не сбрасывается.

## Фаза 7: Завершение и сквозные проверки

- [ ] T040 Выполнить security review импорта, app-specific storage, logs и permissions по `specs/001-offline-book-reader/checklists/security.md`.
- [ ] T041 Выполнить UX review требований и UI состояний по `specs/001-offline-book-reader/checklists/ux.md`.
- [ ] T042 Выполнить все сценарии `specs/001-offline-book-reader/quickstart.md` на Android emulator или устройстве.
- [ ] T043 Измерить открытие 20 MB книги и долю кадров до 16,7 ms при прокрутке текста и библиотеки из 100 книг; зафиксировать результаты в `specs/001-offline-book-reader/performance-results.md`.
- [ ] T044 Сформировать согласованный EPUB/FB2 test corpus, выполнить импорт каждого файла, рассчитать долю успешных открытий и записать результат в `specs/001-offline-book-reader/import-corpus-results.md`.
- [ ] T045 Обновить `README.md` инструкциями сборки, офлайн-ограничениями, форматами EPUB/FB2 без DRM и запретом OPDS в MVP.

## Зависимости и порядок выполнения

`Настройка → Основание → US1 → US2 → US3 → US4 → Завершение`.

- US1 зависит от всей фазы «Основание».
- US2 использует import/storage из US1 и может начаться после T017; UI library независим от reader renderer.
- US3 зависит от common parser contract и ReaderScreen из US1.
- US4 зависит от ReaderScreen и DataStore foundation, но не от содержания глав.
- Финальные проверки зависят от всех нужных User Story.

## Возможности параллельной работы

- После T001/T002: T003 и T004.
- После T005: T006, T008, T009 и T010; T011 можно выполнять отдельно.
- В US1: T013 и T014; EPUB и FB2 parser после контракта T009 можно вести параллельно.
- В US2, US3 и US4 тестовые задачи с `[P]` можно выполнять параллельно с задачами в других файлах.

## Стратегия реализации

1. Завершить T001–T012.
2. Завершить T013–T021 и проверить полный офлайн-сценарий US1 — это рекомендуемый минимальный MVP.
3. Добавить US2 для полноценной библиотеки, затем US3 и US4 по порядку приоритетов.
4. Выполнить T040–T045 перед релизом.

Все задачи имеют формат checkbox + ID + labels + путь и предназначены для отдельных commit.
