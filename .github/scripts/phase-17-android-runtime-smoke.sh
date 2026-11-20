#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is not set}"

APK="${GITHUB_WORKSPACE}/app/build/outputs/apk/debug/app-debug.apk"
UI_XML="${GITHUB_WORKSPACE}/wolf-window.xml"
SCREENSHOT="${GITHUB_WORKSPACE}/wolf-phase17.png"

test -s "$APK"
adb install -r "$APK"

adb shell svc wifi disable
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Some Google APIs emulator images have no telephony service at all.
# In that case mobile data is unavailable by design; do not call svc data.
PHONE_SERVICE="$(adb shell service check phone 2>&1 | tr -d '\r')"
if printf '%s' "$PHONE_SERVICE" | grep -qiE 'Service phone:[[:space:]]*found[[:space:]]*$'; then
  adb shell svc data disable
  echo "Telephony service found; mobile data disabled."
else
  echo "No telephony service on this emulator; mobile data radio is unavailable."
fi

test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = "1"
test "$(adb shell settings get global wifi_on | tr -d '\r')" = "0"

adb shell am force-stop com.wolfcodes.offline
adb shell am start -W -n com.wolfcodes.offline/.MainActivity

for attempt in $(seq 1 30); do
  if adb shell pidof com.wolfcodes.offline >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
adb shell pidof com.wolfcodes.offline

rm -f "$UI_XML"
for attempt in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/wolf-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/wolf-window.xml "$UI_XML" >/dev/null 2>&1 || true
  if test -s "$UI_XML" && grep -q "WOLF OFFLINE VIDEO" "$UI_XML"; then
    break
  fi
  sleep 1
done
test -s "$UI_XML"
grep -q "WOLF OFFLINE VIDEO" "$UI_XML"

adb shell screencap -p /sdcard/wolf-phase17.png
adb pull /sdcard/wolf-phase17.png "$SCREENSHOT"
test -s "$SCREENSHOT"
