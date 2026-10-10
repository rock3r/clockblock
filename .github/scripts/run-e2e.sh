#!/usr/bin/env bash
# Runs the connected (emulator) tests on the emulator that android-emulator-runner booted, keeping the Gradle log
# in connected-tests.log for verify-e2e-ran.sh.
#
# On the API 37 image surfaceflinger aborts when SystemUI samples the screen under the gesture navigation handle
# ("Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma" in the goldfish gralloc mapper; the current stable
# emulator doesn't offer that feature). Each abort restarts system_server: the package service disappears for a while
# ("cmd: Can't find service: package", "Failure calling service package: Broken pipe" during the install), and after
# it the user is locked again, so an app started then can't read its SharedPreferences and the instrumentation dies
# before running a test. AGP's runner reports neither as a failure. So:
#   1. switch to three-button navigation, which doesn't sample the screen;
#   2. wait until the package service has answered and the user's credential-encrypted storage has been available
#      several times in a row, not just once;
#   3. if the APKs still fail to install, print some emulator diagnostics, wait again and retry once.
set -uo pipefail

log=connected-tests.log

# Succeeds once `pm path android` has worked and user 0 has been unlocked 5 times in a row, 2 s apart; gives up
# after 5 minutes.
wait_for_steady_system() {
  local ok=0
  for _ in $(seq 1 150); do
    if adb shell pm path android >/dev/null 2>&1 \
      && [ "$(adb shell getprop sys.user.0.ce_available 2>/dev/null | tr -d '\r')" = "true" ]; then
      ok=$((ok + 1))
    else
      ok=0
    fi
    if [ "$ok" -ge 5 ]; then return 0; fi
    sleep 2
  done
  echo "::warning::The system didn't settle within 5 minutes."
  return 1
}

diagnostics() {
  echo "::group::Emulator diagnostics"
  echo "boot_completed=$(adb shell getprop sys.boot_completed 2>&1)"
  echo "system_server starts=$(adb shell getprop sys.system_server.start_count 2>&1)"
  echo "user 0 unlocked=$(adb shell getprop sys.user.0.ce_available 2>&1)"
  adb shell df -h /data 2>&1
  adb shell cat /proc/meminfo 2>&1 | head -3
  adb logcat -d -b crash 2>&1 | tail -40
  echo "::endgroup::"
}

adb wait-for-device
# Best effort: it avoids one source of the aborts (the navigation handle's sampling), not all of them (#122).
three_button=false
for _ in 1 2 3 4 5; do
  wait_for_steady_system
  if adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton; then
    three_button=true
    break
  fi
done
if [ "$three_button" != true ]; then echo "::warning::Couldn't switch to three-button navigation; running with gestures."; fi
for attempt in 1 2; do
  wait_for_steady_system
  diagnostics
  # The device log is the only trace of an instrumentation run that ends without running anything.
  adb logcat -c 2>/dev/null
  adb logcat -v threadtime > "emulator-logcat-$attempt.txt" 2>&1 &
  logcat_pid=$!
  ./gradlew :app:connectedPlayDebugAndroidTest 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  kill "$logcat_pid" 2>/dev/null
  echo "system_server starts after the run=$(adb shell getprop sys.system_server.start_count 2>&1)"
  if ! grep -qiE "failed to install|AndroidTestRunner failed|INSTALL_FAILED|Can't find service: package|Failure calling service package" "$log"; then
    exit "$status"
  fi
  echo "::warning::The e2e APKs didn't install (attempt $attempt of 2)."
  diagnostics
done
exit 1
