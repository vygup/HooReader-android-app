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

## Открытые критерии и продолжение

По указанию пользователя работа остановлена после T036. T001–T036 отмечены выполненными;
T037–T065 не начаты. Следующая — T037: ReadingIndicatorResolverTest.kt, затем T038 и US3.
Блокированных задач в выполненном диапазоне нет; физический performance стенд и API 26
недоступны и остаются незавершёнными доказательствами следующих этапов.

SC-001/002 полный приёмочный двухрежимный gesture прогон, стилизация/визуальная матрица
указателей US3, SC-005/006, SC-008, SC-007 с реальными участниками, OS process-death T059,
обновление v1→v2 и RC остаются следующих задач. Существующие «Готово» и прямой выход
соответствуют текущей промежуточной фазе: их предусмотренные изменения — US4/US5.
Reviewer-owned checklists не изменены.
