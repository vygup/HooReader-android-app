# Проверка второго RC — T063

Дата: 2026-10-09. **Частично выполнено; NOT_VERIFIED_API26.**

`./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest` — PASS:
Detekt, Android Lint, 115 unit tests в каждом из debug/release (230 выполнений),
оба debug APK собраны. [Лог](evidence/validation/check-build.txt),
[JUnit XML](evidence/validation/unit/). Внешние release-smoke tests также собраны,
их Detekt пройден.

`./gradlew :app:connectedDebugAndroidTest` — PASS на Pixel_10 AVD, Android 17/API 37,
arm64-v8a, google play 16 KB image. XML: 65 tests, 0 failures/errors, 17 skipped,
48 выполнены. Gradle progress ошибочно выводит 82, считая skipped дважды; итог
взят из XML, а не строки progress. [Лог](evidence/validation/connected-api37.txt),
[XML](evidence/validation/connected-api37.xml).

Skipped — специальные opt-in font-scale (8), process-death (4), performance (4),
panel timing (1). Font-scale и process-death отдельно выполнены в T058/T059;
новый UI timing — диагностически в T061. Старые standalone performance paths
в общем прогоне не запускались, их skipped не означает принятие метрик.

API 26: образ не установлен, физическое устройство отсутствует; прогон не выполнен.
API 37 удовлетворяет части «API 34+», но не заменяет API 26. T063 оставлена
неотмеченной до отдельного прогона на минимальной поддерживаемой версии.
Первоначальный timeout экспериментального process-death harness сохранён как
`process-death-initial-harness-timeout.txt`; исправленный opt-in T059 прошёл отдельно.

Продуктовая приёмка остаётся незавершённой: SC-004 требует физического стенда
и полного протокола, SC-007 — пяти участников. Успешные сборки это не заменяют.

## Дополнение SC-001

При финальной сверке обнаружен пробел: 100 жестов покрывались только вертикальным
тестом. PagedReaderTest расширен на 100 последовательных жестов для каждого формата:
свайпы в обе стороны, отмена, возврат к точке касания после touch slop и жест на границе.
После каждого проверяются страница и отсутствие панелей. Также 20 касаний выполняются
с движением ниже slop; позиция, layout и viewport остаются неизменными.

Повторены `:app:check`, обе debug сборки и четыре PagedReaderTest на API 37:
4 passed, 0 skipped/failures. [Лог](evidence/validation/paged-100-api37.txt),
[XML](evidence/validation/paged-100-api37.xml). Production-код и подписанный RC
после сборки не менялись. Основной XML из 65 тестов сохранён отдельно;
четыре повторных теста не прибавляются к нему как новые уникальные сценарии.
