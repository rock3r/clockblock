#!/usr/bin/env bash
# Runs the connected (emulator) tests on the emulator that android-emulator-runner booted, keeping the Gradle log
# in connected-tests.log for verify-e2e-ran.sh.
#
# On the API 37 image the package service can answer right after boot and then disappear again for a while ("cmd:
# Can't find service: package", then "Failure calling service package: Broken pipe" during the install). AGP's
# runner doesn't retry the install, so:
#   1. wait until the package service has answered several times in a row, not just once;
#   2. if the APKs still fail to install, print some emulator diagnostics, wait again and retry once.
set -uo pipefail

log=connected-tests.log

# Succeeds once `pm path android` has worked 5 times in a row, 2 s apart; gives up after 5 minutes.
wait_for_package_service() {
  local ok=0
  for _ in $(seq 1 150); do
    if adb shell pm path android >/dev/null 2>&1; then ok=$((ok + 1)); else ok=0; fi
    if [ "$ok" -ge 5 ]; then return 0; fi
    sleep 2
  done
  echo "::warning::The package service didn't settle within 5 minutes."
  return 1
}

diagnostics() {
  echo "::group::Emulator diagnostics"
  echo "boot_completed=$(adb shell getprop sys.boot_completed 2>&1)"
  echo "system_server starts=$(adb shell getprop sys.system_server.start_count 2>&1)"
  adb shell df -h /data 2>&1
  adb shell cat /proc/meminfo 2>&1 | head -3
  adb logcat -d -b crash 2>&1 | tail -40
  echo "::endgroup::"
}

adb wait-for-device
for attempt in 1 2; do
  wait_for_package_service
  diagnostics
  ./gradlew :app:connectedDebugAndroidTest 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  if ! grep -qE "Failed to install|AndroidTestRunner failed|INSTALL_FAILED" "$log"; then
    exit "$status"
  fi
  echo "::warning::The e2e APKs didn't install (attempt $attempt of 2)."
  diagnostics
done
exit 1
