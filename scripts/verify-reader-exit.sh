#!/usr/bin/env bash
# Проверки US5 на выделенном Android-стенде; исходный системный масштаб восстанавливается.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-${ANDROID_HOME:?Set ANDROID_HOME or ADB}/platform-tools/adb}"
REPORT_DIR="${1:-specs/002-reader-appearance/evidence/exit}"
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
classes="com.hooreader.navigation.HooReaderNavHostTest#realReaderPrioritizesMenusCancelsDialogAndRetriesFailedExit,com.hooreader.settings.AppSettingsTest"
for scale in 1.0 2.0; do
    "$ADB" shell settings put system font_scale "$scale"
    result="$REPORT_DIR/T057-system${scale}.txt"
    "$ADB" shell am instrument -w -r -e class "$classes" \
        com.hooreader.test/androidx.test.runner.AndroidJUnitRunner > "$result"
    if ! rg -q '^OK \(2 tests\)' "$result"; then
        cat "$result"
        exit 1
    fi
    "$ADB" exec-out run-as com.hooreader tar -C files/exit-evidence -cf - . | tar -xf - -C "$REPORT_DIR"
    echo "PASS: US5 system_font_scale=$scale"
done
