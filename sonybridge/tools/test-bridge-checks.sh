#!/bin/bash
# Tests bridge.sh's pre-install checks against a fake adb; no phone needed.
# Usage: sonybridge/tools/test-bridge-checks.sh
HERE="$(cd "$(dirname "$0")" && pwd)"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
touch "$T/bridge.apk"
cat > "$T/adb" <<'FAKE'
#!/bin/bash
# FAKE_CACAO=found|missing  FAKE_SHIM=found|missing  FAKE_ERR=1 (adb itself fails)
echo "$*" >> "$FAKE_LOG"; a="$*"; f="${a#shell ls }"
if [ "${FAKE_ERR:-}" = 1 ]; then echo "error: no devices/emulators found" >&2; exit 1; fi
if [ "${FAKE_ERR:-}" = serial ]; then echo "adb: device 'abc' not found" >&2; exit 1; fi
case "$*" in
  "shell service check cacao")
    [ "${FAKE_CACAO:-found}" = found ] && echo "Service cacao: found" || echo "Service cacao: not found" ;;
  "shell ls /system/lib64/"*)
    if [ "${FAKE_CACAO:-found}" = found ]; then echo "$f"; else echo "ls: $f: No such file or directory"; exit 1; fi ;;
  "shell ls /vendor/lib/hw/"*)
    if [ "${FAKE_SHIM:-found}" = found ]; then echo "ls: $f: Permission denied"; else echo "ls: $f: No such file or directory"; fi; exit 1 ;;
esac
exit 0
FAKE
chmod +x "$T/adb"
# The fake is also first on PATH, so a plain `adb` call can never reach a real phone.
export ADB="$T/adb" FAKE_LOG="$T/log" PATH="$T:$PATH"
fails=0
run() { : > "$FAKE_LOG"; OUT=$(echo "$1" | "$HERE/bridge.sh" "${@:2}" 2>&1); RC=$?; }
ok() { echo "PASS $1"; }
bad() { echo "FAIL $1: $2"; echo "$OUT" | sed 's/^/    /'; fails=$((fails+1)); }
touched() { grep -qE '^(push|reboot|root|shell mount|shell pm uninstall)' "$FAKE_LOG"; }

test_all_present() {
  FAKE_CACAO=found FAKE_SHIM=found run n check
  [ $RC -eq 0 ] || { bad $FUNCNAME "exit $RC"; return; }
  [ "$(echo "$OUT" | grep -c ': found')" -ge 2 ] || { bad $FUNCNAME "both checks not reported found"; return; }
  ok $FUNCNAME
}
test_cacao_missing_declined() {
  FAKE_CACAO=missing FAKE_SHIM=found run n install "$T/bridge.apk"
  [ $RC -eq 1 ] || { bad $FUNCNAME "exit $RC"; return; }
  echo "$OUT" | grep -q 'cacao.*missing' || { bad $FUNCNAME "cacao not named missing"; return; }
  echo "$OUT" | grep -qi 'slow motion' || { bad $FUNCNAME "slow motion not named"; return; }
  echo "$OUT" | grep -qF 'Install anyway? [y/N]' || { bad $FUNCNAME "no prompt"; return; }
  touched && { bad $FUNCNAME "phone touched: $(tr '\n' ';' < "$FAKE_LOG")"; return; }
  ok $FUNCNAME
}
test_shim_missing_accepted() {
  FAKE_CACAO=found FAKE_SHIM=missing run y install "$T/bridge.apk"
  [ $RC -eq 0 ] || { bad $FUNCNAME "exit $RC"; return; }
  echo "$OUT" | grep -qi 'pro mode' || { bad $FUNCNAME "Pro mode not named"; return; }
  grep -q '^push' "$FAKE_LOG" || { bad $FUNCNAME "no push"; return; }
  ok $FUNCNAME
}
test_adb_error_stops() {
  FAKE_ERR=1 run y install "$T/bridge.apk"
  [ $RC -eq 1 ] || { bad $FUNCNAME "exit $RC"; return; }
  echo "$OUT" | grep -qi 'could not check' || { bad $FUNCNAME "no 'could not check'"; return; }
  echo "$OUT" | grep -qi 'missing' && { bad $FUNCNAME "says missing"; return; }
  grep -q '^push' "$FAKE_LOG" && { bad $FUNCNAME "pushed"; return; }
  ok $FUNCNAME
}
test_default_adb() {
  # Without ADB set, bridge.sh finds adb on PATH (here the fake).
  : > "$FAKE_LOG"; OUT=$(env -u ADB "$HERE/bridge.sh" check 2>&1 </dev/null); RC=$?
  [ $RC -eq 0 ] || { bad $FUNCNAME "exit $RC"; return; }
  grep -q 'service check cacao' "$FAKE_LOG" || { bad $FUNCNAME "fake adb not called"; return; }
  ok $FUNCNAME
}
test_unknown_device_stops() {
  # adb's own "device ... not found" (a stale ANDROID_SERIAL) must not read as "cacao missing".
  FAKE_ERR=serial run y install "$T/bridge.apk"
  [ $RC -eq 1 ] || { bad $FUNCNAME "exit $RC"; return; }
  echo "$OUT" | grep -qi 'could not check' || { bad $FUNCNAME "no 'could not check'"; return; }
  echo "$OUT" | grep -qi 'missing' && { bad $FUNCNAME "says missing"; return; }
  grep -q '^push' "$FAKE_LOG" && { bad $FUNCNAME "pushed"; return; }
  ok $FUNCNAME
}
for t in test_unknown_device_stops test_default_adb test_all_present test_cacao_missing_declined test_shim_missing_accepted test_adb_error_stops; do $t; done
[ $fails -eq 0 ] && echo "all passed" || { echo "$fails failed"; exit 1; }
