# Исследование: Офлайн-читалка книг

## Импорт и локальная копия

**Решение**: Использовать `ActivityResultContracts.OpenDocument`, читать выбранный `Uri` и после
валидации копировать книгу во внутреннее app-specific storage.

**Обоснование**: `OpenDocument` возвращает `Uri`; собственная копия сохраняет чтение после удаления
или переноса оригинала и не требует постоянного доступа к нему.

**Альтернативы**: Хранить только `Uri` — отклонено, так как нарушает FR-003. Запрашивать широкие
storage permissions — отклонено, так как они не нужны File Picker.

## Данные и настройки

**Решение**: Book, Chapter и ReadingPosition хранить в Room; тему и размер текста — в DataStore
Preferences.

**Обоснование**: Room даёт типобезопасный слой SQLite и migration path для связных сущностей;
DataStore подходит для небольших глобальных preferences.

**Альтернативы**: Один DataStore для всего — отклонено для библиотеки и глав. Прямой SQLite —
отклонён, так как Room проверяет запросы при компиляции.

## EPUB, FB2 и модель содержимого

**Решение**: Использовать Readium Kotlin Toolkit для открытия EPUB, metadata, cover и структуры
publication. FB2 читать потоковым `XmlPullParser`, учитывая XML-кодировку. Оба формата
преобразовывать в общий Document/Chapter/Block model для Compose UI.

**Обоснование**: Readium поддерживает EPUB 2/3 и предоставляет publication/locator models;
потоковый parser ограничивает memory для FB2. Общая модель даёт одинаковые правила глав и позиции.

**Альтернативы**: WebView renderer — отклонён, поскольку UI обязан быть Compose. Полная загрузка
книги в memory — отклонена по FR-016. DRM — отклонён по уточнению спецификации.

## Прогресс и производительность

**Решение**: Сохранять `bookId`, `chapterIndex`, `blockIndex`, `characterOffset` и
`progressPercent`; делать debounced запись при прокрутке и принудительную — при смене главы, уходе
с экрана и `ProcessLifecycleOwner.ON_STOP`. Библиотека и reader используют `LazyColumn`; reader
держит текущую главу и небольшой соседний набор блоков.

**Обоснование**: Логическая позиция устойчива к layout, в отличие от пиксельного offset. Lazy lists
создают элементы только для viewport; тяжёлый parsing и копирование выполняются вне main thread.

**Альтернативы**: Только scroll offset или процент — отклонены: не выполняют FR-009 и FR-013.

## Источники

- [ActivityResultContracts.OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument)
- [Room](https://developer.android.com/training/data-storage/room/accessing-data)
- [Preferences DataStore](https://developer.android.com/reference/kotlin/androidx/datastore/preferences/core/Preferences)
- [Lazy lists в Compose](https://developer.android.com/develop/ui/compose/lists)
- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)
- [ProcessLifecycleOwner](https://developer.android.google.cn/reference/androidx/lifecycle/ProcessLifecycleOwner)
