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
exit $fail
