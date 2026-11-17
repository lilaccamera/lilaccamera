#!/bin/bash
# Burst length experiment: N recordings asking for 177 frames, one line each with the burst's frame count, the camera
# temperature and free memory before it. Usage: slowmo-len.sh [runs] [package]. Needs the Lilac debug app in the foreground.
PKG=${2:-io.github.lilaccamera.debug.slowmo}
b() { adb shell am broadcast -a io.github.lilaccamera.SLOWMO -p "$PKG" --es cmd "$1" --ei width 1280 --ei height 720 --ei frames 177 >/dev/null; }
for n in $(seq ${1:-4}); do
  mem=$(adb shell "grep -E 'MemAvailable' /proc/meminfo" | awk '{print int($2/1024)}'); temp=$(adb shell 'cat /sys/class/thermal/thermal_zone0/temp' | tr -d '\r')
  adb logcat -c; b start
  for i in $(seq 40); do adb logcat -d -s LilacSonyBridge | grep -q "startVideo -> 0" && break; sleep 0.5; done
  sleep 2.5; b trigger
  for i in $(seq 60); do adb logcat -d | grep -q "Burst 1:" && break; sleep 0.2; done
  line=$(adb logcat -d | grep "Burst 1:" | sed 's/.*Burst 1: //')
  sleep 2; b stop; sleep 10
  echo "run $n: MemAvailable ${mem} MB, temp ${temp} C, $line"
done
