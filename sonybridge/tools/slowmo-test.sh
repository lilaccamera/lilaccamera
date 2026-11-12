#!/bin/bash
# One slow-motion recording on the phone through the debug broadcast of a Lilac debug build, then a check of the
# saved file with ffprobe (resolution, frame count, time stamp jumps, sound) and of the camera service processes.
# Needs the camera open in video mode in the foreground. Usage: slowmo-test.sh [package] [width] [height] [frames] [out dir]
# Only metadata and statistics are read; the video is never looked at.
set -euo pipefail
PKG=${1:-com.mdomokos.lilaccamera.debug.slowmo}; W=${2:-1280}; H=${3:-720}; N=${4:-91}
OUT=${5:-${TMPDIR:-/tmp}/slowmo-test}; mkdir -p "$OUT"
cmd() { adb shell am broadcast -a com.mdomokos.lilaccamera.SLOWMO -p "$PKG" --es cmd "$1" --ei width "$W" --ei height "$H" --ei frames "$N" >/dev/null; }
pids() { adb shell 'pidof cacaoserver cameraserver camerahalserver 2>/dev/null; pidof android.hardware.camera.provider@2.4-service_64 2>/dev/null' | tr '\r\n' '  '; }
newest() { adb shell 'ls -t /sdcard/DCIM/LilacCamera/*_960fps.mp4 2>/dev/null | head -1' | tr -d '\r'; }

echo "processes before: $(pids)"
adb logcat -c
last=$(newest)
cmd start
# wait for the ordinary video to run (the recorder logs it), then let it run 2.5 s like Sony's app before the trigger
for i in $(seq 40); do adb logcat -d -s LilacSonyBridge | grep -q "startVideo -> 0" && break; sleep 0.5; done
sleep 2.5; cmd trigger; sleep 9; cmd stop
for i in $(seq 40); do f=$(newest); [ -n "$f" ] && [ "$f" != "$last" ] && break; sleep 1; done
[ "$f" = "$last" ] && { echo "NO NEW FILE; bridge log:"; adb logcat -d -s LilacSonyBridge PLog_SlowMotionRecorder | tail -15; exit 1; }
sleep 2
adb pull "$f" "$OUT/" >/dev/null; file="$OUT/$(basename "$f")"
echo "file: $file ($(du -h "$file" | cut -f1))"
ffprobe -v error -select_streams v:0 -show_entries stream=width,height,nb_frames,duration:stream_side_data=rotation -of csv=p=0 "$file" | tr '\n' ' '; echo
ffprobe -v error -select_streams v:0 -show_entries packet=pts_time -of csv=p=0 "$file" | awk -F, 'NR==1{p=$1} {d=$1-p; if (NR>1 && (d>0.05||d<0)) printf "video jump at frame %d: %.3f s\n", NR-1, d; p=$1} END{printf "video frames %d, last pts %.3f\n", NR, p}'
ffprobe -v error -select_streams a:0 -show_entries packet=pts_time -of csv=p=0 "$file" | awk -F, 'NR==1{p=$1} {d=$1-p; if (NR>1 && d>0.05) printf "audio silent for %.3f s from %.3f\n", d, p; p=$1} END{printf "audio packets %d, last pts %.3f\n", NR, p}'
echo "processes after:  $(pids)"
adb logcat -d | grep -E "Burst:|Mode change|Stop:|Sound:|abort|onStopRequest|FATAL" | cut -c1-200
