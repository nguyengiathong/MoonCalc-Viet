#!/usr/bin/env bash
# Usage: smoke-test.sh <dir with the APK> <output dir>
# Expects a booted emulator/device on adb.
set -euo pipefail
apk=$(ls "$1"/*.apk | head -1)
out=$2
pkg=io.github.nguyengiathong.mooncalc
mkdir -p "$out"
fail=0
err() { echo "::error::$*"; fail=1; }

# dump <file>: the current screen's UI tree (WebView content included) into $out/<file>
dump() {
  for attempt in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$out/$1" > /dev/null; then return 0; fi
    sleep 3
  done
  return 1
}
# tap <file> <regex>: tap the centre of the first node in the dump whose attributes match
tap() {
  local b
  b=$(grep -oE "<node [^>]*($2)[^>]*>" "$out/$1" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ' || true)
  [ -n "$b" ] || return 1
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# bounds <file> <regex>: "x1 y1 x2 y2" of the first node in the dump whose attributes match
bounds() {
  grep -oE "<node [^>]*($2)[^>]*>" "$out/$1" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ' || true
}
# scroll_to <file> <regex>: page down until that node is fully on screen (dump left in <file>).
# Page Down rather than a swipe: the map and charts take drags for themselves. If a key press did
# not move anything, drag in the page's left margin instead.
scroll_to() {
  local b x1 y1 x2 y2 prev="" cur
  for attempt in $(seq 1 40); do
    dump "$1" || true
    b=$(bounds "$1" "$2")
    if [ -n "$b" ]; then
      read -r x1 y1 x2 y2 <<< "$b"
      if [ "$y1" -gt 30 ] && [ "$y2" -lt $(( screen_h - 30 )) ] && [ "$y2" -gt "$y1" ]; then return 0; fi
    fi
    cur=$(md5sum < "$out/$1" 2> /dev/null || true)
    if [ "$cur" = "$prev" ]; then
      adb shell input swipe 8 $(( screen_h * 3 / 4 )) 8 $(( screen_h / 4 )) 400
    else
      adb shell input keyevent KEYCODE_PAGE_DOWN
    fi
    prev=$cur
    sleep 1
  done
  echo "--- last screen while looking for: $2 ---"
  grep -oE '(text|content-desc|resource-id)="[^"]+"' "$out/$1" | head -25 || true
  return 1
}
# wait_for <file> <regex> [seconds]: dump until a node matching the regex shows up (dialogs can be slow)
wait_for() {
  local end=$(( SECONDS + ${3:-30} ))
  while [ $SECONDS -lt $end ]; do
    dump "$1" && grep -qE "$2" "$out/$1" && return 0
    sleep 2
  done
  echo "--- screen while waiting for: $2 ---"
  grep -oE '(text|content-desc|resource-id)="[^"]+"' "$out/$1" | head -25 || true
  return 1
}

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 20

# --- the page renders ---
for attempt in 1 2 3 4 5; do
  dump ui.xml && grep -q 'MoonCalc' "$out/ui.xml" && break
  sleep 5
done
adb exec-out screencap -p > "$out/screen.png"
echo "--- page text seen by accessibility ---"
grep -o 'text="[^"]\+"' "$out/ui.xml" | head -40 || true

grep -q 'MoonCalc' "$out/ui.xml" || err "Page title not found on screen"
# phase name is computed by the page's script, so it proves the JavaScript ran
grep -Eq 'Trăng (mới|lưỡi|khuyết|tròn)|huyền' "$out/ui.xml" || err "Moon phase not rendered"

# Android 15+ draws every app edge to edge: the page must sit between the status bar and the
# navigation bar, not under them.
bounds=$(grep -o 'class="android.webkit.WebView"[^>]*bounds="[^"]*"' "$out/ui.xml" | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' || true)
screen_h=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
echo "WebView bounds: ${bounds:-not found}, screen height: $screen_h"
if [[ "$bounds" =~ ^\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]$ ]]; then
  [ "${BASH_REMATCH[2]}" -gt 0 ] || err "Page starts under the status bar"
  [ "${BASH_REMATCH[4]}" -lt "$screen_h" ] || err "Page runs under the navigation bar"
else
  err "WebView not found in the UI dump"
fi

# --- "Định vị": Android's permission dialog, then a position from the emulator's GPS (Hà Nội) ---
adb shell cmd location set-location-enabled true || true
if tap ui.xml 'text="Định vị"'; then
  allow='permission_allow_foreground_only_button|text="While using the app"'
  if wait_for ui-permission.xml "$allow" && tap ui-permission.xml "$allow"; then
    located=""
    for attempt in $(seq 1 12); do
      adb emu geo fix 105.8542 21.0285 > /dev/null
      sleep 4
      if dump ui-located.xml && grep -q 'text="21\.02' "$out/ui-located.xml"; then located=1; break; fi
    done
    adb exec-out screencap -p > "$out/screen-located.png"
    echo "--- after locating ---"
    grep -oE 'text="[^"]*(Vị trí|vị trí|Định vị|21\.0|105\.8)[^"]*"' "$out/ui-located.xml" || true
    [ -n "$located" ] || err "Latitude from GPS did not reach the page"
    adb shell dumpsys package "$pkg" | grep -q 'ACCESS_FINE_LOCATION: granted=true\|ACCESS_COARSE_LOCATION: granted=true' \
      || err "Location permission not granted after the dialog"
  else
    err "Location permission dialog did not appear"
  fi
else
  err "\"Định vị\" button not found"
fi

# --- notifications: switch one on, allow them in Android's dialog, check the alarm, send a test ---
if scroll_to ui-notify.xml 'Báo lúc bắt đầu khoảng quanh trăng cao nhất'; then
  tap ui-notify.xml 'Báo lúc bắt đầu khoảng quanh trăng cao nhất' || err "Could not tap the notification switch"
  allow='permission_allow_button|text="Allow"'
  if wait_for ui-notify-permission.xml "$allow" && tap ui-notify-permission.xml "$allow"; then :; else
    err "Notification permission dialog did not appear"
  fi
  sleep 4
  adb shell dumpsys alarm > "$out/alarms.txt"
  echo "--- our alarm ---"
  grep -m3 'mooncalc' "$out/alarms.txt" || true
  grep -q 'io.github.nguyengiathong.mooncalc.NOTIFY' "$out/alarms.txt" || err "No alarm set for the notification schedule"
  if scroll_to ui-test.xml 'text="Gửi thử thông báo"'; then
    tap ui-test.xml 'text="Gửi thử thông báo"' || err "Could not tap the test button"
    sleep 15
    adb shell dumpsys notification --noredact > "$out/notifications.txt"
    echo "--- our notifications ---"
    grep -A12 'pkg=io.github.nguyengiathong.mooncalc' "$out/notifications.txt" | grep -E 'NotificationRecord|android\.(title|text)=' | head -6 || true
    grep -q 'Thông báo thử' "$out/notifications.txt" || err "Test notification was not shown"
    adb shell cmd statusbar expand-notifications || true
    sleep 2
    adb exec-out screencap -p > "$out/screen-notification.png"
    adb shell cmd statusbar collapse || true
  else
    err "\"Gửi thử thông báo\" button not found"
  fi
else
  err "Notification switches not found"
fi

adb logcat -d -v tag -s MoonCalc:V AndroidRuntime:E > "$out/logcat.txt" || true
echo "--- logcat ---"
cat "$out/logcat.txt"
grep -q '^E/MoonCalc' "$out/logcat.txt" && err "JavaScript errors in console"
grep -q 'FATAL EXCEPTION' "$out/logcat.txt" && err "App crashed"
adb shell pidof "$pkg" > /dev/null || err "App is not running"
exit $fail
