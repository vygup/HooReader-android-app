# Проверка второго RC — T063

Дата: 2026-10-09. **T063 выполнена: API 26 и API 37 — PASS.**

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

API 26: установлен официальный Google APIs ARM64 image и создан отдельный AVD
HooReader_API26, Android 8.0.0, 1080×1920, density 420. Полный `check`, обе debug
сборки и `connectedDebugAndroidTest` прошли: XML — 65 tests, 48 passed, 17 opt-in
skipped, 0 failures/errors. [Лог](evidence/validation/connected-api26.txt),
[XML](evidence/validation/connected-api26.xml), [стенд](evidence/validation/api26-device.json),
[установка SDK](evidence/validation/api26-sdk-install.txt).
API 37 удовлетворяет части «API 34+»; API 26 проверен отдельно.
Первоначальный timeout экспериментального process-death harness сохранён как
`process-death-initial-harness-timeout.txt`; исправленный opt-in T059 прошёл отдельно.

Продуктовая приёмка остаётся незавершённой: SC-004 требует физического стенда
и полного протокола. SC-007 позднее подтверждён пользователем; источник и доступные
подробности записаны в [usability-results.md](usability-results.md).

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

## Исправления совместимости тестов API 26

Первый полный прогон выявил два ограничения тестового инструмента, а не поведения
читалки: внешний MotionEvent создавался с SOURCE_UNKNOWN, а Compose captureToImage
не поддерживает dialog windows на API ниже 28. В HooReaderNavHostTest источник
изменён на SOURCE_TOUCHSCREEN, доставка каждого события проверяется. В AppSettingsTest
на API 26–27 используется UiAutomation.takeScreenshot; проверки видимости и поведения
диалога сохраняются. На API 28+ остаётся прежний Compose capture.

Первый прогон (2 failures) и промежуточный после исправления касания (1 failure)
сохранены в [attempts](evidence/validation/attempts/). Финальный полный API 26 прогон
прошёл без ошибок. После обоих изменений повторены два затронутых класса на API 37:
4 tests passed, 0 skipped/failures/errors. [Лог](evidence/validation/exit-regression-api37.txt),
[XML](evidence/validation/exit-regression-api37.xml). Production-код и подписанные
APK/AAB не менялись; пересборка RC по этим изменениям не требуется.

T063 отмечена выполненной, T062 закрыта по подтверждению пользователя. Аппаратный
SC-004 остаётся незавершённым условием приёмки; пропущенные opt-in tests не объявляются
выполненными на API 26.


## Решение о выпуске 2026-10-10

Пользователь разрешил выпуск 2.0.0 без подтверждения SC-004: «Релизим без этой проверки».
Предыдущие результаты выше сохраняют историю проверок. Выпуск принят с исключением,
действующим только для 2.0.0 и истекающим перед следующим выпуском. Метрика SC-004
не объявляется пройденной. [Решение](evidence/release/acceptance-exception.json).
