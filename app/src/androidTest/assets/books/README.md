# Тестовые книги

Все тексты созданы специально для тестов HooReader и свободны от сторонних произведений.
В Phase 7 эти fixtures вошли в фиксированный синтетический corpus v1 для измерения SC-003.
Состав, границы представительности и результат: [import-corpus-results.md](../../../../../specs/001-offline-book-reader/import-corpus-results.md).

| Файл | Назначение |
|---|---|
| `structured.epub` | EPUB 3: две главы, содержание, кириллица, диакритика, bold/italic, список и SVG-обложка. |
| `no-author-cover.epub` | Допустимый EPUB без автора и обложки; проверка fallback. |
| `structured.fb2` | UTF-8 FB2: главы, вложенный раздел, форматирование, встроенное PNG. |
| `windows-1251.fb2` | FB2 с объявленной кодировкой Windows-1251 и кириллицей. |
| `missing-metadata.fb2` | FB2 с пустыми title/author и без обложки; проверка fallback. |
| `drm-marker.epub` | Синтетический marker AES-шифрования главы в `META-INF/encryption.xml`. Данные не зашифрованы; проверяется только обнаружение маркера, без DRM-ключей или реального защищённого произведения. |
| `corrupt.epub`, `corrupt.fb2` | Оборванные ZIP и XML; ожидается безопасный отказ. |
| `empty.epub`, `empty.fb2` | Нулевой размер; ожидается отказ. |
| `unsupported.pdf` | Синтетическая сигнатура PDF; формат должен быть отклонён. |

EPUB сохраняет `mimetype` первым элементом ZIP без сжатия. Corpus v1 добавляет каталог `corpus/` и manifest с размером/хешем/ожидаемым результатом.
Генератор: `python3 scripts/generate-import-corpus.py`; прогон: `scripts/verify-import-corpus.sh`.
Исходные fixture bytes не изменяются; новые тексты также синтетические.
