#!/usr/bin/env bash
# Fails the e2e job when the connected tests didn't really run.
#
# AGP's connected test runner can fail to install the APKs (a full emulator /data partition, or a package manager
# that isn't up yet) and still end the Gradle task successfully, with zero tests run. That kept the e2e job green
# without running a single test from 7 October 2026, when it moved to the API 37 emulator. So, after
# `connectedDebugAndroidTest`:
#   1. the Gradle log must not mention a failed install or a failed test runner;
#   2. at least one test case must have run, counted from the JUnit XML when the runner writes it, otherwise from
#      the HTML report's "tests" counter.
#
# Usage: verify-e2e-ran.sh <gradle log> [results dir] [report index.html]
set -euo pipefail

log="${1:?gradle log}"
results="${2:-app/build/outputs/androidTest-results/connected}"
report="${3:-app/build/reports/androidTests/connected/debug/index.html}"

if grep -nE "Failed to install|AndroidTestRunner failed|INSTALL_FAILED" "$log"; then
  echo "::error::The e2e APKs failed to install, so no tests ran (see the lines above)."
  exit 1
fi

tests=0
if [ -d "$results" ]; then
  tests=$(find "$results" -name '*.xml' -exec cat {} + 2>/dev/null | { grep -o '<testcase ' || true; } | wc -l | tr -d ' ')
fi
if [ "$tests" -eq 0 ] && [ -f "$report" ]; then
  # <div class="infoBox" id="tests"> is followed by <div class="counter">N</div>.
  tests=$({ grep -A2 'id="tests"' "$report" || true; } | { grep -o 'counter">[0-9]*' || true; } | { grep -o '[0-9]*' || true; } | head -1)
  tests="${tests:-0}"
fi

if [ "$tests" -eq 0 ]; then
  echo "::error::No e2e tests ran: no test cases in $results or $report."
  exit 1
fi
echo "$tests e2e test cases ran."
