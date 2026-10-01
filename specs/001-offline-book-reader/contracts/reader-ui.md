# UI-контракт: библиотека и читалка

## LibraryScreen

Состояния: `loading`, `empty`, `content`, `importing`, `error`.

| Событие | Результат |
|---|---|
| `ImportRequested` | Открывает Android File Picker для EPUB и FB2. |
| `ImportCompleted` | В библиотеке появляется `READY` Book. |
| `ImportFailed` | Понятная причина; новая Book не появляется. |
| `BookSelected(bookId)` | Открывает ReaderScreen. |
| `DeleteRequested(bookId)` | Запрашивает подтверждение и удаляет только app-local данные. |

Карточка всегда содержит title, author, cover/placeholder и progress.

## ReaderScreen

Состояния: `opening`, `reading`, `chapterList`, `recoverableError`.

| Событие | Результат |
|---|---|
| `ReaderOpened(bookId)` | Восстанавливает ReadingPosition либо начинает с начала. |
| `VisibleBlockChanged` | Debounced обновление ReadingPosition. |
| `ChapterSelected(index)` | Переходит к началу главы и сохраняет позицию. |
| `FontScaleChanged` / `ThemeChanged` | Сохраняет preference и логическую позицию. |
| `ReaderStopped` / app `ON_STOP` | Принудительно сохраняет последнюю валидную позицию. |

Повреждённый block или image не прерывает чтение остальных блоков.
