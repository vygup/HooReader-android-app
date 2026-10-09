#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ "${1:-}" != '--legacy' ]]; then
  exec python3 "$ROOT/scripts/reader-performance-v2.py" "$@"
fi
shift
ADB="${ADB:-adb}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
OUTPUT="${1:-/tmp/hooreader-performance.json}"
# Run against an empty test installation. The test deletes only the books it creates.
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" -s "$SERIAL" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
result="$("$ADB" -s "$SERIAL" shell am instrument -w \
  -e class com.hooreader.acceptance.ReaderPerformanceTest -e phase7Performance true \
  com.hooreader.test/androidx.test.runner.AndroidJUnitRunner)"
printf '%s\n' "$result"
[[ "$result" == *'OK (1 test)'* ]]
"$ADB" -s "$SERIAL" exec-out run-as com.hooreader cat files/phase7-performance.json > "$OUTPUT"
printf 'Raw metrics: %s\n' "$OUTPUT"
