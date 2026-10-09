# Выпуск HooReader

## Текущий RC 2.0.0

Текущие `build-release.py` и `verify-release.py` работают с **2.0.0**, versionCode 2,
и сохраняют файлы в `app/build/release/2.0.0/`. Подпись и environment variables
остались от первого выпуска. [Notes RC](releases/2.0.0.md). Проверка обновления:
`python3 scripts/verify-reader-upgrade.py` на одном временном `-read-only` emulator,
с локальными подписанными APK обеих версий; между v1 и v2 данные не очищаются.

Для RC выполнены сборка APK/AAB, проверка сертификатов и внешний release smoke.
SHA-256 проверяется командой `shasum -a 256 -c SHA256SUMS.txt` из каталога 2.0.0.
Подготовка RC не означает приёмку: SC-004 остаётся открытым.
T062/SC-007 пройдена по подтверждению пользователя.
Совместимость проверена общим UI-набором на API 26 и API 37; T063 закрыта.
Новый tag, push и GitHub Release в рамках этой задачи не создаются.

После сборки и проверок точного APK комплект готовится командой:

```sh
python3 scripts/package-release.py
```

Скрипт требует чистого зафиксированного состояния при сборке и совпадения SHA-256
APK с успешными smoke и upgrade. Архив `app/build/release/2.0.0/HooReader-2.0.0-release.zip`
содержит только APK, AAB, `SHA256SUMS.txt`, `RELEASE_NOTES.md`, `INSTALLATION.md`
и `RELEASE_MANIFEST.json`. Ключи подписи и тестовые данные в него не входят.
Рядом создаётся `HooReader-2.0.0-release.zip.sha256`; его можно проверить командой
`shasum -a 256 -c HooReader-2.0.0-release.zip.sha256` из каталога комплекта.
Manifest сохраняет исходный коммит, публичный сертификат, hashes файлов и состояние
приёмки. Пока SC-004 открыт, статус комплекта — `RC_PENDING_ACCEPTANCE`.
[Отчёт подготовки 2.0.0](releases/2.0.0-validation.md).

Разделы о версии 1.0.0 ниже сохраняют историю первого выпуска; текущие команды
сборки и smoke используют каталог 2.0.0 вместо исторического 1.0.0.

## Первый выпуск

Версия 1.0.0 (versionCode 1) — первый offline MVP. Release notes:
[1.0.0.md](releases/1.0.0.md); план: [1.0.0-plan.md](releases/1.0.0-plan.md).
Отчёт проверки: [1.0.0-validation.md](releases/1.0.0-validation.md).
GitHub remote проекта — `git@github.com:vygup/HooReader.git`.
Готовые APK/AAB, подписи, smoke result и checksums находятся в `app/build/release/1.0.0/`.

## Ключ подписи

Ключ первого выпуска создан локально: `.signing/hooreader-release.p12`, alias `hooreader`.
Пароль хранится в `.signing/password`; каталог имеет права 700, оба файла — 600 и исключены из Git.
**Сохраните защищённую резервную копию этих файлов отдельно от проекта.** Скрипт генерации
не заменяет существующий ключ. Не прикладывайте `.signing/` к GitHub Release и не публикуйте пароль.

Обновления APK требуют того же ключа подписи. Потеря ключа вне Play App Signing лишает возможности
выпускать обычные обновления установленного приложения.
Источник: [Android — Sign your app](https://developer.android.com/studio/publish/app-signing).

Для новой рабочей машины или CI передаются только environment variables:

```text
HOOREADER_KEYSTORE_PATH
HOOREADER_KEYSTORE_PASSWORD
HOOREADER_KEY_ALIAS
HOOREADER_KEY_PASSWORD
```

Если переменные не заданы, `build-release.py` использует локальные `.signing/` файлы и передаёт
пароли дочернему Gradle-процессу через окружение. Пароли не включаются в CLI arguments и вывод.
Неполная конфигурация отклоняется. Debug signing не используется как fallback для release.

## Сборка

```sh
python3 scripts/build-release.py
```

Команда запускает `:app:check`, собирает release APK/AAB, проверяет applicationId/version,
APK certificate через apksigner и AAB через jarsigner. Формирует именованные файлы и SHA-256.
`create-release-key.py` используется **только один раз** для нового проекта без существующей подписи.
Уже созданный ключ этой версии повторно генерировать нельзя.

## Проверка реального release APK

Отдельный модуль `release-smoke` — внешний тестовый процесс; в release APK он не входит.
Тест работает с non-debuggable приложением через UI Automator и настоящий системный File Picker.
[Назначение UI Automator](https://developer.android.com/training/testing/other-components/ui-automator).

Проверка удаляет/очищает тестовую установку, поэтому runner разрешает ровно один эмулятор,
запущенный с `-read-only`: изменения остаются во временном overlay, постоянные данные AVD сохраняются.
На физическом устройстве runner работать отказывается. Перед запуском основной постоянный AVD
должен быть закрыт; выбранные ABI/system image должны быть установлены.

```sh
"$ANDROID_HOME/emulator/emulator" -avd Pixel_10 -read-only -no-snapshot -gpu host
./gradlew :release-smoke:detekt :release-smoke:assembleDebug :release-smoke:assembleDebugAndroidTest
ADB="$ANDROID_HOME/platform-tools/adb" ANDROID_SERIAL=emulator-5554 python3 scripts/verify-release.py
```

Проверяются package/version/non-debuggable, EPUB и Windows-1251 FB2, локальное чтение,
переход главы, тёмная тема и размер текста, завершение процесса/авиарежим, повторное открытие,
удаление записи при сохранении выбранного исходного файла. Предыдущий авиарежим возвращается.
Результат сохраняется в `app/build/release/2.0.0/release-smoke.txt`.

## Контроль артефактов

```sh
cd app/build/release/2.0.0
shasum -a 256 -c SHA256SUMS.txt
```

`apk-signature.txt` содержит публичный SHA-256 сертификата. `aab-signature.txt` содержит результат
JAR verification; предупреждения о self-signed certificate ожидаемы для Android app signing key.
Сборка остаётся release/non-debuggable, без debug Activity/Provider и INTERNET/storage permissions.
Порог плавности из Phase 7 не объявляется пройденным; ограничения перечисляются в notes.

## Публикация

После успешной проверки source commit фиксируется tag `v1.0.0`. Для публикации нужен доступ
к `vygup/HooReader` на GitHub. Tag и release создаются только без перезаписи существующего выпуска.

```sh
git push origin main
git push origin v1.0.0
```

На странице GitHub Releases выбираются существующий tag `v1.0.0`, заголовок `HooReader 1.0.0`
и текст из `docs/releases/1.0.0.md`. Прикладываются **только** APK, AAB и `SHA256SUMS.txt`.
Локальный архив `HooReader-1.0.0-release.zip` содержит эти публичные артефакты и notes для передачи.

AAB сам по себе не является опубликованным приложением в Google Play: необходимы аккаунт,
настройка Play App Signing, карточка и проверки магазина. Эти действия в первом локальном
выпуске не выполнены. Подписание AAB не означает одобрение магазина.
