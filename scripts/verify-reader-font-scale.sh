#!/usr/bin/env bash
# Полная SC-008 матрица на выделенной тестовой установке; пользовательские данные не очищаются.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-${ANDROID_HOME:?Set ANDROID_HOME or ADB}/platform-tools/adb}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
REPORT_DIR="${1:-specs/002-reader-appearance/evidence/font-scale}"
mkdir -p "$REPORT_DIR"
original_scale="$("$ADB" -s "$SERIAL" shell settings get system font_scale | tr -d '\r')"
restore_scale() {
    if [[ "$original_scale" == null ]]; then
        "$ADB" -s "$SERIAL" shell settings delete system font_scale >/dev/null
    else
        "$ADB" -s "$SERIAL" shell settings put system font_scale "$original_scale"
    fi
}
trap restore_scale EXIT
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
{
    date -u '+%Y-%m-%dT%H:%M:%SZ'
    "$ADB" -s "$SERIAL" shell getprop ro.product.model
    "$ADB" -s "$SERIAL" shell getprop ro.build.fingerprint
    "$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk
    "$ADB" -s "$SERIAL" shell wm size
    "$ADB" -s "$SERIAL" shell wm density
    echo "original_font_scale=$original_scale"
} > "$REPORT_DIR/device.txt"
for system_scale in 1.0 1.5 2.0; do
    for reading_scale in 0.75 2.0; do
        "$ADB" -s "$SERIAL" shell settings put system font_scale "$system_scale"
        result="$REPORT_DIR/system${system_scale}-reading${reading_scale}.txt"
        "$ADB" -s "$SERIAL" shell am instrument -w -r \
            -e class com.hooreader.reader.ReaderFontScaleAcceptanceTest \
            -e fontScaleAcceptance true -e systemScale "$system_scale" -e readingScale "$reading_scale" \
            com.hooreader.test/androidx.test.runner.AndroidJUnitRunner > "$result"
        "$ADB" -s "$SERIAL" exec-out run-as com.hooreader tar -C files/font-scale-evidence -cf - . |
            tar -xf - -C "$REPORT_DIR"
        if ! rg -q '^OK \(8 tests\)' "$result"; then
            cat "$result"
            exit 1
        fi
        echo "PASS: system=$system_scale reading=$reading_scale (16 combinations)"
    done
done
