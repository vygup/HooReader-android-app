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
