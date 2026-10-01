# Сборка HooReader

## Окружение

- JDK 17–21 (для проверки проекта используется JDK 21).
- Android SDK: платформа API 36 и Build Tools 35.0.0.
- Android Studio либо командная строка с `ANDROID_HOME`, указывающим на SDK.
  Вместо переменной можно задать `sdk.dir` в локальном `local.properties`.

Gradle 8.14.3 загружается через Wrapper; SHA-256 дистрибутива зафиксирован в
`gradle/wrapper/gradle-wrapper.properties`. Устанавливать Gradle отдельно не требуется.
Сборка впервые требует доступа к Google Maven, Maven Central и Gradle Plugin Portal.

```sh
./gradlew help
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:testDebugUnitTest
```

При запуске из терминала `JAVA_HOME` должен указывать на совместимый JDK. В Android Studio
тот же JDK необходимо выбрать в настройках Gradle. Java/Kotlin bytecode приложения имеет
целевую версию 17; минимальный Android API — 26.

## Версии инструментов

Android Gradle Plugin 8.11.1 и Kotlin 2.1.21 закреплены в `build.gradle.kts`.
Compose compiler использует ту же версию, что и Kotlin.

Источники совместимости:

- [Android Gradle Plugin 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes).
- [Readium Kotlin Toolkit 3.1.2](https://github.com/readium/kotlin-toolkit/tree/3.1.2).
- [Gradle Wrapper](https://docs.gradle.org/8.14.3/userguide/gradle_wrapper.html).

## Зависимости и тестирование

Модуль подключает Compose BOM 2025.08.01, Navigation Compose 2.9.3, Lifecycle 2.9.3,
Room 2.7.2, DataStore Preferences 1.1.7, Coroutines 1.10.2 и Readium 3.1.2.
Для EPUB подключены только `readium-shared` и `readium-streamer`; UI использует Compose.
Для Readium включён core library desugaring с `desugar_jdk_libs` 2.1.5.

Room обрабатывается через KSP 2.1.21-2.0.1. Схемы будущей базы экспортируются в
`app/schemas/`; их нужно сохранять в Git для проверки миграций.

JUnit, coroutine-test, Room testing и Robolectric подключены для локальных тестов.
AndroidJUnitRunner, Espresso и Compose UI testing подключены для instrumentation tests.
Для запуска последних требуется устройство или эмулятор:

```sh
./gradlew :app:connectedDebugAndroidTest
```

В фазе настройки тестовых сценариев ещё нет. Команда `assembleDebugAndroidTest` проверяет
сборку тестового APK и совместимость зависимостей, но не запускает тесты на устройстве.

## Форматирование и статический анализ

Detekt 1.23.8 проверяет Kotlin-код приложения, локальных и instrumentation tests.
Правила `detekt-formatting` используют ktlint с Android style. Имена Compose-функций
с аннотацией `@Composable` могут начинаться с заглавной буквы.
Сгенерированный код не входит в список исходников для основной задачи `detekt`.
Настройки хранятся в `config/detekt/detekt.yml` и `.editorconfig`.

```sh
./gradlew :app:detekt
./gradlew :app:detekt --auto-correct
./gradlew :app:check
```

Обычная проверка не исправляет файлы автоматически; исправление выполняется только при
передаче `--auto-correct`. Задача `check` включает detekt и Android Lint; нарушения
останавливают проверку. Отчёты находятся в `app/build/reports/`.
При автоисправлении detekt может вернуть ошибку для уже исправленных нарушений:
после него нужно повторить обычную команду `detekt`.

- [Detekt Gradle Plugin](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/).

## Проверка фазы 1

На установленном Android SDK с API 36 и Build Tools 35.0.0, с JDK 21 выполнена команда:

```sh
./gradlew :app:check :app:assembleDebug :app:assembleDebugAndroidTest
```

Все задачи завершились успешно. Detekt не обнаружил нарушений. Проверено обнаружение
ошибок форматирования и их исправление на временном Kotlin-файле; после проверки файл удалён.
Android Lint завершился без ошибок, с 27 предупреждениями: доступны более новые версии
зависимостей и Gradle, отсутствует launcher icon, не заданы отдельные правила переноса данных
для Android 12+. В manifest резервное копирование отключено через `allowBackup="false"`.

Локальные тесты имеют статус `NO-SOURCE`: тестовые сценарии запланированы в следующих фазах.
Запуск на устройстве не проверен, поскольку подключённого устройства или запущенного эмулятора
не было. Эта проверка подтверждает настройку проекта, а не готовность функций чтения.
