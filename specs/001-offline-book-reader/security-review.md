# Security review — T040

Дата: 2026-10-01. Область: offline MVP, исходники приложения и итоговый release manifest.
Основание: [security.md](checklists/security.md), FR-001–FR-016 и конституция 2.0.0.
Чекбоксы reviewer-owned чеклиста не изменены. Review выполнен; это не утверждение, что все
требования безопасности уже полны или что приложение прошло аудит недоверенных файлов.

## Проверенные механизмы

| Пункты | Наблюдение и доказательство | Результат |
|---|---|---|
| CHK001–003 | `BookFileStorage` использует `Context.filesDir/books/<UUID>`; Room и DataStore также внутри sandbox. Release manifest: `allowBackup=false`. Книги, пути, metadata и позиции следует считать приватными; явной классификации в spec нет. Не заданы отдельные правила device-to-device transfer для Android 12+. | Sandbox подтверждён; требования приватности/переноса неполны. |
| CHK004, CHK012 | UI вызывает `LibraryRepository.deleteBook`: сначала удаляется UUID-каталог, затем запись, главы и позиция через foreign keys. Исходный URI не сохраняется и не используется для удаления. `LibraryRepositoryTest`, `BookFileStorageTest` и `ImportBookFlowTest` проверяют исходник. | Соответствует FR-014. |
| CHK005 | Файлы копируются потоково; reader держит до 128 блоков. Изображения уменьшаются до 1024 px, обложки до 512 px. Размер входа, распакованных ZIP entries, отдельного XML-блока и base64 binary, время обработки не ограничены. FB2 binary полностью декодируется в память. | Риск исчерпания диска/памяти/CPU; FR-016 не гарантирует защиту от вредоносного входа. |
| CHK006 | `EpubContentMapper`/`EpubTextReader` игнорируют script/style/iframe/object/embed/audio/video; WebView отсутствует. `localEntry` отклоняет URI scheme/authority и `..`; EPUB не распаковывается в произвольные пути. FB2 media принимает только `#id`. `nextSafe` отклоняет DTD с ENTITY. `StructuredContentParserTest` проверяет активный content и внешние media. | Активное содержимое не исполняется; внешние ресурсы не загружаются. |
| CHK007–009 | `EpubValidation` отклоняет LCP/rights и неизвестное шифрование; стандартная обфускация шрифтов разрешена. Readium дополнительно проверяет `isRestricted`. Ошибки EMPTY/UNSUPPORTED_FORMAT/DRM/CORRUPT/IO превращаются в фиксированные русские строки. `BookImportValidatorTest` проверяет различение ошибок. | Соответствует принятому scope; гарантий определения любого нестандартного DRM нет. |
| CHK010–011 | В merged release manifest нет INTERNET, READ/WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE и runtime permissions. Только signature permission AndroidX dynamic receiver. Выбор через `OpenDocument`; `takePersistableUriPermission` отсутствует, stream закрывается после копирования. | Минимальный доступ подтверждён. Временный grant регулируется Android; отдельное требование срока в spec отсутствует. |
| CHK013, CHK015 | Сеть/аккаунты/OPDS исключены spec и конституцией §IV. Нет сетевых запросов приложения и секретов в просмотренном коде/fixtures; `.gitignore` исключает env, keystore, local.properties. Readium открывает локальный `FileResourceFactory`. | Offline scope согласован. Проверка не является историческим secret scan всех git commits. |
| CHK014 | В `app/src/main/java` нет Log, println и printStackTrace. Ошибки UI не показывают exception/message/stack trace, URI или путь книги. Внутренние исключения библиотек и системный crash log не управляются этим review. | Логи приложения не раскрывают текст; политика диагностики явно не сформулирована. |
| CHK016 | Staging `.part` удаляется в finally; после отказа/отмены некоммитнутый UUID-каталог удаляется в NonCancellable. READY запись и главы публикуются одной Room transaction после parsing. SIGKILL не выполняет finally: возможны осиротевшие каталоги, recovery при старте отсутствует. | Отказы/отмена обработаны; очистка после гибели процесса — пробел. |
| CHK017 | Нечитаемый блок/media получает заглушку, локальная книга и последняя позиция сохраняются; retry открытия доступен. `ReaderViewModelTest`, `ContentBlockRendererTest` проверяют деградацию. | Соответствует FR-008; отдельная политика retention отсутствует в spec. |
| CHK018 | Координаты и процент проверяются моделью и SQL triggers; bookId — FK; timestamp защищает от запоздалой записи. Debounce 400 ms, flush при ON_STOP/выходе. `ReadingPositionSaverTest` и `BookRepositoryTest` проверяют failures и чужие/некорректные позиции. | Последняя завершённая запись сохраняется. Внезапная гибель до debounce может потерять ещё не записанный прогресс. |

## Проверка сборки

`./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest` завершилась успешно.
Проверен `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`:
exported launcher Activity; AndroidX startup provider и Room service не экспортируются;
exported ProfileInstallReceiver защищён `android.permission.DUMP`. Debug test provider не экспортируется
и отсутствует в release. Android Lint не обнаружил ошибок.

## Оставшиеся решения перед расширением аудитории

1. Уточнить в spec лимиты входа, ZIP expansion, XML-блока/base64 и время/отмену обработки;
   добавить adversarial fixtures и проверки отказа без OOM/заполнения диска.
2. Определить recovery для осиротевших импортов после завершения процесса.
3. Уточнить backup/device transfer и приватность системной диагностики.

Эти замечания не изменяют scope и не добавляют OPDS. T040 завершает review, а не исправление
всех обнаруженных пробелов. Требования на новые политики должны быть согласованы в spec до реализации.
