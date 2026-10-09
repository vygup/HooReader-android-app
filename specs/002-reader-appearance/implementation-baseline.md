# Исходное состояние реализации второго релиза

Дата: 2026-10-02. Ветка: `release/v2.0.0`. Исходный код первого релиза: `2f1b924`;
корпус T001: `40a9f6c`. Рабочее дерево до начала реализации было чистым.

## Инструментарий

macOS Darwin 25.6.0 arm64; OpenJDK JBR 21.0.11. Gradle Wrapper 8.14.3,
AGP 8.11.1, Kotlin/Compose compiler 2.1.21, KSP 2.1.21-2.0.1,
Compose BOM 2025.08.01; compileSdk/targetSdk 36, minSdk 26.
Версии зависимостей не изменены. Ignore-файлы проверены: `.gitignore` покрывает
Gradle/Kotlin/build/editor/log/temp/секреты, wrapper JAR разрешён явно;
другие технологические ignore-файлы этому Android-проекту не нужны.

## Исходная проверка

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks
```

Успешно: 113 выполненных Gradle tasks, 38 секунд. Unit: 56 debug + 56 release,
0 failures/errors/skipped. Detekt пройден. Android Lint: 0 ошибок, 25 предупреждений.
Оба debug APK собраны. Полный журнал: [baseline-gradle.txt](evidence/validation/baseline-gradle.txt).

Существующие предупреждения: SDK XML v4 при tooling parser v3; устаревающие Gradle API
(несовместимость с будущим Gradle 9); lint warnings (включая доступные обновления зависимостей).
Обновления версий ради устранения предупреждений не выполнялись.

## Android-стенды

ADB 1.0.41 / platform-tools 37.0.1. Физические устройства не подключены.
Запущен существующий AVD `Pixel_10`: `emulator-5554`, `sdk_gphone16k_arm64`, API 37,
arm64-v8a, page size 16 KB, RAM AVD 2048 MB, дисплей 1080×2424, density 420.
Других AVD (в частности API 26) нет. Эмулятор доступен для функциональных проверок;
SC-004 на физическом стенде имеет статус **NOT_VERIFIED_DEVICE**.
Instrumentation APK собран, исполнение Android tests не входит в исходный прогон T002.

Ограничения sandbox потребовали разрешения на Gradle cache, ADB и запуск эмулятора;
после разрешения tooling работает. Это не дефекты приложения.

## Контрольная точка фазы 1

T001–T002 завершены. Два малых fixtures воспроизводимы (проверены SHA-256/XML/ZIP),
два нагрузочных ≥20 MB сгенерированы вне Git/APK в `/tmp/hooreader-v2-corpus`.
Исходные unit/static/build проверки прошли, новых регрессий не обнаружено.
Следующая задача — T003: контракт последовательного потока блоков.
