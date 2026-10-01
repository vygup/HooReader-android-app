#!/usr/bin/env bash
set -euo pipefail

# Run after assembling both debug APKs; this acceptance check uses an emulator only.
ADB="${ADB:-adb}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
case "$SERIAL" in
  emulator-*) ;;
  *) echo 'Для этой проверки выберите Android emulator через ANDROID_SERIAL.' >&2; exit 1 ;;
esac
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUNNER='com.hooreader.test/androidx.test.runner.AndroidJUnitRunner'
TEST='com.hooreader.reader.ProcessDeathAcceptanceTest'

"$ADB" -s "$SERIAL" install -r "$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" -s "$SERIAL" install -r "$REPO_ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

run_phase() {
  local phase="$1"
  local result
  result="$("$ADB" -s "$SERIAL" shell am instrument -w -e class "$TEST#$phase" -e processDeathPhase "$phase" "$RUNNER")"
  printf '%s\n' "$result"
  [[ "$result" == *'OK (1 test)'* ]]
}

run_phase seed
previous_mode="$("$ADB" -s "$SERIAL" shell settings get global airplane_mode_on | tr -d '\r')"
restore_airplane_mode() {
  if [[ "$previous_mode" != '1' ]]; then
    "$ADB" -s "$SERIAL" shell cmd connectivity airplane-mode disable
  fi
}
trap restore_airplane_mode EXIT
"$ADB" -s "$SERIAL" shell cmd connectivity airplane-mode enable
"$ADB" -s "$SERIAL" shell am force-stop com.hooreader
run_phase verify
