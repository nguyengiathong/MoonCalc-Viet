#!/usr/bin/env bash
# Usage: smoke-test.sh <dir with the APK> <output dir>
# Expects a booted emulator/device on adb.
set -euo pipefail
apk=$(ls "$1"/*.apk | head -1)
out=$2
pkg=io.github.nguyengiathong.mooncalc
mkdir -p "$out"

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 20

ok=""
for attempt in 1 2 3 4 5; do
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$out/ui.xml" > /dev/null; then
    if grep -q 'MoonCalc' "$out/ui.xml"; then ok=1; break; fi
  fi
  sleep 5
done
adb exec-out screencap -p > "$out/screen.png"
adb logcat -d -v tag -s MoonCalc:V AndroidRuntime:E > "$out/logcat.txt" || true

echo "--- page text seen by accessibility ---"
grep -o 'text="[^"]\+"' "$out/ui.xml" | head -40 || true
echo "--- logcat ---"
cat "$out/logcat.txt"

fail=0
[ -n "$ok" ] || { echo "::error::Page title not found on screen"; fail=1; }
# phase name is computed by the page's script, so it proves the JavaScript ran
grep -Eq 'Trăng (mới|lưỡi|khuyết|tròn)|huyền' "$out/ui.xml" || { echo "::error::Moon phase not rendered"; fail=1; }
grep -q '^E/MoonCalc' "$out/logcat.txt" && { echo "::error::JavaScript errors in console"; fail=1; }
grep -q 'FATAL EXCEPTION' "$out/logcat.txt" && { echo "::error::App crashed"; fail=1; }
adb shell pidof "$pkg" > /dev/null || { echo "::error::App is not running"; fail=1; }

# Android 15+ draws every app edge to edge: the page must sit between the status bar and the
# navigation bar, not under them.
bounds=$(grep -o 'class="android.webkit.WebView"[^>]*bounds="[^"]*"' "$out/ui.xml" | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' || true)
screen_h=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
echo "WebView bounds: ${bounds:-not found}, screen height: $screen_h"
if [[ "$bounds" =~ ^\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$ ]]; then
  [ "${BASH_REMATCH[2]}" -gt 0 ] || { echo "::error::Page starts under the status bar"; fail=1; }
  [ "${BASH_REMATCH[4]}" -lt "$screen_h" ] || { echo "::error::Page runs under the navigation bar"; fail=1; }
else
  echo "::error::WebView not found in the UI dump"; fail=1
fi
exit $fail
