#!/usr/bin/env bash
set -euo pipefail
ADB="${ADB:-adb}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
OUTPUT="${1:-/tmp/hooreader-import-corpus.json}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
case "$SERIAL" in
  emulator-*) ;;
  *) echo 'Offline corpus использует выделенный emulator через ANDROID_SERIAL.' >&2; exit 1 ;;
esac
mkdir -p "$(dirname "$OUTPUT")"
previous_mode="$("$ADB" -s "$SERIAL" shell settings get global airplane_mode_on | tr -d '\r')"
restore_airplane_mode() {
  if [[ "$previous_mode" != '1' ]]; then
    "$ADB" -s "$SERIAL" shell cmd connectivity airplane-mode disable
  fi
}
trap restore_airplane_mode EXIT
"$ADB" -s "$SERIAL" shell cmd connectivity airplane-mode enable
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
result="$("$ADB" -s "$SERIAL" shell am instrument -w \
  -e class com.hooreader.acceptance.ImportCorpusTest \
  com.hooreader.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result"
[[ "$result" == *'OK (1 test)'* ]]
"$ADB" -s "$SERIAL" exec-out run-as com.hooreader cat files/phase7-corpus.json > "$OUTPUT"
printf 'Corpus results: %s\n' "$OUTPUT"
