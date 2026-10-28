#!/bin/bash
# Runs the launch tests that "Build for testing" built (ci/public/ios.yml) on
# one simulator, from ios/. Each test runs on its own, with the simulator set
# to that test's appearance first and read back. XCUIDevice.shared.appearance,
# which the tests also set, is ignored when tests run through xcodebuild on
# iOS 26 simulators: in run 40714297807 the dark test found the page bone, and
# the simulator never went dark (Apple's developer forums, thread 812656).
# Logs go to ../results/<kind>-<look>.log and result bundles to
# ../results/<kind>-<look>.xcresult. It fails unless each test passed, by name.
#
#   UDID=<simulator> bash ../tools/run_ui_tests.sh iphone|ipad
set -euo pipefail
kind="$1"
: "${UDID:?the simulator to run on}"
mkdir -p ../results
xcrun simctl bootstatus "$UDID" -b > /dev/null
failed=0
for run in light:testLightPageIsBone dark:testDarkPageIsAubergine; do
  look="${run%%:*}"
  test="${run#*:}"
  log="../results/$kind-$look.log"
  xcrun simctl ui "$UDID" appearance "$look"
  now=$(xcrun simctl ui "$UDID" appearance | tr -d '[:space:]')
  echo "$kind: the simulator's appearance is $now, for $test"
  if [ "$now" != "$look" ]; then
    echo "::error::$kind: the simulator is $now, not $look, so $test was not run"
    failed=1
    continue
  fi
  xcodebuild -project Sorta.xcodeproj -scheme Sorta -configuration Debug \
    -destination "id=$UDID" -derivedDataPath build \
    -resultBundlePath "../results/$kind-$look.xcresult" \
    "-only-testing:SortaUITests/LaunchTests/$test" \
    -test-timeouts-enabled YES \
    -default-test-execution-time-allowance 300 \
    -maximum-test-execution-time-allowance 300 \
    CODE_SIGNING_ALLOWED=NO \
    test-without-building 2>&1 | tee "$log" | grep --line-buffered -E "error:|Test [Cc]ase|TEST |\*\* " || true
  if grep -qE "LaunchTests[ .]$test.*passed" "$log" && grep -qE "\*\* TEST (EXECUTE )?SUCCEEDED \*\*" "$log"; then
    echo "$kind: $test passed"
  else
    echo "::error::$kind: $test did not pass"
    failed=1
  fi
done
exit "$failed"
