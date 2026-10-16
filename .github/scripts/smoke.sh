#!/usr/bin/env bash
# Installs the APK on the emulator, sets a Belgrade GPS fix, walks every screen, records screenshots + API logs + crashes.
set -u
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/*.apk | head -1)
PKG=net.kravos.weather

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell settings put secure location_mode 3
# Novi Beograd (lon lat)
adb emu geo fix 20.4122 44.8176
sleep 2
adb emu geo fix 20.4122 44.8176
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity > "$OUT/start.txt" 2>&1
sleep 25

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1" >> "$OUT/steps.txt"; }

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  python3 - "$1" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
want, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for n in root.iter('node'):
    if n.get('text') == want or n.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
  if [ -s "$OUT/tap.txt" ]; then
    adb shell input tap $(cat "$OUT/tap.txt")
    echo "tapped $1" >> "$OUT/steps.txt"
  else
    echo "not found: $1" >> "$OUT/steps.txt"
  fi
  sleep 3
}

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 2; }

tap_text "Allow"
tap_text "While using the app"
sleep 3
shot 01-today
for i in 2 3 4 5 6 7; do scroll; shot 0$i-today-scroll; done

tap_text "Prognoza"
sleep 3
shot 10-hourly
scroll; shot 11-hourly-scroll
tap_text "16 dana"
shot 12-daily
scroll; shot 13-daily-scroll
tap_text "Dugoročno"
sleep 6
shot 14-long
scroll; shot 15-long-scroll
scroll; shot 16-long-scroll2

tap_text "Vazduh"
sleep 3
shot 20-air
scroll; shot 21-air-scroll
scroll; shot 22-air-scroll2
scroll; shot 23-air-scroll3

tap_text "Radar"
sleep 20
shot 30-radar
tap_text "Pusti"
sleep 8
shot 31-radar-play
tap_text "Pauza"
tap_text "Padavine — prognoza"
sleep 15
shot 32-precip-fc
tap_text "Temperatura"
sleep 6
shot 33-temp
tap_text "Vetar"
sleep 6
shot 34-wind

tap_text "Danas"
sleep 2
tap_text "Lokacije"
sleep 2
shot 50-places
tap_text "Traži grad ili mesto…"
adb shell input text "Thessaloniki"
sleep 6
shot 51-search
tap_text "Solun"
sleep 12
shot 52-thessaloniki
scroll; shot 53-thessaloniki-scroll

tap_text "Podešavanja"
sleep 2
shot 40-settings
tap_text "Tamna"
sleep 2
shot 41-settings-dark
scroll; shot 42-settings-scroll2
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 3
tap_text "Lokacije"
sleep 2
tap_text "Novi Beograd"
sleep 8
shot 60-dark-today
scroll; shot 61-dark-scroll
tap_text "Vazduh"
sleep 2
shot 62-dark-air
tap_text "Radar"
sleep 15
shot 63-dark-radar
tap_text "Temperatura"
sleep 8
shot 64-dark-temp
tap_text "Danas"

# run the background worker once (notifications/widget path)
adb shell cmd jobscheduler run -f $PKG 0 >/dev/null 2>&1 || true
sleep 5
adb shell dumpsys notification --noredact > "$OUT/notifications.txt" 2>&1

PID=$(adb shell pidof $PKG | tr -d '\r')
echo "pid=$PID" > "$OUT/status.txt"
adb logcat -d > "$OUT/logcat.txt"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -n -E "KravosWeather|AndroidRuntime|net.kravos" "$OUT/logcat.txt" | tail -400 > "$OUT/app-log.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "CRASH" >> "$OUT/status.txt"; fi
rm -f "$OUT/logcat.txt"
cat "$OUT/status.txt" "$OUT/steps.txt"
exit 0
