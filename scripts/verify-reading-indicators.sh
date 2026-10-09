#!/usr/bin/env bash
# Матрица US3 на выделенном Android-стенде; исходный системный масштаб восстанавливается.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-${ANDROID_HOME:?Set ANDROID_HOME or ADB}/platform-tools/adb}"
REPORT_DIR="${1:-specs/002-reader-appearance/evidence/indicators}"
mkdir -p "$REPORT_DIR"
original_scale="$("$ADB" shell settings get system font_scale | tr -d '\r')"
restore_scale() {
    if [[ "$original_scale" == null ]]; then
        "$ADB" shell settings delete system font_scale >/dev/null
    else
        "$ADB" shell settings put system font_scale "$original_scale"
    fi
}
trap restore_scale EXIT
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
{
    date -u '+%Y-%m-%dT%H:%M:%SZ'
    "$ADB" shell getprop ro.product.model
    "$ADB" shell getprop ro.build.fingerprint
    "$ADB" shell getprop ro.build.version.sdk
    "$ADB" shell wm size
    "$ADB" shell wm density
    echo "original_font_scale=$original_scale"
} > "$REPORT_DIR/device.txt"
for pair in '1.0 1.0' '2.0 0.75' '2.0 2.0'; do
    read -r system_scale reading_scale <<< "$pair"
    "$ADB" shell settings put system font_scale "$system_scale"
    result="$REPORT_DIR/system${system_scale}-reading${reading_scale}.txt"
    "$ADB" shell am instrument -w -r \
        -e class com.hooreader.reader.ReadingIndicatorsTest \
        -e indicatorSystemScale "$system_scale" -e indicatorReadingScale "$reading_scale" \
        com.hooreader.test/androidx.test.runner.AndroidJUnitRunner > "$result"
    if ! rg -q '^OK \([0-9]+ tests?\)' "$result"; then
        cat "$result"
        exit 1
    fi
    echo "PASS: system=$system_scale reading=$reading_scale"
    "$ADB" exec-out run-as com.hooreader tar -C files/indicator-evidence -cf - . | tar -xf - -C "$REPORT_DIR"
done
