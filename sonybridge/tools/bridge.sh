#!/bin/bash
# Installs, removes or checks the Lilac Sony Bridge as a privileged system app on the connected phone (adb root needed).
#   bridge.sh install [apk]   push the APK to /system/priv-app/LilacSonyBridge and reboot (default: the debug build)
#   bridge.sh uninstall       delete that folder and reboot
#   bridge.sh check           show whether the system app is installed, privileged and has its permission
# A system app is only scanned at boot, so every new build needs install + reboot. Set ANDROID_SERIAL to pick a phone.
# Undo of everything this script does: `bridge.sh uninstall`. No vendor file, SELinux setting or bootloader is touched.
set -euo pipefail
PKG=com.mdomokos.lilaccamera.sonybridge
DIR=/system/priv-app/LilacSonyBridge
HERE="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_APK="$HERE/../build/outputs/apk/debug/sonybridge-debug.apk"

rw() { adb root >/dev/null; adb wait-for-device; adb shell 'mount -o remount,rw /' >/dev/null; }

case "${1:-check}" in
  install)
    APK="${2:-$DEFAULT_APK}"
    [ -f "$APK" ] || { echo "no APK at $APK (build it: ./gradlew :sonybridge:assembleDebug)" >&2; exit 1; }
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
    echo "-- flags:";     adb shell dumpsys package "$PKG" | grep -E "^\s+(flags|privateFlags|pkgFlags)=|IMAGE_PROCESSOR" | head -6
    echo "-- cacao service:"; adb shell service check cacao
    echo "-- last capability report from the bridge (written when Lilac asks):"
    adb logcat -d -s LilacSonyBridge:I | grep "capabilities:" | tail -1 || true
    ;;
  *) sed -n 2,6p "$0"; exit 1 ;;
esac
