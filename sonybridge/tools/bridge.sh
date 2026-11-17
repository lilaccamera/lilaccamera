#!/bin/bash
# Installs, removes or checks the Lilac Sony Bridge as a privileged system app on the connected phone (adb root needed).
#   bridge.sh install [apk]   push the APK to /system/priv-app/LilacSonyBridge and reboot (default: the debug build)
#   bridge.sh uninstall       delete that folder and reboot
#   bridge.sh check           show whether the system app is installed, privileged and has its permission
# A system app is only scanned at boot, so every new build needs install + reboot. Set ANDROID_SERIAL to pick a phone.
# Undo of everything this script does: `bridge.sh uninstall`. No vendor file, SELinux setting or bootloader is touched.
# install first checks, without changing anything, that the ROM has Sony's photo service (cacao) and the raw19 RAW
# shim; if either is missing it asks before going on. The shim renames Sony's camera HAL to
# /vendor/lib/hw/camera.lilac-real.so and takes its place, so that file exists only on ROMs with the shim.
# Set ADB to use another adb binary.
set -euo pipefail
PKG=io.github.lilaccamera.sonybridge
DIR=/system/priv-app/LilacSonyBridge
HERE="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_APK="$HERE/../build/outputs/apk/debug/sonybridge-debug.apk"
ADB="${ADB:-adb}"
adb() { command "$ADB" "$@"; }
CACAO_LIBS="/system/lib64/libcacao_client.so /system/lib64/libimageprocessorjni.so"
SHIM_FILE=/vendor/lib/hw/camera.lilac-real.so

# Each check prints one line and returns 0 found, 1 missing, 2 could not check (adb failed).
# A file the shell user may not read still shows up as "Permission denied", which counts as found.
file_state() {
  local out; out=$(adb shell ls "$1" 2>&1) || true
  case "$out" in
    *"No such file"*) return 1 ;;
    "$1"|*"Permission denied"*) return 0 ;;
    *) return 2 ;;
  esac
}
check_cacao() {
  local out f rc=0; out=$(adb shell service check cacao 2>&1) || true
  case "$out" in
    *"Service cacao: not found"*) rc=1 ;;
    *"Service cacao: found"*) for f in $CACAO_LIBS; do file_state "$f" || { rc=$?; break; }; done ;;
    *) rc=2 ;;
  esac
  case $rc in
    0) echo "cacao: found (Sony's photo service and its libraries)" ;;
    1) echo "cacao: missing (Sony's photo service or its libraries). Slow motion will not work without it." ;;
    *) echo "cacao: could not check: ${out:-no answer from adb}" ;;
  esac
  return $rc
}
check_shim() {
  local rc=0; file_state "$SHIM_FILE" || rc=$?
  case $rc in
    0) echo "raw19 shim: found" ;;
    1) echo "raw19 shim: missing ($SHIM_FILE). Pro mode needs it for RAW." ;;
    *) echo "raw19 shim: could not check: $(adb shell true 2>&1 || true)" ;;
  esac
  return $rc
}
# Runs both checks; stops on an adb failure, asks before going on when something is missing.
preflight() {
  local c=0 s=0
  check_cacao || c=$?; check_shim || s=$?
  if [ $c -eq 2 ] || [ $s -eq 2 ]; then echo "Could not check the phone; nothing was changed. Is it connected, with USB debugging on?" >&2; exit 1; fi
  if [ $c -ne 0 ] || [ $s -ne 0 ]; then
    echo "The bridge and the features above do nothing without what is missing."
    printf "Install anyway? [y/N] "; read -r answer || answer=
    [ "$answer" = y ] || { echo "Stopped; nothing was changed."; exit 1; }
  fi
}

rw() { adb root >/dev/null; adb wait-for-device; adb shell 'mount -o remount,rw /' >/dev/null; }

case "${1:-check}" in
  install)
    APK="${2:-$DEFAULT_APK}"
    [ -f "$APK" ] || { echo "no APK at $APK (build it: ./gradlew :sonybridge:assembleDebug)" >&2; exit 1; }
    preflight
    adb shell pm uninstall "$PKG" >/dev/null 2>&1 || true   # a normal install would hide the system copy
    rw
    adb shell "mkdir -p $DIR"
    adb push "$APK" "$DIR/LilacSonyBridge.apk"
    adb shell "chmod 644 $DIR/LilacSonyBridge.apk; chcon u:object_r:system_file:s0 $DIR/LilacSonyBridge.apk; sync"
    echo "pushed; rebooting (the system app is picked up at boot)"
    adb reboot
    ;;
  uninstall)
    rw
    adb shell "rm -rf $DIR; sync"
    echo "removed; rebooting"
    adb reboot
    ;;
  check)
    echo "-- package:";   adb shell pm path "$PKG" || echo "not installed"
    echo "-- flags:";     adb shell dumpsys package "$PKG" | grep -E "^\s+(flags|privateFlags|pkgFlags)=|IMAGE_PROCESSOR" | head -6 || true
    rc=0; check_cacao || rc=1; check_shim || rc=1
    echo "-- last capability report from the bridge (written when Lilac asks):"
    adb logcat -d -s LilacSonyBridge:I | grep "capabilities:" | tail -1 || true
    exit $rc
    ;;
  *) sed -n 2,6p "$0"; exit 1 ;;
esac
