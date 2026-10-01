# UX review — T041

Дата: 2026-10-01. Основание: [ux.md](checklists/ux.md), spec и UI contract.
Reviewer-owned чекбоксы оставлены без изменений. Здесь отдельно указаны наблюдаемое поведение
и пробелы написанных требований; существование UI не делает исходные требования полными.

| Пункты | Требования и фактическое UI-поведение | Вывод |
|---|---|---|
| CHK001 | FR-006 перечисляет поля; `BookCard`: обложка слева, название/автор/процент справа, удаление ниже. Название 2 строки, автор 1 строка, ellipsis, lazy keys. | Состав выполнен; порядок/приоритет не закреплены в spec. |
| CHK002 | `LibraryScreen`: «Пока нет книг», объяснение EPUB/FB2, доступная кнопка импорта. LOADING и IMPORTING имеют индикатор; повторный импорт во время операции отключён. | Пустое состояние реализовано; contract содержит empty без текста. |
| CHK003, CHK016 | Фиксированные русские причины EMPTY/UNSUPPORTED_FORMAT/DRM/CORRUPT/IO видны в библиотеке. «Понятно» закрывает сообщение; кнопка нового выбора остаётся доступной. | Recovery понятен; тексты и визуальный приоритет определены resources, не spec. |
| CHK004 | Сообщение явно говорит о сохранении прежней записи и позиции; «Открыть книгу» ведёт к существующему bookId. | Соответствует FR-005. |
| CHK005 | AlertDialog называет книгу, предупреждает об удалении её прогресса и сохранении исходника. «Отмена» и «Удалить из библиотеки»; выбор переживает recreation. | Подтверждение реализовано и проверено `ImportBookFlowTest`. |
| CHK006 | Reader opening показывает CircularProgressIndicator. RecoverableError предлагает «Повторить» и возврат в библиотеку. | Ожидание обозначено; текстовый статус/timeout не определены. |
| CHK007 | Кнопка «Содержание» открывает ModalBottomSheet; главы пронумерованы при отсутствии title; выбор закрывает sheet и открывает начало главы; dismiss возвращает к чтению. | Проверяется `ReaderNavigationTest` для EPUB и FB2. |
| CHK008–009 | Вертикальный LazyColumn, heading, bold/italic, bullet lists, локальные raster images. Нечитаемое media/SVG — «Изображение недоступно», malformed block — безопасная заглушка. | Согласовано с FR-007/008; весь CSS, SVG и сложная вёрстка в scope не входят. |
| CHK010–011 | «Настройки чтения» доступны в reader. Начало LIGHT/100%; диапазон 75–200%, кнопки ±25%, slider 4 промежуточные точки; LIGHT/DARK RadioButton, «Готово». | Поведение определено кодом; диапазон/шаг не заданы spec. |
| CHK012 | Позиция логическая; theme сохраняет viewport, font/orientation восстанавливают тот же block и ограничивают character offset. | `ReaderNavigationTest`, `ReaderSettingsTest`, `ReaderPositionResolverTest` покрывают anchor. |
| CHK013 | 0% до первого чтения, «≈ N%» после; «Прочитано» от 98%; Room query наблюдает обновления. | Соответствует FR-011, проверено repository/UI tests. |
| CHK014 | Material buttons имеют текст; удаление содержит название книги в semantics; темы — selectableGroup/Role.RadioButton; slider имеет числовую semantics. | Базовая семантика есть; TalkBack traversal и полная accessibility acceptance не выполнены. Slider не имеет отдельной явно заданной contentDescription. |
| CHK015 | Material3 light/dark palette; sp учитывает системный fontScale, reader дополнительно применяет preference. Cover/placeholder имеют описание; image использует alt/fallback. Settings sheet прокручивается. | Нет требований минимального контраста/максимального system scale; 200% системного текста и длинные toolbar labels требуют отдельной проверки. |
| CHK017 | Filename и «Неизвестный автор», placeholder без ошибки импорта; отсутствующая обложка не мешает открытию. | Соответствует FR-004, покрыто `LibraryRepositoryTest`/`LibraryScreenTest`. |
| CHK018 | Resolver выбирает ближайший существующий block/chapter молча, сохраняя доступное чтение. | FR-010 выполнен; текст уведомления о fallback не задан и не реализован. |

## Результат

Код и существующие тесты покрывают основные состояния Library/Reader/Settings, безопасное
удаление, повторный импорт и восстановление anchor. Для сквозного device-прогона см.
[quickstart-results.md](quickstart-results.md), который формируется задачей T042.

Review выявил неполноту требований к accessibility, системному масштабу, сообщениям ожидания
и fallback. Это замечания к требованиям и границе проверки, а не отметки reviewer-owned чеклиста.
T041 выполнена; UX review не равнозначен полному аудиту TalkBack/контраста.
