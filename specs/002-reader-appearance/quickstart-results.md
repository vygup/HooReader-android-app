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

## Открытые критерии

SC-001/SC-002 подтверждены для текущего вертикального US1 пути; постраничный путь ещё не реализован.
SC-003, SC-005/006, указатели US3, полная SC-008 матрица и SC-007 user study ожидают своих задач.
SC-004 остаётся открытым: физического устройства нет; ранняя пагинация превышает ≤1 s,
включая сохранённые выбросы [T013](pagination-probe-optimized-results.md). API 26 отсутствует.
Релизная готовность не объявляется. Reviewer-owned checklists не изменены.
