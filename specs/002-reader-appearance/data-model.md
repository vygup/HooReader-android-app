# Модель данных: режимы и интерфейс чтения

**Дата**: 2026-10-02
**Основание**: [spec.md](spec.md), [research.md](research.md)

## Сохраняемые настройки

| Сущность / поле | Тип и допустимые значения | По умолчанию | Хранение |
|---|---|---|---|
| ReaderPreferences.theme | ReaderTheme LIGHT/DARK | LIGHT | существующий theme |
| ReaderPreferences.fontScale | конечный Float, 0,75–2,0 | 1,0 | существующий font_scale |
| ReaderPreferences.readingMode | ReadingMode VERTICAL/PAGINATED | VERTICAL | новый reading_mode |
| AppPreferences.confirmReaderExit | Boolean | true | новый confirm_reader_exit |

Все поля относятся к одному пользователю устройства и применяются ко всем книгам.
Оба preferences flow используют один DataStore reader_preferences. Отсутствующий или неизвестный
reading_mode даёт VERTICAL. Старые theme/font_scale читаются без изменения; существующее
ограничение масштаба сохраняется. Ключи записываются атомарно через edit только для изменённых полей.
Настройки приложения доступны отдельно от меню настройки чтения.

SettingsWriteState: persistedSnapshot, requestedSnapshot, pendingFields, error.
Последовательная очередь применяет намерения к последнему requestedSnapshot. Успех удаляет
только подтверждённые pendingFields, ошибка оставляет их для retry; новый запрос заменяет
устаревшее pending значение того же поля. Ошибка другого поля не сбрасывается.
Pending изменения хранятся в памяти до успешной записи; после завершения процесса восстанавливается
persistedSnapshot. UI сообщает о несохранённом выборе. При сбое flush перед изменением геометрии
ReaderPreferences для выбранной пользователем смены режима/масштаба effective выбор остаётся
прежним до повторной попытки. Системная смена геометрии этим правилом не блокируется.

## ReadingPosition и LogicalAnchor

Существующая ReadingPosition в Room v1 остаётся источником долговечной позиции:

| Поле | Правило |
|---|---|
| bookId | UUID существующей READY книги |
| chapterIndex | ≥0, индекс доступной главы |
| blockIndex | ≥0, исходный индекс внутри главы |
| characterOffset | 0..text.length, UTF-16 offset исходного Kotlin String |
| progressPercent | конечный Double, 0..100 |
| updatedAt | ≥0, монотонно увеличивается для новых сохранений |

LogicalAnchor — набор chapterIndex/blockIndex/characterOffset без пикселей и номера страницы.
Он связывает вертикальную строку, PageSlice и ReadingPosition. Для изображения offset=0.
Граница фрагмента не разрывает Unicode surrogate pair; порядок координат лексикографический.
Переходы глав используют начало следующей или последнюю страницу предыдущей.

restoreAnchor отдельно удерживается при восстановлении/смене геометрии: показ страницы, содержащей
anchor, не перезаписывает его первым символом этой страницы. После явного перелистывания позиция
переходит к началу новой settledPage. Вертикальный список получает offset первой видимой строки
из общего TextLayoutResult, а не из firstVisibleItemIndex без учёта обрезки абзаца.

GeometryChangeOrigin: USER_PREFERENCE — режим/масштаб из меню; SYSTEM_CONFIGURATION —
ориентация, viewport, density или системный шрифт. USER_PREFERENCE требует успешного flush
до применения выбора. SYSTEM_CONFIGURATION захватывает актуальный logicalPosition либо
restoreAnchor незавершённого восстановления, создаёт новую generation и перестраивает layout
без ожидания записи; effective режим/масштаб остаются прежними, системные метрики обновляются.

Несохранённая позиция остаётся в памяти с revision и отдельной ошибкой сохранения. Повтор
записывает последнюю актуальную позицию; последующая навигация заменяет более старую pending
позицию. Успех записи старой revision не очищает более новую pending revision и не меняет
restoreAnchor. Этот сбой не переводит операцию выхода в FAILED, если выход не выполнялся.
После завершения процесса доступна последняя успешно записанная ReadingPosition.

Прогресс использует существующее приближение по блокам и доле абзаца с ограничением 0..100.
Для конца доступного содержимого принудительно 100, для начала — 0, включая одноэлементную книгу.
Значение не выводится из pageNumber; в UI целый процент получается усечением к меньшему целому.

## Производное хранилище содержимого

BookContentIndex не является источником истины и безопасно пересоздаётся:

| Поле | Назначение |
|---|---|
| bookId, contentHash | связь с исходной неизменной книгой |
| parserVersion, spoolVersion | инвалидация после изменения нормализации |
| blockRecords | последовательные записи ContentBlock в файловом spool |
| chapterDirectory | диапазоны и начало записей каждой главы |
| checkpoints | файловые смещения для небольших окон произвольного чтения |
| imageMetrics | размеры локальных media либо детерминированный fallback |
| complete | признак полностью записанного и проверенного spool |

Записи сохраняют исходные координаты и TextStyleRange[start,endExclusive).
UTF-8 byte offsets spool не подменяют UTF-16 characterOffset. orderedBlocks() обходит книгу
один раз; FB2 не сканируется заново на каждую главу. Существующие книги индексируются лениво,
импортированные после изменения могут подготовить spool во время локальной обработки.
Готовый spool публикуется атомарно после проверки; незавершённый .part не читается как готовый.

Путь: filesDir/books/<bookId>/derived/content/<contentHash>-<parserVersion>/.
Удаление книги через существующий BookFileStorage удаляет производные данные вместе с исходной
app-local копией. Ошибка или отсутствие spool приводит к пересозданию, не к удалению позиции.
Все пути проверяются как принадлежащие каталогу книги; внешние media не загружаются.

## LayoutKey, PageIndex и PageSlice

LayoutKey — идентичность измеренной разбивки:

- contentHash, parserVersion, paginatorVersion, layout schema version;
- content width/height в px после insets и постоянных полос;
- density и параметры реального системного преобразования sp;
- масштаб чтения, resolved typography/font identity, lineHeight, lineBreak;
- layout direction, locale, list indent, межблочные интервалы и размеры полос.

Системная версия/смена шрифта инвалидируют несовместимый кэш. Цвета и controlsVisible
не меняют ключ, когда метрики текста неизменны. Ориентация учитывается фактическими dimensions.

PageIndex хранится в filesDir/books/<bookId>/derived/pages/<layoutHash>/.
Количество сохранённых layouts ограничивается двумя последними ключами на книгу; эвикция
удаляет только производные данные. PageIndex содержит точные pageCounts завершённых глав,
page boundary records, checkpoints, completedPrefix и eofKnown. Публикация префикса атомарна.
Незавершённые или повреждённые записи не используются; partial prefix валидируется перед reuse.

| Поле PageSlice | Правило |
|---|---|
| chapterIndex, localPageIndex | ≥0; новая глава начинает страницу |
| globalPageNumber | 1 + localPageIndex + сумма точных counts предыдущих глав |
| startAnchor, endAnchorExclusive | упорядоченный непересекающийся диапазон |
| fragments | текстовые строки, списки, media/fallback с исходными координатами |
| geometry | проверенные позиции внутри content viewport |

Границы следующих страниц непрерывны по доступному содержимому; декоративные list markers
не изменяют исходные offsets. Пустая глава имеет одну страницу с сообщением fallback.
При split текста styles остаются относительно исходного блока, а отображение использует
измеренные линии. Изображение занимает цельную область и вписывается пропорционально.

globalPageNumber доступен только при готовом точном префиксе. Подготовленный frontier
не считается концом книги: впереди может быть ещё содержимое. В RAM — окно до 128 блоков,
текущая/предыдущая/следующая страницы и bounded LRU; полный текст и все layout не удерживаются.

## Состояния интерфейса и операции

ReaderUiState: Opening, PreparingPages, Reading, RecoverableError.
Reading включает effectiveMode, logicalPosition, indicator, layoutGeneration, текущий page/window.
PreparingPages сохраняет последний anchor, показывает подготовку, разрешает выход и отмену
новым выбором; не разрешает жесту стать tap и не публикует неверный номер. Только завершённое
Reading с правильным номером считается применённым постраничным режимом.

ReaderChromeState: controlsVisible=false, overlay=NONE.
Overlay: NONE, CONTENTS, READER_SETTINGS, EXIT_CONFIRMATION; состояния взаимоисключающие.
ExitState: NONE, CONFIRMING, SAVING, FAILED.
ReaderViewModel — единственный владелец ReaderChromeState/overlay/ExitState; NavHost и sheets
не дублируют их локальными mutable флагами. Очередь preferences сохраняет своего владельца.

| Событие | Переход / инвариант |
|---|---|
| BookOpened | Opening → Reading или PreparingPages; панели скрыты |
| BookTap | только Reading/NONE: переключить controlsVisible один раз |
| NavigationDragStarted | скрыть панели; подавить tap до конца жеста |
| OpenContents/OpenReaderSettings | один overlay, без навигации книги |
| DismissOverlay/ChapterSelected | overlay=NONE, панели скрыты |
| GeometryRequested(USER_PREFERENCE) | успешный flush anchor → новая generation; сбой оставляет прежний выбор и retry |
| GeometryChanged(SYSTEM_CONFIGURATION) | anchor в памяти → новая generation с актуальными метриками; запись не блокирует layout |
| PositionWriteFailed | pending позиция и ошибка; retry последней revision, без отката геометрии |
| LayoutCompleted | Reading только для актуальных generation/LayoutKey |
| ExitRequested | CONFIRMING при включённой защите, иначе SAVING |
| ExitCancelled | NONE, та же позиция и прежняя видимость панелей |
| ExitConfirmed | SAVING; повторные запросы игнорируются |
| FlushSucceeded при выходе | один NavigateToLibrary effect |
| FlushFailed при выходе | FAILED, книга остаётся доступной с retry |
| ON_STOP | flush без запроса и без навигации |

Подробные внешние события: [UI-контракт](contracts/reader-ui.md).
Изменения Room schema не требуются; тест совместимости проверяет старые позиции и preferences.
