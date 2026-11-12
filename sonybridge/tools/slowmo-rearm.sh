#!/bin/bash
# Experiment: a second burst inside one recording. Usage: slowmo-rearm.sh <variant: none|b|c|bc> <gap seconds> [package]
# Fires one plain burst, waits <gap> s after it has arrived, then fires a second one after the re-arm steps of the variant
# (b: hand the encoder to the service again, c: repeat the mode change). RESTART=1 first restarts cacaoserver (adb root).
# Only metadata and logs are read. Needs the Lilac debug app in the foreground in video mode.
set -uo pipefail
VAR=${1:-none}; GAP=${2:-3}; PKG=${3:-com.mdomokos.lilaccamera.debug.slowmo}
b() { adb shell am broadcast -a com.mdomokos.lilaccamera.SLOWMO -p "$PKG" --es cmd "$1" --es variant "${2:-x}" --ei width 1280 --ei height 720 --ei frames 177 >/dev/null; }
if [ "${RESTART:-0}" = 1 ]; then
  adb shell am force-stop "$PKG"; adb shell am force-stop com.mdomokos.lilaccamera.sonybridge
  adb shell 'stop cacaoserver; start cacaoserver'; sleep 4
  adb shell am start -n "$PKG/com.hinnka.mycamera.MainActivity" >/dev/null 2>&1; sleep 9
fi
echo "cacaoserver $(adb shell pidof cacaoserver)"
adb logcat -c
b start
for i in $(seq 40); do adb logcat -d -s LilacSonyBridge | grep -q "startVideo -> 0" && break; sleep 0.5; done
sleep 2.5; b trigger
for i in $(seq 100); do adb logcat -d | grep -q "Burst 1:" && break; sleep 0.2; done
sleep "$GAP"
[ "$VAR" = none ] && b trigger2 a || b trigger2 "$VAR"
sleep 11; b stop; sleep 12
adb logcat -d | grep -E "LilacSonyBridge|PLog_SlowMotion|libcacao_client: start|CACAO" | grep -vE "TRANSMITTING|serialize|getSerialize|session established|Mode	|Variant frames|native callback type=1$" | cut -c7-175
echo "cacaoserver after: $(adb shell pidof cacaoserver)"
