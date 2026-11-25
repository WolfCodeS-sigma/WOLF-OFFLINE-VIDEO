#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is not set}"

APK="${GITHUB_WORKSPACE}/app/build/outputs/apk/debug/app-debug.apk"
UI_XML="${GITHUB_WORKSPACE}/wolf-window.xml"
SCREENSHOT="${GITHUB_WORKSPACE}/wolf-phase17.png"

test -s "$APK"
adb install -r "$APK"

# Require Android 12 (API 31) and use the Realme 7's 1080x2400 display resolution.
ANDROID_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
test "$ANDROID_API" = "31"
echo "Android API verified: $ANDROID_API (Android 12)"
adb shell wm size 1080x2400
DISPLAY_SIZE="$(adb shell wm size | tr -d '\r')"
printf '%s\n' "$DISPLAY_SIZE"
printf '%s\n' "$DISPLAY_SIZE" | grep -q "Override size: 1080x2400"

# Airplane mode is a protected system operation. Ask ConnectivityService to
# change it so Android itself publishes the protected state-change broadcast.
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable

# Some Google APIs emulator images have no telephony service or mobile radio.
PHONE_SERVICE="$(adb shell service check phone 2>&1 | tr -d '\r')"
if printf '%s' "$PHONE_SERVICE" | grep -qiE 'Service phone:[[:space:]]*found[[:space:]]*$'; then
  adb shell svc data disable
  echo "Telephony service found; mobile data disabled."
else
  echo "No telephony service on this emulator; mobile data radio is unavailable."
fi

test "$(adb shell cmd connectivity airplane-mode | tr -d '\r')" = "enabled"
test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = "1"
test "$(adb shell settings get global wifi_on | tr -d '\r')" = "0"

# Exercise the actual scene dispatch and AVC/MP4 encoder path while the emulator is offline.
gradle --no-daemon :app:connectedDebugAndroidTest

# connectedDebugAndroidTest manages the tested package lifecycle. Reinstall the
# production debug APK explicitly before the independent application-launch smoke test.
APP_ID="com.wolfcodes.offline"
adb install -r "$APK"

# Launch through the implicit Android MAIN/LAUNCHER intent. PackageManager
# resolves the manifest-declared launcher activity for this package, so the
# smoke test does not duplicate or guess a Java/Kotlin Activity class name.
adb shell am force-stop "$APP_ID"
adb shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  "$APP_ID"

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
