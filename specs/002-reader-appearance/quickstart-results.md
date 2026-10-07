# Фактические результаты второго релиза

## US1 — Phase 3, T014–T021

Pixel_10 AVD, API 37, 1080×2424, density 420, VERTICAL. Физического стенда нет.
Открытая книга скрывает управление; сверху только глава и целый процент. Управление
доступно первым tap, содержание/настройки — вторым. Выход доступен также при Opening/error.

- 20 tap с jitter ниже системного slop: ровно 20 переключений; bounds viewport и LogicalAnchor
  неизменны после каждого. Проверено настоящим touch input.
- 100 scroll/cancel/diagonal/return-to-down/multi-pointer/long-press/boundary жестов:
  панели не открылись; viewport не изменился. Достижение slop подавляет tap до конца жеста.
- Consumed дочерняя кнопка и cancel не дают BookTap; обычный tap работает.
- Быстрые касания содержания/настроек дают ровно один menu. Реальный системный Back закрывает
  menu, скрывает панели и удерживает книгу. Проверены оба menu по три раза и переход главы.
- Старые импорт/навигация/настройки/rotation/restore сценарии обновлены первым реальным tap
  и проходят в общем Android-наборе.

[Жесты/viewport](evidence/validation/T019-android.txt),
[menu/Back](evidence/validation/T020-menu-green.txt),
[финальный phase check](evidence/validation/T021-full-gradle-android.txt): 79 debug +
79 release unit tests, detekt/lint/build/APK — успешно. Android: 35 tests,
29 passed, 6 opt-in skipped, 0 failures. Opt-in skip не означает проверку сценария.

## Появление панелей, диагностический SC-004

[Профиль](evidence/panel-profile.json), [условия перед серией](evidence/panel-conditions.json),
[все raw значения](evidence/panels-v2.json).
ProfileId: `4f0255d7bbb5824ae19746a2598b7e50b507e0cf2059b94af89aa5a4bd832bfe`.

| Пять значений, ms | min | median | max | Статус |
|---|---:|---:|---:|---|
| 147.322, 132.398, 146.392, 131.785, 129.935 | 129.935 | 132.398 | 147.322 | NOT_VERIFIED_DEVICE |

Отдельный opt-in замер выполнен пять раз. Таймер начинается перед dispatch UP и заканчивается
после проверки доступности действий и drawn capture controls; viewport/anchor проверены
после каждого появления. Это диагностическая верхняя оценка, не точный физический
first-presented frame. Первое выполнение через Gradle успешно, но raw был удалён вместе
с тестовой установкой; для сохранения всех значений повторено через ADB без удаления APK.
Эмулятор не подтверждает аппаратный порог ≤0,3 s.

## US2 — функциональные проверки T022–T034

Production ReaderViewModel использует общий spool и PageIndexStore. EPUB/FB2 проверены
на API 37 настоящими destination/input: один свайп — одна settledPage; cancel и обе
границы сохраняют страницу/скрытые панели; содержание, режим, масштаб и rotation
восстанавливают исходный anchor внутри длинного абзаца. Containing-page restore не
заменяет его page start. После явного перелистывания сохраняется начало новой страницы.
Vertical окно ограничено 128 элементами и проходит соседние, в том числе пустые главы.
Native line packing сохраняет все Unicode chars/spans, не разрывает surrogate pairs,
images цельные и fallback не меняет geometry после bitmap failure.

USER_PREFERENCE при failed position flush удерживает effective выбор до retry;
SYSTEM_CONFIGURATION сразу использует новую геометрию и anchor из памяти. Проверены
rapid system font 150→200%, последующая навигация, pending revisions, late success/failure
старой записи и retry последней позиции. Полное OS process death остаётся задачей T059.
Два новых EPUB/FB2 Android-теста подтвердили database/reader reopen без retained owners
и без внешнего original с UTF-16 offset 15000 в paragraph >100000 chars.

[Полный интеграционный T033](evidence/T033-full-android.xml): 35 passed, 6 opt-in skipped,
0 failures. [Targeted T034](evidence/T034-android.xml): 10 passed, 0 failures.
[Полный check/build/lint T034](evidence/T034-check.txt): 98 debug +98 release unit tests,
успешно. Функциональный SC-003 подтверждён в этом окружении; acceptance ниже не подменён.

## US2 — checkpoint фазы 4, T035–T036

Функциональная фаза 4 завершена на Pixel_10 AVD/API 37. Оба формата и оба режима
проверены production source/reader destination. [Полный финальный прогон](evidence/T036-full-android.xml):
43 теста, 37 passed, 6 opt-in skipped, 0 failures/errors. В Gradle console строка
«Finished 49 tests» включает повторную регистрацию skips; число уникальных testcase
в сохранённом XML — 43. [Check/build/lint/detekt и APK](evidence/T036-validation.txt)
прошли; unit XML подтверждает 98 debug +98 release, без failures/errors/skips.
Opt-in pagination измерена отдельно в T035, skipped общего прогона это не заменяет.

| Сценарий | Результат и проверка |
|---|---|
| EPUB/FB2 × VERTICAL/PAGINATED | Реальные настройки/свайпы/содержание, default mode и сохранённый выбор; chooser и PagedReaderTest |
| Границы главы/книги | TOC, первая/последняя страница, движение через границу последней главы в обе стороны; synthetic frontier/EOF test и vertical соседнее окно с empty fallback |
| Отсутствие пропусков/дублирования | Native TextPaginationLayoutTest собирает обратно весь исходный текст каждого блока из fragments, проверяет непрерывные anchors/global counts и UTF-16 surrogate boundaries для EPUB/FB2 |
| Geometry и restore | Scale/rotation/mode сохраняют inner-paragraph anchor; реальные Room/reader reopen без original, native failed flush/system changes/latest retry |
| Режим после закрытия книги | Выход экранной кнопкой и новое открытие создают другой ReaderViewModel (assertNotSame), восстанавливают PAGINATED, исходный anchor и точный номер; оба формата |
| Панели в PAGINATED | 20 настоящих taps на каждом формате: visibility toggle, bounds viewport, LogicalAnchor, generation и ReaderPageState неизменны после каждого |

Containing-page restore удерживает исходный inner anchor; только явное settled-page
navigation сохраняет page start. PAGINATED не удерживает дополнительное vertical окно;
возврат в VERTICAL загружает ≤128 блоков вокруг актуального anchor. Старый layout
не публикуется после cancellation, `.part` очищены.

[T035 production performance](performance-results.md): baseline 180 и optimized 180
raw измерений с отдельными profileId. Новый reader/Room/source/prefix/native draw
включены в таймер; точный номер не заменён оценкой. Максимум повторной серии
55169.996 ms, причина больших FB2 задержек не установлена; все значения сохранены.
**SC-004 ≤1000 ms НЕ ПРОЙДЕН и остаётся release blocker**. Физического устройства нет,
аппаратная приёмка NOT_VERIFIED_DEVICE; functional checkpoint не объявляет релиз готовым.

## US3 — фаза 5, T037–T042

2026-10-03: указатели вынесены в измеренные постоянные полосы вокруг reader_viewport.
В VERTICAL сверху глава первого видимого фрагмента и целый процент всей книги; в PAGINATED
сверху глава, снизу точный globalPageNumber актуального LayoutKey. Процент и номер другого
режима отсутствуют. Неназванная глава получает «Глава N», пустой список разделов — отсутствие
поля главы. Номер не публикуется для прежнего LayoutKey во время перерасчёта.

Процент учитывает все блоки и долю текущего абзаца; исправлено преждевременное 100% в начале
последнего абзаца. Достижение реального конца доступного содержимого даёт 100%, начало — 0%,
включая одноэлементную книгу; UI округляет вниз. Конец окна из 128 блоков не считается концом
книги. Показатель видимого фрагмента отделён от restore anchor: начальное позиционирование
короткой главы не перезаписывает сохранённый UTF-16 offset. Старые callbacks от другой
layoutGeneration игнорируются.

Шрифт указателя — labelSmall 11sp, ограниченный 90% размера основного текста;
onSurfaceVariant с alpha 0,6. Системное преобразование sp выполняет Compose. Ellipsis
применён только к названию; процент/номер выводятся полностью. Верхняя и нижняя полосы
измеряются перед книгой в одном проходе SubcomposeLayout, их реальные размеры входят
в LayoutKey. Панели остаются независимым overlay и не меняют viewport/page slices.

| Проверка | Результат |
|---|---|
| Unit/debug и release | По 105 tests, 0 failures/errors/skips; 6 новых resolver и 1 restore/indicator regression |
| check, APK, Android test APK, Detekt, Lint | Успешно; [журнал](evidence/indicators/T042-check.txt) |
| EPUB/FB2 × VERTICAL/PAGINATED × LIGHT/DARK | 8 сценариев: swipe/scroll через главу, TOC, reopen, положение полос, целые цифры, toggle invariants |
| Системный 100%, масштаб чтения 100% | 8/8, обе ориентации, 16 PNG |
| Системный 200%, масштаб чтения 75% | 8/8, обе ориентации, 16 PNG |
| Системный 200%, масштаб чтения 200% | 8/8, обе ориентации, 16 PNG |
| Общий Android regression | 51 test: 45 passed, 6 opt-in skipped, 0 failures |

Стенд: Pixel_10 AVD/API 37, 1080×2424, density 420. Системный шрифт изменён через Android
settings, каждый screenshot-сценарий проверяет фактическую configuration.fontScale;
подмены LocalDensity нет. Скрипт вернул исходный масштаб 1,0. По 10 просмотренным снимкам
подтверждены меньший размер/контраст указателей и отсутствие перекрытий в обеих темах,
режимах и ориентациях, включая системный максимум. Все 48 оригиналов и машинный отчёт:
[галерея/логи/SHA-256](evidence/indicators/README.md).

Новый T038 сначала выявил нижнюю полосу внутри reader_viewport; [неуспешный прогон](evidence/indicators/T038-before.txt)
сохранён, после интеграции тест проходит. Для контроля обрезки проверяются фактические
границы строки, line end и отсутствие ellipsis/vertical overflow. Общий regression:
[raw instrumentation](evidence/indicators/T042-full-android.txt),
[XML, построенный из raw статусов](evidence/indicators/T042-full-android.xml).

## Открытые критерии и продолжение (контрольная точка после T042)

На момент этой контрольной точки фазы 1–5 были завершены: T001–T042 отмечены выполненными;
T043–T065 ещё не начаты. Физический performance стенд и API 26
по-прежнему недоступны. SC-004 ≤1 секунды остаётся непройденным release criterion T035.

Техническая визуальная проверка T042 не заменяет SC-007 с пятью реальными участниками.
Полная матрица SC-008 (включая 150% и остальные UI), SC-005/006, итоговый двухрежимный
SC-001/002, OS process-death T059, обновление v1→v2 и RC остаются следующими задачами.
Существующие «Готово» и прямой выход соответствуют промежуточной фазе: их изменения — US4/US5.
Позднее, по прямому запросу пользователя, UX checklist был проверен по спецификации и закрыт;
для CHK004 и CHK020 добавлены недостающие формулировки в spec.md.

## US4 — фаза 6, T043–T048

Reader settings теперь держат persisted и requested snapshots, pending поля и ошибку записи.
Intents сериализуются по полям: успешная запись очищает только подтверждённое поле, ошибка
темы не мешает сохранить масштаб и режим, новые действия заменяют ожидающее значение того же
поля, retry использует последнее requested значение. DataStore атомарно изменяет только ключ
текущего поля. Меню показывает requested выбор, сообщает об ошибке с действием повтора,
применяет тему и режим после записи, масштаб — после отпускания slider; геометрия меняется
после успешной записи. «Готово» и соответствующая строка удалены. Закрытие снаружи, Back и
свайп возвращают читалку к скрытым панелям.

Модульные регрессии: ReaderSettingsViewModelTest — 3 tests, Preferences repository — 6 tests,
0 failures/errors/skips. Проверено selective failure темы при успешной записи масштаба и режима,
замена pending темы, retry последнего выбора и восстановление persisted настроек новой моделью.
Android API 37 Pixel_10 AVD: 2 UI tests passed при системном шрифте 100%; integration-сценарий
повторён при 200% и прошёл. Проверены автоприменение theme/scale/mode, закрытие меню касанием
снаружи, системным Back и свайпом, hidden chrome, отсутствие «Готово», ошибка/retry и сохранение
режима после пересоздания Activity и повторного открытия книги. Исходный системный масштаб
эмулятора восстановлен до 100%.

[T043 unit XML](evidence/settings/T043-viewmodel-unit.xml),
[T048 repository regression XML](evidence/settings/T048-preferences-regression.xml),
[T044 UI XML, 100%](evidence/settings/T044-api37-font100.xml),
[T044 UI XML, 200%](evidence/settings/T044-api37-font200.xml).
Для SC-005 подтверждены автоприменение и сохранение при повторном открытии книги, пересоздании
Activity и новой ViewModel. Полная проверка после завершения процесса и в авиарежиме остаётся
для завершающей фазы; полный SC-008 также ещё не закрыт. Временные пороги
SC-004 для доступности/применения настроек этим UI-прогоном не измерялись и остаются
непроверенными по физическому стенду; ранее зарегистрированный performance blocker сохраняется.


## US5 — фаза 7, T049–T057

На 2026-10-07 реализована общая защита выхода. В библиотеке доступен отдельный экран
настроек приложения с переключателем «Подтверждать выход из книги». По умолчанию он включён,
в том числе для старого файла preferences; новое значение сохраняется в том же singleton
DataStore без изменения Room v1, темы, масштаба и режима. Экран использует общую очередь
настроек: несохранённое значение и ошибка остаются до успешной записи, retry пишет последний выбор.

Экранная кнопка и системный Back используют одну машину выхода ReaderViewModel. Back сначала
закрывает содержание/настройки. Отмена запроса кнопкой, Back или настоящим касанием вне окна
сохраняет позицию и прежнюю видимость панелей. Подтверждение и выход при отключённой защите
возвращают в библиотеку только после успешного flush. Ошибка оставляет книгу доступной
с «Повторить выход»; повторные запросы во время SAVING не создают второй dialog/navigation.
ON_STOP и disposal сохраняют позицию без выхода и запроса. Завершение жеста снимает tap
suppression даже при открытом запросе, поэтому после отмены первое касание вновь работает.

Проверки финального кода:

- 115 debug и 115 release unit tests: 0 failures/errors/skips; Detekt, Lint и оба debug APK
  прошли. Lint: 0 ошибок, 24 предупреждения. Критические проверки: reducer — 5, настоящий
  ReaderViewModel — 7, position saver — 7, preferences repository — 6, shared settings queue — 4.
- Полный Android XML на Pixel_10 AVD, API 37: 54 уникальных теста, 48 passed, 6 opt-in skipped,
  0 failures/errors. Повторные status-сообщения Gradle не считаются дополнительными тестами.
  Skipped — pagination/performance/panel timings и две process-death фазы, требующие специального запуска.
- Отдельная US5-матрица: два настоящих destination-сценария при системном шрифте 100% и ещё
  два при 200%; все прошли. Проверены оба значения переключателя, пересоздание Activity,
  оба способа выхода, три способа отмены, menu priority, failed flush/retry и пять повторных
  Back при задержанной записи. Уход Activity в CREATED и возврат проверены при true и false:
  запрос не возникает. При retry долговечная Room позиция соответствует последнему блоку.
- Новые unit-сценарии подтверждают сохранение внутреннего offset после отмены, отсутствие
  navigation при ON_STOP, один navigation effect после последней revision, удержание последней
  позиции при сбое и нескольких lifecycle flush. Preferences проверены после закрытия и
  повторного открытия DataStore; успешная запись соседнего поля не стирает pending app preference.
- Скриншоты настроек и dialog при 100%/200% проверены визуально: подписи переносятся,
  вертикальные действия доступны и не обрезаны. Исходный системный масштаб восстановлен до 100%.
  Сценарии прежнего импорта, повторного открытия настроек и paginated reader обновлены для подтверждения выхода.

SC-006 функционально подтверждён на указанном эмуляторе; результаты не подменяют физический
стенд или полную API/font/orientation матрицу фазы 8. OS process-death второго релиза остаётся
T059. SC-004 ≤1 секунды по-прежнему является открытым release blocker по T035;
этот прогон не измерял performance и не закрывает SC-007 или полный SC-008.

[Итоговая проверка](evidence/exit/T057-final-validation.txt),
[полный Android XML](evidence/exit/T057-full-android.xml),
[US5, 100%](evidence/exit/T057-system1.0.txt), [US5, 200%](evidence/exit/T057-system2.0.txt),
[сводка и APK hashes](evidence/exit/phase7-validation.json).
Воспроизведение на выделенном стенде: `ADB=<путь к adb> bash scripts/verify-reader-exit.sh`
после сборки debug и androidTest APK.

## Фаза 8 — SC-008, T058

На 2026-10-07 полная матрица EPUB/FB2 × VERTICAL/PAGINATED × LIGHT/DARK ×
портрет/альбом × системный шрифт 100/150/200% × масштаб чтения 0,75/2,0 прошла
на Pixel_10 AVD, API 37: 96 сочетаний, 48 instrumentation methods, 0 failures/skips.
Проверены реальные tap/swipe, содержание, прокручиваемые настройки, смена режима,
отмена/подтверждение выхода и настройки приложения. Системный шрифт менялся через Android
Settings, без подмены Density; после поворота/смены шрифта позиция сохранена (точно для
PAGINATED, в пределах одного абзаца для VERTICAL). Панели не меняют viewport; указатели
расположены вне текста. Проверка границ строк учитывает округление intrinsic width до
целого пикселя. Скриншоты крайних вариантов проверены визуально; все 576 PNG сохранены.

Матрица выявила и закрыла дефект Back у полностью раскрытого Material3 sheet: первое
нажатие сворачивало меню наполовину. Настройки и содержание теперь пропускают промежуточное
состояние, закрываются одним Back и возвращают скрытые панели. Подготовительные failures
сохранены отдельно и не считаются финальным результатом. Исходный font_scale=1,0 восстановлен.
TalkBack исключён решением B; этот прогон не подтверждает физические временные пороги или SC-007.

Доказательства: [стенд](evidence/font-scale/device.txt),
[матрица и screenshots](evidence/font-scale/README.md),
[машинный итог](evidence/font-scale/summary.json).
Воспроизведение: `ADB=<путь к adb> scripts/verify-reader-font-scale.sh`.

## Фаза 8 — OS process-death, T059

Специальный opt-in прогон на том же API 37 прошёл для VERTICAL/PAGINATED ×
confirm_reader_exit=true/false. В каждом сценарии импортированы собственные EPUB и FB2
с абзацем >100 000 UTF-16 code units; сохранён безопасный внутренний offset около 15 000.
После seed скрипт завершал процесс, отдельная фаза удаляла только derived каталоги этих
двух книг, затем процесс снова завершался. Verify подтвердил новый PID, авиарежим,
DARK/1,5, прежний режим/переключатель, точные исходные chapter/block/offset и пересозданный
spool/index. В PAGINATED готовая страница содержит anchor и имеет точный номер.
Исходные внешние cache files удалены до завершения процесса; app-local книги и Room позиции
сохранены. После проверки удалены только собственные записи, preferences и авиарежим восстановлены.

Все 12 основных отдельных фаз passed; дополнительный cleanup passed. Подготовительный timeout
Compose harness не входит в результат. Обычные skipped tests не считаются этим доказательством.
Доказательство: [process-death-v2.txt](evidence/process-death-v2.txt).
