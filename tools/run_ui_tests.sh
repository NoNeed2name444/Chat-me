#!/bin/bash
# Runs the UI tests that "Build for testing" built (ci/public/ios.yml) on one
# simulator, from ios/. The log goes to ../<kind>.log and the result bundle to
# ../<kind>.xcresult. It fails unless each launch test passed, by name.
#
#   UDID=<simulator> bash ../tools/run_ui_tests.sh iphone|ipad
set -euo pipefail
kind="$1"
: "${UDID:?the simulator to run on}"
xcodebuild -project Sorta.xcodeproj -scheme Sorta -configuration Debug \
  -destination "id=$UDID" -derivedDataPath build \
  -resultBundlePath "../$kind.xcresult" \
  -test-timeouts-enabled YES \
  -default-test-execution-time-allowance 300 \
  -maximum-test-execution-time-allowance 300 \
  CODE_SIGNING_ALLOWED=NO \
  test-without-building 2>&1 | tee "../$kind.log" | grep --line-buffered -E "error:|Test [Cc]ase|TEST |\*\* " || true
failed=0
for test in testLightPageIsBone testDarkPageIsAubergine; do
  if grep -qE "LaunchTests[ .]$test.*passed" "../$kind.log"; then
    echo "$kind: $test passed"
  else
    echo "::error::$kind: $test did not pass"
    failed=1
  fi
done
grep -qE "\*\* TEST (EXECUTE )?SUCCEEDED \*\*" "../$kind.log" || { echo "::error::$kind: the test run did not succeed"; failed=1; }
exit "$failed"
