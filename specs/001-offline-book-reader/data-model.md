# Модель данных: Офлайн-читалка книг

## Book

| Поле | Правило | Назначение |
|---|---|---|
| `id` | UUID | Идентификатор записи. |
| `contentHash` | SHA-256, уникальный | Обнаружение одинакового содержимого. |
| `format` | `EPUB` или `FB2` | Выбор parser. |
| `localPath` | app-specific путь | Независимая копия книги. |
| `title` / `author` | непустые строки | Metadata или fallback из спецификации. |
| `coverPath` | nullable | Обложка или UI placeholder. |
| `state` | `IMPORTING`, `READY`, `FAILED` | Не выдавать неполный импорт за доступную книгу. |

Переходы: `IMPORTING → READY` после проверки, копирования, parsing и metadata; `IMPORTING → FAILED`
удаляет неполные файл и metadata. Удаление `READY` удаляет только app-local данные.

## Chapter и ContentBlock

Chapter имеет составной ключ `bookId` + `index`, nullable title, `sourceRef` и `blockCount`.
`Book 1:N Chapter`. При отсутствии содержания создаётся одна логическая глава без UI содержания.

ContentBlock — runtime-модель: `chapterIndex`, `blockIndex`, `kind` (`paragraph`, `heading`, `list`,
`image`), text/style и nullable media reference. Блоки извлекаются по главам, не для всей книги.

## ReadingPosition

| Поле | Правило |
|---|---|
| `bookId` | уникальный внешний ключ: одна последняя позиция на книгу |
| `chapterIndex`, `blockIndex`, `characterOffset` | неотрицательные логические координаты |
| `progressPercent` | 0–100, только для библиотеки |
| `updatedAt` | timestamp последней валидной записи |

Если структура недоступна, выбираются ближайшие существующие глава и блок.

## ReaderPreferences

`theme` = `LIGHT` или `DARK`; `fontScale` — ограниченное значение размера текста. Preferences
глобальны для устройства и не содержат сетевых credentials.
