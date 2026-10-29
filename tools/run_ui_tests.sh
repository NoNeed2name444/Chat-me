#!/bin/bash
# Runs the launch tests that "Build for testing" built (ci/public/ios.yml) on
# one simulator, from ios/. Each test runs on its own, with the simulator set
# to that test's appearance first and read back. The tests also set
# XCUIDevice.shared.appearance, but in run 40714297807 that didn't take: the
# dark test found the page bone, and the simulator never went dark. One report
# on Apple's developer forums says the same of tests run through xcodebuild
# (thread 812656); nobody there confirmed it.
# Logs go to ../results/<kind>-<look>.log and result bundles to
# ../results/<kind>-<look>.xcresult. It fails unless each test passed, by name.
# The simulator is shut down afterwards, so the next one boots alone: in run
# 40718963474 the iPad booted beside the running iPhone, in 6 minutes the
# first time and in 11 the second.
#
#   UDID=<simulator> bash ../tools/run_ui_tests.sh iphone|ipad
set -euo pipefail
kind="$1"
: "${UDID:?the simulator to run on}"
mkdir -p ../results
booting=$SECONDS
xcrun simctl bootstatus "$UDID" -b > /dev/null
echo "$kind: the simulator booted in $((SECONDS - booting)) seconds"
failed=0
# Each run is <appearance>:<target>/<class>/<test>, or <appearance>:<target>
# for a whole unit-test bundle. The gallery's screenshots are step 2's
# renders of the look kit, by day and by night.
for run in light:SortaUITests/LaunchTests/testLightPageIsBone \
           dark:SortaUITests/LaunchTests/testDarkPageIsAubergine \
           light:SortaUITests/GalleryTests/testGalleryRenders \
           dark:SortaUITests/GalleryTests/testGalleryRenders \
           light:SortaUnitTests; do
  look="${run%%:*}"
  only="${run#*:}"
  test="${only##*/}"
  name="$look-${only//\//-}"
  log="../results/$kind-$name.log"
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
    -resultBundlePath "../results/$kind-$name.xcresult" \
    "-only-testing:$only" \
    -test-timeouts-enabled YES \
    -default-test-execution-time-allowance 300 \
    -maximum-test-execution-time-allowance 300 \
    CODE_SIGNING_ALLOWED=NO \
    test-without-building 2>&1 | tee "$log" | grep --line-buffered -E "error:|Test [Cc]ase|TEST |\*\* " || true
  if [ "$only" = "$test" ]; then
    # a whole bundle: every case passed, and none failed
    passed_by_name=$(grep -cE "Test [Cc]ase .*passed" "$log" || true)
    grep -qE "Test [Cc]ase .*failed" "$log" && passed_by_name=0
  else
    passed_by_name=$(grep -cE "[ .]$test.*passed" "$log" || true)
  fi
  if [ "$passed_by_name" -gt 0 ] && grep -qE "\*\* TEST (EXECUTE )?SUCCEEDED \*\*" "$log"; then
    echo "$kind: $test passed"
  else
    echo "::error::$kind: $test did not pass"
    failed=1
  fi
done
xcrun simctl shutdown "$UDID" || echo "::warning::$kind: the simulator didn't shut down"
exit "$failed"
