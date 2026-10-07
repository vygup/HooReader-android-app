# Проверка указателей: T037–T042

2026-10-03, Pixel_10 AVD / API 37. Устройство и исходный системный масштаб: [device.txt](device.txt).

24 успешных запуска сценариев, 48 снимков. Реальная шкала Android задаётся через `settings put system font_scale`; тест сверяет `resources.configuration.fontScale`. Скрипт восстанавливает исходное значение при завершении, включая ошибку.

Автоматически проверены глава первого видимого фрагмента, точный номер, отсутствие чужого показателя, переходы через границу главы и содержание, повторное открытие, границы полос относительно viewport, отсутствие обрезки цифр и неизменность anchor/generation/page slices при касаниях панелей.

Визуально просмотрены 10 исходных снимков: оба режима, обе темы и ориентации при системном максимуме, включая крайние масштабы чтения. Указатели меньше основного текста, менее контрастны и находятся в отдельных полосах. Название сокращается с ellipsis, цифры различимы. Это техническая проверка; SC-007 с пятью участниками не проводился.

Повтор после сборки APK:

```sh
ANDROID_HOME=/path/to/sdk ANDROID_SERIAL=emulator-5554 bash scripts/verify-reading-indicators.sh
```

[Машинный отчёт и SHA-256](summary.json), [check/build/lint/detekt](T042-check.txt), [регрессия Android](T042-full-android.txt).

[T038 до исправления](T038-before.txt): тест обнаружил нижний указатель внутри reader_viewport. После интеграции общих измеренных полос — [8/8 UI](T041-ui.xml). Проверка цифр использует границы строки и отсутствие ellipsis/vertical overflow: `hasVisualOverflow` у короткого Compose Text в semantics сравнивает ширину абзаца с компактной шириной Text и даёт ложное срабатывание.

| Система / чтение | Формат | Режим | Тема | Портрет | Альбом |
|---|---|---|---|---|---|
| 1.0 / 1.0 | epub | VERTICAL | light | [PNG](epub-VERTICAL-light-system1.0-reading1.0-orientation1.png) | [PNG](epub-VERTICAL-light-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | epub | VERTICAL | dark | [PNG](epub-VERTICAL-dark-system1.0-reading1.0-orientation1.png) | [PNG](epub-VERTICAL-dark-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | epub | PAGINATED | light | [PNG](epub-PAGINATED-light-system1.0-reading1.0-orientation1.png) | [PNG](epub-PAGINATED-light-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | epub | PAGINATED | dark | [PNG](epub-PAGINATED-dark-system1.0-reading1.0-orientation1.png) | [PNG](epub-PAGINATED-dark-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | fb2 | VERTICAL | light | [PNG](fb2-VERTICAL-light-system1.0-reading1.0-orientation1.png) | [PNG](fb2-VERTICAL-light-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | fb2 | VERTICAL | dark | [PNG](fb2-VERTICAL-dark-system1.0-reading1.0-orientation1.png) | [PNG](fb2-VERTICAL-dark-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | fb2 | PAGINATED | light | [PNG](fb2-PAGINATED-light-system1.0-reading1.0-orientation1.png) | [PNG](fb2-PAGINATED-light-system1.0-reading1.0-orientation2.png) |
| 1.0 / 1.0 | fb2 | PAGINATED | dark | [PNG](fb2-PAGINATED-dark-system1.0-reading1.0-orientation1.png) | [PNG](fb2-PAGINATED-dark-system1.0-reading1.0-orientation2.png) |
| 2.0 / 0.75 | epub | VERTICAL | light | [PNG](epub-VERTICAL-light-system2.0-reading0.75-orientation1.png) | [PNG](epub-VERTICAL-light-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | epub | VERTICAL | dark | [PNG](epub-VERTICAL-dark-system2.0-reading0.75-orientation1.png) | [PNG](epub-VERTICAL-dark-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | epub | PAGINATED | light | [PNG](epub-PAGINATED-light-system2.0-reading0.75-orientation1.png) | [PNG](epub-PAGINATED-light-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | epub | PAGINATED | dark | [PNG](epub-PAGINATED-dark-system2.0-reading0.75-orientation1.png) | [PNG](epub-PAGINATED-dark-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | fb2 | VERTICAL | light | [PNG](fb2-VERTICAL-light-system2.0-reading0.75-orientation1.png) | [PNG](fb2-VERTICAL-light-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | fb2 | VERTICAL | dark | [PNG](fb2-VERTICAL-dark-system2.0-reading0.75-orientation1.png) | [PNG](fb2-VERTICAL-dark-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | fb2 | PAGINATED | light | [PNG](fb2-PAGINATED-light-system2.0-reading0.75-orientation1.png) | [PNG](fb2-PAGINATED-light-system2.0-reading0.75-orientation2.png) |
| 2.0 / 0.75 | fb2 | PAGINATED | dark | [PNG](fb2-PAGINATED-dark-system2.0-reading0.75-orientation1.png) | [PNG](fb2-PAGINATED-dark-system2.0-reading0.75-orientation2.png) |
| 2.0 / 2.0 | epub | VERTICAL | light | [PNG](epub-VERTICAL-light-system2.0-reading2.0-orientation1.png) | [PNG](epub-VERTICAL-light-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | epub | VERTICAL | dark | [PNG](epub-VERTICAL-dark-system2.0-reading2.0-orientation1.png) | [PNG](epub-VERTICAL-dark-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | epub | PAGINATED | light | [PNG](epub-PAGINATED-light-system2.0-reading2.0-orientation1.png) | [PNG](epub-PAGINATED-light-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | epub | PAGINATED | dark | [PNG](epub-PAGINATED-dark-system2.0-reading2.0-orientation1.png) | [PNG](epub-PAGINATED-dark-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | fb2 | VERTICAL | light | [PNG](fb2-VERTICAL-light-system2.0-reading2.0-orientation1.png) | [PNG](fb2-VERTICAL-light-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | fb2 | VERTICAL | dark | [PNG](fb2-VERTICAL-dark-system2.0-reading2.0-orientation1.png) | [PNG](fb2-VERTICAL-dark-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | fb2 | PAGINATED | light | [PNG](fb2-PAGINATED-light-system2.0-reading2.0-orientation1.png) | [PNG](fb2-PAGINATED-light-system2.0-reading2.0-orientation2.png) |
| 2.0 / 2.0 | fb2 | PAGINATED | dark | [PNG](fb2-PAGINATED-dark-system2.0-reading2.0-orientation1.png) | [PNG](fb2-PAGINATED-dark-system2.0-reading2.0-orientation2.png) |
